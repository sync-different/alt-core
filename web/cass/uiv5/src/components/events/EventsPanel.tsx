/**
 * Events Panel - Activity feed showing user actions
 */

import { useState, useEffect, useRef } from 'react';
import {
  Box,
  Typography,
  Paper,
  List,
  ListItem,
  ListItemText,
  Avatar,
} from '@mui/material';
import {
  CloudUpload as UploadIcon,
  Label as TagIcon,
  Share as ShareIcon,
  Favorite as LikeIcon,
  Comment as CommentIcon,
  Event as EventIcon,
} from '@mui/icons-material';
import { pullMessages, decodeMessageBody, type Message } from '../../services/chatApi';

export function EventsPanel() {
  const [events, setEvents] = useState<Message[]>([]);
  const intervalRef = useRef<number | null>(null);

  // Load events
  const loadEvents = async () => {
    try {
      const lastEventId = events.length > 0
        ? events[events.length - 1].msg_date
        : 0;

      const newMessages = await pullMessages('', lastEventId);

      // Filter to only show EVENT messages
      const eventMessages = newMessages.filter(msg => msg.msg_type === 'EVENT');

      if (eventMessages.length > 0) {
        setEvents(prev => [...prev, ...eventMessages]);
      }
    } catch (error) {
      console.error('Failed to load events:', error);
    }
  };

  // Format relative timestamp
  const formatRelativeTime = (timestamp: number): string => {
    const ts = timestamp < 10000000000 ? timestamp * 1000 : timestamp;
    const now = Date.now();
    const diff = now - ts;

    const seconds = Math.floor(diff / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days} day${days > 1 ? 's' : ''} ago`;
    if (hours > 0) return `${hours} hour${hours > 1 ? 's' : ''} ago`;
    if (minutes > 0) return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
    return 'Just now';
  };

  // Get icon for event type
  const getEventIcon = (body: string) => {
    const decodedBody = decodeMessageBody(body).toLowerCase();

    if (decodedBody.includes('upload')) return <UploadIcon color="primary" />;
    if (decodedBody.includes('tag')) return <TagIcon color="secondary" />;
    if (decodedBody.includes('share')) return <ShareIcon color="info" />;
    if (decodedBody.includes('like')) return <LikeIcon color="error" />;
    if (decodedBody.includes('comment')) return <CommentIcon color="action" />;

    return <EventIcon color="action" />;
  };

  // Start polling on mount
  useEffect(() => {
    // Initial load
    loadEvents();

    // Poll every 30 seconds
    intervalRef.current = window.setInterval(() => {
      loadEvents();
    }, 30000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', p: 2 }}>
      {/* Events List */}
      <Paper
        sx={{
          flex: 1,
          overflow: 'auto',
          backgroundColor: '#f9f9f9',
        }}
      >
        {events.length === 0 ? (
          <Box sx={{ p: 2 }}>
            <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
              No events yet
            </Typography>
          </Box>
        ) : (
          <List sx={{ p: 0 }}>
            {events.map((event, index) => (
              <ListItem
                key={`${event.msg_date}-${index}`}
                sx={{
                  borderBottom: '1px solid #eee',
                  '&:last-child': { borderBottom: 'none' },
                }}
              >
                <Avatar sx={{ mr: 2 }}>
                  {getEventIcon(event.msg_body)}
                </Avatar>
                <ListItemText
                  primary={
                    <Typography variant="body2">
                      {decodeMessageBody(event.msg_body)}
                    </Typography>
                  }
                  secondary={
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">
                        {event.msg_user}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatRelativeTime(event.msg_date)}
                      </Typography>
                    </Box>
                  }
                />
              </ListItem>
            ))}
          </List>
        )}
      </Paper>
    </Box>
  );
}
