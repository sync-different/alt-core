/**
 * Selection Toolbar
 * Displays actions available for selected files.
 *
 * Default mode (no props): drives off the file-view's `useFileSelection` hook
 * and refreshes via `resetFiles` — backwards-compatible with MyFilesPage.
 *
 * Custom mode (caller supplies `selection` + optional `onAfterTag`): drives
 * off any selection-shaped object. Used by the FoldersPage to share the same
 * UI without coupling to `state.files`. See
 * internal/PROJECT_FOLDER_MULTISELECT.md.
 */

import { useState } from 'react';
import { useDispatch } from 'react-redux';
import {
  Box,
  Toolbar,
  Typography,
  IconButton,
  Tooltip,
  Snackbar,
  Alert,
} from '@mui/material';
import {
  Close as CloseIcon,
  Download as DownloadIcon,
  Label as LabelIcon,
  Share as ShareIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { useFileSelection } from '../../hooks/useFileSelection';
import { useDownloadManager } from '../../contexts/DownloadManagerContext';
import { TagDialog } from '../tags/TagDialog';
import { ShareDialog } from '../share/ShareDialog';
import { addTags } from '../../services/fileApi';
import { resetFiles } from '../../store/slices/filesSlice';
import type { AppDispatch } from '../../store/store';
import type { File } from '../../types/models';

/** Minimal selection contract the toolbar needs. */
export interface ToolbarSelection {
  selectedCount: number;            // total selected (files + folders)
  selectedFiles: File[];
  selectedFileIds: string[];
  selectedFolderCount?: number;     // folders selected (FoldersPage); omit/0 in file-only views
  selectedFolderFileCount?: number; // recursive file count across selected folders
  selectedFolderBytes?: number;     // recursive total bytes across selected folders
  selectedFolderStatsLoading?: boolean; // still enumerating one+ selected folder
  deselectAll: () => void;
}

interface SelectionToolbarProps {
  inline?: boolean;
  /**
   * Override the selection source. When omitted, falls back to the file-view's
   * `useFileSelection` (legacy behavior — MyFilesPage relies on this).
   */
  selection?: ToolbarSelection;
  /**
   * Called after a successful tag add (e.g. to refresh the underlying list).
   * When omitted, falls back to dispatching `resetFiles()` for the file view.
   */
  onAfterTag?: () => void;
  /**
   * Override the Download click. Caller is fully responsible for queueing.
   * Used by the FoldersPage to hydrate `file_size` (missing from the
   * getfolders-json.fn response) via getfileinfo.fn before queueing — without
   * this, the download manager falls back to the direct (non-chunked) path
   * and reports negative bytes-left because totalBytes is 0.
   * When omitted, falls back to the legacy per-file `addToQueue` loop.
   */
  onDownloadOverride?: () => void;
}

export function SelectionToolbar({
  inline = false,
  selection,
  onAfterTag,
  onDownloadOverride,
}: SelectionToolbarProps) {
  const dispatch = useDispatch<AppDispatch>();
  const fileViewSelection = useFileSelection();
  const sel: ToolbarSelection = selection ?? fileViewSelection;
  const { selectedCount, selectedFiles, selectedFileIds, deselectAll } = sel;
  // file/folder breakdown for an accurate label ("X files, Y folders selected (N files, Z GB)")
  const folderCount = sel.selectedFolderCount ?? 0;
  const fileCount = Math.max(0, selectedCount - folderCount);
  const fmtBytes = (n: number): string => {
    if (n < 1024) return `${n} B`;
    const u = ['KB', 'MB', 'GB', 'TB'];
    let v = n / 1024, i = 0;
    while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
    return `${v.toFixed(v < 10 ? 1 : 0)} ${u[i]}`;
  };
  const selectionLabel = (() => {
    const parts: string[] = [];
    if (fileCount > 0) parts.push(`${fileCount} ${fileCount === 1 ? 'file' : 'files'}`);
    if (folderCount > 0) parts.push(`${folderCount} ${folderCount === 1 ? 'folder' : 'folders'}`);
    let label = parts.length === 0 ? '0 files selected' : `${parts.join(', ')} selected`;
    // when folders are selected, append the recursive contents summary
    if (folderCount > 0) {
      if (sel.selectedFolderStatsLoading) {
        label += ' (scanning…)';
      } else {
        const fc = sel.selectedFolderFileCount ?? 0;
        const bytes = sel.selectedFolderBytes ?? 0;
        label += ` (${fc} ${fc === 1 ? 'file' : 'files'}, ${fmtBytes(bytes)})`;
      }
    }
    return label;
  })();
  const { addToQueue } = useDownloadManager();
  const [tagDialogOpen, setTagDialogOpen] = useState(false);
  const [shareDialogOpen, setShareDialogOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
    open: false,
    message: '',
    severity: 'success',
  });

  const handleAddTags = async (tags: string[]) => {
    try {
      await addTags(selectedFileIds, tags);
      setSnackbar({
        open: true,
        message: `Tags added to ${selectedFileIds.length} file${selectedFileIds.length !== 1 ? 's' : ''}`,
        severity: 'success',
      });
      deselectAll();
      // Refresh the underlying list so tag updates are visible.
      if (onAfterTag) {
        onAfterTag();
      } else {
        dispatch(resetFiles());
      }
    } catch (error) {
      console.error('Failed to add tags:', error);
      setSnackbar({
        open: true,
        message: 'Failed to add tags',
        severity: 'error',
      });
    }
  };

  const handleDownload = () => {
    if (onDownloadOverride) {
      onDownloadOverride();
      return;
    }
    selectedFiles.forEach(file => addToQueue(file));
    setSnackbar({
      open: true,
      message: `Added ${selectedFiles.length} file${selectedFiles.length !== 1 ? 's' : ''} to download queue`,
      severity: 'success',
    });
  };

  if (selectedCount === 0) {
    return null;
  }

  const toolbarContent = (
    <>
      <IconButton
        edge="start"
        color="inherit"
        onClick={deselectAll}
        sx={{ mr: 1 }}
      >
        <CloseIcon />
      </IconButton>

      <Typography variant={inline ? "subtitle1" : "h6"} sx={{ mr: 2 }}>
        {selectionLabel}
      </Typography>

      <Tooltip title="Add Tags">
        <IconButton color="inherit" onClick={() => setTagDialogOpen(true)} size={inline ? "small" : "medium"}>
          <LabelIcon />
        </IconButton>
      </Tooltip>

      <Tooltip title="Download">
        <IconButton color="inherit" onClick={handleDownload} size={inline ? "small" : "medium"}>
          <DownloadIcon />
        </IconButton>
      </Tooltip>

      <Tooltip title="Share">
        <IconButton color="inherit" onClick={() => setShareDialogOpen(true)} size={inline ? "small" : "medium"}>
          <ShareIcon />
        </IconButton>
      </Tooltip>

      <Tooltip title="Delete (coming soon)">
        {/* Wrap in span so Tooltip works even when the IconButton is disabled. */}
        <span>
          <IconButton color="inherit" disabled size={inline ? "small" : "medium"}>
            <DeleteIcon />
          </IconButton>
        </span>
      </Tooltip>
    </>
  );

  return (
    <>
      {inline ? (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            px: 2,
            py: 0.5,
            borderRadius: 1,
            height: 48,
          }}
        >
          {toolbarContent}
        </Box>
      ) : (
        <Box
          sx={{
            position: 'sticky',
            top: 0,
            zIndex: 1100,
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            mb: 2,
          }}
        >
          <Toolbar>
            {toolbarContent}
          </Toolbar>
        </Box>
      )}

      <TagDialog
        open={tagDialogOpen}
        onClose={() => setTagDialogOpen(false)}
        onSave={handleAddTags}
        selectedFileIds={selectedFileIds}
      />

      <ShareDialog
        open={shareDialogOpen}
        onClose={() => setShareDialogOpen(false)}
        fileIds={selectedFileIds}
      />

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
}
