/**
 * Admin Page
 * Admin-only page with sub-tab navigation for admin operations.
 * First sub-tab: Current Logins
 */

import { useState } from 'react';
import { useSelector } from 'react-redux';
import { Box, Tabs, Tab, Alert } from '@mui/material';
import { selectIsAdmin } from '../store/slices/authSlice';
import { CurrentLoginsTab } from '../components/admin/CurrentLoginsTab';
import { UsersTab } from '../components/admin/UsersTab';

export function AdminPage() {
  const isAdmin = useSelector(selectIsAdmin);
  const [activeTab, setActiveTab] = useState(0);

  if (!isAdmin) {
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="error">
          Access denied. This page is only available to administrators.
        </Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Sub-tab navigation */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Tabs
          value={activeTab}
          onChange={(_e, newValue) => setActiveTab(newValue)}
          sx={{ px: 2 }}
        >
          <Tab label="Current Logins" />
          <Tab label="Users" />
        </Tabs>
      </Box>

      {/* Tab content */}
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {activeTab === 0 && <CurrentLoginsTab />}
        {activeTab === 1 && <UsersTab />}
      </Box>
    </Box>
  );
}
