/**
 * Video Player Component
 * In-page video player with HLS streaming, transcription, and comments
 */

import { useState, useRef, useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { Paper, Box, IconButton, Typography, Chip, Stack } from '@mui/material';
import {
  Close as CloseIcon,
  Download as DownloadIcon,
  Fullscreen as FullscreenIcon,
  ChevronLeft as ChevronLeftIcon,
  ChevronRight as ChevronRightIcon,
} from '@mui/icons-material';
import Hls from 'hls.js';
import type { File } from '../../types/models';
import { formatDate, formatFileSize, formatDuration } from '../../utils/formatters';
import { setCurrentFile, clearCurrentFile, setVideoCurrentTime } from '../../store/slices/viewerSlice';
import { buildUrl } from '../../utils/urlHelper';
import { RightSidebar, RIGHT_SIDEBAR_WIDTH } from '../../components/layout/RightSidebar';

interface VideoPlayerProps {
  open: boolean;
  onClose: () => void;
  file: File;
}

export function VideoPlayer({ open, onClose, file }: VideoPlayerProps) {
  const dispatch = useDispatch();
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Set current file for context-aware chat
  useEffect(() => {
    dispatch(setCurrentFile(file));
    return () => {
      dispatch(clearCurrentFile());
    };
  }, [file, dispatch]);

  // Handle Escape key to close viewer
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose]);

  // Log video player state
  useEffect(() => {
    if (import.meta.env.DEV) {
      console.log('VideoPlayer - open:', open);
      console.log('VideoPlayer - file:', file);
      console.log('VideoPlayer - video_url_webapp:', file?.video_url_webapp);
      console.log('VideoPlayer - file_path_webapp:', file?.file_path_webapp);
    }
  }, [open, file]);

  // Handle download
  const handleDownload = () => {
    if (file) {
      window.open(file.file_path_webapp, '_blank');
    }
  };

  // Handle fullscreen
  const handleFullscreen = () => {
    if (videoRef.current) {
      if (document.fullscreenElement) {
        document.exitFullscreen();
      } else {
        videoRef.current.requestFullscreen();
      }
    }
  };

  // Initialize HLS.js when component mounts or URL changes
  useEffect(() => {
    console.log('VideoPlayer useEffect triggered - open:', open, 'videoRef.current:', !!videoRef.current);

    if (!open) {
      console.log('Skipping HLS init - dialog not open');
      return;
    }

    // Wait for video element to be mounted (Dialog animation)
    const timeout = setTimeout(() => {
      if (!videoRef.current) {
        console.log('❌ Video element still not ready after timeout');
        return;
      }

      console.log('✅ Video element ready, initializing HLS...');

      // Get video URL with UUID parameter
      const uuid = localStorage.getItem('uuid');
      let videoUrl = file.video_url_webapp || file.file_path_webapp;

      console.log('Building video URL from:', { video_url_webapp: file.video_url_webapp, file_path_webapp: file.file_path_webapp });

      if (!videoUrl) {
        console.error('No video URL found for file:', file);
        return;
      }

      // Add UUID as query parameter
      const separator = videoUrl.includes('?') ? '&' : '?';
      videoUrl = `${videoUrl}${separator}uuid=${uuid}`;

      // Handle relative URLs (getvideo.m3u8) and absolute URLs
      if (videoUrl.startsWith('getvideo.m3u8') || videoUrl.startsWith('getaudio.m3u8')) {
        // Relative HLS manifest - prepend /cass/ path
        videoUrl = buildUrl(`/cass/${videoUrl}`);
      } else if (videoUrl.startsWith('/cass/')) {
        videoUrl = buildUrl(videoUrl);
      } else if (!videoUrl.startsWith('http://') && !videoUrl.startsWith('https://')) {
        videoUrl = buildUrl(videoUrl);
      }

      console.log('Final video URL:', videoUrl);

      const video = videoRef.current;

      // Clean up previous HLS instance
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }

      // Check if HLS is supported
      console.log('Hls.isSupported():', Hls.isSupported());

      if (Hls.isSupported()) {
        console.log('Creating HLS instance...');
        const hls = new Hls({
          debug: true, // Always enable debug for now
          enableWorker: true,
          lowLatencyMode: false,
          backBufferLength: 90,
        });

        hlsRef.current = hls;

        console.log('Loading source:', videoUrl);
        hls.loadSource(videoUrl);

        console.log('Attaching media to video element');
        hls.attachMedia(video);

        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          console.log('✅ HLS manifest parsed, ready to play');
          // Auto-play the video
          video.play().catch(err => {
            console.error('Failed to autoplay:', err);
          });
        });

        hls.on(Hls.Events.ERROR, (_event, data) => {
          console.error('❌ HLS error:', data);
          if (data.fatal) {
            switch (data.type) {
              case Hls.ErrorTypes.NETWORK_ERROR:
                console.error('Fatal network error, trying to recover');
                hls.startLoad();
                break;
              case Hls.ErrorTypes.MEDIA_ERROR:
                console.error('Fatal media error, trying to recover');
                hls.recoverMediaError();
                break;
              default:
                console.error('Fatal error, cannot recover');
                hls.destroy();
                break;
            }
          }
        });
      } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        // Native HLS support (Safari)
        console.log('Using native HLS support (Safari)');
        video.src = videoUrl;
        // Auto-play for Safari
        video.addEventListener('loadedmetadata', () => {
          video.play().catch(err => {
            console.error('Failed to autoplay:', err);
          });
        });
      } else {
        console.error('❌ HLS is not supported in this browser');
      }

      // Event listeners for video element
      const handleTimeUpdate = () => {
        setCurrentTime(video.currentTime);
        // Update Redux store for chat timestamp feature
        dispatch(setVideoCurrentTime(video.currentTime));
      };
      const handleDurationChange = () => setDuration(video.duration);

      video.addEventListener('timeupdate', handleTimeUpdate);
      video.addEventListener('durationchange', handleDurationChange);
    }, 100); // Wait 100ms for Dialog to mount

    // Cleanup
    return () => {
      clearTimeout(timeout);

      if (videoRef.current) {
        const video = videoRef.current;
        video.removeEventListener('timeupdate', () => {});
        video.removeEventListener('durationchange', () => {});
      }

      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, [open, file]);

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
          padding: 2,
          backgroundColor: 'rgba(0,0,0,0.9)',
          borderBottom: '1px solid rgba(255,255,255,0.1)',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="h6" sx={{ color: 'white' }}>
            {file.name}
          </Typography>
          {duration > 0 && (
            <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)' }}>
              {formatDuration(currentTime)} / {formatDuration(duration)}
            </Typography>
          )}
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
          <IconButton onClick={handleFullscreen} sx={{ color: 'white' }}>
            <FullscreenIcon />
          </IconButton>
          <IconButton onClick={handleDownload} sx={{ color: 'white' }}>
            <DownloadIcon />
          </IconButton>
          <IconButton onClick={onClose} sx={{ color: 'white' }}>
            <CloseIcon />
          </IconButton>
        </Box>
      </Box>

      {/* Video Player */}
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          overflow: 'hidden',
          padding: 2,
          position: 'relative',
          zIndex: 0,
        }}
      >
        <video
          ref={videoRef}
          controls
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'contain',
          }}
        />
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
          {file.tags?.map((tag) => (
            <Chip
              key={tag}
              label={tag}
              size="small"
              sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white' }}
            />
          ))}
        </Stack>
        <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)' }}>
          {formatFileSize(file.file_size)} • {formatDate(file.file_date_long)}
          {file.file_width && file.file_height && ` • ${file.file_width}x${file.file_height}`}
        </Typography>
      </Box>
    </Paper>

    {/* Right Sidebar for chat, transcript, etc. */}
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
