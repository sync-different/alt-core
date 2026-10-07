/**
 * RemoveFileTypeDialog — stage removal of admin-added file types (written on the tab's Save).
 * Only admin-added types are listed: shipped types can be unticked but never removed (Q11).
 * Removing a type also unticks it; files already indexed stay searchable (Q2).
 */

import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  TextField,
  Typography,
} from '@mui/material';

export interface RemovableType {
  ext: string;
  description: string;
  groupLabel: string;
  selected: boolean;
}

interface Props {
  open: boolean;
  removable: RemovableType[];
  selectedCount: number;   // types currently ticked (staged state) — a removal can't leave zero (Q3)
  onClose: () => void;
  onRemove: (exts: string[]) => void;
}

export function RemoveFileTypeDialog({ open, removable, selectedCount, onClose, onRemove }: Props) {
  const [query, setQuery] = useState('');
  const [picked, setPicked] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (open) {
      setQuery('');
      setPicked(new Set());
    }
  }, [open]);

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return removable.filter((t) => !q || t.ext.includes(q) || t.description.toLowerCase().includes(q));
  }, [removable, query]);

  const toggle = (ext: string) => {
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(ext)) next.delete(ext); else next.add(ext);
      return next;
    });
  };

  const unticks = removable.filter((t) => picked.has(t.ext) && t.selected).length;
  const wouldEmpty = picked.size > 0 && selectedCount - unticks <= 0;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Remove file type</DialogTitle>
      <DialogContent>
        {removable.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            There are no admin-added file types. Shipped types can be unticked, but not removed.
          </Typography>
        ) : (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Only admin-added types can be removed. Files of a removed type that are already indexed stay searchable.
            </Typography>
            <TextField
              placeholder="Search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              size="small"
              fullWidth
            />
            <List dense sx={{ maxHeight: 320, overflow: 'auto' }}>
              {shown.map((t) => (
                <ListItemButton key={t.ext} onClick={() => toggle(t.ext)}>
                  <ListItemIcon sx={{ minWidth: 36 }}>
                    <Checkbox edge="start" checked={picked.has(t.ext)} tabIndex={-1} disableRipple size="small" />
                  </ListItemIcon>
                  <ListItemText primary={`${t.ext} — ${t.description}`} secondary={t.groupLabel} />
                </ListItemButton>
              ))}
            </List>
            {wouldEmpty && (
              <Alert severity="warning" sx={{ mt: 1 }}>
                At least one file type must stay selected.
              </Alert>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          color="error"
          variant="contained"
          disabled={picked.size === 0 || wouldEmpty}
          onClick={() => onRemove([...picked])}
        >
          Remove{picked.size > 0 ? ` (${picked.size})` : ''}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
