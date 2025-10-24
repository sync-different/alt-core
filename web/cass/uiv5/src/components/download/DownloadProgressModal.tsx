/**
 * Download Progress Modal
 * Shows download progress with speed, estimated time remaining, and error/retry information
 */

import { Dialog, DialogTitle, DialogContent, DialogActions, Button, LinearProgress, Typography, Box, Alert } from '@mui/material';
import { Download as DownloadIcon, Warning as WarningIcon } from '@mui/icons-material';
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
  const hasErrors = progress.errorCount > 0 || progress.retryCount > 0;

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
            sx={{
              height: 8,
              borderRadius: 4,
              my: 2,
              '& .MuiLinearProgress-bar': {
                backgroundColor: hasErrors && !isComplete ? 'warning.main' : undefined
              }
            }}
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

          {/* Current Status */}
          {!isComplete && progress.currentStatus && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {progress.currentStatus}
            </Typography>
          )}

          {/* Estimated Time Remaining */}
          {!isComplete && progress.estimatedTimeRemaining > 0 && !progress.lastError && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              Estimated time remaining: {formatTimeRemaining(progress.estimatedTimeRemaining)}
            </Typography>
          )}

          {/* Error and Retry Information */}
          {hasErrors && !isComplete && (
            <Alert
              severity="warning"
              icon={<WarningIcon fontSize="small" />}
              sx={{ mt: 2 }}
            >
              <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                {progress.retryCount} retry attempt{progress.retryCount !== 1 ? 's' : ''}
                {progress.errorCount > progress.retryCount && ` (${progress.errorCount} error${progress.errorCount !== 1 ? 's' : ''})`}
              </Typography>
              {progress.lastError && (
                <Typography variant="caption" sx={{ display: 'block', mt: 0.5 }}>
                  {progress.lastError}
                </Typography>
              )}
            </Alert>
          )}

          {/* Success Message */}
          {isComplete && (
            <Alert severity="success" sx={{ mt: 2 }}>
              <Typography variant="body2">
                File downloaded successfully!
              </Typography>
              {hasErrors && (
                <Typography variant="caption" sx={{ display: 'block', mt: 0.5 }}>
                  Completed with {progress.retryCount} retry attempt{progress.retryCount !== 1 ? 's' : ''}
                </Typography>
              )}
            </Alert>
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
