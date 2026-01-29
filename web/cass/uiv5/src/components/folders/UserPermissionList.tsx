/**
 * User Permission List Component
 * Displays list of users with their permissions for a folder
 */

import { useState } from 'react';
import {
  Box,
  List,
  ListItem,
  ListItemText,
  IconButton,
  Select,
  MenuItem,
  Typography,
  Tooltip,
  Divider,
  Checkbox,
  FormControlLabel,
} from '@mui/material';
import {
  Add as AddIcon,
  Remove as RemoveIcon,
  Person as PersonIcon,
} from '@mui/icons-material';
import type { FolderPermission, PermissionLevel, PermissionDepth } from '../../services/folderPermissionApi';
import { AddUserDialog } from './AddUserDialog';
import { RemoveUserDialog } from './RemoveUserDialog';

interface UserPermissionListProps {
  permissions: FolderPermission[];
  folderName: string;
  onAddUser: (username: string, permission: PermissionLevel) => void;
  onRemoveUser: (username: string) => void;
  onUpdatePermission: (username: string, permission: PermissionLevel) => void;
  onUpdateDepth: (username: string, depth: PermissionDepth) => void;
  disabled?: boolean;
}

export function UserPermissionList({
  permissions,
  folderName,
  onAddUser,
  onRemoveUser,
  onUpdatePermission,
  onUpdateDepth,
  disabled = false,
}: UserPermissionListProps) {
  const [addDialogOpen, setAddDialogOpen] = useState(false);
  const [removeDialogOpen, setRemoveDialogOpen] = useState(false);
  const [userToRemove, setUserToRemove] = useState<string | null>(null);

  const handleAddClick = () => {
    setAddDialogOpen(true);
  };

  const handleAddUser = (username: string, permission: PermissionLevel) => {
    onAddUser(username, permission);
    setAddDialogOpen(false);
  };

  const handleRemoveClick = (username: string) => {
    setUserToRemove(username);
    setRemoveDialogOpen(true);
  };

  const handleRemoveConfirm = () => {
    if (userToRemove) {
      onRemoveUser(userToRemove);
    }
    setRemoveDialogOpen(false);
    setUserToRemove(null);
  };

  const handleRemoveCancel = () => {
    setRemoveDialogOpen(false);
    setUserToRemove(null);
  };

  const existingUsers = permissions.map((p) => p.username);

  return (
    <Box>
      {/* Header with Add button */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: 1,
        }}
      >
        <Typography variant="subtitle2" color="text.secondary">
          User Permissions
        </Typography>
        <Tooltip title="Add user">
          <IconButton
            size="small"
            onClick={handleAddClick}
            disabled={disabled}
            color="primary"
          >
            <AddIcon />
          </IconButton>
        </Tooltip>
      </Box>

      <Divider sx={{ mb: 1 }} />

      {/* Permission List */}
      {permissions.length === 0 ? (
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ py: 2, textAlign: 'center' }}
        >
          No users have access to this folder.
        </Typography>
      ) : (
        <List dense disablePadding>
          {permissions.map((perm) => (
            <ListItem
              key={perm.username}
              sx={{
                px: 0,
                py: 0.5,
                flexDirection: 'column',
                alignItems: 'stretch',
                '&:hover': {
                  bgcolor: 'action.hover',
                  borderRadius: 1,
                },
              }}
            >
              {/* Top row: username, permission select, remove button */}
              <Box sx={{ display: 'flex', alignItems: 'center', width: '100%' }}>
                <PersonIcon
                  sx={{ mr: 1, color: 'text.secondary', fontSize: 20 }}
                />
                <ListItemText
                  primary={perm.username}
                  secondary={perm.inheritedFrom ? `(inherited from ${perm.inheritedFrom})` : null}
                  primaryTypographyProps={{
                    variant: 'body2',
                    fontWeight: 500,
                  }}
                  secondaryTypographyProps={{
                    variant: 'caption',
                    color: 'info.main',
                    sx: { fontStyle: 'italic' },
                  }}
                  sx={{ flex: 1 }}
                />
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <Select
                    size="small"
                    value={perm.permission}
                    onChange={(e) =>
                      onUpdatePermission(perm.username, e.target.value as PermissionLevel)
                    }
                    disabled={disabled}
                    sx={{
                      minWidth: 100,
                      '& .MuiSelect-select': {
                        py: 0.5,
                        fontSize: '0.875rem',
                      },
                    }}
                  >
                    <MenuItem value="r">Read Only</MenuItem>
                    <MenuItem value="rw">Read & Write</MenuItem>
                  </Select>
                  <Tooltip title="Remove user">
                    <IconButton
                      size="small"
                      onClick={() => handleRemoveClick(perm.username)}
                      disabled={disabled}
                      color="error"
                    >
                      <RemoveIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              </Box>
              {/* Bottom row: Apply to subfolders checkbox */}
              <Box sx={{ pl: 4, mt: 0.5 }}>
                <FormControlLabel
                  control={
                    <Checkbox
                      size="small"
                      checked={perm.depth === '*'}
                      onChange={(e) =>
                        onUpdateDepth(perm.username, e.target.checked ? '*' : '.')
                      }
                      disabled={disabled}
                    />
                  }
                  label={
                    <Typography variant="caption" color="text.secondary">
                      Apply to subfolders
                    </Typography>
                  }
                  sx={{ m: 0 }}
                />
              </Box>
            </ListItem>
          ))}
        </List>
      )}

      {/* Add User Dialog */}
      <AddUserDialog
        open={addDialogOpen}
        folderName={folderName}
        existingUsers={existingUsers}
        onAdd={handleAddUser}
        onCancel={() => setAddDialogOpen(false)}
      />

      {/* Remove User Dialog */}
      <RemoveUserDialog
        open={removeDialogOpen}
        username={userToRemove || ''}
        folderName={folderName}
        onConfirm={handleRemoveConfirm}
        onCancel={handleRemoveCancel}
      />
    </Box>
  );
}
