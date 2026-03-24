/**
 * Current Logins Tab
 * Shows all active login sessions with ability to remove tokens.
 */

import { useState, useEffect, useCallback } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
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
  Tooltip,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
} from '@mui/material';
import {
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { fetchLogins, removeAuthToken } from '../../services/fileApi';
import type { LoginSession } from '../../services/fileApi';
import { selectUuid, clearAuth } from '../../store/slices/authSlice';
import Cookies from 'js-cookie';

export function CurrentLoginsTab() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const currentUuid = useSelector(selectUuid);
  const [sessions, setSessions] = useState<LoginSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Confirm delete dialog state
  const [deleteTarget, setDeleteTarget] = useState<LoginSession | null>(null);
  const [deleting, setDeleting] = useState(false);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchLogins();
      setSessions(data);
    } catch {
      setError('Failed to load login sessions.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const handleDeleteClick = (session: LoginSession) => {
    setDeleteTarget(session);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;

    const isDeletingSelf = deleteTarget.uuid === currentUuid;
    setDeleting(true);

    try {
      const result = await removeAuthToken(deleteTarget.uuid);
      if (result.status === 'ok') {
        if (isDeletingSelf) {
          // Admin deleted their own session — log out
          Cookies.remove('uuid');
          localStorage.removeItem('uuid');
          dispatch(clearAuth());
          navigate('/login');
          return;
        }
        // Auto-refresh the list
        setDeleteTarget(null);
        await loadSessions();
      } else {
        setError(`Failed to remove token: ${result.status}`);
        setDeleteTarget(null);
      }
    } catch {
      setError('Failed to remove auth token.');
      setDeleteTarget(null);
    } finally {
      setDeleting(false);
    }
  };

  const handleDeleteCancel = () => {
    setDeleteTarget(null);
  };

  const isDeletingSelf = deleteTarget?.uuid === currentUuid;

  const formatLoginTime = (epochMs: number): string => {
    if (!epochMs) return 'Unknown';
    return new Date(epochMs).toLocaleString();
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Header with refresh */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h6">Active Login Sessions</Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={loadSessions} disabled={loading}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : sessions.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
          No active sessions found.
        </Typography>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell><strong>Username</strong></TableCell>
                <TableCell><strong>UUID</strong></TableCell>
                <TableCell><strong>Login Time</strong></TableCell>
                <TableCell><strong>Type</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sessions.map((session) => {
                const isSelf = session.uuid === currentUuid;
                return (
                  <TableRow
                    key={session.uuid}
                    sx={{
                      bgcolor: isSelf ? 'rgba(0, 120, 212, 0.08)' : 'inherit',
                    }}
                  >
                    <TableCell>
                      {session.username}
                      {isSelf && (
                        <Chip
                          label="You"
                          size="small"
                          color="primary"
                          variant="outlined"
                          sx={{ ml: 1 }}
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                        {session.uuid}
                      </Typography>
                    </TableCell>
                    <TableCell>{formatLoginTime(session.loginTime)}</TableCell>
                    <TableCell>
                      {session.isRemote ? (
                        <Chip label="Remote" size="small" color="warning" variant="outlined" />
                      ) : (
                        <Chip label="Local" size="small" color="success" variant="outlined" />
                      )}
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title={isSelf ? 'Remove your own session' : 'Remove session'}>
                        <IconButton
                          size="small"
                          color="error"
                          onClick={() => handleDeleteClick(session)}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Confirm delete dialog */}
      <Dialog open={!!deleteTarget} onClose={handleDeleteCancel}>
        <DialogTitle>
          {isDeletingSelf ? 'Log Out?' : 'Remove Session?'}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {isDeletingSelf
              ? 'You are about to remove your own session. This will log you out immediately.'
              : `Are you sure you want to remove the session for user "${deleteTarget?.username}" (${deleteTarget?.uuid})?`}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDeleteCancel} disabled={deleting}>
            Cancel
          </Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleting}
          >
            {deleting ? <CircularProgress size={20} /> : isDeletingSelf ? 'Log Out' : 'Remove'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
