/**
 * Shares Page Component
 * Displays and manages active file shares
 */

import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Button,
  Paper,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Radio,
  RadioGroup,
  FormControlLabel,
  FormControl,
  TextField,
  Checkbox,
  CircularProgress,
  Autocomplete,
  IconButton,
} from '@mui/material';
import { Add as AddIcon, ContentCopy as CopyIcon } from '@mui/icons-material';
import { getActiveShares, createShare, removeShare, getUsersAndEmails, getShareSettings, updateShare, checkRemoteAccess, getClusterId, type User } from '../services/shareService';
import { fetchTags } from '../services/fileApi';
import { AddUserModal } from '../components/modals/AddUserModal';

export function SharesPage() {
  const [sharesHtml, setSharesHtml] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [remoteAccessEnabled, setRemoteAccessEnabled] = useState<boolean | null>(null);
  const [clusterId, setClusterId] = useState<string | null>(null);

  // Add Share Modal state
  const [addShareOpen, setAddShareOpen] = useState(false);
  const [shareType, setShareType] = useState<'CLUSTER' | 'TAG'>('CLUSTER');
  const [tagName, setTagName] = useState('');
  const [availableTags, setAvailableTags] = useState<string[]>([]);
  const [shareWithAll, setShareWithAll] = useState(true);
  const [users, setUsers] = useState<User[]>([]);
  const [selectedUsers, setSelectedUsers] = useState<Set<string>>(new Set());
  const [creatingShare, setCreatingShare] = useState(false);
  const [addUserModalOpen, setAddUserModalOpen] = useState(false);

  // Edit Share Modal state
  const [editShareOpen, setEditShareOpen] = useState(false);
  const [editShareType, setEditShareType] = useState('');
  const [editShareKey, setEditShareKey] = useState('');
  const [editUsers, setEditUsers] = useState<User[]>([]);
  const [editSelectedUsers, setEditSelectedUsers] = useState<Set<string>>(new Set());
  const [updatingShare, setUpdatingShare] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);

  // Load shares and check remote access on mount
  useEffect(() => {
    const init = async () => {
      try {
        const isRemoteAccessEnabled = await checkRemoteAccess();
        setRemoteAccessEnabled(isRemoteAccessEnabled);

        // Get cluster ID if remote access is enabled
        if (isRemoteAccessEnabled) {
          const clusterIdValue = await getClusterId();
          setClusterId(clusterIdValue);
        }

        await loadShares();
      } catch (err) {
        console.error('Failed to check remote access:', err);
        setError('Failed to load page');
        setLoading(false);
      }
    };
    init();
  }, []);

  const loadShares = async () => {
    try {
      setLoading(true);
      setError(null);
      const html = await getActiveShares();
      console.log('Shares HTML:', html); // Debug: see what HTML we're getting

      // Wrap raw TR elements in proper table structure
      const wrappedHtml = `<table>${html}</table>`;
      setSharesHtml(wrappedHtml);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load shares');
    } finally {
      setLoading(false);
    }
  };

  const handleAddShareClick = async () => {
    setAddShareOpen(true);
    setError(null);
    setSuccess(null);

    // Load users and tags
    try {
      const [fetchedUsers, tagsData] = await Promise.all([
        getUsersAndEmails(),
        fetchTags()
      ]);
      setUsers(fetchedUsers);
      // Extract tag names from Tag objects (Tag from tagsSlice has 'tag' property)
      const tagNames = tagsData.map(tag => tag.tag);
      setAvailableTags(tagNames);
    } catch (err) {
      console.error('Failed to load users/tags:', err);
    }
  };

  const handleCloseAddShare = () => {
    setAddShareOpen(false);
    setShareType('CLUSTER');
    setTagName('');
    setShareWithAll(true);
    setSelectedUsers(new Set());
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

  const handleCreateShare = async () => {
    setError(null);
    setSuccess(null);

    // Validation
    if (shareType === 'TAG' && (!tagName || tagName.trim() === '')) {
      setError('Tag name is required');
      return;
    }

    if (!shareWithAll && selectedUsers.size === 0) {
      setError('Please select at least one user or choose "Share with all users"');
      return;
    }

    try {
      setCreatingShare(true);

      await createShare({
        shareType,
        shareKey: shareType === 'TAG' ? tagName.trim() : '',
        selectedUsers: shareWithAll ? [] : Array.from(selectedUsers),
      });

      setSuccess('Share created successfully');
      handleCloseAddShare();

      // Reload shares
      await loadShares();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create share');
    } finally {
      setCreatingShare(false);
    }
  };

  const handleRemoveShare = useCallback(async (shareType: string, shareKey: string) => {
    if (!window.confirm('Are you sure you want to remove this share?')) {
      return;
    }

    try {
      setError(null);
      setSuccess(null);
      const updatedHtml = await removeShare(shareType, shareKey);

      // Wrap raw TR elements in proper table structure
      const wrappedHtml = `<table>${updatedHtml}</table>`;
      setSharesHtml(wrappedHtml);
      setSuccess('Share removed successfully');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove share');
    }
  }, []);

  const handleUserAdded = async () => {
    // Reload users after adding a new one
    try {
      const fetchedUsers = await getUsersAndEmails();
      setUsers(fetchedUsers);
      // Also reload for edit modal if it's open
      if (editShareOpen) {
        setEditUsers(fetchedUsers);
      }
    } catch (err) {
      console.error('Failed to reload users:', err);
    }
  };

  const handleEditShare = useCallback(async (shareType: string, shareKey: string) => {
    console.log('handleEditShare called', shareType, shareKey);
    try {
      setError(null);
      setEditShareType(shareType);
      setEditShareKey(shareKey);

      // Load share settings and all users
      const [shareSettings, allUsers] = await Promise.all([
        getShareSettings(shareType, shareKey),
        getUsersAndEmails()
      ]);

      console.log('Loaded share settings:', shareSettings);
      console.log('Loaded users:', allUsers);

      setEditUsers(allUsers);

      // Mark users who have access
      const usersWithAccess = new Set(shareSettings.users.map(u => u.username));
      setEditSelectedUsers(usersWithAccess);

      setEditShareOpen(true);
    } catch (err) {
      console.error('Error in handleEditShare:', err);
      setError(err instanceof Error ? err.message : 'Failed to load share settings');
    }
  }, []);

  const handleCloseEditShare = () => {
    setEditShareOpen(false);
    setEditShareType('');
    setEditShareKey('');
    setEditUsers([]);
    setEditSelectedUsers(new Set());
  };

  const handleEditUserToggle = (username: string) => {
    const newSelected = new Set(editSelectedUsers);
    if (newSelected.has(username)) {
      newSelected.delete(username);
    } else {
      newSelected.add(username);
    }
    setEditSelectedUsers(newSelected);
  };

  const handleUpdateShare = async () => {
    setError(null);
    setSuccess(null);

    if (editSelectedUsers.size === 0) {
      setError('Please select at least one user');
      return;
    }

    try {
      setUpdatingShare(true);

      await updateShare(editShareType, editShareKey, Array.from(editSelectedUsers));

      setSuccess('Share updated successfully');
      handleCloseEditShare();

      // Reload shares
      await loadShares();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update share');
    } finally {
      setUpdatingShare(false);
    }
  };

  const handleCopyRemoteLink = async () => {
    if (!clusterId) return;

    const link = `https://web.alterante.com/cass/index.htm?cluster=${clusterId}`;

    try {
      // Try modern clipboard API first
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(link);
      } else {
        // Fallback for older browsers or non-HTTPS contexts
        const textArea = document.createElement('textarea');
        textArea.value = link;
        textArea.style.position = 'fixed';
        textArea.style.left = '-999999px';
        textArea.style.top = '-999999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();

        try {
          document.execCommand('copy');
        } finally {
          document.body.removeChild(textArea);
        }
      }

      setCopySuccess(true);

      // Reset success message after 2 seconds
      setTimeout(() => {
        setCopySuccess(false);
      }, 2000);
    } catch (err) {
      console.error('Failed to copy link:', err);
      setError('Failed to copy link to clipboard');
    }
  };

  // Parse HTML and add click handlers for buttons
  useEffect(() => {
    console.log('useEffect running, sharesHtml:', !!sharesHtml);
    if (sharesHtml) {
      const container = document.getElementById('shares-container');
      if (container) {
        console.log('Container found');

        // Use event delegation - add a single listener to the container
        const clickHandler = (e: Event) => {
          const target = e.target as HTMLElement;

          // Find the button element (might be target or parent)
          let button: HTMLElement | null = target;
          while (button && button !== container && button.tagName !== 'BUTTON' && button.tagName !== 'A' && button.tagName !== 'INPUT') {
            button = button.parentElement;
          }

          if (!button || button === container) return;

          const onclickAttr = button.getAttribute('onclick');
          if (onclickAttr) {
            // Check for remove button
            const removeMatch = onclickAttr.match(/confirmremoveshare\('([^']+)','([^']+)'\)/);
            if (removeMatch) {
              e.preventDefault();
              e.stopPropagation();
              e.stopImmediatePropagation();
              const [, shareTypeVal, shareKeyVal] = removeMatch;
              handleRemoveShare(shareTypeVal, shareKeyVal);
              return;
            }

            // Check for edit button
            const editMatch = onclickAttr.match(/getmodal\('([^']+)','([^']+)'\)/);
            if (editMatch) {
              e.preventDefault();
              e.stopPropagation();
              e.stopImmediatePropagation();
              const [, shareTypeVal, shareKeyVal] = editMatch;
              console.log('Edit button clicked via delegation:', shareTypeVal, shareKeyVal);
              handleEditShare(shareTypeVal, shareKeyVal);
              return;
            }
          }
        };

        // Add listener in capture phase to intercept before onclick
        container.addEventListener('click', clickHandler, true);

        // Cleanup function
        return () => {
          container.removeEventListener('click', clickHandler, true);
        };
      }
    }
  }, [sharesHtml, handleRemoveShare, handleEditShare]);

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">
          Active Shares
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleAddShareClick}
        >
          Add Share
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      {remoteAccessEnabled === false && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Remote access is currently disabled. Sharing features are not available.
        </Alert>
      )}

      {remoteAccessEnabled === true && clusterId && (
        <Alert
          severity={copySuccess ? "success" : "info"}
          sx={{ mb: 2 }}
          action={
            <IconButton
              color="inherit"
              size="small"
              onClick={handleCopyRemoteLink}
              title="Copy to clipboard"
            >
              <CopyIcon fontSize="small" />
            </IconButton>
          }
        >
          {copySuccess ? (
            'Link copied to clipboard!'
          ) : (
            <>
              Use this link to access this computer remotely: <strong>https://web.alterante.com/cass/index.htm?cluster={clusterId}</strong>
            </>
          )}
        </Alert>
      )}

      <Paper sx={{ p: 3 }}>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress />
          </Box>
        ) : sharesHtml ? (
          <Box
            id="shares-container"
            dangerouslySetInnerHTML={{ __html: sharesHtml }}
            sx={{
              // Reset any Bootstrap/legacy styles
              '& *': {
                boxSizing: 'border-box',
              },
              // Table styling
              '& table': {
                width: '100% !important',
                borderCollapse: 'collapse',
                mt: 2,
                display: 'table !important',
              },
              '& thead': {
                display: 'table-header-group !important',
              },
              '& tbody': {
                display: 'table-row-group !important',
              },
              '& tr': {
                display: 'table-row !important',
              },
              '& th, & td': {
                display: 'table-cell !important',
                padding: '12px 8px',
                textAlign: 'left',
                borderBottom: '1px solid #e0e0e0',
                verticalAlign: 'middle',
              },
              '& th': {
                backgroundColor: '#f5f5f5',
                fontWeight: 600,
                color: '#333',
              },
              '& tbody tr:hover': {
                backgroundColor: '#f9f9f9',
              },
              // Button styling
              '& button, & a.btn, & input[type="button"]': {
                padding: '6px 16px',
                backgroundColor: '#004080',
                color: 'white !important',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                textDecoration: 'none',
                display: 'inline-block',
                fontSize: '14px',
                fontFamily: 'inherit',
                '&:hover': {
                  backgroundColor: '#003060',
                },
              },
              // Remove button styling (red)
              '& button[onclick*="confirmremoveshare"], & a[onclick*="confirmremoveshare"], & input[onclick*="confirmremoveshare"]': {
                backgroundColor: '#c75450',
                position: 'relative',
                paddingLeft: '36px',
                '&:hover': {
                  backgroundColor: '#b23c38',
                },
                '&::before': {
                  content: '""',
                  position: 'absolute',
                  left: '12px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  width: '14px',
                  height: '16px',
                  backgroundImage: 'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'14\' height=\'16\' viewBox=\'0 0 14 16\' fill=\'white\'%3E%3Cpath d=\'M11 2H9c0-.55-.45-1-1-1H6c-.55 0-1 .45-1 1H3c-.55 0-1 .45-1 1v1c0 .55.45 1 1 1h8c.55 0 1-.45 1-1V3c0-.55-.45-1-1-1zM4 15c0 .55.45 1 1 1h4c.55 0 1-.45 1-1V6H4v9zm2-7c0-.28.22-.5.5-.5s.5.22.5.5v5c0 .28-.22.5-.5.5s-.5-.22-.5-.5V8zm2 0c0-.28.22-.5.5-.5s.5.22.5.5v5c0 .28-.22.5-.5.5s-.5-.22-.5-.5V8z\'/%3E%3C/svg%3E")',
                  backgroundSize: 'contain',
                  backgroundRepeat: 'no-repeat',
                  backgroundPosition: 'center',
                },
              },
              // Override any conflicting styles
              '& .table': {
                marginBottom: 0,
              },
              '& .btn': {
                margin: '0 4px',
              },
            }}
          />
        ) : (
          <Typography color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
            No active shares
          </Typography>
        )}
      </Paper>

      {/* Add Share Modal */}
      <Dialog open={addShareOpen} onClose={handleCloseAddShare} maxWidth="sm" fullWidth>
        <DialogTitle>Choose what to Share</DialogTitle>
        <DialogContent>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <FormControl component="fieldset" sx={{ width: '100%', mt: 2 }}>
            <RadioGroup value={shareType} onChange={(e) => setShareType(e.target.value as 'CLUSTER' | 'TAG')}>
              <FormControlLabel
                value="CLUSTER"
                control={<Radio />}
                label="All Files on this Computer"
              />
              <FormControlLabel
                value="TAG"
                control={<Radio />}
                label="Tag"
              />
            </RadioGroup>

            {shareType === 'TAG' && (
              <Autocomplete
                freeSolo
                options={availableTags}
                value={tagName}
                onChange={(_event, newValue) => {
                  setTagName(newValue || '');
                }}
                onInputChange={(_event, newInputValue) => {
                  setTagName(newInputValue);
                }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Tag Name"
                    placeholder="Enter or select tag name"
                  />
                )}
                sx={{ mt: 2 }}
              />
            )}

            <Box sx={{ mt: 3, mb: 2 }}>
              <Typography variant="subtitle1" gutterBottom>
                Share with:
              </Typography>

              <FormControlLabel
                control={
                  <Checkbox
                    checked={shareWithAll}
                    onChange={(e) => {
                      setShareWithAll(e.target.checked);
                      if (e.target.checked) {
                        setSelectedUsers(new Set());
                      }
                    }}
                  />
                }
                label="Share with all users"
              />

              {!shareWithAll && (
                <>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => setAddUserModalOpen(true)}
                    sx={{ mt: 1, mb: 2 }}
                  >
                    Add New User
                  </Button>

                  <Paper variant="outlined" sx={{ maxHeight: 200, overflow: 'auto', p: 1 }}>
                    {users.length === 0 ? (
                      <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
                        No users available
                      </Typography>
                    ) : (
                      users.map((user) => (
                        <FormControlLabel
                          key={user.username}
                          control={
                            <Checkbox
                              checked={selectedUsers.has(user.username)}
                              onChange={() => handleUserToggle(user.username)}
                            />
                          }
                          label={`${user.username} (${user.email})`}
                          sx={{ display: 'block', mb: 0.5 }}
                        />
                      ))
                    )}
                  </Paper>
                </>
              )}
            </Box>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseAddShare} disabled={creatingShare}>
            Cancel
          </Button>
          <Button onClick={handleCreateShare} variant="contained" disabled={creatingShare}>
            {creatingShare ? 'Creating...' : 'Create Share'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Share Modal */}
      <Dialog open={editShareOpen} onClose={handleCloseEditShare} maxWidth="sm" fullWidth>
        <DialogTitle>Edit Share Settings</DialogTitle>
        <DialogContent>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Box sx={{ mb: 3 }}>
            <Typography variant="body1" sx={{ mb: 1 }}>
              <strong>Type:</strong> {editShareType}
            </Typography>
            <Typography variant="body1">
              <strong>Share Key:</strong> {editShareKey}
            </Typography>
          </Box>

          <Typography variant="subtitle1" gutterBottom>
            Users with access:
          </Typography>

          <Button
            variant="outlined"
            size="small"
            onClick={() => setAddUserModalOpen(true)}
            sx={{ mt: 1, mb: 2 }}
          >
            Add New User
          </Button>

          <Paper variant="outlined" sx={{ maxHeight: 300, overflow: 'auto', p: 1 }}>
            {editUsers.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
                No users available
              </Typography>
            ) : (
              editUsers.map((user) => (
                <FormControlLabel
                  key={user.username}
                  control={
                    <Checkbox
                      checked={editSelectedUsers.has(user.username)}
                      onChange={() => handleEditUserToggle(user.username)}
                      disabled={updatingShare}
                    />
                  }
                  label={`${user.username} (${user.email})`}
                  sx={{ display: 'block', mb: 0.5 }}
                />
              ))
            )}
          </Paper>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseEditShare} disabled={updatingShare}>
            Cancel
          </Button>
          <Button onClick={handleUpdateShare} variant="contained" disabled={updatingShare}>
            {updatingShare ? 'Updating...' : 'Update Share'}
          </Button>
        </DialogActions>
      </Dialog>

      <AddUserModal
        open={addUserModalOpen}
        onClose={() => setAddUserModalOpen(false)}
        onUserAdded={handleUserAdded}
      />
    </Box>
  );
}
