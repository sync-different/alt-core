/**
 * React Router Configuration
 * Defines all application routes with authentication protection
 */

import { createBrowserRouter, Navigate } from 'react-router-dom';
import { LoginPage } from './features/auth/LoginPage';
import { ProtectedRoute } from './features/auth/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';
import { HomePage } from './pages/HomePage';
import { MyFilesPage } from './pages/MyFilesPage';
import { FoldersPage } from './pages/FoldersPage';
import { BackupPage } from './pages/BackupPage';
import { SharesPage } from './pages/SharesPage';
import { MultiClusterPage } from './pages/MultiClusterPage';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          {
            index: true,
            element: <Navigate to="/home" replace />,
          },
          {
            path: 'home',
            element: <HomePage />,
          },
          {
            path: 'files/:ftype/:range',
            element: <MyFilesPage />,
          },
          {
            path: 'folders',
            element: <FoldersPage />,
          },
          {
            path: 'backup',
            element: <BackupPage />,
          },
          {
            path: 'shares',
            element: <SharesPage />,
          },
          {
            path: 'clusters',
            element: <MultiClusterPage />,
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
]);
