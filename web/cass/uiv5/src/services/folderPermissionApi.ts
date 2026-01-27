/**
 * Folder Permission API Service
 * Handles fetching and saving folder permissions
 * Currently uses mock data until backend is implemented
 */

import api from './api';

export type PermissionLevel = 'r' | 'rw' | 'none';

export interface FolderPermission {
  username: string;
  permission: PermissionLevel;
}

export interface FolderPermissionsResponse {
  permissions: FolderPermission[];
}

// Mock data for testing until backend is ready
const MOCK_PERMISSIONS: Record<string, FolderPermission[]> = {
  'scanfolders': [
    { username: 'admin', permission: 'rw' },
    { username: 'user1', permission: 'r' },
    { username: 'user2', permission: 'rw' },
  ],
  'default': [
    { username: 'admin', permission: 'rw' },
  ],
};

// Mock current user permission per folder (for testing F3 filtering)
// In real implementation, this would come from the backend based on logged-in user
const MOCK_CURRENT_USER_PERMISSIONS: Record<string, PermissionLevel> = {
  // By default, user has access to all folders
  // Add specific paths here to restrict access for testing
  // Example: 'restricted-folder': 'none',
};

// Flag to enable/disable mock mode
const USE_MOCK = false;

/**
 * Fetch folder permissions for a specific folder
 * @param folderPath - The folder path to get permissions for
 * @returns Promise with permissions array
 */
export const fetchFolderPermissions = async (
  folderPath: string
): Promise<FolderPermissionsResponse> => {
  if (USE_MOCK) {
    // Simulate network delay
    await new Promise((resolve) => setTimeout(resolve, 300));

    const permissions = MOCK_PERMISSIONS[folderPath] || MOCK_PERMISSIONS['default'];
    return { permissions: [...permissions] };
  }

  // Real API call (to be implemented with backend)
  const response = await api.get('/cass/getfolderperm.fn', {
    params: { sFolder: folderPath },
  });

  return {
    permissions: response.data?.permissions || [],
  };
};

/**
 * Save folder permissions
 * @param folderPath - The folder path to save permissions for
 * @param permissions - Array of user permissions
 */
export const saveFolderPermissions = async (
  folderPath: string,
  permissions: FolderPermission[]
): Promise<void> => {
  if (USE_MOCK) {
    // Simulate network delay
    await new Promise((resolve) => setTimeout(resolve, 500));

    // Update mock data (for testing persistence within session)
    MOCK_PERMISSIONS[folderPath] = [...permissions];
    console.log('Mock: Saved permissions for', folderPath, permissions);
    return;
  }

  // Real API call - uses GET with URL params (backend pattern)
  const response = await api.get('/cass/setfolderperm.fn', {
    params: {
      sFolder: folderPath,
      permissions: JSON.stringify(permissions),
    },
  });

  // Check for error response
  if (response.data && response.data.success === false) {
    throw new Error(response.data.error || 'Failed to save permissions');
  }
};

/**
 * Check if current user has permission to access a folder
 * @param folderPath - The folder path to check
 * @returns Promise with permission level for current user
 */
export const checkFolderPermission = async (
  folderPath: string
): Promise<PermissionLevel> => {
  if (USE_MOCK) {
    // Check if this specific folder has a restricted permission
    if (MOCK_CURRENT_USER_PERMISSIONS[folderPath]) {
      return MOCK_CURRENT_USER_PERMISSIONS[folderPath];
    }
    // Default: return 'rw' for all folders
    return 'rw';
  }

  // Real API call (to be implemented with backend)
  const response = await api.get('/cass/getfolderperm.fn', {
    params: { sFolder: folderPath },
  });

  // Backend should return current user's permission
  return response.data?.currentUserPermission || 'none';
};

/**
 * Check permissions for multiple folders at once
 * More efficient for filtering folder lists
 * @param folderPaths - Array of folder paths to check
 * @returns Promise with map of folder path to permission level
 */
export const checkFolderPermissions = async (
  folderPaths: string[]
): Promise<Map<string, PermissionLevel>> => {
  const result = new Map<string, PermissionLevel>();

  if (USE_MOCK) {
    // In mock mode, check each folder against mock permissions
    for (const path of folderPaths) {
      const perm = MOCK_CURRENT_USER_PERMISSIONS[path] || 'rw';
      result.set(path, perm);
    }
    return result;
  }

  // Real API: batch check permissions (to be implemented with backend)
  // For now, fall back to individual checks
  for (const path of folderPaths) {
    const perm = await checkFolderPermission(path);
    result.set(path, perm);
  }

  return result;
};

/**
 * Get list of all users (for adding new users to permissions)
 * Uses existing getUsersAndEmails from shareService
 */
export { getUsersAndEmails } from './shareService';
