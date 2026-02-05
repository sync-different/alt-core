/**
 * Folder Info Sidebar Component
 * Right sidebar showing folder/file details and permission management
 * - All users can see folder/file details (name, path, item count, size, etc.)
 * - Only admin users can view and manage permissions (folders only)
 */

import { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Button,
  Divider,
  CircularProgress,
  Alert,
  Snackbar,
  Chip,
  TextField,
  Autocomplete,
} from '@mui/material';
import {
  Close as CloseIcon,
  Folder as FolderIcon,
  InsertDriveFile as FileIcon,
  Save as SaveIcon,
  Download as DownloadIcon,
} from '@mui/icons-material';
import { selectIsAdmin } from '../../store/slices/authSlice';
import {
  selectSelectedFolder,
  selectFolderPermissions,
  selectIsSidebarOpen,
  selectIsLoading,
  selectIsSaving,
  selectIsDirty,
  selectError,
  selectFileInfo,
  selectIsLoadingFileInfo,
  closeSidebar,
  loadFolderPermissions,
  loadFileInfo,
  saveFolderPermissions,
  addUserPermission,
  removeUserPermission,
  updateUserPermission,
  updateUserDepth,
  clearError,
  updateFileInfoTags,
} from '../../store/slices/folderPermissionsSlice';
import type { AppDispatch } from '../../store/store';
import type { PermissionLevel, PermissionDepth } from '../../services/folderPermissionApi';
import { UserPermissionList } from './UserPermissionList';
import { RIGHT_SIDEBAR_WIDTH } from '../layout/RightSidebar';
import { useSidebarContext } from '../../contexts/SidebarContext';
import { formatFileSize, formatDate } from '../../utils/formatters';
import { addTags, fetchTags } from '../../services/fileApi';
import { useFileDownload } from '../../hooks/useFileDownload';
import { DownloadProgressModal } from '../download/DownloadProgressModal';
import type { File } from '../../types/models';

const SIDEBAR_WIDTH = 320;

export function FolderInfoSidebar() {
  const { rightSidebarOpen } = useSidebarContext();
  const dispatch = useDispatch<AppDispatch>();
  const isAdmin = useSelector(selectIsAdmin);
  const selectedFolder = useSelector(selectSelectedFolder);
  const permissions = useSelector(selectFolderPermissions);
  const isOpen = useSelector(selectIsSidebarOpen);
  const isLoading = useSelector(selectIsLoading);
  const isSaving = useSelector(selectIsSaving);
  const isDirty = useSelector(selectIsDirty);
  const error = useSelector(selectError);
  const fileInfo = useSelector(selectFileInfo);
  const isLoadingFileInfo = useSelector(selectIsLoadingFileInfo);

  // Tag input state
  const [tagInput, setTagInput] = useState('');
  const [isAddingTag, setIsAddingTag] = useState(false);
  const [tagSuccess, setTagSuccess] = useState<string | null>(null);
  const [availableTags, setAvailableTags] = useState<string[]>([]);

  // Download manager hook
  const {
    isDownloading,
    downloadProgress,
    isComplete,
    currentFile: downloadingFile,
    startDownload,
    cancelDownload,
    closeModal,
  } = useFileDownload();

  // Load permissions when folder is selected (admin only, folders only)
  useEffect(() => {
    if (selectedFolder && isOpen && isAdmin && selectedFolder.type !== 'file') {
      const folderPath = selectedFolder.path || selectedFolder.name;
      dispatch(loadFolderPermissions(folderPath));
    }
  }, [selectedFolder, isOpen, isAdmin, dispatch]);

  // Load file info when file is selected
  useEffect(() => {
    if (selectedFolder && isOpen && selectedFolder.type === 'file' && selectedFolder.md5) {
      dispatch(loadFileInfo(selectedFolder.md5));
    }
  }, [selectedFolder, isOpen, dispatch]);

  // Load available tags when sidebar opens for a file
  useEffect(() => {
    if (isOpen && selectedFolder?.type === 'file') {
      loadAvailableTags();
    }
  }, [isOpen, selectedFolder?.type]);

  const loadAvailableTags = async () => {
    try {
      const tags = await fetchTags();
      setAvailableTags(tags.map(t => t.tag));
    } catch (error) {
      console.error('Failed to load available tags:', error);
    }
  };

  const handleClose = () => {
    dispatch(closeSidebar());
  };

  const handleSave = () => {
    if (selectedFolder) {
      const folderPath = selectedFolder.path || selectedFolder.name;
      dispatch(saveFolderPermissions({ folderPath, permissions }));
    }
  };

  const handleAddUser = (username: string, permission: PermissionLevel) => {
    // Default depth is '.' (current folder only) - checkbox defaults to unchecked
    dispatch(addUserPermission({ username, permission, depth: '.' }));
  };

  const handleRemoveUser = (username: string) => {
    dispatch(removeUserPermission(username));
  };

  const handleUpdatePermission = (username: string, permission: PermissionLevel) => {
    dispatch(updateUserPermission({ username, permission }));
  };

  const handleUpdateDepth = (username: string, depth: PermissionDepth) => {
    dispatch(updateUserDepth({ username, depth }));
  };

  const handleCloseError = () => {
    dispatch(clearError());
  };

  // Parse tags from comma-separated string
  const getTags = (): string[] => {
    if (!fileInfo?.file_tags) return [];
    return fileInfo.file_tags.split(',').filter(Boolean).map(t => t.trim());
  };

  // Handle adding a new tag (accepts optional tag parameter for autocomplete selection)
  const handleAddTag = async (tagToAdd?: string) => {
    const tag = (tagToAdd || tagInput).trim();
    if (!tag || !selectedFolder?.md5) return;

    setIsAddingTag(true);
    try {
      await addTags([selectedFolder.md5], [tag]);

      // Update local state
      const currentTags = getTags();
      const updatedTags = [...currentTags, tag].join(',');
      dispatch(updateFileInfoTags(updatedTags));

      setTagInput('');
      setTagSuccess(`Tag "${tag}" added`);
    } catch (err) {
      console.error('Failed to add tag:', err);
    } finally {
      setIsAddingTag(false);
    }
  };

  // Handle tag click (could navigate to tag filter in future)
  const handleTagClick = (tag: string) => {
    // For now, just log - could be extended to filter by tag
    console.log('Tag clicked:', tag);
  };

  // Handle file download
  const handleDownload = () => {
    if (!fileInfo || !selectedFolder?.md5) return;

    // Construct a File object from fileInfo for the download manager
    const fileForDownload: File = {
      multiclusterid: selectedFolder.md5,
      nickname: fileInfo.nickname,
      md5hash: selectedFolder.md5,
      file_name: fileInfo.name,
      name: fileInfo.name,
      file_ext: fileInfo.file_ext,
      file_group: fileInfo.file_group,
      file_size: fileInfo.file_size,
      file_date: fileInfo.file_date,
      file_date_long: fileInfo.file_date_long,
      file_tags: fileInfo.file_tags,
      file_thumbnail: fileInfo.file_thumbnail,
      file_path: selectedFolder.path || '',
      file_path_webapp: fileInfo.file_path_webapp,
      video_url_webapp: fileInfo.video_url_webapp,
    };

    startDownload(fileForDownload);
  };

  const itemName = selectedFolder?.name || 'Unknown';
  const isFile = selectedFolder?.type === 'file';

  return (
    <>
      {/* Sidebar Drawer */}
      <Drawer
        anchor="right"
        open={isOpen}
        variant="persistent"
        hideBackdrop
        sx={{
          '& .MuiDrawer-paper': {
            width: SIDEBAR_WIDTH,
            position: 'fixed',
            right: rightSidebarOpen ? RIGHT_SIDEBAR_WIDTH : 0, // Shift left when chat sidebar is open
            top: 64, // Below top nav
            height: 'calc(100% - 64px)',
            boxShadow: 3,
            zIndex: (theme) => theme.zIndex.drawer, // Below RightSidebar
            transition: 'right 0.3s ease',
          },
        }}
      >
        <Box
          sx={{
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            bgcolor: 'background.paper',
          }}
        >
          {/* Header */}
          <Box
            sx={{
              p: 2,
              bgcolor: 'rgb(42, 42, 42)',
              color: 'white',
              display: 'flex',
              alignItems: 'flex-start',
              justifyContent: 'space-between',
              gap: 1,
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, flex: 1, minWidth: 0 }}>
              {isFile ? (
                <FileIcon sx={{ mt: 0.5, flexShrink: 0 }} />
              ) : (
                <FolderIcon sx={{ mt: 0.5, flexShrink: 0 }} />
              )}
              <Typography
                variant="h6"
                sx={{
                  wordBreak: 'break-word',
                  overflowWrap: 'break-word',
                }}
              >
                {itemName}
              </Typography>
            </Box>
            <IconButton size="small" onClick={handleClose} sx={{ color: 'white', flexShrink: 0 }}>
              <CloseIcon />
            </IconButton>
          </Box>

          {/* Content */}
          <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
            {/* Loading state for file info */}
            {isFile && isLoadingFileInfo && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress />
              </Box>
            )}

            {/* Folder/File Info */}
            {(!isFile || !isLoadingFileInfo) && (
              <Box sx={{ mb: 3 }}>
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  {isFile ? 'File Details' : 'Folder Details'}
                </Typography>

                {/* Name - use fileInfo.name if available for proper decoding */}
                <Typography
                  variant="body2"
                  sx={{ wordBreak: 'break-word', overflowWrap: 'break-word' }}
                >
                  <strong>Name:</strong> {isFile && fileInfo ? fileInfo.name : itemName}
                </Typography>

                {/* Path */}
                {selectedFolder?.path && (
                  <Typography
                    variant="body2"
                    sx={{ mt: 0.5, wordBreak: 'break-word', overflowWrap: 'break-word' }}
                  >
                    <strong>Path:</strong> {selectedFolder.path}
                  </Typography>
                )}

                {/* Folder-specific: Item count */}
                {!isFile && selectedFolder?.count !== undefined && (
                  <Typography variant="body2" sx={{ mt: 0.5 }}>
                    <strong>Items:</strong> {selectedFolder.count}
                  </Typography>
                )}

                {/* File-specific info from API */}
                {isFile && fileInfo && (
                  <>
                    {/* File size */}
                    {fileInfo.file_size > 0 && (
                      <Typography variant="body2" sx={{ mt: 0.5 }}>
                        <strong>Size:</strong> {formatFileSize(fileInfo.file_size)}
                      </Typography>
                    )}

                    {/* File date */}
                    {fileInfo.file_date_long > 0 && (
                      <Typography variant="body2" sx={{ mt: 0.5 }}>
                        <strong>Date:</strong> {formatDate(fileInfo.file_date_long)}
                      </Typography>
                    )}

                    {/* File type */}
                    {fileInfo.file_group && (
                      <Typography variant="body2" sx={{ mt: 0.5 }}>
                        <strong>Type:</strong> {fileInfo.file_group.charAt(0).toUpperCase() + fileInfo.file_group.slice(1)}
                        {fileInfo.file_ext && ` (.${fileInfo.file_ext})`}
                      </Typography>
                    )}

                    {/* Tags Section */}
                    <Box sx={{ mt: 1.5 }}>
                      <Typography variant="body2" sx={{ mb: 0.5 }}>
                        <strong>Tags:</strong>
                      </Typography>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mb: 1 }}>
                        {getTags().length > 0 ? (
                          getTags().map((tag) => (
                            <Chip
                              key={tag}
                              label={tag}
                              size="small"
                              onClick={() => handleTagClick(tag)}
                              sx={{
                                backgroundColor: 'primary.main',
                                color: 'white',
                                cursor: 'pointer',
                                '&:hover': {
                                  backgroundColor: 'primary.dark',
                                },
                              }}
                            />
                          ))
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            No tags
                          </Typography>
                        )}
                      </Box>
                      {/* Add Tag Input with Autocomplete */}
                      <Autocomplete
                        freeSolo
                        options={availableTags.filter(t => !getTags().includes(t))}
                        inputValue={tagInput}
                        onInputChange={(_, newValue) => setTagInput(newValue)}
                        onChange={(_, newValue) => {
                          if (newValue && typeof newValue === 'string') {
                            // Pass selected tag directly to avoid state timing issues
                            handleAddTag(newValue);
                          }
                        }}
                        disabled={isAddingTag}
                        size="small"
                        renderInput={(params) => (
                          <TextField
                            {...params}
                            placeholder="Add tag..."
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' && tagInput.trim()) {
                                e.preventDefault();
                                handleAddTag();
                              }
                            }}
                          />
                        )}
                        sx={{ mt: 0.5 }}
                      />
                    </Box>

                    {/* Thumbnail preview for any file type with a thumbnail */}
                    {fileInfo.file_thumbnail && (
                      <Box sx={{ mt: 2, textAlign: 'center' }}>
                        <img
                          src={`data:image/jpeg;base64,${fileInfo.file_thumbnail}`}
                          alt={fileInfo.name}
                          style={{
                            maxWidth: '100%',
                            maxHeight: 200,
                            borderRadius: 4,
                            border: '1px solid rgba(0,0,0,0.1)',
                          }}
                        />
                      </Box>
                    )}

                    {/* Download Button */}
                    <Button
                      variant="contained"
                      fullWidth
                      startIcon={<DownloadIcon />}
                      onClick={handleDownload}
                      disabled={isDownloading}
                      sx={{ mt: 2 }}
                    >
                      {isDownloading ? 'Downloading...' : 'Download'}
                    </Button>
                  </>
                )}

                {/* Fallback: Show MD5 if no file info loaded yet */}
                {isFile && !fileInfo && selectedFolder?.md5 && (
                  <Typography
                    variant="body2"
                    sx={{ mt: 0.5, wordBreak: 'break-word', overflowWrap: 'break-word' }}
                  >
                    <strong>MD5:</strong> {selectedFolder.md5}
                  </Typography>
                )}
              </Box>
            )}

            {/* Permissions Section - Admin Only, Folders Only */}
            {isAdmin && !isFile && (
              <>
                <Divider sx={{ my: 2 }} />

                {/* Loading State */}
                {isLoading && (
                  <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                    <CircularProgress />
                  </Box>
                )}

                {/* Admin Override Notice */}
                {!isLoading && (
                  <Alert severity="success" sx={{ mb: 2 }}>
                    <Typography variant="body2">
                      Admin override: You have full access to all folders.
                    </Typography>
                  </Alert>
                )}

                {/* Permissions List */}
                {!isLoading && (
                  <UserPermissionList
                    permissions={permissions}
                    folderName={itemName}
                    onAddUser={handleAddUser}
                    onRemoveUser={handleRemoveUser}
                    onUpdatePermission={handleUpdatePermission}
                    onUpdateDepth={handleUpdateDepth}
                    disabled={isSaving}
                  />
                )}
              </>
            )}
          </Box>

          {/* Footer with Save Button - Admin Only, Folders Only */}
          {isAdmin && !isFile && (
            <Box
              sx={{
                p: 2,
                borderTop: 1,
                borderColor: 'divider',
                bgcolor: 'background.default',
              }}
            >
              <Button
                variant="contained"
                fullWidth
                startIcon={isSaving ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />}
                onClick={handleSave}
                disabled={!isDirty || isSaving || isLoading}
              >
                {isSaving ? 'Saving...' : 'Save Permissions'}
              </Button>
              {isDirty && (
                <Typography
                  variant="caption"
                  color="warning.main"
                  sx={{ display: 'block', textAlign: 'center', mt: 1 }}
                >
                  You have unsaved changes
                </Typography>
              )}
            </Box>
          )}
        </Box>
      </Drawer>

      {/* Error Snackbar */}
      <Snackbar
        open={!!error}
        autoHideDuration={5000}
        onClose={handleCloseError}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity="error" onClose={handleCloseError}>
          {error}
        </Alert>
      </Snackbar>

      {/* Tag Success Snackbar */}
      <Snackbar
        open={!!tagSuccess}
        autoHideDuration={3000}
        onClose={() => setTagSuccess(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity="success" onClose={() => setTagSuccess(null)}>
          {tagSuccess}
        </Alert>
      </Snackbar>

      {/* Download Progress Modal */}
      <DownloadProgressModal
        open={isDownloading || isComplete}
        fileName={downloadingFile?.name || fileInfo?.name || ''}
        progress={downloadProgress}
        onCancel={isComplete ? closeModal : cancelDownload}
        isComplete={isComplete}
      />
    </>
  );
}
