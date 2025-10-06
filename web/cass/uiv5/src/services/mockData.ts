/**
 * Mock data for development mode
 * Simulates backend responses when backend is not available
 */

import type { File } from '../types/models';
import type { Tag } from '../store/slices/tagsSlice';
import type { SidebarStats } from '../store/slices/sidebarSlice';

// Check if we're in mock mode (dev mode + VITE_MOCK_API=true)
export const isMockMode = import.meta.env.DEV && import.meta.env.VITE_MOCK_API === 'true';

// Generate sample files
export const generateMockFiles = (count: number = 20): File[] => {
  const fileTypes = ['photo', 'music', 'movie', 'document'];
  const extensions = ['.jpg', '.png', '.mp3', '.mp4', '.pdf', '.doc'];
  const tags = ['important', 'work', 'personal', 'archive', 'backup'];

  return Array.from({ length: count }, (_, i) => ({
    multiclusterid: `mock-${i}-${Math.random().toString(36).substr(2, 9)}`,
    nickname: `mock-${i}`,
    md5hash: `md5-${i}`,
    file_name: `sample-file-${i}${extensions[i % extensions.length]}`,
    name: `sample-file-${i}${extensions[i % extensions.length]}`,
    file_group: fileTypes[i % fileTypes.length],
    file_size: Math.floor(Math.random() * 10000000),
    file_date: new Date(Date.now() - Math.random() * 365 * 24 * 60 * 60 * 1000).toISOString(),
    file_date_long: Date.now() - Math.floor(Math.random() * 365 * 24 * 60 * 60 * 1000),
    file_tags: tags.slice(0, Math.floor(Math.random() * 3)).join(','),
    tags: tags.slice(0, Math.floor(Math.random() * 3)),
    file_path: `/mock/path/to/file-${i}`,
  }));
};

export const mockTags: Tag[] = [
  { tag: 'important', count: 45 },
  { tag: 'work', count: 32 },
  { tag: 'personal', count: 28 },
  { tag: 'archive', count: 15 },
  { tag: 'backup', count: 12 },
];

export const mockSidebarStats: SidebarStats = {
  fileTypes: [
    { ftype: '.all', count: 156 },
    { ftype: '.photo', count: 45 },
    { ftype: '.music', count: 23 },
    { ftype: '.video', count: 18 },
    { ftype: '.document', count: 35 },
    { ftype: '.doc', count: 12 },
    { ftype: '.xls', count: 8 },
    { ftype: '.ppt', count: 5 },
    { ftype: '.pdf', count: 10 },
  ],
  timeRanges: [
    { range: '1', count: 5 },
    { range: '3', count: 12 },
    { range: '7', count: 23 },
    { range: '14', count: 34 },
    { range: '30', count: 67 },
    { range: '365', count: 145 },
    { range: '.all', count: 156 },
  ],
};
