/**
 * fileTypesApi — admin "File types" tab: which file types the Scrubber indexes.
 * Backend: getfiletypes-json.fn / setfiletypes-json.fn (utils.FileTypesConfig).
 * See internal/PROJECT_TAB_ADMIN_FILETYPES.md.
 *
 * Both calls use baseURL '' (same origin). The save sends the custom "X-Alt-Request" header (the
 * server's CSRF guard), which would trigger a CORS preflight against http://localhost:8081 in
 * `npm run dev`, and the server doesn't answer OPTIONS. Same-origin through the Vite /cass proxy
 * avoids the preflight; in production baseURL is '' anyway.
 */
import api from './api';

export interface FileTypeEntry {
  ext: string;          // ".doc"
  description: string;
  selected: boolean;    // indexed by the Scrubber
  custom: boolean;      // admin-added (only these can be removed)
}

export interface FileTypeGroup {
  id: string;           // "office", "video", ... "others"
  label: string;        // "Documents", "Video Files", ... "Other"
  types: FileTypeEntry[];
}

export interface FileTypesConfig {
  version: string;
  groups: FileTypeGroup[];
  orphans: string[];    // selected but not in the catalog (kept by the server, not shown as rows)
  warnings: string[];
}

export interface NewFileType {
  ext: string;
  description: string;
  group: string;
}

export interface SaveFileTypesResult extends FileTypesConfig {
  selectedAdded: string[];
  selectedRemoved: string[];
  catalogAdded: string[];
  catalogRemoved: string[];
}

/** A rejected request. status: 400 invalid, 409 changed by someone else (version = current), 500 server. */
export class FileTypesError extends Error {
  status: number;
  version?: string;
  constructor(message: string, status: number, version?: string) {
    super(message);
    this.status = status;
    this.version = version;
  }
}

// Mirrors the server's validation (FileTypesConfig.EXT_RE / DESC_RE) for live feedback in the dialog.
export const EXT_PATTERN = /^\.[a-z0-9][a-z0-9+_-]{0,15}$/;
export const DESC_PATTERN = /^[A-Za-z0-9 ()+/_'&-]{1,60}$/;

export function normalizeExt(raw: string): string {
  const s = raw.trim().toLowerCase();
  return s && !s.startsWith('.') ? `.${s}` : s;
}

type Raw = Record<string, unknown>;

function check(data: unknown): Raw {
  const d = (typeof data === 'string' ? JSON.parse(data) : data) as Raw;
  if (!d || d.success !== true) {
    throw new FileTypesError(
      String(d?.error ?? 'Request failed'),
      Number(d?.status ?? 403),
      d?.version as string | undefined,
    );
  }
  return d;
}

export async function getFileTypes(): Promise<FileTypesConfig> {
  const res = await api.get('/cass/getfiletypes-json.fn', {
    baseURL: '',
    params: { _t: Date.now() },
  });
  return check(res.data) as unknown as FileTypesConfig;
}

export async function saveFileTypes(req: {
  selected: string[];
  add: NewFileType[];
  remove: string[];
  version: string;
}): Promise<SaveFileTypesResult> {
  const res = await api.get('/cass/setfiletypes-json.fn', {
    baseURL: '',
    params: { ftpayload: JSON.stringify(req), _t: Date.now() },
    headers: { 'X-Alt-Request': '1' },
  });
  return check(res.data) as unknown as SaveFileTypesResult;
}
