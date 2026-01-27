/**
 * Sidebar Context
 * Provides shared state for sidebar visibility across components
 */

import { createContext, useContext } from 'react';

interface SidebarContextType {
  rightSidebarOpen: boolean;
}

export const SidebarContext = createContext<SidebarContextType>({
  rightSidebarOpen: false,
});

export const useSidebarContext = () => useContext(SidebarContext);
