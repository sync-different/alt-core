/**
 * File Card - Grid View Item
 * Displays a file as a card with thumbnail and metadata
 */

import { useState } from 'react';
import { useDispatch } from 'react-redux';
import {
  Card,
  CardContent,
  CardMedia,
  Box,
  Typography,
  Checkbox,
  IconButton,
  Chip,
  Menu,
  MenuItem,
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
  MoreVert as MoreVertIcon,
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

interface FileCardProps {
  file: File;
  onCardClick?: (file: File) => void;
  onDownload?: (file: File) => void;
  showDetails?: boolean;
  gridSize?: 'xs' | 'small' | 'medium' | 'large';
}

export function FileCard({ file, onCardClick, onDownload, showDetails = true, gridSize = 'medium' }: FileCardProps) {
  const dispatch = useDispatch<AppDispatch>();
  const { isSelected, toggleSelect, handleClick } = useFileSelection();
  const selected = isSelected(file.nickname);
  const tags = useSelector((state: RootState) => state.tags.tags);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [isHovered, setIsHovered] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
    open: false,
    message: '',
    severity: 'success',
  });
  const [addTagDialogOpen, setAddTagDialogOpen] = useState(false);
  const [newTagName, setNewTagName] = useState('');
  const [showAllTags, setShowAllTags] = useState(false);

  // Calculate dimensions based on grid size
  const thumbnailHeight = gridSize === 'xs' ? 80 : gridSize === 'small' ? 120 : gridSize === 'medium' ? 200 : 280;
  const iconSize = gridSize === 'xs' ? 32 : gridSize === 'small' ? 48 : gridSize === 'medium' ? 80 : 120;

  const handleCardClick = (event: React.MouseEvent) => {
    // Don't trigger selection if clicking on checkbox or menu
    if ((event.target as HTMLElement).closest('.MuiCheckbox-root, .MuiIconButton-root')) {
      return;
    }

    // If multi-selecting (shift/ctrl/cmd), use selection handler
    if (event.shiftKey || event.ctrlKey || event.metaKey) {
      handleClick(file.nickname, event);
      return;
    }

    // If custom click handler provided, use it (e.g., open viewer)
    if (onCardClick) {
      onCardClick(file);
    } else {
      // Fallback to selection
      handleClick(file.nickname, event);
    }
  };

  const handleCheckboxChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    event.stopPropagation();
    toggleSelect(file.nickname);
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleDownload = (event: React.MouseEvent) => {
    event.stopPropagation();
    handleMenuClose();
    if (onDownload) {
      onDownload(file);
    }
  };

  const getThumbnail = () => {
    if (file.file_thumbnail) {
      return `data:image/jpeg;base64,${file.file_thumbnail}`;
    }
    return null;
  };

  const getFileIcon = () => {
    const iconProps = { sx: { fontSize: iconSize, color: 'action.disabled' } };
    switch (file.file_group) {
      case 'photo':
        return <ImageIcon {...iconProps} />;
      case 'music':
        return <MusicIcon {...iconProps} />;
      case 'movie':
        return <VideoIcon {...iconProps} />;
      case 'document':
        return <DocumentIcon {...iconProps} />;
      default:
        return <DocumentIcon {...iconProps} />;
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
    handleMenuClose();
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

  const thumbnail = getThumbnail();

  return (
    <Card
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={handleCardClick}
      sx={{
        cursor: 'pointer',
        position: 'relative',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        border: selected ? 2 : 1,
        borderColor: selected ? 'primary.main' : 'divider',
        transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        transform: isHovered ? 'translateY(-4px)' : 'translateY(0)',
        '&:hover': {
          boxShadow: 6,
          borderColor: selected ? 'primary.main' : 'primary.light',
        },
      }}
    >
      {/* Checkbox - Top Left */}
      <Box
        sx={{
          position: 'absolute',
          top: 8,
          left: 8,
          zIndex: 1,
          opacity: selected || isHovered ? 1 : 0,
          transform: selected || isHovered ? 'scale(1)' : 'scale(0.8)',
          transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      >
        <Checkbox
          checked={selected}
          onChange={handleCheckboxChange}
          sx={{
            bgcolor: 'background.paper',
            borderRadius: 0.5,
            boxShadow: 1,
            '&:hover': { bgcolor: 'background.paper', boxShadow: 2 },
          }}
        />
      </Box>

      {/* Menu - Top Right */}
      <Box
        sx={{
          position: 'absolute',
          top: 8,
          right: 8,
          zIndex: 1,
          opacity: isHovered ? 1 : 0,
          transform: isHovered ? 'scale(1)' : 'scale(0.8)',
          transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      >
        <IconButton
          size="small"
          onClick={handleMenuOpen}
          sx={{
            bgcolor: 'background.paper',
            boxShadow: 1,
            '&:hover': { bgcolor: 'background.paper', boxShadow: 2 },
          }}
        >
          <MoreVertIcon fontSize="small" />
        </IconButton>
      </Box>

      {/* Thumbnail or Icon */}
      {thumbnail ? (
        <Box sx={{ overflow: 'hidden', height: thumbnailHeight, bgcolor: '#f5f5f5' }}>
          <CardMedia
            component="img"
            height={thumbnailHeight}
            image={thumbnail}
            alt={file.name}
            loading="lazy"
            sx={{
              objectFit: 'cover',
              transition: 'transform 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
              transform: isHovered ? 'scale(1.05)' : 'scale(1)',
            }}
          />
        </Box>
      ) : (
        <Box
          sx={{
            height: thumbnailHeight,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: '#f5f5f5',
            transition: 'background-color 0.3s',
            '&:hover': {
              bgcolor: '#eeeeee',
            },
          }}
        >
          {getFileIcon()}
        </Box>
      )}

      {/* Content */}
      {showDetails && (
        <CardContent sx={{ flex: 1, p: gridSize === 'xs' ? 0.5 : gridSize === 'small' ? 1 : gridSize === 'medium' ? 2 : 2.5 }}>
          <Typography
            variant={gridSize === 'xs' || gridSize === 'small' ? 'caption' : 'body2'}
            sx={{
              fontWeight: 500,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              display: '-webkit-box',
              WebkitLineClamp: gridSize === 'xs' || gridSize === 'small' ? 1 : 2,
              WebkitBoxOrient: 'vertical',
              mb: gridSize === 'xs' || gridSize === 'small' ? 0.5 : 1,
              minHeight: gridSize === 'xs' || gridSize === 'small' ? 20 : 40,
            }}
            title={file.name}
          >
            {file.name}
          </Typography>

          {gridSize !== 'xs' && gridSize !== 'small' && (
            <>
              <Typography variant="caption" color="text.secondary" display="block">
                {formatDate(file.file_date_long)}
              </Typography>

              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                {formatFileSize(file.file_size)}
              </Typography>
            </>
          )}

          {/* Tags */}
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', alignItems: 'center' }}>
            {(showAllTags ? getTags() : getTags().slice(0, gridSize === 'xs' || gridSize === 'small' ? 1 : 2)).map((tag) => (
              <Chip
                key={tag}
                label={tag}
                size="small"
                variant="filled"
                onClick={(event) => handleTagClick(event, tag)}
                onDelete={(event) => handleRemoveTag(event, tag)}
                sx={{
                  backgroundColor: 'primary.main',
                  color: 'white',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  height: '22px',
                  cursor: 'pointer',
                  border: '2px solid',
                  borderColor: '#003366',
                  borderRadius: '4px 12px 12px 4px',
                  '& .MuiChip-label': {
                    px: 1,
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
            {!showAllTags && getTags().length > (gridSize === 'xs' || gridSize === 'small' ? 1 : 2) && (
              <Chip
                label={`+${getTags().length - (gridSize === 'xs' || gridSize === 'small' ? 1 : 2)}`}
                size="small"
                variant="filled"
                onClick={handleShowAllTags}
                sx={{
                  backgroundColor: 'grey.300',
                  color: 'text.secondary',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  height: '22px',
                  borderRadius: '4px 12px 12px 4px',
                  cursor: 'pointer',
                  '& .MuiChip-label': {
                    px: 1,
                  },
                  '&:hover': {
                    backgroundColor: 'grey.400',
                  },
                }}
              />
            )}
            {showAllTags && getTags().length > (gridSize === 'xs' || gridSize === 'small' ? 1 : 2) && (
              <Chip
                label="Show less"
                size="small"
                variant="filled"
                onClick={handleShowAllTags}
                sx={{
                  backgroundColor: 'grey.300',
                  color: 'text.secondary',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  height: '22px',
                  borderRadius: '4px 12px 12px 4px',
                  cursor: 'pointer',
                  '& .MuiChip-label': {
                    px: 1,
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
                width: 22,
                height: 22,
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
        </CardContent>
      )}

      {/* Context Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        {file.file_group === 'music' && (
          <MenuItem onClick={handleAddToPlaylist}>
            <QueueMusicIcon fontSize="small" sx={{ mr: 1 }} />
            Add to Playlist
          </MenuItem>
        )}
        <MenuItem onClick={handleDownload}>
          <DownloadIcon fontSize="small" sx={{ mr: 1 }} />
          Download
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <OpenIcon fontSize="small" sx={{ mr: 1 }} />
          Open
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <FolderIcon fontSize="small" sx={{ mr: 1 }} />
          Open Folder
        </MenuItem>
      </Menu>

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
    </Card>
  );
}
