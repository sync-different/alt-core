/**
 * Redux store configuration with TypeScript support
 */

import { configureStore } from '@reduxjs/toolkit';
import authReducer from './slices/authSlice';
import filesReducer from './slices/filesSlice';
import tagsReducer from './slices/tagsSlice';
import sidebarReducer from './slices/sidebarSlice';
import viewerReducer from './slices/viewerSlice';
import playlistReducer from './slices/playlistSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    files: filesReducer,
    tags: tagsReducer,
    sidebar: sidebarReducer,
    viewer: viewerReducer,
    playlist: playlistReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        // Ignore these action types if needed
        ignoredActions: [],
        // Ignore these field paths in all actions
        ignoredActionPaths: [],
        // Ignore these paths in the state
        ignoredPaths: [],
      },
    }),
});

// Infer the `RootState` and `AppDispatch` types from the store itself
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
