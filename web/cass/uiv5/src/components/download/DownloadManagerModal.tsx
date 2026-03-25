/**
 * Download Manager Modal
 * Shows download queue, per-file progress, global settings, and download log.
 */

import { useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button,
  LinearProgress, Typography, Box, Alert, Slider, Collapse,
  IconButton, List, ListItem, ListItemText,
  Chip, Divider, FormControlLabel, Switch,
} from '@mui/material';
import {
  Download as DownloadIcon,
  Close as CloseIcon,
  Delete as DeleteIcon,
  PlayArrow as StartIcon,
  Stop as StopIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  CheckCircle as CheckIcon,
  Error as ErrorIcon,
  HourglassEmpty as QueuedIcon,
  Cancel as CancelledIcon,
  ClearAll as ClearAllIcon,
  FolderOpen as FolderIcon,
} from '@mui/icons-material';
import { useDownloadManager, type QueueItem } from '../../contexts/DownloadManagerContext';
import { formatSpeed, formatTimeRemaining } from '../../services/downloadService';

const chunkMarks = [
  { value: 5, label: '5 MB' },
  { value: 10, label: '10 MB' },
  { value: 15, label: '15 MB' },
  { value: 20, label: '20 MB' },
];

const retryMarks = [
  { value: 1, label: '1' },
  { value: 3, label: '3' },
  { value: 5, label: '5' },
  { value: 10, label: '10' },
];

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function StatusChip({ status }: { status: QueueItem['status'] }) {
  switch (status) {
    case 'queued':
      return <Chip icon={<QueuedIcon />} label="Queued" size="small" variant="outlined" />;
    case 'downloading':
      return <Chip icon={<DownloadIcon />} label="Downloading" size="small" color="primary" />;
    case 'complete':
      return <Chip icon={<CheckIcon />} label="Complete" size="small" color="success" />;
    case 'failed':
      return <Chip icon={<ErrorIcon />} label="Failed" size="small" color="error" />;
    case 'cancelled':
      return <Chip icon={<CancelledIcon />} label="Cancelled" size="small" variant="outlined" color="warning" />;
  }
}

export function DownloadManagerModal() {
  const {
    queue, isOpen, isProcessing, chunkSizeMB, maxRetries, saveAs, downloadFolder, folderError, logEntries,
    removeFromQueue, startDownloads, cancelAll, closeModal,
    setChunkSizeMB, setMaxRetries, setSaveAs, pickDownloadFolder, clearDownloadFolder, clearCompleted,
  } = useDownloadManager();

  const [logExpanded, setLogExpanded] = useState(false);
  const [settingsExpanded, setSettingsExpanded] = useState(true);

  const queuedCount = queue.filter(q => q.status === 'queued').length;
  const completedCount = queue.filter(q => q.status === 'complete' || q.status === 'failed' || q.status === 'cancelled').length;
  const hasQueuedItems = queuedCount > 0;

  // Debug: log every render with queue state (expanded so values are visible)
  queue.forEach((q, i) => {
    console.log(`[DM-Modal] render item[${i}] status=${q.status} pct=${q.progress.percentage} file=${q.file.name.slice(0,30)}`);
  });

  return (
    <Dialog
      open={isOpen}
      maxWidth="sm"
      fullWidth
      onClose={() => closeModal()}
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pr: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <DownloadIcon />
          Download Manager
          {queue.length > 0 && (
            <Chip label={`${queue.length} file${queue.length !== 1 ? 's' : ''}`} size="small" variant="outlined" />
          )}
        </Box>
        <IconButton onClick={() => closeModal()} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ pb: 1 }}>
        {/* Settings Section */}
        <Box sx={{ mb: 2 }}>
          <Box
            sx={{ display: 'flex', alignItems: 'center', cursor: 'pointer', py: 0.5 }}
            onClick={() => setSettingsExpanded(!settingsExpanded)}
          >
            <Typography variant="subtitle2" color="text.secondary">
              Download Settings
            </Typography>
            <Box sx={{ ml: 'auto' }}>
              {settingsExpanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
            </Box>
          </Box>

          <Collapse in={settingsExpanded}>
            {/* Chunk Size Slider */}
            <Box sx={{ mb: 2, mt: 1 }}>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Chunk Size
              </Typography>
              <Box sx={{ px: 4 }}>
                <Slider
                  value={chunkSizeMB}
                  onChange={(_, value) => setChunkSizeMB(value as number)}
                  min={5}
                  max={20}
                  step={5}
                  marks={chunkMarks}
                  valueLabelDisplay="auto"
                  valueLabelFormat={(value) => `${value} MB`}
                  disabled={isProcessing}
                />
              </Box>
            </Box>

            {/* Max Retries Slider */}
            <Box sx={{ mb: 1 }}>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Max Retries per Chunk
              </Typography>
              <Box sx={{ px: 4 }}>
                <Slider
                  value={maxRetries}
                  onChange={(_, value) => setMaxRetries(value as number)}
                  min={1}
                  max={10}
                  step={1}
                  marks={retryMarks}
                  valueLabelDisplay="auto"
                  disabled={isProcessing}
                />
              </Box>
            </Box>

            {/* Download Folder */}
            <Box sx={{ mt: 1, mb: 1 }}>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Download Folder
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<FolderIcon />}
                  onClick={pickDownloadFolder}
                  disabled={isProcessing}
                >
                  {downloadFolder || 'Choose Folder'}
                </Button>
                {downloadFolder && (
                  <IconButton size="small" onClick={clearDownloadFolder} disabled={isProcessing} title="Clear folder selection">
                    <CloseIcon fontSize="small" />
                  </IconButton>
                )}
              </Box>
              {folderError && (
                <Typography variant="caption" color="error" sx={{ mt: 0.5, display: 'block' }}>
                  {folderError}
                </Typography>
              )}
              {!downloadFolder && !folderError && (
                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                  No folder selected — files save to browser default
                </Typography>
              )}
            </Box>

            {/* Save As Toggle */}
            {!downloadFolder && (
              <Box sx={{ mt: 1 }}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={saveAs}
                      onChange={(e) => setSaveAs(e.target.checked)}
                      disabled={isProcessing}
                      size="small"
                    />
                  }
                  label={
                    <Typography variant="body2" color="text.secondary">
                      Choose file name and location (Save As)
                    </Typography>
                  }
                />
              </Box>
            )}
          </Collapse>
        </Box>

        <Divider sx={{ mb: 1 }} />

        {/* Queue */}
        {queue.length === 0 ? (
          <Box sx={{ py: 4, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              No files in download queue. Click the download button on any file to add it.
            </Typography>
          </Box>
        ) : (
          <List dense disablePadding sx={{ maxHeight: 300, overflow: 'auto' }}>
            {queue.map((item) => (
              <ListItem key={item.id} sx={{ px: 0, flexDirection: 'column', alignItems: 'stretch' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', width: '100%' }}>
                  <ListItemText
                    primary={
                      <Typography variant="body2" noWrap sx={{ maxWidth: 280 }}>
                        {item.file.name}
                      </Typography>
                    }
                    secondary={formatFileSize(item.file.file_size)}
                  />
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, ml: 1 }}>
                    <StatusChip status={item.status} />
                    {(item.status === 'queued') && (
                      <IconButton size="small" onClick={() => removeFromQueue(item.id)} title="Remove from queue">
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </Box>
                </Box>

                {/* Per-file progress bar when downloading */}
                {item.status === 'downloading' && (
                  <Box sx={{ width: '100%', mt: 0.5, mb: 1 }}>
                    <LinearProgress
                      variant="determinate"
                      value={item.progress.percentage}
                      sx={{ height: 6, borderRadius: 3 }}
                    />
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">
                        {item.progress.percentage}%
                        {item.progress.currentStatus && ` — ${item.progress.currentStatus}`}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {item.progress.speedKBps > 0 && formatSpeed(item.progress.speedKBps)}
                        {item.progress.elapsedTime > 0 && ` | ${formatTimeRemaining(item.progress.elapsedTime)}`}
                      </Typography>
                    </Box>
                  </Box>
                )}

                {/* Completed stats */}
                {item.status === 'complete' && item.progress.averageSpeedKBps != null && (
                  <Typography variant="caption" color="text.secondary" sx={{ ml: 2, mb: 0.5 }}>
                    {formatTimeRemaining(item.progress.elapsedTime)} — avg {formatSpeed(item.progress.averageSpeedKBps)}
                  </Typography>
                )}

                {/* Error message */}
                {item.status === 'failed' && item.error && (
                  <Typography variant="caption" color="error" sx={{ ml: 2, mb: 0.5 }}>
                    {item.error}
                  </Typography>
                )}

                <Divider />
              </ListItem>
            ))}
          </List>
        )}

        {/* Download Log */}
        {logEntries.length > 0 && (
          <Box sx={{ mt: 2 }}>
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                cursor: 'pointer',
                py: 1,
              }}
              onClick={() => setLogExpanded(!logExpanded)}
            >
              <Typography variant="subtitle2" color="text.secondary">
                Download Log ({logEntries.length} messages)
              </Typography>
              {logExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            </Box>

            <Collapse in={logExpanded}>
              <Box
                sx={{
                  maxHeight: 200,
                  overflow: 'auto',
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  p: 1,
                }}
              >
                {logEntries.map((entry, idx) => (
                  <Alert
                    key={idx}
                    severity={entry.type === 'success' ? 'success' : entry.type === 'error' ? 'error' : entry.type === 'warning' ? 'warning' : 'info'}
                    sx={{ mb: 0.5, py: 0, '& .MuiAlert-message': { py: 0.5 } }}
                    icon={false}
                  >
                    <Typography variant="caption" component="div">
                      <span style={{ color: '#888' }}>
                        [{new Date(entry.timestamp).toLocaleTimeString()}]
                      </span>
                      {' '}{entry.message}
                    </Typography>
                  </Alert>
                ))}
              </Box>
            </Collapse>
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2, justifyContent: 'space-between' }}>
        <Box>
          {completedCount > 0 && (
            <Button size="small" startIcon={<ClearAllIcon />} onClick={clearCompleted}>
              Clear Completed
            </Button>
          )}
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          {isProcessing ? (
            <Button onClick={cancelAll} color="error" startIcon={<StopIcon />}>
              Cancel All
            </Button>
          ) : (
            hasQueuedItems && (
              <Button onClick={startDownloads} variant="contained" startIcon={<StartIcon />}>
                Start Download{queuedCount > 1 ? `s (${queuedCount})` : ''}
              </Button>
            )
          )}
        </Box>
      </DialogActions>
    </Dialog>
  );
}
