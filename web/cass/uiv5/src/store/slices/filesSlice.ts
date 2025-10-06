import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import type { File } from '../../types/models';

export interface FileFilters {
  ftype: string;
  range: string;
  searchQuery: string;
}

interface FilesState {
  files: File[];
  loading: boolean;
  hasMore: boolean;
  filters: FileFilters;
  selectedFileIds: string[];
  viewMode: 'list' | 'grid';
  sortOrder: 'asc' | 'desc';
  refreshTrigger: number;
  showGridDetails: boolean;
  gridSize: 'xs' | 'small' | 'medium' | 'large';
  listSize: 'xs' | 'small' | 'medium' | 'large';
}

const initialState: FilesState = {
  files: [],
  loading: false,
  hasMore: true,
  filters: {
    ftype: '.all',
    range: '.all',
    searchQuery: '',
  },
  selectedFileIds: [],
  viewMode: 'list', // Default to list view (grid view will be added in Phase 4)
  sortOrder: 'desc',
  refreshTrigger: 0,
  showGridDetails: true, // Show details by default in grid view
  gridSize: 'medium', // Default grid size
  listSize: 'medium', // Default list size
};

const filesSlice = createSlice({
  name: 'files',
  initialState,
  reducers: {
    setFiles: (state, action: PayloadAction<File[]>) => {
      state.files = action.payload;
      state.hasMore = action.payload.length >= 100;
    },
    appendFiles: (state, action: PayloadAction<File[]>) => {
      state.files = [...state.files, ...action.payload];
      state.hasMore = action.payload.length >= 100;
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
    },
    setFilters: (state, action: PayloadAction<Partial<FileFilters>>) => {
      state.filters = { ...state.filters, ...action.payload };
      // Reset files and pagination when filters change
      state.files = [];
      state.hasMore = true;
    },
    toggleFileSelection: (state, action: PayloadAction<string>) => {
      const id = action.payload;
      const index = state.selectedFileIds.indexOf(id);
      if (index > -1) {
        state.selectedFileIds.splice(index, 1);
      } else {
        state.selectedFileIds.push(id);
      }
    },
    selectRange: (state, action: PayloadAction<{ startId: string; endId: string }>) => {
      const { startId, endId } = action.payload;
      const startIndex = state.files.findIndex(f => f.multiclusterid === startId);
      const endIndex = state.files.findIndex(f => f.multiclusterid === endId);

      if (startIndex !== -1 && endIndex !== -1) {
        const start = Math.min(startIndex, endIndex);
        const end = Math.max(startIndex, endIndex);
        const rangeIds = state.files.slice(start, end + 1).map(f => f.multiclusterid);

        // Add all IDs in range to selection
        rangeIds.forEach(id => {
          if (!state.selectedFileIds.includes(id)) {
            state.selectedFileIds.push(id);
          }
        });
      }
    },
    selectAll: (state) => {
      state.selectedFileIds = state.files.map(file => file.multiclusterid);
    },
    deselectAll: (state) => {
      state.selectedFileIds = [];
    },
    setViewMode: (state, action: PayloadAction<'list' | 'grid'>) => {
      state.viewMode = action.payload;
    },
    setSortOrder: (state, action: PayloadAction<'asc' | 'desc'>) => {
      state.sortOrder = action.payload;
      state.files = [];
      state.hasMore = true;
    },
    resetFiles: (state) => {
      state.files = [];
      state.hasMore = true;
      state.selectedFileIds = [];
      state.refreshTrigger = Date.now();
    },
    toggleGridDetails: (state) => {
      state.showGridDetails = !state.showGridDetails;
    },
    setGridSize: (state, action: PayloadAction<'xs' | 'small' | 'medium' | 'large'>) => {
      state.gridSize = action.payload;
    },
    setListSize: (state, action: PayloadAction<'xs' | 'small' | 'medium' | 'large'>) => {
      state.listSize = action.payload;
    },
    updateFileTags: (state, action: PayloadAction<{ fileId: string; tags: string }>) => {
      const file = state.files.find(f => f.nickname === action.payload.fileId);
      if (file) {
        file.file_tags = action.payload.tags;
      }
    },
  },
});

export const {
  setFiles,
  appendFiles,
  setLoading,
  setFilters,
  toggleFileSelection,
  selectRange,
  selectAll,
  deselectAll,
  setViewMode,
  setSortOrder,
  resetFiles,
  toggleGridDetails,
  setGridSize,
  setListSize,
  updateFileTags,
} = filesSlice.actions;

export default filesSlice.reducer;
