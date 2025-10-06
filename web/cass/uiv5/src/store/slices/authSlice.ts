/**
 * Redux slice for authentication state management
 */

import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import type { RootState } from '../store';

interface AuthState {
  uuid: string | null;
  username: string | null;
  isAuthenticated: boolean;
}

const initialState: AuthState = {
  uuid: null,
  username: null,
  isAuthenticated: false,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setAuth: (state, action: PayloadAction<{ uuid: string; username: string }>) => {
      state.uuid = action.payload.uuid;
      state.username = action.payload.username;
      state.isAuthenticated = true;
    },
    clearAuth: (state) => {
      state.uuid = null;
      state.username = null;
      state.isAuthenticated = false;
    },
  },
});

// Actions
export const { setAuth, clearAuth } = authSlice.actions;

// Selectors
export const selectIsAuthenticated = (state: RootState): boolean =>
  state.auth.isAuthenticated;

export const selectUsername = (state: RootState): string | null =>
  state.auth.username;

export const selectUuid = (state: RootState): string | null =>
  state.auth.uuid;

// Reducer
export default authSlice.reducer;
