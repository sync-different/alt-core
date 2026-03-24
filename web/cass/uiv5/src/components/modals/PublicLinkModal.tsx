/**
 * Public Link Modal
 * Allows admin to generate a public link for a file by creating
 * an auth token for a selected non-admin user.
 */

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  TextField,
  MenuItem,
  CircularProgress,
  Alert,
  InputAdornment,
  IconButton,
  Tooltip,
} from '@mui/material';
import {
  ContentCopy as CopyIcon,
  Check as CheckIcon,
} from '@mui/icons-material';
import { generatePublicToken, fetchUsersAndEmail, removeAuthToken } from '../../services/fileApi';
import type { File } from '../../types/models';

interface PublicLinkModalProps {
  open: boolean;
  onClose: () => void;
  file: File;
}

export function PublicLinkModal({ open, onClose, file }: PublicLinkModalProps) {
  const [users, setUsers] = useState<string[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [selectedUser, setSelectedUser] = useState('');
  const [generatedUuid, setGeneratedUuid] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // Load users when modal opens
  useEffect(() => {
    if (open) {
      setGeneratedUuid(null);
      setSelectedUser('');
      setError(null);
      setCopied(false);
      loadUsers();
    }
  }, [open]);

  const loadUsers = async () => {
    setUsersLoading(true);
    try {
      const data = await fetchUsersAndEmail();
      setUsers(data.map((u) => u.username));
    } catch {
      setError('Failed to load users.');
    } finally {
      setUsersLoading(false);
    }
  };

  const handleGenerate = async () => {
    if (!selectedUser) return;
    setGenerating(true);
    setError(null);
    setCopied(false);
    try {
      const result = await generatePublicToken(selectedUser);
      if (result.uuid) {
        setGeneratedUuid(result.uuid);
      } else {
        setError(result.error || 'Failed to generate token.');
      }
    } catch {
      setError('Failed to generate public token.');
    } finally {
      setGenerating(false);
    }
  };

  const buildPublicUrl = (): string => {
    if (!generatedUuid) return '';
    const md5 = file.nickname || file.md5hash || file.multiclusterid;
    const isVideo = file.file_group === 'movie';

    if (isVideo) {
      const params = new URLSearchParams({
        md5,
        uuid: generatedUuid,
      });
      return `${window.location.origin}/cass/getvideo.m3u8?${params.toString()}`;
    }

    const ext = (file.file_ext || '').replace(/^\./, '');
    const params = new URLSearchParams({
      sNamer: md5,
      sFileExt: ext,
      sFileName: file.file_name || file.name,
      uuid: generatedUuid,
    });
    return `${window.location.origin}/cass/getfile.fn?${params.toString()}`;
  };

  const handleCopy = async () => {
    const url = buildPublicUrl();
    let success = false;

    // Try modern clipboard API first
    if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(url);
        success = true;
      } catch {
        // Falls through to fallback
      }
    }

    // Fallback: use the visible TextField's native selection + execCommand
    // MUI Dialog's focus trap interferes with off-screen textarea approach,
    // so we select text directly from the already-rendered input field.
    if (!success) {
      try {
        const dialogEl = document.querySelector('.MuiDialog-root');
        const input = dialogEl?.querySelector<HTMLInputElement>('input[readonly]');
        if (input) {
          input.focus();
          input.setSelectionRange(0, input.value.length);
          success = document.execCommand('copy');
        }
      } catch {
        // Falls through
      }
    }

    if (success) {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleCancel = async () => {
    // If token was generated, revoke it
    if (generatedUuid) {
      try {
        await removeAuthToken(generatedUuid);
      } catch {
        // Best effort — close anyway
      }
    }
    onClose();
  };

  const handleClose = () => {
    // Close without revoking — token stays valid
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth onClick={(e) => e.stopPropagation()} onMouseDown={(e) => e.stopPropagation()}>
      <DialogTitle>Generate Public Link</DialogTitle>
      <DialogContent dividers>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {/* File info */}
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          File: <strong>{file.file_name || file.name}</strong>
        </Typography>

        {/* User selection */}
        <TextField
          select
          label="Select User"
          value={selectedUser}
          onChange={(e) => {
            setSelectedUser(e.target.value);
            setGeneratedUuid(null);
            setCopied(false);
          }}
          fullWidth
          size="small"
          disabled={usersLoading || !!generatedUuid}
          sx={{ mb: 2 }}
        >
          {usersLoading ? (
            <MenuItem disabled>Loading users...</MenuItem>
          ) : users.length === 0 ? (
            <MenuItem disabled>No users available</MenuItem>
          ) : (
            users.map((u) => (
              <MenuItem key={u} value={u}>{u}</MenuItem>
            ))
          )}
        </TextField>

        {/* Generate button */}
        {!generatedUuid && (
          <Button
            variant="contained"
            onClick={handleGenerate}
            disabled={!selectedUser || generating}
            fullWidth
          >
            {generating ? <CircularProgress size={20} /> : 'Generate Token'}
          </Button>
        )}

        {/* Generated URL display */}
        {generatedUuid && (
          <Box sx={{ mt: 2 }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Public URL:
            </Typography>
            <TextField
              fullWidth
              size="small"
              value={buildPublicUrl()}
              InputProps={{
                readOnly: true,
                sx: { fontFamily: 'monospace', fontSize: '0.8rem' },
                endAdornment: (
                  <InputAdornment position="end">
                    <Tooltip title={copied ? 'Copied!' : 'Copy URL'}>
                      <IconButton onClick={handleCopy} edge="end" size="small">
                        {copied ? <CheckIcon color="success" /> : <CopyIcon />}
                      </IconButton>
                    </Tooltip>
                  </InputAdornment>
                ),
              }}
            />
            <Alert severity="info" sx={{ mt: 1 }}>
              Anyone with this link can access this file as user "{selectedUser}". The token can be revoked from the Admin tab.
            </Alert>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel}>
          Cancel
        </Button>
        <Button onClick={handleClose} variant="contained" disabled={!generatedUuid}>
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}
