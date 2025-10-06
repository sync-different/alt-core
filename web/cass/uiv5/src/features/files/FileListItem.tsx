/**
 * File List Item - Single Row in Table
 */

import { useState } from 'react';
import { useDispatch } from 'react-redux';
import {
  TableRow,
  TableCell,
  Checkbox,
  Box,
  Chip,
  IconButton,
  Tooltip,
  Avatar,
  Snackbar,
  Alert,
  TextField,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Autocomplete,
} from '@mui/material';
import {
  Download as DownloadIcon,
  OpenInNew as OpenIcon,
  Folder as FolderIcon,
  Image as ImageIcon,
  MusicNote as MusicIcon,
  VideoLibrary as VideoIcon,
  Description as DocumentIcon,
  Add as AddIcon,
  QueueMusic as QueueMusicIcon,
} from '@mui/icons-material';
import { formatDate, formatFileSize } from '../../utils/formatters';
import { useFileSelection } from '../../hooks/useFileSelection';
import { removeTags, addTags } from '../../services/fileApi';
import { setFilters, updateFileTags } from '../../store/slices/filesSlice';
import { addFiles as addToPlaylist, setOpen as setPlaylistOpen } from '../../store/slices/playlistSlice';
import type { File } from '../../types/models';
import type { AppDispatch, RootState } from '../../store/store';
import { useSelector } from 'react-redux';

interface FileListItemProps {
  file: File;
  onRowClick?: (file: File) => void;
  onDownload?: (file: File) => void;
  listSize?: 'xs' | 'small' | 'medium' | 'large';
}

export function FileListItem({ file, onRowClick, onDownload, listSize = 'medium' }: FileListItemProps) {
  const dispatch = useDispatch<AppDispatch>();
  const { isSelected, toggleSelect, handleClick } = useFileSelection();
  const selected = isSelected(file.nickname);
  const tags = useSelector((state: RootState) => state.tags.tags);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
    open: false,
    message: '',
    severity: 'success',
  });
  const [addTagDialogOpen, setAddTagDialogOpen] = useState(false);
  const [newTagName, setNewTagName] = useState('');
  const [showAllTags, setShowAllTags] = useState(false);

  // Calculate dimensions based on list size
  const thumbnailSize = listSize === 'xs' ? 40 : listSize === 'small' ? 60 : listSize === 'medium' ? 120 : 160;
  const rowHeight = listSize === 'xs' ? 60 : listSize === 'small' ? 80 : listSize === 'medium' ? 140 : 180;
  const cellPadding = listSize === 'xs' ? '4px' : listSize === 'small' ? '8px' : listSize === 'medium' ? '16px' : '20px';
  const iconFontSize = listSize === 'xs' ? 30 : listSize === 'small' ? 40 : listSize === 'medium' ? 80 : 100;
  const chipSize = listSize === 'xs' || listSize === 'small' ? 'small' : 'medium';
  const fontSize = listSize === 'xs' ? '0.75rem' : listSize === 'small' ? '0.875rem' : listSize === 'medium' ? '1rem' : '1.125rem';
  const maxTags = listSize === 'xs' || listSize === 'small' ? 2 : 3;

  const handleCheckboxChange = () => {
    toggleSelect(file.nickname);
  };

  const handleRowClick = (event: React.MouseEvent) => {
    // Don't trigger selection if clicking on action buttons
    if ((event.target as HTMLElement).closest('.actions, .MuiCheckbox-root')) {
      return;
    }

    // If multi-selecting (shift/ctrl/cmd), use selection handler
    if (event.shiftKey || event.ctrlKey || event.metaKey) {
      handleClick(file.nickname, event);
      return;
    }

    // If custom click handler provided, use it (e.g., open viewer)
    if (onRowClick) {
      onRowClick(file);
    } else {
      // Fallback to selection
      handleClick(file.nickname, event);
    }
  };

  const handleDownload = (event: React.MouseEvent) => {
    event.stopPropagation();
    if (onDownload) {
      onDownload(file);
    }
  };

  const getThumbnail = () => {
    if (file.file_thumbnail) {
      return (
        <Avatar
          src={`data:image/jpeg;base64,${file.file_thumbnail}`}
          variant="rounded"
          sx={{ width: thumbnailSize, height: thumbnailSize }}
        />
      );
    }

    // Show icon based on file type
    const iconProps = { sx: { fontSize: iconFontSize } };
    switch (file.file_group) {
      case 'photo':
        return <ImageIcon {...iconProps} color="primary" />;
      case 'music':
        return <MusicIcon {...iconProps} color="secondary" />;
      case 'movie':
        return <VideoIcon {...iconProps} color="error" />;
      case 'document':
        return <DocumentIcon {...iconProps} color="action" />;
      default:
        return <DocumentIcon {...iconProps} color="action" />;
    }
  };

  const getTags = () => {
    if (!file.file_tags) return [];
    return file.file_tags.split(',').filter(Boolean);
  };

  const handleTagClick = (event: React.MouseEvent, tag: string) => {
    event.stopPropagation();
    // Search for this tag
    dispatch(setFilters({ searchQuery: tag }));
  };

  const handleRemoveTag = async (event: React.MouseEvent, tag: string) => {
    event.stopPropagation();
    try {
      await removeTags([file.nickname], [tag]);
      setSnackbar({
        open: true,
        message: `Tag "${tag}" removed`,
        severity: 'success',
      });
      // Update the file's tags locally without full refresh
      const currentTags = getTags();
      const updatedTags = currentTags.filter(t => t !== tag).join(',');
      dispatch(updateFileTags({ fileId: file.nickname, tags: updatedTags }));
    } catch (error) {
      console.error('Failed to remove tag:', error);
      setSnackbar({
        open: true,
        message: 'Failed to remove tag',
        severity: 'error',
      });
    }
  };

  const handleAddTagClick = (event: React.MouseEvent) => {
    event.stopPropagation();
    setAddTagDialogOpen(true);
  };

  const handleAddTagClose = () => {
    setAddTagDialogOpen(false);
    setNewTagName('');
  };

  const handleAddTagSubmit = async () => {
    if (!newTagName.trim()) return;

    try {
      console.log('Adding tag:', newTagName.trim(), 'to file:', file.nickname);
      await addTags([file.nickname], [newTagName.trim()]);
      setSnackbar({
        open: true,
        message: `Tag "${newTagName}" added`,
        severity: 'success',
      });
      handleAddTagClose();

      // Update the file's tags locally without full refresh
      const currentTags = getTags();
      const updatedTags = [...currentTags, newTagName.trim()].join(',');
      dispatch(updateFileTags({ fileId: file.nickname, tags: updatedTags }));
    } catch (error: any) {
      console.error('Failed to add tag:', error);
      console.error('Error details:', error.response?.data, error.message);
      setSnackbar({
        open: true,
        message: `Failed to add tag: ${error.message || 'Unknown error'}`,
        severity: 'error',
      });
    }
  };

  const handleAddToPlaylist = (event: React.MouseEvent) => {
    event.stopPropagation();
    dispatch(addToPlaylist([file]));
    dispatch(setPlaylistOpen(true));
    setSnackbar({
      open: true,
      message: 'Added to playlist',
      severity: 'success',
    });
  };

  const handleShowAllTags = (event: React.MouseEvent) => {
    event.stopPropagation();
    setShowAllTags(!showAllTags);
  };

  return (
    <TableRow
      hover
      selected={selected}
      onClick={handleRowClick}
      sx={{
        '&:hover .actions': { opacity: 1 },
        cursor: 'pointer',
        height: rowHeight,
        '& .MuiTableCell-root': {
          padding: cellPadding,
        },
      }}
    >
      <TableCell padding="checkbox">
        <Checkbox checked={selected} onChange={handleCheckboxChange} />
      </TableCell>

      <TableCell>
        <Box sx={{ display: 'flex', alignItems: 'center', py: 1 }}>
          {getThumbnail()}
        </Box>
      </TableCell>

      <TableCell>
        <Tooltip title={file.name}>
          <Box
            sx={{
              maxWidth: 300,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              cursor: 'pointer',
              fontSize,
              '&:hover': { textDecoration: 'underline' },
            }}
          >
            {file.name}
          </Box>
        </Tooltip>
      </TableCell>

      <TableCell sx={{ fontSize }}>{formatDate(file.file_date_long)}</TableCell>

      <TableCell sx={{ fontSize }}>{formatFileSize(file.file_size)}</TableCell>

      <TableCell>
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', alignItems: 'center' }}>
          {(showAllTags ? getTags() : getTags().slice(0, maxTags)).map((tag) => (
            <Chip
              key={tag}
              label={tag}
              size={chipSize as 'small' | 'medium'}
              variant="filled"
              onClick={(event) => handleTagClick(event, tag)}
              onDelete={(event) => handleRemoveTag(event, tag)}
              sx={{
                backgroundColor: 'primary.main',
                color: 'white',
                fontWeight: 500,
                fontSize: listSize === 'small' ? '0.75rem' : '0.8125rem',
                cursor: 'pointer',
                border: '2px solid',
                borderColor: '#003366',
                borderRadius: '4px 12px 12px 4px',
                '& .MuiChip-label': {
                  px: listSize === 'small' ? 1 : 1.5,
                },
                '& .MuiChip-deleteIcon': {
                  color: 'rgba(255, 255, 255, 0.7)',
                  opacity: 0,
                  transition: 'opacity 0.2s',
                  '&:hover': {
                    color: 'white',
                  },
                },
                '&:hover': {
                  backgroundColor: 'primary.dark',
                },
                '&:hover .MuiChip-deleteIcon': {
                  opacity: 1,
                },
              }}
            />
          ))}
          {!showAllTags && getTags().length > maxTags && (
            <Chip
              label={`+${getTags().length - maxTags}`}
              size={chipSize as 'small' | 'medium'}
              variant="filled"
              onClick={handleShowAllTags}
              sx={{
                backgroundColor: 'grey.300',
                color: 'text.secondary',
                fontWeight: 500,
                fontSize: listSize === 'small' ? '0.75rem' : '0.8125rem',
                borderRadius: '4px 12px 12px 4px',
                cursor: 'pointer',
                '& .MuiChip-label': {
                  px: listSize === 'small' ? 1 : 1.5,
                },
                '&:hover': {
                  backgroundColor: 'grey.400',
                },
              }}
            />
          )}
          {showAllTags && getTags().length > maxTags && (
            <Chip
              label="Show less"
              size={chipSize as 'small' | 'medium'}
              variant="filled"
              onClick={handleShowAllTags}
              sx={{
                backgroundColor: 'grey.300',
                color: 'text.secondary',
                fontWeight: 500,
                fontSize: listSize === 'small' ? '0.75rem' : '0.8125rem',
                borderRadius: '4px 12px 12px 4px',
                cursor: 'pointer',
                '& .MuiChip-label': {
                  px: listSize === 'small' ? 1 : 1.5,
                },
                '&:hover': {
                  backgroundColor: 'grey.400',
                },
              }}
            />
          )}
          <IconButton
            size="small"
            onClick={handleAddTagClick}
            sx={{
              width: 24,
              height: 24,
              padding: 0,
              color: 'primary.main',
              '&:hover': {
                backgroundColor: 'primary.lighter',
              },
            }}
          >
            <AddIcon fontSize="small" />
          </IconButton>
        </Box>
      </TableCell>

      <TableCell align="right">
        <Box className="actions" sx={{ opacity: 0, transition: 'opacity 0.2s' }}>
          {file.file_group === 'music' && (
            <Tooltip title="Add to Playlist">
              <IconButton onClick={handleAddToPlaylist}>
                <QueueMusicIcon />
              </IconButton>
            </Tooltip>
          )}
          <Tooltip title="Download">
            <IconButton onClick={handleDownload}>
              <DownloadIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Open">
            <IconButton>
              <OpenIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Open Folder">
            <IconButton>
              <FolderIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </TableCell>

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

      {/* Add Tag Dialog */}
      <Dialog
        open={addTagDialogOpen}
        onClose={handleAddTagClose}
        maxWidth="xs"
        fullWidth
        onClick={(e) => e.stopPropagation()}
      >
        <DialogTitle>Add Tag</DialogTitle>
        <DialogContent onClick={(e) => e.stopPropagation()}>
          <Autocomplete
            freeSolo
            options={tags.map(t => t.tag)}
            value={newTagName}
            onChange={(_event, newValue) => {
              setNewTagName(newValue || '');
            }}
            onInputChange={(_event, newInputValue) => {
              setNewTagName(newInputValue);
            }}
            renderInput={(params) => (
              <TextField
                {...params}
                autoFocus
                margin="dense"
                label="Tag Name"
                fullWidth
                variant="outlined"
                onKeyPress={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleAddTagSubmit();
                  }
                }}
              />
            )}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleAddTagClose}>Cancel</Button>
          <Button onClick={handleAddTagSubmit} variant="contained" disabled={!newTagName.trim()}>
            Add
          </Button>
        </DialogActions>
      </Dialog>
    </TableRow>
  );
}
