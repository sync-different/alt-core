/**
 * File Download Service
 * Handles chunked file downloads with progress tracking, retry logic,
 * resume support via incremental disk writes, integrity verification,
 * connection health checks, adaptive chunk sizing, chunk checksum
 * verification, and parallel chunk downloads.
 */

import SparkMD5 from 'spark-md5';
import type { File } from '../types/models';
import { buildUrl } from '../utils/urlHelper';

const DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024; // 10MB
const DEFAULT_MAX_RETRIES = 3; // Maximum retry attempts per chunk
const RETRY_DELAY = 1000; // Initial retry delay in ms (will increase exponentially)
const CHUNK_TIMEOUT = 60000; // 60s timeout per chunk fetch
const HEALTH_CHECK_TIMEOUT = 10000; // 10s timeout for health check
const HEALTH_CHECK_RETRY_DELAY = 10000; // 10s between health check retries

// Adaptive chunk sizing constants
const ADAPTIVE_RETRY_THRESHOLD = 2;     // Halve chunk size after this many consecutive chunk retries
const ADAPTIVE_SUCCESS_THRESHOLD = 5;   // Double chunk size after this many consecutive successes
const ADAPTIVE_MIN_CHUNK_SIZE = 1.25 * 1024 * 1024; // 1.25MB floor
const ADAPTIVE_MAX_MULTIPLIER = 2;      // Max chunk size = 2x the user-selected chunk size

export interface DownloadProgress {
  percentage: number;
  downloadedBytes: number;
  totalBytes: number;
  speedKBps: number;
  estimatedTimeRemaining: number;
  elapsedTime: number;
  errorCount: number;
  retryCount: number;
  lastError?: string;
  currentStatus?: string;
  averageSpeedKBps?: number;
  currentChunkSizeMB?: number; // For adaptive chunk size display
}

export interface DownloadLogEntry {
  type: 'info' | 'warning' | 'error' | 'success';
  message: string;
  timestamp: number;
}

export interface ResumeState {
  resumeOffset: number;         // bytes successfully written to disk
  completedChunks: number;      // number of chunks written (sequential mode)
  totalChunks: number;          // total chunks for the file
  fileHandle?: FileSystemFileHandle; // to reopen on resume
  inMemoryChunks?: Blob[];      // fallback when FS API unavailable
  md5State?: string;            // serialized spark-md5 state for resume (sequential only)
  completedChunkSet?: number[]; // F7: set of completed chunk indices (parallel mode)
}

export interface DownloadOptions {
  onProgress?: (progress: DownloadProgress) => void;
  onComplete?: () => void;
  onError?: (error: Error, resumeState?: ResumeState) => void;
  onLog?: (entry: DownloadLogEntry) => void;
  signal?: AbortSignal;
  chunkSize?: number;
  maxRetries?: number;
  saveAs?: boolean;
  directoryHandle?: FileSystemDirectoryHandle | null;
  resumeState?: ResumeState;
  enableAdaptiveChunks?: boolean; // F8: adaptive chunk sizing
  enableParallelChunks?: boolean; // F7: parallel chunk downloads
  parallelChunkCount?: number;    // F7: number of parallel chunks (1-5, default 2)
}

/**
 * Sleep for a specified number of milliseconds, respecting abort signal
 */
function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new Error('Download cancelled'));
      return;
    }
    const timer = setTimeout(resolve, ms);
    const onAbort = () => {
      clearTimeout(timer);
      reject(new Error('Download cancelled'));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

/**
 * Check if an HTTP status code is retryable (server errors)
 */
function isRetryableStatus(status: number): boolean {
  return status === 502 || status === 503 || status === 504 || status === 500 || status === 408;
}

/**
 * Fetch with timeout — wraps fetch to add a per-request timeout
 */
async function fetchWithTimeout(
  url: string,
  options: RequestInit,
  timeoutMs: number
): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  const externalSignal = options.signal;
  if (externalSignal) {
    if (externalSignal.aborted) {
      clearTimeout(timeoutId);
      throw new Error('Download cancelled');
    }
    externalSignal.addEventListener('abort', () => controller.abort(), { once: true });
  }

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });
    clearTimeout(timeoutId);
    return response;
  } catch (err) {
    clearTimeout(timeoutId);
    if (controller.signal.aborted && !externalSignal?.aborted) {
      throw new Error('Chunk download timed out');
    }
    throw err;
  }
}

/**
 * Compute MD5 of an ArrayBuffer using spark-md5
 */
function computeMD5(buffer: ArrayBuffer): string {
  const spark = new SparkMD5.ArrayBuffer();
  spark.append(buffer);
  return spark.end();
}

/**
 * F5: Check if the server is reachable via nodeinfo.fn
 */
export async function checkServerHealth(
  signal?: AbortSignal,
  onLog?: (entry: DownloadLogEntry) => void,
): Promise<boolean> {
  try {
    const url = buildUrl('/cass/nodeinfo.fn');
    onLog?.({ type: 'info', message: 'Health check: contacting server...', timestamp: Date.now() });
    const response = await fetchWithTimeout(url, {
      method: 'GET',
      credentials: 'include',
      signal,
    }, HEALTH_CHECK_TIMEOUT);
    if (response.ok) {
      onLog?.({ type: 'info', message: 'Health check: server is available', timestamp: Date.now() });
    } else {
      onLog?.({ type: 'warning', message: `Health check: server responded with HTTP ${response.status}`, timestamp: Date.now() });
    }
    return response.ok;
  } catch {
    onLog?.({ type: 'warning', message: 'Health check: server unreachable', timestamp: Date.now() });
    return false;
  }
}

/**
 * F5: Wait for server to become available, retrying with 10s intervals.
 */
export async function waitForServer(
  maxRetries: number,
  signal?: AbortSignal,
  onStatus?: (message: string) => void,
  onLog?: (entry: DownloadLogEntry) => void,
): Promise<boolean> {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    if (signal?.aborted) return false;

    const msg = `Server unreachable — retrying in ${HEALTH_CHECK_RETRY_DELAY / 1000}s (attempt ${attempt}/${maxRetries})`;
    onStatus?.(msg);
    onLog?.({ type: 'warning', message: msg, timestamp: Date.now() });

    await sleep(HEALTH_CHECK_RETRY_DELAY, signal);

    if (signal?.aborted) return false;

    const healthy = await checkServerHealth(signal);
    if (healthy) {
      onLog?.({ type: 'info', message: 'Server is available again.', timestamp: Date.now() });
      return true;
    }
  }
  return false;
}

/**
 * Download a single chunk with retry logic for server errors, network failures,
 * and optional chunk checksum verification (F6).
 * Returns { blob, hadRetry } so the caller knows if retries occurred.
 */
async function downloadChunk(
  url: string,
  offset: number,
  chunkSize: number,
  chunkIndex: number,
  totalChunks: number,
  expectedTotalSize: number,
  maxRetries: number,
  signal?: AbortSignal,
  onRetry?: (attempt: number, error: string) => void,
  onLog?: (entry: DownloadLogEntry) => void,
): Promise<{ blob: Blob; hadRetry: boolean }> {
  const chunkUrl = `${url}&filechunk_size=${chunkSize}&filechunk_offset=${offset}`;
  let lastError: Error | null = null;
  let hadRetry = false;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const response = await fetchWithTimeout(chunkUrl, {
        method: 'GET',
        credentials: 'include',
        signal,
      }, CHUNK_TIMEOUT);

      if (!response.ok) {
        if (isRetryableStatus(response.status)) {
          lastError = new Error(`HTTP ${response.status} for chunk ${chunkIndex + 1}/${totalChunks}`);

          if (attempt < maxRetries) {
            hadRetry = true;
            const delay = RETRY_DELAY * Math.pow(2, attempt);
            onRetry?.(attempt + 1, `HTTP ${response.status} - Retrying chunk ${chunkIndex + 1} in ${delay}ms...`);
            await sleep(delay, signal);
            continue;
          }
        } else {
          throw new Error(`Error downloading chunk ${chunkIndex + 1}. Status code: ${response.status}`);
        }
      } else {
        const blob = await response.blob();

        // Validate chunk size — last chunk may be smaller
        const isLastChunk = chunkIndex === totalChunks - 1;
        const expectedSize = isLastChunk
          ? expectedTotalSize - offset
          : chunkSize;

        if (blob.size === 0) {
          throw new Error(`Empty response for chunk ${chunkIndex + 1}/${totalChunks}`);
        }

        if (!isLastChunk && blob.size !== expectedSize) {
          lastError = new Error(`Chunk ${chunkIndex + 1} size mismatch: got ${blob.size}, expected ${expectedSize}`);
          if (attempt < maxRetries) {
            hadRetry = true;
            const delay = RETRY_DELAY * Math.pow(2, attempt);
            onRetry?.(attempt + 1, `Size mismatch on chunk ${chunkIndex + 1} - Retrying in ${delay}ms...`);
            await sleep(delay, signal);
            continue;
          }
        }

        // F6: Chunk checksum verification (optional — only if header present)
        const serverChunkMD5 = response.headers.get('X-Chunk-MD5');
        if (serverChunkMD5) {
          const arrayBuffer = await blob.arrayBuffer();
          const clientChunkMD5 = computeMD5(arrayBuffer);
          if (clientChunkMD5 !== serverChunkMD5) {
            onLog?.({ type: 'warning', message: `Chunk ${chunkIndex + 1} MD5 mismatch: server=${serverChunkMD5} client=${clientChunkMD5}`, timestamp: Date.now() });
            lastError = new Error(`Chunk ${chunkIndex + 1} checksum mismatch`);
            if (attempt < maxRetries) {
              hadRetry = true;
              const delay = RETRY_DELAY * Math.pow(2, attempt);
              onRetry?.(attempt + 1, `Checksum mismatch on chunk ${chunkIndex + 1} - Retrying in ${delay}ms...`);
              await sleep(delay, signal);
              continue;
            }
          } else {
            onLog?.({ type: 'info', message: `Chunk ${chunkIndex + 1}/${totalChunks} checksum verified (MD5 match)`, timestamp: Date.now() });
          }
          // Return a new blob from the already-read buffer to avoid double-read
          return { blob: new Blob([arrayBuffer]), hadRetry };
        } else {
          onLog?.({ type: 'info', message: `Chunk ${chunkIndex + 1}/${totalChunks} downloaded (checksum skipped — no X-Chunk-MD5 header)`, timestamp: Date.now() });
        }

        return { blob, hadRetry };
      }
    } catch (error) {
      if (signal?.aborted) {
        throw new Error('Download cancelled');
      }

      lastError = error as Error;

      if (attempt < maxRetries) {
        hadRetry = true;
        const delay = RETRY_DELAY * Math.pow(2, attempt);
        const errMsg = (error as Error).message || 'Network error';
        onRetry?.(attempt + 1, `${errMsg} on chunk ${chunkIndex + 1} - Retrying in ${delay}ms...`);
        await sleep(delay, signal);
        continue;
      }
    }
  }

  throw lastError || new Error(`Failed to download chunk ${chunkIndex + 1} after ${maxRetries} retries`);
}

/**
 * Download file directly (for files smaller than chunk size) with retry logic
 */
async function downloadFileDirect(
  file: File,
  options: DownloadOptions
): Promise<void> {
  const uuid = localStorage.getItem('uuid');
  const url = buildUrl(`${file.file_path_webapp}&uuid=${uuid}`);
  const maxRetries = options.maxRetries ?? DEFAULT_MAX_RETRIES;
  const log = options.onLog;

  let lastError: Error | null = null;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      if (options.signal?.aborted) {
        throw new Error('Download cancelled');
      }

      if (attempt > 0) {
        const delay = RETRY_DELAY * Math.pow(2, attempt - 1);
        log?.({ type: 'warning', message: `Retrying download (attempt ${attempt}/${maxRetries}) in ${delay}ms...`, timestamp: Date.now() });
        await sleep(delay, options.signal);
      }

      let writable: FileSystemWritableFileStream | null = null;
      const useSaveAs = options.saveAs !== false;

      if (options.directoryHandle) {
        try {
          const fileHandle = await options.directoryHandle.getFileHandle(file.name, { create: true });
          writable = await fileHandle.createWritable();
        } catch (err) {
          options.onError?.(new Error(`Cannot write to folder: ${(err as Error).message}`));
          return;
        }
      } else if (attempt === 0 && useSaveAs && 'showSaveFilePicker' in window) {
        try {
          const fileExtension = file.name.split('.').pop();
          const handle = await (window as any).showSaveFilePicker({
            suggestedName: file.name,
            types: [{
              description: 'Files',
              accept: { 'application/octet-stream': [`.${fileExtension}`] }
            }]
          });
          writable = await handle.createWritable();
        } catch (err) {
          if ((err as Error).name === 'AbortError') {
            options.onError?.(new Error('Download cancelled'));
            return;
          }
        }
      }

      const response = await fetchWithTimeout(url, {
        method: 'GET',
        credentials: 'include',
        signal: options.signal,
      }, CHUNK_TIMEOUT * 3);

      if (!response.ok) {
        if (isRetryableStatus(response.status)) {
          lastError = new Error(`HTTP ${response.status}`);
          if (writable) await writable.abort();
          continue;
        }
        throw new Error(`Download failed with status: ${response.status}`);
      }

      const contentLength = parseInt(response.headers.get('content-length') || '0', 10);
      const totalBytes = contentLength || file.file_size;
      const reader = response.body?.getReader();

      if (!reader) {
        throw new Error('Response body is not readable');
      }

      // F10: Incremental MD5 hashing
      const spark = new SparkMD5.ArrayBuffer();

      const startTime = Date.now();
      let downloadedBytes = 0;
      const chunks: Uint8Array[] = [];

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        downloadedBytes += value.length;
        spark.append(value.buffer);

        if (writable) {
          await writable.write(value);
        } else {
          chunks.push(value);
        }

        const elapsedTimeSec = (Date.now() - startTime) / 1000;
        const speedKBps = elapsedTimeSec > 0 ? (downloadedBytes / 1024) / elapsedTimeSec : 0;
        const remainingBytes = totalBytes - downloadedBytes;
        const estimatedTimeSec = speedKBps > 0 ? remainingBytes / (speedKBps * 1024) : 0;

        options.onProgress?.({
          percentage: totalBytes > 0 ? Math.round((downloadedBytes / totalBytes) * 100) : 0,
          downloadedBytes,
          totalBytes,
          speedKBps,
          estimatedTimeRemaining: estimatedTimeSec,
          elapsedTime: elapsedTimeSec,
          errorCount: 0,
          retryCount: attempt,
          currentStatus: 'Downloading...',
        });
      }

      if (writable) {
        await writable.close();
      } else {
        const blob = new Blob(chunks);
        const blobUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = file.name;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(blobUrl);
      }

      // F10: Verify MD5 integrity
      const computedMD5 = spark.end();
      const expectedMD5 = file.nickname;
      if (expectedMD5 && computedMD5 !== expectedMD5) {
        log?.({ type: 'error', message: `Integrity check failed: computed MD5 ${computedMD5} does not match expected ${expectedMD5}`, timestamp: Date.now() });
        options.onError?.(new Error(`Integrity check failed: file is corrupted (MD5 mismatch)`));
        return;
      }
      if (expectedMD5 && computedMD5 === expectedMD5) {
        log?.({ type: 'success', message: `Integrity verified: MD5 ${computedMD5}`, timestamp: Date.now() });
      }

      const totalElapsedSec = (Date.now() - startTime) / 1000;
      const avgSpeedKBps = totalElapsedSec > 0 ? (downloadedBytes / 1024) / totalElapsedSec : 0;
      options.onProgress?.({
        percentage: 100,
        downloadedBytes,
        totalBytes,
        speedKBps: avgSpeedKBps,
        estimatedTimeRemaining: 0,
        elapsedTime: totalElapsedSec,
        errorCount: 0,
        retryCount: attempt,
        currentStatus: 'Complete',
        averageSpeedKBps: avgSpeedKBps,
      });

      options.onComplete?.();
      return;

    } catch (error) {
      if (options.signal?.aborted) {
        options.onError?.(new Error('Download cancelled'));
        return;
      }
      lastError = error as Error;
      if (attempt === maxRetries) {
        log?.({ type: 'error', message: `Download failed after ${maxRetries} retries: ${lastError.message}`, timestamp: Date.now() });
        options.onError?.(lastError);
      }
    }
  }
}

/**
 * Download file in chunks sequentially with retry, resume, adaptive sizing,
 * chunk checksum verification, and MD5 integrity check.
 */
async function downloadFileByChunksSequential(
  file: File,
  options: DownloadOptions
): Promise<void> {
  const uuid = localStorage.getItem('uuid');
  const baseUrl = buildUrl(`${file.file_path_webapp}&uuid=${uuid}`);

  const initialChunkSize = options.chunkSize || DEFAULT_CHUNK_SIZE;
  const maxRetries = options.maxRetries ?? DEFAULT_MAX_RETRIES;
  const totalSize = file.file_size;
  let downloadedBytes = 0;
  let totalErrors = 0;
  let totalRetries = 0;
  const log = options.onLog;
  const enableAdaptive = options.enableAdaptiveChunks ?? false;

  // F8: Adaptive chunk sizing state
  let currentChunkSize = initialChunkSize;
  let consecutiveRetries = 0;
  let consecutiveSuccesses = 0;

  // Resume state
  const resume = options.resumeState;
  let fileHandle: FileSystemFileHandle | undefined = resume?.fileHandle;
  let inMemoryChunks: Blob[] = resume?.inMemoryChunks ? [...resume.inMemoryChunks] : [];

  // F10: Incremental MD5 hasher — restore state on resume
  const spark = new SparkMD5.ArrayBuffer();
  if (resume?.md5State) {
    try {
      spark.setState(JSON.parse(resume.md5State));
    } catch {
      log?.({ type: 'warning', message: 'Could not restore MD5 state from resume — integrity check may be inaccurate', timestamp: Date.now() });
    }
  }

  if (resume && resume.resumeOffset > 0) {
    downloadedBytes = resume.resumeOffset;
    log?.({ type: 'info', message: `Resuming from ${formatFileSize(downloadedBytes)} already downloaded`, timestamp: Date.now() });
  }

  const hasFileSystemAPI = 'showSaveFilePicker' in window;
  let writable: FileSystemWritableFileStream | null = null;

  try {
    const useSaveAs = options.saveAs !== false;

    if (resume && fileHandle) {
      try {
        writable = await fileHandle.createWritable({ keepExistingData: true });
        await writable.seek(downloadedBytes);
      } catch (err) {
        log?.({ type: 'warning', message: `Could not reopen file for resume: ${(err as Error).message}. Restarting download.`, timestamp: Date.now() });
        downloadedBytes = 0;
        inMemoryChunks = [];
        fileHandle = undefined;
        writable = null;
      }
    }

    if (!writable && !resume) {
      if (options.directoryHandle) {
        try {
          fileHandle = await options.directoryHandle.getFileHandle(file.name, { create: true });
          writable = await fileHandle.createWritable();
        } catch (err) {
          options.onError?.(new Error(`Cannot write to folder: ${(err as Error).message}`));
          return;
        }
      } else if (useSaveAs && hasFileSystemAPI) {
        try {
          const fileExtension = file.name.split('.').pop();
          const handle = await (window as any).showSaveFilePicker({
            suggestedName: file.name,
            types: [{
              description: 'Files',
              accept: { 'application/octet-stream': [`.${fileExtension}`] }
            }]
          });
          fileHandle = handle;
          writable = await handle.createWritable();
        } catch (err) {
          if ((err as Error).name === 'AbortError') {
            options.onError?.(new Error('Download cancelled'));
            return;
          }
        }
      }
    }

    const useIncrementalWrite = writable !== null;
    const startTime = Date.now();

    let totalChunks = Math.ceil(totalSize / currentChunkSize);

    if (downloadedBytes === 0) {
      log?.({ type: 'info', message: `Starting chunked download: ${totalChunks} chunks of ${(currentChunkSize / (1024 * 1024)).toFixed(2)} MB`, timestamp: Date.now() });
    }

    let currentOffset = downloadedBytes;
    let chunkIndex = downloadedBytes > 0 ? Math.floor(downloadedBytes / currentChunkSize) : 0;

    while (currentOffset < totalSize) {
      if (options.signal?.aborted) {
        if (writable) {
          try { await writable.close(); } catch { /* ignore */ }
        }
        const resumeState: ResumeState = {
          resumeOffset: downloadedBytes,
          completedChunks: chunkIndex,
          totalChunks,
          fileHandle,
          inMemoryChunks: useIncrementalWrite ? undefined : inMemoryChunks,
          md5State: JSON.stringify(spark.getState()),
        };
        options.onError?.(new Error('Download cancelled'), resumeState);
        return;
      }

      const remainingBytes = totalSize - currentOffset;
      const thisChunkSize = Math.min(currentChunkSize, remainingBytes);
      totalChunks = chunkIndex + Math.ceil(remainingBytes / currentChunkSize);

      try {
        const { blob: chunk, hadRetry: chunkHadRetry } = await downloadChunk(
          baseUrl, currentOffset, thisChunkSize, chunkIndex, totalChunks, totalSize,
          maxRetries, options.signal,
          (retryAttempt, errorMsg) => {
            totalRetries++;
            log?.({ type: 'warning', message: errorMsg, timestamp: Date.now() });

            const elapsedTimeSec = (Date.now() - startTime) / 1000;
            const speedKBps = downloadedBytes > 0 ? (downloadedBytes / 1024) / elapsedTimeSec : 0;
            const remBytes = totalSize - downloadedBytes;
            const estimatedTimeSec = speedKBps > 0 ? remBytes / (speedKBps * 1024) : 0;

            options.onProgress?.({
              percentage: Math.min(100, Math.round((downloadedBytes / totalSize) * 100)),
              downloadedBytes,
              totalBytes: totalSize,
              speedKBps,
              estimatedTimeRemaining: estimatedTimeSec,
              elapsedTime: elapsedTimeSec,
              errorCount: totalErrors,
              retryCount: totalRetries,
              lastError: errorMsg,
              currentStatus: `Retrying (attempt ${retryAttempt}/${maxRetries})...`,
              currentChunkSizeMB: currentChunkSize / (1024 * 1024),
            });
          },
          log,
        );

        // F10: Feed chunk to MD5 hasher before writing to disk
        const arrayBuffer = await chunk.arrayBuffer();
        spark.append(arrayBuffer);

        if (useIncrementalWrite && writable) {
          await writable.write(chunk);
        } else {
          inMemoryChunks.push(chunk);
        }

        downloadedBytes += chunk.size;
        currentOffset += chunk.size;
        chunkIndex++;

        log?.({ type: 'info', message: `Chunk ${chunkIndex}/${totalChunks} downloaded (${(chunk.size / 1024).toFixed(0)} KB)`, timestamp: Date.now() });

        // F8: Adaptive chunk sizing
        if (enableAdaptive) {
          if (chunkHadRetry) {
            consecutiveSuccesses = 0;
            consecutiveRetries++;
            if (consecutiveRetries >= ADAPTIVE_RETRY_THRESHOLD) {
              const newSize = Math.max(ADAPTIVE_MIN_CHUNK_SIZE, currentChunkSize / 2);
              if (newSize !== currentChunkSize) {
                currentChunkSize = newSize;
                log?.({ type: 'info', message: `Adaptive: reduced chunk size to ${(currentChunkSize / (1024 * 1024)).toFixed(2)} MB`, timestamp: Date.now() });
              }
              consecutiveRetries = 0;
            }
          } else {
            consecutiveRetries = 0;
            consecutiveSuccesses++;
            if (consecutiveSuccesses >= ADAPTIVE_SUCCESS_THRESHOLD) {
              const maxChunkSize = initialChunkSize * ADAPTIVE_MAX_MULTIPLIER;
              const newSize = Math.min(maxChunkSize, currentChunkSize * 2);
              if (newSize !== currentChunkSize) {
                currentChunkSize = newSize;
                log?.({ type: 'info', message: `Adaptive: increased chunk size to ${(currentChunkSize / (1024 * 1024)).toFixed(2)} MB`, timestamp: Date.now() });
              }
              consecutiveSuccesses = 0;
            }
          }
        }

        const elapsedTimeSec = (Date.now() - startTime) / 1000;
        const speedKBps = elapsedTimeSec > 0 ? ((downloadedBytes - (resume?.resumeOffset || 0)) / 1024) / elapsedTimeSec : 0;
        const remBytes = totalSize - downloadedBytes;
        const estimatedTimeSec = speedKBps > 0 ? remBytes / (speedKBps * 1024) : 0;

        options.onProgress?.({
          percentage: Math.min(100, Math.round((downloadedBytes / totalSize) * 100)),
          downloadedBytes,
          totalBytes: totalSize,
          speedKBps,
          estimatedTimeRemaining: estimatedTimeSec,
          elapsedTime: elapsedTimeSec,
          errorCount: totalErrors,
          retryCount: totalRetries,
          currentStatus: `Downloading chunk ${chunkIndex}/${totalChunks}...`,
          currentChunkSizeMB: currentChunkSize / (1024 * 1024),
        });
      } catch (chunkError) {
        totalErrors++;
        log?.({ type: 'error', message: `Chunk ${chunkIndex + 1} failed after ${maxRetries} retries: ${(chunkError as Error).message}`, timestamp: Date.now() });

        if (writable) {
          try { await writable.close(); } catch { /* ignore */ }
        }

        const resumeState: ResumeState = {
          resumeOffset: downloadedBytes,
          completedChunks: chunkIndex,
          totalChunks,
          fileHandle,
          inMemoryChunks: useIncrementalWrite ? undefined : inMemoryChunks,
          md5State: JSON.stringify(spark.getState()),
        };

        throw Object.assign(chunkError as Error, { resumeState });
      }
    }

    // All chunks downloaded
    if (useIncrementalWrite && writable) {
      await writable.close();
    } else if (inMemoryChunks.length > 0) {
      const finalBlob = new Blob(inMemoryChunks);
      const blobUrl = URL.createObjectURL(finalBlob);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = file.name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(blobUrl);
    }

    // F10: Verify MD5 integrity
    const computedMD5 = spark.end();
    const expectedMD5 = file.nickname;
    if (expectedMD5 && computedMD5 !== expectedMD5) {
      log?.({ type: 'error', message: `Integrity check failed: computed MD5 ${computedMD5} does not match expected ${expectedMD5}. File kept on disk for debugging.`, timestamp: Date.now() });
      options.onError?.(new Error(`Integrity check failed: file is corrupted (MD5 mismatch). File kept on disk for debugging.`));
      return;
    }
    if (expectedMD5 && computedMD5 === expectedMD5) {
      log?.({ type: 'success', message: `Integrity verified: MD5 ${computedMD5}`, timestamp: Date.now() });
    }

    const totalElapsedSec = (Date.now() - startTime) / 1000;
    const avgSpeedKBps = totalElapsedSec > 0 ? (totalSize / 1024) / totalElapsedSec : 0;
    options.onProgress?.({
      percentage: 100,
      downloadedBytes: totalSize,
      totalBytes: totalSize,
      speedKBps: avgSpeedKBps,
      estimatedTimeRemaining: 0,
      elapsedTime: totalElapsedSec,
      errorCount: totalErrors,
      retryCount: totalRetries,
      currentStatus: 'Complete',
      averageSpeedKBps: avgSpeedKBps,
      currentChunkSizeMB: currentChunkSize / (1024 * 1024),
    });

    log?.({ type: 'success', message: `Download complete: ${formatFileSize(totalSize)} in ${formatTimeRemaining(totalElapsedSec)}`, timestamp: Date.now() });
    options.onComplete?.();
  } catch (err) {
    const error = err as Error & { resumeState?: ResumeState };
    log?.({ type: 'error', message: `Download failed: ${error.message}`, timestamp: Date.now() });
    options.onError?.(error, error.resumeState);
  }
}

/**
 * F7: Download file in chunks with parallel downloads.
 * Uses a fixed chunk size (adaptive disabled per Q7.2).
 * Tracks completed chunks as a Set for resume (per Q7.1).
 * Writes chunks to disk in order using seek offsets.
 */
async function downloadFileByChunksParallel(
  file: File,
  options: DownloadOptions
): Promise<void> {
  const uuid = localStorage.getItem('uuid');
  const baseUrl = buildUrl(`${file.file_path_webapp}&uuid=${uuid}`);

  const chunkSize = options.chunkSize || DEFAULT_CHUNK_SIZE;
  const maxRetries = options.maxRetries ?? DEFAULT_MAX_RETRIES;
  const parallelCount = options.parallelChunkCount ?? 2;
  const totalSize = file.file_size;
  const totalChunks = Math.ceil(totalSize / chunkSize);
  let downloadedBytes = 0;
  let totalErrors = 0;
  let totalRetries = 0;
  const log = options.onLog;

  // Resume state — track which chunks are done
  const resume = options.resumeState;
  const completedChunkSet = new Set<number>(resume?.completedChunkSet || []);
  let fileHandle: FileSystemFileHandle | undefined = resume?.fileHandle;

  if (resume && completedChunkSet.size > 0) {
    downloadedBytes = completedChunkSet.size * chunkSize;
    // Last chunk may be smaller — adjust
    if (completedChunkSet.has(totalChunks - 1)) {
      downloadedBytes = (completedChunkSet.size - 1) * chunkSize + (totalSize % chunkSize || chunkSize);
    }
    log?.({ type: 'info', message: `Resuming parallel download: ${completedChunkSet.size}/${totalChunks} chunks already downloaded`, timestamp: Date.now() });
  }

  const hasFileSystemAPI = 'showSaveFilePicker' in window;
  let writable: FileSystemWritableFileStream | null = null;
  // For parallel mode with FS API, we need random-access writes via seek
  // For in-memory fallback, we use an array indexed by chunk number
  const inMemoryChunkMap: Map<number, Blob> = new Map();

  try {
    const useSaveAs = options.saveAs !== false;

    if (resume && fileHandle) {
      try {
        writable = await fileHandle.createWritable({ keepExistingData: true });
      } catch (err) {
        log?.({ type: 'warning', message: `Could not reopen file for resume: ${(err as Error).message}. Restarting download.`, timestamp: Date.now() });
        downloadedBytes = 0;
        completedChunkSet.clear();
        fileHandle = undefined;
        writable = null;
      }
    }

    if (!writable && !resume) {
      if (options.directoryHandle) {
        try {
          fileHandle = await options.directoryHandle.getFileHandle(file.name, { create: true });
          writable = await fileHandle.createWritable();
        } catch (err) {
          options.onError?.(new Error(`Cannot write to folder: ${(err as Error).message}`));
          return;
        }
      } else if (useSaveAs && hasFileSystemAPI) {
        try {
          const fileExtension = file.name.split('.').pop();
          const handle = await (window as any).showSaveFilePicker({
            suggestedName: file.name,
            types: [{
              description: 'Files',
              accept: { 'application/octet-stream': [`.${fileExtension}`] }
            }]
          });
          fileHandle = handle;
          writable = await handle.createWritable();
        } catch (err) {
          if ((err as Error).name === 'AbortError') {
            options.onError?.(new Error('Download cancelled'));
            return;
          }
        }
      }
    }

    const useIncrementalWrite = writable !== null;
    const startTime = Date.now();

    log?.({ type: 'info', message: `Starting parallel download: ${totalChunks} chunks of ${(chunkSize / (1024 * 1024)).toFixed(2)} MB, ${parallelCount} parallel`, timestamp: Date.now() });

    // Build list of chunks that still need downloading
    const pendingChunks: number[] = [];
    for (let i = 0; i < totalChunks; i++) {
      if (!completedChunkSet.has(i)) {
        pendingChunks.push(i);
      }
    }

    // Process chunks in batches of parallelCount
    let pendingIndex = 0;

    while (pendingIndex < pendingChunks.length) {
      if (options.signal?.aborted) break;

      // Take next batch
      const batch = pendingChunks.slice(pendingIndex, pendingIndex + parallelCount);
      pendingIndex += batch.length;

      // Download batch in parallel
      log?.({ type: 'info', message: `Requesting chunks ${batch.map(c => c + 1).join(', ')} of ${totalChunks} in parallel`, timestamp: Date.now() });
      const results = await Promise.allSettled(
        batch.map(async (ci) => {
          const offset = ci * chunkSize;
          const thisChunkSize = Math.min(chunkSize, totalSize - offset);

          const { blob } = await downloadChunk(
            baseUrl, offset, thisChunkSize, ci, totalChunks, totalSize,
            maxRetries, options.signal,
            (_retryAttempt, errorMsg) => {
              totalRetries++;
              log?.({ type: 'warning', message: errorMsg, timestamp: Date.now() });
            },
            log,
          );

          return { chunkIndex: ci, blob, offset };
        })
      );

      // Process results — write completed chunks, track failures
      let batchFailed = false;
      for (const result of results) {
        if (options.signal?.aborted) break;

        if (result.status === 'fulfilled') {
          const { chunkIndex: ci, blob, offset } = result.value;

          // Write to disk at the correct offset
          if (useIncrementalWrite && writable) {
            await writable.seek(offset);
            await writable.write(blob);
          } else {
            inMemoryChunkMap.set(ci, blob);
          }

          completedChunkSet.add(ci);
          downloadedBytes += blob.size;

          log?.({ type: 'info', message: `Chunk ${ci + 1}/${totalChunks} downloaded (${(blob.size / 1024).toFixed(0)} KB)`, timestamp: Date.now() });
        } else {
          totalErrors++;
          batchFailed = true;
          log?.({ type: 'error', message: `Chunk failed: ${result.reason?.message || 'Unknown error'}`, timestamp: Date.now() });
        }
      }

      // Report progress
      const elapsedTimeSec = (Date.now() - startTime) / 1000;
      const resumeDownloaded = resume ? (resume.completedChunkSet?.length || 0) * chunkSize : 0;
      const speedKBps = elapsedTimeSec > 0 ? ((downloadedBytes - resumeDownloaded) / 1024) / elapsedTimeSec : 0;
      const remBytes = totalSize - downloadedBytes;
      const estimatedTimeSec = speedKBps > 0 ? remBytes / (speedKBps * 1024) : 0;

      options.onProgress?.({
        percentage: Math.min(100, Math.round((downloadedBytes / totalSize) * 100)),
        downloadedBytes,
        totalBytes: totalSize,
        speedKBps,
        estimatedTimeRemaining: estimatedTimeSec,
        elapsedTime: elapsedTimeSec,
        errorCount: totalErrors,
        retryCount: totalRetries,
        currentStatus: `Downloaded ${completedChunkSet.size}/${totalChunks} chunks (${parallelCount} parallel)...`,
      });

      // If any chunk in the batch failed, stop and report for resume
      if (batchFailed) {
        if (writable) {
          try { await writable.close(); } catch { /* ignore */ }
        }

        const resumeState: ResumeState = {
          resumeOffset: downloadedBytes,
          completedChunks: completedChunkSet.size,
          totalChunks,
          fileHandle,
          completedChunkSet: Array.from(completedChunkSet),
        };

        throw Object.assign(
          new Error(`Download failed: ${totalErrors} chunk(s) failed after retries`),
          { resumeState }
        );
      }
    }

    if (options.signal?.aborted) {
      if (writable) {
        try { await writable.close(); } catch { /* ignore */ }
      }
      const resumeState: ResumeState = {
        resumeOffset: downloadedBytes,
        completedChunks: completedChunkSet.size,
        totalChunks,
        fileHandle,
        completedChunkSet: Array.from(completedChunkSet),
      };
      options.onError?.(new Error('Download cancelled'), resumeState);
      return;
    }

    // All chunks downloaded
    if (useIncrementalWrite && writable) {
      await writable.close();
    } else if (inMemoryChunkMap.size > 0) {
      // Combine in-memory chunks in order
      const orderedBlobs: Blob[] = [];
      for (let i = 0; i < totalChunks; i++) {
        const blob = inMemoryChunkMap.get(i);
        if (blob) orderedBlobs.push(blob);
      }
      const finalBlob = new Blob(orderedBlobs);
      const blobUrl = URL.createObjectURL(finalBlob);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = file.name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(blobUrl);
    }

    // F10: For parallel mode, we need to read the file back to compute MD5
    // since chunks may have been written out of order.
    // If we have a fileHandle, read it back; otherwise hash in-memory chunks in order.
    let computedMD5 = '';
    if (inMemoryChunkMap.size > 0) {
      const spark = new SparkMD5.ArrayBuffer();
      for (let i = 0; i < totalChunks; i++) {
        const blob = inMemoryChunkMap.get(i);
        if (blob) {
          spark.append(await blob.arrayBuffer());
        }
      }
      computedMD5 = spark.end();
    } else if (fileHandle) {
      try {
        const fileObj = await fileHandle.getFile();
        const spark = new SparkMD5.ArrayBuffer();
        const reader = fileObj.stream().getReader();
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          spark.append(value.buffer);
        }
        computedMD5 = spark.end();
      } catch {
        log?.({ type: 'warning', message: 'Could not read file back for integrity check', timestamp: Date.now() });
      }
    }

    const expectedMD5 = file.nickname;
    if (expectedMD5 && computedMD5 && computedMD5 !== expectedMD5) {
      log?.({ type: 'error', message: `Integrity check failed: computed MD5 ${computedMD5} does not match expected ${expectedMD5}. File kept on disk for debugging.`, timestamp: Date.now() });
      options.onError?.(new Error(`Integrity check failed: file is corrupted (MD5 mismatch). File kept on disk for debugging.`));
      return;
    }
    if (expectedMD5 && computedMD5 && computedMD5 === expectedMD5) {
      log?.({ type: 'success', message: `Integrity verified: MD5 ${computedMD5}`, timestamp: Date.now() });
    }

    const totalElapsedSec = (Date.now() - startTime) / 1000;
    const avgSpeedKBps = totalElapsedSec > 0 ? (totalSize / 1024) / totalElapsedSec : 0;
    options.onProgress?.({
      percentage: 100,
      downloadedBytes: totalSize,
      totalBytes: totalSize,
      speedKBps: avgSpeedKBps,
      estimatedTimeRemaining: 0,
      elapsedTime: totalElapsedSec,
      errorCount: totalErrors,
      retryCount: totalRetries,
      currentStatus: 'Complete',
      averageSpeedKBps: avgSpeedKBps,
    });

    log?.({ type: 'success', message: `Download complete: ${formatFileSize(totalSize)} in ${formatTimeRemaining(totalElapsedSec)}`, timestamp: Date.now() });
    options.onComplete?.();
  } catch (err) {
    const error = err as Error & { resumeState?: ResumeState };
    log?.({ type: 'error', message: `Download failed: ${error.message}`, timestamp: Date.now() });
    options.onError?.(error, error.resumeState);
  }
}

/**
 * Download file with automatic chunking for large files.
 * Routes to sequential or parallel based on options.
 */
export async function downloadFile(
  file: File,
  options: DownloadOptions = {}
): Promise<void> {
  const chunkSize = options.chunkSize || DEFAULT_CHUNK_SIZE;
  if (file.file_size > chunkSize) {
    if (options.enableParallelChunks) {
      await downloadFileByChunksParallel(file, options);
    } else {
      await downloadFileByChunksSequential(file, options);
    }
  } else {
    await downloadFileDirect(file, options);
  }
}

/**
 * Format speed for display
 */
export function formatSpeed(speedKBps: number): string {
  if (speedKBps < 1024) {
    return `${speedKBps.toFixed(1)} KB/s`;
  } else {
    return `${(speedKBps / 1024).toFixed(1)} MB/s`;
  }
}

/**
 * Format time remaining for display
 */
export function formatTimeRemaining(seconds: number): string {
  if (seconds < 60) {
    return `${Math.round(seconds)}s`;
  } else if (seconds < 3600) {
    const mins = Math.floor(seconds / 60);
    const secs = Math.round(seconds % 60);
    return `${mins}m ${secs}s`;
  } else {
    const hours = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    return `${hours}h ${mins}m`;
  }
}

/**
 * Format file size for display
 */
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}
