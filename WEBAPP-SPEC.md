# Alterante Web Application - Complete Specification

**Version:** UI v3
**Technology Stack (Current):** AngularJS 1.x, jQuery 3.7.1, Bootstrap 4.6.2
**Purpose:** This document provides a comprehensive specification of the Alterante web application to facilitate migration to React.

---

## Table of Contents

1. [Application Overview](#1-application-overview)
2. [Architecture & Routing](#2-architecture--routing)
3. [Complete API Reference](#3-complete-api-reference)
4. [Data Models](#4-data-models)
5. [Pages & Views](#5-pages--views)
6. [Modals & Dialogs](#6-modals--dialogs)
7. [UI Components](#7-ui-components)
8. [State Management](#8-state-management)
9. [User Flows](#9-user-flows)
10. [Special Features](#10-special-features)
11. [Third-Party Dependencies](#11-third-party-dependencies)
12. [React Migration Recommendations](#12-react-migration-recommendations)

---

## 1. Application Overview

### Purpose
Alterante is a decentralized file management system that enables users to discover, organize, backup, and access files across multiple distributed nodes and clusters.

### Core Capabilities
- **File Management**: Browse, search, filter files by type and date range
- **Media Playback**: View images, play audio/video with HLS streaming
- **Social Features**: Comments, likes, sharing, real-time chat
- **Multi-Cluster**: Query and manage files across multiple remote clusters
- **Backup Management**: Configure backup rules for file types to target devices
- **Tagging System**: Organize files with tags, share by tags
- **File Upload**: Chunked upload with progress tracking

### Current Stack
- **Frontend**: AngularJS 1.x, jQuery 3.7.1, Bootstrap 4.6.2
- **Backend**: Java-based rtserver (Netty HTTP server)
- **Location**: `/web/cass/uiv3/`

---

## 2. Architecture & Routing

### 2.1 Route Definitions

| Route | Template | Controller | Purpose |
|-------|----------|------------|---------|
| `/MyFiles/:ftype/:range` | partialMyFiles.htm | MyFilesController | List view of files with filters |
| `/MyFiles/grid/:ftype/:range` | partialMyFilesGrid.htm | MyFilesController | Grid view of files |
| `/Home` | partialHome.htm | HomeController | Dashboard with device info |
| `/Backup` | partialBackup.htm | BackupController | Backup configuration |
| `/Shares` | partialShares.htm | SharesController | Share management (iframe) |
| `/MultiCluster` | partialMultiCluster.htm | MultiClusterController | Multi-cluster query interface |
| `/MultiCluster/:ftype/:range` | partialMultiCluster.htm | MultiClusterController | Multi-cluster with filters |

**Default Route:** Redirects to `/MyFiles`

### 2.2 URL Parameters

**`:ftype` - File Type Filter**
- `.all` - All files
- `.photo` - Images (jpg, jpeg, png, gif, bmp, tiff)
- `.music` - Audio files (mp3, m4a, wav, wma, aac)
- `.video` - Videos (mp4, mov, avi, mkv)
- `.document` - All documents
- `.doc`, `.xls`, `.ppt`, `.pdf` - Specific document types

**`:range` - Time Range Filter**
- `1` - Today (24 hours)
- `3` - Past 3 days
- `7` - This week
- `14` - Past 2 weeks
- `30` - Past 30 days
- `365` - This year
- `.all` - All time

### 2.3 Application Structure

```
web/cass/uiv3/
├── indexv2.htm              # Login page
├── home.htm                 # Main application shell
├── js/
│   ├── app.js              # Route configuration
│   ├── appconf.js          # App config, directives, filters
│   ├── alterante.js        # Main application logic
│   └── controllers/
│       ├── homecontroller.js
│       ├── myfilescontroller.js
│       ├── backupcontroller.js
│       ├── sharescontroller.js
│       ├── multiclustercontroller.js
│       ├── filtercontroller.js
│       └── rigthbarcontroller.js
├── partials/
│   ├── partialHome.htm
│   ├── partialMyFiles.htm
│   ├── partialMyFilesGrid.htm
│   ├── partialBackup.htm
│   ├── partialShares.htm
│   ├── partialMultiCluster.htm
│   ├── partialModals.htm
│   └── partialModalsCluster.htm
├── css/
├── img/
└── bootstrap-4.6.2/
```

---

## 3. Complete API Reference

### 3.1 Authentication

#### Login
```
POST /cass/login.fn
Parameters:
  - boxuser: string (username)
  - boxpass: string (password)
  - cluster: string (optional, cluster ID)
Response:
  - HTML with error span or success (redirects to home.htm)
```

#### Session
```
Cookie: uuid (session identifier)
Expires: On browser close
```

### 3.2 File Query & Search

#### Main File Query
```
GET /cass/query.fn
Parameters:
  - ftype: string (file type filter)
  - foo: string (search query)
  - days: number (time range in days)
  - view: "json" (response format)
  - numobj: number (max results, default 100)
  - date: number (pagination offset, Unix timestamp)
  - order: "Asc" | "Desc" (sort direction)
  - screenSize: number (thumbnail size, e.g., 160)
  - cluster: string (optional, cluster ID)
Response:
  {
    files: File[],
    hasMore: boolean
  }
```

#### Search Suggestions
```
GET /cass/suggest.fn
Parameters:
  - ftype: string
  - days: number
  - foo: string (search prefix)
  - view: "json"
  - numobj: number (max suggestions, default 10)
Response:
  {
    suggestions: Array<{
      name: string,
      file_ext: string,
      file_group: string
    }>
  }
```

#### Sidebar Statistics
```
GET /cass/sidebar.fn
Parameters:
  - ftype: string
  - foo: string
  - days: number
Response:
  {
    stats: {
      all: number,
      photo: number,
      music: number,
      video: number,
      document: number,
      // Time range counts
      day1: number,
      day3: number,
      day7: number,
      day14: number,
      day30: number,
      day365: number,
      dayall: number
    }
  }
```

### 3.3 File Operations

#### File Upload (Chunked)
```
POST /cass/file
Content-Type: multipart/form-data
Parameters:
  - file: binary (file chunk)
  - filechunk_size: number (chunk size in bytes)
  - filechunk_offset: number (offset for this chunk)
Response:
  - Success: 200 OK
  - Failure: Error message
```

#### File Download
```
GET {file_path_webapp}?uuid={user_uuid}&filechunk_size={size}&filechunk_offset={offset}
Parameters:
  - uuid: string (user session ID)
  - filechunk_size: number (optional, for chunked download, default 10485760 = 10MB)
  - filechunk_offset: number (optional, starting offset)
Response:
  - Binary file data
  - Content-Length header
  - Content-Type header
```

### 3.4 Tags

#### Get All Tags
```
GET /cass/gettags_webapp.fn
Parameters:
  - multiclusterid: string (optional, for cluster-specific tags)
Response:
  {
    tags: Array<{
      tagname: string,
      tagcnt: number
    }>
  }
```

#### Apply/Remove Tags
```
GET /cass/applytags.fn
Parameters:
  - tag: string (tag name)
  - {md5}: "on" (one parameter per file MD5)
  - DeleteTag: string (optional, tag to delete)
  - multiclusterid: string (optional)
Response:
  - "OK" or error message
```

### 3.5 Sharing

#### Get Users List
```
GET /cass/getusersandemail.fn
Response:
  {
    users: Array<{
      username: string,
      email: string
    }>
  }
```

#### Get Share Settings
```
GET /cass/getsharesettingstag.fn
Parameters:
  - sharetype: "TAG"
  - sharekey: string (tag name)
Response:
  {
    shared_users: string[] (usernames)
  }
```

#### Create Share
```
GET /cass/doshare_webapp.fn
Parameters:
  - sharetype: "TAG"
  - shareusers: string (comma-separated usernames)
  - sharekey: string (tag name)
Response:
  {
    token: string (share token)
  }
```

#### Get Invitation Email Template
```
GET /cass/invitation_webapp.fn
Parameters:
  - sharetype: "TAG"
  - sharekey: string
Response:
  {
    subject: string,
    body: string (HTML email template)
  }
```

#### Add User
```
GET /cass/adduser.fn
Parameters:
  - boxuser: string (username)
  - boxpass: string (password)
  - useremail: string (email address)
Response:
  - "OK" or error message
```

### 3.6 Comments & Chat

#### Pull Messages (Comments/Chat)
```
GET /cass/chat_pull.fn
Parameters:
  - md5: string (file hash, or empty for global chat)
  - msg_from: number (last message ID, or 0 for all)
  - multiclusterid: string (optional)
Response:
  {
    messages: Array<{
      msg_date: string (Unix timestamp),
      msg_type: "CHAT" | "COMMENT" | "LIKE" | "EVENT" | "FB",
      msg_user: string,
      msg_body: string (base64 encoded)
    }>
  }
```

#### Send Message
```
GET /cass/chat_push.fn
Parameters:
  - md5: string (file hash, or empty for global chat)
  - msg_user: string (username)
  - msg_from: number (message ID)
  - msg_type: "CHAT" | "COMMENT" | "LIKE" | "EVENT"
  - msg_body: string (base64 encoded message)
  - multiclusterid: string (optional)
Response:
  - "OK"
```

#### Clear All Chats
```
GET /cass/chat_clear.fn
Response:
  - "OK"
```

### 3.7 Multi-Cluster Management

#### List Clusters
```
GET /cass/getmulticlusters.fn
Response:
  {
    clusters: Array<{
      cluster: string (UUID),
      name: string,
      user: string,
      password: string,
      uuid: string (user UUID)
    }>
  }
```

#### Add Cluster
```
GET /cass/addmulticluster.fn
Parameters:
  - multiclusteruser: string
  - multiclusterpassword: string
  - multiclusterid: string (cluster URL)
  - multiclustername: string (display name)
Response:
  - "OK" or error message
```

#### Remove Cluster
```
GET /cass/removemulticluster.fn
Parameters:
  - multiclusterid: string (cluster UUID)
Response:
  - "OK"
```

#### Query Cluster
```
GET /cass/querymulticluster.fn
Parameters:
  - multiclusterid: string
  - foo: string (search query)
  - ftype: string
  - days: number
  - date: number (pagination offset)
Response:
  {
    files: File[],
    hasMore: boolean
  }
```

#### Save Cluster Login
```
GET /cass/saveloginmulticluster.fn
Parameters:
  - multiclustername: string
  - multiclusteruser: string
Response:
  - "OK"
```

### 3.8 Backup Configuration

#### Get File Extensions
```
GET /cass/getextensions.fn
Response:
  {
    groups: Array<{
      name: string (e.g., "Images"),
      extensions: string[]
    }>
  }
```

#### Get Backup Configuration
```
GET /cass/getbackupconfig.fn
Response:
  {
    rules: Array<{
      rule: number,
      extensions: string[],
      devices: Array<{
        node_id: string,
        node_type: string
      }>
    }>
  }
```

#### Save Backup Configuration
```
POST /backupconfig.c
Content-Type: application/json
Body:
  {
    rules: Array<{
      rule: number,
      extensions: string[],
      devices: Array<{
        node_id: string,
        node_type: string
      }>
    }>
  }
Response:
  - "OK"
```

### 3.9 System & Nodes

#### Get Node Information
```
GET /cass/nodeinfo.fn
Response:
  {
    nodes: Array<{
      node_id: string (UUID),
      node_machine: string,
      node_type: "server" | "client" | "cloud",
      node_ip: string,
      node_port: number,
      node_nettyport_post: number,
      node_idx_percent: string (e.g., "75%"),
      node_backup_percent: string,
      node_backuppath: string,
      node_lastping_long: number (Unix timestamp),
      node_diskfree: number,
      node_disktotal: number
    }>
  }
```

#### Get Server Property
```
GET /cass/serverproperty.fn
Parameters:
  - property: string (property name, e.g., "allowremote")
Response:
  - Property value as string
```

#### Update Server Property
```
GET /cass/serverupdateproperty.fn
Parameters:
  - property: string
  - pvalue: string (new value)
Response:
  - "OK"
```

#### Get Remote EULA
```
GET /cass/getremoteeula.fn
Response:
  - HTML string (EULA text)
```

### 3.10 Media & Streaming

#### HLS Video Stream
```
GET /cass/{video_url_webapp}
Parameters:
  - uuid: string (user session ID)
  - multiclusterid: string (optional)
Response:
  - HLS manifest (.m3u8)
```

#### Get Video Transcription
```
GET /cass/gettranslate_json.fn
Parameters:
  - sMD5: string (video file MD5)
Response:
  {
    segments: Array<{
      start: number (seconds),
      end: number,
      text: string
    }>
  }
```

### 3.11 Social Integration

#### Publish to Facebook
```
GET /cass/fbpublish.fn
Parameters:
  - md5: string (file hash)
  - fbtext: string (post caption)
  - fbtoken: string (Facebook access token)
Response:
  - "OK" or error message
```

---

## 4. Data Models

### 4.1 File Object

```typescript
interface File {
  // Identifiers
  nickname: string;              // MD5 hash
  name: string;                  // File name with extension

  // File metadata
  file_ext: string;             // Extension (e.g., ".jpg")
  file_group: string;           // "photo" | "music" | "movie" | "document"
  file_size: number;            // Size in bytes
  file_date: string;            // ISO date string
  file_date_long: number;       // Unix timestamp in milliseconds
  file_tags: string;            // Comma-separated tags

  // Display
  file_thumbnail: string;        // Base64 encoded thumbnail image

  // URLs
  file_path_webapp: string;      // Download URL
  file_remote_webapp: string;    // Open in system URL
  file_folder_webapp: string;    // Open folder URL

  // Audio specific
  audio_url?: string;            // Local audio stream URL
  audio_url_remote?: string;     // Remote audio stream URL
  song_title?: string;           // ID3 tag
  song_artist?: string;          // ID3 tag
  song_album?: string;           // ID3 tag

  // Video specific
  video_url_webapp?: string;     // HLS video stream URL

  // Image specific
  img_height?: number;           // Image height in pixels
  img_width?: number;            // Image width in pixels
}
```

### 4.2 Node/Device Object

```typescript
interface Node {
  node_id: string;              // UUID
  node_machine: string;         // Computer/device name
  node_type: "server" | "client" | "cloud";
  node_ip: string;              // IP address
  node_port: number;            // HTTP port (default 8081)
  node_nettyport_post: number;  // Upload port (default 8087)
  node_idx_percent: string;     // Index progress (e.g., "100%")
  node_backup_percent: string;  // Backup progress
  node_backuppath: string;      // Backup directory path
  node_lastping_long: number;   // Last seen (Unix timestamp)
  node_diskfree: number;        // Free disk space in bytes
  node_disktotal: number;       // Total disk space in bytes
}
```

### 4.3 Tag Object

```typescript
interface Tag {
  tagname: string;              // Tag name
  tagcnt: number;               // Number of files with this tag
}
```

### 4.4 Message Object

```typescript
interface Message {
  msg_date: string;             // Unix timestamp as string
  msg_type: "CHAT" | "COMMENT" | "LIKE" | "EVENT" | "FB";
  msg_user: string;             // Username
  msg_body: string;             // Base64 encoded message content
}
```

### 4.5 Cluster Object

```typescript
interface Cluster {
  cluster: string;              // UUID
  name: string;                 // Display name
  user: string;                 // Username for authentication
  password: string;             // Password (encrypted)
  uuid: string;                 // User UUID
}
```

### 4.6 Backup Rule Object

```typescript
interface BackupRule {
  rule: number;                 // Rule index (0, 1, 2, ...)
  extensions: string[];         // Array of file extensions (e.g., [".jpg", ".png"])
  devices: Array<{
    node_id: string;            // Target device UUID
    node_type: string;          // Device type
  }>;
}
```

### 4.7 User Object

```typescript
interface User {
  username: string;             // Unique username
  email: string;                // Email address
}
```

---

## 5. Pages & Views

### 5.1 Login Page (`indexv2.htm`)

**Route:** `/cass/uiv3/indexv2.htm`

**Purpose:** User authentication

**Features:**
- Username/password input
- Remember me checkbox (stores credentials in localStorage)
- Forgot password link (placeholder)
- Error message display
- Support for cluster parameter in URL

**API Calls:**
- `POST /cass/login.fn`

**User Flow:**
1. User enters credentials
2. Optionally checks "Remember password"
3. Clicks "Sign In"
4. On success: Redirect to `home.htm`
5. On failure: Show error message

---

### 5.2 Home Page (`/Home`)

**Template:** `partialHome.htm`
**Controller:** `HomeController`

**Purpose:** Dashboard showing all connected devices and their status

**Features:**
- Display all nodes (servers, clients, cloud nodes)
- Show device statistics:
  - Computer name
  - Type (Server/Client/Cloud)
  - IP address and ports
  - Index progress
  - Backup progress
  - Disk usage (free/total)
  - Last seen timestamp
- Auto-refresh every 15 seconds
- Settings button (opens modal)

**API Calls:**
- `GET /cass/nodeinfo.fn` (every 15 seconds)
- `GET /cass/serverproperty.fn?property=allowremote`
- `GET /cass/getremoteeula.fn`
- `GET /cass/serverupdateproperty.fn` (on settings save)

**Key Functions:**
- `load()` - Fetch and display device information
- `seen(longDate)` - Calculate "last seen" time (e.g., "2 minutes ago")
- `openSettings()` - Open settings modal
- `calcPercent(free, total)` - Calculate disk usage percentage

**UI Components:**
- Card for each device
- Progress bars (index, backup, disk usage)
- Status badges (online/offline)
- Settings modal

---

### 5.3 My Files Page (`/MyFiles/:ftype/:range`)

**Templates:**
- List View: `partialMyFiles.htm`
- Grid View: `partialMyFilesGrid.htm`

**Controller:** `MyFilesController`

**Purpose:** Main file browsing interface

**Features:**

#### View Modes
1. **List View**
   - Table layout
   - Columns: Checkbox, Thumbnail, Name, Date, Size, Tags, Actions
   - Sortable by name, date, size
   - Hover to show action buttons

2. **Grid View**
   - Card-based layout (4 columns responsive)
   - Large thumbnails
   - Overlay with file info
   - Click to select

#### Filtering
- **File Type:** All, Photos, Music, Videos, Documents, .doc, .xls, .ppt, .pdf
- **Time Range:** Today, 3 days, Week, 2 weeks, Month, Year, All Time
- **Search:** Real-time search with autocomplete suggestions
- **Tags:** Click tag to filter

#### File Operations
- **View/Play:** Click file to open appropriate viewer/player
- **Download:** Single file or chunked (10MB chunks for large files)
- **Tag Management:** Add/remove tags (bulk or individual)
- **Share:** Share selected files via tags
- **Select:** Checkbox selection with "Select All" toggle

#### Pagination
- Infinite scroll (loads 100 files at a time)
- Triggered when scrolling to bottom (100px threshold)
- "Back to top" button appears after scrolling past 60px

#### Sorting
- By name (A-Z, Z-A)
- By date (newest first, oldest first)
- By size (largest first, smallest first)

**API Calls:**
- `GET /cass/query.fn` (initial load and pagination)
- `GET /cass/sidebar.fn` (filter counts)
- `GET /cass/suggest.fn` (search suggestions)
- `GET /cass/gettags_webapp.fn` (tag list)
- `GET /cass/applytags.fn` (tag operations)
- `GET /cass/getusersandemail.fn` (for sharing)
- `GET /cass/getsharesettingstag.fn` (get share settings)
- `GET /cass/doshare_webapp.fn` (create share)
- `GET /cass/adduser.fn` (add new user)
- File download URL (for downloads)

**Key Functions:**
- `loadQuery()` - Fetch files based on filters
- `play(file)` - Route to appropriate viewer/player
- `downloadFile(file)` - Download with progress tracking
- `selShare()` - Open share modal
- `applyTag(md5, tag)` - Add tag to file
- `removeTag(md5, tag)` - Remove tag from file
- `handleScroll()` - Infinite scroll handler
- `toggleView()` - Switch between list/grid
- `selFile(file)` - Toggle file selection
- `selAll()` - Select/deselect all files

**State:**
- `files[]` - Current file list
- `selectedFiles[]` - Selected file MD5s
- `fileView` - "list" or "grid"
- `order` - "Asc" or "Desc"
- `loading` - Loading state
- `hasMore` - More files available
- `searchQuery` - Current search text

---

### 5.4 Backup Page (`/Backup`)

**Template:** `partialBackup.htm`
**Controller:** `BackupController`

**Purpose:** Configure backup rules (which file types go to which devices)

**Features:**
- Define multiple backup rules
- TreeView for file extension selection
- Device selection (checkbox list)
- Visual representation of rules
- Add/remove rules
- Device status display (online/offline, disk space)

**Rule Structure:**
- Each rule maps file extensions → target devices
- Example: All photos → Server Node + Cloud Node

**API Calls:**
- `GET /cass/nodeinfo.fn` (get available devices)
- `GET /cass/getextensions.fn` (get file extension groups)
- `GET /cass/getbackupconfig.fn` (load existing rules)
- `POST /backupconfig.c` (save rules)

**Key Functions:**
- `load()` - Load devices and extensions
- `addRule()` - Create new blank rule
- `loadRule(ruleIndex)` - Load rule into editor
- `selAcept()` - Save backup configuration
- `removeRule(ruleIndex)` - Delete rule

**UI Components:**
- TreeView (file extensions grouped by type)
- Device list with checkboxes
- Rule cards with edit/delete buttons
- Save/Cancel buttons

---

### 5.5 Shares Page (`/Shares`)

**Template:** `partialShares.htm`
**Controller:** `SharesController`

**Purpose:** Manage file shares

**Implementation:** Embedded iframe (`/cass/shares.htm`)

**Note:** This is a legacy page that loads a separate HTML page. The main sharing functionality is integrated into My Files page via the share modal.

---

### 5.6 Multi-Cluster Page (`/MultiCluster`)

**Template:** `partialMultiCluster.htm`
**Controller:** `MultiClusterController`

**Purpose:** Query and manage files across multiple remote clusters

**Features:**

#### Cluster Management
- Add new cluster (URL, name, credentials)
- Edit existing cluster settings
- Remove cluster
- Test cluster connection
- Save login credentials

#### Cluster Display
- Card for each cluster
- Connection status indicator
- File count and statistics
- Quick actions (edit, remove, query)

#### File Query
- Query each cluster independently
- Same filters as My Files (type, range, search)
- Combined results display
- Cluster-specific tags
- File preview/playback from remote clusters

#### File Operations (Cross-Cluster)
- View/play files from remote clusters
- Download from remote clusters
- Tag management per cluster
- Comments on remote files

**API Calls:**
- `GET /cass/getmulticlusters.fn` (list clusters)
- `GET /cass/addmulticluster.fn` (add cluster)
- `GET /cass/removemulticluster.fn` (remove cluster)
- `GET /cass/querymulticluster.fn` (query specific cluster)
- `GET /cass/saveloginmulticluster.fn` (save credentials)
- `GET /cass/gettags_webapp.fn?multiclusterid=X` (cluster tags)
- `GET /cass/applytags.fn?multiclusterid=X` (cluster tag operations)

**Key Functions:**
- `loadClusters()` - Fetch all clusters
- `addCluster()` - Add new cluster
- `editCluster(cluster)` - Edit cluster settings
- `removeCluster(cluster)` - Delete cluster
- `loadClusterQuery(cluster)` - Query specific cluster
- `play(file, cluster)` - Open file from cluster

**State:**
- `clusters[]` - List of configured clusters
- `clusterResults{}` - Results per cluster
- `selectedCluster` - Currently active cluster

---

## 6. Modals & Dialogs

### 6.1 Image Viewer Modal (`#imageview`)

**Purpose:** View images in a carousel with comments and actions

**Features:**

#### Carousel (Swiper.js)
- Swipe/arrow navigation
- Keyboard navigation (left/right arrows, escape)
- Thumbnail strip at bottom
- Lazy loading
- Infinite loading (fetches more files when reaching last image)
- Auto-slideshow with configurable speed (3s, 6s, 9s, 12s)

#### Image Display
- Full-screen view
- Responsive sizing: `(windowWidth - 20)px`
- Image metadata overlay
  - File name
  - Date
  - Size
  - Dimensions

#### Actions
- Download
- Open in system
- Open folder
- Facebook share
- Close (X button or Escape key)

#### Comments & Social
- Comments list (auto-refresh every 30s)
- Add comment textarea
- Like button with count
- User identification
- Tag parsing (tag:tagname → clickable)

#### Tags
- Tag input (Bootstrap Tagsinput)
- Add/remove tags
- Tag suggestions

**API Calls:**
- `GET /cass/chat_pull.fn?md5=X` (every 30s)
- `GET /cass/chat_push.fn` (on comment/like)
- `GET /cass/applytags.fn` (tag operations)
- `GET /cass/fbpublish.fn` (Facebook share)
- File download URL

**Key Functions:**
- `showImage(fileList, index)` - Initialize carousel
- `slideChange(index)` - Handle slide change
- `addComment()` - Post comment
- `likeImage()` - Like image
- `downloadFromModal()` - Download image
- `shareToFacebook()` - Facebook publish

**Event Handlers:**
- Keyboard: Arrow keys, Escape
- Click: Thumbnails, Next/Prev buttons
- Swipe: Touch gestures

---

### 6.2 Video Player Modal (`#footerVideoBar`)

**Purpose:** Play videos with HLS streaming, comments, and transcription

**Features:**

#### Video Player (HLS.js)
- Adaptive bitrate streaming
- Native HLS support for Safari
- Flash fallback detection (legacy)
- Responsive sizing: `(windowWidth * 0.95 - 270) * (264/640)`
- Play/pause controls
- Volume control
- Fullscreen option

#### Video Controls
- Standard video controls
- Progress bar
- Time display (current/total)

#### Transcription
- Display video transcript
- Timestamp alignment
- Click timestamp to seek

#### Actions
- Download video
- Open in system
- Open folder
- Close (X button)

#### Comments & Social
- Comments with video timestamps
- Add comment with current timestamp
- Like button
- Auto-refresh comments (every 30s)

#### Tags
- Tag input
- Add/remove tags

**API Calls:**
- `GET /cass/{video_url_webapp}` (HLS manifest)
- `GET /cass/gettranslate_json.fn?sMD5=X` (transcription)
- `GET /cass/chat_pull.fn?md5=X` (comments)
- `GET /cass/chat_push.fn` (post comment/like)
- `GET /cass/applytags.fn` (tags)

**Key Functions:**
- `showVideo(file)` - Initialize video player
- `loadTranscript()` - Fetch transcription
- `seekToTimestamp(seconds)` - Jump to time
- `addCommentWithTimestamp()` - Comment with time
- `closeVideo()` - Clean up player

**HLS.js Configuration:**
```javascript
{
  debug: false,
  enableWorker: true,
  lowLatencyMode: false,
  backBufferLength: 90
}
```

---

### 6.3 PDF Viewer Modal (`#pdfViewer`)

**Purpose:** View PDF files with comments

**Features:**

#### PDF Display
- Embedded iframe viewer
- Full-width display
- Zoom controls (browser default)
- Page navigation (browser default)

#### Actions
- Download PDF
- Open in system
- Open folder
- Close

#### Comments & Social
- Comments list
- Add comment
- Like button
- Auto-refresh (every 30s)

#### Tags
- Tag input
- Add/remove tags

**API Calls:**
- File URL (in iframe src)
- `GET /cass/chat_pull.fn?md5=X`
- `GET /cass/chat_push.fn`
- `GET /cass/applytags.fn`

**Key Functions:**
- `showPdf(file)` - Load PDF in iframe
- `downloadPdf()` - Download file
- `closePdf()` - Close modal

---

### 6.4 Document Viewer Modal (`#docViewer`)

**Purpose:** Display document icon and metadata with comments

**Features:**

#### Document Display
- Large file type icon (based on extension)
- File name
- File size
- Date
- Extension badge

#### Actions
- Download document
- Open in system (if available)
- Open folder
- Close

#### Comments & Social
- Comments list
- Add comment
- Like button
- Auto-refresh (every 30s)

#### Tags
- Tag input
- Add/remove tags

**Supported Extensions:**
- Microsoft Office: .doc, .docx, .xls, .xlsx, .ppt, .pptx
- OpenOffice: .odt, .ods, .odp
- Text: .txt, .rtf
- Other: .csv, .xml, .json

**API Calls:**
- Same as PDF viewer

**Key Functions:**
- `showDocument(file)` - Display document info
- `downloadDocument()` - Download file
- `closeDocument()` - Close modal

---

### 6.5 Download Progress Modal (`#downloadModal`)

**Purpose:** Show download progress for large files

**Features:**

#### Progress Display
- Progress bar (0-100%)
- Bytes downloaded / Total bytes
- Download speed (KB/s, MB/s)
- Estimated time remaining

#### Controls
- Cancel button (aborts download)
- Auto-close on completion

#### Chunked Download
- For files ≥ 10MB
- Default chunk size: 10MB (10485760 bytes)
- Sequential chunk requests
- Resume on failure (retry up to 3 times)

**Implementation:**
```javascript
function downloadFile(file) {
  const chunkSize = 10485760; // 10MB
  let offset = 0;

  function downloadChunk() {
    $.ajax({
      url: file.file_path_webapp,
      data: {
        uuid: getCookie('uuid'),
        filechunk_size: chunkSize,
        filechunk_offset: offset
      },
      xhr: function() {
        const xhr = new XMLHttpRequest();
        xhr.addEventListener('progress', updateProgress);
        return xhr;
      }
    }).done(function(data) {
      // Append chunk to buffer
      // Update progress
      // If more chunks, download next
      // Else, save file
    });
  }
}
```

**Key Functions:**
- `downloadFile(file)` - Start download
- `updateProgress(event)` - Update progress bar
- `cancelDownload()` - Abort download
- `saveFile(blob, filename)` - Save to disk

---

### 6.6 Share Modal (`#sharemodal`)

**Purpose:** Share files with other users via tags

**Features:**

#### Tag Input
- Single tag name for the share
- Validates tag name

#### User Selection
- Checkbox list of all users
- Username and email display
- Select/deselect all
- Add new user button

#### Email Preview
- Shows email invitation template
- Displays share link
- Copy to clipboard

#### Add User Form
- Username (required)
- Password (required)
- Email (required, validated)
- Create button

**Workflow:**
1. User selects files in My Files page
2. Clicks "Share" button
3. Enters tag name for the share
4. Selects users to share with
5. Optionally adds new user
6. Clicks "Share" button
7. System creates tag, applies to files, shares with users
8. Email invitation is generated

**API Calls:**
- `GET /cass/getusersandemail.fn` (user list)
- `GET /cass/applytags.fn` (apply tag to files)
- `GET /cass/doshare_webapp.fn` (create share)
- `GET /cass/invitation_webapp.fn` (email template)
- `GET /cass/adduser.fn` (add user)

**Key Functions:**
- `openShareModal(selectedFiles)` - Initialize modal
- `loadUsers()` - Fetch user list
- `addUser()` - Create new user
- `createShare()` - Execute share
- `copyEmailToClipboard()` - Copy invitation

**Validation:**
- Tag name: Required, alphanumeric + underscore
- Email: RFC 5322 format
- Username: Required
- Password: Required

---

### 6.7 Upload Modal (`#dropzone`)

**Purpose:** Upload files with drag-and-drop

**Features:**

#### Dropzone (Dropzone.js)
- Drag-and-drop area
- Click to browse
- Multiple file selection
- File previews
- Progress bars per file

#### Chunk Size Selector
- Slider (NoUiSlider)
- Options: 5MB, 10MB, 15MB, 20MB
- Displays selected size

#### Upload Queue
- List of pending uploads
- Progress per file
- Upload speed
- Cancel individual upload
- Remove from queue

#### Auto-Upload
- Starts immediately on file drop/selection
- Sequential or parallel (configurable)

**API Calls:**
- `POST /cass/file` (chunked upload)

**Dropzone Configuration:**
```javascript
{
  url: '/cass/file',
  paramName: 'file',
  maxFilesize: 10000, // MB
  chunking: true,
  forceChunking: true,
  chunkSize: 10485760, // 10MB default
  parallelChunkUploads: false,
  retryChunks: true,
  retryChunksLimit: 3
}
```

**Key Functions:**
- `initDropzone()` - Initialize Dropzone instance
- `updateChunkSize(size)` - Change chunk size
- `onUploadProgress(file, progress)` - Update progress
- `onUploadComplete(file)` - Handle completion
- `onUploadError(file, error)` - Handle error

---

### 6.8 Cluster Form Modal (`#clusterform`)

**Purpose:** Add or edit cluster settings

**Features:**

#### Form Fields
- Cluster URL (read-only after creation)
- Cluster Name (editable)
- User (username)
- Password

#### Validation
- URL format check
- Required fields
- Connection test on save

#### Modes
- **Add Mode:** All fields editable except URL
- **Edit Mode:** Only name, user, password editable

**API Calls:**
- `GET /cass/addmulticluster.fn` (add/update)
- `GET /cass/saveloginmulticluster.fn` (save credentials)

**Key Functions:**
- `openAddCluster()` - Open in add mode
- `openEditCluster(cluster)` - Open in edit mode
- `saveCluster()` - Save settings
- `testConnection()` - Verify cluster connectivity

**Validation Rules:**
- URL: Must start with http:// or https://
- Name: Required, max 50 chars
- User: Required
- Password: Required for new clusters

---

## 7. UI Components

### 7.1 Top Navigation Bar

**Components:**

#### Logo
- Alterante logo (left side)
- Click to go to Home

#### Main Menu
- Home
- My Files
- Backup
- Shares
- Multi-Cluster

#### Actions (Right Side)
- Upload button (opens dropzone modal)
- Search input with autocomplete
- User dropdown menu
  - About
  - Settings (opens settings iframe in modal)
  - Log Out

**Search Autocomplete:**
- Triggers on 2+ characters
- Shows up to 10 suggestions
- Icons for file type (photo, music, document, tag)
- Click to execute search
- Keyboard navigation (up/down, enter)

**Upload Button:**
- Tooltip: "Upload Files"
- Opens dropzone modal
- Shows upload count badge if uploads in progress

---

### 7.2 Left Sidebar Filters

**Two Display Modes:**
1. **Icon Mode:** Collapsed, shows only icons
2. **Expanded Mode:** Shows labels and counts

**Toggle:** Hover to expand, leave to collapse

#### File Types Section
- All Types (`.all`)
- Photos (`.photo`) - Icon: image
- Music (`.music`) - Icon: music
- Videos (`.video`) - Icon: video
- Documents (`.document`) - Icon: file
- Specific: .doc, .xls, .ppt, .pdf

**Badge:** Shows count for each type

#### Time Range Section
- Today (1 day) - Icon: calendar
- Past 3 days - Icon: calendar
- This Week (7 days) - Icon: calendar
- Past 2 Weeks (14 days) - Icon: calendar
- Past 30 days - Icon: calendar
- This Year (365 days) - Icon: calendar
- All Time (`.all`) - Icon: infinity

**Badge:** Shows count for each range

#### Tags Section
- Dynamic list from `gettags_webapp.fn`
- Tag name + count badge
- Click to filter by tag
- Hover menu:
  - Share tag (opens share modal)
- Sorted by count (descending)

**API Calls:**
- `GET /cass/gettags_webapp.fn` (on page load and after tag changes)
- `GET /cass/sidebar.fn` (on filter change, for counts)

**Active State:**
- Highlighted when filter is active
- Badge color changes

---

### 7.3 Right Sidebar Panel

**Toggle:** Icon button on right edge (slide in/out)

**Three Tabs:**

#### 1. Chat Tab (`#chatTab`)

**Purpose:** Global chat room

**Features:**
- Message list (auto-scroll to bottom)
- User identification (username + timestamp)
- Send message textarea
- Emoji support
- Unread message badge
- Clear all button (admin only)

**Message Display:**
```
[2025-01-15 14:32] john.doe
Hello everyone!

[2025-01-15 14:33] jane.smith
Hi John! How are you?
```

**API Calls:**
- `GET /cass/chat_pull.fn` (every 30 seconds)
- `GET /cass/chat_push.fn?msg_type=CHAT` (on send)
- `GET /cass/chat_clear.fn` (clear all)

**Key Functions:**
- `loadMessages()` - Fetch new messages
- `sendMessage()` - Send chat message
- `scrollToBottom()` - Auto-scroll
- `markAsRead()` - Clear unread badge
- `clearAll()` - Delete all messages (admin)

**Auto-Refresh:** Every 30 seconds

**Notification:**
- Beep sound on new message (if not focused)
- Badge count on tab

---

#### 2. Playlist Tab (`#playlistTab`)

**Purpose:** Audio playlist management

**Features:**
- List of queued audio files
- Currently playing indicator (highlighted)
- Play/pause toggle
- Delete track button
- Clear all button
- Auto-play next track on completion
- Download/Open/Folder links per track

**Track Display:**
```
[Icon] Song Title - Artist
       Album
       [Download] [Open] [Folder] [Delete]
```

**Audio Player:**
- HTML5 `<audio>` element
- Controls: Play/Pause, Volume, Seek
- Mini player (always visible when playing)
- Visual progress bar

**Playback Logic:**
- Click track to play
- Auto-advance to next on completion
- Loop playlist (optional)
- Shuffle (optional)

**Key Functions:**
- `addToPlaylist(file)` - Add file
- `playTrack(index)` - Play specific track
- `nextTrack()` - Advance to next
- `prevTrack()` - Go to previous
- `removeTrack(index)` - Delete from playlist
- `clearPlaylist()` - Remove all

**State:**
- `playlistFiles[]` - Track list
- `currentTrack` - Index of playing track
- `isPlaying` - Playback state

---

#### 3. Events Tab (`#eventsTab`)

**Purpose:** Activity feed (user actions)

**Features:**
- Event list (newest first)
- Event types:
  - File uploaded
  - File tagged
  - File shared
  - File liked
  - Comment added
- User identification
- Timestamp (relative, e.g., "2 minutes ago")
- File thumbnail (click to view)
- Action description

**Event Display:**
```
[Thumbnail] john.doe uploaded photo.jpg
            2 minutes ago
```

**API Calls:**
- `GET /cass/chat_pull.fn?msg_type=EVENT` (every 30s)

**Key Functions:**
- `loadEvents()` - Fetch events
- `playFromEvent(file)` - View file from event
- `formatTimestamp(timestamp)` - Relative time

**Auto-Refresh:** Every 30 seconds

---

### 7.4 File List Components

#### List View Item (Table Row)

**Columns:**
1. **Checkbox** - Selection toggle
2. **Thumbnail** - 60x60px image
3. **Name** - File name (truncated with tooltip)
4. **Date** - Formatted date (e.g., "Jan 15, 2025")
5. **Size** - Human-readable (e.g., "2.5 MB")
6. **Tags** - Tag badges (click to filter)
7. **Actions** - Buttons (show on hover)
   - Download
   - Open
   - Folder
   - Tag
   - Share

**Row Highlighting:**
- Hover: Light gray background
- Selected: Blue background
- Active (playing): Green border

---

#### Grid View Item (Card)

**Card Structure:**
```
┌─────────────────┐
│   [Thumbnail]   │
│                 │
│   File Name     │
│   Date | Size   │
│   [Tag] [Tag]   │
│                 │
│   [Select Icon] │
└─────────────────┘
```

**Card Actions:**
- Click card: Open viewer
- Click select icon: Toggle selection
- Click tag: Filter by tag

**Card States:**
- Normal: White background
- Hover: Shadow effect
- Selected: Blue border

---

### 7.5 Tag Component (Bootstrap Tagsinput)

**Features:**
- Autocomplete from existing tags
- Add new tag (Enter or comma)
- Remove tag (X button or backspace)
- Bulk operations (apply to multiple files)
- Tag validation (alphanumeric + underscore)

**Visual:**
```
[tag1 X] [tag2 X] [tag3 X] [Type to add...]
```

**API Integration:**
- Fetch: `GET /cass/gettags_webapp.fn`
- Add: `GET /cass/applytags.fn?tag=X&{md5}=on`
- Remove: `GET /cass/applytags.fn?tag=X&DeleteTag=X&{md5}=on`

---

### 7.6 Node/Device Card (Home Page)

**Card Layout:**
```
┌──────────────────────────────────┐
│ [Icon] Computer Name             │
│        Server | 192.168.1.100    │
│                                  │
│ Index Progress:   [====75%====] │
│ Backup Progress:  [====50%====] │
│ Disk Usage:       [====60%====] │
│                                  │
│ Last Seen: 2 minutes ago         │
└──────────────────────────────────┘
```

**Status Indicators:**
- Online: Green dot
- Offline: Gray dot (if last ping > 5 minutes)

**Progress Bars:**
- Index: Blue
- Backup: Orange
- Disk: Green (normal) / Red (> 90% used)

---

## 8. State Management

### 8.1 Global State (`$rootScope`)

**Session:**
- `uuid` - User session cookie
- `username` - Current username

**Navigation:**
- `mnu` - Current menu/page ("Home", "MyFiles", "Backup", "Shares", "MultiCluster")
- `previousRoute` - For back navigation

**View Settings:**
- `fileView` - "list" or "grid"
- `order` - "Asc" or "Desc" (sort direction)
- `showRightMenu` - Right sidebar visibility (boolean)

**Notifications:**
- `alerts[]` - Alert messages
  - `{type: "success" | "danger", msg: string}`

**Playlist:**
- `playlistfiles[]` - Audio playlist
- `currentPlayingFile` - Currently playing file object
- `isPlaying` - Playback state (boolean)

**Selection:**
- `filesSelected` - Count of selected files
- `selAllAttr` - "Select all" checkbox state

**Events:**
- `playFile` - File to play (triggered from events tab)

---

### 8.2 Controller-Specific State (`$scope`)

#### MyFilesController

```javascript
$scope.files = [];              // Current file list
$scope.selectedMD5s = [];       // Selected file MD5s
$scope.searchQuery = "";        // Search text
$scope.loading = false;         // Loading state
$scope.hasMore = true;          // More files available
$scope.lastDate = null;         // Pagination offset
$scope.suggestions = [];        // Search suggestions
$scope.tags = [];              // Available tags
$scope.sortBy = "date";        // Sort field
```

#### HomeController

```javascript
$scope.nodes = [];             // Device list
$scope.loading = false;
$scope.allowRemote = false;    // Remote access enabled
$scope.showSettings = false;   // Settings modal
```

#### BackupController

```javascript
$scope.rules = [];             // Backup rules
$scope.nodes = [];             // Available devices
$scope.extensions = [];        // File extension groups
$scope.currentRule = null;     // Rule being edited
$scope.selectedExtensions = []; // Selected for current rule
$scope.selectedDevices = [];   // Selected for current rule
```

#### MultiClusterController

```javascript
$scope.clusters = [];          // Configured clusters
$scope.clusterResults = {};    // Results per cluster
$scope.selectedCluster = null; // Active cluster
$scope.loading = {};           // Loading state per cluster
```

#### RightBarController

```javascript
$scope.messages = [];          // Chat messages
$scope.events = [];            // Event feed
$scope.newMessage = "";        // Chat input
$scope.unreadCount = 0;        // Unread message count
$scope.activeTab = "chat";     // Active right sidebar tab
```

---

### 8.3 Persistent State

#### Cookies
- `uuid` - User session identifier (HttpOnly, set by server)

#### LocalStorage
- `mail` - Remembered username (if "Remember me" checked)
- `pass` - Remembered password (if "Remember me" checked)
- **Note:** Storing password in localStorage is insecure. Consider removing.

#### URL Parameters
- `ftype` - File type filter (in route)
- `range` - Time range filter (in route)
- `cluster` - Cluster ID for multi-cluster login
- `foo` - Search query (in query string)

---

### 8.4 State Synchronization

**Between Controllers:**
- Use `$rootScope.$broadcast()` for events
- Example: File tagged → Broadcast "tagsChanged" → Sidebar reloads tags

**With Backend:**
- Polling (every 15-30 seconds for chat, events, device status)
- On-demand (file queries, tag operations)

**State Persistence:**
- View settings (list/grid, sort order) persist in `$rootScope`
- Filter selections are in URL (can bookmark/share)
- Session persists via cookie

---

## 9. User Flows

### 9.1 Login Flow

```
User opens /cass/uiv3/indexv2.htm
  ↓
Enter username and password
  ↓
(Optional) Check "Remember me"
  ↓
Click "Sign In"
  ↓
POST /cass/login.fn
  ↓
If success:
  - Set uuid cookie
  - Save credentials to localStorage (if remember me)
  - Redirect to home.htm
If failure:
  - Show error message
  - Stay on login page
```

---

### 9.2 Browse Files Flow

```
User clicks "My Files" in nav
  ↓
Load /MyFiles/.all/.all (all files, all time)
  ↓
GET /cass/query.fn?ftype=.all&days=0&view=json&numobj=100
GET /cass/sidebar.fn?ftype=.all&days=0
GET /cass/gettags_webapp.fn
  ↓
Display files (list or grid view)
Display sidebar filters with counts
Display tags
  ↓
User applies filter (e.g., Photos, Last 7 days)
  ↓
Route to /MyFiles/.photo/7
  ↓
Reload files with new filter
  ↓
User scrolls to bottom
  ↓
Load more files (infinite scroll)
  ↓
Repeat until no more files
```

---

### 9.3 View Image Flow

```
User clicks image thumbnail
  ↓
Call play(file)
  ↓
Check file_group === "photo"
  ↓
Call showImage(files, index)
  ↓
Initialize Swiper carousel with all images
Set active slide to clicked image
  ↓
Open #imageview modal
  ↓
Load comments: GET /cass/chat_pull.fn?md5=X
Load tags from file object
  ↓
Display image in carousel
Display comments
Display tags
  ↓
Start comment polling (every 30s)
  ↓
User navigates (arrow keys, swipe, click)
  ↓
Update active slide
Lazy load adjacent images
Update comments for new image
  ↓
User closes modal
  ↓
Stop polling
Clean up Swiper instance
```

---

### 9.4 Play Video Flow

```
User clicks video thumbnail
  ↓
Call play(file)
  ↓
Check file_group === "movie"
  ↓
Call showVideo(file)
  ↓
Initialize HLS.js player
Load HLS manifest: GET /cass/{video_url_webapp}
  ↓
Open #footerVideoBar modal
  ↓
Load comments: GET /cass/chat_pull.fn?md5=X
Load transcription: GET /cass/gettranslate_json.fn?sMD5=X
Load tags from file object
  ↓
Display video player
Display comments
Display transcription
Display tags
  ↓
Start comment polling (every 30s)
  ↓
User plays video
  ↓
HLS.js handles adaptive streaming
Display transcription synchronized with playback
  ↓
User adds comment
  ↓
POST /cass/chat_push.fn with timestamp
  ↓
User closes modal
  ↓
Stop polling
Destroy HLS.js instance
Pause video
```

---

### 9.5 Download File Flow

#### Small File (< 10MB)

```
User clicks download button
  ↓
Direct download: window.open(file_path_webapp + "?uuid=" + uuid)
  ↓
Browser handles download
```

#### Large File (≥ 10MB)

```
User clicks download button
  ↓
Open #downloadModal
Initialize progress bar
  ↓
Start chunked download:
  chunkSize = 10MB
  offset = 0
  ↓
Download chunk:
  GET file_path_webapp?uuid=X&filechunk_size=10485760&filechunk_offset=0
  ↓
Update progress bar
Calculate speed (bytes/sec)
Estimate time remaining
  ↓
Append chunk to buffer (Blob)
  ↓
offset += chunkSize
  ↓
If offset < file_size:
  Download next chunk
Else:
  Save file to disk (Blob → download)
  Close modal
  Show success alert
```

---

### 9.6 Tag Files Flow

#### Single File

```
User clicks file tag input (in modal)
  ↓
Type tag name
  ↓
Press Enter or comma
  ↓
GET /cass/applytags.fn?tag=newtag&{md5}=on
  ↓
Update file object (add to file_tags)
Update tag list in sidebar (increment count or add new)
  ↓
Tag appears in input field
```

#### Multiple Files (Bulk)

```
User selects multiple files (checkboxes)
  ↓
Click "Tag" button in toolbar
  ↓
Enter tag name in prompt
  ↓
GET /cass/applytags.fn?tag=newtag&{md51}=on&{md52}=on&{md53}=on
  ↓
Update all file objects
Update tag list in sidebar
  ↓
Show success alert
```

---

### 9.7 Share Files Flow

```
User selects files
  ↓
Click "Share" button
  ↓
Open #sharemodal
  ↓
Load users: GET /cass/getusersandemail.fn
  ↓
User enters tag name for share
User selects recipients (checkboxes)
  ↓
(Optional) User adds new user:
  - Fill username, password, email
  - GET /cass/adduser.fn
  - Reload user list
  ↓
Click "Share" button
  ↓
Apply tag to files:
  GET /cass/applytags.fn?tag=sharetagname&{md5s}=on
  ↓
Create share:
  GET /cass/doshare_webapp.fn?sharetype=TAG&sharekey=sharetagname&shareusers=user1,user2
  ↓
Get email template:
  GET /cass/invitation_webapp.fn?sharetype=TAG&sharekey=sharetagname
  ↓
Display email preview
Show share token/link
  ↓
User copies email to send manually
(Or system sends email via backend)
  ↓
Close modal
Show success alert
```

---

### 9.8 Upload Files Flow

```
User clicks "Upload" button in nav
  ↓
Open #dropzone modal
  ↓
User drags files OR clicks to browse
  ↓
Files added to Dropzone queue
  ↓
For each file:
  ↓
  Calculate chunks:
    numChunks = ceil(file.size / chunkSize)
  ↓
  For each chunk (i = 0 to numChunks):
    ↓
    offset = i * chunkSize
    chunk = file.slice(offset, offset + chunkSize)
    ↓
    POST /cass/file
      FormData:
        - file: chunk
        - filechunk_size: chunkSize
        - filechunk_offset: offset
    ↓
    Update progress bar:
      percent = (i + 1) / numChunks * 100
    ↓
    Calculate speed:
      speed = bytesUploaded / (currentTime - startTime)
    ↓
    Display speed (KB/s or MB/s)
    Display ETA
  ↓
  On complete:
    Show success checkmark
    Remove from queue
  ↓
  On error:
    Retry (up to 3 times)
    If still fails, show error
  ↓
All uploads complete:
  Close modal (or keep open for more)
  Reload file list (if on My Files page)
```

---

### 9.9 Configure Backup Flow

```
User clicks "Backup" in nav
  ↓
Load /Backup
  ↓
GET /cass/nodeinfo.fn (available devices)
GET /cass/getextensions.fn (file extension groups)
GET /cass/getbackupconfig.fn (existing rules)
  ↓
Display devices with status
Display rules (if any)
  ↓
User clicks "Add Rule"
  ↓
Show TreeView (file extensions)
Show device list (checkboxes)
  ↓
User selects extensions (e.g., .jpg, .png, .gif)
User selects target devices (e.g., Server, Cloud Node)
  ↓
Click "Save Rule"
  ↓
Build rule object:
  {
    rule: nextRuleIndex,
    extensions: [".jpg", ".png", ".gif"],
    devices: [{node_id: "...", node_type: "server"}, ...]
  }
  ↓
Add to rules array
  ↓
Display rule card:
  "Images (.jpg, .png, .gif) → Server, Cloud Node"
  [Edit] [Delete]
  ↓
User clicks "Save Configuration"
  ↓
POST /backupconfig.c
  Body: JSON array of all rules
  ↓
Show success alert
Backend starts backup processes according to rules
```

---

### 9.10 Multi-Cluster Query Flow

```
User clicks "Multi-Cluster" in nav
  ↓
Load /MultiCluster
  ↓
GET /cass/getmulticlusters.fn
  ↓
Display cluster cards (if any)
  ↓
User clicks "Add Cluster"
  ↓
Open #clusterform modal
  ↓
User enters:
  - Cluster URL (e.g., https://cluster.example.com:8081)
  - Cluster Name (e.g., "Home Server")
  - User (username)
  - Password
  ↓
Click "Add"
  ↓
GET /cass/addmulticluster.fn?multiclusteruser=X&multiclusterpassword=Y&multiclusterid=URL&multiclustername=NAME
  ↓
If success:
  - Add to clusters array
  - Display new cluster card
  - Close modal
If failure:
  - Show error (invalid credentials, unreachable, etc.)
  ↓
User clicks "Query" on cluster card
  ↓
GET /cass/querymulticluster.fn?multiclusterid=X&ftype=.all&days=0
  ↓
Display results in cluster's section
  ↓
User clicks file from cluster
  ↓
Open viewer/player (same as local files)
Files load from remote cluster URLs
  ↓
User tags file on remote cluster:
  GET /cass/applytags.fn?tag=X&{md5}=on&multiclusterid=Y
```

---

## 10. Special Features

### 10.1 File Upload (Dropzone.js)

**Implementation:** `web/cass/uiv3/js/alterante.js`

**Features:**
- Drag-and-drop file upload
- Click to browse
- Multiple file selection
- Chunked upload (configurable: 5-20MB chunks)
- Real-time progress tracking
- Upload speed calculation
- Pause/resume (via retry mechanism)
- Auto-retry on failure (3 attempts)

**Chunk Size Selector:**
- NoUiSlider widget
- Range: 5MB to 20MB
- Visual display of selected size
- Updates `chunkSize` parameter for uploads

**Configuration:**
```javascript
Dropzone.options.dropzone = {
  url: '/cass/file',
  paramName: 'file',
  maxFilesize: 10000,        // 10GB max
  chunking: true,
  forceChunking: true,
  chunkSize: 10485760,       // 10MB default
  parallelChunkUploads: false,
  retryChunks: true,
  retryChunksLimit: 3,

  init: function() {
    this.on('uploadprogress', function(file, progress, bytesSent) {
      // Update progress bar
      // Calculate speed
    });

    this.on('success', function(file) {
      // Mark as complete
    });

    this.on('error', function(file, errorMessage) {
      // Show error
      // Retry
    });
  }
};
```

---

### 10.2 Video Streaming (HLS.js)

**Implementation:** `web/cass/uiv3/js/controllers/myfilescontroller.js`

**Features:**
- HTTP Live Streaming (HLS) support
- Adaptive bitrate streaming
- Fallback for Safari (native HLS)
- Flash detection for legacy browsers

**HLS.js Setup:**
```javascript
if (Hls.isSupported()) {
  var hls = new Hls({
    debug: false,
    enableWorker: true,
    lowLatencyMode: false,
    backBufferLength: 90
  });

  hls.loadSource(videoUrl);
  hls.attachMedia(videoElement);

  hls.on(Hls.Events.MANIFEST_PARSED, function() {
    videoElement.play();
  });

  hls.on(Hls.Events.ERROR, function(event, data) {
    if (data.fatal) {
      switch(data.type) {
        case Hls.ErrorTypes.NETWORK_ERROR:
          hls.startLoad();
          break;
        case Hls.ErrorTypes.MEDIA_ERROR:
          hls.recoverMediaError();
          break;
        default:
          hls.destroy();
          break;
      }
    }
  });
} else if (videoElement.canPlayType('application/vnd.apple.mpegurl')) {
  // Native HLS support (Safari)
  videoElement.src = videoUrl;
}
```

**Transcription Integration:**
- Fetch: `GET /cass/gettranslate_json.fn?sMD5=X`
- Display synchronized with video playback
- Clickable timestamps to seek

---

### 10.3 Image Carousel (Swiper.js)

**Implementation:** `web/cass/uiv3/partials/partialModals.htm`

**Features:**
- Touch/swipe support
- Keyboard navigation (arrow keys)
- Thumbnail navigation strip
- Lazy loading
- Infinite loading (fetch more on last image)
- Auto-slideshow with configurable speed
- Zoom (optional, not currently enabled)

**Swiper Configuration:**
```javascript
var swiper = new Swiper('.swiper-container', {
  navigation: {
    nextEl: '.swiper-button-next',
    prevEl: '.swiper-button-prev',
  },
  keyboard: {
    enabled: true,
  },
  thumbs: {
    swiper: thumbsSwiper,
  },
  lazy: {
    loadPrevNext: true,
  },
  loop: false,
  on: {
    slideChange: function() {
      var index = this.activeIndex;
      // Update comments for current image
      // Update tags
      // Check if near end, load more files
    }
  }
});
```

**Auto-Slideshow:**
```javascript
function startSlideshow(interval) {
  slideshowInterval = setInterval(function() {
    swiper.slideNext();
  }, interval);
}

function stopSlideshow() {
  clearInterval(slideshowInterval);
}
```

**Infinite Loading:**
```javascript
swiper.on('slideChange', function() {
  var index = this.activeIndex;
  var total = this.slides.length;

  if (total - index < 5 && hasMoreFiles) {
    loadMoreFiles().then(function(newFiles) {
      // Add slides to Swiper
      newFiles.forEach(function(file) {
        swiper.appendSlide(createSlide(file));
      });
    });
  }
});
```

---

### 10.4 Search with Autocomplete

**Implementation:** `web/cass/uiv3/js/controllers/myfilescontroller.js`

**Features:**
- Real-time suggestions as user types
- Icon indicators for file type
- Keyboard navigation (up/down arrows, enter)
- Click to select suggestion
- Escape to close

**Trigger:** Minimum 2 characters

**API Call:**
```javascript
GET /cass/suggest.fn?ftype=.all&days=0&foo=searchtext&view=json&numobj=10
```

**Response:**
```json
{
  "suggestions": [
    {
      "name": "vacation_photo.jpg",
      "file_ext": ".jpg",
      "file_group": "photo"
    },
    {
      "name": "tag:vacation",
      "file_ext": "",
      "file_group": "tag"
    }
  ]
}
```

**UI:**
```html
<div class="autocomplete-dropdown">
  <div class="suggestion" ng-repeat="s in suggestions">
    <i class="icon-{{ s.file_group }}"></i>
    <span>{{ s.name }}</span>
  </div>
</div>
```

**Keyboard Handling:**
```javascript
$scope.selectedIndex = -1;

$scope.onKeyDown = function(event) {
  switch(event.keyCode) {
    case 40: // Down arrow
      $scope.selectedIndex = Math.min($scope.selectedIndex + 1, $scope.suggestions.length - 1);
      break;
    case 38: // Up arrow
      $scope.selectedIndex = Math.max($scope.selectedIndex - 1, -1);
      break;
    case 13: // Enter
      if ($scope.selectedIndex >= 0) {
        $scope.selectSuggestion($scope.suggestions[$scope.selectedIndex]);
      } else {
        $scope.doSearch();
      }
      break;
    case 27: // Escape
      $scope.closeSuggestions();
      break;
  }
};
```

---

### 10.5 Comments System

**Implementation:** Shared across all file viewer modals

**Features:**
- Comments per file (identified by MD5)
- User identification (username + timestamp)
- Date grouping (e.g., "Today", "Yesterday", "Jan 15")
- Like functionality
- Tag parsing (tag:tagname → clickable link to filter)
- Video timestamp integration (for video comments)
- CSV export (admin feature)
- Auto-refresh (polling every 30 seconds)

**Message Types:**
- `CHAT` - Global chat (no MD5)
- `COMMENT` - File comment
- `LIKE` - Like action
- `EVENT` - User activity
- `FB` - Facebook share notification

**Display Logic:**
```javascript
function groupByDate(messages) {
  var groups = {};

  messages.forEach(function(msg) {
    var date = new Date(parseInt(msg.msg_date));
    var dateKey;

    if (isToday(date)) {
      dateKey = "Today";
    } else if (isYesterday(date)) {
      dateKey = "Yesterday";
    } else {
      dateKey = formatDate(date); // "Jan 15, 2025"
    }

    if (!groups[dateKey]) {
      groups[dateKey] = [];
    }

    groups[dateKey].push({
      user: msg.msg_user,
      body: decodeBase64(msg.msg_body),
      time: formatTime(date),
      type: msg.msg_type
    });
  });

  return groups;
}
```

**Tag Parsing:**
```javascript
function parseCommentTags(text) {
  return text.replace(/tag:(\w+)/g, '<a href="#" ng-click="filterByTag(\'$1\')">tag:$1</a>');
}
```

**Video Timestamp Integration:**
```javascript
// Add comment with current video time
function addCommentWithTimestamp() {
  var currentTime = videoElement.currentTime;
  var message = "[" + formatTime(currentTime) + "] " + $scope.commentText;

  sendComment(message);
}

// Parse timestamps in comments
function parseTimestamps(text) {
  return text.replace(/\[(\d{2}:\d{2})\]/g, '<a href="#" ng-click="seekTo(\'$1\')">[$1]</a>');
}
```

**Polling:**
```javascript
var commentInterval;

function startCommentPolling(md5) {
  var lastMsgId = 0;

  commentInterval = setInterval(function() {
    $.get('/cass/chat_pull.fn', {
      md5: md5,
      msg_from: lastMsgId
    }).done(function(data) {
      var newMessages = JSON.parse(data);

      if (newMessages.length > 0) {
        $scope.messages = $scope.messages.concat(newMessages);
        lastMsgId = newMessages[newMessages.length - 1].msg_date;
        $scope.$apply();

        // Play notification sound if not focused
        if (!document.hasFocus()) {
          playBeep();
        }
      }
    });
  }, 30000); // 30 seconds
}

function stopCommentPolling() {
  clearInterval(commentInterval);
}
```

---

### 10.6 Infinite Scroll

**Implementation:** `web/cass/uiv3/js/controllers/myfilescontroller.js`

**Features:**
- Automatically loads more files when scrolling near bottom
- Threshold: 100px from bottom
- Loads 100 files per request
- Uses last file date as pagination offset
- Loading indicator during fetch
- "No more files" message when exhausted

**Scroll Detection:**
```javascript
$(window).scroll(function() {
  var scrollTop = $(window).scrollTop();
  var windowHeight = $(window).height();
  var documentHeight = $(document).height();

  var distanceFromBottom = documentHeight - (scrollTop + windowHeight);

  if (distanceFromBottom < 100 && !$scope.loading && $scope.hasMore) {
    $scope.$apply(function() {
      $scope.loadMore();
    });
  }

  // Show "back to top" button
  if (scrollTop > 60) {
    $('#backToTop').fadeIn();
  } else {
    $('#backToTop').fadeOut();
  }
});
```

**Load More:**
```javascript
$scope.loadMore = function() {
  if ($scope.loading || !$scope.hasMore) return;

  $scope.loading = true;

  var lastFile = $scope.files[$scope.files.length - 1];
  var lastDate = lastFile ? lastFile.file_date_long : null;

  $.get('/cass/query.fn', {
    ftype: $scope.ftype,
    days: $scope.days,
    foo: $scope.searchQuery,
    view: 'json',
    numobj: 100,
    date: lastDate,
    order: $scope.order
  }).done(function(data) {
    var result = JSON.parse(data);

    $scope.files = $scope.files.concat(result.files);
    $scope.hasMore = result.files.length === 100;
    $scope.loading = false;
    $scope.$apply();
  });
};
```

---

### 10.7 File Selection

**Implementation:** Both list and grid views

**Features:**
- Individual selection (checkbox or icon click)
- Select all / Deselect all toggle
- Visual indication of selected files
- Selection count badge
- Bulk operations on selected files (tag, share)

**List View:**
```html
<tr ng-repeat="file in files" ng-class="{selected: file.selected}">
  <td>
    <input type="checkbox" ng-model="file.selected" ng-change="updateSelection()">
  </td>
  <!-- ... other columns ... -->
</tr>
```

**Grid View:**
```html
<div class="file-card" ng-repeat="file in files" ng-class="{selected: file.selected}">
  <div class="select-icon" ng-click="toggleSelect(file)">
    <i ng-class="file.selected ? 'fa-check-circle' : 'fa-circle'"></i>
  </div>
  <!-- ... card content ... -->
</div>
```

**Select All:**
```javascript
$scope.selectAll = function() {
  var allSelected = $scope.files.every(function(f) { return f.selected; });

  $scope.files.forEach(function(file) {
    file.selected = !allSelected;
  });

  $scope.updateSelection();
};

$scope.updateSelection = function() {
  $scope.selectedFiles = $scope.files.filter(function(f) { return f.selected; });
  $rootScope.filesSelected = $scope.selectedFiles.length;
};
```

**Bulk Tag:**
```javascript
$scope.bulkTag = function() {
  var tag = prompt("Enter tag name:");
  if (!tag) return;

  var md5Params = $scope.selectedFiles.map(function(file) {
    return file.nickname + '=on';
  }).join('&');

  $.get('/cass/applytags.fn?tag=' + tag + '&' + md5Params).done(function() {
    // Update file objects
    $scope.selectedFiles.forEach(function(file) {
      if (!file.file_tags.includes(tag)) {
        file.file_tags += (file.file_tags ? ',' : '') + tag;
      }
    });

    // Reload tags in sidebar
    $scope.loadTags();

    alert('Tag applied to ' + $scope.selectedFiles.length + ' files');
  });
};
```

---

## 11. Third-Party Dependencies

### 11.1 Core Libraries

**AngularJS 1.8.3**
- `angular.min.js` - Core framework
- `angular-route.min.js` - Routing module
- `angular-sanitize.min.js` - HTML sanitization
- `angular-ui-router.min.js` - Advanced routing
- Modules used: `ngRoute`, `ngSanitize`, `ui.bootstrap`, `ui.router`, `ui.slider`, `ngProgressLite`

**jQuery 3.7.1**
- `jquery.min.js` - DOM manipulation, AJAX
- `jquery-ui.min.js` - UI widgets, sortable, draggable

---

### 11.2 UI Framework

**Bootstrap 4.6.2**
- `bootstrap.min.css` - CSS framework
- `bootstrap.min.js` - JavaScript components
- Components used:
  - Grid system
  - Cards
  - Modals
  - Dropdowns
  - Progress bars
  - Alerts
  - Forms
  - Buttons
  - Badges

**Font Awesome 5.x**
- `fontawesome.min.css`
- `solid.min.css`
- Icons used: fa-home, fa-photo, fa-music, fa-video, fa-file, fa-tag, fa-upload, fa-download, fa-share, etc.

---

### 11.3 File Upload

**Dropzone.js 5.x**
- `dropzone.min.js`
- Features: Drag-and-drop, chunked upload, progress tracking, previews

---

### 11.4 Media Playback

**HLS.js**
- `hls.js` - HTTP Live Streaming
- Features: Adaptive bitrate, error recovery

**Video.js 7.x**
- `video.dev.js`
- `video-js.min.css`
- `videojs.hls.min.js` - HLS plugin
- Features: HTML5 video player with controls

**Swiper.js**
- `swiper-bundle.min.js`
- `swiper-bundle.min.css`
- Features: Image carousel, touch/swipe support, keyboard navigation, lazy loading

---

### 11.5 Form Components

**Bootstrap Tagsinput**
- `bootstrap-tagsinput.min.js`
- `bootstrap-tagsinput.css`
- `bootstrap-tagsinput-angular.min.js` - Angular integration
- Features: Tag input with autocomplete, validation

**NoUiSlider**
- `nouislider.min.js`
- `nouislider.min.css`
- Features: Range slider (used for chunk size selector)

**UI Bootstrap**
- `ui-bootstrap-tpls.min.js`
- Features: Angular directives for Bootstrap components (modals, tooltips, etc.)

---

### 11.6 Utilities

**jQuery Balloon**
- `jquery.balloon.min.js`
- Features: Tooltip plugin

**ngProgressLite**
- Features: Angular progress bar (loading indicator)

---

### 11.7 TreeView (Backup Page)

**Custom TreeView Plugin**
- `plugins.js`
- Features: Hierarchical checkbox tree for file extensions

---

## 12. React Migration Recommendations

### 12.1 Architecture

**Recommended Stack:**
- **Framework:** React 18+ with TypeScript
- **Routing:** React Router v6
- **State Management:** Redux Toolkit + RTK Query (or Zustand for simpler state)
- **UI Framework:** Material-UI (MUI) or Chakra UI
- **Forms:** React Hook Form + Zod validation
- **Build Tool:** Vite

---

### 12.2 State Management Strategy

#### Global State (Redux Toolkit)

**Slices:**
1. **authSlice** - User session, authentication
2. **filesSlice** - File list, filters, selection
3. **uiSlice** - View settings, modals, sidebar visibility
4. **playlistSlice** - Audio playlist
5. **chatSlice** - Messages, events
6. **clustersSlice** - Multi-cluster state
7. **tagsSlice** - Tag list, tag operations

**RTK Query API Endpoints:**
- `fileApi` - All file-related endpoints (query, download, upload)
- `tagApi` - Tag operations
- `chatApi` - Comments, chat, events
- `clusterApi` - Multi-cluster management
- `systemApi` - Nodes, settings

**Example:**
```typescript
// fileApi.ts
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

export const fileApi = createApi({
  reducerPath: 'fileApi',
  baseQuery: fetchBaseQuery({ baseUrl: '/cass/' }),
  tagTypes: ['Files', 'Tags'],
  endpoints: (builder) => ({
    getFiles: builder.query<File[], QueryParams>({
      query: (params) => ({
        url: 'query.fn',
        params: { ...params, view: 'json' }
      }),
      providesTags: ['Files']
    }),
    applyTag: builder.mutation<void, ApplyTagParams>({
      query: (params) => ({
        url: 'applytags.fn',
        params
      }),
      invalidatesTags: ['Files', 'Tags']
    }),
    // ... more endpoints
  })
});

export const { useGetFilesQuery, useApplyTagMutation } = fileApi;
```

---

### 12.3 Routing Structure

```typescript
// routes.tsx
import { createBrowserRouter } from 'react-router-dom';

const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />
  },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        path: 'home',
        element: <HomePage />
      },
      {
        path: 'files/:ftype/:range',
        element: <MyFilesPage />
      },
      {
        path: 'backup',
        element: <BackupPage />
      },
      {
        path: 'shares',
        element: <SharesPage />
      },
      {
        path: 'clusters',
        element: <MultiClusterPage />
      }
    ]
  }
]);
```

---

### 12.4 Component Structure

**Recommended Hierarchy:**

```
src/
├── features/
│   ├── auth/
│   │   ├── LoginPage.tsx
│   │   └── authSlice.ts
│   ├── files/
│   │   ├── MyFilesPage.tsx
│   │   ├── FileList.tsx
│   │   ├── FileCard.tsx
│   │   ├── FileGrid.tsx
│   │   ├── filesSlice.ts
│   │   └── fileApi.ts
│   ├── modals/
│   │   ├── ImageViewer.tsx
│   │   ├── VideoPlayer.tsx
│   │   ├── PdfViewer.tsx
│   │   ├── DownloadProgress.tsx
│   │   └── ShareModal.tsx
│   ├── sidebar/
│   │   ├── LeftSidebar.tsx
│   │   ├── RightSidebar.tsx
│   │   ├── FilterPanel.tsx
│   │   └── ChatPanel.tsx
│   ├── upload/
│   │   ├── UploadZone.tsx
│   │   └── uploadSlice.ts
│   └── clusters/
│       ├── MultiClusterPage.tsx
│       └── clusterSlice.ts
├── components/
│   ├── layout/
│   │   ├── AppLayout.tsx
│   │   ├── TopNav.tsx
│   │   └── Footer.tsx
│   ├── common/
│   │   ├── SearchBar.tsx
│   │   ├── TagInput.tsx
│   │   ├── ProgressBar.tsx
│   │   └── Alert.tsx
│   └── ui/ (shadcn/ui components)
├── hooks/
│   ├── useFileSelection.ts
│   ├── useInfiniteScroll.ts
│   ├── useFileUpload.ts
│   └── usePolling.ts
├── services/
│   └── api.ts
├── types/
│   └── models.ts
├── utils/
│   ├── formatters.ts
│   └── validators.ts
└── store/
    └── store.ts
```

---

### 12.5 Key Component Migrations

#### FileList Component

**AngularJS:**
```html
<div ng-repeat="file in files">
  <file-card file="file" on-select="selectFile(file)"></file-card>
</div>
```

**React:**
```tsx
// FileList.tsx
import { useGetFilesQuery } from './fileApi';
import { FileCard } from './FileCard';
import { useInfiniteScroll } from '@/hooks/useInfiniteScroll';

export function FileList() {
  const { ftype, range } = useParams();
  const { data, isLoading, hasMore, fetchMore } = useGetFilesQuery({ ftype, range });

  const { observerRef } = useInfiniteScroll({
    hasMore,
    isLoading,
    onLoadMore: fetchMore
  });

  return (
    <div className="file-grid">
      {data?.files.map(file => (
        <FileCard key={file.nickname} file={file} />
      ))}
      <div ref={observerRef} className="scroll-trigger" />
      {isLoading && <Spinner />}
    </div>
  );
}
```

---

#### ImageViewer Component

**Replace:** Swiper.js (still use it, but with React wrapper)

```tsx
// ImageViewer.tsx
import { Swiper, SwiperSlide } from 'swiper/react';
import { Navigation, Keyboard, Thumbs } from 'swiper/modules';
import 'swiper/css';

export function ImageViewer({ files, initialIndex }: Props) {
  const [activeIndex, setActiveIndex] = useState(initialIndex);
  const { data: comments } = useGetCommentsQuery(files[activeIndex].nickname, {
    pollingInterval: 30000 // 30 seconds
  });

  return (
    <Modal open onClose={onClose} fullScreen>
      <Swiper
        modules={[Navigation, Keyboard, Thumbs]}
        navigation
        keyboard
        initialSlide={initialIndex}
        onSlideChange={(swiper) => setActiveIndex(swiper.activeIndex)}
      >
        {files.map(file => (
          <SwiperSlide key={file.nickname}>
            <img src={file.file_path_webapp} alt={file.name} />
          </SwiperSlide>
        ))}
      </Swiper>

      <CommentPanel comments={comments} fileId={files[activeIndex].nickname} />
    </Modal>
  );
}
```

---

#### VideoPlayer Component

**Replace:** HLS.js (still use it, but with React wrapper or use react-player)

```tsx
// VideoPlayer.tsx
import ReactPlayer from 'react-player';

export function VideoPlayer({ file }: Props) {
  const playerRef = useRef<ReactPlayer>(null);
  const [currentTime, setCurrentTime] = useState(0);

  const { data: transcript } = useGetTranscriptQuery(file.nickname);
  const { data: comments } = useGetCommentsQuery(file.nickname, {
    pollingInterval: 30000
  });

  return (
    <Modal open onClose={onClose}>
      <ReactPlayer
        ref={playerRef}
        url={file.video_url_webapp}
        controls
        onProgress={({ playedSeconds }) => setCurrentTime(playedSeconds)}
        config={{
          file: {
            hlsOptions: {
              enableWorker: true,
              lowLatencyMode: false
            }
          }
        }}
      />

      <TranscriptPanel
        transcript={transcript}
        currentTime={currentTime}
        onSeek={(time) => playerRef.current?.seekTo(time)}
      />

      <CommentPanel
        comments={comments}
        fileId={file.nickname}
        videoTime={currentTime}
      />
    </Modal>
  );
}
```

---

#### UploadZone Component

**Replace:** Dropzone.js with react-dropzone

```tsx
// UploadZone.tsx
import { useDropzone } from 'react-dropzone';
import { useFileUpload } from '@/hooks/useFileUpload';

export function UploadZone() {
  const { uploadFile, progress, isUploading } = useFileUpload();

  const { getRootProps, getInputProps, acceptedFiles } = useDropzone({
    onDrop: (files) => {
      files.forEach(file => uploadFile(file));
    }
  });

  return (
    <Modal open onClose={onClose}>
      <div {...getRootProps()} className="dropzone">
        <input {...getInputProps()} />
        <p>Drag files here or click to browse</p>
      </div>

      {acceptedFiles.map((file, i) => (
        <UploadProgress
          key={i}
          file={file}
          progress={progress[file.name]}
        />
      ))}
    </Modal>
  );
}
```

**Custom Hook:**
```tsx
// useFileUpload.ts
export function useFileUpload() {
  const [progress, setProgress] = useState<Record<string, number>>({});

  const uploadFile = async (file: File) => {
    const chunkSize = 10 * 1024 * 1024; // 10MB
    const chunks = Math.ceil(file.size / chunkSize);

    for (let i = 0; i < chunks; i++) {
      const start = i * chunkSize;
      const end = Math.min(start + chunkSize, file.size);
      const chunk = file.slice(start, end);

      const formData = new FormData();
      formData.append('file', chunk);
      formData.append('filechunk_size', chunkSize.toString());
      formData.append('filechunk_offset', start.toString());

      await fetch('/cass/file', {
        method: 'POST',
        body: formData
      });

      setProgress(prev => ({
        ...prev,
        [file.name]: ((i + 1) / chunks) * 100
      }));
    }
  };

  return { uploadFile, progress };
}
```

---

### 12.6 Custom Hooks

#### useInfiniteScroll

```tsx
export function useInfiniteScroll({ hasMore, isLoading, onLoadMore }: Props) {
  const observerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !isLoading) {
          onLoadMore();
        }
      },
      { threshold: 1.0 }
    );

    if (observerRef.current) {
      observer.observe(observerRef.current);
    }

    return () => observer.disconnect();
  }, [hasMore, isLoading, onLoadMore]);

  return { observerRef };
}
```

---

#### usePolling

```tsx
export function usePolling(callback: () => void, interval: number, enabled: boolean = true) {
  useEffect(() => {
    if (!enabled) return;

    const id = setInterval(callback, interval);
    return () => clearInterval(id);
  }, [callback, interval, enabled]);
}
```

---

#### useFileSelection

```tsx
export function useFileSelection(files: File[]) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const toggleSelect = (id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const selectAll = () => {
    setSelectedIds(new Set(files.map(f => f.nickname)));
  };

  const deselectAll = () => {
    setSelectedIds(new Set());
  };

  const isSelected = (id: string) => selectedIds.has(id);

  return {
    selectedIds,
    selectedFiles: files.filter(f => selectedIds.has(f.nickname)),
    toggleSelect,
    selectAll,
    deselectAll,
    isSelected,
    count: selectedIds.size
  };
}
```

---

### 12.7 API Service Layer

Create a centralized API client:

```typescript
// services/api.ts
import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/cass',
  withCredentials: true,
  params: {
    view: 'json'
  }
});

// Request interceptor
apiClient.interceptors.request.use(config => {
  // Add uuid to all requests
  const uuid = getCookie('uuid');
  if (uuid && config.params) {
    config.params.uuid = uuid;
  }
  return config;
});

// Response interceptor
apiClient.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      // Redirect to login
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

---

### 12.8 Replace Polling with WebSockets

**Current:** Polling every 30 seconds for comments, chat, events

**Recommended:** WebSocket connection for real-time updates

**Implementation:**
```typescript
// services/websocket.ts
class WebSocketService {
  private ws: WebSocket | null = null;
  private listeners: Map<string, Set<Function>> = new Map();

  connect(url: string) {
    this.ws = new WebSocket(url);

    this.ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      const { type, payload } = data;

      this.listeners.get(type)?.forEach(listener => listener(payload));
    };
  }

  on(event: string, callback: Function) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(callback);
  }

  off(event: string, callback: Function) {
    this.listeners.get(event)?.delete(callback);
  }

  send(type: string, payload: any) {
    this.ws?.send(JSON.stringify({ type, payload }));
  }
}

export const wsService = new WebSocketService();
```

**Usage in React:**
```tsx
// useWebSocket.ts
export function useWebSocket(event: string, callback: (data: any) => void) {
  useEffect(() => {
    wsService.on(event, callback);
    return () => wsService.off(event, callback);
  }, [event, callback]);
}

// In component
function ChatPanel() {
  const [messages, setMessages] = useState([]);

  useWebSocket('new_message', (message) => {
    setMessages(prev => [...prev, message]);
  });

  return <MessageList messages={messages} />;
}
```

**Backend Change Required:**
- Implement WebSocket endpoint in rtserver
- Broadcast events: new comment, new chat, file uploaded, etc.

---

### 12.9 TypeScript Types

```typescript
// types/models.ts
export interface File {
  nickname: string;
  name: string;
  file_ext: string;
  file_group: 'photo' | 'music' | 'movie' | 'document';
  file_size: number;
  file_date: string;
  file_date_long: number;
  file_tags: string;
  file_thumbnail: string;
  file_path_webapp: string;
  file_remote_webapp: string;
  file_folder_webapp: string;
  audio_url?: string;
  audio_url_remote?: string;
  song_title?: string;
  song_artist?: string;
  song_album?: string;
  video_url_webapp?: string;
  img_height?: number;
  img_width?: number;
}

export interface Node {
  node_id: string;
  node_machine: string;
  node_type: 'server' | 'client' | 'cloud';
  node_ip: string;
  node_port: number;
  node_nettyport_post: number;
  node_idx_percent: string;
  node_backup_percent: string;
  node_backuppath: string;
  node_lastping_long: number;
  node_diskfree: number;
  node_disktotal: number;
}

export interface Message {
  msg_date: string;
  msg_type: 'CHAT' | 'COMMENT' | 'LIKE' | 'EVENT' | 'FB';
  msg_user: string;
  msg_body: string;
}

export interface Tag {
  tagname: string;
  tagcnt: number;
}

export interface Cluster {
  cluster: string;
  name: string;
  user: string;
  password: string;
  uuid: string;
}

export interface BackupRule {
  rule: number;
  extensions: string[];
  devices: Array<{
    node_id: string;
    node_type: string;
  }>;
}
```

---

### 12.10 Testing Strategy

**Unit Tests:**
- Redux slices (reducers, actions)
- Custom hooks
- Utility functions
- API service functions

**Component Tests:**
- File card rendering
- Tag input behavior
- Modal interactions
- Form validation

**Integration Tests:**
- File upload flow
- Search and filter
- Tag operations
- Share workflow

**E2E Tests (Playwright/Cypress):**
- Login flow
- Browse files
- View image carousel
- Play video
- Upload files
- Create backup rule
- Add cluster

---

### 12.11 Performance Optimizations

**Virtualization:**
- Use `react-window` or `react-virtuoso` for large file lists
- Render only visible items

**Code Splitting:**
- Lazy load modals
- Lazy load routes
- Lazy load heavy components (video player, carousel)

**Memoization:**
- `useMemo` for expensive calculations
- `React.memo` for pure components
- `useCallback` for event handlers

**Image Optimization:**
- Lazy load images
- Use intersection observer
- Serve thumbnails from CDN
- Progressive image loading

**Bundle Optimization:**
- Tree shaking
- Code splitting by route
- Dynamic imports for modals
- Minimize vendor bundles

---

### 12.12 Migration Phases

**Phase 1: Setup & Infrastructure**
1. Initialize React + TypeScript + Vite project
2. Setup Redux Toolkit + RTK Query
3. Create API service layer
4. Define TypeScript types
5. Setup routing structure

**Phase 2: Authentication & Layout**
1. Login page
2. App layout (nav, sidebars, footer)
3. Authentication state management
4. Protected routes

**Phase 3: Core Pages**
1. Home page (device dashboard)
2. My Files page (list view)
3. File card component
4. Filters (left sidebar)
5. Search with autocomplete

**Phase 4: File Operations**
1. File selection
2. Tag management
3. Download (with chunking)
4. Share modal
5. Grid view

**Phase 5: Media Viewers**
1. Image viewer (Swiper)
2. Video player (HLS)
3. PDF viewer
4. Document viewer
5. Comments component

**Phase 6: Upload & Right Sidebar**
1. Upload zone (Dropzone)
2. Chat panel
3. Playlist panel
4. Events panel

**Phase 7: Backup & Multi-Cluster**
1. Backup configuration page
2. Multi-cluster management
3. Cluster query

**Phase 8: Polish & Testing**
1. Error handling
2. Loading states
3. Responsive design
4. Accessibility
5. Testing
6. Performance optimization

---

## Conclusion

This specification provides a complete blueprint for the Alterante web application, covering:

- **All pages and views** with detailed feature descriptions
- **Complete API reference** with 40+ endpoints
- **Data models** with TypeScript-ready structures
- **UI components** with implementation details
- **User flows** for all major features
- **Special functionality** (upload, streaming, carousel, etc.)
- **Third-party dependencies** currently used
- **Comprehensive React migration guide** with code examples

**Key Takeaways for React Migration:**

1. **State Management:** Use Redux Toolkit for global state, RTK Query for API calls
2. **Replace Polling:** Implement WebSocket for real-time features (chat, comments, events)
3. **Reuse Libraries:** Continue using HLS.js, Swiper, but with React wrappers
4. **Type Safety:** Full TypeScript coverage with strict types
5. **Testing:** Comprehensive test coverage (unit, integration, E2E)
6. **Performance:** Virtualization, code splitting, lazy loading, memoization
7. **Phased Approach:** Migrate incrementally, starting with core pages

This specification can be used as:
- Technical documentation for developers
- Migration roadmap for React rewrite
- API reference for backend integration
- Feature catalog for stakeholders
- Testing checklist for QA

Total API endpoints documented: **40+**
Total pages/views: **7**
Total modals: **8**
Total UI components: **15+**
Total user flows: **10+**

---

## 13. React Migration Status (UI v5)

**Version:** UI v5 (React)
**Technology Stack:** React 19.1.1, TypeScript, Vite, Material-UI 6.1, Redux Toolkit
**Location:** `/web/cass/uiv5/`
**Migration Date:** October 2025
**Status:** Core Features Completed ✅

### 13.1 Technology Stack

#### Frontend Framework
- **React 19.1.1** - Modern React with concurrent features
- **TypeScript** - Full type safety across the application
- **Vite 7.1.7** - Fast build tool and dev server
- **Material-UI (MUI) 6.1** - Component library for consistent design
- **Redux Toolkit** - State management
- **React Router 6** - Client-side routing

#### Media & UI Libraries
- **Swiper 11.x** - Image carousel with touch support
- **HLS.js 1.5.x** - HTTP Live Streaming for video playback
- **js-cookie** - Cookie management
- **React Dropzone** - File upload with drag & drop

#### Build & Development
- **Vite** - Development server with HMR
- **TypeScript** - Type checking and compilation
- **ESLint** - Code linting
- **Base URL:** `/cass/uiv5/dist/` (production)

### 13.2 Project Structure

```
web/cass/uiv5/
├── public/                      # Static assets
│   └── img/                     # Images (logo, icons)
├── src/
│   ├── components/              # Reusable components
│   │   ├── layout/              # Layout components
│   │   │   ├── TopNav.tsx       # Top navigation bar
│   │   │   └── RightSidebar.tsx # Collapsible right sidebar
│   │   ├── chat/                # Chat & comments
│   │   │   └── ChatPanel.tsx    # Chat/comments panel
│   │   ├── transcript/          # Video transcription
│   │   │   └── TranscriptPanel.tsx
│   │   ├── tags/                # File tagging
│   │   │   └── TagsPanel.tsx
│   │   ├── playlist/            # Playlist management
│   │   │   └── PlaylistPanel.tsx
│   │   ├── events/              # Event notifications
│   │   │   └── EventsPanel.tsx
│   │   └── upload/              # File upload
│   │       └── UploadZone.tsx
│   ├── features/                # Feature-based modules
│   │   ├── auth/                # Authentication
│   │   │   └── LoginPage.tsx
│   │   ├── files/               # File management
│   │   │   ├── FilesPage.tsx
│   │   │   ├── FileCard.tsx
│   │   │   └── FileFilters.tsx
│   │   └── media/               # Media viewers
│   │       ├── ImageViewer.tsx  # Image carousel
│   │       ├── VideoPlayer.tsx  # Video player with HLS
│   │       └── PdfViewer.tsx    # PDF viewer
│   ├── services/                # API services
│   │   ├── api.ts               # Base API configuration
│   │   ├── authApi.ts           # Authentication API
│   │   ├── fileApi.ts           # File operations
│   │   ├── chatApi.ts           # Chat/comments API
│   │   ├── downloadService.ts   # File download (chunked)
│   │   └── uploadService.ts     # File upload (chunked)
│   ├── store/                   # Redux store
│   │   ├── store.ts             # Store configuration
│   │   └── slices/              # Redux slices
│   │       ├── authSlice.ts     # Authentication state
│   │       ├── filesSlice.ts    # File listing & filters
│   │       └── viewerSlice.ts   # Media viewer state
│   ├── types/                   # TypeScript types
│   │   └── models.ts            # Data models
│   ├── utils/                   # Utility functions
│   │   ├── urlHelper.ts         # URL building (buildUrl)
│   │   └── formatters.ts        # Data formatting
│   ├── App.tsx                  # Root component
│   └── main.tsx                 # Application entry point
├── dist/                        # Production build output
├── package.json
├── tsconfig.json
├── vite.config.ts
└── README.md
```

### 13.3 Completed Features

#### ✅ Authentication & Layout
- [x] Login page with username/password authentication
- [x] Hivebot logo (inline SVG) on login page and top nav
- [x] UUID session management (localStorage + cookies)
- [x] Remember me functionality
- [x] Top navigation bar with search, upload, user menu
- [x] Logout functionality
- [x] Blue theme matching original design (#004080)

#### ✅ File Management
- [x] Files page with grid/list views
- [x] File type filtering (all, image, movie, document, audio, other)
- [x] Date range filtering (7, 30, 90, 180, 360 days, all)
- [x] Search with autocomplete suggestions
- [x] Sort by name, date, size
- [x] File selection (single & multi-select)
- [x] File type icons
- [x] Pagination (infinite scroll / load more)
- [x] File metadata display

#### ✅ Media Viewers
- [x] **Image Viewer**: Full-screen carousel with Swiper.js
  - Keyboard navigation (arrows, escape, space)
  - Slideshow mode (6-second intervals)
  - Lazy loading (3-slide window)
  - Download functionality
  - Right sidebar integration
- [x] **Video Player**: Full-screen with HLS streaming
  - HLS.js for adaptive streaming
  - Native HLS support for Safari
  - Video controls (play, pause, fullscreen)
  - Download functionality
  - Right sidebar integration
- [x] **PDF Viewer**: Full-screen iframe-based viewer
  - PDF.js rendering
  - Download functionality
  - Right sidebar integration

#### ✅ Chat & Comments System
- [x] Global chat (CHAT message type)
- [x] File-specific comments (COMMENT message type)
- [x] Context-aware switching based on current file
- [x] Real-time polling (30-second intervals)
- [x] Video timestamp feature ((MM:SS) format in messages)
- [x] **Download comments as CSV**
  - Filename includes file name
  - Extracts video timestamps to separate column
  - Formats: User, Date, Time (optional), Comment
- [x] Message decoding (HTML entities)
- [x] Auto-scroll to latest message

#### ✅ Transcript Feature
- [x] Transcript panel for video files
- [x] Auto-scrolling to active segment
- [x] WebVTT and simple text format support
- [x] Timestamp display with segments
- [x] Fetch from backend (gettranslate_json.fn endpoint)

#### ✅ Right Sidebar
- [x] Tabbed interface (Chat, Playlist, Events, Tags, Transcript)
- [x] Persistent drawer across navigation
- [x] Toggle button on right edge (blue chevron)
- [x] Full-screen mode support for media viewers
- [x] Dynamic tab visibility (Tags, Transcript based on context)
- [x] Proper z-index layering (1302 in fullscreen mode)

#### ✅ File Operations
- [x] **Download** with UUID authentication
- [x] **Chunked Download** for large files (10MB chunks)
  - Progress tracking
  - Speed calculation
  - Estimated time remaining
- [x] **Upload** with drag & drop
  - Multi-file support
  - Progress tracking
  - File preview

#### ✅ Network & Configuration
- [x] `buildUrl()` helper for network access (eliminates hardcoded localhost:8081)
- [x] Vite proxy configuration for `/cass` API endpoints
- [x] Base URL configuration (`/cass/uiv5/dist/`)
- [x] Environment-aware URL building (dev vs production)

#### ✅ Tags System
- [x] Tags panel showing current file tags
- [x] Tag display with chips
- [x] Context-aware (only shows when file selected)

### 13.4 Known Issues & Limitations

#### Performance
- [ ] Bundle size: 1.5MB (code splitting recommended)
- [ ] Consider dynamic imports for media viewers
- [ ] Virtual scrolling not implemented for large file lists

#### Features Not Yet Implemented
- [ ] Admin user detection (currently hardcoded to false)
- [ ] Forgot password functionality
- [ ] Settings page
- [ ] About page
- [ ] Playlist management (UI exists, logic pending)
- [ ] Events panel (UI exists, logic pending)
- [ ] Backup configuration page
- [ ] Shares management page
- [ ] Multi-cluster page
- [ ] Folders page (full implementation)

#### Browser Compatibility
- [ ] Node.js version warning (18.x → 22.12+ recommended)
- [ ] IE11 not supported (modern browsers only)

### 13.5 Key Implementation Details

#### URL Helper (`buildUrl`)
All API calls must use the `buildUrl()` helper to handle both development (proxy) and production (absolute paths) environments:

```typescript
// src/utils/urlHelper.ts
export function buildUrl(path: string): string {
  if (import.meta.env.DEV) {
    // Development: use Vite proxy
    return path;
  }
  // Production: construct full URL
  const host = window.location.host;
  const protocol = window.location.protocol;
  return `${protocol}//${host}${path}`;
}
```

Usage examples:
```typescript
// File API
fetch(buildUrl('/cass/sidebar.fn?uuid=...'))

// Download
window.open(buildUrl(`${file.file_path_webapp}&uuid=${uuid}`))

// Upload
fetch(buildUrl('/formpost/upload'), { ... })
```

#### Authentication Flow
1. User enters credentials on login page
2. POST to `/cass/login.fn?boxuser=X&boxpass=Y`
3. Backend returns HTML with UUID (extracted from response)
4. UUID stored in localStorage
5. UUID included in all API requests as query parameter
6. Session maintained via cookies + localStorage

#### Media Viewer Integration
All media viewers (Image, Video, PDF) follow the same pattern:
- Full-screen modal (`position: fixed`, `zIndex: 1300`)
- Top bar with file info and action buttons
- Content area with viewer (zIndex: 0)
- Bottom info bar (zIndex: 1)
- Right sidebar integration (zIndex: 1301)
- Blue chevron toggle button (zIndex: 1302 in fullscreen)

#### Chat Context Switching
```typescript
// Viewer slice manages current file context
dispatch(setCurrentFile(file));        // Set context
dispatch(clearCurrentFile());          // Clear on close

// Chat panel uses currentFileMD5 selector
const currentFileMD5 = useSelector(selectCurrentFileMD5);

// API call includes MD5 for file-specific comments
await pullMessages(currentFileMD5, lastMsgId);
```

### 13.6 Migration Comparison

| Feature | Angular (uiv3) | React (uiv5) | Status |
|---------|---------------|--------------|--------|
| Login Page | ✅ | ✅ | Complete |
| File Listing | ✅ | ✅ | Complete |
| File Filters | ✅ | ✅ | Complete |
| Search | ✅ | ✅ | Complete |
| Image Viewer | ✅ | ✅ | Complete |
| Video Player | ✅ | ✅ | Complete |
| PDF Viewer | ✅ | ✅ | Complete |
| Chat/Comments | ✅ | ✅ | Complete |
| Transcript | ✅ | ✅ | Complete |
| Tags | ✅ | ✅ | Complete |
| Upload | ✅ | ✅ | Complete |
| Download | ✅ | ✅ | Complete |
| Chunked Download | ✅ | ✅ | Complete |
| CSV Export | ✅ | ✅ | Complete |
| Right Sidebar | ✅ | ✅ | Complete |
| Playlist | ✅ | 🔄 | UI only |
| Events | ✅ | 🔄 | UI only |
| Backup Config | ✅ | ❌ | Not started |
| Shares | ✅ | ❌ | Not started |
| Multi-Cluster | ✅ | ❌ | Not started |
| Folders | ✅ | ❌ | Not started |

### 13.7 Development Commands

```bash
# Install dependencies
npm install

# Development server (http://localhost:5173)
npm run dev

# Production build
npm run build

# Preview production build
npm run preview

# Type checking
npm run type-check

# Linting
npm run lint
```

### 13.8 Configuration Files

#### vite.config.ts
```typescript
export default defineConfig({
  plugins: [react()],
  base: '/cass/uiv5/dist/',
  server: {
    proxy: {
      '/cass': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
        ws: true,
      },
      '/formpost': {
        target: 'http://localhost:8087',
        changeOrigin: true,
        secure: false,
      }
    }
  }
})
```

#### tsconfig.json
- Strict mode enabled
- Path aliases configured
- Target: ES2020
- Lib: ES2020, DOM, DOM.Iterable

### 13.9 Testing Strategy

#### Recommended Testing Approach
- **Unit Tests**: Vitest + React Testing Library
- **Integration Tests**: Playwright or Cypress
- **E2E Tests**: Playwright for critical user flows
- **API Mocking**: MSW (Mock Service Worker)

#### Priority Test Coverage
1. Authentication flow
2. File listing and filtering
3. Media viewer functionality
4. Chat/comments system
5. Upload/download operations

### 13.10 Performance Optimizations

#### Implemented
- Lazy loading images in carousel (3-slide window)
- Code splitting via Vite
- React.memo for expensive components
- useCallback for event handlers
- Debounced search (300ms)

#### Recommended
- Implement React.lazy for route-based code splitting
- Add virtual scrolling for large file lists
- Optimize bundle size (current: 1.5MB)
- Add service worker for offline support
- Implement progressive image loading

### 13.11 Deployment

#### Production Build
```bash
npm run build
```

Output directory: `dist/`

Files generated:
- `index.html` (0.50 kB)
- `assets/index-*.css` (~8 kB)
- `assets/index-*.js` (~1.5 MB)
- `img/*` (static assets)

#### Deployment Path
Production files should be deployed to:
```
/web/cass/uiv5/dist/
```

The application will be accessible at:
```
http://your-server:8081/cass/uiv5/dist/
```

#### Backend Requirements
- Java rtserver running on port 8081
- Netty POST handler on port 8087 (for uploads)
- CORS headers configured for cross-origin requests
- UUID-based session authentication

### 13.12 Future Enhancements

#### Short Term (Next Phase)
- [ ] Implement WebSocket for real-time chat (replace polling)
- [ ] Add keyboard shortcuts overlay (?)
- [ ] Implement virtual scrolling for file lists
- [ ] Add dark mode support
- [ ] Improve error handling and user feedback
- [ ] Add loading skeletons for better UX

#### Medium Term
- [ ] Implement Backup configuration page
- [ ] Implement Shares management page
- [ ] Implement Multi-cluster query interface
- [ ] Implement Folders page
- [ ] Add full Playlist management logic
- [ ] Add full Events panel logic
- [ ] Add user settings page
- [ ] Add about page with version info

#### Long Term
- [ ] Progressive Web App (PWA) support
- [ ] Offline mode with service workers
- [ ] Advanced search with filters
- [ ] Bulk operations (multi-select actions)
- [ ] Advanced tag management
- [ ] File version history
- [ ] Collaborative features
- [ ] Mobile-responsive optimizations

### 13.13 Migration Lessons Learned

#### What Worked Well
1. **Inline SVG logos** - Avoided path resolution issues
2. **buildUrl() helper** - Clean abstraction for dev/prod URLs
3. **Redux Toolkit** - Simplified state management
4. **Material-UI** - Consistent, professional design
5. **TypeScript** - Caught many bugs during development
6. **Vite** - Fast development experience with HMR

#### Challenges & Solutions
1. **HLS Video Playback**
   - Challenge: Video element not ready when component mounts
   - Solution: Added 100ms timeout for Dialog animation
   
2. **Chat Context Switching**
   - Challenge: Global chat showing when viewing file
   - Solution: Added currentFileMD5 to useEffect dependencies
   
3. **Z-Index Layering**
   - Challenge: Sidebar toggle button behind viewer
   - Solution: Proper z-index hierarchy (1302 for toggle in fullscreen)
   
4. **Network Access**
   - Challenge: Hardcoded localhost:8081 URLs failing in production
   - Solution: buildUrl() helper for all API calls

5. **SVG Logo Display**
   - Challenge: External SVG not loading in production
   - Solution: Inline SVG as React component

#### Best Practices Established
- Always use `buildUrl()` for API endpoints
- Use Redux slices for feature-based state
- Inline critical assets (logos, icons) as React components
- Follow z-index layering conventions (modals, drawers, overlays)
- Add context dependencies to useEffect hooks
- Use TypeScript interfaces for all data models
- Implement proper loading and error states

---

## Conclusion (Updated)

This specification now includes complete documentation for both the original AngularJS application (uiv3) and the React migration (uiv5), providing:

- **Original Angular app** (uiv3) - Complete reference
- **React migration** (uiv5) - Implementation details and status
- **API reference** - 40+ endpoints (unchanged, used by both versions)
- **Data models** - TypeScript-ready structures
- **Migration comparison** - Feature parity matrix
- **Lessons learned** - Challenges and solutions

**React Migration Status: Core Features Complete ✅**

The React application (uiv5) successfully implements all core features from the Angular version, including:
- Authentication and session management
- File listing with filters and search
- Full-screen media viewers (image, video, PDF)
- Chat and comments system with CSV export
- Video transcription display
- File upload and download (including chunked)
- Right sidebar with tabbed interface
- Responsive, modern UI with Material-UI

Remaining work focuses on additional pages (Backup, Shares, Multi-Cluster, Folders) and advanced features (Playlist management, Events panel, Settings).

The migration demonstrates successful modernization of a complex AngularJS application to React with improved performance, type safety, and maintainability.

---

**Document Version:** 2.0
**Last Updated:** October 2025
**React Migration Version:** UI v5 (Core Features Complete)
