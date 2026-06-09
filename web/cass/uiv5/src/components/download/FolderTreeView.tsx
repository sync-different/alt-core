/**
 * FolderTreeView — collapsible folder-structure view of the download queue
 * (PROJECT_FOLDER_DOWNLOAD). Groups queue items by their `file.file_relative_path`
 * (e.g. "folder1/sub/image1.jpg") into a nested tree, rolls up per-subfolder status
 * (X/Y files complete + progress bar, a green check when all complete, a warning when
 * any failed), and nests files under their folders. Items WITHOUT a relative path
 * (ordinary single-file downloads) are shown at the root.
 *
 * Pure presentational: derives everything from the queue snapshot passed in.
 */
import { useState, useMemo } from 'react';
import {
  Box, Typography, LinearProgress, Collapse, IconButton, Chip,
} from '@mui/material';
import FolderIcon from '@mui/icons-material/Folder';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import DownloadIcon from '@mui/icons-material/Download';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import type { QueueItem } from '../../contexts/DownloadManagerContext';

function formatFileSize(bytes: number): string {
  if (!bytes || bytes < 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  const u = ['KB', 'MB', 'GB', 'TB'];
  let v = bytes / 1024, i = 0;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return `${v.toFixed(v < 10 ? 1 : 0)} ${u[i]}`;
}

// ---- tree model ----
interface FileNode {
  kind: 'file';
  name: string;
  item: QueueItem;
}
interface FolderNode {
  kind: 'folder';
  name: string;
  path: string;                 // full path from root, for stable keys / expansion state
  children: TreeNode[];
}
type TreeNode = FolderNode | FileNode;

// roll-up summary for a folder subtree
interface RollUp {
  total: number;
  complete: number;
  failed: number;
  downloading: number;
  totalBytes: number;
  doneBytes: number;
}

function buildTree(items: QueueItem[]): FolderNode {
  const root: FolderNode = { kind: 'folder', name: '', path: '', children: [] };
  for (const item of items) {
    const rel = item.file.file_relative_path;
    if (!rel) {
      // ordinary download — sits at root as a file
      root.children.push({ kind: 'file', name: item.file.name, item });
      continue;
    }
    const parts = rel.split('/').filter((p) => p.length > 0);
    let cur = root;
    for (let i = 0; i < parts.length - 1; i++) {
      const seg = parts[i];
      const childPath = cur.path ? `${cur.path}/${seg}` : seg;
      let next = cur.children.find(
        (c): c is FolderNode => c.kind === 'folder' && c.name === seg,
      );
      if (!next) {
        next = { kind: 'folder', name: seg, path: childPath, children: [] };
        cur.children.push(next);
      }
      cur = next;
    }
    cur.children.push({ kind: 'file', name: parts[parts.length - 1], item });
  }
  return root;
}

function rollUp(node: FolderNode): RollUp {
  const acc: RollUp = { total: 0, complete: 0, failed: 0, downloading: 0, totalBytes: 0, doneBytes: 0 };
  for (const child of node.children) {
    if (child.kind === 'file') {
      const st = child.item.status;
      const size = child.item.file.file_size || 0;
      acc.total += 1;
      acc.totalBytes += size;
      if (st === 'complete') { acc.complete += 1; acc.doneBytes += size; }
      else if (st === 'failed' || st === 'cancelled') acc.failed += 1;
      else if (st === 'downloading') {
        acc.downloading += 1;
        acc.doneBytes += child.item.progress?.downloadedBytes || 0;
      }
    } else {
      const sub = rollUp(child);
      acc.total += sub.total;
      acc.complete += sub.complete;
      acc.failed += sub.failed;
      acc.downloading += sub.downloading;
      acc.totalBytes += sub.totalBytes;
      acc.doneBytes += sub.doneBytes;
    }
  }
  return acc;
}

function FolderRow({ node, depth }: { node: FolderNode; depth: number }) {
  const [open, setOpen] = useState(true);
  const r = useMemo(() => rollUp(node), [node]);
  const pct = r.totalBytes > 0 ? Math.round((r.doneBytes / r.totalBytes) * 100) : (r.total > 0 ? Math.round((r.complete / r.total) * 100) : 0);
  const allDone = r.total > 0 && r.complete === r.total;
  const hasFailed = r.failed > 0;
  const inProgress = r.downloading > 0;

  return (
    <Box>
      <Box
        sx={{ display: 'flex', alignItems: 'center', gap: 0.5, py: 0.5, pl: depth * 2, cursor: 'pointer' }}
        onClick={() => setOpen((o) => !o)}
      >
        <IconButton size="small" sx={{ p: 0.25 }}>
          {open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
        </IconButton>
        <FolderIcon fontSize="small" sx={{ color: '#FFB300' }} />
        <Typography variant="body2" sx={{ fontWeight: 500, flexShrink: 0 }}>{node.name}</Typography>
        {/* status icon */}
        {allDone && !hasFailed && <CheckCircleIcon fontSize="small" color="success" />}
        {hasFailed && <ErrorIcon fontSize="small" color="error" />}
        {inProgress && !allDone && <DownloadIcon fontSize="small" color="primary" />}
        <Box sx={{ flexGrow: 1 }} />
        <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
          {r.complete}/{r.total} files{r.failed > 0 ? ` · ${r.failed} failed` : ''} · {formatFileSize(r.totalBytes)}
        </Typography>
      </Box>
      {/* folder-level progress bar (rolled up) */}
      <Box sx={{ pl: depth * 2 + 4, pr: 1, mb: 0.5 }}>
        <LinearProgress
          variant="determinate"
          value={Math.min(100, pct)}
          color={hasFailed ? 'error' : allDone ? 'success' : 'primary'}
          sx={{ height: 4, borderRadius: 2 }}
        />
      </Box>
      <Collapse in={open} timeout="auto" unmountOnExit>
        {node.children.map((child) =>
          child.kind === 'folder'
            ? <FolderRow key={child.path} node={child} depth={depth + 1} />
            : <FileRow key={child.item.id} node={child} depth={depth + 1} />,
        )}
      </Collapse>
    </Box>
  );
}

function FileRow({ node, depth }: { node: FileNode; depth: number }) {
  const { status, file, progress } = node.item;
  const icon =
    status === 'complete' ? <CheckCircleIcon fontSize="small" color="success" />
    : status === 'failed' || status === 'cancelled' ? <ErrorIcon fontSize="small" color="error" />
    : status === 'downloading' ? <DownloadIcon fontSize="small" color="primary" />
    // pending (queued / waiting to retry) — hourglass, matching the List view's "Queued" chip.
    // Neutral grey so it doesn't clash with the amber folder icon.
    : status === 'queued' || status === 'waiting_retry'
      ? <HourglassEmptyIcon fontSize="small" sx={{ color: 'text.secondary' }} titleAccess="Pending" />
    : <InsertDriveFileIcon fontSize="small" sx={{ color: 'text.disabled' }} />;
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, py: 0.25, pl: depth * 2 + 3 }}>
      {icon}
      <Typography variant="body2" noWrap sx={{ flexGrow: 1, minWidth: 0 }}>{node.name}</Typography>
      {status === 'downloading' && (progress?.percentage ?? 0) > 0 && (
        <Chip size="small" variant="outlined" label={`${Math.round(progress!.percentage)}%`} sx={{ height: 18 }} />
      )}
      <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
        {formatFileSize(file.file_size || 0)}
      </Typography>
    </Box>
  );
}

export function FolderTreeView({ queue }: { queue: QueueItem[] }) {
  const root = useMemo(() => buildTree(queue), [queue]);
  if (queue.length === 0) return null;
  return (
    <Box sx={{ maxHeight: 300, overflow: 'auto', border: 1, borderColor: 'divider', borderRadius: 1, p: 1 }}>
      {root.children.map((child) =>
        child.kind === 'folder'
          ? <FolderRow key={child.path} node={child} depth={0} />
          : <FileRow key={child.item.id} node={child} depth={0} />,
      )}
    </Box>
  );
}
