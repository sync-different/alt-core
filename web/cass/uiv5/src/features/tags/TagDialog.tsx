/**
 * Tag Dialog
 * Modal for adding/removing tags to selected files
 */

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Chip,
  Box,
  Autocomplete,
  Typography,
} from '@mui/material';
import { fetchTags } from '../../services/fileApi';

interface TagDialogProps {
  open: boolean;
  onClose: () => void;
  onSave: (tags: string[]) => void;
  selectedFileIds: string[];
}

export function TagDialog({ open, onClose, onSave, selectedFileIds }: TagDialogProps) {
  const [availableTags, setAvailableTags] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [inputValue, setInputValue] = useState('');

  useEffect(() => {
    if (open) {
      loadTags();
    }
  }, [open]);

  const loadTags = async () => {
    try {
      const tags = await fetchTags();
      setAvailableTags(tags.map(t => t.tag));
    } catch (error) {
      console.error('Failed to load tags:', error);
    }
  };

  const handleSave = () => {
    if (selectedTags.length > 0) {
      onSave(selectedTags);
      handleClose();
    }
  };

  const handleClose = () => {
    setSelectedTags([]);
    setInputValue('');
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        Add Tags to {selectedFileIds.length} File{selectedFileIds.length !== 1 ? 's' : ''}
      </DialogTitle>
      <DialogContent>
        <Box sx={{ pt: 2 }}>
          <Autocomplete
            multiple
            freeSolo
            options={availableTags}
            value={selectedTags}
            onChange={(_, newValue) => setSelectedTags(newValue)}
            inputValue={inputValue}
            onInputChange={(_, newInputValue) => setInputValue(newInputValue)}
            renderTags={(value, getTagProps) =>
              value.map((option, index) => (
                <Chip
                  label={option}
                  {...getTagProps({ index })}
                  key={option}
                />
              ))
            }
            renderInput={(params) => (
              <TextField
                {...params}
                label="Tags"
                placeholder="Type or select tags"
                helperText="Press Enter to add a new tag"
              />
            )}
          />
          <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
            You can type new tags or select from existing ones
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <Button onClick={handleSave} variant="contained" disabled={selectedTags.length === 0}>
          Add Tags
        </Button>
      </DialogActions>
    </Dialog>
  );
}
