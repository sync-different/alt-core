/**
 * Transcript Panel - Shows video transcription with auto-scrolling
 */

import { useEffect, useRef, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  Box,
  Typography,
  List,
  ListItem,
  CircularProgress,
  LinearProgress,
} from '@mui/material';
import { selectVideoCurrentTime, selectCurrentFile, seekToTime } from '../../store/slices/viewerSlice';
import type { RootState, AppDispatch } from '../../store/store';
import type { TranscriptionSegment } from '../../types/models';
import { buildUrl } from '../../utils/urlHelper';

type PipelineStatus =
  | 'unknown'        // before first poll
  | 'not_started'    // streaming dir doesn't exist yet
  | 'encoding'       // ffmpeg producing HLS segments
  | 'transcribing'   // audio.aac extracted, whisper running
  | 'complete'       // audio.json has segments
  | 'error'          // audio.json contains an error
  | 'not_available'; // file isn't a video / no transcription expected

const POLL_INTERVAL_MS = 4000;

export function TranscriptPanel() {
  const dispatch = useDispatch<AppDispatch>();
  const currentFile = useSelector((state: RootState) => selectCurrentFile(state));
  const videoCurrentTime = useSelector((state: RootState) => selectVideoCurrentTime(state));
  const [transcription, setTranscription] = useState<TranscriptionSegment[]>([]);
  const [loading, setLoading] = useState(false);
  const [pipelineStatus, setPipelineStatus] = useState<PipelineStatus>('unknown');
  const [encodingProgress, setEncodingProgress] = useState<number>(0);
  const [errorMessage, setErrorMessage] = useState<string>('');
  const activeSegmentRef = useRef<HTMLDivElement>(null);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load transcription when file changes
  useEffect(() => {
    // Cancel any in-flight polling from the previous file.
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    setTranscription([]);
    setEncodingProgress(0);
    setErrorMessage('');

    if (!currentFile) {
      setPipelineStatus('not_available');
      return;
    }

    // If file has transcription data directly
    if (currentFile.file_transcription) {
      try {
        const parsed = parseTranscription(currentFile.file_transcription);
        setTranscription(parsed);
        setPipelineStatus('complete');
      } catch (error) {
        console.error('Failed to parse transcription:', error);
        setPipelineStatus('error');
        setErrorMessage('Failed to parse transcription data');
      }
    }
    // If file has transcription URL
    else if (currentFile.video_transcription_webapp) {
      loadTranscriptionFromUrl(currentFile.video_transcription_webapp);
    }
    // Try to load from gettranslate_json.fn endpoint using MD5; poll status
    // endpoint while not yet complete so the UI shows real progress.
    else if (currentFile.nickname) {
      checkStatusAndLoad(currentFile.nickname);
    }
    // No transcription available
    else {
      setPipelineStatus('not_available');
    }

    return () => {
      if (pollTimerRef.current) {
        clearTimeout(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [currentFile]);

  // Auto-scroll to active segment
  useEffect(() => {
    if (activeSegmentRef.current) {
      activeSegmentRef.current.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      });
    }
  }, [videoCurrentTime]);

  // Polls gettranscribestatus.fn until the pipeline reaches a terminal state.
  // While encoding/transcribing, updates progress; on complete, fetches the
  // transcript; on error, surfaces the message.
  const checkStatusAndLoad = async (md5: string) => {
    try {
      const url = buildUrl(`/cass/gettranscribestatus.fn?sMD5=${md5}`);
      const response = await fetch(url, { credentials: 'include' });
      if (!response.ok) {
        setPipelineStatus('not_available');
        return;
      }
      const json = await response.json();
      const status = (json?.status || 'unknown') as PipelineStatus;
      setPipelineStatus(status);

      if (status === 'encoding' && typeof json.progress === 'number') {
        setEncodingProgress(json.progress);
      }

      if (status === 'complete') {
        await loadTranscriptionFromMD5(md5);
        return;
      }

      if (status === 'error') {
        setErrorMessage(json.errorMessage || 'Transcription failed');
        return;
      }

      if (status === 'encoding' || status === 'transcribing' || status === 'unknown') {
        // Schedule next poll. Stored in a ref so the cleanup useEffect can cancel.
        pollTimerRef.current = setTimeout(() => checkStatusAndLoad(md5), POLL_INTERVAL_MS);
      }
      // not_started / not_available: stop polling — there's no encoding job in progress.
    } catch (error) {
      console.error('Failed to check transcription status:', error);
      setPipelineStatus('not_available');
    }
  };

  const loadTranscriptionFromMD5 = async (md5: string) => {
    setLoading(true);
    try {
      const url = buildUrl(`/cass/gettranslate_json.fn?sMD5=${md5}`);

      const response = await fetch(url, {
        credentials: 'include', // Include cookies for authentication
      });

      if (!response.ok) {
        setPipelineStatus('not_available');
        setTranscription([]);
        return;
      }

      const json = await response.json();

      // gettranslate_json.fn now returns one of:
      //   {"segments":[...]}                                 — happy path
      //   {"segments":[],"status":"in_progress"}             — curl mid-flight
      //   {"segments":[],"status":"error","errorMessage":""} — LocalAI error
      //   {"segments":[],"status":"not_available"}           — no audio.json
      if (json?.status === 'error') {
        setPipelineStatus('error');
        setErrorMessage(json.errorMessage || 'Transcription failed');
        setTranscription([]);
      } else if (json?.status === 'in_progress') {
        setPipelineStatus('transcribing');
        setTranscription([]);
        // Re-enter status polling so we update when whisper finishes.
        pollTimerRef.current = setTimeout(() => checkStatusAndLoad(md5), POLL_INTERVAL_MS);
      } else if (json && json.segments && Array.isArray(json.segments) && json.segments.length > 0) {
        const segments: TranscriptionSegment[] = json.segments.map((item: any) => ({
          start: parseFloat(item.start || 0) / 1000000000, // Convert nanoseconds to seconds
          end: parseFloat(item.end || 0) / 1000000000,     // Convert nanoseconds to seconds
          text: item.text || '',
        }));
        setTranscription(segments);
        setPipelineStatus('complete');
      } else {
        setPipelineStatus('not_available');
        setTranscription([]);
      }
    } catch (error) {
      console.error('Failed to load transcription from MD5:', error);
      setPipelineStatus('not_available');
      setTranscription([]);
    } finally {
      setLoading(false);
    }
  };

  const loadTranscriptionFromUrl = async (url: string) => {
    setLoading(true);
    try {
      // Build URL with proper base
      const fullUrl = buildUrl(url);

      const response = await fetch(fullUrl, {
        credentials: 'include', // Include cookies for authentication
      });
      const text = await response.text();

      const parsed = parseTranscription(text);
      setTranscription(parsed);
    } catch (error) {
      console.error('Failed to load transcription:', error);
    } finally {
      setLoading(false);
    }
  };

  // Parse transcription text (VTT or simple format)
  const parseTranscription = (text: string): TranscriptionSegment[] => {
    const segments: TranscriptionSegment[] = [];

    // Try to parse as WebVTT format
    if (text.includes('WEBVTT') || text.includes('-->')) {
      const lines = text.split('\n');
      let currentSegment: Partial<TranscriptionSegment> = {};

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();

        // Skip empty lines and headers
        if (!line || line.startsWith('WEBVTT') || line.startsWith('NOTE')) {
          continue;
        }

        // Timestamp line (e.g., "00:00:01.000 --> 00:00:05.000")
        if (line.includes('-->')) {
          const [startStr, endStr] = line.split('-->').map(s => s.trim());
          currentSegment.start = parseVTTTime(startStr);
          currentSegment.end = parseVTTTime(endStr);
        }
        // Text line
        else if (currentSegment.start !== undefined && currentSegment.end !== undefined) {
          currentSegment.text = line;
          segments.push(currentSegment as TranscriptionSegment);
          currentSegment = {};
        }
      }
    }
    // Simple format: one line per timestamp (e.g., "0.5: Hello world")
    else {
      const lines = text.split('\n');
      for (const line of lines) {
        const match = line.match(/^(\d+(?:\.\d+)?)\s*[:|-]\s*(.+)$/);
        if (match) {
          const start = parseFloat(match[1]);
          segments.push({
            start,
            end: start + 5, // Default 5 second duration
            text: match[2].trim(),
          });
        }
      }
    }

    return segments;
  };

  // Parse VTT timestamp (e.g., "00:00:01.000" or "01:23.456")
  const parseVTTTime = (timeStr: string): number => {
    const parts = timeStr.split(':');
    if (parts.length === 3) {
      // HH:MM:SS.mmm
      const [hours, minutes, seconds] = parts;
      return (
        parseFloat(hours) * 3600 +
        parseFloat(minutes) * 60 +
        parseFloat(seconds)
      );
    } else if (parts.length === 2) {
      // MM:SS.mmm
      const [minutes, seconds] = parts;
      return parseFloat(minutes) * 60 + parseFloat(seconds);
    } else {
      // SS.mmm
      return parseFloat(timeStr);
    }
  };

  // Format time for display
  const formatTime = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  };

  // Find active segment based on video time
  const getActiveSegmentIndex = (): number => {
    return transcription.findIndex(
      (seg) => videoCurrentTime >= seg.start && videoCurrentTime < seg.end
    );
  };

  const activeIndex = getActiveSegmentIndex();

  if (!currentFile) {
    return (
      <Box sx={{ p: 2, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          No file selected
        </Typography>
      </Box>
    );
  }

  if (loading) {
    return (
      <Box sx={{ p: 2, display: 'flex', justifyContent: 'center' }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (transcription.length === 0) {
    if (pipelineStatus === 'encoding') {
      const pct = Math.round(encodingProgress * 100);
      return (
        <Box sx={{ p: 3, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
            Encoding video for streaming…
          </Typography>
          <LinearProgress
            variant={encodingProgress > 0 ? 'determinate' : 'indeterminate'}
            value={pct}
            sx={{ mb: 1 }}
          />
          {encodingProgress > 0 && (
            <Typography variant="caption" color="text.secondary">
              {pct}% complete
            </Typography>
          )}
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Transcription will start once encoding finishes.
          </Typography>
        </Box>
      );
    }
    if (pipelineStatus === 'transcribing') {
      return (
        <Box sx={{ p: 3, textAlign: 'center' }}>
          <CircularProgress size={24} sx={{ mb: 1.5 }} />
          <Typography variant="body2" color="text.secondary">
            Transcribing audio…
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            This can take a few minutes for long videos.
          </Typography>
        </Box>
      );
    }
    if (pipelineStatus === 'error') {
      return (
        <Box sx={{ p: 3, textAlign: 'center' }}>
          <Typography variant="body2" color="error" sx={{ mb: 1 }}>
            Transcription failed
          </Typography>
          {errorMessage && (
            <Typography variant="caption" color="text.secondary">
              {errorMessage}
            </Typography>
          )}
        </Box>
      );
    }
    return (
      <Box sx={{ p: 2, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          No transcription data available
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <List sx={{ flex: 1, overflow: 'auto', p: 0 }}>
        {transcription.map((segment, index) => (
          <ListItem
            key={index}
            ref={index === activeIndex ? activeSegmentRef as any : null}
            onClick={() => dispatch(seekToTime(segment.start))}
            sx={{
              flexDirection: 'column',
              alignItems: 'flex-start',
              cursor: 'pointer',
              borderLeft: index === activeIndex ? '3px solid' : 'none',
              borderColor: 'primary.main',
              bgcolor: index === activeIndex ? 'action.selected' : 'transparent',
              transition: 'background-color 0.2s',
              py: 1.5,
              px: 2,
              '&:hover': {
                bgcolor: 'action.hover',
              },
            }}
          >
            <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5 }}>
              {formatTime(segment.start)}
            </Typography>
            <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
              {segment.text}
            </Typography>
          </ListItem>
        ))}
      </List>
    </Box>
  );
}
