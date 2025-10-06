/**
 * useFileDownload Hook
 * Manages file download state and progress
 */

import { useState, useRef } from 'react';
import { downloadFile, type DownloadProgress } from '../services/downloadService';
import type { File } from '../types/models';

export function useFileDownload() {
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadProgress, setDownloadProgress] = useState<DownloadProgress>({
    percentage: 0,
    downloadedBytes: 0,
    totalBytes: 0,
    speedKBps: 0,
    estimatedTimeRemaining: 0,
  });
  const [isComplete, setIsComplete] = useState(false);
  const [currentFile, setCurrentFile] = useState<File | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  const startDownload = async (file: File) => {
    // Reset state
    setIsDownloading(true);
    setIsComplete(false);
    setCurrentFile(file);
    setDownloadProgress({
      percentage: 0,
      downloadedBytes: 0,
      totalBytes: file.file_size,
      speedKBps: 0,
      estimatedTimeRemaining: 0,
    });

    // Create abort controller
    abortControllerRef.current = new AbortController();

    try {
      await downloadFile(file, {
        onProgress: (progress) => {
          setDownloadProgress(progress);
        },
        onComplete: () => {
          setIsComplete(true);
          setIsDownloading(false);
        },
        onError: (error) => {
          console.error('Download error:', error);
          setIsDownloading(false);
          setIsComplete(false);
          setCurrentFile(null);
        },
        signal: abortControllerRef.current.signal,
      });
    } catch (error) {
      console.error('Download error:', error);
      setIsDownloading(false);
      setIsComplete(false);
      setCurrentFile(null);
    }
  };

  const cancelDownload = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsDownloading(false);
    setIsComplete(false);
    setCurrentFile(null);
  };

  const closeModal = () => {
    setIsDownloading(false);
    setIsComplete(false);
    setCurrentFile(null);
  };

  return {
    isDownloading,
    downloadProgress,
    isComplete,
    currentFile,
    startDownload,
    cancelDownload,
    closeModal,
  };
}
