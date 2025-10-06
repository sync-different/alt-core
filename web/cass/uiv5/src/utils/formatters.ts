/**
 * Utility functions for formatting data
 */

/**
 * Format file size in bytes to human-readable string
 * @param bytes - Size in bytes
 * @returns Formatted string (e.g., "1.5 MB", "500 KB")
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
};

/**
 * Format timestamp to readable date string
 * @param timestamp - Unix timestamp (milliseconds), ISO date string, or custom format
 * @returns Formatted date string (e.g., "Jan 15, 2024 3:45 PM")
 */
export const formatDate = (timestamp: number | string | undefined | null): string => {
  if (!timestamp && timestamp !== 0 && timestamp !== '0') {
    return 'Unknown';
  }

  let date: Date;

  if (typeof timestamp === 'string') {
    // Handle custom backend format: "2025.07.29 11:11:57.590 PDT"
    const datePattern = /^(\d{4})\.(\d{2})\.(\d{2})\s+(.+)$/;
    const match = timestamp.match(datePattern);

    if (match) {
      const [, year, month, day, timeAndZone] = match;
      const timePart = timeAndZone.split(' ')[0];
      const isoString = `${year}-${month}-${day}T${timePart}`;
      date = new Date(isoString);
    } else {
      // Try parsing as numeric timestamp string (e.g., "1753812717590")
      const numericTimestamp = parseInt(timestamp, 10);
      if (!isNaN(numericTimestamp)) {
        date = new Date(numericTimestamp);
      } else {
        // Fall back to standard Date parsing
        date = new Date(timestamp);
      }
    }
  } else {
    // Numeric timestamp (Unix milliseconds)
    date = new Date(timestamp);
  }

  if (isNaN(date.getTime())) {
    return 'Unknown';
  }

  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  });
};

/**
 * Format timestamp to relative time string
 * @param timestamp - Unix timestamp in milliseconds
 * @returns Relative time string (e.g., "2 minutes ago", "3 days ago")
 */
export const formatRelativeTime = (timestamp: number): string => {
  const now = Date.now();
  const diff = now - timestamp;

  const seconds = Math.floor(diff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  const weeks = Math.floor(days / 7);
  const months = Math.floor(days / 30);
  const years = Math.floor(days / 365);

  if (seconds < 60) {
    return seconds === 1 ? '1 second ago' : `${seconds} seconds ago`;
  } else if (minutes < 60) {
    return minutes === 1 ? '1 minute ago' : `${minutes} minutes ago`;
  } else if (hours < 24) {
    return hours === 1 ? '1 hour ago' : `${hours} hours ago`;
  } else if (days < 7) {
    return days === 1 ? '1 day ago' : `${days} days ago`;
  } else if (weeks < 4) {
    return weeks === 1 ? '1 week ago' : `${weeks} weeks ago`;
  } else if (months < 12) {
    return months === 1 ? '1 month ago' : `${months} months ago`;
  } else {
    return years === 1 ? '1 year ago' : `${years} years ago`;
  }
};

/**
 * Format duration in seconds to readable string
 * @param seconds - Duration in seconds
 * @returns Formatted duration (e.g., "3:45", "1:23:45")
 */
export const formatDuration = (seconds: number): string => {
  if (seconds < 0) return '0:00';

  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);

  const pad = (num: number): string => num.toString().padStart(2, '0');

  if (hours > 0) {
    return `${hours}:${pad(minutes)}:${pad(secs)}`;
  } else {
    return `${minutes}:${pad(secs)}`;
  }
};

/**
 * Format percentage string
 * @param value - Percentage value (0-100) or string
 * @returns Formatted percentage (e.g., "75%")
 */
export const formatPercentage = (value: number | string): string => {
  const num = typeof value === 'string' ? parseFloat(value) : value;
  if (isNaN(num)) return '0%';
  return `${Math.round(num)}%`;
};

/**
 * Truncate string to specified length with ellipsis
 * @param str - String to truncate
 * @param maxLength - Maximum length
 * @returns Truncated string
 */
export const truncateString = (str: string, maxLength: number): string => {
  if (str.length <= maxLength) return str;
  return `${str.substring(0, maxLength - 3)}...`;
};
