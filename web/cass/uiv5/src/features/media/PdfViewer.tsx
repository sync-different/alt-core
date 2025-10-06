/**
 * PDF Viewer Component
 * In-page PDF viewer that occupies the main content area
 */

import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { Box, IconButton, Typography, Paper } from '@mui/material';
import {
  Close as CloseIcon,
  Download as DownloadIcon,
  OpenInNew as OpenInNewIcon,
  ChevronLeft as ChevronLeftIcon,
  ChevronRight as ChevronRightIcon,
} from '@mui/icons-material';
import type { File } from '../../types/models';
import { formatDate, formatFileSize } from '../../utils/formatters';
import { setCurrentFile, clearCurrentFile } from '../../store/slices/viewerSlice';
import { buildUrl } from '../../utils/urlHelper';
import { RightSidebar, RIGHT_SIDEBAR_WIDTH } from '../../components/layout/RightSidebar';
import { useState } from 'react';

interface PdfViewerProps {
  onClose: () => void;
  file: File;
}

export function PdfViewer({ onClose, file }: PdfViewerProps) {
  const dispatch = useDispatch();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Set current file for context-aware chat
  useEffect(() => {
    dispatch(setCurrentFile(file));
    return () => {
      dispatch(clearCurrentFile());
    };
  }, [file, dispatch]);
  // Get PDF URL with UUID authentication
  const getPdfUrl = () => {
    const uuid = localStorage.getItem('uuid');
    let url = file.file_path_webapp || '';

    if (!url) return '';

    // Add UUID as query parameter
    const separator = url.includes('?') ? '&' : '?';
    url = `${url}${separator}uuid=${uuid}`;

    // Build URL with proper base
    return buildUrl(url);
  };

  // Handle download
  const handleDownload = () => {
    if (file) {
      window.open(getPdfUrl(), '_blank');
    }
  };

  // Handle open in new tab
  const handleOpenInNew = () => {
    if (file) {
      window.open(getPdfUrl(), '_blank');
    }
  };

  return (
    <>
      <Paper
        elevation={0}
        sx={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: sidebarOpen ? RIGHT_SIDEBAR_WIDTH : 0,
          bottom: 0,
          zIndex: 1300,
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: '#525659',
          transition: 'right 0.3s',
        }}
      >
      {/* Top Bar */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: 2,
          backgroundColor: 'white',
          borderBottom: '1px solid #e0e0e0',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <Box>
          <Typography variant="h6">{file.name}</Typography>
          <Typography variant="body2" color="text.secondary">
            {formatFileSize(file.file_size)} • {formatDate(file.file_date_long)}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 1, position: 'relative', zIndex: 2 }}>
          <IconButton
            onClick={() => setSidebarOpen(!sidebarOpen)}
            title="Toggle sidebar"
            sx={{
              backgroundColor: 'rgba(0, 64, 128, 0.8)',
              '&:hover': {
                backgroundColor: 'rgba(0, 64, 128, 1)',
              },
            }}
          >
            {sidebarOpen ? <ChevronRightIcon /> : <ChevronLeftIcon />}
          </IconButton>
          <IconButton onClick={handleOpenInNew} title="Open in new tab">
            <OpenInNewIcon />
          </IconButton>
          <IconButton onClick={handleDownload} title="Download">
            <DownloadIcon />
          </IconButton>
          <IconButton onClick={onClose} title="Close">
            <CloseIcon />
          </IconButton>
        </Box>
      </Box>

      {/* PDF Display */}
      <Box sx={{ flex: 1, overflow: 'hidden', position: 'relative', zIndex: 0 }}>
        <iframe
          src={getPdfUrl()}
          style={{
            width: '100%',
            height: '100%',
            border: 'none',
          }}
          title={file.file_name}
        />
      </Box>
    </Paper>

    {/* Right Sidebar for chat, tags, etc. */}
    <Box
      sx={{
        position: 'fixed',
        top: 0,
        right: 0,
        bottom: 0,
        width: sidebarOpen ? RIGHT_SIDEBAR_WIDTH : 0,
        zIndex: 1301,
      }}
    >
      <RightSidebar fullscreen={true} externalOpen={sidebarOpen} onOpenChange={setSidebarOpen} />
    </Box>
    </>
  );
}
