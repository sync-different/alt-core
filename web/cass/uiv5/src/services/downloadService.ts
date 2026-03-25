/**
 * File Download Service
 * Handles chunked file downloads with progress tracking and retry logic
 */

import type { File } from '../types/models';
import { buildUrl } from '../utils/urlHelper';

const DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024; // 10MB
const DEFAULT_MAX_RETRIES = 3; // Maximum retry attempts per chunk
const RETRY_DELAY = 1000; // Initial retry delay in ms (will increase exponentially)

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
}

export interface DownloadLogEntry {
  type: 'info' | 'warning' | 'error' | 'success';
  message: string;
  timestamp: number;
}

export interface DownloadOptions {
  onProgress?: (progress: DownloadProgress) => void;
  onComplete?: () => void;
  onError?: (error: Error) => void;
  onLog?: (entry: DownloadLogEntry) => void;
  signal?: AbortSignal;
  chunkSize?: number;
  maxRetries?: number;
  saveAs?: boolean; // true = show file picker, false = save to default downloads folder
  directoryHandle?: FileSystemDirectoryHandle | null; // pre-selected download folder
}

/**
 * Sleep for a specified number of milliseconds
 */
function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Download a single chunk with retry logic for 502 errors
 */
async function downloadChunk(
  url: string,
  offset: number,
  chunkSize: number,
  chunkIndex: number,
  maxRetries: number,
  signal?: AbortSignal,
  onRetry?: (attempt: number, error: string) => void
): Promise<Blob> {
  const chunkUrl = `${url}&filechunk_size=${chunkSize}&filechunk_offset=${offset}`;
  let lastError: Error | null = null;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const response = await fetch(chunkUrl, {
        method: 'GET',
        credentials: 'include',
        signal,
      });

      if (!response.ok) {
        // Check if it's a 502 error (Bad Gateway)
        if (response.status === 502) {
          lastError = new Error(`HTTP 502 Bad Gateway for chunk ${chunkIndex + 1}`);

          // If we haven't exhausted retries, wait and retry
          if (attempt < maxRetries) {
            const delay = RETRY_DELAY * Math.pow(2, attempt); // Exponential backoff
            onRetry?.(attempt + 1, `HTTP 502 - Retrying chunk ${chunkIndex + 1} in ${delay}ms...`);
            await sleep(delay);
            continue; // Retry
          }
        } else {
          // For other errors, fail immediately
          throw new Error(`Error downloading chunk ${chunkIndex + 1}. Status code: ${response.status}`);
        }
      } else {
        // Success
        return response.blob();
      }
    } catch (error) {
      // Check if it was aborted
      if (signal?.aborted) {
        throw new Error('Download cancelled');
      }

      // Network error or other fetch error
      lastError = error as Error;

      if (attempt < maxRetries) {
        const delay = RETRY_DELAY * Math.pow(2, attempt);
        onRetry?.(attempt + 1, `Network error on chunk ${chunkIndex + 1} - Retrying in ${delay}ms...`);
        await sleep(delay);
        continue; // Retry
      }
    }
  }

  // If we get here, all retries failed
  throw lastError || new Error(`Failed to download chunk ${chunkIndex + 1} after ${maxRetries} retries`);
}

/**
 * Download file directly (for files < 10MB)
 */
async function downloadFileDirect(
  file: File,
  options: DownloadOptions
): Promise<void> {
  const uuid = localStorage.getItem('uuid');
  const url = buildUrl(`${file.file_path_webapp}&uuid=${uuid}`);

  try {
    // Determine how to write the file
    let writable: FileSystemWritableFileStream | null = null;
    const useSaveAs = options.saveAs !== false; // default true

    if (options.directoryHandle) {
      // Write directly to pre-selected folder
      try {
        const fileHandle = await options.directoryHandle.getFileHandle(file.name, { create: true });
        writable = await fileHandle.createWritable();
      } catch (err) {
        options.onError?.(new Error(`Cannot write to folder: ${(err as Error).message}`));
        return;
      }
    } else if (useSaveAs && 'showSaveFilePicker' in window) {
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
        // User cancelled file picker
        if ((err as Error).name === 'AbortError') {
          options.onError?.(new Error('Download cancelled'));
          return;
        }
      }
    }

    await new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('GET', url, true);
      xhr.responseType = 'blob';

      const startTime = Date.now();

      xhr.onprogress = (event) => {
        if (event.lengthComputable && options.onProgress) {
          const elapsedTimeSec = (Date.now() - startTime) / 1000;
          const speedKBps = (event.loaded / 1024) / elapsedTimeSec;
          const remainingBytes = event.total - event.loaded;
          const estimatedTimeSec = remainingBytes / (speedKBps * 1024);

          options.onProgress({
            percentage: Math.round((event.loaded / event.total) * 100),
            downloadedBytes: event.loaded,
            totalBytes: event.total,
            speedKBps,
            estimatedTimeRemaining: estimatedTimeSec,
            elapsedTime: elapsedTimeSec,
            errorCount: 0,
            retryCount: 0,
            currentStatus: 'Downloading...',
          });
        }
      };

      xhr.onload = async () => {
        if (xhr.status === 200) {
          const blob = xhr.response;

          if (writable) {
            await writable.write(blob);
            await writable.close();
          } else {
            const blobUrl = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = blobUrl;
            a.download = file.name;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(blobUrl);
          }

          // Report final progress with average speed
          const totalElapsedSec = (Date.now() - startTime) / 1000;
          const avgSpeedKBps = totalElapsedSec > 0 ? (file.file_size / 1024) / totalElapsedSec : 0;
          options.onProgress?.({
            percentage: 100,
            downloadedBytes: file.file_size,
            totalBytes: file.file_size,
            speedKBps: avgSpeedKBps,
            estimatedTimeRemaining: 0,
            elapsedTime: totalElapsedSec,
            errorCount: 0,
            retryCount: 0,
            currentStatus: 'Complete',
            averageSpeedKBps: avgSpeedKBps,
          });

          options.onComplete?.();
          resolve();
        } else {
          const error = new Error(`Download failed with status: ${xhr.status}`);
          options.onError?.(error);
          reject(error);
        }
      };

      xhr.onerror = () => {
        const error = new Error('Network error while downloading');
        options.onError?.(error);
        reject(error);
      };

      // Handle abort signal
      if (options.signal) {
        options.signal.addEventListener('abort', () => {
          xhr.abort();
          writable?.abort();
          const error = new Error('Download cancelled');
          options.onError?.(error);
          reject(error);
        });
      }

      xhr.send();
    });
  } catch (err) {
    options.onError?.(err as Error);
  }
}

/**
 * Download file in chunks (for files >= 10MB) with retry logic
 */
async function downloadFileByChunks(
  file: File,
  options: DownloadOptions
): Promise<void> {
  const uuid = localStorage.getItem('uuid');
  const baseUrl = buildUrl(`${file.file_path_webapp}&uuid=${uuid}`);

  const chunkSize = options.chunkSize || DEFAULT_CHUNK_SIZE;
  const maxRetries = options.maxRetries ?? DEFAULT_MAX_RETRIES;
  const totalSize = file.file_size;
  const totalChunks = Math.ceil(totalSize / chunkSize);
  const downloadedChunks: Blob[] = [];
  let downloadedBytes = 0;
  let totalErrors = 0;
  let totalRetries = 0;
  const log = options.onLog;

  try {
    // Determine how to write the file
    let writable: FileSystemWritableFileStream | null = null;
    const useSaveAs = options.saveAs !== false; // default true

    if (options.directoryHandle) {
      // Write directly to pre-selected folder
      try {
        const fileHandle = await options.directoryHandle.getFileHandle(file.name, { create: true });
        writable = await fileHandle.createWritable();
      } catch (err) {
        options.onError?.(new Error(`Cannot write to folder: ${(err as Error).message}`));
        return;
      }
    } else if (useSaveAs && 'showSaveFilePicker' in window) {
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
        // User cancelled file picker
        if ((err as Error).name === 'AbortError') {
          options.onError?.(new Error('Download cancelled'));
          return;
        }
      }
    }

    const startTime = Date.now();
    log?.({ type: 'info', message: `Starting chunked download: ${totalChunks} chunks of ${chunkSize / (1024 * 1024)} MB`, timestamp: Date.now() });

    // Download each chunk
    for (let i = 0; i < totalChunks; i++) {
      if (options.signal?.aborted) {
        if (writable) {
          await writable.abort();
        }
        options.onError?.(new Error('Download cancelled'));
        return;
      }

      const offset = i * chunkSize;

      // Download chunk with retry logic
      try {
        const chunk = await downloadChunk(
          baseUrl,
          offset,
          chunkSize,
          i,
          maxRetries,
          options.signal,
          (retryAttempt, errorMsg) => {
            // Report retry attempt
            totalErrors++;
            totalRetries++;
            log?.({ type: 'warning', message: errorMsg, timestamp: Date.now() });

            const elapsedTimeSec = (Date.now() - startTime) / 1000;
            const speedKBps = downloadedBytes > 0 ? (downloadedBytes / 1024) / elapsedTimeSec : 0;
            const remainingBytes = totalSize - downloadedBytes;
            const estimatedTimeSec = speedKBps > 0 ? remainingBytes / (speedKBps * 1024) : 0;
            const percentage = Math.min(100, Math.round((downloadedBytes / totalSize) * 100));

            if (options.onProgress) {
              options.onProgress({
                percentage,
                downloadedBytes,
                totalBytes: totalSize,
                speedKBps,
                estimatedTimeRemaining: estimatedTimeSec,
                elapsedTime: elapsedTimeSec,
                errorCount: totalErrors,
                retryCount: totalRetries,
                lastError: errorMsg,
                currentStatus: `Retrying (attempt ${retryAttempt}/${maxRetries})...`,
              });
            }
          }
        );

        downloadedChunks.push(chunk);
        downloadedBytes += chunk.size;
        log?.({ type: 'info', message: `Chunk ${i + 1}/${totalChunks} downloaded (${(chunk.size / 1024).toFixed(0)} KB)`, timestamp: Date.now() });

        // Calculate progress
        const elapsedTimeSec = (Date.now() - startTime) / 1000;
        const speedKBps = (downloadedBytes / 1024) / elapsedTimeSec;
        const remainingBytes = totalSize - downloadedBytes;
        const estimatedTimeSec = remainingBytes / (speedKBps * 1024);
        const percentage = Math.min(100, Math.round((downloadedBytes / totalSize) * 100));

        // Report progress with smooth animation
        if (options.onProgress) {
          options.onProgress({
            percentage,
            downloadedBytes,
            totalBytes: totalSize,
            speedKBps,
            estimatedTimeRemaining: estimatedTimeSec,
            elapsedTime: elapsedTimeSec,
            errorCount: totalErrors,
            retryCount: totalRetries,
            currentStatus: `Downloading chunk ${i + 1}/${totalChunks}...`,
          });
        }

        // Small delay for smooth progress animation
        await new Promise(resolve => setTimeout(resolve, 20));
      } catch (chunkError) {
        // Chunk download failed after all retries
        totalErrors++;
        log?.({ type: 'error', message: `Chunk ${i + 1}/${totalChunks} failed after ${maxRetries} retries: ${(chunkError as Error).message}`, timestamp: Date.now() });
        throw chunkError;
      }
    }

    // Combine all chunks into final blob
    const finalBlob = new Blob(downloadedChunks);

    if (writable) {
      await writable.write(finalBlob);
      await writable.close();
    } else {
      const blobUrl = URL.createObjectURL(finalBlob);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = file.name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(blobUrl);
    }

    // Report final progress with average speed
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

    log?.({ type: 'success', message: `Download complete: ${(totalSize / (1024 * 1024)).toFixed(1)} MB in ${formatTimeRemaining(totalElapsedSec)}`, timestamp: Date.now() });

    options.onComplete?.();
  } catch (err) {
    log?.({ type: 'error', message: `Download failed: ${(err as Error).message}`, timestamp: Date.now() });
    options.onError?.(err as Error);
  }
}

/**
 * Download file with automatic chunking for large files
 */
export async function downloadFile(
  file: File,
  options: DownloadOptions = {}
): Promise<void> {
  const chunkSize = options.chunkSize || DEFAULT_CHUNK_SIZE;
  if (file.file_size > chunkSize) {
    // Large file - download in chunks
    await downloadFileByChunks(file, options);
  } else {
    // Small file - direct download
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
