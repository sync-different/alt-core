/**
 * Share Dialog
 * Modal for sharing files with other users via tags
 */

import { useState, useEffect } from 'react';
import { useDispatch } from 'react-redux';
import {
  Dialog,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Typography,
  Alert,
  Checkbox,
  FormControlLabel,
  Paper,
} from '@mui/material';
import { getUsersAndEmails, shareFiles, type User } from '../../services/shareService';
import { AddUserModal } from '../../components/modals/AddUserModal';
import { resetFiles } from '../../store/slices/filesSlice';
import type { AppDispatch } from '../../store/store';

interface ShareDialogProps {
  open: boolean;
  onClose: () => void;
  fileIds: string[]; // Array of MD5 hashes
}

export function ShareDialog({ open, onClose, fileIds }: ShareDialogProps) {
  const dispatch = useDispatch<AppDispatch>();
  const [tagName, setTagName] = useState('');
  const [users, setUsers] = useState<User[]>([]);
  const [selectedUsers, setSelectedUsers] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [addUserModalOpen, setAddUserModalOpen] = useState(false);

  // Load users when modal opens
  useEffect(() => {
    if (open) {
      loadUsers();
    } else {
      // Reset state when modal closes
      setTagName('');
      setSelectedUsers(new Set());
      setError(null);
      setSuccess(null);
    }
  }, [open]);

  const loadUsers = async () => {
    try {
      setLoadingUsers(true);
      const fetchedUsers = await getUsersAndEmails();
      setUsers(fetchedUsers);
    } catch (err) {
      setError('Failed to load users');
      console.error('Error loading users:', err);
    } finally {
      setLoadingUsers(false);
    }
  };

  const handleClose = () => {
    setTagName('');
    setSelectedUsers(new Set());
    setError(null);
    setSuccess(null);
    onClose();
  };

  const handleUserToggle = (username: string) => {
    const newSelected = new Set(selectedUsers);
    if (newSelected.has(username)) {
      newSelected.delete(username);
    } else {
      newSelected.add(username);
    }
    setSelectedUsers(newSelected);
  };

  const handleShareFiles = async () => {
    setError(null);
    setSuccess(null);

    // Validation
    if (!tagName || tagName.trim() === '') {
      setError('Tag is mandatory');
      return;
    }

    if (selectedUsers.size === 0) {
      setError('Please select at least one user');
      return;
    }

    if (fileIds.length === 0) {
      setError('No files selected to share');
      return;
    }

    try {
      setLoading(true);

      await shareFiles({
        tagName: tagName.trim(),
        selectedUsers: Array.from(selectedUsers),
        fileMD5s: fileIds,
      });

      // Success
      setSuccess(`Successfully shared ${fileIds.length} file${fileIds.length !== 1 ? 's' : ''} with ${selectedUsers.size} user${selectedUsers.size !== 1 ? 's' : ''}`);

      // Refresh file list to show updated tags
      dispatch(resetFiles());

      // Close after a brief delay to show success message
      setTimeout(() => {
        handleClose();
      }, 2000);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to share files');
    } finally {
      setLoading(false);
    }
  };

  const handleUserAdded = () => {
    // Reload users after adding a new one
    loadUsers();
  };

  return (
    <>
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <Box
          sx={{
            backgroundColor: 'rgb(42, 42, 42)',
            color: 'white',
            p: 1.5,
            textAlign: 'center',
            position: 'relative',
          }}
        >
          <Typography variant="h6">Tag Files to Share</Typography>
          <Button
            onClick={handleClose}
            sx={{
              position: 'absolute',
              right: 10,
              top: 10,
              color: 'white',
              minWidth: 'auto',
              fontWeight: 'bold',
            }}
          >
            X
          </Button>
        </Box>

        <DialogContent sx={{ pt: 2 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          {success && (
            <Alert severity="success" sx={{ mb: 2 }}>
              {success}
            </Alert>
          )}

          <TextField
            fullWidth
            placeholder="Add Tag..."
            value={tagName}
            onChange={(e) => setTagName(e.target.value)}
            disabled={loading}
            sx={{ mb: 2 }}
          />

          <Button
            variant="contained"
            onClick={() => setAddUserModalOpen(true)}
            disabled={loading || loadingUsers}
            sx={{ mb: 2 }}
          >
            Add User
          </Button>

          <Paper
            variant="outlined"
            sx={{
              height: 300,
              overflow: 'auto',
              p: 1,
            }}
          >
            {loadingUsers ? (
              <Typography sx={{ p: 2, textAlign: 'center' }}>Loading users...</Typography>
            ) : users.length === 0 ? (
              <Typography sx={{ p: 2, textAlign: 'center' }}>No users available</Typography>
            ) : (
              <Box>
                {users.map((user) => (
                  <FormControlLabel
                    key={user.username}
                    control={
                      <Checkbox
                        checked={selectedUsers.has(user.username)}
                        onChange={() => handleUserToggle(user.username)}
                        disabled={loading}
                      />
                    }
                    label={`${user.username} (${user.email})`}
                    sx={{ display: 'block', mb: 1 }}
                  />
                ))}
              </Box>
            )}
          </Paper>

          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {fileIds.length} file{fileIds.length !== 1 ? 's' : ''} selected to share
          </Typography>
        </DialogContent>

        <DialogActions sx={{ p: 2, justifyContent: 'flex-end' }}>
          <Button variant="contained" onClick={handleClose} disabled={loading}>
            Cancel
          </Button>
          <Button variant="contained" onClick={handleShareFiles} disabled={loading || loadingUsers}>
            {loading ? 'Sharing...' : 'Share Files'}
          </Button>
        </DialogActions>
      </Dialog>

      <AddUserModal
        open={addUserModalOpen}
        onClose={() => setAddUserModalOpen(false)}
        onUserAdded={handleUserAdded}
      />
    </>
  );
}
