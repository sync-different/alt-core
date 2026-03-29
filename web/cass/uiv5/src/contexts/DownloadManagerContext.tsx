/**
 * Download Manager Context
 * Shared download queue and state management across all views.
 * Supports multi-file queuing with sequential download processing,
 * resume from partial downloads, unattended auto-retry,
 * connection health checks (F5), parallel chunk downloads (F7),
 * adaptive chunk sizing (F8), and MD5 integrity verification (F10).
 */

import { createContext, useContext, useState, useCallback, useRef, type ReactNode } from 'react';
import { flushSync } from 'react-dom';
import {
  downloadFile, checkServerHealth, waitForServer,
  formatSpeed, formatTimeRemaining,
  type DownloadProgress, type DownloadLogEntry, type ResumeState,
} from '../services/downloadService';
import type { File } from '../types/models';

export type QueueItemStatus = 'queued' | 'downloading' | 'complete' | 'failed' | 'cancelled' | 'waiting_retry';

export interface QueueItem {
  id: string;
  file: File;
  status: QueueItemStatus;
  progress: DownloadProgress;
  error?: string;
  addedAt: number;
  resumeState?: ResumeState;    // partial download state for resume
  fileRetryCount: number;       // how many times this file has been retried (full-file level)
}

const initialProgress: DownloadProgress = {
  percentage: 0,
  downloadedBytes: 0,
  totalBytes: 0,
  speedKBps: 0,
  estimatedTimeRemaining: 0,
  elapsedTime: 0,
  errorCount: 0,
  retryCount: 0,
  currentStatus: '',
};

const FILE_RETRY_COOLDOWN = 30000; // 30s before auto-retrying a failed file
const DEFAULT_MAX_FILE_RETRIES = 5; // how many times to retry entire file before permanent failure

interface DownloadManagerContextType {
  queue: QueueItem[];
  isOpen: boolean;
  isProcessing: boolean;
  chunkSizeMB: number;
  maxRetries: number;
  maxFileRetries: number;
  enableAdaptiveChunks: boolean;
  enableParallelChunks: boolean;
  parallelChunkCount: number;
  saveAs: boolean;
  downloadFolder: string;
  folderError: string;
  logEntries: DownloadLogEntry[];
  addToQueue: (file: File) => void;
  removeFromQueue: (id: string) => void;
  resumeDownload: (id: string) => void;
  startDownloads: () => void;
  cancelAll: () => void;
  openModal: () => void;
  closeModal: () => void;
  setChunkSizeMB: (value: number) => void;
  setMaxRetries: (value: number) => void;
  setMaxFileRetries: (value: number) => void;
  setEnableAdaptiveChunks: (value: boolean) => void;
  setEnableParallelChunks: (value: boolean) => void;
  setParallelChunkCount: (value: number) => void;
  setSaveAs: (value: boolean) => void;
  pickDownloadFolder: () => void;
  clearDownloadFolder: () => void;
  clearCompleted: () => void;
}

const DownloadManagerContext = createContext<DownloadManagerContextType | undefined>(undefined);

export function DownloadManagerProvider({ children }: { children: ReactNode }) {
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [chunkSizeMB, setChunkSizeMB] = useState(10);
  const [maxRetries, setMaxRetries] = useState(3);
  const [maxFileRetries, setMaxFileRetries] = useState(DEFAULT_MAX_FILE_RETRIES);
  const [enableAdaptiveChunks, setEnableAdaptiveChunks] = useState(false);
  const [enableParallelChunks, setEnableParallelChunks] = useState(false);
  const [parallelChunkCount, setParallelChunkCount] = useState(2);
  const [saveAs, setSaveAs] = useState(true);
  const [downloadFolder, setDownloadFolder] = useState('');
  const [folderError, setFolderError] = useState('');
  const [logEntries, setLogEntries] = useState<DownloadLogEntry[]>([]);

  const directoryHandleRef = useRef<FileSystemDirectoryHandle | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);
  const isProcessingRef = useRef(false);

  const addLog = useCallback((entry: DownloadLogEntry) => {
    setLogEntries(prev => [...prev, entry].slice(-1000));
  }, []);

  const addToQueue = useCallback((file: File) => {
    setQueue(prev => {
      // Don't add duplicates (same file nickname) if already downloading/queued
      if (prev.some(item => item.file.nickname === file.nickname && item.status !== 'complete' && item.status !== 'failed' && item.status !== 'cancelled')) {
        return prev;
      }
      const item: QueueItem = {
        id: `${file.nickname}-${Date.now()}`,
        file,
        status: 'queued',
        progress: { ...initialProgress, totalBytes: file.file_size },
        addedAt: Date.now(),
        fileRetryCount: 0,
      };
      return [...prev, item];
    });
    setIsOpen(true);
  }, []);

  const removeFromQueue = useCallback((id: string) => {
    setQueue(prev => prev.filter(item => item.id !== id));
  }, []);

  const clearCompleted = useCallback(() => {
    setQueue(prev => prev.filter(item => item.status !== 'complete' && item.status !== 'failed' && item.status !== 'cancelled'));
  }, []);

  const pickDownloadFolder = useCallback(async () => {
    try {
      setFolderError('');
      if (!('showDirectoryPicker' in window)) {
        throw new Error('NOT_SUPPORTED');
      }
      const handle = await (window as any).showDirectoryPicker({ mode: 'readwrite' });
      directoryHandleRef.current = handle;
      setDownloadFolder(handle.name);
    } catch (err) {
      const error = err as Error;
      if (error.name === 'AbortError') return;

      if (error.message === 'NOT_SUPPORTED' || error.name === 'TypeError' || error.name === 'SecurityError') {
        const isSecure = window.isSecureContext;
        let msg: string;
        if (!isSecure) {
          msg = 'Choose Folder requires HTTPS or localhost.';
        } else {
          msg = 'Choose Folder is not available in this browser. Try Chrome or Edge, or check browser privacy/shield settings.';
        }
        setFolderError(msg);
        addLog({ type: 'warning', message: msg, timestamp: Date.now() });
      }
    }
  }, [addLog]);

  const clearDownloadFolder = useCallback(() => {
    directoryHandleRef.current = null;
    setDownloadFolder('');
    setFolderError('');
  }, []);

  /**
   * Download a single queue item, handling resume state
   */
  const downloadQueueItem = useCallback(async (
    item: QueueItem,
    chunkSize: number,
    retries: number,
    useSaveAs: boolean,
    adaptive: boolean,
    parallel: boolean,
    parallelCount: number,
    signal: AbortSignal,
  ): Promise<{ success: boolean; resumeState?: ResumeState }> => {
    return new Promise((resolve) => {
      downloadFile(item.file, {
        onProgress: (progress) => {
          setQueue(prev => prev.map(q =>
            q.id === item.id ? { ...q, progress } : q
          ));
        },
        onComplete: () => {
          setQueue(prev => prev.map(q =>
            q.id === item.id ? { ...q, status: 'complete' as QueueItemStatus } : q
          ));
          addLog({ type: 'success', message: `Download complete: ${item.file.name}`, timestamp: Date.now() });
          resolve({ success: true });
        },
        onError: (error, resumeState) => {
          if (signal.aborted) {
            setQueue(prev => prev.map(q =>
              q.id === item.id && q.status === 'downloading'
                ? { ...q, status: 'cancelled' as QueueItemStatus, resumeState }
                : q
            ));
            addLog({ type: 'warning', message: `Download cancelled: ${item.file.name}`, timestamp: Date.now() });
          } else {
            setQueue(prev => prev.map(q =>
              q.id === item.id
                ? { ...q, status: 'failed' as QueueItemStatus, error: error.message, resumeState }
                : q
            ));
            addLog({ type: 'error', message: `Download failed: ${item.file.name} - ${error.message}`, timestamp: Date.now() });
          }
          resolve({ success: false, resumeState });
        },
        onLog: (entry) => {
          addLog(entry);
        },
        signal,
        chunkSize: chunkSize * 1024 * 1024,
        maxRetries: retries,
        saveAs: useSaveAs && !item.resumeState, // Don't show save picker on resume
        directoryHandle: directoryHandleRef.current,
        resumeState: item.resumeState,
        enableAdaptiveChunks: adaptive,
        enableParallelChunks: parallel,
        parallelChunkCount: parallelCount,
      });
    });
  }, [addLog]);

  const processQueue = useCallback(async (
    chunkSize: number,
    retries: number,
    fileRetries: number,
    useSaveAs: boolean,
    adaptive: boolean,
    parallel: boolean,
    parallelCount: number,
  ) => {
    if (isProcessingRef.current) return;
    isProcessingRef.current = true;
    setIsProcessing(true);
    const queueStartTime = Date.now();

    abortControllerRef.current = new AbortController();
    const signal = abortControllerRef.current.signal;

    let processedSuccess = 0;
    let processedFail = 0;

    const processableStatuses: QueueItemStatus[] = ['queued', 'waiting_retry'];

    // Keep processing until no more work to do
    let hasWork = true;
    while (hasWork && !signal.aborted) {
      hasWork = false;

      // Get fresh queue state
      const snapshot = queueRef.current;

      for (const item of snapshot) {
        if (signal.aborted) break;

        if (!processableStatuses.includes(item.status)) continue;
        hasWork = true;

        // F5: Health check before each file
        const healthy = await checkServerHealth(signal, addLog);
        if (signal.aborted) break;

        if (!healthy) {
          addLog({ type: 'warning', message: `Server unreachable before starting ${item.file.name}`, timestamp: Date.now() });

          // Update item status to show health check
          setQueue(prev => prev.map(q =>
            q.id === item.id
              ? { ...q, progress: { ...q.progress, currentStatus: 'Server unreachable — waiting...' } }
              : q
          ));

          const recovered = await waitForServer(
            fileRetries,
            signal,
            (msg) => {
              setQueue(prev => prev.map(q =>
                q.id === item.id
                  ? { ...q, progress: { ...q.progress, currentStatus: msg } }
                  : q
              ));
            },
            addLog,
          );

          if (signal.aborted) break;

          if (!recovered) {
            setQueue(prev => prev.map(q =>
              q.id === item.id
                ? { ...q, status: 'failed' as QueueItemStatus, error: 'Server unreachable after all retries' }
                : q
            ));
            addLog({ type: 'error', message: `Server unreachable — ${item.file.name} marked as failed with resume available`, timestamp: Date.now() });
            continue;
          }
        }

        // Mark as downloading
        flushSync(() => {
          setQueue(prev => prev.map(q =>
            q.id === item.id
              ? {
                  ...q,
                  status: 'downloading' as QueueItemStatus,
                  progress: {
                    ...initialProgress,
                    totalBytes: item.file.file_size,
                    downloadedBytes: item.resumeState?.resumeOffset || 0,
                    percentage: item.resumeState
                      ? Math.round((item.resumeState.resumeOffset / item.file.file_size) * 100)
                      : 0,
                    currentStatus: item.resumeState ? 'Resuming download...' : 'Starting download...',
                  },
                }
              : q
          ));
        });

        const isResume = !!item.resumeState;
        addLog({
          type: 'info',
          message: isResume
            ? `Resuming download: ${item.file.name} (attempt ${item.fileRetryCount + 1})`
            : `Starting download: ${item.file.name}`,
          timestamp: Date.now(),
        });

        const result = await downloadQueueItem(item, chunkSize, retries, useSaveAs, adaptive, parallel, parallelCount, signal);

        if (signal.aborted) break;

        if (result.success) {
          processedSuccess++;
        }

        // If failed and under file retry limit, schedule auto-retry (unattended mode)
        if (!result.success && !signal.aborted) {
          const currentItem = queueRef.current.find(q => q.id === item.id);
          const currentRetryCount = currentItem?.fileRetryCount ?? item.fileRetryCount;

          if (currentRetryCount < fileRetries - 1 && result.resumeState) {
            const nextRetry = currentRetryCount + 1;
            addLog({
              type: 'warning',
              message: `Will auto-retry ${item.file.name} in ${FILE_RETRY_COOLDOWN / 1000}s (attempt ${nextRetry + 1}/${fileRetries})`,
              timestamp: Date.now(),
            });

            setQueue(prev => prev.map(q =>
              q.id === item.id
                ? {
                    ...q,
                    status: 'waiting_retry' as QueueItemStatus,
                    fileRetryCount: nextRetry,
                    resumeState: result.resumeState,
                  }
                : q
            ));

            // Wait for cooldown
            try {
              await new Promise<void>((resolve, reject) => {
                if (signal.aborted) { reject(new Error('cancelled')); return; }
                const timer = setTimeout(resolve, FILE_RETRY_COOLDOWN);
                signal.addEventListener('abort', () => { clearTimeout(timer); reject(new Error('cancelled')); }, { once: true });
              });
            } catch {
              break; // cancelled during cooldown
            }

            hasWork = true;
          } else {
            processedFail++;
          }
        }
      }
    }

    // Log queue completion summary (use processedSuccess/processedFail since queue state may not have flushed yet)
    if (!signal.aborted) {
      const processedTotal = processedSuccess + processedFail;
      const finalQueue = queueRef.current;
      const totalErrors = finalQueue.reduce((sum, q) => sum + q.progress.errorCount, 0);
      const totalRetryCount = finalQueue.reduce((sum, q) => sum + q.progress.retryCount, 0);
      const elapsedSeconds = (Date.now() - queueStartTime) / 1000;

      // Sum file sizes for successfully processed files (use file_size, not downloadedBytes)
      const totalDownloaded = finalQueue.reduce((sum, q) => sum + Number(q.progress.downloadedBytes), 0);
      const avgSpeedKBps = elapsedSeconds > 0 ? totalDownloaded / 1024 / elapsedSeconds : 0;

      const fmtSize = (bytes: number) => {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
        return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
      };

      const parts: string[] = [
        `Queue complete: ${processedSuccess}/${processedTotal} files`,
        processedFail > 0 ? `${processedFail} failed` : '',
        `${fmtSize(totalDownloaded)} downloaded`,
        `${formatTimeRemaining(elapsedSeconds)} elapsed`,
        avgSpeedKBps > 0 ? `avg ${formatSpeed(avgSpeedKBps)}` : '',
        `${totalErrors} errors`,
        `${totalRetryCount} retries`,
      ].filter(Boolean);

      addLog({ type: processedFail > 0 ? 'warning' : 'success', message: parts.join(' — '), timestamp: Date.now() });
    }

    isProcessingRef.current = false;
    setIsProcessing(false);
    abortControllerRef.current = null;
  }, [addLog, downloadQueueItem]);

  const queueRef = useRef<QueueItem[]>([]);
  queueRef.current = queue;

  const startDownloads = useCallback(() => {
    const currentQueue = queueRef.current;
    const actionableItems = currentQueue.filter(item => item.status === 'queued' || item.status === 'waiting_retry');
    if (actionableItems.length > 0) {
      processQueue(chunkSizeMB, maxRetries, maxFileRetries, saveAs, enableAdaptiveChunks, enableParallelChunks, parallelChunkCount);
    }
  }, [processQueue, chunkSizeMB, maxRetries, maxFileRetries, saveAs, enableAdaptiveChunks, enableParallelChunks, parallelChunkCount]);

  /**
   * Resume a specific failed download — re-queues it with its resume state intact
   */
  const resumeDownload = useCallback((id: string) => {
    setQueue(prev => prev.map(q =>
      q.id === id && q.status === 'failed'
        ? { ...q, status: 'queued' as QueueItemStatus, error: undefined }
        : q
    ));

    // Auto-start if not already processing
    if (!isProcessingRef.current) {
      setTimeout(() => {
        const q = queueRef.current;
        const actionable = q.filter(item => item.status === 'queued' || item.status === 'waiting_retry');
        if (actionable.length > 0) {
          processQueue(chunkSizeMB, maxRetries, maxFileRetries, saveAs, enableAdaptiveChunks, enableParallelChunks, parallelChunkCount);
        }
      }, 50);
    }
  }, [processQueue, chunkSizeMB, maxRetries, maxFileRetries, saveAs, enableAdaptiveChunks, enableParallelChunks, parallelChunkCount]);

  const cancelAll = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setQueue(prev => prev.map(q =>
      q.status === 'queued' || q.status === 'downloading' || q.status === 'waiting_retry'
        ? { ...q, status: 'cancelled' as QueueItemStatus }
        : q
    ));
    isProcessingRef.current = false;
    setIsProcessing(false);
  }, []);

  const openModal = useCallback(() => setIsOpen(true), []);
  const closeModal = useCallback(() => setIsOpen(false), []);

  return (
    <DownloadManagerContext.Provider value={{
      queue,
      isOpen,
      isProcessing,
      chunkSizeMB,
      maxRetries,
      maxFileRetries,
      enableAdaptiveChunks,
      enableParallelChunks,
      parallelChunkCount,
      saveAs,
      downloadFolder,
      folderError,
      logEntries,
      addToQueue,
      removeFromQueue,
      resumeDownload,
      startDownloads,
      cancelAll,
      openModal,
      closeModal,
      setChunkSizeMB,
      setMaxRetries,
      setMaxFileRetries,
      setEnableAdaptiveChunks,
      setEnableParallelChunks,
      setParallelChunkCount,
      setSaveAs,
      pickDownloadFolder,
      clearDownloadFolder,
      clearCompleted,
    }}>
      {children}
    </DownloadManagerContext.Provider>
  );
}

export function useDownloadManager() {
  const context = useContext(DownloadManagerContext);
  if (!context) {
    throw new Error('useDownloadManager must be used within a DownloadManagerProvider');
  }
  return context;
}
