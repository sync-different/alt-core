/**
 * Edit User modal — admin sets a user's email and/or password.
 *
 * Two modes (controlled by `mode` prop):
 * - "user": edits a specific non-admin user (target supplied via `username`).
 *   Calls setUseremail.fn / setuserpassword.fn.
 * - "admin": edits the calling admin's own account. Username comes from the
 *   active session, target can't be picked. Calls setadminpassword.fn for
 *   password (and setuseremail.fn for the admin's email if needed).
 *
 * Field semantics (per spec Q3 + Q7):
 * - Email: pre-filled, optional change.
 * - Password: blank by default. Empty = don't change. Non-empty = change to this.
 * - Confirm password: required when password is non-empty (catch typos).
 * - Show/hide password toggle on both inputs.
 *
 * Per Q5: backend kills the target user's sessions on password change.
 * Per Q6: email change does NOT kill sessions.
 */

import { useEffect, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Typography,
  Alert,
  Table,
  TableBody,
  TableRow,
  TableCell,
  IconButton,
  InputAdornment,
} from '@mui/material';
import {
  Visibility as VisibilityIcon,
  VisibilityOff as VisibilityOffIcon,
} from '@mui/icons-material';
import {
  setUserEmail,
  setUserPassword,
  setAdminPassword,
} from '../../services/userApi';

export type EditUserMode = 'user' | 'admin';

interface EditUserModalProps {
  open: boolean;
  mode: EditUserMode;
  /** Target username (mode="user") OR the calling admin's username (mode="admin"). */
  username: string;
  /** Pre-fill the email field. Undefined → empty. */
  currentEmail?: string;
  onClose: () => void;
  onSaved: () => void;
}

export function EditUserModal({
  open,
  mode,
  username,
  currentEmail,
  onClose,
  onSaved,
}: EditUserModalProps) {
  const [email, setEmail] = useState(currentEmail ?? '');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // Reset fields whenever the modal opens for a new target.
  useEffect(() => {
    if (open) {
      setEmail(currentEmail ?? '');
      setPassword('');
      setConfirmPassword('');
      setShowPassword(false);
      setError(null);
    }
  }, [open, currentEmail, username]);

  const handleClose = () => {
    setError(null);
    onClose();
  };

  const handleSave = async () => {
    setError(null);

    const trimmedEmail = email.trim();
    const trimmedPassword = password.trim();
    const trimmedConfirm = confirmPassword.trim();

    // Email format check (only when email is being changed)
    if (trimmedEmail && trimmedEmail !== (currentEmail ?? '')) {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(trimmedEmail)) {
        setError('Invalid email format');
        return;
      }
    }

    // Password: empty = don't change. Non-empty = require confirm match.
    if (trimmedPassword) {
      if (trimmedPassword !== trimmedConfirm) {
        setError('Passwords do not match');
        return;
      }
    }

    // At least one field must change.
    const emailChanged = !!trimmedEmail && trimmedEmail !== (currentEmail ?? '');
    const passwordChanged = !!trimmedPassword;
    if (!emailChanged && !passwordChanged) {
      setError('No changes to save');
      return;
    }

    try {
      setLoading(true);

      // Order: email first (cheap, doesn't invalidate sessions), then password
      // (which may kill our session if mode="admin" — though backend keeps
      // the calling session alive per setadminpassword.fn implementation).
      if (emailChanged) {
        await setUserEmail(username, trimmedEmail);
      }
      if (passwordChanged) {
        if (mode === 'admin') {
          await setAdminPassword(trimmedPassword);
        } else {
          await setUserPassword(username, trimmedPassword);
        }
      }

      onSaved();
      handleClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save changes');
    } finally {
      setLoading(false);
    }
  };

  const passwordEndAdornment = (
    <InputAdornment position="end">
      <IconButton
        onClick={() => setShowPassword((s) => !s)}
        edge="end"
        size="small"
        aria-label="toggle password visibility"
      >
        {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
      </IconButton>
    </InputAdornment>
  );

  return (
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
        <Typography variant="h6">
          {mode === 'admin' ? 'Edit my account' : `Edit user: ${username}`}
        </Typography>
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

        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          Leave password blank to keep the current password.
        </Typography>

        <Table>
          <TableBody>
            <TableRow>
              <TableCell sx={{ border: 'none', pl: 0, width: '35%' }}>Email</TableCell>
              <TableCell sx={{ border: 'none', pr: 0 }}>
                <TextField
                  fullWidth
                  size="small"
                  type="email"
                  placeholder="Email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  disabled={loading}
                />
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell sx={{ border: 'none', pl: 0 }}>New password</TableCell>
              <TableCell sx={{ border: 'none', pr: 0 }}>
                <TextField
                  fullWidth
                  size="small"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="(leave blank to keep)"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={loading}
                  InputProps={{ endAdornment: passwordEndAdornment }}
                />
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell sx={{ border: 'none', pl: 0 }}>Confirm password</TableCell>
              <TableCell sx={{ border: 'none', pr: 0 }}>
                <TextField
                  fullWidth
                  size="small"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="(retype new password)"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  disabled={loading || !password}
                />
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </DialogContent>

      <DialogActions sx={{ p: 2, justifyContent: 'flex-end' }}>
        <Button variant="contained" onClick={handleClose} disabled={loading}>
          Cancel
        </Button>
        <Button variant="contained" onClick={handleSave} disabled={loading}>
          {loading ? 'Saving…' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
