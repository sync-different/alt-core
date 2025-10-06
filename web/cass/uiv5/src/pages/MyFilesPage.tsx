/**
 * My Files Page - Main File Browser
 */

import { useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Box, Typography, ToggleButtonGroup, ToggleButton, Select, MenuItem, FormControl, InputLabel, IconButton, Tooltip } from '@mui/material';
import { ViewList as ViewListIcon, ViewModule as ViewModuleIcon, Info as InfoIcon, InfoOutlined as InfoOutlinedIcon, ViewComfy as ViewComfyIcon, ViewModule as ViewModuleMediumIcon, Apps as AppsIcon } from '@mui/icons-material';
import { FileList } from '../features/files/FileList';
import { SelectionToolbar } from '../features/files/SelectionToolbar';
import { PlaylistPanel } from '../components/playlist/PlaylistPanel';
import { fetchFiles } from '../services/fileApi';
import { setFiles, appendFiles, setLoading, setFilters, setViewMode, setSortOrder, toggleGridDetails, setGridSize, setListSize } from '../store/slices/filesSlice';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll';
import { useFileSelection } from '../hooks/useFileSelection';
import type { RootState, AppDispatch } from '../store/store';

const TIME_RANGE_DAYS: Record<string, number> = {
  '1': 1,
  '3': 3,
  '7': 7,
  '14': 14,
  '30': 30,
  '365': 365,
  '.all': 0,
};

export function MyFilesPage() {
  const { ftype = '.all', range = '.all' } = useParams();
  const dispatch = useDispatch<AppDispatch>();

  const files = useSelector((state: RootState) => state.files.files);
  const loading = useSelector((state: RootState) => state.files.loading);
  const hasMore = useSelector((state: RootState) => state.files.hasMore);
  const searchQuery = useSelector((state: RootState) => state.files.filters.searchQuery);
  const sortOrder = useSelector((state: RootState) => state.files.sortOrder);
  const viewMode = useSelector((state: RootState) => state.files.viewMode);
  const refreshTrigger = useSelector((state: RootState) => state.files.refreshTrigger);
  const showGridDetails = useSelector((state: RootState) => state.files.showGridDetails);
  const gridSize = useSelector((state: RootState) => state.files.gridSize);
  const listSize = useSelector((state: RootState) => state.files.listSize);
  const { selectedCount } = useFileSelection();

  // Update filters when route params change
  useEffect(() => {
    dispatch(setFilters({ ftype, range }));
  }, [ftype, range, dispatch]);

  // Load files when filters change or refresh is triggered
  useEffect(() => {
    loadFiles(true);
  }, [ftype, range, searchQuery, sortOrder, refreshTrigger]);

  const loadFiles = async (reset: boolean = false) => {
    if (loading) return;

    try {
      dispatch(setLoading(true));

      const days = TIME_RANGE_DAYS[range] ?? 0;
      const lastFile = reset ? null : files[files.length - 1];

      console.log('Loading files:', {
        reset,
        currentFileCount: files.length,
        lastFileDate: lastFile?.file_date_long,
        lastFileName: lastFile?.name,
      });

      const result = await fetchFiles({
        ftype,
        days,
        foo: searchQuery,
        numobj: 100,
        date: lastFile?.file_date_long,
        order: sortOrder === 'asc' ? 'Asc' : 'Desc',
        screenSize: 160,
      });

      console.log('Fetched files:', {
        count: result.files?.length || 0,
        reset,
      });

      if (reset) {
        dispatch(setFiles(result.files || []));
      } else {
        dispatch(appendFiles(result.files || []));
      }
    } catch (error: any) {
      console.error('Failed to fetch files:', error);
      console.error('Error details:', {
        message: error.message,
        response: error.response,
        request: error.request,
        config: error.config,
      });
      dispatch(setFiles([]));
    } finally {
      dispatch(setLoading(false));
    }
  };

  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const loadMore = () => {
    if (!loading && hasMore) {
      loadFiles(false);
    }
  };

  const observerRef = useInfiniteScroll({
    hasMore,
    isLoading: loading,
    onLoadMore: loadMore,
    root: scrollContainerRef.current,
  });

  const handleViewModeChange = (_event: React.MouseEvent<HTMLElement>, newMode: 'list' | 'grid' | null) => {
    if (newMode !== null) {
      dispatch(setViewMode(newMode));
    }
  };

  const handleSortChange = (event: any) => {
    dispatch(setSortOrder(event.target.value));
  };

  const handleGridSizeChange = (_event: React.MouseEvent<HTMLElement>, newSize: 'xs' | 'small' | 'medium' | 'large' | null) => {
    if (newSize !== null) {
      dispatch(setGridSize(newSize));
    }
  };

  const handleListSizeChange = (_event: React.MouseEvent<HTMLElement>, newSize: 'xs' | 'small' | 'medium' | 'large' | null) => {
    if (newSize !== null) {
      dispatch(setListSize(newSize));
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', p: 3 }}>
      {/* Fixed Header */}
      <Box sx={{
        position: 'sticky',
        top: 0,
        zIndex: 10,
        backgroundColor: 'background.default',
        pb: 2,
      }}>
        <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center', minHeight: 48 }}>
          {selectedCount === 0 ? (
            <Typography variant="h4" sx={{ height: 48, display: 'flex', alignItems: 'center', color: 'text.primary' }}>
              My Files
            </Typography>
          ) : (
            <SelectionToolbar inline />
          )}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            {selectedCount === 0 && files.length > 0 && (
              <Typography variant="body2" color="text.secondary">
                {files.length} {files.length === 1 ? 'file' : 'files'}
              </Typography>
            )}
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <InputLabel>Sort By</InputLabel>
              <Select
                value={sortOrder}
                label="Sort By"
                onChange={handleSortChange}
              >
                <MenuItem value="desc">Newest First</MenuItem>
                <MenuItem value="asc">Oldest First</MenuItem>
              </Select>
            </FormControl>
            <ToggleButtonGroup
              value={viewMode}
              exclusive
              onChange={handleViewModeChange}
              size="small"
              aria-label="view mode"
            >
              <ToggleButton value="list" aria-label="list view">
                <ViewListIcon sx={{ color: viewMode === 'list' ? '#3B82F6' : 'inherit' }} />
              </ToggleButton>
              <ToggleButton value="grid" aria-label="grid view">
                <ViewModuleIcon sx={{ color: viewMode === 'grid' ? '#10B981' : 'inherit' }} />
              </ToggleButton>
            </ToggleButtonGroup>
            {viewMode === 'list' && (
              <ToggleButtonGroup
                value={listSize}
                exclusive
                onChange={handleListSizeChange}
                size="small"
                aria-label="list size"
              >
                <Tooltip title="Extra Small">
                  <ToggleButton value="xs" aria-label="extra small list">
                    <AppsIcon sx={{ fontSize: '0.875rem', color: listSize === 'xs' ? '#8B5CF6' : 'inherit' }} />
                  </ToggleButton>
                </Tooltip>
                <Tooltip title="Small">
                  <ToggleButton value="small" aria-label="small list">
                    <AppsIcon fontSize="small" sx={{ color: listSize === 'small' ? '#3B82F6' : 'inherit' }} />
                  </ToggleButton>
                </Tooltip>
                <Tooltip title="Medium">
                  <ToggleButton value="medium" aria-label="medium list">
                    <ViewModuleMediumIcon fontSize="medium" sx={{ color: listSize === 'medium' ? '#10B981' : 'inherit' }} />
                  </ToggleButton>
                </Tooltip>
                <Tooltip title="Large">
                  <ToggleButton value="large" aria-label="large list">
                    <ViewComfyIcon fontSize="large" sx={{ color: listSize === 'large' ? '#F59E0B' : 'inherit' }} />
                  </ToggleButton>
                </Tooltip>
              </ToggleButtonGroup>
            )}
            {viewMode === 'grid' && (
              <>
                <ToggleButtonGroup
                  value={gridSize}
                  exclusive
                  onChange={handleGridSizeChange}
                  size="small"
                  aria-label="grid size"
                >
                  <Tooltip title="Extra Small">
                    <ToggleButton value="xs" aria-label="extra small grid">
                      <AppsIcon sx={{ fontSize: '0.875rem', color: gridSize === 'xs' ? '#8B5CF6' : 'inherit' }} />
                    </ToggleButton>
                  </Tooltip>
                  <Tooltip title="Small">
                    <ToggleButton value="small" aria-label="small grid">
                      <AppsIcon fontSize="small" sx={{ color: gridSize === 'small' ? '#3B82F6' : 'inherit' }} />
                    </ToggleButton>
                  </Tooltip>
                  <Tooltip title="Medium">
                    <ToggleButton value="medium" aria-label="medium grid">
                      <ViewModuleMediumIcon fontSize="medium" sx={{ color: gridSize === 'medium' ? '#10B981' : 'inherit' }} />
                    </ToggleButton>
                  </Tooltip>
                  <Tooltip title="Large">
                    <ToggleButton value="large" aria-label="large grid">
                      <ViewComfyIcon fontSize="large" sx={{ color: gridSize === 'large' ? '#F59E0B' : 'inherit' }} />
                    </ToggleButton>
                  </Tooltip>
                </ToggleButtonGroup>
                <Tooltip title={showGridDetails ? "Hide details" : "Show details"}>
                  <IconButton
                    size="small"
                    onClick={() => dispatch(toggleGridDetails())}
                    color={showGridDetails ? "primary" : "default"}
                  >
                    {showGridDetails ? <InfoIcon /> : <InfoOutlinedIcon />}
                  </IconButton>
                </Tooltip>
              </>
            )}
          </Box>
        </Box>
      </Box>

      {/* Scrollable Content */}
      <Box ref={scrollContainerRef} sx={{ flex: 1, overflow: 'auto' }}>
        <FileList observerRef={observerRef} hasMore={hasMore} onLoadMore={loadMore} />
      </Box>

      {/* Playlist Panel */}
      <PlaylistPanel />
    </Box>
  );
}
