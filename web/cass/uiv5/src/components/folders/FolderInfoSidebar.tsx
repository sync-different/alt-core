/**
 * Folder Info Sidebar Component
 * Right sidebar showing folder details and permission management
 * Only visible to admin users
 */

import { useEffect } from 'react';
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
} from '@mui/material';
import {
  Close as CloseIcon,
  Folder as FolderIcon,
  Save as SaveIcon,
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
  closeSidebar,
  loadFolderPermissions,
  saveFolderPermissions,
  addUserPermission,
  removeUserPermission,
  updateUserPermission,
  updateUserDepth,
  clearError,
} from '../../store/slices/folderPermissionsSlice';
import type { AppDispatch } from '../../store/store';
import type { PermissionLevel, PermissionDepth } from '../../services/folderPermissionApi';
import { UserPermissionList } from './UserPermissionList';
import { RIGHT_SIDEBAR_WIDTH } from '../layout/RightSidebar';
import { useSidebarContext } from '../../contexts/SidebarContext';

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

  // Load permissions when folder is selected
  useEffect(() => {
    if (selectedFolder && isOpen) {
      const folderPath = selectedFolder.path || selectedFolder.name;
      dispatch(loadFolderPermissions(folderPath));
    }
  }, [selectedFolder, isOpen, dispatch]);

  // Don't render if not admin
  if (!isAdmin) {
    return null;
  }

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

  const folderName = selectedFolder?.name || 'Unknown';

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
              <FolderIcon sx={{ mt: 0.5, flexShrink: 0 }} />
              <Typography
                variant="h6"
                sx={{
                  wordBreak: 'break-word',
                  overflowWrap: 'break-word',
                }}
              >
                {folderName}
              </Typography>
            </Box>
            <IconButton size="small" onClick={handleClose} sx={{ color: 'white', flexShrink: 0 }}>
              <CloseIcon />
            </IconButton>
          </Box>

          {/* Content */}
          <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
            {/* Folder Info */}
            <Box sx={{ mb: 3 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Folder Details
              </Typography>
              <Typography
                variant="body2"
                sx={{ wordBreak: 'break-word', overflowWrap: 'break-word' }}
              >
                <strong>Name:</strong> {folderName}
              </Typography>
              {selectedFolder?.path && (
                <Typography
                  variant="body2"
                  sx={{ mt: 0.5, wordBreak: 'break-word', overflowWrap: 'break-word' }}
                >
                  <strong>Path:</strong> {selectedFolder.path}
                </Typography>
              )}
              {selectedFolder?.count !== undefined && (
                <Typography variant="body2" sx={{ mt: 0.5 }}>
                  <strong>Items:</strong> {selectedFolder.count}
                </Typography>
              )}
            </Box>

            <Divider sx={{ my: 2 }} />

            {/* Loading State */}
            {isLoading && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress />
              </Box>
            )}

            {/* Admin Override Notice */}
            {!isLoading && isAdmin && (
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
                folderName={folderName}
                onAddUser={handleAddUser}
                onRemoveUser={handleRemoveUser}
                onUpdatePermission={handleUpdatePermission}
                onUpdateDepth={handleUpdateDepth}
                disabled={isSaving}
              />
            )}
          </Box>

          {/* Footer with Save Button */}
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
    </>
  );
}
