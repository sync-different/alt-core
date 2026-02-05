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
  ToggleButton,
  ToggleButtonGroup,
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
  const [sortOrder, setSortOrder] = useState<'newest' | 'oldest'>('newest');
  const intervalRef = useRef<number | null>(null);
  const lastEventIdRef = useRef<number>(0);

  // Load events
  const loadEvents = async () => {
    try {
      const newMessages = await pullMessages('', lastEventIdRef.current);

      // Filter to only show EVENT messages
      const eventMessages = newMessages.filter(msg => msg.msg_type === 'EVENT');

      if (eventMessages.length > 0) {
        // Update the lastEventId ref with the newest message's date
        const newestEventId = Math.max(...eventMessages.map(e => e.msg_date));
        lastEventIdRef.current = newestEventId;

        // Deduplicate by msg_date before adding
        setEvents(prev => {
          const existingIds = new Set(prev.map(e => e.msg_date));
          const uniqueNewEvents = eventMessages.filter(e => !existingIds.has(e.msg_date));
          return [...prev, ...uniqueNewEvents];
        });
      }
    } catch (error) {
      console.error('Failed to load events:', error);
    }
  };

  // Format timestamp with exact date and relative time
  // e.g., "Feb 4 20:15:32 (20 hours ago)"
  const formatTimestamp = (timestamp: number): string => {
    const ts = timestamp < 10000000000 ? timestamp * 1000 : timestamp;
    const date = new Date(ts);
    const now = Date.now();
    const diff = now - ts;

    // Format exact date: "Feb 4 8:15:32 PM"
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const month = months[date.getMonth()];
    const day = date.getDate();
    const hours24 = date.getHours();
    const hours12 = hours24 % 12 || 12;
    const ampm = hours24 >= 12 ? 'PM' : 'AM';
    const minutes = date.getMinutes().toString().padStart(2, '0');
    const seconds = date.getSeconds().toString().padStart(2, '0');
    const exactDate = `${month} ${day} ${hours12}:${minutes}:${seconds} ${ampm}`;

    // Calculate relative time
    const diffSeconds = Math.floor(diff / 1000);
    const diffMinutes = Math.floor(diffSeconds / 60);
    const diffHours = Math.floor(diffMinutes / 60);
    const diffDays = Math.floor(diffHours / 24);

    let relativeTime: string;
    if (diffDays > 0) {
      relativeTime = `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
    } else if (diffHours > 0) {
      relativeTime = `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
    } else if (diffMinutes > 0) {
      relativeTime = `${diffMinutes} minute${diffMinutes > 1 ? 's' : ''} ago`;
    } else {
      relativeTime = 'Just now';
    }

    return `${exactDate} (${relativeTime})`;
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

  // Get sorted events based on sort order
  const sortedEvents = [...events].sort((a, b) => {
    return sortOrder === 'newest'
      ? b.msg_date - a.msg_date
      : a.msg_date - b.msg_date;
  });

  // Handle sort order change
  const handleSortChange = (_event: React.MouseEvent<HTMLElement>, newSort: 'newest' | 'oldest' | null) => {
    if (newSort !== null) {
      setSortOrder(newSort);
    }
  };

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', p: 2 }}>
      {/* Sort Toggle */}
      <Box sx={{ mb: 1, display: 'flex', justifyContent: 'flex-end' }}>
        <ToggleButtonGroup
          value={sortOrder}
          exclusive
          onChange={handleSortChange}
          size="small"
        >
          <ToggleButton value="newest" sx={{ textTransform: 'none', px: 1.5, py: 0.5 }}>
            Newest First
          </ToggleButton>
          <ToggleButton value="oldest" sx={{ textTransform: 'none', px: 1.5, py: 0.5 }}>
            Oldest First
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>

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
            {sortedEvents.map((event, index) => (
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
                        {formatTimestamp(event.msg_date)}
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
