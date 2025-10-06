/**
 * Playlist Panel Component
 * Right-side panel for managing and playing audio files
 * Based on the AngularJS RightBarController implementation
 */

import { useState, useRef, useEffect } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import {
  Box,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Typography,
  Paper,
  Button,
} from '@mui/material';
import {
  Close as CloseIcon,
  Delete as DeleteIcon,
  PlayArrow as PlayArrowIcon,
} from '@mui/icons-material';
import type { RootState } from '../../store/store';
import type { File } from '../../types/models';

interface PlaylistPanelProps {
  embedded?: boolean; // If true, render without Drawer wrapper
}

export function PlaylistPanel({ embedded = false }: PlaylistPanelProps = {}) {
  const playlist = useSelector((state: RootState) => state.playlist.files);
  const isOpen = useSelector((state: RootState) => state.playlist.isOpen);
  const dispatch = useDispatch();

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const audioRef = useRef<HTMLAudioElement>(null);
  const currentFile = playlist[currentIndex];

  // Get download URL bypassing proxy
  const getDownloadUrl = (file: File) => {
    if (!file.file_path_webapp) return '';

    const uuid = localStorage.getItem('uuid');
    const separator = file.file_path_webapp.includes('?') ? '&' : '?';
    let url = `${file.file_path_webapp}${separator}uuid=${uuid}`;

    // Point directly to backend (port 8081) to bypass Vite proxy
    if (url.startsWith('/cass/')) {
      url = `http://localhost:8081${url}`;
    } else if (url.includes('localhost:5173')) {
      url = url.replace('localhost:5173', 'localhost:8081');
    }

    return url;
  };

  // Auto-play when first file is added to empty playlist
  useEffect(() => {
    if (playlist.length === 1 && !isPlaying) {
      playAudio(playlist[0]);
    }
  }, [playlist.length]);

  // Auto-play next song when current finishes
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleEnded = () => {
      setIsPlaying(false);
      const nextIndex = (currentIndex + 1) % playlist.length;
      setCurrentIndex(nextIndex);
      if (playlist.length > 0) {
        playAudio(playlist[nextIndex]);
      }
    };

    audio.addEventListener('ended', handleEnded);
    return () => audio.removeEventListener('ended', handleEnded);
  }, [currentIndex, playlist]);

  // Play audio file
  const playAudio = (file: File) => {
    const audio = audioRef.current;
    if (!audio) {
      console.error('No audio element ref');
      return;
    }

    const uuid = localStorage.getItem('uuid');
    console.log('PlaylistPanel - Playing audio:', {
      file,
      file_path_webapp: file.file_path_webapp,
      uuid
    });

    // Use file_path_webapp like other media components
    let audioUrl = file.file_path_webapp || '';

    if (!audioUrl) {
      console.error('No file_path_webapp for audio file:', file);
      return;
    }

    // Add UUID parameter
    const separator = audioUrl.includes('?') ? '&' : '?';
    audioUrl = `${audioUrl}${separator}uuid=${uuid}`;

    // Point directly to backend (port 8081) to bypass Vite proxy
    // The proxy can cause issues with audio streaming
    if (audioUrl.startsWith('/cass/')) {
      audioUrl = `http://localhost:8081${audioUrl}`;
    } else if (audioUrl.includes('localhost:5173')) {
      audioUrl = audioUrl.replace('localhost:5173', 'localhost:8081');
    }

    console.log('PlaylistPanel - Audio URL:', audioUrl);

    const source = audio.querySelector('source');
    if (source) {
      source.src = audioUrl;
      audio.load();
      audio.play().catch(err => {
        console.error('Error playing audio:', err);
      });
      setIsPlaying(true);
    }
  };

  // Handle play button click on playlist item
  const handlePlayClick = (index: number) => {
    setCurrentIndex(index);
    playAudio(playlist[index]);
  };

  // Handle delete song from playlist
  const handleDelete = (index: number) => {
    const wasPlaying = isPlaying && currentIndex === index;

    if (wasPlaying) {
      audioRef.current?.pause();
      setIsPlaying(false);
    }

    dispatch({ type: 'playlist/removeFile', payload: index });

    // Adjust current index if needed
    if (currentIndex > index) {
      setCurrentIndex(currentIndex - 1);
    } else if (currentIndex === index && wasPlaying && playlist.length > 1) {
      // Play next song if we deleted the currently playing one
      const nextIndex = index >= playlist.length - 1 ? 0 : index;
      setCurrentIndex(nextIndex);
      playAudio(playlist[nextIndex]);
    }
  };

  // Clear all playlist
  const handleClearAll = () => {
    audioRef.current?.pause();
    setIsPlaying(false);
    setCurrentIndex(0);
    dispatch({ type: 'playlist/clearAll' });
  };

  // Close panel
  const handleClose = () => {
    dispatch({ type: 'playlist/togglePanel' });
  };

  // Get song title or filename
  const getSongTitle = (file: File) => {
    return file.file_artist
      ? decodeURIComponent(file.file_artist)
      : file.name;
  };

  // Get artist name
  const getArtistName = (file: File) => {
    return file.file_album
      ? decodeURIComponent(file.file_album)
      : undefined;
  };

  // Main content component
  const content = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Header - only show close button if not embedded */}
      {!embedded && (
        <Box
          sx={{
            p: 2,
            backgroundColor: '#004080',
            color: 'white',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Typography variant="h6">Playlist</Typography>
          <IconButton onClick={handleClose} sx={{ color: 'white' }}>
            <CloseIcon />
          </IconButton>
        </Box>
      )}

        {/* Playlist Items */}
        <Box sx={{ flex: 1, overflow: embedded ? 'visible' : 'auto', p: 2 }}>
          {playlist.length === 0 ? (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ textAlign: 'center', mt: 4 }}
            >
              No songs in playlist
            </Typography>
          ) : (
            <List>
              {playlist.map((file, index) => (
                <ListItem
                  key={`${file.nickname}-${index}`}
                  sx={{
                    mb: 1,
                    backgroundColor: index === currentIndex ? '#e3f2fd' : 'white',
                    borderRadius: 1,
                    border: index === currentIndex ? '2px solid #004080' : '1px solid #ddd',
                    cursor: 'pointer',
                    '&:hover': {
                      backgroundColor: index === currentIndex ? '#e3f2fd' : '#f5f5f5',
                    },
                  }}
                  onClick={() => handlePlayClick(index)}
                  secondaryAction={
                    <IconButton
                      edge="end"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDelete(index);
                      }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  }
                >
                  <Box sx={{ mr: 2 }}>
                    {index === currentIndex && isPlaying ? (
                      <PlayArrowIcon color="primary" />
                    ) : (
                      <PlayArrowIcon sx={{ color: 'transparent' }} />
                    )}
                  </Box>
                  <ListItemText
                    primary={getSongTitle(file)}
                    secondary={getArtistName(file)}
                    primaryTypographyProps={{
                      sx: {
                        fontWeight: index === currentIndex ? 'bold' : 'normal',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      },
                    }}
                  />
                </ListItem>
              ))}
            </List>
          )}
        </Box>

        {/* Audio Player */}
        {playlist.length > 0 && (
          <Paper
            sx={{
              p: 2,
              borderTop: '1px solid #ddd',
              backgroundColor: 'white',
            }}
          >
            <Box sx={{ mb: 2 }}>
              <Typography variant="body2" sx={{ fontWeight: 'bold', mb: 0.5 }}>
                Now Playing:
              </Typography>
              <Typography variant="body2" color="text.secondary" noWrap>
                {currentFile ? getSongTitle(currentFile) : 'No song selected'}
              </Typography>
              {currentFile && getArtistName(currentFile) && (
                <Typography variant="caption" color="text.secondary" noWrap>
                  {getArtistName(currentFile)}
                </Typography>
              )}
            </Box>

            <audio
              ref={audioRef}
              controls
              autoPlay
              style={{ width: '100%', marginBottom: '8px' }}
            >
              <source src="" />
              Your browser does not support the audio tag.
            </audio>

            <Box sx={{ display: 'flex', gap: 1, justifyContent: 'space-between' }}>
              <Button
                size="small"
                variant="outlined"
                onClick={handleClearAll}
                fullWidth
              >
                Clear All
              </Button>
              {currentFile && (
                <Button
                  size="small"
                  variant="outlined"
                  component="a"
                  href={getDownloadUrl(currentFile)}
                  download={currentFile.name}
                  fullWidth
                >
                  Download
                </Button>
              )}
            </Box>
          </Paper>
        )}
    </Box>
  );

  // Wrap in Drawer if not embedded
  if (embedded) {
    return content;
  }

  return (
    <Drawer
      anchor="right"
      open={isOpen}
      onClose={handleClose}
      sx={{
        '& .MuiDrawer-paper': {
          width: 400,
          backgroundColor: '#f5f5f5',
        },
      }}
    >
      {content}
    </Drawer>
  );
}
