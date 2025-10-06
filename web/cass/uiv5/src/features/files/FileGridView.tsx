/**
 * File Grid View - Grid Layout
 * Displays files as a responsive grid of cards
 */

import { useSelector } from 'react-redux';
import { Box, Grid } from '@mui/material';
import { FileCard } from './FileCard';
import { ImageViewer } from '../media/ImageViewer';
import { VideoPlayer } from '../media/VideoPlayer';
import { PdfViewer } from '../media/PdfViewer';
import { DocumentViewer } from '../media/DocumentViewer';
import { DownloadProgressModal } from '../../components/download/DownloadProgressModal';
import { useMediaViewer } from '../../hooks/useMediaViewer';
import { useFileDownload } from '../../hooks/useFileDownload';
import type { File } from '../../types/models';
import type { RootState } from '../../store/store';

interface FileGridViewProps {
  files: File[];
  hasMore?: boolean;
  onLoadMore?: () => void;
}

export function FileGridView({ files, hasMore, onLoadMore }: FileGridViewProps) {
  const showGridDetails = useSelector((state: RootState) => state.files.showGridDetails);
  const gridSize = useSelector((state: RootState) => state.files.gridSize);
  const {
    imageViewerOpen,
    currentImageIndex,
    openImageViewer,
    closeImageViewer,
    videoPlayerOpen,
    currentVideoFile,
    openVideoPlayer,
    closeVideoPlayer,
    pdfViewerOpen,
    currentPdfFile,
    openPdfViewer,
    closePdfViewer,
    documentViewerOpen,
    currentDocumentFile,
    openDocumentViewer,
    closeDocumentViewer,
  } = useMediaViewer();
  const { isDownloading, downloadProgress, isComplete, currentFile, startDownload, cancelDownload, closeModal } =
    useFileDownload();

  // Filter only image files for the viewer
  const imageFiles = files.filter(f => f.file_group === 'photo');

  const handleCardClick = (file: File) => {
    console.log('File clicked:', file);
    console.log('file_ext:', file.file_ext);
    console.log('file_group:', file.file_group);
    console.log('file_name:', file.name);

    if (file.file_group === 'photo') {
      const index = imageFiles.findIndex(f => f.nickname === file.nickname);
      if (index !== -1) {
        openImageViewer(index);
      }
    } else if (file.file_group === 'movie') {
      openVideoPlayer(file);
    } else if (file.file_ext?.toLowerCase() === '.pdf' || file.name?.toLowerCase().endsWith('.pdf')) {
      console.log('Opening PDF viewer');
      openPdfViewer(file);
    } else if (file.file_group === 'document') {
      openDocumentViewer(file);
    }
  };

  // If any media viewer is open, show it in the main content area instead of file grid
  if (imageViewerOpen && imageFiles.length > 0) {
    return (
      <ImageViewer
        open={imageViewerOpen}
        onClose={closeImageViewer}
        files={imageFiles}
        initialIndex={currentImageIndex}
        hasMore={hasMore}
        onLoadMore={onLoadMore}
      />
    );
  }

  if (videoPlayerOpen && currentVideoFile) {
    return (
      <VideoPlayer
        open={videoPlayerOpen}
        onClose={closeVideoPlayer}
        file={currentVideoFile}
      />
    );
  }

  if (pdfViewerOpen && currentPdfFile) {
    return (
      <PdfViewer
        onClose={closePdfViewer}
        file={currentPdfFile}
      />
    );
  }

  if (documentViewerOpen && currentDocumentFile) {
    return (
      <DocumentViewer
        open={documentViewerOpen}
        onClose={closeDocumentViewer}
        file={currentDocumentFile}
      />
    );
  }

  // Grid size responsive breakpoints
  const getGridSize = () => {
    switch (gridSize) {
      case 'xs':
        return { xs: 4, sm: 3, md: 2, lg: 1.5, xl: 1 };
      case 'small':
        return { xs: 6, sm: 4, md: 3, lg: 2, xl: 1.5 };
      case 'medium':
        return { xs: 12, sm: 6, md: 4, lg: 3, xl: 2 };
      case 'large':
        return { xs: 12, sm: 12, md: 6, lg: 4, xl: 3 };
    }
  };

  return (
    <>
      <Box sx={{ width: '100%' }}>
        <Grid container spacing={0}>
          {files.map((file) => (
            <Grid size={getGridSize()} key={file.nickname}>
              <FileCard file={file} onCardClick={handleCardClick} onDownload={startDownload} showDetails={showGridDetails} gridSize={gridSize} />
            </Grid>
          ))}
        </Grid>
      </Box>

      <DownloadProgressModal
        open={isDownloading || isComplete}
        fileName={currentFile?.name || ''}
        progress={downloadProgress}
        onCancel={isComplete ? closeModal : cancelDownload}
        isComplete={isComplete}
      />
    </>
  );
}
