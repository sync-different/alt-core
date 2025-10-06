import { useState } from 'react';
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
} from '@mui/material';
import { addUser, type AddUserData } from '../../services/shareService';

interface AddUserModalProps {
  open: boolean;
  onClose: () => void;
  onUserAdded: () => void;
}

export function AddUserModal({ open, onClose, onUserAdded }: AddUserModalProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleClose = () => {
    setUsername('');
    setPassword('');
    setEmail('');
    setError(null);
    onClose();
  };

  const handleAddUser = async () => {
    setError(null);

    // Validation
    if (!username || username.trim() === '') {
      setError('Username is mandatory');
      return;
    }

    if (!password || password.trim() === '') {
      setError('Password is mandatory');
      return;
    }

    if (!email || email.trim() === '') {
      setError('Email is mandatory');
      return;
    }

    // Email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('Invalid email format');
      return;
    }

    try {
      setLoading(true);
      const userData: AddUserData = {
        username: username.trim(),
        password: password.trim(),
        email: email.trim(),
      };

      await addUser(userData);

      // Success - reset form and notify parent
      setUsername('');
      setPassword('');
      setEmail('');
      onUserAdded();
      handleClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add user');
    } finally {
      setLoading(false);
    }
  };

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
        <Typography variant="h6">Add User</Typography>
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

        <Table>
          <TableBody>
            <TableRow>
              <TableCell sx={{ border: 'none', pl: 0 }}>Username</TableCell>
              <TableCell sx={{ border: 'none', pr: 0 }}>
                <TextField
                  fullWidth
                  size="small"
                  placeholder="User Name"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  disabled={loading}
                />
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell sx={{ border: 'none', pl: 0 }}>Password</TableCell>
              <TableCell sx={{ border: 'none', pr: 0 }}>
                <TextField
                  fullWidth
                  size="small"
                  type="password"
                  placeholder="Password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={loading}
                />
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell sx={{ border: 'none', pl: 0 }}>Email</TableCell>
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
          </TableBody>
        </Table>
      </DialogContent>

      <DialogActions sx={{ p: 2, justifyContent: 'flex-end' }}>
        <Button variant="contained" onClick={handleClose} disabled={loading}>
          Cancel
        </Button>
        <Button variant="contained" onClick={handleAddUser} disabled={loading}>
          {loading ? 'Adding...' : 'Add User'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
