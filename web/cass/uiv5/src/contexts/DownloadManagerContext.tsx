/**
 * Download Manager Context
 * Shared download queue and state management across all views.
 * Supports multi-file queuing with sequential download processing.
 */

import { createContext, useContext, useState, useCallback, useRef, type ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { downloadFile, type DownloadProgress, type DownloadLogEntry } from '../services/downloadService';
import type { File } from '../types/models';

export type QueueItemStatus = 'queued' | 'downloading' | 'complete' | 'failed' | 'cancelled';

export interface QueueItem {
  id: string;
  file: File;
  status: QueueItemStatus;
  progress: DownloadProgress;
  error?: string;
  addedAt: number;
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

interface DownloadManagerContextType {
  queue: QueueItem[];
  isOpen: boolean;
  isProcessing: boolean;
  chunkSizeMB: number;
  maxRetries: number;
  saveAs: boolean;
  downloadFolder: string;
  folderError: string;
  logEntries: DownloadLogEntry[];
  addToQueue: (file: File) => void;
  removeFromQueue: (id: string) => void;
  startDownloads: () => void;
  cancelAll: () => void;
  openModal: () => void;
  closeModal: () => void;
  setChunkSizeMB: (value: number) => void;
  setMaxRetries: (value: number) => void;
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
  const [saveAs, setSaveAs] = useState(true);
  const [downloadFolder, setDownloadFolder] = useState('');
  const [folderError, setFolderError] = useState('');
  const [logEntries, setLogEntries] = useState<DownloadLogEntry[]>([]);

  const directoryHandleRef = useRef<FileSystemDirectoryHandle | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);
  const isProcessingRef = useRef(false);

  const addLog = useCallback((entry: DownloadLogEntry) => {
    setLogEntries(prev => [...prev, entry].slice(-100));
  }, []);

  const addToQueue = useCallback((file: File) => {
    setQueue(prev => {
      // Don't add duplicates (same file MD5)
      if (prev.some(item => item.file.nickname === file.nickname && item.status !== 'complete' && item.status !== 'failed' && item.status !== 'cancelled')) {
        return prev;
      }
      const item: QueueItem = {
        id: `${file.nickname}-${Date.now()}`,
        file,
        status: 'queued',
        progress: { ...initialProgress, totalBytes: file.file_size },
        addedAt: Date.now(),
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
      // User cancelled the picker
      if (error.name === 'AbortError') return;

      // API not available or blocked by browser
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

  const processQueue = useCallback(async (currentQueue: QueueItem[], chunkSize: number, retries: number, useSaveAs: boolean) => {
    if (isProcessingRef.current) return;
    isProcessingRef.current = true;
    setIsProcessing(true);

    abortControllerRef.current = new AbortController();

    console.log('[DM] processQueue called, items:', currentQueue.length, 'ids:', currentQueue.map(q => q.id));

    // Process items sequentially
    for (let i = 0; i < currentQueue.length; i++) {
      const item = currentQueue[i];

      console.log(`[DM] item[${i}] id=${item.id} status=${item.status} file=${item.file.name}`);

      // Skip non-queued items
      if (item.status !== 'queued') {
        console.log(`[DM] skipping item[${i}] — status is ${item.status}`);
        continue;
      }

      // Check if cancelled
      if (abortControllerRef.current?.signal.aborted) break;

      // Update status to downloading — flush immediately so the UI shows the progress bar
      console.log(`[DM] setting item[${i}] to downloading`);
      flushSync(() => {
        setQueue(prev => {
          const updated = prev.map(q =>
            q.id === item.id ? { ...q, status: 'downloading' as QueueItemStatus, progress: { ...initialProgress, totalBytes: item.file.file_size, currentStatus: 'Starting download...' } } : q
          );
          console.log(`[DM] setQueue(downloading) — matched:`, updated.some(q => q.id === item.id && q.status === 'downloading'), 'queue ids:', prev.map(q => q.id));
          return updated;
        });
      });

      addLog({ type: 'info', message: `Starting download: ${item.file.name}`, timestamp: Date.now() });

      console.log(`[DM] calling downloadFile for item[${i}] size=${item.file.file_size}`);
      try {
        await downloadFile(item.file, {
          onProgress: (progress) => {
            console.log(`[DM] onProgress item[${i}] id=${item.id} pct=${progress.percentage}%`);
            setQueue(prev => prev.map(q =>
              q.id === item.id ? { ...q, progress } : q
            ));
          },
          onComplete: () => {
            console.log(`[DM] onComplete item[${i}] id=${item.id}`);
            setQueue(prev => prev.map(q =>
              q.id === item.id ? { ...q, status: 'complete' as QueueItemStatus } : q
            ));
            addLog({ type: 'success', message: `Download complete: ${item.file.name}`, timestamp: Date.now() });
          },
          onError: (error) => {
            setQueue(prev => prev.map(q =>
              q.id === item.id ? { ...q, status: 'failed' as QueueItemStatus, error: error.message } : q
            ));
            addLog({ type: 'error', message: `Download failed: ${item.file.name} - ${error.message}`, timestamp: Date.now() });
          },
          onLog: (entry) => {
            addLog(entry);
          },
          signal: abortControllerRef.current!.signal,
          chunkSize: chunkSize * 1024 * 1024,
          maxRetries: retries,
          saveAs: useSaveAs,
          directoryHandle: directoryHandleRef.current,
        });
      } catch (error) {
        if (abortControllerRef.current?.signal.aborted) {
          setQueue(prev => prev.map(q =>
            q.id === item.id && q.status === 'downloading' ? { ...q, status: 'cancelled' as QueueItemStatus } : q
          ));
          addLog({ type: 'warning', message: `Download cancelled: ${item.file.name}`, timestamp: Date.now() });
          break;
        }
        setQueue(prev => prev.map(q =>
          q.id === item.id ? { ...q, status: 'failed' as QueueItemStatus, error: (error as Error).message } : q
        ));
        addLog({ type: 'error', message: `Download failed: ${item.file.name} - ${(error as Error).message}`, timestamp: Date.now() });
      }
    }

    isProcessingRef.current = false;
    setIsProcessing(false);
    abortControllerRef.current = null;
  }, [addLog]);

  // Keep a ref to the latest queue for startDownloads to read without using setQueue as a getter
  const queueRef = useRef<QueueItem[]>([]);
  queueRef.current = queue;

  const startDownloads = useCallback(() => {
    const currentQueue = queueRef.current;
    const queuedItems = currentQueue.filter(item => item.status === 'queued');
    if (queuedItems.length > 0) {
      processQueue(currentQueue, chunkSizeMB, maxRetries, saveAs);
    }
  }, [processQueue, chunkSizeMB, maxRetries, saveAs]);

  const cancelAll = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setQueue(prev => prev.map(q =>
      q.status === 'queued' || q.status === 'downloading'
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
      saveAs,
      downloadFolder,
      folderError,
      logEntries,
      addToQueue,
      removeFromQueue,
      startDownloads,
      cancelAll,
      openModal,
      closeModal,
      setChunkSizeMB,
      setMaxRetries,
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
