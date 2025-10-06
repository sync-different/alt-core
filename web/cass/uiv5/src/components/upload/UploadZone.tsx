/**
 * Upload Zone Component
 * Provides drag-and-drop file upload with chunked upload support
 * Based on the AngularJS Dropzone implementation
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
} from '@mui/material';
import {
  Close as CloseIcon,
  CloudUpload as CloudUploadIcon,
  Add as AddIcon,
  Cancel as CancelIcon,
  Delete as DeleteIcon,
  CheckCircle as CheckCircleIcon,
} from '@mui/icons-material';

interface UploadFile {
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'completed' | 'error';
  error?: string;
  uploadedBytes: number;
  startTime?: number;
  speed?: number;
}

interface UploadZoneProps {
  open: boolean;
  onClose: () => void;
}

export function UploadZone({ open, onClose }: UploadZoneProps) {
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [chunkSize, setChunkSize] = useState(10); // MB
  const [uploadSpeed, setUploadSpeed] = useState('0 KB/s');
  const abortControllersRef = useRef<Map<string, AbortController>>(new Map());

  // Get netty port from server
  const [nettyPort, setNettyPort] = useState(8087);
  useEffect(() => {
    fetch('/cass/nodeinfo.fn')
      .then(res => res.json())
      .then(data => {
        const serverNode = data.nodes.find((n: any) => n.node_type === 'server');
        if (serverNode?.node_nettyport_post) {
          setNettyPort(serverNode.node_nettyport_post);
          console.log('Netty port obtained from server:', serverNode.node_nettyport_post);
        }
      })
      .catch(err => console.error('Failed to get netty port:', err));
  }, []);

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const newFiles: UploadFile[] = acceptedFiles.map(file => ({
      file,
      progress: 0,
      status: 'pending',
      uploadedBytes: 0,
    }));
    setFiles(prev => [...prev, ...newFiles]);
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    noClick: true, // We'll use a button instead
  });

  const uploadFile = async (uploadFile: UploadFile, index: number) => {
    const { file } = uploadFile;
    const chunkSizeBytes = chunkSize * 1024 * 1024;
    const totalChunks = Math.ceil(file.size / chunkSizeBytes);
    const abortController = new AbortController();
    abortControllersRef.current.set(file.name, abortController);

    const startTime = Date.now();

    // Determine upload URL based on protocol
    const protocol = window.location.protocol;
    const hostname = window.location.hostname;
    const isHttps = protocol === 'https:';

    try {
      setFiles(prev => prev.map((f, i) =>
        i === index ? { ...f, status: 'uploading', startTime } : f
      ));

      for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
        const start = chunkIndex * chunkSizeBytes;
        const end = Math.min(start + chunkSizeBytes, file.size);
        const chunk = file.slice(start, end);

        const formData = new FormData();
        const paramName = `upload.${file.name}.${totalChunks}.${chunkIndex + 1}.p`;

        // Create a new File object from the chunk with the .p extension
        const chunkFile = new File([chunk], paramName, { type: file.type });
        formData.append(paramName, chunkFile);

        // Determine URL based on protocol:
        // HTTP: use netty port (8087) with /formpost
        // HTTPS: use standard https with filename in path
        let url: string;
        const isDevelopment = window.location.port === '5173';

        if (isHttps) {
          url = `https://${hostname}/${paramName}`;
        } else {
          // HTTP: use proxy in development, direct port 8087 in production
          url = isDevelopment
            ? '/formpost'
            : `http://${hostname}:${nettyPort}/formpost`;
        }

        const response = await fetch(url, {
          method: 'POST',
          body: formData,
          signal: abortController.signal,
          // Only include credentials in development (using proxy)
          credentials: isDevelopment ? 'include' : 'omit',
        });

        if (!response.ok) {
          throw new Error(`Upload failed: ${response.statusText}`);
        }

        const uploadedBytes = end;
        const progress = (uploadedBytes / file.size) * 100;
        const elapsed = (Date.now() - startTime) / 1000; // seconds
        const uploadedMB = uploadedBytes / (1024 * 1024);
        const speedMbps = (uploadedMB * 8) / elapsed;
        const speedKBs = speedMbps * 122.07;

        setFiles(prev => prev.map((f, i) =>
          i === index ? {
            ...f,
            progress,
            uploadedBytes,
            speed: speedKBs
          } : f
        ));
        setUploadSpeed(`${speedKBs.toFixed(0)} KB/s`);
      }

      setFiles(prev => prev.map((f, i) =>
        i === index ? { ...f, status: 'completed', progress: 100 } : f
      ));
    } catch (error: any) {
      if (error.name !== 'AbortError') {
        setFiles(prev => prev.map((f, i) =>
          i === index ? {
            ...f,
            status: 'error',
            error: error.message || 'Upload failed'
          } : f
        ));
      }
    } finally {
      abortControllersRef.current.delete(file.name);
    }
  };

  const handleUploadAll = () => {
    files.forEach((file, index) => {
      if (file.status === 'pending') {
        uploadFile(file, index);
      }
    });
  };

  const handleCancel = (index: number) => {
    const file = files[index];
    const controller = abortControllersRef.current.get(file.file.name);
    if (controller) {
      controller.abort();
    }
    setFiles(prev => prev.filter((_, i) => i !== index));
  };

  const handleRemoveAll = () => {
    abortControllersRef.current.forEach(controller => controller.abort());
    abortControllersRef.current.clear();
    setFiles([]);
  };

  const handleClose = () => {
    handleRemoveAll();
    onClose();
  };

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

        <Box sx={{ mb: 3, textAlign: 'center' }}>
          <Typography variant="h6" sx={{ fontWeight: 'bold', color: 'text.secondary', mb: 2 }}>
            Upload Chunk Size
          </Typography>
          <Box sx={{ px: 8 }}>
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
            />
          </Box>
        </Box>

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

        {uploadSpeed !== '0 KB/s' && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Speed: {uploadSpeed}
          </Alert>
        )}

        {files.length > 0 && (
          <Box>
            <Box sx={{ mb: 2, display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
              <Button
                variant="contained"
                color="primary"
                onClick={handleUploadAll}
                disabled={files.every(f => f.status !== 'pending')}
              >
                Upload All
              </Button>
              <Button
                variant="outlined"
                color="error"
                onClick={handleRemoveAll}
              >
                Clear All
              </Button>
            </Box>

            {files.map((uploadFile, index) => (
              <Paper key={index} sx={{ p: 2, mb: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {uploadFile.file.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {(uploadFile.file.size / (1024 * 1024)).toFixed(2)} MB
                    </Typography>
                  </Box>
                  {uploadFile.status === 'completed' && (
                    <CheckCircleIcon color="success" />
                  )}
                </Box>

                {uploadFile.status !== 'pending' && (
                  <LinearProgress
                    variant="determinate"
                    value={uploadFile.progress}
                    sx={{ mb: 1 }}
                  />
                )}

                {uploadFile.status === 'completed' && (
                  <Alert severity="success" sx={{ mt: 1 }}>
                    File uploaded successfully.
                  </Alert>
                )}

                {uploadFile.status === 'error' && (
                  <Alert severity="error" sx={{ mt: 1 }}>
                    {uploadFile.error}
                  </Alert>
                )}

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
                  <Button
                    size="small"
                    variant="outlined"
                    color="error"
                    startIcon={<DeleteIcon />}
                    onClick={() => handleCancel(index)}
                  >
                    Clear
                  </Button>
                </Box>
              </Paper>
            ))}
          </Box>
        )}
      </Paper>
    </Box>
  );
}
