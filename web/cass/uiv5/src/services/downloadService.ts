/**
 * File Download Service
 * Handles chunked file downloads with progress tracking
 */

import type { File } from '../types/models';
import { buildUrl } from '../utils/urlHelper';

const CHUNK_SIZE = 10 * 1024 * 1024; // 10MB

export interface DownloadProgress {
  percentage: number;
  downloadedBytes: number;
  totalBytes: number;
  speedKBps: number;
  estimatedTimeRemaining: number;
}

export interface DownloadOptions {
  onProgress?: (progress: DownloadProgress) => void;
  onComplete?: () => void;
  onError?: (error: Error) => void;
  signal?: AbortSignal;
}

/**
 * Download a single chunk
 */
async function downloadChunk(
  url: string,
  offset: number,
  chunkSize: number,
  signal?: AbortSignal
): Promise<Blob> {
  const chunkUrl = `${url}&filechunk_size=${chunkSize}&filechunk_offset=${offset}`;

  const response = await fetch(chunkUrl, {
    method: 'GET',
    credentials: 'include',
    signal,
  });

  if (!response.ok) {
    throw new Error(`Error downloading chunk. Status code: ${response.status}`);
  }

  return response.blob();
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
    // Try to use File System Access API if available
    let writable: FileSystemWritableFileStream | null = null;

    if ('showSaveFilePicker' in window) {
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

        options.onComplete?.();
      } else {
        options.onError?.(new Error(`Download failed with status: ${xhr.status}`));
      }
    };

    xhr.onerror = () => {
      options.onError?.(new Error('Network error while downloading'));
    };

    // Handle abort signal
    if (options.signal) {
      options.signal.addEventListener('abort', () => {
        xhr.abort();
        writable?.abort();
        options.onError?.(new Error('Download cancelled'));
      });
    }

    xhr.send();
  } catch (err) {
    options.onError?.(err as Error);
  }
}

/**
 * Download file in chunks (for files >= 10MB)
 */
async function downloadFileByChunks(
  file: File,
  options: DownloadOptions
): Promise<void> {
  const uuid = localStorage.getItem('uuid');
  const baseUrl = buildUrl(`${file.file_path_webapp}&uuid=${uuid}`);

  const totalSize = file.file_size;
  const totalChunks = Math.ceil(totalSize / CHUNK_SIZE);
  const downloadedChunks: Blob[] = [];
  let downloadedBytes = 0;

  try {
    // Try to use File System Access API if available
    let writable: FileSystemWritableFileStream | null = null;

    if ('showSaveFilePicker' in window) {
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

    // Download each chunk
    for (let i = 0; i < totalChunks; i++) {
      if (options.signal?.aborted) {
        if (writable) {
          await writable.abort();
        }
        options.onError?.(new Error('Download cancelled'));
        return;
      }

      const offset = i * CHUNK_SIZE;
      const chunk = await downloadChunk(baseUrl, offset, CHUNK_SIZE, options.signal);
      downloadedChunks.push(chunk);
      downloadedBytes += chunk.size;

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
        });
      }

      // Small delay for smooth progress animation
      await new Promise(resolve => setTimeout(resolve, 20));
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

    options.onComplete?.();
  } catch (err) {
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
  if (file.file_size > CHUNK_SIZE) {
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
