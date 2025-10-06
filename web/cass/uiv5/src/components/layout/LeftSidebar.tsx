/**
 * Left Sidebar - File Type and Time Range Filters
 */

import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import {
  Box,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  Typography,
  Paper,
} from '@mui/material';
import {
  InsertDriveFile as AllIcon,
  Image as PhotoIcon,
  MusicNote as MusicIcon,
  VideoLibrary as VideoIcon,
  Description as DocumentIcon,
  Article as DocIcon,
  TableChart as XlsIcon,
  Slideshow as PptIcon,
  PictureAsPdf as PdfIcon,
  CalendarToday as CalendarIcon,
  LocalOffer as TagIcon,
} from '@mui/icons-material';
import { fetchSidebarStats, fetchTags } from '../../services/fileApi';
import { setStats } from '../../store/slices/sidebarSlice';
import { setTags } from '../../store/slices/tagsSlice';
import { setFilters } from '../../store/slices/filesSlice';
import type { RootState } from '../../store/store';
import type { AppDispatch } from '../../store/store';

const FILE_TYPES = [
  { value: '.all', label: 'All Types', icon: AllIcon, color: '#6B7280' },
  { value: '.photo', label: 'Photos', icon: PhotoIcon, color: '#10B981' },
  { value: '.music', label: 'Music', icon: MusicIcon, color: '#8B5CF6' },
  { value: '.video', label: 'Videos', icon: VideoIcon, color: '#EF4444' },
  { value: '.document', label: 'Documents', icon: DocumentIcon, color: '#3B82F6' },
  { value: '.doc', label: 'Word', icon: DocIcon, color: '#2563EB' },
  { value: '.xls', label: 'Excel', icon: XlsIcon, color: '#059669' },
  { value: '.ppt', label: 'PowerPoint', icon: PptIcon, color: '#DC2626' },
  { value: '.pdf', label: 'PDF', icon: PdfIcon, color: '#DC2626' },
];

const TIME_RANGES = [
  { value: '1', label: 'Today', days: 1 },
  { value: '3', label: 'Past 3 Days', days: 3 },
  { value: '7', label: 'This Week', days: 7 },
  { value: '14', label: 'Past 2 Weeks', days: 14 },
  { value: '30', label: 'Past 30 Days', days: 30 },
  { value: '365', label: 'This Year', days: 365 },
  { value: '.all', label: 'All Time', days: 0 },
];

export function LeftSidebar() {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const { ftype = '.all', range = '.all' } = useParams();

  const stats = useSelector((state: RootState) => state.sidebar.stats);
  const tags = useSelector((state: RootState) => state.tags.tags);
  const searchQuery = useSelector((state: RootState) => state.files.filters.searchQuery);

  useEffect(() => {
    loadSidebarData();
  }, [ftype, range, searchQuery]);

  const loadSidebarData = async () => {
    try {
      const days = TIME_RANGES.find(r => r.value === range)?.days || 0;

      // Fetch sidebar stats
      // foo parameter should be the search query or '.all' if empty
      const statsData = await fetchSidebarStats({
        ftype,
        days,
        foo: searchQuery || '.all',
      });
      dispatch(setStats(statsData));

      // Fetch tags
      const tagsData = await fetchTags();
      dispatch(setTags(tagsData));
    } catch (error) {
      console.error('Failed to load sidebar data:', error);
    }
  };

  const handleFileTypeClick = (fileType: string) => {
    navigate(`/files/${fileType}/${range}`);
  };

  const handleTimeRangeClick = (timeRange: string) => {
    navigate(`/files/${ftype}/${timeRange}`);
  };

  const handleTagClick = (tagName: string) => {
    // When tag is clicked, set it as search query and reset filters to .all/.all
    dispatch(setFilters({
      ftype: '.all',
      range: '.all',
      searchQuery: tagName,
    }));
    // Navigate to .all/.all view
    navigate('/files/.all/.all');
  };

  const getFileTypeCount = (fileType: string): number => {
    if (!stats?.fileTypes) return 0;
    const found = stats.fileTypes.find(ft => ft.ftype === fileType);
    console.log(`getFileTypeCount(${fileType}):`, found, 'from stats:', stats.fileTypes);
    return found?.count || 0;
  };

  const getTimeRangeCount = (rangeValue: string): number => {
    if (!stats?.timeRanges) return 0;
    const found = stats.timeRanges.find(tr => tr.range === rangeValue);
    console.log(`getTimeRangeCount(${rangeValue}):`, found, 'from stats:', stats.timeRanges);
    return found?.count || 0;
  };

  return (
    <Paper
      elevation={0}
      sx={{
        width: 240,
        height: '100%',
        borderRight: 1,
        borderColor: 'divider',
        overflowY: 'auto',
      }}
    >
      <Box sx={{ p: 1.5 }}>
        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5, fontSize: '0.75rem', fontWeight: 600 }}>
          FILE TYPES
        </Typography>
        <List dense>
          {FILE_TYPES.map((type) => {
            const Icon = type.icon;
            const count = getFileTypeCount(type.value);
            const isActive = ftype === type.value;

            return (
              <ListItemButton
                key={type.value}
                selected={isActive}
                onClick={() => handleFileTypeClick(type.value)}
                sx={{
                  borderRadius: 1,
                  mb: 0.25,
                  py: 0.5,
                  minHeight: 32,
                  '&.Mui-selected': {
                    backgroundColor: 'primary.lighter',
                    '&:hover': {
                      backgroundColor: 'primary.light',
                    },
                  },
                }}
              >
                <ListItemIcon sx={{ minWidth: 32 }}>
                  <Icon
                    fontSize="small"
                    sx={{
                      color: type.color,
                      opacity: isActive ? 1 : 0.7,
                      transition: 'opacity 0.2s',
                      '&:hover': { opacity: 1 }
                    }}
                  />
                </ListItemIcon>
                <ListItemText
                  primary={type.label}
                  primaryTypographyProps={{
                    variant: 'body2',
                    fontSize: '0.875rem',
                    fontWeight: isActive ? 600 : 400,
                  }}
                />
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ ml: 0.5, fontSize: '0.75rem' }}
                >
                  {count}
                </Typography>
              </ListItemButton>
            );
          })}
        </List>

        <Divider sx={{ my: 1.5 }} />

        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5, fontSize: '0.75rem', fontWeight: 600 }}>
          TIME RANGE
        </Typography>
        <List dense>
          {TIME_RANGES.map((timeRange) => {
            const count = getTimeRangeCount(timeRange.value);
            const isActive = range === timeRange.value;

            return (
              <ListItemButton
                key={timeRange.value}
                selected={isActive}
                onClick={() => handleTimeRangeClick(timeRange.value)}
                sx={{
                  borderRadius: 1,
                  mb: 0.25,
                  py: 0.5,
                  minHeight: 32,
                  '&.Mui-selected': {
                    backgroundColor: 'primary.lighter',
                    '&:hover': {
                      backgroundColor: 'primary.light',
                    },
                  },
                }}
              >
                <ListItemIcon sx={{ minWidth: 32 }}>
                  <CalendarIcon
                    fontSize="small"
                    sx={{
                      color: '#F59E0B',
                      opacity: isActive ? 1 : 0.7,
                      transition: 'opacity 0.2s',
                      '&:hover': { opacity: 1 }
                    }}
                  />
                </ListItemIcon>
                <ListItemText
                  primary={timeRange.label}
                  primaryTypographyProps={{
                    variant: 'body2',
                    fontSize: '0.875rem',
                    fontWeight: isActive ? 600 : 400,
                  }}
                />
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ ml: 0.5, fontSize: '0.75rem' }}
                >
                  {count}
                </Typography>
              </ListItemButton>
            );
          })}
        </List>

        <Divider sx={{ my: 1.5 }} />

        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5, fontSize: '0.75rem', fontWeight: 600 }}>
          TAGS
        </Typography>
        {tags && tags.length > 0 ? (
          <Box
            sx={{
              maxHeight: 400,
              overflowY: 'auto',
              // Hide scrollbar while keeping scroll functionality
              '&::-webkit-scrollbar': {
                display: 'none',
              },
              scrollbarWidth: 'none', // Firefox
              msOverflowStyle: 'none', // IE/Edge
            }}
          >
            <List dense>
              {tags.map((tag) => {
                const isActive = searchQuery === tag.tag;
                return (
                  <ListItemButton
                    key={tag.tag}
                    selected={isActive}
                    sx={{
                      borderRadius: 1,
                      mb: 0.25,
                      py: 0.5,
                      minHeight: 32,
                      '&.Mui-selected': {
                        backgroundColor: 'primary.lighter',
                        '&:hover': {
                          backgroundColor: 'primary.light',
                        },
                      },
                    }}
                    onClick={() => handleTagClick(tag.tag)}
                  >
                    <ListItemIcon sx={{ minWidth: 32 }}>
                      <TagIcon
                        fontSize="small"
                        sx={{
                          color: '#EC4899',
                          opacity: isActive ? 1 : 0.7,
                          transition: 'opacity 0.2s',
                          '&:hover': { opacity: 1 }
                        }}
                      />
                    </ListItemIcon>
                    <ListItemText
                      primary={tag.tag}
                      primaryTypographyProps={{
                        variant: 'body2',
                        fontSize: '0.875rem',
                        fontWeight: isActive ? 600 : 400,
                        noWrap: true,
                        sx: {
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }
                      }}
                    />
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ ml: 0.5, flexShrink: 0, fontSize: '0.75rem' }}
                    >
                      {tag.count}
                    </Typography>
                  </ListItemButton>
                );
              })}
            </List>
          </Box>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', py: 1 }}>
            No tags found
          </Typography>
        )}
      </Box>
    </Paper>
  );
}
