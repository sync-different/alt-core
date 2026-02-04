/**
 * Folder Upload Context
 * Shares folder upload state between FoldersPage and TopNav
 * Enables the upload button to be permission-aware when on the Folders page
 */

import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { useSelector } from 'react-redux';
import { selectIsAdmin } from '../store/slices/authSlice';
import { checkFolderPermission } from '../services/folderPermissionApi';

interface FolderUploadContextType {
  // Current folder path (null when not on Folders page)
  currentFolder: string | null;
  // Whether user can upload to current folder
  canUpload: boolean;
  // Reason why upload is disabled (for tooltip)
  uploadDisabledReason: string | null;
  // Whether we're on the Folders page
  isOnFoldersPage: boolean;
  // Function to update current folder (called by FoldersPage)
  setCurrentFolder: (folder: string | null) => void;
  // Function to set whether we're on folders page
  setIsOnFoldersPage: (isOn: boolean) => void;
}

const FolderUploadContext = createContext<FolderUploadContextType | undefined>(undefined);

export function FolderUploadProvider({ children }: { children: ReactNode }) {
  const isAdmin = useSelector(selectIsAdmin);
  const [currentFolder, setCurrentFolderState] = useState<string | null>(null);
  const [canUpload, setCanUpload] = useState(false);
  const [uploadDisabledReason, setUploadDisabledReason] = useState<string | null>(null);
  const [isOnFoldersPage, setIsOnFoldersPageState] = useState(false);

  const setCurrentFolder = useCallback(async (folder: string | null) => {
    setCurrentFolderState(folder);

    if (folder === null) {
      // Not on Folders page or no folder selected
      setCanUpload(false);
      setUploadDisabledReason(null);
      return;
    }

    // Check if at root (scanfolders)
    if (folder === 'scanfolders') {
      setCanUpload(false);
      setUploadDisabledReason('Navigate to a folder to upload');
      return;
    }

    // Admin can always upload
    if (isAdmin) {
      setCanUpload(true);
      setUploadDisabledReason(null);
      return;
    }

    // Check permission for non-admin users
    try {
      const permission = await checkFolderPermission(folder);
      if (permission === 'rw') {
        setCanUpload(true);
        setUploadDisabledReason(null);
      } else {
        setCanUpload(false);
        setUploadDisabledReason("You don't have write permission to this folder");
      }
    } catch (error) {
      console.error('Failed to check folder permission:', error);
      setCanUpload(false);
      setUploadDisabledReason('Unable to verify folder permissions');
    }
  }, [isAdmin]);

  const setIsOnFoldersPage = useCallback((isOn: boolean) => {
    setIsOnFoldersPageState(isOn);
    if (!isOn) {
      // Reset state when leaving Folders page
      setCurrentFolderState(null);
      setCanUpload(false);
      setUploadDisabledReason(null);
    }
  }, []);

  return (
    <FolderUploadContext.Provider
      value={{
        currentFolder,
        canUpload,
        uploadDisabledReason,
        isOnFoldersPage,
        setCurrentFolder,
        setIsOnFoldersPage,
      }}
    >
      {children}
    </FolderUploadContext.Provider>
  );
}

export function useFolderUpload() {
  const context = useContext(FolderUploadContext);
  if (context === undefined) {
    throw new Error('useFolderUpload must be used within a FolderUploadProvider');
  }
  return context;
}
