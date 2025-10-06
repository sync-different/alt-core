/**
 * Chat Panel - Global chat room with real-time messaging
 */

import { useState, useEffect, useRef } from 'react';
import { useSelector } from 'react-redux';
import {
  Box,
  TextField,
  IconButton,
  Typography,
  Paper,
  List,
  ListItem,
  Button,
} from '@mui/material';
import {
  Send as SendIcon,
  Clear as ClearIcon,
  Download as DownloadIcon,
} from '@mui/icons-material';
import { pullMessages, pushMessage, clearAllChat, decodeMessageBody, type Message } from '../../services/chatApi';
import { selectCurrentFileMD5, selectVideoCurrentTime, selectCurrentFile } from '../../store/slices/viewerSlice';
import type { RootState } from '../../store/store';

export function ChatPanel() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [messageText, setMessageText] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const intervalRef = useRef<number | null>(null);

  // Check if user is admin (currently not available in auth state, defaulting to false)
  const isAdmin = false;

  // Get current file MD5 for context-aware chat
  const currentFileMD5 = useSelector((state: RootState) => selectCurrentFileMD5(state));

  // Get current file for filename in CSV export
  const currentFile = useSelector((state: RootState) => selectCurrentFile(state));

  // Get video current time for timestamp feature
  const videoCurrentTime = useSelector((state: RootState) => selectVideoCurrentTime(state));

  // Auto-scroll to bottom when new messages arrive
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  // Load messages
  const loadMessages = async () => {
    try {
      const lastMsgId = messages.length > 0
        ? messages[messages.length - 1].msg_date
        : 0;

      // If currentFileMD5 is set, fetch comments for that file (COMMENT type)
      // Otherwise fetch global chat (CHAT type)
      const newMessages = await pullMessages(currentFileMD5, lastMsgId);

      // Filter messages based on context
      // If viewing a file, show COMMENT messages
      // If in global chat, show CHAT messages
      const relevantMessages = newMessages.filter(msg =>
        currentFileMD5 ? msg.msg_type === 'COMMENT' : msg.msg_type === 'CHAT'
      );

      if (relevantMessages.length > 0) {
        setMessages(prev => [...prev, ...relevantMessages]);
        scrollToBottom();

        // Play notification sound if not focused (future enhancement)
        if (!document.hasFocus()) {
          // TODO: Play beep sound
        }
      }
    } catch (error) {
      console.error('Failed to load messages:', error);
    }
  };

  // Send message
  const handleSend = async () => {
    if (!messageText.trim()) return;

    setLoading(true);
    try {
      // If currentFileMD5 is set, send as COMMENT, otherwise send as CHAT
      const msgType = currentFileMD5 ? 'COMMENT' : 'CHAT';

      // If video is playing, prepend timestamp to message (like AngularJS implementation)
      let finalMessage = messageText;
      if (videoCurrentTime > 0 && !isNaN(videoCurrentTime)) {
        const totalSeconds = Math.floor(videoCurrentTime);
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        const timestamp = `(${minutes < 10 ? '0' + minutes : minutes}:${seconds < 10 ? '0' + seconds : seconds}) `;
        finalMessage = timestamp + messageText;
      }

      await pushMessage(currentFileMD5, msgType, finalMessage);
      setMessageText('');
      // Reload messages to show the new one
      await loadMessages();
    } catch (error) {
      console.error('Failed to send message:', error);
    } finally {
      setLoading(false);
    }
  };

  // Clear all messages (admin only)
  const handleClearAll = async () => {
    if (!window.confirm('Are you sure you want to clear all messages?')) {
      return;
    }

    try {
      await clearAllChat();
      setMessages([]);
    } catch (error) {
      console.error('Failed to clear messages:', error);
    }
  };

  // Format timestamp
  const formatTimestamp = (timestamp: number): string => {
    // Backend returns Unix timestamp in seconds or milliseconds
    // If timestamp is less than year 2000 in milliseconds, it's in seconds
    const ts = timestamp < 10000000000 ? timestamp * 1000 : timestamp;
    const date = new Date(ts);

    if (isNaN(date.getTime())) {
      return 'Unknown time';
    }

    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  // Format timestamp for CSV export (dd/MM/yyyy HH:mm)
  const formatTimestampForCSV = (timestamp: number): string => {
    const ts = timestamp < 10000000000 ? timestamp * 1000 : timestamp;
    const date = new Date(ts);

    if (isNaN(date.getTime())) {
      return '';
    }

    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');

    return `${day}/${month}/${year} ${hours}:${minutes}`;
  };

  // Download comments as CSV
  const handleDownloadComments = () => {
    if (messages.length === 0) {
      alert('No comments to download.');
      return;
    }

    let includeTime = false;
    const processedMsgs: Array<{ username: string; date: string; time: string; text: string }> = [];

    messages.forEach((msg) => {
      const raw = decodeMessageBody(msg.msg_body).replace(/;/g, ',');
      const user = msg.msg_user || 'Unknown';
      const date = formatTimestampForCSV(msg.msg_date);

      // Check if message contains video timestamps like (00:15) or (02:30)
      const timestampRegex = /\((\d{2}:\d{2})\)/g;
      if (timestampRegex.test(raw)) {
        includeTime = true;
        // Split by timestamp markers
        const parts = raw.split(/(?=\(\d{2}:\d{2}\))/);
        parts.forEach((part) => {
          const match = part.match(/^\((\d{2}:\d{2})\)\s*/);
          let time = '';
          let text = part;

          if (match) {
            time = match[1];
            text = part.replace(match[0], '');
          }

          if (text.trim()) {
            processedMsgs.push({ username: user, date, time, text: text.trim() });
          }
        });
      } else {
        // Split by newlines
        raw.split('\n').forEach((line) => {
          if (line.trim()) {
            processedMsgs.push({ username: user, date, time: '', text: line.trim() });
          }
        });
      }
    });

    // Build CSV content
    let csvContent = includeTime ? 'User;Date;Time;Comment\n' : 'User;Date;Comment\n';

    processedMsgs.forEach((item) => {
      let row = `${item.username};${item.date}`;
      if (includeTime) row += `;${item.time}`;
      row += `;${item.text}\n`;
      csvContent += row;
    });

    // Create blob and download
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const now = new Date();

    // Build filename with current file name if available
    let filename = 'comments';
    if (currentFile?.name) {
      // Remove file extension and sanitize filename
      const baseName = currentFile.name.replace(/\.[^/.]+$/, '').replace(/[^a-zA-Z0-9_-]/g, '_');
      filename = `comments_${baseName}`;
    }
    filename += `_${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}.csv`;

    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  // Reset messages when file context changes
  useEffect(() => {
    // Clear messages when switching between global chat and file comments
    setMessages([]);
  }, [currentFileMD5]);

  // Start polling on mount and when file context changes
  useEffect(() => {
    // Initial load
    loadMessages();

    // Poll every 30 seconds
    intervalRef.current = window.setInterval(() => {
      loadMessages();
    }, 30000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [currentFileMD5]);

  // Scroll to bottom when messages change
  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  return (
    <Box
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden', // Prevent scrollbar on outer container
      }}
    >
      {/* Header with download and clear buttons */}
      <Box sx={{ px: 2, pt: 2, pb: 1, display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
        {messages.length > 0 && (
          <Button
            size="small"
            startIcon={<DownloadIcon />}
            onClick={handleDownloadComments}
            variant="outlined"
          >
            Download
          </Button>
        )}
        {isAdmin && (
          <Button
            size="small"
            startIcon={<ClearIcon />}
            onClick={handleClearAll}
            color="error"
          >
            Clear All
          </Button>
        )}
      </Box>

      {/* Messages List - Only this scrolls */}
      <Paper
        sx={{
          flex: 1,
          overflow: 'auto',
          mx: 2,
          mt: 0,
          mb: 2,
          p: 2,
          backgroundColor: '#f9f9f9',
        }}
      >
        {messages.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
            No messages yet. Start a conversation!
          </Typography>
        ) : (
          <List sx={{ p: 0 }}>
            {messages.map((msg, index) => (
              <ListItem
                key={`${msg.msg_date}-${index}`}
                sx={{
                  flexDirection: 'column',
                  alignItems: 'flex-start',
                  p: 1,
                  borderBottom: '1px solid #eee',
                }}
              >
                <Typography variant="caption" color="text.secondary">
                  [{formatTimestamp(msg.msg_date)}] {msg.msg_user}
                </Typography>
                <Typography variant="body2" sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>
                  {decodeMessageBody(msg.msg_body)}
                </Typography>
              </ListItem>
            ))}
            <div ref={messagesEndRef} />
          </List>
        )}
      </Paper>

      {/* Message Input - Fixed at bottom */}
      <Box sx={{ px: 2, pb: 2, display: 'flex', gap: 1 }}>
        <TextField
          fullWidth
          size="small"
          placeholder="Type a message..."
          value={messageText}
          onChange={(e) => setMessageText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          disabled={loading}
          multiline
          maxRows={3}
        />
        <IconButton
          color="primary"
          onClick={handleSend}
          disabled={loading || !messageText.trim()}
        >
          <SendIcon />
        </IconButton>
      </Box>
    </Box>
  );
}
