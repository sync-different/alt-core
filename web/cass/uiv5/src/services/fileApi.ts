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
export const fetchFolders = async (sFolder: string = 'scanfolders'): Promise<Folder[]> => {
  const response = await api.get('/cass/getfolders-json.fn', {
    params: { sFolder }
  });

  // Backend returns array of folder names or objects with folder info
  const folders = Array.isArray(response.data) ? response.data : response.data?.folders || [];

  return folders.map((folder: any) => ({
    name: typeof folder === 'string' ? folder : folder.name || folder.folder || '',
    path: typeof folder === 'object' ? folder.path : undefined,
    count: typeof folder === 'object' ? folder.count : undefined,
    type: typeof folder === 'object' ? folder.type : undefined,
    md5: typeof folder === 'object' ? folder.md5 : undefined,
  }));
};
