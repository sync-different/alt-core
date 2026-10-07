/**
 * AddFileTypeDialog — stage a new admin-added file type (written on the tab's Save, not here).
 * Validation mirrors the server (FileTypesConfig): extension, description, existing group.
 * Defaults per the plan: group "Other" (Q12), "Scan this type" on (Q13).
 */

import { useEffect, useState } from 'react';
import {
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import type { FileTypeGroup, NewFileType } from '../../services/fileTypesApi';
import { DESC_PATTERN, EXT_PATTERN, normalizeExt } from '../../services/fileTypesApi';

interface Props {
  open: boolean;
  groups: FileTypeGroup[];
  existing: Set<string>;   // every ext already in the catalog or staged — duplicates are rejected
  onClose: () => void;
  onAdd: (t: NewFileType, scan: boolean) => void;
}

const DEFAULT_GROUP = 'others';

export function AddFileTypeDialog({ open, groups, existing, onClose, onAdd }: Props) {
  const [ext, setExt] = useState('');
  const [description, setDescription] = useState('');
  const [group, setGroup] = useState(DEFAULT_GROUP);
  const [scan, setScan] = useState(true);

  useEffect(() => {
    if (open) {
      setExt('');
      setDescription('');
      setGroup(groups.some((g) => g.id === DEFAULT_GROUP) ? DEFAULT_GROUP : groups[0]?.id ?? '');
      setScan(true);
    }
  }, [open, groups]);

  const norm = normalizeExt(ext);
  const extError = !ext
    ? ''
    : !EXT_PATTERN.test(norm)
      ? 'Letters, digits, + _ - only (up to 16 characters after the dot)'
      : existing.has(norm)
        ? `${norm} is already a known file type`
        : '';
  const descError = description && !DESC_PATTERN.test(description.trim())
    ? "Up to 60 letters, digits, spaces and ( ) + / _ ' & - (no commas or dots)"
    : '';
  const valid = !!ext && !extError && !!description.trim() && !descError && !!group;

  const submit = () => {
    if (!valid) return;
    onAdd({ ext: norm, description: description.trim(), group }, scan);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Add file type</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Extension"
            placeholder=".mxf"
            value={ext}
            onChange={(e) => setExt(e.target.value)}
            error={!!extError}
            helperText={extError || (ext ? `Will be added as ${norm}` : 'For example .mxf or braw')}
            autoFocus
            size="small"
          />
          <TextField
            label="Description"
            placeholder="MXF video"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            error={!!descError}
            helperText={descError || ' '}
            size="small"
          />
          <FormControl size="small">
            <InputLabel id="ft-group-label">Group</InputLabel>
            <Select
              labelId="ft-group-label"
              label="Group"
              value={group}
              onChange={(e) => setGroup(e.target.value)}
            >
              {groups.map((g) => (
                <MenuItem key={g.id} value={g.id}>{g.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControlLabel
            control={<Checkbox checked={scan} onChange={(e) => setScan(e.target.checked)} />}
            label="Scan this type"
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={!valid}>Add</Button>
      </DialogActions>
    </Dialog>
  );
}
