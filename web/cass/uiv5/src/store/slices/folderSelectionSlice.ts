import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';

/**
 * Multi-select state for the FoldersPage. Mirrors the shape of `filesSlice`'s
 * selection sub-state, but stays separate because the FoldersPage owns its own
 * folder/file feed (loaded per-navigation), independent from the search-driven
 * `state.files.files` list.
 *
 * `selectRange` and `selectAll` accept the caller-supplied ordered list of MD5s
 * (only file items in the current folder, since folders aren't selectable in
 * v1). The slice itself doesn't track the underlying feed.
 *
 * See internal/PROJECT_FOLDER_MULTISELECT.md for design rationale.
 */
interface FolderSelectionState {
  selectedFileIds: string[];
  lastSelectedId: string | null;
}

const initialState: FolderSelectionState = {
  selectedFileIds: [],
  lastSelectedId: null,
};

const folderSelectionSlice = createSlice({
  name: 'folderSelection',
  initialState,
  reducers: {
    toggleFileSelection: (state, action: PayloadAction<string>) => {
      const id = action.payload;
      const idx = state.selectedFileIds.indexOf(id);
      if (idx > -1) {
        state.selectedFileIds.splice(idx, 1);
      } else {
        state.selectedFileIds.push(id);
      }
      state.lastSelectedId = id;
    },
    selectRange: (
      state,
      action: PayloadAction<{ startId: string; endId: string; allIds: string[] }>,
    ) => {
      const { startId, endId, allIds } = action.payload;
      const startIndex = allIds.indexOf(startId);
      const endIndex = allIds.indexOf(endId);
      if (startIndex !== -1 && endIndex !== -1) {
        const start = Math.min(startIndex, endIndex);
        const end = Math.max(startIndex, endIndex);
        const rangeIds = allIds.slice(start, end + 1);
        rangeIds.forEach((id) => {
          if (!state.selectedFileIds.includes(id)) {
            state.selectedFileIds.push(id);
          }
        });
      }
      state.lastSelectedId = endId;
    },
    selectAll: (state, action: PayloadAction<string[]>) => {
      state.selectedFileIds = [...action.payload];
    },
    deselectAll: (state) => {
      state.selectedFileIds = [];
      state.lastSelectedId = null;
    },
  },
});

export const {
  toggleFileSelection,
  selectRange,
  selectAll,
  deselectAll,
} = folderSelectionSlice.actions;

export default folderSelectionSlice.reducer;
