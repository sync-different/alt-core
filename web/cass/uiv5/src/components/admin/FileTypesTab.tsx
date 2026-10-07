/**
 * FileTypesTab — admin "File types": choose which file types the Scrubber indexes, and add or
 * remove admin-added types. Edits are staged; one Save writes them together (one request, one
 * version check). The scanner reloads FileExtensions.txt every pass, so a save applies on the next
 * scan pass with no restart. See internal/PROJECT_TAB_ADMIN_FILETYPES.md.
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  InputAdornment,
  Paper,
  Snackbar,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  Architecture as CadIcon,
  Brush as AdobeIcon,
  DeleteOutline as RemoveIcon,
  Description as DocIcon,
  Image as ImageIcon,
  InsertDriveFile as OtherIcon,
  Movie as VideoIcon,
  MusicNote as AudioIcon,
  Search as SearchIcon,
  Undo as UndoIcon,
} from '@mui/icons-material';
import type { SvgIconComponent } from '@mui/icons-material';
import {
  FileTypesError,
  getFileTypes,
  saveFileTypes,
} from '../../services/fileTypesApi';
import type { FileTypesConfig, NewFileType } from '../../services/fileTypesApi';
import { AddFileTypeDialog } from './AddFileTypeDialog';
import { RemoveFileTypeDialog } from './RemoveFileTypeDialog';

const GROUP_ICONS: Record<string, SvgIconComponent> = {
  office: DocIcon,
  video: VideoIcon,
  audio: AudioIcon,
  foto: ImageIcon,
  adobe: AdobeIcon,
  cad: CadIcon,
  others: OtherIcon,
};

interface Row {
  ext: string;
  description: string;
  custom: boolean;
  isNew: boolean;       // staged add
  removed: boolean;     // staged removal
}

export function FileTypesTab() {
  const [cfg, setCfg] = useState<FileTypesConfig | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  // staged edits
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [adds, setAdds] = useState<NewFileType[]>([]);
  const [removes, setRemoves] = useState<Set<string>>(new Set());
  const [query, setQuery] = useState('');

  const [addOpen, setAddOpen] = useState(false);
  const [removeOpen, setRemoveOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>(
    { open: false, message: '', severity: 'success' },
  );
  const showSnack = (message: string, severity: 'success' | 'error' = 'success') =>
    setSnackbar({ open: true, message, severity });

  const resetDraft = (c: FileTypesConfig) => {
    const sel = new Set<string>();
    c.groups.forEach((g) => g.types.forEach((t) => { if (t.selected) sel.add(t.ext); }));
    setSelected(sel);
    setAdds([]);
    setRemoves(new Set());
  };

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    setConflict(false);
    try {
      const c = await getFileTypes();
      setCfg(c);
      resetDraft(c);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : 'Failed to load file types.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // ---- derived ----
  const baseline = useMemo(() => {
    const s = new Set<string>();
    cfg?.groups.forEach((g) => g.types.forEach((t) => { if (t.selected) s.add(t.ext); }));
    return s;
  }, [cfg]);

  const effectiveSelected = useMemo(
    () => new Set([...selected].filter((e) => !removes.has(e))),
    [selected, removes],
  );

  const known = useMemo(() => {
    const s = new Set<string>();
    cfg?.groups.forEach((g) => g.types.forEach((t) => s.add(t.ext)));
    adds.forEach((a) => s.add(a.ext));
    return s;
  }, [cfg, adds]);

  const groupsView = useMemo(() => {
    if (!cfg) return [];
    const q = query.trim().toLowerCase();
    return cfg.groups.map((g) => {
      const rows: Row[] = g.types.map((t) => ({
        ext: t.ext, description: t.description, custom: t.custom, isNew: false, removed: removes.has(t.ext),
      }));
      adds.filter((a) => a.group === g.id).forEach((a) => rows.push({
        ext: a.ext, description: a.description, custom: true, isNew: true, removed: false,
      }));
      const shown = q ? rows.filter((r) => r.ext.includes(q) || r.description.toLowerCase().includes(q)) : rows;
      return { group: g, rows, shown };
    });
  }, [cfg, adds, removes, query]);

  const totalCount = known.size - removes.size;
  const catalogAdded = adds.map((a) => a.ext);
  const catalogRemoved = [...removes];
  const newlySelected = [...effectiveSelected].filter((e) => !baseline.has(e) && !catalogAdded.includes(e));
  const newlyUnselected = [...baseline].filter((e) => !effectiveSelected.has(e) && !removes.has(e));
  const dirty = catalogAdded.length > 0 || catalogRemoved.length > 0 || newlySelected.length > 0 || newlyUnselected.length > 0;
  const canSave = dirty && effectiveSelected.size > 0 && !saving;

  // Warn before leaving the page with unsaved changes.
  useEffect(() => {
    if (!dirty) return undefined;
    const h = (e: BeforeUnloadEvent) => { e.preventDefault(); };
    window.addEventListener('beforeunload', h);
    return () => window.removeEventListener('beforeunload', h);
  }, [dirty]);

  // ---- edits ----
  const toggle = (ext: string) => setSelected((prev) => {
    const next = new Set(prev);
    if (next.has(ext)) next.delete(ext); else next.add(ext);
    return next;
  });

  const toggleGroup = (rows: Row[], on: boolean) => setSelected((prev) => {
    const next = new Set(prev);
    rows.filter((r) => !r.removed).forEach((r) => { if (on) next.add(r.ext); else next.delete(r.ext); });
    return next;
  });

  const handleAdd = (t: NewFileType, scan: boolean) => {
    setAdds((prev) => [...prev, t]);
    if (scan) setSelected((prev) => new Set(prev).add(t.ext));
    setAddOpen(false);
  };

  const discardAdd = (ext: string) => {
    setAdds((prev) => prev.filter((a) => a.ext !== ext));
    setSelected((prev) => { const n = new Set(prev); n.delete(ext); return n; });
  };

  const handleRemove = (exts: string[]) => {
    setRemoves((prev) => { const n = new Set(prev); exts.forEach((e) => n.add(e)); return n; });
    setRemoveOpen(false);
  };

  const undoRemove = (ext: string) => setRemoves((prev) => { const n = new Set(prev); n.delete(ext); return n; });

  const removable = useMemo(() => (cfg ? cfg.groups.flatMap((g) => g.types
    .filter((t) => t.custom && !removes.has(t.ext))
    .map((t) => ({ ext: t.ext, description: t.description, groupLabel: g.label, selected: selected.has(t.ext) }))) : []),
  [cfg, removes, selected]);

  const doSave = async () => {
    if (!cfg) return;
    setSaving(true);
    try {
      const res = await saveFileTypes({
        selected: [...effectiveSelected],
        add: adds,
        remove: catalogRemoved,
        version: cfg.version,
      });
      setCfg(res);
      resetDraft(res);
      setConfirmOpen(false);
      showSnack('Saved. Changes apply on the next scan pass.');
    } catch (err) {
      setConfirmOpen(false);
      if (err instanceof FileTypesError && err.status === 409) {
        setConflict(true);
      } else {
        showSnack(err instanceof Error ? err.message : 'Save failed', 'error');
      }
    } finally {
      setSaving(false);
    }
  };

  // ---- render ----
  if (loading && !cfg) {
    return <Box sx={{ p: 4, display: 'flex', justifyContent: 'center' }}><CircularProgress /></Box>;
  }
  if (loadError && !cfg) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={load}>Retry</Button>}>
          {loadError}
        </Alert>
      </Box>
    );
  }
  if (!cfg) return null;

  const list = (label: string, items: string[]) => items.length > 0 && (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="subtitle2">{label} ({items.length})</Typography>
      <Typography variant="body2" color="text.secondary">{items.join('  ')}</Typography>
    </Box>
  );

  return (
    <Box sx={{ p: 3 }}>
      {/* Top action bar */}
      <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1, mb: 2 }}>
        <Typography variant="h6" sx={{ mr: 1 }}>File types</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
          {effectiveSelected.size} of {totalCount} selected
        </Typography>
        <TextField
          placeholder="Search"
          size="small"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
          sx={{ width: 200 }}
        />
        <Button startIcon={<AddIcon />} variant="outlined" size="small" onClick={() => setAddOpen(true)}>
          Add file type
        </Button>
        <Tooltip title={removable.length === 0 ? 'Only admin-added types can be removed' : ''}>
          <span>
            <Button
              startIcon={<RemoveIcon />}
              variant="outlined"
              size="small"
              color="error"
              disabled={removable.length === 0}
              onClick={() => setRemoveOpen(true)}
            >
              Remove file type
            </Button>
          </span>
        </Tooltip>
        <Button size="small" disabled={!dirty || saving} onClick={() => resetDraft(cfg)}>Cancel</Button>
        <Button variant="contained" size="small" disabled={!canSave} onClick={() => setConfirmOpen(true)}>
          Save
        </Button>
      </Box>

      <Alert severity="info" sx={{ mb: 2 }}>
        The scanner indexes only the selected types. Changes apply on the next scan pass. Thumbnails and video
        streaming exist only for some types.
      </Alert>
      {conflict && (
        <Alert severity="warning" sx={{ mb: 2 }} action={<Button color="inherit" size="small" onClick={load}>Reload</Button>}>
          Another admin changed the file types since you loaded them. Reload to see their changes (your unsaved edits will be discarded).
        </Alert>
      )}
      {cfg.orphans.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Selected but not in the catalog (kept, still scanned): {cfg.orphans.join(', ')}
        </Alert>
      )}
      {effectiveSelected.size === 0 && (
        <Alert severity="error" sx={{ mb: 2 }}>At least one file type must be selected.</Alert>
      )}

      {/* Group cards */}
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 2 }}>
        {groupsView.filter((v) => v.shown.length > 0 || !query).map(({ group, rows, shown }) => {
          const Icon = GROUP_ICONS[group.id] ?? OtherIcon;
          const live = rows.filter((r) => !r.removed);
          const on = live.filter((r) => effectiveSelected.has(r.ext)).length;
          return (
            <Paper key={group.id} variant="outlined" sx={{ p: 1.5 }}>
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={live.length > 0 && on === live.length}
                    indeterminate={on > 0 && on < live.length}
                    onChange={(e) => toggleGroup(rows, e.target.checked)}
                    disabled={live.length === 0}
                  />
                )}
                label={(
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Icon fontSize="small" color="action" />
                    <Typography variant="subtitle1">{group.label}</Typography>
                    <Typography variant="caption" color="text.secondary">{on}/{live.length}</Typography>
                  </Box>
                )}
              />
              <Box sx={{ pl: 3 }}>
                {shown.map((r) => (
                  <Box key={r.ext} sx={{ display: 'flex', alignItems: 'center', opacity: r.removed ? 0.5 : 1 }}>
                    <FormControlLabel
                      sx={{ flex: 1, mr: 0 }}
                      control={(
                        <Checkbox
                          size="small"
                          checked={!r.removed && effectiveSelected.has(r.ext)}
                          disabled={r.removed}
                          onChange={() => toggle(r.ext)}
                        />
                      )}
                      label={(
                        <Typography variant="body2" sx={{ textDecoration: r.removed ? 'line-through' : 'none' }}>
                          <b>{r.ext}</b> {r.description}
                        </Typography>
                      )}
                    />
                    {r.isNew && <Chip label="new" size="small" color="primary" sx={{ mr: 0.5 }} onDelete={() => discardAdd(r.ext)} />}
                    {!r.isNew && r.custom && !r.removed && <Chip label="custom" size="small" variant="outlined" />}
                    {r.removed && (
                      <Tooltip title="Undo remove">
                        <IconButton size="small" onClick={() => undoRemove(r.ext)}><UndoIcon fontSize="small" /></IconButton>
                      </Tooltip>
                    )}
                  </Box>
                ))}
              </Box>
            </Paper>
          );
        })}
      </Box>

      <AddFileTypeDialog
        open={addOpen}
        groups={cfg.groups}
        existing={known}
        onClose={() => setAddOpen(false)}
        onAdd={handleAdd}
      />
      <RemoveFileTypeDialog
        open={removeOpen}
        removable={removable}
        selectedCount={effectiveSelected.size}
        onClose={() => setRemoveOpen(false)}
        onRemove={handleRemove}
      />

      {/* Save confirmation */}
      <Dialog open={confirmOpen} onClose={() => !saving && setConfirmOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Save file types?</DialogTitle>
        <DialogContent>
          {list('Add to catalog', catalogAdded)}
          {list('Remove from catalog', catalogRemoved)}
          {list('Start scanning', newlySelected)}
          {list('Stop scanning', newlyUnselected)}
          {(newlyUnselected.length > 0 || catalogRemoved.length > 0) && (
            <Alert severity="info" sx={{ mt: 1 }}>
              Files of types you stop scanning or remove that are already indexed stay searchable. Only new files of
              those types stop being indexed.
            </Alert>
          )}
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Changes apply on the next scan pass.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)} disabled={saving}>Cancel</Button>
          <Button variant="contained" onClick={doSave} disabled={saving} startIcon={saving ? <CircularProgress size={16} /> : undefined}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={5000}
        onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar((s) => ({ ...s, open: false }))}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
