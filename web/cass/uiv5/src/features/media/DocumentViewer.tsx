/**
 * Document Viewer Modal
 * Full-screen document viewer with download and actions
 */

import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { Dialog, Box, IconButton, Typography, Paper } from '@mui/material';
import {
  Close as CloseIcon,
  Download as DownloadIcon,
  Description as DocumentIcon,
} from '@mui/icons-material';
import type { File } from '../../types/models';
import { formatDate, formatFileSize } from '../../utils/formatters';
import { setCurrentFile, clearCurrentFile } from '../../store/slices/viewerSlice';

interface DocumentViewerProps {
  open: boolean;
  onClose: () => void;
  file: File;
}

export function DocumentViewer({ open, onClose, file }: DocumentViewerProps) {
  const dispatch = useDispatch();

  // Set current file for context-aware chat
  useEffect(() => {
    if (open) {
      dispatch(setCurrentFile(file));
    }
    return () => {
      dispatch(clearCurrentFile());
    };
  }, [file, open, dispatch]);
  // Handle download
  const handleDownload = () => {
    if (file) {
      const link = document.createElement('a');
      link.href = file.file_path_webapp || '';
      link.download = file.file_name;
      link.click();
    }
  };

  // Get file icon based on extension
  const getFileIcon = () => {
    // For future: can customize icons based on document type
    return <DocumentIcon sx={{ fontSize: 120, color: 'primary.main' }} />;
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{
        sx: {
          minHeight: '60vh',
        },
      }}
    >
      {/* Top Bar */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: 2,
          borderBottom: '1px solid #e0e0e0',
        }}
      >
        <Typography variant="h6">{file.name}</Typography>

        <Box sx={{ display: 'flex', gap: 1 }}>
          <IconButton onClick={handleDownload} title="Download">
            <DownloadIcon />
          </IconButton>
          <IconButton onClick={onClose} title="Close">
            <CloseIcon />
          </IconButton>
        </Box>
      </Box>

      {/* Document Info */}
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 4,
        }}
      >
        <Paper
          elevation={0}
          sx={{
            padding: 4,
            textAlign: 'center',
            backgroundColor: '#f5f5f5',
            width: '100%',
          }}
        >
          {getFileIcon()}

          <Typography variant="h5" sx={{ mt: 2, mb: 1 }}>
            {file.file_name}
          </Typography>

          <Typography variant="body1" color="text.secondary" sx={{ mb: 1 }}>
            {file.file_ext?.toUpperCase()} Document
          </Typography>

          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            {formatFileSize(file.file_size)} • {formatDate(file.file_date_long)}
          </Typography>

          <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
            <IconButton
              onClick={handleDownload}
              sx={{
                backgroundColor: 'primary.main',
                color: 'white',
                '&:hover': {
                  backgroundColor: 'primary.dark',
                },
              }}
            >
              <DownloadIcon />
            </IconButton>
          </Box>

          <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block' }}>
            Download to view this document
          </Typography>
        </Paper>
      </Box>
    </Dialog>
  );
}
