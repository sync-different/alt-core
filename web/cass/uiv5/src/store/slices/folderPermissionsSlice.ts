/**
 * Redux slice for folder permissions state management
 */

import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import type { RootState } from '../store';
import type { Folder } from '../../services/fileApi';
import {
  fetchFolderPermissions,
  saveFolderPermissions as saveFolderPermissionsApi,
  type FolderPermission,
  type PermissionLevel,
} from '../../services/folderPermissionApi';

interface FolderPermissionsState {
  // Currently selected folder for the info sidebar
  selectedFolder: Folder | null;

  // Permissions for the selected folder
  permissions: FolderPermission[];

  // Loading states
  isLoading: boolean;
  isSaving: boolean;

  // Error state
  error: string | null;

  // Sidebar open state
  sidebarOpen: boolean;

  // Track if permissions have been modified
  isDirty: boolean;
}

const initialState: FolderPermissionsState = {
  selectedFolder: null,
  permissions: [],
  isLoading: false,
  isSaving: false,
  error: null,
  sidebarOpen: false,
  isDirty: false,
};

// Async thunk for fetching folder permissions
export const loadFolderPermissions = createAsyncThunk(
  'folderPermissions/load',
  async (folderPath: string, { rejectWithValue }) => {
    try {
      const response = await fetchFolderPermissions(folderPath);
      return response.permissions;
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to load permissions');
    }
  }
);

// Async thunk for saving folder permissions
export const saveFolderPermissions = createAsyncThunk(
  'folderPermissions/save',
  async (
    { folderPath, permissions }: { folderPath: string; permissions: FolderPermission[] },
    { rejectWithValue }
  ) => {
    try {
      await saveFolderPermissionsApi(folderPath, permissions);
      return permissions;
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to save permissions');
    }
  }
);

const folderPermissionsSlice = createSlice({
  name: 'folderPermissions',
  initialState,
  reducers: {
    // Set the selected folder and open sidebar
    selectFolder: (state, action: PayloadAction<Folder | null>) => {
      state.selectedFolder = action.payload;
      state.sidebarOpen = action.payload !== null;
      state.isDirty = false;
      state.error = null;
    },

    // Close the sidebar
    closeSidebar: (state) => {
      state.sidebarOpen = false;
      state.selectedFolder = null;
      state.permissions = [];
      state.isDirty = false;
      state.error = null;
    },

    // Toggle sidebar visibility
    toggleSidebar: (state) => {
      state.sidebarOpen = !state.sidebarOpen;
    },

    // Add a user permission
    addUserPermission: (state, action: PayloadAction<FolderPermission>) => {
      const existing = state.permissions.find(
        (p) => p.username === action.payload.username
      );
      if (!existing) {
        state.permissions.push(action.payload);
        state.isDirty = true;
      }
    },

    // Remove a user permission
    removeUserPermission: (state, action: PayloadAction<string>) => {
      state.permissions = state.permissions.filter(
        (p) => p.username !== action.payload
      );
      state.isDirty = true;
    },

    // Update a user's permission level
    updateUserPermission: (
      state,
      action: PayloadAction<{ username: string; permission: PermissionLevel }>
    ) => {
      const perm = state.permissions.find(
        (p) => p.username === action.payload.username
      );
      if (perm) {
        perm.permission = action.payload.permission;
        state.isDirty = true;
      }
    },

    // Clear any errors
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    // Load permissions
    builder
      .addCase(loadFolderPermissions.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(loadFolderPermissions.fulfilled, (state, action) => {
        state.isLoading = false;
        state.permissions = action.payload;
        state.isDirty = false;
      })
      .addCase(loadFolderPermissions.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Save permissions
    builder
      .addCase(saveFolderPermissions.pending, (state) => {
        state.isSaving = true;
        state.error = null;
      })
      .addCase(saveFolderPermissions.fulfilled, (state, action) => {
        state.isSaving = false;
        state.permissions = action.payload;
        state.isDirty = false;
      })
      .addCase(saveFolderPermissions.rejected, (state, action) => {
        state.isSaving = false;
        state.error = action.payload as string;
      });
  },
});

// Actions
export const {
  selectFolder,
  closeSidebar,
  toggleSidebar,
  addUserPermission,
  removeUserPermission,
  updateUserPermission,
  clearError,
} = folderPermissionsSlice.actions;

// Selectors
export const selectSelectedFolder = (state: RootState) =>
  state.folderPermissions.selectedFolder;

export const selectFolderPermissions = (state: RootState) =>
  state.folderPermissions.permissions;

export const selectIsSidebarOpen = (state: RootState) =>
  state.folderPermissions.sidebarOpen;

export const selectIsLoading = (state: RootState) =>
  state.folderPermissions.isLoading;

export const selectIsSaving = (state: RootState) =>
  state.folderPermissions.isSaving;

export const selectIsDirty = (state: RootState) =>
  state.folderPermissions.isDirty;

export const selectError = (state: RootState) =>
  state.folderPermissions.error;

// Reducer
export default folderPermissionsSlice.reducer;
