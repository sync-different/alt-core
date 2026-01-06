/**
 * Comment Timeline - Visual timeline showing comment markers on video progress bar
 */

import { Box, Tooltip } from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';
import { seekToTime, selectVideoCurrentTime, selectVideoDuration } from '../../store/slices/viewerSlice';
import type { RootState } from '../../store/store';

interface CommentMarker {
  timestamp: number; // seconds
  text: string;
  user: string;
}

interface CommentTimelineProps {
  markers: CommentMarker[];
  onMarkerClick?: (timestamp: number) => void;
  selectedTimestamp?: number | null;
}

export function CommentTimeline({ markers, onMarkerClick, selectedTimestamp }: CommentTimelineProps) {
  const dispatch = useDispatch();
  const currentTime = useSelector((state: RootState) => selectVideoCurrentTime(state));
  const duration = useSelector((state: RootState) => selectVideoDuration(state));

  // Don't render if no duration or no markers
  if (duration <= 0 || markers.length === 0) {
    return null;
  }

  const handleMarkerClick = (timestamp: number) => {
    dispatch(seekToTime(timestamp));
    onMarkerClick?.(timestamp);
  };

  const handleBarClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const percentage = clickX / rect.width;
    const seekTime = percentage * duration;
    dispatch(seekToTime(seekTime));
  };

  // Calculate progress percentage
  const progressPercent = (currentTime / duration) * 100;

  return (
    <Box sx={{ px: 2, py: 1 }}>
      {/* Timeline container */}
      <Box
        onClick={handleBarClick}
        sx={{
          position: 'relative',
          height: 24,
          backgroundColor: '#e0e0e0',
          borderRadius: 1,
          cursor: 'pointer',
          overflow: 'visible',
          '&:hover': {
            backgroundColor: '#d0d0d0',
          },
        }}
      >
        {/* Progress bar */}
        <Box
          sx={{
            position: 'absolute',
            top: 0,
            left: 0,
            height: '100%',
            width: `${progressPercent}%`,
            backgroundColor: 'rgba(25, 118, 210, 0.3)',
            borderRadius: 1,
            pointerEvents: 'none',
          }}
        />

        {/* Current time indicator */}
        <Box
          sx={{
            position: 'absolute',
            top: 0,
            left: `${progressPercent}%`,
            width: 2,
            height: '100%',
            backgroundColor: '#1976d2',
            transform: 'translateX(-50%)',
            pointerEvents: 'none',
          }}
        />

        {/* Comment markers */}
        {markers.map((marker, index) => {
          const position = (marker.timestamp / duration) * 100;
          const isSelected = selectedTimestamp === marker.timestamp;
          return (
            <Tooltip
              key={`marker-${index}-${marker.timestamp}`}
              title={
                <Box>
                  <Box sx={{ fontWeight: 'bold' }}>{marker.user}</Box>
                  <Box>{formatTime(marker.timestamp)}: {marker.text}</Box>
                </Box>
              }
              arrow
              placement="top"
            >
              <Box
                onClick={(e) => {
                  e.stopPropagation();
                  handleMarkerClick(marker.timestamp);
                }}
                sx={{
                  position: 'absolute',
                  top: '50%',
                  left: `${position}%`,
                  transform: isSelected ? 'translate(-50%, -50%) scale(1.3)' : 'translate(-50%, -50%)',
                  width: 12,
                  height: 12,
                  backgroundColor: isSelected ? '#e65100' : '#ff9800',
                  borderRadius: '50%',
                  border: isSelected ? '3px solid #fff' : '2px solid #fff',
                  boxShadow: isSelected ? '0 2px 6px rgba(0,0,0,0.5)' : '0 1px 3px rgba(0,0,0,0.3)',
                  cursor: 'pointer',
                  zIndex: isSelected ? 2 : 1,
                  '&:hover': {
                    backgroundColor: '#f57c00',
                    transform: 'translate(-50%, -50%) scale(1.2)',
                  },
                  transition: 'transform 0.1s, background-color 0.1s, box-shadow 0.1s',
                }}
              />
            </Tooltip>
          );
        })}
      </Box>

      {/* Time labels */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          mt: 0.5,
          fontSize: '0.7rem',
          color: 'text.secondary',
        }}
      >
        <span>{formatTime(currentTime)}</span>
        <span>{markers.length} comment{markers.length !== 1 ? 's' : ''}</span>
        <span>{formatTime(duration)}</span>
      </Box>
    </Box>
  );
}

// Helper function to format seconds as MM:SS
function formatTime(seconds: number): string {
  if (isNaN(seconds) || seconds < 0) return '00:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}
