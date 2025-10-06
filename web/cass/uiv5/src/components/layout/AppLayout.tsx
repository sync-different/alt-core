/**
 * Main Application Layout Component
 * Provides the overall structure with navigation, sidebars, and content area
 */

import { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Box } from '@mui/material';
import { TopNav } from './TopNav';
import { LeftSidebar } from './LeftSidebar';
import { RightSidebar, RIGHT_SIDEBAR_WIDTH } from './RightSidebar';

export function AppLayout() {
  const location = useLocation();
  const showLeftSidebar = location.pathname.startsWith('/files');
  const [rightSidebarOpen, setRightSidebarOpen] = useState(false);

  return (
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
  );
}
