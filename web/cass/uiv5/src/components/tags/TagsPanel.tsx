/**
 * Tags Panel - Shows and manages tags for the current file
 */

import { useState } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import {
  Box,
  TextField,
  Chip,
  Typography,
  Snackbar,
  Alert,
  Stack,
} from '@mui/material';
import { selectCurrentFile, updateCurrentFileTags } from '../../store/slices/viewerSlice';
import { addTags, removeTags } from '../../services/fileApi';
import type { RootState } from '../../store/store';
import type { AppDispatch } from '../../store/store';

export function TagsPanel() {
  const dispatch = useDispatch<AppDispatch>();
  const currentFile = useSelector((state: RootState) => selectCurrentFile(state));
  const [inputValue, setInputValue] = useState('');
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
    open: false,
    message: '',
    severity: 'success',
  });

  const getTags = () => {
    if (!currentFile?.file_tags) return [];
    return currentFile.file_tags.split(',').filter(Boolean);
  };

  const handleKeyDown = async (event: React.KeyboardEvent) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      event.stopPropagation();
      if (inputValue.trim()) {
        await handleAddTag(inputValue.trim());
      }
    }
  };

  const handleAddTag = async (tag: string) => {
    if (!currentFile) return;

    try {
      await addTags([currentFile.nickname], [tag]);

      // Update the current file's tags in the viewer state
      const currentTags = getTags();
      const updatedTags = [...currentTags, tag].join(',');
      dispatch(updateCurrentFileTags(updatedTags));

      setSnackbar({
        open: true,
        message: `Tag "${tag}" added`,
        severity: 'success',
      });
      setInputValue('');
    } catch (error) {
      console.error('Failed to add tag:', error);
      setSnackbar({
        open: true,
        message: 'Failed to add tag',
        severity: 'error',
      });
    }
  };

  const handleRemoveTag = async (tag: string) => {
    if (!currentFile) return;

    try {
      await removeTags([currentFile.nickname], [tag]);

      // Update the current file's tags in the viewer state
      const currentTags = getTags();
      const updatedTags = currentTags.filter(t => t !== tag).join(',');
      dispatch(updateCurrentFileTags(updatedTags));

      setSnackbar({
        open: true,
        message: `Tag "${tag}" removed`,
        severity: 'success',
      });
    } catch (error) {
      console.error('Failed to remove tag:', error);
      setSnackbar({
        open: true,
        message: 'Failed to remove tag',
        severity: 'error',
      });
    }
  };

  if (!currentFile) {
    return (
      <Box sx={{ p: 2, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          No file selected
        </Typography>
      </Box>
    );
  }

  const tags = getTags();

  return (
    <Box sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <TextField
        fullWidth
        size="small"
        placeholder="Add Tags..."
        value={inputValue}
        onChange={(e) => setInputValue(e.target.value)}
        onKeyDown={handleKeyDown}
        sx={{ mb: 2 }}
      />

      <Typography variant="subtitle2" sx={{ mb: 1, color: 'text.secondary' }}>
        Tags ({tags.length})
      </Typography>

      <Stack spacing={1} sx={{ flex: 1, overflow: 'auto' }}>
        {tags.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', mt: 2 }}>
            No tags yet. Add some tags above.
          </Typography>
        ) : (
          tags.map((tag) => (
            <Chip
              key={tag}
              label={tag}
              onDelete={() => handleRemoveTag(tag)}
              sx={{
                justifyContent: 'space-between',
                '& .MuiChip-label': {
                  flexGrow: 1,
                  textAlign: 'left',
                },
              }}
            />
          ))
        )}
      </Stack>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={3000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
