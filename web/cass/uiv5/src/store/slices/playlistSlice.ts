/**
 * Playlist Redux Slice
 * Manages audio playlist state
 */

import { createSlice } from '@reduxjs/toolkit';
import type { File } from '../../types/models';

interface PlaylistState {
  files: File[];
  isOpen: boolean;
}

const initialState: PlaylistState = {
  files: [],
  isOpen: false,
};

const playlistSlice = createSlice({
  name: 'playlist',
  initialState,
  reducers: {
    addFiles: (state, action: { payload: File[] }) => {
      state.files = [...state.files, ...action.payload];
    },
    removeFile: (state, action: { payload: number }) => {
      state.files.splice(action.payload, 1);
    },
    clearAll: (state) => {
      state.files = [];
    },
    togglePanel: (state) => {
      state.isOpen = !state.isOpen;
    },
    setOpen: (state, action: { payload: boolean }) => {
      state.isOpen = action.payload;
    },
  },
});

export const {
  addFiles,
  removeFile,
  clearAll,
  togglePanel,
  setOpen,
} = playlistSlice.actions;

export default playlistSlice.reducer;
