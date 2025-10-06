/**
 * Download Progress Modal
 * Shows download progress with speed and estimated time remaining
 */

import { Dialog, DialogTitle, DialogContent, DialogActions, Button, LinearProgress, Typography, Box } from '@mui/material';
import { Download as DownloadIcon } from '@mui/icons-material';
import type { DownloadProgress } from '../../services/downloadService';
import { formatSpeed, formatTimeRemaining } from '../../services/downloadService';

interface DownloadProgressModalProps {
  open: boolean;
  fileName: string;
  progress: DownloadProgress;
  onCancel: () => void;
  isComplete: boolean;
}

export function DownloadProgressModal({
  open,
  fileName,
  progress,
  onCancel,
  isComplete,
}: DownloadProgressModalProps) {
  return (
    <Dialog
      open={open}
      maxWidth="sm"
      fullWidth
      disableEscapeKeyDown
      onClose={(_, reason) => {
        if (reason !== 'backdropClick') {
          onCancel();
        }
      }}
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <DownloadIcon />
        {isComplete ? 'Download Complete' : 'Downloading...'}
      </DialogTitle>

      <DialogContent>
        <Box sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {fileName}
          </Typography>

          <LinearProgress
            variant="determinate"
            value={progress.percentage}
            sx={{ height: 8, borderRadius: 4, my: 2 }}
          />

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              {progress.percentage}%
            </Typography>

            {!isComplete && progress.speedKBps > 0 && (
              <Typography variant="body2" color="text.secondary">
                {formatSpeed(progress.speedKBps)}
              </Typography>
            )}
          </Box>

          {!isComplete && progress.estimatedTimeRemaining > 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              Estimated time remaining: {formatTimeRemaining(progress.estimatedTimeRemaining)}
            </Typography>
          )}

          {isComplete && (
            <Typography variant="body2" color="success.main" sx={{ mt: 1 }}>
              File downloaded successfully!
            </Typography>
          )}
        </Box>
      </DialogContent>

      <DialogActions>
        {!isComplete ? (
          <Button onClick={onCancel} color="error">
            Cancel
          </Button>
        ) : (
          <Button onClick={onCancel} variant="contained">
            Close
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
