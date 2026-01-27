/**
 * Main Application Layout Component
 * Provides the overall structure with navigation, sidebars, and content area
 */

import { useState, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { Box } from '@mui/material';
import { TopNav } from './TopNav';
import { LeftSidebar } from './LeftSidebar';
import { RightSidebar, RIGHT_SIDEBAR_WIDTH } from './RightSidebar';
import { SidebarContext } from '../../contexts/SidebarContext';
import { fetchUserSessionInfo } from '../../services/fileApi';
import { setIsAdmin } from '../../store/slices/authSlice';
import type { AppDispatch } from '../../store/store';

export function AppLayout() {
  const location = useLocation();
  const dispatch = useDispatch<AppDispatch>();
  const showLeftSidebar = location.pathname.startsWith('/files');
  const [rightSidebarOpen, setRightSidebarOpen] = useState(false);

  // Fetch user session info on mount to get isAdmin status
  useEffect(() => {
    const loadUserSession = async () => {
      try {
        const sessionInfo = await fetchUserSessionInfo();
        dispatch(setIsAdmin(sessionInfo.isAdmin));
      } catch (error) {
        console.error('Failed to load user session info:', error);
      }
    };
    loadUserSession();
  }, [dispatch]);

  return (
    <SidebarContext.Provider value={{ rightSidebarOpen }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
        {/* Top Navigation */}
        <TopNav />

        {/* Main Layout Container */}
        <Box
          sx={{
            display: 'flex',
            flex: 1,
            overflow: 'hidden',
            position: 'relative',
            height: 'calc(100vh - 64px)', // Subtract TopNav height
          }}
        >
          {/* Left Sidebar - Show only on files page */}
          {showLeftSidebar && <LeftSidebar />}

          {/* Main Content Area */}
          <Box
            component="main"
            sx={{
              flex: 1,
              overflow: 'hidden',
              backgroundColor: 'background.paper',
              marginRight: rightSidebarOpen ? `${RIGHT_SIDEBAR_WIDTH}px` : 0,
              transition: 'margin-right 0.3s',
              display: 'flex',
              flexDirection: 'column',
              height: '100%',
            }}
          >
            <Outlet />
          </Box>

          {/* Right Sidebar - Chat, Playlist, Events */}
          <RightSidebar onOpenChange={setRightSidebarOpen} />
        </Box>
      </Box>
    </SidebarContext.Provider>
  );
}
