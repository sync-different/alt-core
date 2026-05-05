/**
 * Users Tab — admin CRUD for system users.
 *
 * Lists non-admin users (admin doesn't appear; admin self-edit happens via
 * the "Edit my account" button at the top). Each row exposes Edit + Delete.
 *
 * See internal/PROJECT_TAB_ADMIN_USERS.md.
 */

import { useCallback, useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Typography,
  CircularProgress,
  Alert,
  Snackbar,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
} from '@mui/material';
import {
  Delete as DeleteIcon,
  Edit as EditIcon,
  PersonAdd as PersonAddIcon,
  Refresh as RefreshIcon,
  AccountCircle as AccountCircleIcon,
} from '@mui/icons-material';
import { listUsers, deleteUser } from '../../services/userApi';
import type { User } from '../../services/shareService';
import { selectUsername } from '../../store/slices/authSlice';
import { AddUserModal } from '../modals/AddUserModal';
import { EditUserModal, type EditUserMode } from './EditUserModal';

export function UsersTab() {
  const adminUsername = useSelector(selectUsername);

  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Modal state
  const [addOpen, setAddOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<{
    mode: EditUserMode;
    username: string;
    email?: string;
  } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Snackbar for action feedback
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: 'success' | 'error';
  }>({ open: false, message: '', severity: 'success' });

  const showSnack = (message: string, severity: 'success' | 'error' = 'success') => {
    setSnackbar({ open: true, message, severity });
  };

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listUsers();
      // Defensive: backend has been observed returning records with empty
      // username (corrupt rows in users.txt — addUser doesn't validate the
      // username param server-side). Filter them out so we don't render
      // blank rows the admin can't act on (Edit/Delete by name fails).
      const cleaned = data.filter((u) => u.username && u.username.trim() !== '');
      if (cleaned.length !== data.length) {
        console.warn(
          `[UsersTab] Filtered ${data.length - cleaned.length} record(s) with empty username from getusersandemail.fn response. `
            + 'Likely corrupt rows in users.txt. Inspect via: '
            + 'grep -nE "^," scrubber/config/users.txt',
        );
      }
      // Sort by username for stable display.
      cleaned.sort((a, b) => a.username.localeCompare(b.username));
      setUsers(cleaned);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load users.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await deleteUser(deleteTarget.username);
      showSnack(`Deleted user "${deleteTarget.username}"`);
      setDeleteTarget(null);
      await loadUsers();
    } catch (err) {
      showSnack(
        err instanceof Error ? err.message : 'Failed to delete user',
        'error',
      );
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Top action bar */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: 2,
        }}
      >
        <Typography variant="h6">Users</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            startIcon={<AccountCircleIcon />}
            variant="outlined"
            size="small"
            onClick={() =>
              setEditTarget({
                mode: 'admin',
                username: adminUsername ?? 'admin',
              })
            }
          >
            Edit my account
          </Button>
          <Button
            startIcon={<PersonAddIcon />}
            variant="contained"
            size="small"
            onClick={() => setAddOpen(true)}
          >
            Add User
          </Button>
          <Tooltip title="Refresh">
            <IconButton onClick={loadUsers} disabled={loading} size="small">
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={28} />
        </Box>
      ) : users.length === 0 ? (
        <Box sx={{ py: 6, textAlign: 'center', color: 'text.secondary' }}>
          <Typography variant="body2">
            No users yet — click "Add User" to create one.
          </Typography>
        </Box>
      ) : (
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Username</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Email</TableCell>
                <TableCell sx={{ fontWeight: 600, width: 120 }} align="right">
                  Actions
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {users.map((u) => (
                <TableRow key={u.username} hover>
                  <TableCell>{u.username}</TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton
                        size="small"
                        onClick={() =>
                          setEditTarget({
                            mode: 'user',
                            username: u.username,
                            email: u.email,
                          })
                        }
                      >
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        onClick={() => setDeleteTarget(u)}
                        sx={{ color: 'error.main' }}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Add User modal — reuses the existing modal from the share flow */}
      <AddUserModal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onUserAdded={() => {
          showSnack('User added');
          loadUsers();
        }}
      />

      {/* Edit User modal — single instance, reused for per-row + admin self-edit */}
      {editTarget && (
        <EditUserModal
          open={!!editTarget}
          mode={editTarget.mode}
          username={editTarget.username}
          currentEmail={editTarget.email}
          onClose={() => setEditTarget(null)}
          onSaved={() => {
            showSnack(
              editTarget.mode === 'admin'
                ? 'Account updated'
                : `Updated user "${editTarget.username}"`,
            );
            loadUsers();
          }}
        />
      )}

      {/* Delete confirmation */}
      <Dialog open={!!deleteTarget} onClose={() => !deleting && setDeleteTarget(null)}>
        <DialogTitle>Delete user?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Delete user <strong>{deleteTarget?.username}</strong>? This cannot be undone.
            The user's active sessions and shares will be removed.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)} disabled={deleting}>
            Cancel
          </Button>
          <Button
            color="error"
            variant="contained"
            onClick={handleDeleteConfirm}
            disabled={deleting}
          >
            {deleting ? 'Deleting…' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert
          severity={snackbar.severity}
          onClose={() => setSnackbar({ ...snackbar, open: false })}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
