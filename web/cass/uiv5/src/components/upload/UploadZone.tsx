/**
 * Upload Zone Component
 * Provides drag-and-drop file upload with chunked upload support
 * Features: retry with exponential backoff, time/speed tracking, detailed error reporting
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { useDropzone } from 'react-dropzone';
import {
  Box,
  Button,
  Typography,
  LinearProgress,
  IconButton,
  Paper,
  Slider,
  Alert,
  Collapse,
  Checkbox,
  FormControlLabel,
} from '@mui/material';
import {
  Close as CloseIcon,
  CloudUpload as CloudUploadIcon,
  Add as AddIcon,
  Cancel as CancelIcon,
  Delete as DeleteIcon,
  CheckCircle as CheckCircleIcon,
  Refresh as RefreshIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Error as ErrorIcon,
} from '@mui/icons-material';

// ============ Type Definitions ============

interface ChunkStatus {
  index: number;
  status: 'pending' | 'uploading' | 'completed' | 'failed' | 'retrying';
  attempts: number;
  error?: string;
  httpStatus?: number;
}

interface UploadFile {
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'completed' | 'error' | 'paused';
  error?: string;
  uploadedBytes: number;
  startTime?: number;
  elapsedTime?: number;
  speed?: number;
  estimatedTimeRemaining?: number;
  chunks: ChunkStatus[];
  failedChunks: number[];
  completedChunks: number;
  totalChunks: number;
}

interface UploadNotification {
  id: string;
  type: 'error' | 'warning' | 'info' | 'success';
  message: string;
  timestamp: number;
  fileName?: string;
}

interface GlobalStats {
  totalBytes: number;
  uploadedBytes: number;
  startTime: number;
  speed: number;
  activeUploads: number;
  totalChunks: number;
  completedChunks: number;
}

interface UploadZoneProps {
  open: boolean;
  onClose: () => void;
  /** Target folder path for uploads (when on Folders page) */
  targetFolder?: string | null;
}

// ============ Utility Functions ============

/**
 * Format milliseconds to hh:mm:ss
 */
const formatTime = (ms: number): string => {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
};

/**
 * Format bytes/sec to MB/s with 1 decimal
 */
const formatSpeed = (bytesPerSec: number): string => {
  const mbps = bytesPerSec / (1024 * 1024);
  return `${mbps.toFixed(1)} MB/s`;
};

/**
 * Calculate backoff delay with exponential increase and jitter
 * Base: 1 second, doubles each attempt, max 30 seconds
 * Jitter: +/-25% randomization
 */
const calculateBackoff = (attempt: number): number => {
  const baseDelay = 1000;
  const maxDelay = 30000;
  const exponentialDelay = Math.min(baseDelay * Math.pow(2, attempt - 1), maxDelay);
  const jitter = exponentialDelay * 0.25 * (Math.random() * 2 - 1);
  return Math.round(exponentialDelay + jitter);
};

/**
 * Generate unique ID for notifications
 */
const generateId = (): string => {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
};

// ============ Main Component ============

export function UploadZone({ open, onClose, targetFolder }: UploadZoneProps) {
  // File state
  const [files, setFiles] = useState<UploadFile[]>([]);

  // Settings
  const [chunkSize, setChunkSize] = useState(10); // MB
  const [maxRetries, setMaxRetries] = useState(5);
  const [parallelUploads, setParallelUploads] = useState(false); // Default to serial

  // Notifications
  const [notifications, setNotifications] = useState<UploadNotification[]>([]);
  const [notificationsExpanded, setNotificationsExpanded] = useState(true);

  // Global stats
  const [globalStats, setGlobalStats] = useState<GlobalStats>({
    totalBytes: 0,
    uploadedBytes: 0,
    startTime: 0,
    speed: 0,
    activeUploads: 0,
    totalChunks: 0,
    completedChunks: 0,
  });

  // Refs
  const abortControllersRef = useRef<Map<string, AbortController>>(new Map());
  const statsIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const retryTimeoutsRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  // Get netty port from server
  const [nettyPort, setNettyPort] = useState(8087);

  useEffect(() => {
    fetch('/cass/nodeinfo.fn')
      .then(res => res.json())
      .then(data => {
        const serverNode = data.nodes.find((n: any) => n.node_type === 'server');
        if (serverNode?.node_nettyport_post) {
          setNettyPort(serverNode.node_nettyport_post);
        }
      })
      .catch(err => console.error('Failed to get netty port:', err));
  }, []);

  // Clean up on unmount
  useEffect(() => {
    return () => {
      if (statsIntervalRef.current) {
        clearInterval(statsIntervalRef.current);
      }
      retryTimeoutsRef.current.forEach(timeout => clearTimeout(timeout));
      abortControllersRef.current.forEach(controller => controller.abort());
    };
  }, []);

  // Keep a ref to latest files for use in interval
  const filesRef = useRef<UploadFile[]>([]);
  useEffect(() => {
    filesRef.current = files;
  }, [files]);

  // Stats tracking interval
  useEffect(() => {
    const activeFiles = files.filter(f => f.status === 'uploading');

    if (activeFiles.length > 0 && !statsIntervalRef.current) {
      statsIntervalRef.current = setInterval(() => {
        // Update per-file stats
        setFiles(prev => prev.map(f => {
          if (f.status !== 'uploading' || !f.startTime) return f;

          const elapsed = Date.now() - f.startTime;
          const speed = f.uploadedBytes > 0 ? f.uploadedBytes / (elapsed / 1000) : 0;
          const remaining = f.file.size - f.uploadedBytes;
          const eta = speed > 0 ? remaining / speed : 0;

          return {
            ...f,
            elapsedTime: elapsed,
            speed,
            estimatedTimeRemaining: eta,
          };
        }));

        // Update global stats using ref for latest files
        const currentFiles = filesRef.current;
        const activeCount = currentFiles.filter(f => f.status === 'uploading').length;

        if (activeCount > 0) {
          const totalUploaded = currentFiles.reduce((sum, f) => sum + f.uploadedBytes, 0);
          const totalSize = currentFiles.reduce((sum, f) => sum + f.file.size, 0);
          const totalChunks = currentFiles.reduce((sum, f) => sum + f.totalChunks, 0);
          const completedChunks = currentFiles.reduce((sum, f) => sum + f.completedChunks, 0);

          setGlobalStats(prev => {
            const elapsed = prev.startTime > 0 ? (Date.now() - prev.startTime) / 1000 : 0;
            const speed = elapsed > 0 ? totalUploaded / elapsed : 0;

            return {
              ...prev,
              uploadedBytes: totalUploaded,
              totalBytes: totalSize,
              speed,
              activeUploads: activeCount,
              totalChunks,
              completedChunks,
            };
          });
        }
      }, 500);
    } else if (activeFiles.length === 0 && statsIntervalRef.current) {
      clearInterval(statsIntervalRef.current);
      statsIntervalRef.current = null;
    }
  }, [files]);

  // ============ Notification Helpers ============

  const addNotification = useCallback((
    type: UploadNotification['type'],
    message: string,
    fileName?: string
  ) => {
    const notification: UploadNotification = {
      id: generateId(),
      type,
      message,
      timestamp: Date.now(),
      fileName,
    };
    setNotifications(prev => [notification, ...prev].slice(0, 100)); // Keep last 100
  }, []);

  const clearNotifications = useCallback(() => {
    setNotifications([]);
  }, []);

  // ============ File Drop Handler ============

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const chunkSizeBytes = chunkSize * 1024 * 1024;

    const newFiles: UploadFile[] = acceptedFiles.map(file => {
      const totalChunks = Math.ceil(file.size / chunkSizeBytes);
      const chunks: ChunkStatus[] = Array.from({ length: totalChunks }, (_, i) => ({
        index: i,
        status: 'pending',
        attempts: 0,
      }));

      return {
        file,
        progress: 0,
        status: 'pending',
        uploadedBytes: 0,
        chunks,
        failedChunks: [],
        completedChunks: 0,
        totalChunks,
      };
    });

    setFiles(prev => [...prev, ...newFiles]);
  }, [chunkSize]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    noClick: true,
  });

  // ============ Chunk Upload with Retry ============

  const uploadChunk = async (
    file: File,
    chunkIndex: number,
    totalChunks: number,
    fileIndex: number,
    abortController: AbortController,
    chunkSizeBytes: number
  ): Promise<boolean> => {
    const start = chunkIndex * chunkSizeBytes;
    const end = Math.min(start + chunkSizeBytes, file.size);
    const chunk = file.slice(start, end);

    const protocol = window.location.protocol;
    const hostname = window.location.hostname;
    const isHttps = protocol === 'https:';
    const isDevelopment = window.location.port === '5173';

    let attempt = 0;

    while (attempt < maxRetries + 1) {
      attempt++;

      // Check if aborted
      if (abortController.signal.aborted) {
        return false;
      }

      try {
        // Update chunk status
        setFiles(prev => prev.map((f, i) => {
          if (i !== fileIndex) return f;
          const newChunks = [...f.chunks];
          newChunks[chunkIndex] = {
            ...newChunks[chunkIndex],
            status: attempt === 1 ? 'uploading' : 'retrying',
            attempts: attempt,
          };
          return { ...f, chunks: newChunks };
        }));

        const formData = new FormData();
        const paramName = `upload.${file.name}.${totalChunks}.${chunkIndex + 1}.p`;
        const chunkFile = new File([chunk], paramName, { type: file.type });

        // Add targetFolder BEFORE the file (server processes form fields in order)
        if (targetFolder && targetFolder !== 'scanfolders') {
          formData.append('targetFolder', targetFolder);
          console.log('[UPLOAD] Adding targetFolder to FormData:', targetFolder);
        }

        formData.append(paramName, chunkFile);

        let url: string;
        if (isHttps) {
          url = `https://${hostname}/${paramName}`;
        } else {
          url = isDevelopment
            ? '/formpost'
            : `http://${hostname}:${nettyPort}/formpost`;
        }

        const response = await fetch(url, {
          method: 'POST',
          body: formData,
          signal: abortController.signal,
          // Always include credentials. Port 8087 (Netty upload) requires
          // a valid session cookie — AUDIT Issues #2/#3 closed 2026-04-16.
          credentials: 'include',
        });

        if (!response.ok) {
          throw new UploadError(
            `HTTP ${response.status}: ${response.statusText}`,
            response.status
          );
        }

        // Success - update chunk and file progress
        setFiles(prev => prev.map((f, i) => {
          if (i !== fileIndex) return f;

          const newChunks = [...f.chunks];
          newChunks[chunkIndex] = {
            ...newChunks[chunkIndex],
            status: 'completed',
            attempts: attempt,
          };

          const completedChunks = newChunks.filter(c => c.status === 'completed').length;
          const uploadedBytes = Math.min(completedChunks * chunkSizeBytes, file.size);
          const progress = (completedChunks / totalChunks) * 100;

          return {
            ...f,
            chunks: newChunks,
            completedChunks,
            uploadedBytes,
            progress,
          };
        }));

        return true;

      } catch (error: any) {
        if (error.name === 'AbortError') {
          return false;
        }

        const httpStatus = error instanceof UploadError ? error.httpStatus : undefined;
        const errorMessage = error.message || 'Unknown error';

        if (attempt <= maxRetries) {
          // Calculate backoff
          const backoffMs = calculateBackoff(attempt);
          const backoffSec = Math.ceil(backoffMs / 1000);

          addNotification(
            'warning',
            `Chunk ${chunkIndex + 1} of ${totalChunks} failed (${errorMessage}). Retrying in ${backoffSec}s (attempt ${attempt + 1} of ${maxRetries + 1})...`,
            file.name
          );

          // Update chunk status to show retry countdown
          setFiles(prev => prev.map((f, i) => {
            if (i !== fileIndex) return f;
            const newChunks = [...f.chunks];
            newChunks[chunkIndex] = {
              ...newChunks[chunkIndex],
              status: 'retrying',
              attempts: attempt,
              error: `Retrying in ${backoffSec}s...`,
            };
            return { ...f, chunks: newChunks };
          }));

          // Wait for backoff
          await new Promise<void>((resolve, reject) => {
            const timeoutId = setTimeout(resolve, backoffMs);
            retryTimeoutsRef.current.set(`${file.name}-${chunkIndex}`, timeoutId);

            // Check abort during wait
            const checkAbort = setInterval(() => {
              if (abortController.signal.aborted) {
                clearTimeout(timeoutId);
                clearInterval(checkAbort);
                reject(new Error('Aborted'));
              }
            }, 100);

            setTimeout(() => clearInterval(checkAbort), backoffMs + 100);
          }).catch(() => {
            return false;
          });

          retryTimeoutsRef.current.delete(`${file.name}-${chunkIndex}`);

        } else {
          // All retries exhausted
          setFiles(prev => prev.map((f, i) => {
            if (i !== fileIndex) return f;
            const newChunks = [...f.chunks];
            newChunks[chunkIndex] = {
              ...newChunks[chunkIndex],
              status: 'failed',
              attempts: attempt,
              error: errorMessage,
              httpStatus,
            };
            const failedChunks = [...f.failedChunks];
            if (!failedChunks.includes(chunkIndex)) {
              failedChunks.push(chunkIndex);
            }
            return { ...f, chunks: newChunks, failedChunks };
          }));

          addNotification(
            'error',
            `Chunk ${chunkIndex + 1} of ${totalChunks} failed (HTTP ${httpStatus || 'N/A'}): ${errorMessage}`,
            file.name
          );

          return false;
        }
      }
    }

    return false;
  };

  // ============ File Upload ============

  const uploadFile = async (fileIndex: number, retryOnly = false) => {
    const uploadFileData = files[fileIndex];
    if (!uploadFileData) return;

    const { file } = uploadFileData;
    const chunkSizeBytes = chunkSize * 1024 * 1024;
    const totalChunks = Math.ceil(file.size / chunkSizeBytes);

    // Create or get abort controller
    let abortController = abortControllersRef.current.get(file.name);
    if (!abortController || abortController.signal.aborted) {
      abortController = new AbortController();
      abortControllersRef.current.set(file.name, abortController);
    }

    const startTime = Date.now();

    // Initialize file state
    setFiles(prev => prev.map((f, i) => {
      if (i !== fileIndex) return f;

      // If retrying, keep existing chunks; otherwise initialize
      const chunks = retryOnly ? f.chunks : Array.from({ length: totalChunks }, (_, idx) => ({
        index: idx,
        status: 'pending' as const,
        attempts: 0,
      }));

      return {
        ...f,
        status: 'uploading',
        startTime: retryOnly ? f.startTime : startTime,
        failedChunks: retryOnly ? [] : f.failedChunks,
        chunks,
        totalChunks,
      };
    }));

    // Update global stats
    setGlobalStats(prev => ({
      ...prev,
      totalBytes: prev.totalBytes + (retryOnly ? 0 : file.size),
      startTime: prev.startTime || startTime,
      activeUploads: prev.activeUploads + 1,
    }));

    // Determine which chunks to upload
    const chunksToUpload = retryOnly
      ? uploadFileData.failedChunks
      : Array.from({ length: totalChunks }, (_, i) => i);

    for (const chunkIndex of chunksToUpload) {
      if (abortController.signal.aborted) {
        break;
      }

      await uploadChunk(
        file,
        chunkIndex,
        totalChunks,
        fileIndex,
        abortController,
        chunkSizeBytes
      );
      // Continue to next chunk even if one fails (we track failed chunks)
    }

    // Final status update
    setFiles(prev => prev.map((f, i) => {
      if (i !== fileIndex) return f;

      const failedCount = f.chunks.filter(c => c.status === 'failed').length;
      const completedCount = f.chunks.filter(c => c.status === 'completed').length;
      const elapsedTime = Date.now() - (f.startTime || startTime);

      if (failedCount > 0) {
        return {
          ...f,
          status: 'error',
          elapsedTime,
          error: `${failedCount} chunk(s) failed`,
        };
      } else if (completedCount === totalChunks) {
        const avgSpeed = f.file.size / (elapsedTime / 1000);
        addNotification(
          'success',
          `Completed in ${formatTime(elapsedTime)} | Average speed: ${formatSpeed(avgSpeed)}`,
          file.name
        );
        return {
          ...f,
          status: 'completed',
          progress: 100,
          elapsedTime,
          speed: avgSpeed,
        };
      }

      return f;
    }));

    // Update global stats
    setGlobalStats(prev => ({
      ...prev,
      activeUploads: Math.max(0, prev.activeUploads - 1),
    }));

    abortControllersRef.current.delete(file.name);
  };

  // ============ Action Handlers ============

  const handleUploadAll = async () => {
    const pendingIndices = files
      .map((file, index) => (file.status === 'pending' ? index : -1))
      .filter(index => index !== -1);

    if (parallelUploads) {
      // Upload all files in parallel
      pendingIndices.forEach(index => {
        uploadFile(index);
      });
    } else {
      // Upload files serially (one at a time)
      for (const index of pendingIndices) {
        await uploadFile(index);
      }
    }
  };

  const handleRetryFailed = (fileIndex: number) => {
    uploadFile(fileIndex, true);
  };

  const handleRetryAllFailed = () => {
    files.forEach((file, index) => {
      if (file.status === 'error' && file.failedChunks.length > 0) {
        uploadFile(index, true);
      }
    });
  };

  const handleCancel = (index: number) => {
    const file = files[index];
    const controller = abortControllersRef.current.get(file.file.name);
    if (controller) {
      controller.abort();
    }
    // Clear any retry timeouts for this file
    retryTimeoutsRef.current.forEach((timeout, key) => {
      if (key.startsWith(file.file.name)) {
        clearTimeout(timeout);
        retryTimeoutsRef.current.delete(key);
      }
    });
    setFiles(prev => prev.filter((_, i) => i !== index));
  };

  const handleRemoveAll = () => {
    abortControllersRef.current.forEach(controller => controller.abort());
    abortControllersRef.current.clear();
    retryTimeoutsRef.current.forEach(timeout => clearTimeout(timeout));
    retryTimeoutsRef.current.clear();
    setFiles([]);
    setGlobalStats({
      totalBytes: 0,
      uploadedBytes: 0,
      startTime: 0,
      speed: 0,
      activeUploads: 0,
      totalChunks: 0,
      completedChunks: 0,
    });
  };

  const handleClose = () => {
    handleRemoveAll();
    clearNotifications();
    onClose();
  };

  // ============ Computed Values ============

  const hasFailedFiles = files.some(f => f.status === 'error' && f.failedChunks.length > 0);
  const hasPendingFiles = files.some(f => f.status === 'pending');
  const hasActiveUploads = files.some(f => f.status === 'uploading');

  if (!open) return null;

  return (
    <Box
      sx={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: 3,
      }}
    >
      <Paper
        {...getRootProps()}
        sx={{
          width: '100%',
          maxWidth: 800,
          maxHeight: '90vh',
          overflow: 'auto',
          p: 3,
          position: 'relative',
        }}
      >
        <IconButton
          onClick={handleClose}
          sx={{
            position: 'absolute',
            top: 10,
            right: 10,
          }}
        >
          <CloseIcon />
        </IconButton>

        {/* Target Folder Display */}
        {targetFolder && targetFolder !== 'scanfolders' && (
          <Alert severity="info" sx={{ mb: 2 }}>
            <Typography variant="body2">
              <strong>Uploading to:</strong> {decodeURIComponent(targetFolder)}
            </Typography>
          </Alert>
        )}

        {/* Settings Section */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="h6" sx={{ fontWeight: 'bold', color: 'text.secondary', mb: 2, textAlign: 'center' }}>
            Upload Settings
          </Typography>

          {/* Chunk Size Slider */}
          <Box sx={{ mb: 2 }}>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              Chunk Size
            </Typography>
            <Box sx={{ px: 4 }}>
              <Slider
                value={chunkSize}
                onChange={(_, value) => setChunkSize(value as number)}
                min={5}
                max={20}
                step={5}
                marks={[
                  { value: 5, label: '5 MB' },
                  { value: 10, label: '10 MB' },
                  { value: 15, label: '15 MB' },
                  { value: 20, label: '20 MB' },
                ]}
                valueLabelDisplay="auto"
                valueLabelFormat={(value) => `${value} MB`}
                disabled={hasActiveUploads}
              />
            </Box>
          </Box>

          {/* Max Retries Slider */}
          <Box sx={{ mb: 2 }}>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              Max Retries per Chunk
            </Typography>
            <Box sx={{ px: 4 }}>
              <Slider
                value={maxRetries}
                onChange={(_, value) => setMaxRetries(value as number)}
                min={1}
                max={10}
                step={1}
                marks={[
                  { value: 1, label: '1' },
                  { value: 3, label: '3' },
                  { value: 5, label: '5' },
                  { value: 10, label: '10' },
                ]}
                valueLabelDisplay="auto"
                disabled={hasActiveUploads}
              />
            </Box>
          </Box>

          {/* Parallel Uploads Checkbox */}
          <Box sx={{ px: 4 }}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={parallelUploads}
                  onChange={(e) => setParallelUploads(e.target.checked)}
                  disabled={hasActiveUploads}
                  size="small"
                />
              }
              label={
                <Typography variant="body2" color="text.secondary">
                  Upload files in parallel (faster but uses more bandwidth)
                </Typography>
              }
            />
          </Box>
        </Box>

        {/* Drop Zone */}
        <Box
          sx={{
            border: '2px dashed',
            borderColor: isDragActive ? 'primary.main' : 'divider',
            borderRadius: 2,
            p: 4,
            textAlign: 'center',
            backgroundColor: isDragActive ? 'action.hover' : 'transparent',
            mb: 3,
          }}
        >
          <CloudUploadIcon sx={{ fontSize: 60, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" sx={{ mb: 2 }}>
            {isDragActive ? 'Drop files here' : 'Drop files here'}
          </Typography>
          <input {...getInputProps()} />
          <Button
            variant="contained"
            color="success"
            startIcon={<AddIcon />}
            onClick={() => document.querySelector<HTMLInputElement>('input[type="file"]')?.click()}
          >
            Add files...
          </Button>
        </Box>

        {/* Global Stats */}
        {globalStats.activeUploads > 0 && (
          <Alert severity="info" sx={{ mb: 2 }}>
            <strong>Overall:</strong>{' '}
            {globalStats.totalBytes > 0 ? Math.round((globalStats.uploadedBytes / globalStats.totalBytes) * 100) : 0}%{' '}
            ({globalStats.completedChunks} of {globalStats.totalChunks} chunks) |{' '}
            {formatTime(Date.now() - globalStats.startTime)} elapsed |{' '}
            {formatSpeed(globalStats.speed)}
            {globalStats.speed > 0 && globalStats.totalBytes > globalStats.uploadedBytes && (
              <> | ~{formatTime(((globalStats.totalBytes - globalStats.uploadedBytes) / globalStats.speed) * 1000)} remaining</>
            )}
          </Alert>
        )}

        {/* File List */}
        {files.length > 0 && (
          <Box>
            {/* Action Buttons */}
            <Box sx={{ mb: 2, display: 'flex', gap: 1, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
              <Button
                variant="contained"
                color="primary"
                onClick={handleUploadAll}
                disabled={!hasPendingFiles || hasActiveUploads}
              >
                Upload All
              </Button>
              {hasFailedFiles && (
                <Button
                  variant="contained"
                  color="warning"
                  startIcon={<RefreshIcon />}
                  onClick={handleRetryAllFailed}
                  disabled={hasActiveUploads}
                >
                  Retry All Failed
                </Button>
              )}
              <Button
                variant="outlined"
                color="error"
                onClick={handleRemoveAll}
              >
                Clear All
              </Button>
            </Box>

            {/* File Items */}
            {files.map((uploadFile, index) => (
              <Paper key={index} sx={{ p: 2, mb: 2 }} elevation={2}>
                {/* File Header */}
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {uploadFile.file.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {(uploadFile.file.size / (1024 * 1024)).toFixed(2)} MB
                      {uploadFile.totalChunks > 0 && ` (${uploadFile.totalChunks} chunks)`}
                    </Typography>
                  </Box>
                  {uploadFile.status === 'completed' && <CheckCircleIcon color="success" />}
                  {uploadFile.status === 'error' && <ErrorIcon color="error" />}
                </Box>

                {/* Progress Bar */}
                {uploadFile.status !== 'pending' && (
                  <LinearProgress
                    variant="determinate"
                    value={uploadFile.progress}
                    color={uploadFile.status === 'error' ? 'error' : 'primary'}
                    sx={{ mb: 1, height: 8, borderRadius: 1 }}
                  />
                )}

                {/* Stats Row */}
                {uploadFile.status === 'uploading' && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                    {Math.round(uploadFile.progress)}% ({uploadFile.completedChunks} of {uploadFile.totalChunks} chunks) |{' '}
                    {uploadFile.elapsedTime ? formatTime(uploadFile.elapsedTime) : '00:00:00'} elapsed |{' '}
                    {uploadFile.speed ? formatSpeed(uploadFile.speed) : '0.0 MB/s'}
                    {uploadFile.estimatedTimeRemaining && uploadFile.estimatedTimeRemaining > 0 && (
                      <> | ~{formatTime(uploadFile.estimatedTimeRemaining * 1000)} remaining</>
                    )}
                  </Typography>
                )}

                {/* Completion Summary */}
                {uploadFile.status === 'completed' && uploadFile.elapsedTime && (
                  <Alert severity="success" sx={{ mt: 1, py: 0 }}>
                    <Typography variant="caption">
                      Completed in {formatTime(uploadFile.elapsedTime)} | Average speed: {formatSpeed(uploadFile.speed || 0)}
                    </Typography>
                  </Alert>
                )}

                {/* Error State */}
                {uploadFile.status === 'error' && (
                  <Alert severity="error" sx={{ mt: 1, py: 0 }}>
                    <Typography variant="caption">
                      {uploadFile.error || 'Upload failed'} ({uploadFile.failedChunks.length} failed chunk(s))
                    </Typography>
                  </Alert>
                )}

                {/* Action Buttons */}
                <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                  {uploadFile.status === 'uploading' && (
                    <Button
                      size="small"
                      variant="outlined"
                      color="warning"
                      startIcon={<CancelIcon />}
                      onClick={() => handleCancel(index)}
                    >
                      Cancel
                    </Button>
                  )}
                  {uploadFile.status === 'error' && uploadFile.failedChunks.length > 0 && (
                    <Button
                      size="small"
                      variant="contained"
                      color="warning"
                      startIcon={<RefreshIcon />}
                      onClick={() => handleRetryFailed(index)}
                    >
                      Retry Failed ({uploadFile.failedChunks.length})
                    </Button>
                  )}
                  <Button
                    size="small"
                    variant="outlined"
                    color="error"
                    startIcon={<DeleteIcon />}
                    onClick={() => handleCancel(index)}
                    disabled={uploadFile.status === 'uploading'}
                  >
                    Remove
                  </Button>
                </Box>
              </Paper>
            ))}
          </Box>
        )}

        {/* Notification Log */}
        {notifications.length > 0 && (
          <Box sx={{ mt: 2 }}>
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                cursor: 'pointer',
                py: 1,
              }}
              onClick={() => setNotificationsExpanded(!notificationsExpanded)}
            >
              <Typography variant="subtitle2" color="text.secondary">
                Upload Log ({notifications.length} messages)
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Button size="small" onClick={(e) => { e.stopPropagation(); clearNotifications(); }}>
                  Clear
                </Button>
                {notificationsExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
              </Box>
            </Box>

            <Collapse in={notificationsExpanded}>
              <Box
                sx={{
                  maxHeight: 200,
                  overflow: 'auto',
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  p: 1,
                }}
              >
                {notifications.map((notif) => (
                  <Alert
                    key={notif.id}
                    severity={notif.type}
                    sx={{ mb: 0.5, py: 0, '& .MuiAlert-message': { py: 0.5 } }}
                    icon={false}
                  >
                    <Typography variant="caption" component="div">
                      <span style={{ color: '#888' }}>
                        [{new Date(notif.timestamp).toLocaleTimeString()}]
                      </span>
                      {notif.fileName && <strong> {notif.fileName}:</strong>}
                      {' '}{notif.message}
                    </Typography>
                  </Alert>
                ))}
              </Box>
            </Collapse>
          </Box>
        )}
      </Paper>
    </Box>
  );
}

// ============ Custom Error Class ============

class UploadError extends Error {
  httpStatus?: number;

  constructor(message: string, httpStatus?: number) {
    super(message);
    this.name = 'UploadError';
    this.httpStatus = httpStatus;
  }
}
