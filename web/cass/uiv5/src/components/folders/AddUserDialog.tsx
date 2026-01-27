/**
 * Add User Dialog
 * Allows admin to add a user to folder permissions
 */

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Typography,
  Box,
  CircularProgress,
  Alert,
} from '@mui/material';
import { PersonAdd as PersonAddIcon } from '@mui/icons-material';
import { getUsersAndEmails, type User } from '../../services/shareService';
import type { PermissionLevel } from '../../services/folderPermissionApi';

interface AddUserDialogProps {
  open: boolean;
  folderName: string;
  existingUsers: string[]; // Usernames already in the permission list
  onAdd: (username: string, permission: PermissionLevel) => void;
  onCancel: () => void;
}

export function AddUserDialog({
  open,
  folderName,
  existingUsers,
  onAdd,
  onCancel,
}: AddUserDialogProps) {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedUser, setSelectedUser] = useState<string>('');
  const [selectedPermission, setSelectedPermission] = useState<PermissionLevel>('r');

  // Load users when dialog opens
  useEffect(() => {
    if (open) {
      loadUsers();
    } else {
      // Reset state when closing
      setSelectedUser('');
      setSelectedPermission('r');
      setError(null);
    }
  }, [open]);

  const loadUsers = async () => {
    setLoading(true);
    setError(null);
    try {
      const userList = await getUsersAndEmails();
      // Filter out users that already have permissions
      const availableUsers = userList.filter(
        (u) => !existingUsers.includes(u.username)
      );
      setUsers(availableUsers);

      // Auto-select first user if available
      if (availableUsers.length > 0) {
        setSelectedUser(availableUsers[0].username);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load users');
    } finally {
      setLoading(false);
    }
  };

  const handleAdd = () => {
    if (selectedUser) {
      onAdd(selectedUser, selectedPermission);
    }
  };

  const availableUsers = users.filter((u) => !existingUsers.includes(u.username));

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle
        sx={{
          backgroundColor: 'rgb(42, 42, 42)',
          color: 'white',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <PersonAddIcon />
        Add User Access
      </DialogTitle>

      <DialogContent sx={{ pt: 3 }}>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Grant a user access to folder "{folderName}"
        </Typography>

        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={32} />
          </Box>
        )}

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {!loading && !error && availableUsers.length === 0 && (
          <Alert severity="info">
            All users already have access to this folder.
          </Alert>
        )}

        {!loading && !error && availableUsers.length > 0 && (
          <>
            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel id="select-user-label">User</InputLabel>
              <Select
                labelId="select-user-label"
                value={selectedUser}
                label="User"
                onChange={(e) => setSelectedUser(e.target.value)}
              >
                {availableUsers.map((user) => (
                  <MenuItem key={user.username} value={user.username}>
                    {user.username}
                    {user.email && (
                      <Typography
                        component="span"
                        variant="body2"
                        color="text.secondary"
                        sx={{ ml: 1 }}
                      >
                        ({user.email})
                      </Typography>
                    )}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel id="select-permission-label">Permission</InputLabel>
              <Select
                labelId="select-permission-label"
                value={selectedPermission}
                label="Permission"
                onChange={(e) => setSelectedPermission(e.target.value as PermissionLevel)}
              >
                <MenuItem value="r">Read Only</MenuItem>
                <MenuItem value="rw">Read & Write</MenuItem>
              </Select>
            </FormControl>
          </>
        )}
      </DialogContent>

      <DialogActions sx={{ p: 2, gap: 1 }}>
        <Button onClick={onCancel} variant="outlined">
          Cancel
        </Button>
        <Button
          onClick={handleAdd}
          variant="contained"
          color="primary"
          disabled={!selectedUser || loading}
        >
          Add User
        </Button>
      </DialogActions>
    </Dialog>
  );
}
