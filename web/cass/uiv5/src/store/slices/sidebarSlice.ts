import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';

export interface FileTypeStat {
  ftype: string;
  count: number;
}

export interface TimeRangeStat {
  range: string;
  count: number;
}

export interface SidebarStats {
  fileTypes: FileTypeStat[];
  timeRanges: TimeRangeStat[];
}

interface SidebarState {
  stats: SidebarStats;
  loading: boolean;
}

const initialState: SidebarState = {
  stats: {
    fileTypes: [],
    timeRanges: [],
  },
  loading: false,
};

const sidebarSlice = createSlice({
  name: 'sidebar',
  initialState,
  reducers: {
    setStats: (state, action: PayloadAction<SidebarStats>) => {
      state.stats = action.payload;
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
    },
  },
});

export const { setStats, setLoading } = sidebarSlice.actions;

export default sidebarSlice.reducer;
