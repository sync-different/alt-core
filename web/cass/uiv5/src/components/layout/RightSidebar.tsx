/**
 * Right Sidebar - Chat, Playlist, Events
 */

import { useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Box,
  Drawer,
  Tabs,
  Tab,
  IconButton,
  Badge,
} from '@mui/material';
import {
  ChevronRight as ChevronRightIcon,
  ChevronLeft as ChevronLeftIcon,
  Chat as ChatIcon,
  QueueMusic as PlaylistIcon,
  Notifications as EventsIcon,
  Subtitles as SubtitlesIcon,
  Label as LabelIcon,
} from '@mui/icons-material';
import { ChatPanel } from '../chat/ChatPanel';
import { EventsPanel } from '../events/EventsPanel';
import { TranscriptPanel } from '../transcript/TranscriptPanel';
import { TagsPanel } from '../tags/TagsPanel';
import { PlaylistPanel } from '../playlist/PlaylistPanel';
import { selectCurrentFile } from '../../store/slices/viewerSlice';
import type { RootState } from '../../store/store';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`sidebar-tabpanel-${index}`}
      aria-labelledby={`sidebar-tab-${index}`}
      {...other}
      style={{ height: '100%' }}
    >
      {value === index && (
        <Box sx={{ height: '100%', overflow: 'hidden' }}>
          {children}
        </Box>
      )}
    </div>
  );
}

export const RIGHT_SIDEBAR_WIDTH = 400;

interface RightSidebarProps {
  onOpenChange?: (open: boolean) => void;
  fullscreen?: boolean;
  externalOpen?: boolean;
}

export function RightSidebar({ onOpenChange, fullscreen = false, externalOpen }: RightSidebarProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const [activeTab, setActiveTab] = useState(0);

  // Use external open state if provided (for fullscreen mode), otherwise use internal
  const open = externalOpen !== undefined ? externalOpen : internalOpen;

  // Check if current file is a video (will attempt to fetch transcription for all videos)
  const currentFile = useSelector((state: RootState) => selectCurrentFile(state));
  const hasTranscript = currentFile?.file_group === 'movie';
  const hasFile = !!currentFile;

  // Count total tabs to determine if we should use fullWidth variant
  const totalTabs = 3 + (hasFile ? 1 : 0) + (hasTranscript ? 1 : 0);

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const toggleDrawer = () => {
    const newOpen = !open;
    setInternalOpen(newOpen);
    onOpenChange?.(newOpen);
  };

  const drawerWidth = RIGHT_SIDEBAR_WIDTH;

  return (
    <>
      {/* Toggle Button - Always visible on right edge */}
      <IconButton
        onClick={toggleDrawer}
        sx={{
          position: 'fixed',
          right: open ? drawerWidth : 0,
          top: '50%',
          transform: 'translateY(-50%)',
          zIndex: fullscreen ? 1302 : (theme) => theme.zIndex.drawer + 2,
          backgroundColor: 'primary.main',
          color: 'white',
          '&:hover': {
            backgroundColor: 'primary.dark',
          },
          borderRadius: open ? '4px 0 0 4px' : '4px',
          transition: 'right 0.3s',
        }}
      >
        {open ? <ChevronRightIcon /> : <ChevronLeftIcon />}
      </IconButton>

      {/* Drawer */}
      <Drawer
        anchor="right"
        open={open}
        onClose={toggleDrawer}
        variant="persistent"
        hideBackdrop
        ModalProps={{
          keepMounted: true,
        }}
        sx={{
          width: 0, // Don't take up space in flex layout
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            top: fullscreen ? 0 : 64, // Full height in fullscreen, below top nav otherwise
            height: fullscreen ? '100%' : 'calc(100% - 64px)',
            backgroundColor: '#ffffff',
            borderLeft: '1px solid rgba(0, 0, 0, 0.12)',
            position: 'fixed',
            right: 0,
          },
        }}
      >
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          {/* Tabs */}
          <Tabs
            value={activeTab}
            onChange={handleTabChange}
            variant={totalTabs === 3 ? "fullWidth" : "scrollable"}
            scrollButtons={totalTabs === 3 ? false : "auto"}
            sx={{
              borderBottom: 1,
              borderColor: 'divider',
              minHeight: 48,
              '& .MuiTab-root': {
                minWidth: 60,
                fontSize: '0.75rem',
                padding: '6px 8px',
              },
            }}
          >
            <Tab
              icon={
                <Badge badgeContent={0} color="error">
                  <ChatIcon fontSize="small" />
                </Badge>
              }
              label="Chat"
            />
            <Tab icon={<PlaylistIcon fontSize="small" />} label="Playlist" />
            <Tab icon={<EventsIcon fontSize="small" />} label="Events" />
            {hasFile && <Tab icon={<LabelIcon fontSize="small" />} label="Tags" />}
            {hasTranscript && <Tab icon={<SubtitlesIcon fontSize="small" />} label="Transcript" />}
          </Tabs>

          {/* Tab Panels */}
          <Box sx={{ flex: 1, overflow: 'hidden' }}>
            <TabPanel value={activeTab} index={0}>
              <ChatPanel />
            </TabPanel>
            <TabPanel value={activeTab} index={1}>
              <PlaylistPanel embedded />
            </TabPanel>
            <TabPanel value={activeTab} index={2}>
              <EventsPanel />
            </TabPanel>
            {hasFile && (
              <TabPanel value={activeTab} index={3}>
                <TagsPanel />
              </TabPanel>
            )}
            {hasTranscript && (
              <TabPanel value={activeTab} index={4}>
                <TranscriptPanel />
              </TabPanel>
            )}
          </Box>
        </Box>
      </Drawer>
    </>
  );
}
