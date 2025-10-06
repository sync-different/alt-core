/**
 * Selection Toolbar
 * Displays actions available for selected files
 */

import { useState } from 'react';
import { useDispatch } from 'react-redux';
import {
  Box,
  Toolbar,
  Typography,
  IconButton,
  Tooltip,
  Snackbar,
  Alert,
} from '@mui/material';
import {
  Close as CloseIcon,
  Download as DownloadIcon,
  Label as LabelIcon,
  Share as ShareIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { useFileSelection } from '../../hooks/useFileSelection';
import { TagDialog } from '../tags/TagDialog';
import { ShareDialog } from '../share/ShareDialog';
import { addTags, downloadMultipleFiles } from '../../services/fileApi';
import { resetFiles } from '../../store/slices/filesSlice';
import type { AppDispatch } from '../../store/store';

interface SelectionToolbarProps {
  inline?: boolean;
}

export function SelectionToolbar({ inline = false }: SelectionToolbarProps) {
  const dispatch = useDispatch<AppDispatch>();
  const { selectedCount, selectedFileIds, deselectAll } = useFileSelection();
  const [tagDialogOpen, setTagDialogOpen] = useState(false);
  const [shareDialogOpen, setShareDialogOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
    open: false,
    message: '',
    severity: 'success',
  });

  const handleAddTags = async (tags: string[]) => {
    try {
      await addTags(selectedFileIds, tags);
      setSnackbar({
        open: true,
        message: `Tags added to ${selectedFileIds.length} file${selectedFileIds.length !== 1 ? 's' : ''}`,
        severity: 'success',
      });
      deselectAll();
      // Refresh the file list to show updated tags
      dispatch(resetFiles());
    } catch (error) {
      console.error('Failed to add tags:', error);
      setSnackbar({
        open: true,
        message: 'Failed to add tags',
        severity: 'error',
      });
    }
  };

  const handleDownload = async () => {
    try {
      await downloadMultipleFiles(selectedFileIds);
      setSnackbar({
        open: true,
        message: `Downloading ${selectedFileIds.length} file${selectedFileIds.length !== 1 ? 's' : ''}`,
        severity: 'success',
      });
    } catch (error) {
      console.error('Failed to download files:', error);
      setSnackbar({
        open: true,
        message: 'Failed to download files',
        severity: 'error',
      });
    }
  };

  if (selectedCount === 0) {
    return null;
  }

  const toolbarContent = (
    <>
      <IconButton
        edge="start"
        color="inherit"
        onClick={deselectAll}
        sx={{ mr: 1 }}
      >
        <CloseIcon />
      </IconButton>

      <Typography variant={inline ? "subtitle1" : "h6"} sx={{ mr: 2 }}>
        {selectedCount} {selectedCount === 1 ? 'file' : 'files'} selected
      </Typography>

      <Tooltip title="Add Tags">
        <IconButton color="inherit" onClick={() => setTagDialogOpen(true)} size={inline ? "small" : "medium"}>
          <LabelIcon />
        </IconButton>
      </Tooltip>

      <Tooltip title="Download">
        <IconButton color="inherit" onClick={handleDownload} size={inline ? "small" : "medium"}>
          <DownloadIcon />
        </IconButton>
      </Tooltip>

      <Tooltip title="Share">
        <IconButton color="inherit" onClick={() => setShareDialogOpen(true)} size={inline ? "small" : "medium"}>
          <ShareIcon />
        </IconButton>
      </Tooltip>

      <Tooltip title="Delete">
        <IconButton color="inherit" size={inline ? "small" : "medium"}>
          <DeleteIcon />
        </IconButton>
      </Tooltip>
    </>
  );

  return (
    <>
      {inline ? (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            px: 2,
            py: 0.5,
            borderRadius: 1,
            height: 48,
          }}
        >
          {toolbarContent}
        </Box>
      ) : (
        <Box
          sx={{
            position: 'sticky',
            top: 0,
            zIndex: 1100,
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            mb: 2,
          }}
        >
          <Toolbar>
            {toolbarContent}
          </Toolbar>
        </Box>
      )}

      <TagDialog
        open={tagDialogOpen}
        onClose={() => setTagDialogOpen(false)}
        onSave={handleAddTags}
        selectedFileIds={selectedFileIds}
      />

      <ShareDialog
        open={shareDialogOpen}
        onClose={() => setShareDialogOpen(false)}
        fileIds={selectedFileIds}
      />

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
}
