/**
 * integrityApi — client for the admin data-integrity checks (integritycheck.fn).
 * Async model: start -> poll status -> fetch result. Mirrors the gettranscribestatus polling
 * pattern. Backend logic ports the test-data/ shell scripts; see internal/PROJECT_INTEGRITY_UI.md.
 *
 * Phase 1: check="paths" (path-integrity — Super2/paths entries vs filesystem).
 */
import api from './api';

export type IntegrityCheckId = 'paths' | 'coverage' | 'downloadbtn' | 'streaming' | 'duplicates';

export interface IntegrityStatus {
  status: 'running' | 'done' | 'error';
  check: string;
  scanned: number;
  total: number;
  // paths-check counts
  ok?: number;
  orphan?: number;
  unmounted?: number;
  deleted?: number;
  emptymd5?: number;
  // coverage-check counts
  indexed?: number;
  missing?: number;
  skipped?: number;
  // downloadbtn-check counts
  dlOk?: number;
  dlNotIndexed?: number;
  dlBug?: number;
  dlAmbiguous?: number;
  // duplicates-check counts
  dupMd5?: number;
  dupCopies?: number;
  error?: string;
}

export interface IntegrityRow {
  md5: string;
  path: string;
  status: string; // paths: OK|ORPHAN|UNMOUNTED|DELETED  |  coverage: MISSING  |  downloadbtn: BUG|AMBIGUOUS|NOT-INDEXED
  // streaming-only per-category fields
  thumbnail?: string;
  hls?: string;
  transcription?: string;
  thumbReason?: string;
  hlsReason?: string;
  trReason?: string;
  // duplicates-only fields (one row per duplicate md5)
  copies?: number;
  bytes?: number;   // size of one copy
  paths?: string[];
}

export interface IntegrityResult {
  check: string;
  rows: IntegrityRow[];
  // paths-check summary
  md5Files?: number;
  pathEntries?: number;
  ok?: number;
  orphan?: number;
  unmounted?: number;
  deleted?: number;
  emptymd5?: number;
  unmountedVols?: string[];
  // coverage-check summary
  filesOnDisk?: number;
  indexedPathSet?: number;
  indexed?: number;
  missing?: number;
  skipped?: number;
  indexOnly?: number;
  scanRoots?: string[];
  missingRoots?: string[];
  // downloadbtn-check summary
  dlOk?: number;
  dlNotIndexed?: number;
  dlBug?: number;
  dlAmbiguous?: number;
  // duplicates-check summary
  dupMd5Files?: number;
  dupMd5?: number;
  dupCopies?: number;
  dupUnverifiable?: number;
  dupRedundantBytes?: number;   // total reclaimable bytes = sum of size*(copies-1)
  // streaming-check summary
  folders?: number;
  thumbOk?: number;
  thumbMiss?: number;
  hlsOk?: number;
  hlsMiss?: number;
  trOk?: number;
  trMiss?: number;
  empty?: number;
  incomplete?: number;
  thumbReasons?: Record<string, number>;
  hlsReasons?: Record<string, number>;
  trReasons?: Record<string, number>;
}

// cache-buster: every request gets a unique value so the browser can't serve a cached GET.
// Without this, `?check=paths&action=start` is a byte-identical URL each time → the browser may
// return the PREVIOUS jobId/response and the report looks "cached" instead of re-running. (The
// server doesn't send Cache-Control: no-cache on these .fn responses.)
let _seq = 0;
function bust(): string { return `${performance.now().toFixed(0)}-${_seq++}`; }

/** Start a check; returns the jobId to poll. */
export async function startIntegrityCheck(check: IntegrityCheckId): Promise<string> {
  const res = await api.get('/cass/integritycheck.fn', { params: { check, action: 'start', _t: bust() } });
  if (res.data?.error) throw new Error(res.data.error);
  return res.data.jobId as string;
}

/** Poll a running job's status + live counts. */
export async function getIntegrityStatus(jobId: string): Promise<IntegrityStatus> {
  const res = await api.get('/cass/integritycheck.fn', { params: { action: 'status', jobid: jobId, _t: bust() } });
  if (res.data?.error) throw new Error(res.data.error);
  return res.data as IntegrityStatus;
}

/** Fetch the full result once status==done. issuesOnly drops OK rows. */
export async function getIntegrityResult(jobId: string, issuesOnly = false): Promise<IntegrityResult> {
  const params: Record<string, string> = { action: 'result', jobid: jobId, _t: bust() };
  if (issuesOnly) params.check = 'issues'; // backend treats check=issues|1 as issues-only on result
  const res = await api.get('/cass/integritycheck.fn', { params });
  if (res.data?.error) throw new Error(res.data.error);
  return res.data as IntegrityResult;
}
