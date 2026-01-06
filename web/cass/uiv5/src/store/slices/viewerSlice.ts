/**
 * Redux slice for tracking currently viewed file
 * Used for context-aware chat (comments on specific files)
 */

import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import type { RootState } from '../store';
import type { File } from '../../types/models';

interface ViewerState {
  currentFile: File | null;
  videoCurrentTime: number; // Current video playback time in seconds
  videoDuration: number; // Total video duration in seconds
  seekToTime: number | null; // Target time to seek to (set by chat timestamp click)
}

const initialState: ViewerState = {
  currentFile: null,
  videoCurrentTime: 0,
  videoDuration: 0,
  seekToTime: null,
};

const viewerSlice = createSlice({
  name: 'viewer',
  initialState,
  reducers: {
    setCurrentFile: (state, action: PayloadAction<File | null>) => {
      state.currentFile = action.payload;
    },
    clearCurrentFile: (state) => {
      state.currentFile = null;
      state.videoCurrentTime = 0;
      state.videoDuration = 0;
      state.seekToTime = null;
    },
    setVideoCurrentTime: (state, action: PayloadAction<number>) => {
      state.videoCurrentTime = action.payload;
    },
    setVideoDuration: (state, action: PayloadAction<number>) => {
      state.videoDuration = action.payload;
    },
    seekToTime: (state, action: PayloadAction<number>) => {
      state.seekToTime = action.payload;
    },
    clearSeekToTime: (state) => {
      state.seekToTime = null;
    },
    updateCurrentFileTags: (state, action: PayloadAction<string>) => {
      if (state.currentFile) {
        state.currentFile.file_tags = action.payload;
      }
    },
  },
});

// Actions
export const { setCurrentFile, clearCurrentFile, setVideoCurrentTime, setVideoDuration, seekToTime, clearSeekToTime, updateCurrentFileTags } = viewerSlice.actions;

// Selectors
export const selectCurrentFile = (state: RootState): File | null =>
  state.viewer.currentFile;

export const selectCurrentFileMD5 = (state: RootState): string =>
  state.viewer.currentFile?.nickname || '';

export const selectVideoCurrentTime = (state: RootState): number =>
  state.viewer.videoCurrentTime;

export const selectSeekToTime = (state: RootState): number | null =>
  state.viewer.seekToTime;

export const selectVideoDuration = (state: RootState): number =>
  state.viewer.videoDuration;

// Reducer
export default viewerSlice.reducer;
