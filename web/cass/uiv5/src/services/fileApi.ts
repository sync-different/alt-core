import api from './api';
import type { File } from '../types/models';
import type { Tag } from '../store/slices/tagsSlice';
import type { SidebarStats } from '../store/slices/sidebarSlice';
import { buildUrl } from '../utils/urlHelper';

export interface FetchFilesParams {
  ftype?: string;
  days?: number;
  foo?: string;
  numobj?: number;
  date?: number;
  order?: 'Asc' | 'Desc';
  screenSize?: number;
}

export interface FetchFilesResponse {
  files: File[];
  hasMore: boolean;
}

export interface FetchSidebarStatsParams {
  ftype?: string;
  foo?: string;
  days?: number;
}

export interface DeviceInfo {
  computerName: string;
  type: 'server' | 'client' | 'cloud';
  ip: string;
  port: number;
  indexProgress: number;
  backupProgress: number;
  diskUsage: number;
  lastSeen: number;
  nodeId: string;
}

export interface Folder {
  name: string;
  path?: string;
  count?: number;
  type?: 'file' | 'folder';
  md5?: string;
  size?: number; // BE1: file size in bytes (files only; from getfolders-json.fn). 0/undefined for folders.
}

/**
 * Fetch files from the backend with filters and pagination
 */
export const fetchFiles = async (params: FetchFilesParams): Promise<FetchFilesResponse> => {
  const {
    ftype = '.all',
    days = 0,
    foo = '',
    numobj = 100,
    date,
    order = 'Desc',
    screenSize = 160,
  } = params;

  const queryParams: any = {
    ftype,
    foo: foo || '.all',  // Default to '.all' when empty (matches AngularJS behavior)
    days: days > 0 ? days : '',  // Send empty string when days is 0 (matches AngularJS)
    view: 'json',
    numobj,
    order,
    screenSize,
  };

  if (date !== undefined && date !== null) {
    console.log('Converting date:', { rawDate: date, type: typeof date });

    // Convert Unix timestamp (milliseconds) to backend date format: YYYY.MM.DD+HH:MM:SS.mmm+TIMEZONE
    const dateObj = new Date(Number(date));

    console.log('Date object:', dateObj, 'isValid:', !isNaN(dateObj.getTime()));

    if (!isNaN(dateObj.getTime())) {
      const year = dateObj.getFullYear();
      const month = String(dateObj.getMonth() + 1).padStart(2, '0');
      const day = String(dateObj.getDate()).padStart(2, '0');
      const hours = String(dateObj.getHours()).padStart(2, '0');
      const minutes = String(dateObj.getMinutes()).padStart(2, '0');
      const seconds = String(dateObj.getSeconds()).padStart(2, '0');
      const milliseconds = String(dateObj.getMilliseconds()).padStart(3, '0');

      // Get timezone abbreviation (e.g., "PST", "PDT", "EST")
      const timeZoneStr = dateObj.toLocaleTimeString('en-US', { timeZoneName: 'short' });
      const timeZone = timeZoneStr.split(' ').pop() || 'GMT';

      queryParams.date = `${year}.${month}.${day}+${hours}:${minutes}:${seconds}.${milliseconds}+${timeZone}`;
      console.log('Formatted date:', queryParams.date);
    } else {
      console.error('Invalid date value:', date);
    }
  }

  console.log('Fetching files with params:', queryParams);

  const response = await api.get('/cass/query.fn', { params: queryParams });

  // The backend returns an object with 'fighters' array (AngularJS: data.fighters)
  const files: File[] = Array.isArray(response.data?.fighters)
    ? response.data.fighters
    : [];

  return {
    files,
    hasMore: files.length >= numobj,
  };
};

/**
 * Fetch sidebar statistics for file types and time ranges
 */
export const fetchSidebarStats = async (params: FetchSidebarStatsParams): Promise<SidebarStats> => {
  const { ftype = '.all', foo = '', days = 0 } = params;

  const queryParams: any = {
    ftype,
    days,
  };

  if (foo) {
    queryParams.foo = foo;
  }

  const response = await api.get('/cass/sidebar.fn', { params: queryParams });

  // Backend returns counts in objFound array with two objects:
  // objFound[0] = file type counts (nTotal, nPhoto, nMusic, etc.)
  // objFound[1] = time range counts (nPast24h, nPast3d, etc.)
  const data = response.data || {};

  console.log('sidebar.fn response:', data);

  const fileTypeData = data.objFound?.[0] || {};
  const timeRangeData = data.objFound?.[1] || {};

  console.log('File type data:', fileTypeData);
  console.log('Time range data:', timeRangeData);

  return {
    fileTypes: [
      { ftype: '.all', count: parseInt(fileTypeData.nTotal || 0) },
      { ftype: '.photo', count: parseInt(fileTypeData.nPhoto || 0) },
      { ftype: '.music', count: parseInt(fileTypeData.nMusic || 0) },
      { ftype: '.video', count: parseInt(fileTypeData.nVideo || 0) },
      { ftype: '.document', count: parseInt(fileTypeData.nDocuments || 0) },
      { ftype: '.doc', count: parseInt(fileTypeData.nDoc || 0) },
      { ftype: '.xls', count: parseInt(fileTypeData.nXls || 0) },
      { ftype: '.ppt', count: parseInt(fileTypeData.nPpt || 0) },
      { ftype: '.pdf', count: parseInt(fileTypeData.nPdf || 0) },
    ],
    timeRanges: [
      { range: '1', count: parseInt(timeRangeData.nPast24h || 0) },
      { range: '3', count: parseInt(timeRangeData.nPast3d || 0) },
      { range: '7', count: parseInt(timeRangeData.nPast7d || 0) },
      { range: '14', count: parseInt(timeRangeData.nPast14d || 0) },
      { range: '30', count: parseInt(timeRangeData.nPast30d || 0) },
      { range: '365', count: parseInt(timeRangeData.nPast365d || 0) },
      { range: '.all', count: parseInt(timeRangeData.nAllTime || 0) },
    ],
  };
};

/**
 * Fetch search suggestions based on query
 */
export const fetchSearchSuggestions = async (
  query: string,
  params?: FetchFilesParams
): Promise<string[]> => {
  if (!query || query.length < 2) {
    return [];
  }

  const { ftype = '.all', days = 0 } = params || {};

  const queryParams: any = {
    foo: query,
    ftype,
    days: days > 0 ? days : '',
    view: 'json',
    numobj: 10,
  };

  const response = await api.get('/cass/suggest.fn', { params: queryParams });

  // Backend returns { fighters: [...] } where each item can be a string or object with 'name' property
  // e.g., {"name":"demo-raj2","type":"tag"} or just "filename.txt"
  const fighters = response.data?.fighters || [];
  return Array.isArray(fighters)
    ? fighters.map((item: any) => typeof item === 'string' ? item : item.name || '')
    : [];
};

/**
 * Fetch tags for a file or all tags
 */
export const fetchTags = async (multiclusterid?: string): Promise<Tag[]> => {
  const queryParams: any = {};

  if (multiclusterid) {
    queryParams.multiclusterid = multiclusterid;
  }

  const response = await api.get('/cass/gettags_webapp.fn', { params: queryParams });

  // Backend returns object with 'fighters' array (same as old AngularJS app)
  // Each tag object has 'tagname' and 'tagcnt' properties
  const tags = Array.isArray(response.data?.fighters) ? response.data.fighters : [];

  return tags.map((tag: any) => ({
    tag: tag.tagname || tag.tag || '',
    count: parseInt(tag.tagcnt || tag.count || '0', 10),
  }));
};

/**
 * Fetch user session info including admin status
 * Returns username and isAdmin flag from the backend
 */
export const fetchUserSessionInfo = async (): Promise<{ username: string; isAdmin: boolean }> => {
  const response = await api.get('/cass/gettags_webapp.fn');

  return {
    username: response.data?.username || '',
    isAdmin: response.data?.isAdmin === 'true' || response.data?.isAdmin === true,
  };
};

/**
 * Add tags to files
 */
export const addTags = async (fileIds: string[], tags: string[]): Promise<void> => {
  // Backend expects: /cass/applytags.fn?tag=tagname&md5_1=on&md5_2=on&...
  const tagsStr = tags.join(',');

  // Build query parameters with each file MD5 as a separate param with value "on"
  const params: any = {
    tag: tagsStr,
    _: Date.now(), // Cache buster
  };

  // Add each file ID as a query parameter with value "on"
  fileIds.forEach(id => {
    params[id] = 'on';
  });

  await api.get('/cass/applytags.fn', { params });
};

/**
 * Remove tags from files
 * Uses applytags.fn with DeleteTag parameter (same endpoint as adding tags)
 */
export const removeTags = async (fileIds: string[], tags: string[]): Promise<void> => {
  const tagsStr = tags.join(',');

  // For removing tags, use applytags.fn with DeleteTag parameter
  // Format: /cass/applytags.fn?tag=tagname&DeleteTag=md5
  const params: any = {
    tag: tagsStr,
    _: Date.now(), // Cache buster
  };

  // Add each file ID as DeleteTag parameter
  fileIds.forEach(id => {
    params.DeleteTag = id; // Note: Only one file at a time for removal
  });

  await api.get('/cass/applytags.fn', { params });
};

/**
 * Get download URL for a file
 * Uses getfile.fn endpoint (same as old AngularJS app)
 */
export const getDownloadUrl = (multiclusterid: string): string => {
  const params = new URLSearchParams({
    multiclusterid,
  });

  // Add UUID from localStorage
  const uuid = localStorage.getItem('uuid');
  if (uuid) {
    params.append('uuid', uuid);
  }

  return buildUrl(`/cass/getfile.fn?${params.toString()}`);
};

/**
 * Download a file by triggering browser download
 * Uses blob approach to force download instead of opening in browser
 *
 * @param filePathOrHash - Either the full file_path_webapp URL or the md5 hash
 * @param filename - Optional filename for download
 */
export const downloadFile = (filePathOrHash: string, filename?: string): void => {
  const uuid = localStorage.getItem('uuid');

  console.log('downloadFile called with:', { filePathOrHash, filename, uuid });

  // Determine if we have a full path or just a hash
  let url: string;
  if (filePathOrHash.startsWith('/cass/') || filePathOrHash.includes('getfile.fn')) {
    // It's a full path
    const separator = filePathOrHash.includes('?') ? '&' : '?';
    const path = filePathOrHash.startsWith('/') ? filePathOrHash : `/${filePathOrHash}`;
    url = buildUrl(`${path}${separator}uuid=${uuid}`);
  } else {
    // It's just a hash (multiclusterid)
    url = buildUrl(`/cass/getfile.fn?multiclusterid=${filePathOrHash}&uuid=${uuid}`);
  }

  console.log('Download URL:', url);

  // Open in new tab - browser will download if server sends Content-Disposition header
  window.open(url, '_blank');
};

/**
 * Download multiple files as a ZIP
 */
export const downloadMultipleFiles = async (fileIds: string[]): Promise<void> => {
  const idsStr = fileIds.join(',');
  const params = new URLSearchParams({
    multiclusterid: idsStr,
  });

  // Add UUID from localStorage
  const uuid = localStorage.getItem('uuid');
  if (uuid) {
    params.append('uuid', uuid);
  }

  const url = buildUrl(`/cass/downloadmulti.fn?${params.toString()}`);

  const link = document.createElement('a');
  link.href = url;
  link.download = 'files.zip';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

/**
 * Fetch device/node information
 */
export const fetchDeviceInfo = async (): Promise<DeviceInfo[]> => {
  const response = await api.get('/cass/nodeinfo.fn');

  // Backend returns array of node/device objects
  const nodes = Array.isArray(response.data) ? response.data : [];

  return nodes.map((node: any) => ({
    computerName: node.computerName || node.name || 'Unknown',
    type: node.type || 'client',
    ip: node.ip || node.ipAddress || '',
    port: node.port || 0,
    indexProgress: node.indexProgress || 0,
    backupProgress: node.backupProgress || 0,
    diskUsage: node.diskUsage || 0,
    lastSeen: node.lastSeen || Date.now(),
    nodeId: node.nodeId || node.id || '',
  }));
};

/**
 * Fetch remote folders
 */
export const fetchFolders = async (sFolder: string = 'scanfolders', options?: { browse?: boolean }): Promise<Folder[]> => {
  const params: Record<string, string> = { sFolder };
  if (options?.browse) {
    params.bFolderSel = 'browse';
  }
  const response = await api.get('/cass/getfolders-json.fn', {
    params,
    // Request as text to handle backend JSON with unescaped backslashes in filenames.
    // Backend may write raw filenames like "..\rtserver\id_sec" into JSON without escaping,
    // producing invalid sequences like \r (ambiguous with carriage return).
    // Fix: escape all backslashes between JSON string delimiters before parsing.
    responseType: 'text',
    transformResponse: [(data: string) => {
      try {
        return JSON.parse(data);
      } catch {
        try {
          // Process character by character to properly escape backslashes inside strings
          let result = '';
          let inString = false;
          for (let i = 0; i < data.length; i++) {
            const ch = data[i];
            if (ch === '"' && (i === 0 || data[i - 1] !== '\\')) {
              inString = !inString;
              result += ch;
            } else if (inString && ch === '\\') {
              // Always double the backslash inside strings
              result += '\\\\';
            } else {
              result += ch;
            }
          }
          return JSON.parse(result);
        } catch {
          return [];
        }
      }
    }],
  });

  // Backend returns array of folder names or objects with folder info
  const folders = Array.isArray(response.data) ? response.data : response.data?.folders || [];

  // Helper to safely decode URI-encoded strings (backend may return encoded names)
  const safeDecode = (str: string): string => {
    try {
      return decodeURIComponent(str);
    } catch {
      return str;
    }
  };

  return folders
    .map((folder: any) => ({
      name: safeDecode(typeof folder === 'string' ? folder : folder.name || folder.folder || ''),
      path: typeof folder === 'object' && folder.path ? safeDecode(folder.path) : undefined,
      count: typeof folder === 'object' ? folder.count : undefined,
      type: typeof folder === 'object' ? folder.type : undefined,
      md5: typeof folder === 'object' ? folder.md5 : undefined,
      size: typeof folder === 'object' && folder.size != null ? Number(folder.size) : undefined,
    }))
    .filter((folder: Folder) => !folder.name.startsWith('.')); // Filter out hidden files/folders
};

/**
 * A single file discovered by a recursive folder enumeration (FF3, PROJECT_FOLDER_DOWNLOAD).
 * `relativePath` is the path from the *selected* folder down to the file (POSIX '/'-separated),
 * e.g. selecting "folder1" containing "sub/image1.jpg" yields "folder1/sub/image1.jpg". The client
 * recreates these subdirectories under the chosen destination directory when writing.
 */
export interface EnumeratedFile {
  md5: string;
  name: string;        // bare filename
  size: number;        // bytes (from BE1 — no per-file getfileinfo.fn needed)
  relativePath: string; // <selectedFolderName>/.../<name>
}

export interface EnumerateResult {
  files: EnumeratedFile[];
  skippedFolders: string[]; // subfolder paths whose listing failed (logged + skipped, not fatal)
  truncated: boolean;       // hit the depth cap (symlink-loop / runaway guard)
}

/**
 * FF3 — recursively enumerate all files under a folder, preserving relative paths.
 *
 * Frontend recursion (v1): one `getfolders-json.fn` call per folder NODE (not per file). File size
 * comes from the listing (BE1), so NO per-file `getfileinfo.fn` calls. A subfolder whose listing
 * fails is logged + skipped (the rest continue) — never fatal. Recursion depth is capped to guard
 * against symlink loops / runaway trees.
 *
 * @param absPath   the folder path as `getfolders-json.fn` expects it (what FoldersPage navigates to)
 * @param baseRel   the relative-path prefix to prepend (usually the selected folder's display name)
 * @param onProgress optional callback fired as files are discovered (for a "Scanning… N found" UI)
 */
export const enumerateFolder = async (
  absPath: string,
  baseRel: string,
  onProgress?: (found: number) => void,
): Promise<EnumerateResult> => {
  const MAX_DEPTH = 32; // symlink-loop / runaway guard
  const files: EnumeratedFile[] = [];
  const skippedFolders: string[] = [];
  let truncated = false;

  const walk = async (curAbs: string, curRel: string, depth: number): Promise<void> => {
    if (depth > MAX_DEPTH) { truncated = true; return; }
    let entries: Folder[];
    try {
      entries = await fetchFolders(curAbs);
    } catch (e) {
      console.warn('[enumerateFolder] failed to list, skipping:', curAbs, e);
      skippedFolders.push(curAbs);
      return;
    }
    // process files first, then descend into subfolders
    for (const entry of entries) {
      if (entry.type === 'file' && entry.md5) {
        files.push({
          md5: entry.md5,
          name: entry.name,
          size: entry.size ?? 0,
          relativePath: `${curRel}/${entry.name}`,
        });
      }
    }
    if (onProgress) onProgress(files.length);
    for (const entry of entries) {
      if (entry.type === 'folder') {
        await walk(`${curAbs}/${entry.name}`, `${curRel}/${entry.name}`, depth + 1);
      }
    }
  };

  await walk(absPath, baseRel, 0);
  return { files, skippedFolders, truncated };
};

/**
 * Save scan folder paths to backend (admin only)
 * Writes URL-encoded semicolon-separated paths to scan1.txt via setfolder-json.fn
 */
export const setScanFolders = async (folders: string[]): Promise<{ success: boolean; error?: string }> => {
  // URL-encode each path and join with semicolons
  const encoded = folders
    .map((f) => encodeURIComponent(f))
    .join(';');
  const response = await api.get('/cass/setfolder-json.fn', {
    params: { sFolder: encoded }
  });
  return response.data;
};

/**
 * Fetch available volumes/drives (for folder browser)
 * Returns raw volume paths from getfolders-json.fn?sFolder=units
 */
export const fetchVolumes = async (): Promise<string[]> => {
  const response = await api.get('/cass/getfolders-json.fn', {
    params: { sFolder: 'units' }
  });
  return Array.isArray(response.data) ? response.data : [];
};

/**
 * File info response from getfileinfo.fn
 */
export interface FileInfo {
  nickname: string;
  name: string;
  file_ext: string;
  file_group: string;
  file_size: number;
  file_date: string;
  file_date_long: number;
  file_tags: string;
  file_path_webapp: string;
  file_remote_webapp: string;
  file_folder_webapp: string;
  file_thumbnail?: string;
  video_url_webapp?: string;
  audio_url_webapp?: string;
  error?: string;
}

/**
 * Fetch file info by MD5 hash
 * Uses getfileinfo.fn endpoint
 */
export const fetchFileInfo = async (md5: string): Promise<FileInfo> => {
  const response = await api.get('/cass/getfileinfo.fn', {
    params: { md5 }
  });

  const data = response.data;

  // Check for error response
  if (data.error) {
    throw new Error(data.error);
  }

  // Decode URL-encoded filename
  let decodedName = data.name || '';
  try {
    decodedName = decodeURIComponent(decodedName);
  } catch {
    // Keep original if decoding fails
  }

  return {
    nickname: data.nickname || md5,
    name: decodedName,
    file_ext: data.file_ext || '',
    file_group: data.file_group || 'other',
    file_size: parseInt(data.file_size || '0', 10),
    file_date: data.file_date || '',
    file_date_long: parseInt(data.file_date_long || '0', 10),
    file_tags: data.file_tags || '',
    file_path_webapp: data.file_path_webapp || '',
    file_remote_webapp: data.file_remote_webapp || '',
    file_folder_webapp: data.file_folder_webapp || '',
    file_thumbnail: data.file_thumbnail,
    video_url_webapp: data.video_url_webapp,
    audio_url_webapp: data.audio_url_webapp,
  };
};

/**
 * Login session info returned by getlogins.fn
 */
export interface LoginSession {
  username: string;
  uuid: string;
  loginTime: number;
  isRemote: boolean;
}

/**
 * Fetch all active login sessions (admin only)
 */
export const fetchLogins = async (): Promise<LoginSession[]> => {
  const response = await api.get('/cass/getlogins.fn');
  return Array.isArray(response.data) ? response.data : [];
};

/**
 * Remove a specific auth token (admin removing another user's session)
 */
export const removeAuthToken = async (targetUuid: string): Promise<{ status: string }> => {
  const response = await api.get('/cass/logout.fn', {
    params: { targetUuid }
  });
  return response.data;
};

/**
 * Logout current user (invalidate own session on backend)
 */
export const logoutCurrentUser = async (): Promise<{ status: string }> => {
  const response = await api.get('/cass/logout.fn');
  return response.data;
};

/**
 * Generate a public link auth token for a given user (admin only)
 */
export const generatePublicToken = async (username: string): Promise<{ uuid?: string; username?: string; error?: string }> => {
  const response = await api.get('/cass/gen_public.fn', {
    params: { boxuser: username }
  });
  return response.data;
};

/**
 * Fetch list of non-admin users (for public link user selection)
 * Uses existing getusersandemail.fn endpoint
 */
export const fetchUsersAndEmail = async (): Promise<{ username: string; email: string }[]> => {
  const response = await api.get('/cass/getusersandemail.fn');
  const data = response.data;
  return Array.isArray(data?.users) ? data.users : [];
};
