/**
 * File List Container
 * Manages file display and infinite scroll
 */

import { useSelector } from 'react-redux';
import { Box, CircularProgress, Typography } from '@mui/material';
import { Folder as FolderIcon } from '@mui/icons-material';
import { FileListView } from './FileListView';
import { FileGridView } from './FileGridView';
import type { RootState } from '../../store/store';

interface FileListProps {
  observerRef: React.RefObject<HTMLDivElement | null>;
  hasMore?: boolean;
  onLoadMore?: () => void;
}

export function FileList({ observerRef, hasMore, onLoadMore }: FileListProps) {
  const files = useSelector((state: RootState) => state.files.files);
  const loading = useSelector((state: RootState) => state.files.loading);
  const viewMode = useSelector((state: RootState) => state.files.viewMode);

  if (loading && files.length === 0) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: 400,
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  if (files.length === 0 && !loading) {
    return (
      <Box
        sx={{
          textAlign: 'center',
          py: 8,
        }}
      >
        <FolderIcon sx={{ fontSize: 80, color: '#ccc', mb: 2 }} />
        <Typography variant="h6" color="text.secondary">
          No files found
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Try adjusting your filters or search query
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      {viewMode === 'list' && <FileListView files={files} hasMore={hasMore} onLoadMore={onLoadMore} />}
      {viewMode === 'grid' && <FileGridView files={files} hasMore={hasMore} onLoadMore={onLoadMore} />}

      {/* Infinite scroll sentinel */}
      <Box ref={observerRef} sx={{ height: 20, mt: 2 }} />

      {loading && files.length > 0 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
          <CircularProgress size={24} />
        </Box>
      )}
    </Box>
  );
}
