/**
 * TypeScript type definitions for Alterante data models
 * Based on WEBAPP-SPEC.md specifications
 */

export interface File {
  // Identifiers
  multiclusterid: string;        // Primary identifier (MD5 hash)
  nickname: string;              // MD5 hash (same as multiclusterid)
  md5hash: string;               // MD5 hash

  // File metadata
  file_name: string;             // File name with extension
  name: string;                  // Alias for file_name
  file_ext?: string;             // Extension (e.g., ".jpg")
  file_group: string;            // File type group (photo, music, movie, document)
  file_size: number;             // Size in bytes
  file_date: string;             // ISO date string
  file_date_long: number;        // Unix timestamp in milliseconds

  // Tags and organization
  file_tags?: string;            // Comma-separated tag names
  tags?: string[];               // Array of tag names

  // Thumbnails and paths
  file_thumbnail?: string;       // Base64 encoded thumbnail or path
  file_path: string;             // File path
  file_path_webapp?: string;     // Web-accessible file path
  video_url_webapp?: string;     // HLS video stream URL

  // Optional media-specific fields
  file_duration?: number;        // Duration in seconds (audio/video)
  file_width?: number;           // Width in pixels (images/video)
  file_height?: number;          // Height in pixels (images/video)
  file_artist?: string;          // Artist name (audio)
  file_album?: string;           // Album name (audio)
  file_year?: string;            // Release year (audio/video)
  file_transcription?: string;   // Video transcription/subtitles text
  video_transcription_webapp?: string; // Web-accessible transcription URL
}

export interface TranscriptionSegment {
  start: number;                 // Start time in seconds
  end: number;                   // End time in seconds
  text: string;                  // Transcription text
}

export interface Node {
  node_id: string;               // UUID
  node_machine: string;          // Computer/device name
  node_type: 'server' | 'client' | 'cloud';
  node_ip: string;               // IP address
  node_port: number;             // HTTP port (default 8081)
  node_nettyport_post: number;   // Upload port (default 8087)
  node_idx_percent: string;      // Index progress (e.g., "100%")
  node_backup_percent: string;   // Backup progress
  node_backuppath: string;       // Backup directory path
  node_lastping_long: number;    // Last seen (Unix timestamp)

  // Disk usage fields
  node_diskfree: number;         // Free disk space in bytes
  node_disktotal: number;        // Total disk space in bytes

  // Optional fields
  node_status?: 'online' | 'offline';
  node_version?: string;         // Software version
}

export interface Tag {
  tagname: string;               // Tag name
  tagcnt: number;                // Number of files with this tag
}

export interface Message {
  msg_date: string;              // Unix timestamp as string
  msg_type: 'CHAT' | 'COMMENT' | 'LIKE' | 'EVENT' | 'FB';
  msg_user: string;              // Username
  msg_body: string;              // Base64 encoded message content
}

export interface Cluster {
  cluster: string;               // UUID
  name: string;                  // Display name
  user: string;                  // Username for authentication
  password: string;              // Password (encrypted)
  uuid: string;                  // User UUID
}

export interface BackupRule {
  rule: number;                  // Rule index (0, 1, 2, ...)
  extensions: string[];          // Array of file extensions (e.g., [".jpg", ".png"])
  devices: Array<{
    node_id: string;             // Target device UUID
    node_type: string;           // Device type
  }>;
}

export interface User {
  username: string;              // Unique username
  email: string;                 // Email address
}

// API Response wrapper types
export interface ApiResponse<T> {
  data: T;
  status: string;
  message?: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}
