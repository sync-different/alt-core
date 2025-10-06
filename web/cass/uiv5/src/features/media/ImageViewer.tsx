/**
 * Image Viewer Component
 * In-page image carousel with navigation, comments, and actions
 * Uses lazy loading to only load images near the current slide
 */

import { useState, useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { Paper, Box, IconButton, Typography, Chip, Stack } from '@mui/material';
import {
  Close as CloseIcon,
  Download as DownloadIcon,
  PlayArrow as PlayIcon,
  Stop as StopIcon,
  NavigateBefore,
  NavigateNext,
  ChevronLeft as ChevronLeftIcon,
  ChevronRight as ChevronRightIcon,
} from '@mui/icons-material';
import { Swiper, SwiperSlide } from 'swiper/react';
import { Navigation, Keyboard } from 'swiper/modules';
import type { Swiper as SwiperType } from 'swiper';

// Import Swiper styles
import 'swiper/css';
import 'swiper/css/navigation';
import 'swiper/css/keyboard';

import type { File } from '../../types/models';
import { formatDate, formatFileSize } from '../../utils/formatters';
import { setCurrentFile, clearCurrentFile } from '../../store/slices/viewerSlice';
import { buildUrl } from '../../utils/urlHelper';
import { RightSidebar, RIGHT_SIDEBAR_WIDTH } from '../../components/layout/RightSidebar';

interface ImageViewerProps {
  open: boolean;
  onClose: () => void;
  files: File[];
  initialIndex: number;
  onLoadMore?: () => void;
  hasMore?: boolean;
}

export function ImageViewer({
  open,
  onClose,
  files,
  initialIndex,
  onLoadMore,
  hasMore = false,
}: ImageViewerProps) {
  const dispatch = useDispatch();
  const [swiper, setSwiper] = useState<SwiperType | null>(null);
  const [activeIndex, setActiveIndex] = useState(initialIndex);
  const [isSlideshow, setIsSlideshow] = useState(false);
  const [slideshowInterval, setSlideshowInterval] = useState<number | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const currentFile = files[activeIndex];

  // Only load images within this range
  const LOAD_WINDOW = 3;

  const shouldLoadImage = (index: number) => {
    return Math.abs(index - activeIndex) <= LOAD_WINDOW;
  };

  // Reset active index when initialIndex changes
  useEffect(() => {
    setActiveIndex(initialIndex);
  }, [initialIndex]);

  // Set current file for context-aware chat
  useEffect(() => {
    if (currentFile) {
      dispatch(setCurrentFile(currentFile));
    }
    return () => {
      dispatch(clearCurrentFile());
    };
  }, [currentFile, dispatch]);

  // Helper to get image URL with UUID parameter
  const getImageUrlWithUuid = (file: File) => {
    if (!file.file_path_webapp) return '';

    const uuid = localStorage.getItem('uuid');
    if (!uuid) return file.file_path_webapp;

    // Add UUID as query parameter
    const separator = file.file_path_webapp.includes('?') ? '&' : '?';
    let url = `${file.file_path_webapp}${separator}uuid=${uuid}`;

    // Build URL with proper base
    return buildUrl(url);
  };

  // Cleanup slideshow on unmount or close
  useEffect(() => {
    return () => {
      if (slideshowInterval) {
        clearInterval(slideshowInterval);
      }
    };
  }, [slideshowInterval]);

  // Handle slideshow
  const startSlideshow = (interval: number = 6000) => {
    if (slideshowInterval) {
      clearInterval(slideshowInterval);
    }

    const id = window.setInterval(() => {
      if (swiper) {
        if (swiper.isEnd) {
          swiper.slideTo(0);
        } else {
          swiper.slideNext();
        }
      }
    }, interval);

    setSlideshowInterval(id);
    setIsSlideshow(true);
  };

  const stopSlideshow = () => {
    if (slideshowInterval) {
      clearInterval(slideshowInterval);
      setSlideshowInterval(null);
    }
    setIsSlideshow(false);
  };

  // Handle slide change
  const handleSlideChange = (swiper: SwiperType) => {
    const index = swiper.activeIndex;
    setActiveIndex(index);

    // Load more if near end
    if (hasMore && files.length - index < 5 && onLoadMore) {
      onLoadMore();
    }
  };

  // Handle download
  const handleDownload = () => {
    if (!currentFile) return;

    const uuid = localStorage.getItem('uuid');
    const url = currentFile.file_path_webapp;

    if (url) {
      // Add UUID to URL
      const separator = url.includes('?') ? '&' : '?';
      const downloadUrl = buildUrl(`${url}${separator}uuid=${uuid}`);

      // Open in new tab - browser will download if server sends Content-Disposition header
      window.open(downloadUrl, '_blank');
    }
  };

  // Handle keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!open) return;

      if (e.key === 'Escape') {
        onClose();
      } else if (e.key === ' ') {
        e.preventDefault();
        if (isSlideshow) {
          stopSlideshow();
        } else {
          startSlideshow();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, isSlideshow, onClose]);

  if (!currentFile) return null;

  if (!open) return null;

  return (
    <>
      <Paper
        elevation={0}
        sx={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: sidebarOpen ? RIGHT_SIDEBAR_WIDTH : 0,
          bottom: 0,
          zIndex: 1300,
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: '#000',
          transition: 'right 0.3s',
        }}
      >
      {/* Top Bar */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: 1,
          backgroundColor: 'rgba(0,0,0,0.9)',
          borderBottom: '1px solid rgba(255,255,255,0.1)',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="h6" sx={{ color: 'white' }}>
            {currentFile.name}
          </Typography>
          <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)' }}>
            {activeIndex + 1} / {files.length}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 1, position: 'relative', zIndex: 2 }}>
          <IconButton
            onClick={() => setSidebarOpen(!sidebarOpen)}
            sx={{
              color: 'white',
              backgroundColor: 'rgba(0, 64, 128, 0.8)',
              '&:hover': {
                backgroundColor: 'rgba(0, 64, 128, 1)',
              },
            }}
          >
            {sidebarOpen ? <ChevronRightIcon /> : <ChevronLeftIcon />}
          </IconButton>
          {isSlideshow ? (
            <IconButton onClick={stopSlideshow} sx={{ color: 'white' }}>
              <StopIcon />
            </IconButton>
          ) : (
            <IconButton onClick={() => startSlideshow()} sx={{ color: 'white' }}>
              <PlayIcon />
            </IconButton>
          )}
          <IconButton onClick={handleDownload} sx={{ color: 'white' }}>
            <DownloadIcon />
          </IconButton>
          <IconButton onClick={onClose} sx={{ color: 'white' }}>
            <CloseIcon />
          </IconButton>
        </Box>
      </Box>

      {/* Image Carousel */}
      <Box
        sx={{
          flex: 1,
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          overflow: 'hidden',
          zIndex: 0,
        }}
      >
        <Swiper
          modules={[Navigation, Keyboard]}
          navigation={{
            nextEl: '.swiper-button-next',
            prevEl: '.swiper-button-prev',
          }}
          keyboard={{
            enabled: true,
          }}
          initialSlide={initialIndex}
          onSwiper={setSwiper}
          onSlideChange={handleSlideChange}
          style={{ width: '100%', height: '100%' }}
        >
          {files.map((file, index) => (
            <SwiperSlide key={file.nickname}>
              <Box
                sx={{
                  width: '100%',
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                {shouldLoadImage(index) ? (
                  <img
                    src={getImageUrlWithUuid(file)}
                    alt={file.name}
                    style={{
                      maxWidth: '100%',
                      maxHeight: '100%',
                      objectFit: 'contain',
                    }}
                  />
                ) : (
                  <Box
                    sx={{
                      width: '100%',
                      height: '100%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: 'rgba(255,255,255,0.3)'
                    }}
                  >
                    <Typography variant="body2">Image {index + 1}</Typography>
                  </Box>
                )}
              </Box>
            </SwiperSlide>
          ))}
        </Swiper>

        {/* Navigation Arrows */}
        <IconButton
          className="swiper-button-prev"
          sx={{
            position: 'absolute',
            left: 16,
            top: '50%',
            transform: 'translateY(-50%)',
            zIndex: 2,
            color: 'white',
            backgroundColor: 'rgba(0,0,0,0.5)',
            '&:hover': {
              backgroundColor: 'rgba(0,0,0,0.7)',
            },
          }}
        >
          <NavigateBefore fontSize="large" />
        </IconButton>
        <IconButton
          className="swiper-button-next"
          sx={{
            position: 'absolute',
            right: 16,
            top: '50%',
            transform: 'translateY(-50%)',
            zIndex: 2,
            color: 'white',
            backgroundColor: 'rgba(0,0,0,0.5)',
            '&:hover': {
              backgroundColor: 'rgba(0,0,0,0.7)',
            },
          }}
        >
          <NavigateNext fontSize="large" />
        </IconButton>
      </Box>

      {/* Bottom Info Bar */}
      <Box
        sx={{
          padding: 2,
          backgroundColor: 'rgba(0,0,0,0.9)',
          borderTop: '1px solid rgba(255,255,255,0.1)',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
          {currentFile.tags?.map((tag) => (
            <Chip key={tag} label={tag} size="small" sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white' }} />
          ))}
        </Stack>
        <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)' }}>
          {formatFileSize(currentFile.file_size)} • {formatDate(currentFile.file_date_long)}
        </Typography>
      </Box>
    </Paper>

    {/* Right Sidebar for chat, tags, etc. */}
    <Box
      sx={{
        position: 'fixed',
        top: 0,
        right: 0,
        bottom: 0,
        width: sidebarOpen ? RIGHT_SIDEBAR_WIDTH : 0,
        zIndex: 1301,
      }}
    >
      <RightSidebar fullscreen={true} externalOpen={sidebarOpen} onOpenChange={setSidebarOpen} />
    </Box>
    </>
  );
}
