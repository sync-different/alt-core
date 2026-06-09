/**
 * DataIntegrityTab — Admin sub-tab to run data-integrity checks and view results.
 * Phase 1: Path Integrity (Super2/paths entries vs filesystem). Async: start -> poll -> result.
 * See internal/PROJECT_INTEGRITY_UI.md. Backend: integritycheck.fn.
 */
import { useState, useRef, useEffect } from 'react';
import {
  Box, Button, Typography, LinearProgress, Chip, Stack, Alert,
  Table, TableHead, TableRow, TableCell, TableBody, TableContainer, Paper, TableSortLabel,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import DownloadIcon from '@mui/icons-material/Download';
import TimerIcon from '@mui/icons-material/Timer';
import {
  startIntegrityCheck, getIntegrityStatus, getIntegrityResult,
  type IntegrityStatus, type IntegrityResult,
} from '../../services/integrityApi';

const POLL_MS = 2000;

// format the full round-trip time (ms under 1s, else seconds with 1 decimal)
function fmtElapsed(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}

// human-readable byte size (B / KB / MB / GB)
function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  const u = ['KB', 'MB', 'GB', 'TB'];
  let v = n / 1024, i = 0;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return `${v.toFixed(v < 10 ? 1 : 0)} ${u[i]}`;
}

// streaming per-category cell: green OK, or red MISSING with the parsed reason
function catCell(status?: string, reason?: string) {
  if (status === 'OK') return <Chip size="small" label="OK" color="success" variant="outlined" />;
  return (
    <Chip
      size="small"
      color="error"
      label={reason ? `MISSING (${reason})` : 'MISSING'}
    />
  );
}

// human-readable report title per check id (shown above the results)
const CHECK_TITLE: Record<string, string> = {
  paths: 'Path Integrity',
  coverage: 'Index Coverage',
  downloadbtn: 'Download Button',
  streaming: 'Streaming Artifacts',
  duplicates: 'Duplicate Files',
};

const STATUS_COLOR: Record<string, 'success' | 'error' | 'warning' | 'default'> = {
  OK: 'success',
  INDEXED: 'success',
  ORPHAN: 'error',
  MISSING: 'error',
  BUG: 'error',
  AMBIGUOUS: 'error',
  'NOT-INDEXED': 'warning',
  UNMOUNTED: 'warning',
  DELETED: 'warning',
  SKIPPED: 'default',
};

export function DataIntegrityTab() {
  const [running, setRunning] = useState(false);
  const [status, setStatus] = useState<IntegrityStatus | null>(null);
  const [result, setResult] = useState<IntegrityResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [elapsedMs, setElapsedMs] = useState<number | null>(null);  // full round-trip: click -> result
  // status filter: set of statuses to HIDE (empty = show all, the default). Toggled via filter chips.
  const [hiddenStatuses, setHiddenStatuses] = useState<Set<string>>(new Set());
  // duplicates-table sort: column + direction (default: largest size first — biggest reclaim wins)
  const [sortKey, setSortKey] = useState<'bytes' | 'copies' | 'md5'>('bytes');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');
  const pollRef = useRef<number | null>(null);
  const startTimeRef = useRef<number>(0);

  // cleanup poller on unmount
  useEffect(() => () => { if (pollRef.current) window.clearTimeout(pollRef.current); }, []);

  const runCheck = async (check: 'paths' | 'coverage' | 'downloadbtn' | 'streaming' | 'duplicates') => {
    setError(null);
    setResult(null);
    setStatus(null);
    setElapsedMs(null);
    setHiddenStatuses(new Set());   // reset filters on each run (default: show all)
    setRunning(true);
    startTimeRef.current = performance.now();   // wall-clock from the moment the button is clicked
    try {
      const jobId = await startIntegrityCheck(check);
      const poll = async () => {
        try {
          const st = await getIntegrityStatus(jobId);
          setStatus(st);
          if (st.status === 'done') {
            // all 4 checks now return ISSUES only (paths no longer sends OK rows — would be thousands
            // on a large index). The status filter chips operate on the returned issue rows.
            const res = await getIntegrityResult(jobId, false);
            setResult(res);
            setElapsedMs(Math.round(performance.now() - startTimeRef.current));  // full round-trip to result
            setRunning(false);
          } else if (st.status === 'error') {
            setError(st.error || 'check failed');
            setElapsedMs(Math.round(performance.now() - startTimeRef.current));
            setRunning(false);
          } else {
            pollRef.current = window.setTimeout(poll, POLL_MS);
          }
        } catch (e) {
          setError(e instanceof Error ? e.message : String(e));
          setRunning(false);
        }
      };
      poll();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setRunning(false);
    }
  };

  const pct = status && status.total > 0 ? Math.round((status.scanned / status.total) * 100) : 0;

  // distinct row statuses present (in a stable order), for the clickable filter chips
  const presentStatuses: string[] = (() => {
    if (!result) return [];
    const order = ['OK', 'INDEXED', 'INCOMPLETE', 'EMPTY', 'MISSING', 'ORPHAN', 'BUG', 'AMBIGUOUS', 'UNMOUNTED', 'DELETED', 'NOT-INDEXED'];
    const seen = new Set(result.rows.map((r) => r.status));
    const inOrder = order.filter((s) => seen.has(s));
    // any unexpected statuses appended
    for (const s of seen) if (!order.includes(s)) inOrder.push(s);
    return inOrder;
  })();

  // rows visible after applying the status filter (client-side only; full set stays in result.rows).
  // For duplicates there's no status filter — show all, sorted by the chosen column.
  const visibleRows = result
    ? (result.check === 'duplicates'
        ? [...result.rows].sort((a, b) => {
            let cmp = 0;
            if (sortKey === 'bytes') cmp = (a.bytes ?? 0) - (b.bytes ?? 0);
            else if (sortKey === 'copies') cmp = (a.copies ?? 0) - (b.copies ?? 0);
            else cmp = a.md5.localeCompare(b.md5);
            return sortDir === 'asc' ? cmp : -cmp;
          })
        : result.rows.filter((r) => !hiddenStatuses.has(r.status)))
    : [];

  // toggle sort: clicking the active column flips direction; a new column sorts desc first
  const sortBy = (key: 'bytes' | 'copies' | 'md5') => {
    if (sortKey === key) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else { setSortKey(key); setSortDir('desc'); }
  };

  const toggleStatus = (s: string) => {
    setHiddenStatuses((prev) => {
      const next = new Set(prev);
      if (next.has(s)) next.delete(s); else next.add(s);
      return next;
    });
  };

  // Export the VISIBLE (filtered) rows to a CSV the browser downloads — matches what's shown.
  // Columns mirror the shell scripts. Fields are quoted (paths contain spaces/commas).
  const exportCsv = () => {
    if (!result) return;
    const q = (s: string) => `"${String(s ?? '').replace(/"/g, '""')}"`;
    let lines: string[];
    if (result.check === 'streaming') {
      lines = ['status,thumbnail,hls,transcription,thumb_reason,hls_reason,transcript_reason,path,md5'];
      for (const row of visibleRows) {
        lines.push([
          q(row.status), q(row.thumbnail ?? ''), q(row.hls ?? ''), q(row.transcription ?? ''),
          q(row.thumbReason ?? ''), q(row.hlsReason ?? ''), q(row.trReason ?? ''),
          q(row.path), q(row.md5),
        ].join(','));
      }
    } else if (result.check === 'duplicates') {
      // one row PER path (matches check_duplicates.sh): md5,copies,bytes,path
      lines = ['md5,copies,bytes,path'];
      for (const row of visibleRows) {
        for (const p of row.paths ?? []) {
          lines.push([q(row.md5), q(String(row.copies ?? row.paths?.length ?? 0)), q(String(row.bytes ?? 0)), q(p)].join(','));
        }
      }
    } else {
      lines = ['status,path,md5'];
      for (const row of visibleRows) {
        lines.push([q(row.status), q(row.path), q(row.md5)].join(','));
      }
    }
    // CRLF + BOM so Excel opens it cleanly
    const csv = '﻿' + lines.join('\r\n') + '\r\n';
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    a.href = url;
    a.download = `integrity-${result.check}-${stamp}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>Data Integrity</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        <strong>Path Integrity</strong>: every indexed path (Super2/paths) exists on disk —
        ORPHAN = stale entry (file gone), UNMOUNTED = disconnected drive, DELETED/EMPTYMD5 = informational.
        <br />
        <strong>Index Coverage</strong>: every on-disk file the scanner should index actually is —
        MISSING = scanner missed it (the gap), SKIPPED = correctly excluded (bad extension or hidden).
        <br />
        <strong>Download Button</strong>: every file resolves an md5 for the Folder View download button —
        BUG = indexed but unresolvable (no button), AMBIGUOUS = same name/diff content (may serve wrong bytes),
        NOT-INDEXED = not scanned yet.
        <br />
        <strong>Streaming Artifacts</strong>: per-video thumbnail / HLS / transcription generated —
        INCOMPLETE = some output but a gap, EMPTY = no artifacts; failure reason parsed from log.txt
        (SHORT/CORRUPT/NO-AUDIO/WHISPER-FAIL).
        <br />
        <strong>Duplicate Files</strong>: one content hash (md5) that exists at 2+ live locations on disk —
        the same file stored in multiple places (DELETED entries ignored, identical paths de-duped,
        files verified to exist).
      </Typography>

      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        <Button
          variant="contained"
          startIcon={<PlayArrowIcon />}
          onClick={() => runCheck('paths')}
          disabled={running}
        >
          {running ? 'Running…' : 'Run Path Integrity'}
        </Button>
        <Button
          variant="contained"
          startIcon={<PlayArrowIcon />}
          onClick={() => runCheck('coverage')}
          disabled={running}
        >
          {running ? 'Running…' : 'Run Index Coverage'}
        </Button>
        <Button
          variant="contained"
          startIcon={<PlayArrowIcon />}
          onClick={() => runCheck('downloadbtn')}
          disabled={running}
        >
          {running ? 'Running…' : 'Run Download Button'}
        </Button>
        <Button
          variant="contained"
          startIcon={<PlayArrowIcon />}
          onClick={() => runCheck('streaming')}
          disabled={running}
        >
          {running ? 'Running…' : 'Run Streaming Artifacts'}
        </Button>
        <Button
          variant="contained"
          startIcon={<PlayArrowIcon />}
          onClick={() => runCheck('duplicates')}
          disabled={running}
        >
          {running ? 'Running…' : 'Run Duplicate Files'}
        </Button>
      </Stack>

      {running && status && (
        <Box sx={{ mb: 2 }}>
          <LinearProgress variant={status.total > 0 ? 'determinate' : 'indeterminate'} value={pct} />
          <Typography variant="caption" color="text.secondary">
            {status.check === 'coverage'
              ? `Scanned ${status.scanned} files…`
              : `Scanned ${status.scanned} / ${status.total} md5 files (${pct}%)`}
          </Typography>
        </Box>
      )}

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {result && (
        <>
          <Typography variant="subtitle1" color="text.primary" sx={{ fontWeight: 600, mb: 1 }}>
            {CHECK_TITLE[result.check] ?? result.check} — Results
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap alignItems="center">
            {result.check === 'coverage' ? (
              <>
                <Chip label={`INDEXED: ${result.indexed ?? 0}`} color="success" variant="outlined" />
                <Chip label={`MISSING: ${result.missing ?? 0}`} color={(result.missing ?? 0) > 0 ? 'error' : 'default'} />
                <Chip label={`SKIPPED: ${result.skipped ?? 0}`} variant="outlined" />
                <Chip label={`index-only: ${result.indexOnly ?? 0}`} color={(result.indexOnly ?? 0) > 0 ? 'warning' : 'default'} variant="outlined" />
                <Chip label={`${result.filesOnDisk ?? 0} files on disk`} variant="outlined" />
              </>
            ) : result.check === 'downloadbtn' ? (
              <>
                <Chip label={`OK: ${result.dlOk ?? 0}`} color="success" variant="outlined" />
                <Chip label={`BUG: ${result.dlBug ?? 0}`} color={(result.dlBug ?? 0) > 0 ? 'error' : 'default'} />
                <Chip label={`AMBIGUOUS: ${result.dlAmbiguous ?? 0}`} color={(result.dlAmbiguous ?? 0) > 0 ? 'error' : 'default'} variant="outlined" />
                <Chip label={`NOT-INDEXED: ${result.dlNotIndexed ?? 0}`} color={(result.dlNotIndexed ?? 0) > 0 ? 'warning' : 'default'} variant="outlined" />
                <Chip label={`${result.filesOnDisk ?? 0} files checked`} variant="outlined" />
              </>
            ) : result.check === 'streaming' ? (
              <>
                <Chip label={`THUMB ${result.thumbOk ?? 0} ok / ${result.thumbMiss ?? 0} miss`} color={(result.thumbMiss ?? 0) > 0 ? 'warning' : 'success'} variant="outlined" />
                <Chip label={`HLS ${result.hlsOk ?? 0} ok / ${result.hlsMiss ?? 0} miss`} color={(result.hlsMiss ?? 0) > 0 ? 'warning' : 'success'} variant="outlined" />
                <Chip label={`TRANSCRIPT ${result.trOk ?? 0} ok / ${result.trMiss ?? 0} miss`} color={(result.trMiss ?? 0) > 0 ? 'warning' : 'success'} variant="outlined" />
                <Chip label={`INCOMPLETE: ${result.incomplete ?? 0}`} color={(result.incomplete ?? 0) > 0 ? 'error' : 'default'} />
                <Chip label={`EMPTY: ${result.empty ?? 0}`} variant="outlined" />
                <Chip label={`${result.folders ?? 0} folders`} variant="outlined" />
              </>
            ) : result.check === 'duplicates' ? (
              <>
                <Chip label={`DUPLICATE md5: ${result.dupMd5 ?? 0}`} color={(result.dupMd5 ?? 0) > 0 ? 'error' : 'success'} />
                <Chip label={`total copies: ${result.dupCopies ?? 0}`} variant="outlined" />
                <Chip label={`redundant copies: ${(result.dupCopies ?? 0) - (result.dupMd5 ?? 0)}`} color={(result.dupCopies ?? 0) - (result.dupMd5 ?? 0) > 0 ? 'warning' : 'default'} variant="outlined" />
                <Chip label={`reclaimable: ${fmtBytes(result.dupRedundantBytes ?? 0)}`} color={(result.dupRedundantBytes ?? 0) > 0 ? 'warning' : 'default'} />
                <Chip label={`${result.dupMd5Files ?? 0} md5 files`} variant="outlined" />
                {(result.dupUnverifiable ?? 0) > 0 && (
                  <Chip label={`unverifiable: ${result.dupUnverifiable}`} color="warning" variant="outlined" />
                )}
              </>
            ) : (
              <>
                <Chip label={`OK: ${result.ok ?? 0}`} color="success" variant="outlined" />
                <Chip label={`ORPHAN: ${result.orphan ?? 0}`} color={(result.orphan ?? 0) > 0 ? 'error' : 'default'} />
                <Chip label={`UNMOUNTED: ${result.unmounted ?? 0}`} color={(result.unmounted ?? 0) > 0 ? 'warning' : 'default'} variant="outlined" />
                <Chip label={`DELETED: ${result.deleted ?? 0}`} variant="outlined" />
                <Chip label={`EMPTYMD5: ${result.emptymd5 ?? 0}`} variant="outlined" />
                <Chip label={`${result.pathEntries ?? 0} entries / ${result.md5Files ?? 0} files`} variant="outlined" />
              </>
            )}
            {elapsedMs !== null && (
              <Chip
                size="small"
                color="info"
                variant="outlined"
                icon={<TimerIcon />}
                label={`ran in ${fmtElapsed(elapsedMs)}`}
              />
            )}
            <Box sx={{ flexGrow: 1 }} />
            <Button
              size="small"
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={exportCsv}
              disabled={visibleRows.length === 0}
            >
              Export CSV
            </Button>
          </Stack>

          {/* clickable status filters — all enabled by default; click to hide that status.
              (duplicates rows have no per-status verdict, so no filter for that check) */}
          {result.check !== 'duplicates' && presentStatuses.length >= 1 && (
            <Stack direction="row" spacing={1} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap alignItems="center">
              <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5 }}>Filter:</Typography>
              {presentStatuses.map((s) => {
                const active = !hiddenStatuses.has(s);
                const count = result.rows.filter((r) => r.status === s).length;
                return (
                  <Chip
                    key={s}
                    size="small"
                    label={`${s} (${count})`}
                    color={active ? (STATUS_COLOR[s] || 'default') : 'default'}
                    variant={active ? 'filled' : 'outlined'}
                    onClick={() => toggleStatus(s)}
                    sx={{ opacity: active ? 1 : 0.5, cursor: 'pointer' }}
                  />
                );
              })}
            </Stack>
          )}

          {result.check !== 'coverage' && (result.unmountedVols?.length ?? 0) > 0 && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Disconnected volume(s): {result.unmountedVols!.join(', ')}. Reconnect and re-run to verify those entries.
            </Alert>
          )}

          {result.check === 'coverage' && (result.indexOnly ?? 0) > 0 && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {result.indexOnly} indexed path(s) aren't under a mounted scan root (disconnected drive or a
              root removed from scan config) — not a coverage gap. Run Path Integrity to see them.
            </Alert>
          )}

          {(result.missingRoots?.length ?? 0) > 0 && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Scan root(s) not mounted/found (skipped): {result.missingRoots!.join(', ')}.
            </Alert>
          )}

          {result.check === 'streaming' && (result.trReasons?.['WHISPER-FAIL'] ?? 0) > 0 && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {result.trReasons!['WHISPER-FAIL']} video(s) had audio but transcription failed
              (real LocalAI/whisper failure — hang/timeout/empty reply). Investigate whisper.
            </Alert>
          )}

          {result.rows.length === 0 ? (
            <Alert severity="success">
              {result.check === 'coverage'
                ? 'No coverage gaps — every file the scanner should index is indexed.'
                : result.check === 'downloadbtn'
                ? 'No issues — every file resolves a download-button md5.'
                : result.check === 'streaming'
                ? 'No streaming gaps — every video has thumbnail, HLS, and transcription.'
                : result.check === 'duplicates'
                ? 'No duplicates — every content hash exists at a single location.'
                : 'No issues found — every indexed path exists on disk.'}
            </Alert>
          ) : visibleRows.length === 0 ? (
            <Alert severity="info">All {result.rows.length} rows hidden by the status filter — re-enable a chip above to show rows.</Alert>
          ) : result.check === 'duplicates' ? (
            <TableContainer component={Paper} variant="outlined">
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ width: 90 }} sortDirection={sortKey === 'copies' ? sortDir : false}>
                      <TableSortLabel active={sortKey === 'copies'} direction={sortKey === 'copies' ? sortDir : 'desc'} onClick={() => sortBy('copies')}>
                        Copies
                      </TableSortLabel>
                    </TableCell>
                    <TableCell sx={{ width: 110 }} sortDirection={sortKey === 'bytes' ? sortDir : false}>
                      <TableSortLabel active={sortKey === 'bytes'} direction={sortKey === 'bytes' ? sortDir : 'desc'} onClick={() => sortBy('bytes')}>
                        Size
                      </TableSortLabel>
                    </TableCell>
                    <TableCell sortDirection={sortKey === 'md5' ? sortDir : false}>
                      <TableSortLabel active={sortKey === 'md5'} direction={sortKey === 'md5' ? sortDir : 'desc'} onClick={() => sortBy('md5')}>
                        MD5
                      </TableSortLabel>
                    </TableCell>
                    <TableCell>Paths (same content, multiple locations)</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {visibleRows.map((row, i) => (
                    <TableRow key={`${row.md5}-${i}`} hover>
                      <TableCell>
                        <Chip size="small" label={`×${row.copies ?? row.paths?.length ?? 0}`} color="error" />
                      </TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.78rem', whiteSpace: 'nowrap' }}>
                        {fmtBytes(row.bytes ?? 0)}
                      </TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>{row.md5}</TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.78rem' }}>
                        {(row.paths ?? []).map((p, j) => (
                          <div key={j} style={{ wordBreak: 'break-all' }}>{p}</div>
                        ))}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : result.check === 'streaming' ? (
            <TableContainer component={Paper} variant="outlined">
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Status</TableCell>
                    <TableCell>Thumbnail</TableCell>
                    <TableCell>HLS</TableCell>
                    <TableCell>Transcription</TableCell>
                    <TableCell>Path</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {visibleRows.map((row, i) => (
                    <TableRow key={`${row.md5}-${i}`} hover>
                      <TableCell>
                        <Chip size="small" label={row.status} color={row.status === 'EMPTY' ? 'warning' : 'error'} />
                      </TableCell>
                      <TableCell>{catCell(row.thumbnail, row.thumbReason)}</TableCell>
                      <TableCell>{catCell(row.hls, row.hlsReason)}</TableCell>
                      <TableCell>{catCell(row.transcription, row.trReason)}</TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.78rem', wordBreak: 'break-all' }}>
                        {row.path}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <TableContainer component={Paper} variant="outlined">
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Status</TableCell>
                    <TableCell>Path</TableCell>
                    <TableCell>MD5</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {visibleRows.map((row, i) => (
                    <TableRow key={`${row.md5}-${i}`} hover>
                      <TableCell>
                        <Chip size="small" label={row.status} color={STATUS_COLOR[row.status] || 'default'} />
                      </TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem', wordBreak: 'break-all' }}>
                        {row.path}
                      </TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>{row.md5}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </>
      )}
    </Box>
  );
}
