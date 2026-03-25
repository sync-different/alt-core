/**
 * File List View - Table Layout
 */

import { useState, useCallback, useRef } from 'react';
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
  Box,
} from '@mui/material';
import { FileListItem } from './FileListItem';
import { ImageViewer } from '../media/ImageViewer';
import { VideoPlayer } from '../media/VideoPlayer';
import { PdfViewer } from '../media/PdfViewer';
import { DocumentViewer } from '../media/DocumentViewer';
import { useFileSelection } from '../../hooks/useFileSelection';
import { useMediaViewer } from '../../hooks/useMediaViewer';
import { useDownloadManager } from '../../contexts/DownloadManagerContext';
import type { File } from '../../types/models';
import type { RootState } from '../../store/store';

type SortField = 'name' | 'date' | 'size';
type SortOrder = 'asc' | 'desc';

interface FileListViewProps {
  files: File[];
  hasMore?: boolean;
  onLoadMore?: () => void;
}

// Default column widths
const DEFAULT_COLUMN_WIDTHS = {
  thumbnail: 140,
  name: 300,
  date: 140,
  size: 100,
  tags: 200,
  actions: 150,
};

type ColumnKey = keyof typeof DEFAULT_COLUMN_WIDTHS;

export function FileListView({ files, hasMore, onLoadMore }: FileListViewProps) {
  const listSize = useSelector((state: RootState) => state.files.listSize);
  const { selectedCount, selectAll, deselectAll, isAllSelected } = useFileSelection();
  const [sortField, setSortField] = useState<SortField>('date');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');
  const [columnWidths, setColumnWidths] = useState(DEFAULT_COLUMN_WIDTHS);
  const resizingColumn = useRef<ColumnKey | null>(null);
  const startX = useRef(0);
  const startWidth = useRef(0);

  const handleResizeStart = useCallback((column: ColumnKey, event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    resizingColumn.current = column;
    startX.current = event.clientX;
    startWidth.current = columnWidths[column];

    const handleMouseMove = (e: MouseEvent) => {
      if (!resizingColumn.current) return;
      const diff = e.clientX - startX.current;
      const newWidth = Math.max(50, startWidth.current + diff);
      setColumnWidths(prev => ({
        ...prev,
        [resizingColumn.current!]: newWidth,
      }));
    };

    const handleMouseUp = () => {
      resizingColumn.current = null;
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, [columnWidths]);

  // Resizable header cell component
  const ResizableHeaderCell = ({ column, children, align }: { column: ColumnKey; children: React.ReactNode; align?: 'left' | 'right' }) => (
    <TableCell
      align={align}
      sx={{
        backgroundColor: 'background.paper',
        fontWeight: 600,
        width: columnWidths[column],
        position: 'sticky',
        top: 0,
        zIndex: 2,
        userSelect: 'none',
        '&:hover .resize-handle': {
          opacity: 1,
        },
      }}
    >
      {children}
      <Box
        className="resize-handle"
        onMouseDown={(e) => handleResizeStart(column, e)}
        sx={{
          position: 'absolute',
          right: 0,
          top: '25%',
          bottom: '25%',
          width: 4,
          cursor: 'col-resize',
          opacity: 0.4,
          transition: 'opacity 0.2s, background-color 0.2s',
          backgroundColor: 'divider',
          borderRadius: 1,
          '&:hover': {
            backgroundColor: 'primary.main',
            opacity: 1,
          },
          '&::before': {
            content: '""',
            position: 'absolute',
            left: -4,
            right: -4,
            top: -8,
            bottom: -8,
          },
        }}
      />
    </TableCell>
  );
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
  const { addToQueue } = useDownloadManager();

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
          overflowY: 'auto',
          overflowX: 'hidden',
        }}
      >
        <Table size="small" stickyHeader sx={{ tableLayout: 'fixed', width: '100%' }}>
          <TableHead>
            <TableRow>
              <TableCell
                padding="checkbox"
                sx={{
                  backgroundColor: 'background.paper',
                  fontWeight: 600,
                  position: 'sticky',
                  top: 0,
                  zIndex: 2,
                }}
              >
                <Checkbox
                  checked={isAllSelected && files.length > 0}
                  indeterminate={selectedCount > 0 && !isAllSelected}
                  onChange={handleSelectAll}
                />
              </TableCell>
              <ResizableHeaderCell column="thumbnail">
                Thumbnail
              </ResizableHeaderCell>
              <ResizableHeaderCell column="name">
                <TableSortLabel
                  active={sortField === 'name'}
                  direction={sortField === 'name' ? sortOrder : 'asc'}
                  onClick={() => handleSort('name')}
                >
                  Name
                </TableSortLabel>
              </ResizableHeaderCell>
              <ResizableHeaderCell column="date">
                <TableSortLabel
                  active={sortField === 'date'}
                  direction={sortField === 'date' ? sortOrder : 'asc'}
                  onClick={() => handleSort('date')}
                >
                  Date
                </TableSortLabel>
              </ResizableHeaderCell>
              <ResizableHeaderCell column="size">
                <TableSortLabel
                  active={sortField === 'size'}
                  direction={sortField === 'size' ? sortOrder : 'asc'}
                  onClick={() => handleSort('size')}
                >
                  Size
                </TableSortLabel>
              </ResizableHeaderCell>
              <ResizableHeaderCell column="tags">
                Tags
              </ResizableHeaderCell>
              <ResizableHeaderCell column="actions" align="right">
                Actions
              </ResizableHeaderCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedFiles.map((file) => (
              <FileListItem key={file.nickname} file={file} onRowClick={handleRowClick} onDownload={addToQueue} listSize={listSize} columnWidths={columnWidths} />
            ))}
          </TableBody>
        </Table>
      </TableContainer>

    </>
  );
}
