/**
 * Remove User Confirmation Dialog
 * Confirms before removing a user from folder permissions
 */

import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
} from '@mui/material';
import { Warning as WarningIcon } from '@mui/icons-material';

interface RemoveUserDialogProps {
  open: boolean;
  username: string;
  folderName: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function RemoveUserDialog({
  open,
  username,
  folderName,
  onConfirm,
  onCancel,
}: RemoveUserDialogProps) {
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
        <WarningIcon color="warning" />
        Remove User Access
      </DialogTitle>

      <DialogContent sx={{ pt: 3 }}>
        <Typography>
          Are you sure you want to remove <strong>{username}</strong>'s access to the
          folder <strong>"{folderName}"</strong>?
        </Typography>

        <Box sx={{ mt: 2, p: 2, bgcolor: 'warning.light', borderRadius: 1 }}>
          <Typography variant="body2" color="warning.dark">
            This user will no longer be able to view or access this folder.
          </Typography>
        </Box>
      </DialogContent>

      <DialogActions sx={{ p: 2, gap: 1 }}>
        <Button onClick={onCancel} variant="outlined">
          Cancel
        </Button>
        <Button onClick={onConfirm} variant="contained" color="error">
          Remove Access
        </Button>
      </DialogActions>
    </Dialog>
  );
}
