/**
 * File List View - Table Layout
 */

import { useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Checkbox,
  TableSortLabel,
} from '@mui/material';
import { FileListItem } from './FileListItem';
import { ImageViewer } from '../media/ImageViewer';
import { VideoPlayer } from '../media/VideoPlayer';
import { PdfViewer } from '../media/PdfViewer';
import { DocumentViewer } from '../media/DocumentViewer';
import { DownloadProgressModal } from '../../components/download/DownloadProgressModal';
import { useFileSelection } from '../../hooks/useFileSelection';
import { useMediaViewer } from '../../hooks/useMediaViewer';
import { useFileDownload } from '../../hooks/useFileDownload';
import type { File } from '../../types/models';
import type { RootState } from '../../store/store';

type SortField = 'name' | 'date' | 'size';
type SortOrder = 'asc' | 'desc';

interface FileListViewProps {
  files: File[];
  hasMore?: boolean;
  onLoadMore?: () => void;
}

export function FileListView({ files, hasMore, onLoadMore }: FileListViewProps) {
  const listSize = useSelector((state: RootState) => state.files.listSize);
  const { selectedCount, selectAll, deselectAll, isAllSelected } = useFileSelection();
  const [sortField, setSortField] = useState<SortField>('date');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');
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

  const handleSelectAll = () => {
    if (isAllSelected) {
      deselectAll();
    } else {
      selectAll();
    }
  };

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      // Toggle order if clicking the same field
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      // Set new field with default descending order
      setSortField(field);
      setSortOrder('desc');
    }
  };

  const sortedFiles = [...files].sort((a, b) => {
    let comparison = 0;

    switch (sortField) {
      case 'name':
        comparison = a.name.localeCompare(b.name);
        break;
      case 'date':
        comparison = (a.file_date_long || 0) - (b.file_date_long || 0);
        break;
      case 'size':
        comparison = (a.file_size || 0) - (b.file_size || 0);
        break;
    }

    return sortOrder === 'asc' ? comparison : -comparison;
  });

  const handleRowClick = (file: File) => {
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

  // If any media viewer is open, show it in the main content area instead of file list
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

  return (
    <>
      <TableContainer
        component={Paper}
        elevation={0}
        sx={{
          maxHeight: 'calc(100vh - 200px)', // Allow scrolling within the container
        }}
      >
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell
                padding="checkbox"
                sx={{
                  backgroundColor: 'background.paper',
                  fontWeight: 600,
                }}
              >
                <Checkbox
                  checked={isAllSelected && files.length > 0}
                  indeterminate={selectedCount > 0 && !isAllSelected}
                  onChange={handleSelectAll}
                />
              </TableCell>
              <TableCell sx={{ backgroundColor: 'background.paper', fontWeight: 600 }}>
                Thumbnail
              </TableCell>
              <TableCell sx={{ backgroundColor: 'background.paper', fontWeight: 600 }}>
                <TableSortLabel
                  active={sortField === 'name'}
                  direction={sortField === 'name' ? sortOrder : 'asc'}
                  onClick={() => handleSort('name')}
                >
                  Name
                </TableSortLabel>
              </TableCell>
              <TableCell sx={{ backgroundColor: 'background.paper', fontWeight: 600 }}>
                <TableSortLabel
                  active={sortField === 'date'}
                  direction={sortField === 'date' ? sortOrder : 'asc'}
                  onClick={() => handleSort('date')}
                >
                  Date
                </TableSortLabel>
              </TableCell>
              <TableCell sx={{ backgroundColor: 'background.paper', fontWeight: 600 }}>
                <TableSortLabel
                  active={sortField === 'size'}
                  direction={sortField === 'size' ? sortOrder : 'asc'}
                  onClick={() => handleSort('size')}
                >
                  Size
                </TableSortLabel>
              </TableCell>
              <TableCell sx={{ backgroundColor: 'background.paper', fontWeight: 600 }}>
                Tags
              </TableCell>
              <TableCell align="right" sx={{ backgroundColor: 'background.paper', fontWeight: 600 }}>
                Actions
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedFiles.map((file) => (
              <FileListItem key={file.nickname} file={file} onRowClick={handleRowClick} onDownload={startDownload} listSize={listSize} />
            ))}
          </TableBody>
        </Table>
      </TableContainer>

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
