# Alterante Web UI v5 (React + TypeScript)

Modern React-based web interface for Alterante file management system, built with TypeScript, Vite, and Material-UI.

## Overview

This is a complete rewrite of the Alterante web interface (previously AngularJS-based) using modern React patterns and TypeScript. The application provides file browsing, media playback, chat, and cluster management capabilities.

## Tech Stack

- **React 19.1.1** - UI framework with concurrent features
- **TypeScript 5.6** - Type-safe development
- **Vite 6.0** - Fast build tool and dev server
- **Material-UI (MUI) 6.1** - Component library
- **Redux Toolkit 2.5** - State management
- **React Router 7.1** - Client-side routing
- **Axios** - HTTP client
- **Swiper 11.1** - Touch-enabled image carousel
- **React Dropzone** - File upload with drag & drop

## Project Structure

```
src/
├── components/          # Reusable UI components
│   ├── layout/         # Layout components (AppLayout, TopNav, Sidebars)
│   ├── chat/           # Chat panel
│   ├── events/         # Events panel
│   ├── playlist/       # Audio playlist panel
│   ├── tags/           # Tags management panel
│   ├── transcript/     # Video transcript panel
│   └── upload/         # File upload zone
├── features/           # Feature-specific components
│   ├── auth/           # Authentication (LoginPage, ProtectedRoute)
│   ├── files/          # File browser (FileList, FileGrid, SelectionToolbar)
│   └── media/          # Media viewers (ImageViewer, VideoPlayer)
├── pages/              # Route pages
│   ├── HomePage.tsx    # Device dashboard
│   ├── MyFilesPage.tsx # Main file browser
│   ├── BackupPage.tsx  # Backup management (Phase 7)
│   ├── SharesPage.tsx  # Shares management (Phase 4)
│   └── MultiClusterPage.tsx # Multi-cluster (Phase 7)
├── store/              # Redux store
│   ├── store.ts        # Store configuration
│   └── slices/         # Redux slices (files, playlist, viewer, etc.)
├── services/           # API services
│   ├── api.ts          # Axios instance
│   └── fileApi.ts      # File-related API calls
├── hooks/              # Custom React hooks
│   └── useInfiniteScroll.ts # Infinite scroll implementation
├── types/              # TypeScript types
│   └── models.ts       # Data models (File, Node, Message, etc.)
├── utils/              # Utility functions
│   └── formatters.ts   # Date/size formatting
├── router.tsx          # Route definitions
└── App.tsx             # Root component
```

## Getting Started

### Prerequisites

- **Node.js 18+** (22.12.0+ recommended)
- **npm 9+** or **yarn**
- **Alterante backend** running on `localhost:8081`

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

The app will be available at `http://localhost:5173`

### Build for Production

```bash
# Create production build
npm run build

# Preview production build
npm run preview
```

Output will be in the `dist/` directory.

## Development

### Running the Backend

The React app requires the Alterante backend to be running:

```bash
# From alt-core root directory
cd scrubber
java -cp target/my-app-1.0-SNAPSHOT.jar com.mycompany.app.App
```

Backend should be accessible at `http://localhost:8081`

### Development Server Configuration

The Vite dev server proxies backend requests to avoid CORS issues:

```typescript
// vite.config.ts
server: {
  proxy: {
    '/cass': {
      target: 'http://localhost:8081',
      changeOrigin: true,
    },
    '/formpost': {
      target: 'http://localhost:8087', // Netty upload server
      changeOrigin: true,
    }
  }
}
```

**Important**: Media files (images, videos, audio) bypass the proxy in production and connect directly to port 8081 to avoid data corruption.

### Environment Variables

Create a `.env` file for local development (optional):

```env
VITE_API_BASE_URL=http://localhost:8081
VITE_UPLOAD_PORT=8087
```

## Key Features

### 1. Authentication
- Cookie-based session management
- Protected routes with automatic redirect
- UUID stored in `localStorage` for API requests

### 2. File Browser
- **List & Grid views** with responsive layout
- **Infinite scroll** pagination (100 files per batch)
- **Search** with autocomplete suggestions
- **Filters**: File type (photo, music, video, document) and time range
- **Multi-select** with checkbox, shift-click, and Ctrl/Cmd-click
- **Bulk operations**: Download, tag, delete
- **Sorting**: By date (newest/oldest first)

### 3. Media Playback
- **Image Viewer**: Swiper-based carousel with zoom, slideshow, fullscreen
- **Video Player**: HLS streaming with captions/transcripts
- **Audio Playlist**: Queue management, auto-advance, controls

### 4. File Upload
- **Drag & drop** support via React Dropzone
- **Chunked uploads** (5-20 MB chunks) for large files
- **Progress tracking** with speed calculation
- **Simultaneous uploads** with queue management
- **Auto-detection** of HTTP/HTTPS for upload endpoint selection

### 5. Real-time Features
- **Chat panel** with message history
- **Events panel** for system notifications
- **Live updates** via polling (WebSocket planned for Phase 6)

### 6. Tags Management
- **Tag panel** for current file/selection
- **Autocomplete** tag suggestions
- **Bulk tagging** across multiple files
- **Tag filtering** in file browser

## API Integration

### Authentication Flow

```typescript
POST /cass/webapp_login.fn
  → Receives UUID cookie
  → Store UUID in localStorage
  → Redirect to /home
```

### File Queries

```typescript
GET /cass/query.fn?ftype=.all&days=0&numobj=100&order=Desc&view=json&date=2025.04.25+12:57:42.402+PDT
  → Returns { fighters: File[] }
```

**Pagination**: Uses `date` parameter with last file's timestamp in format `YYYY.MM.DD+HH:MM:SS.mmm+TIMEZONE`

### Media URLs

Files use `file_path_webapp` for direct access:
```typescript
// Image/Audio
http://localhost:8081/cass/getfile.fn?sNamer={hash}&sFileExt={ext}&sFileName={name}&uuid={uuid}

// Video (HLS)
http://localhost:8081/cass/getvideo.fn?sNamer={hash}&sFileExt={ext}&sFileName={name}&uuid={uuid}
```

### Upload Endpoint

```typescript
// Development (via proxy)
POST http://localhost:5173/formpost

// Production
POST http://localhost:8087/formpost  // HTTP
POST https://hostname/{filename}     // HTTPS
```

Chunked upload format: `upload.{filename}.{totalChunks}.{chunkIndex}.p`

## State Management

### Redux Slices

1. **filesSlice**: File browser state (files, filters, selection, view mode)
2. **playlistSlice**: Audio playlist queue
3. **viewerSlice**: Image/video viewer state
4. **sidebarSlice**: Left sidebar stats (file type counts, time ranges)
5. **tagsSlice**: Tag management state
6. **chatSlice**: Chat messages and state
7. **eventsSlice**: System events

### Example: File Selection

```typescript
// Select single file
dispatch(toggleFileSelection(fileId));

// Select range (Shift+click)
dispatch(selectRange({ startId, endId }));

// Select all
dispatch(selectAll());
```

## Component Patterns

### Infinite Scroll

```typescript
const observerRef = useInfiniteScroll({
  hasMore,
  isLoading: loading,
  onLoadMore: loadMore,
  root: scrollContainerRef.current, // Custom scroll container
});

// Sentinel element at bottom of list
<Box ref={observerRef} sx={{ height: 20 }} />
```

### Media Component Pattern

All media components (ImageViewer, VideoPlayer, PlaylistPanel) follow this pattern:
- Get UUID from `localStorage.getItem('uuid')`
- Bypass Vite proxy by constructing URLs with `http://localhost:8081`
- Use `file_path_webapp` from File model

### Embedded Components

Components can render in standalone or embedded mode:

```typescript
// Standalone (with Drawer)
<PlaylistPanel />

// Embedded (in RightSidebar tab)
<PlaylistPanel embedded />
```

## Troubleshooting

### Port Conflicts

If port 5173 is in use:
```bash
npm run dev -- --port 3000
```

### Backend Connection Issues

Ensure backend is running:
```bash
curl http://localhost:8081/cass/nodeinfo.fn
```

### Proxy Issues

Media playback should bypass proxy. If files don't load:
1. Check browser console for CORS errors
2. Verify backend is accessible at `http://localhost:8081`
3. Check Network tab for failed requests

### Build Issues

Clear cache and reinstall:
```bash
rm -rf node_modules dist
npm install
npm run build
```

## Testing

```bash
# Run tests (when implemented)
npm test

# Run linter
npm run lint

# Type check
npx tsc --noEmit
```

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+

## Performance Considerations

### Optimizations Applied

1. **Virtual scrolling**: Not implemented yet (using infinite scroll)
2. **Image lazy loading**: Via Swiper
3. **Code splitting**: Via React Router lazy loading
4. **Memoization**: Critical components use React.memo
5. **Bundle optimization**: Vite's tree-shaking and minification

### Known Limitations

- Large file lists (1000+) may impact performance
- Video transcoding happens on backend (no client-side processing)
- Thumbnail generation is server-side only

## Roadmap

### Phase 3 (Current)
- ✅ File browser with infinite scroll
- ✅ Media viewers (image, video, audio)
- ✅ File upload with chunking
- ✅ Chat panel
- ✅ Tags management
- ✅ Events panel
- ✅ Playlist panel

### Phase 4 (Next)
- [ ] Shares management
- [ ] User management UI
- [ ] Advanced grid view features

### Phase 5
- [ ] Backup rules UI
- [ ] Multi-device management

### Phase 6
- [ ] WebSocket for real-time updates
- [ ] Push notifications

### Phase 7
- [ ] Multi-cluster UI
- [ ] Advanced settings

## Migration from AngularJS

Key differences from the old UI:

1. **Routing**: React Router instead of ui-router
2. **State**: Redux instead of $scope
3. **HTTP**: Axios instead of $http
4. **Components**: Functional components instead of directives
5. **Styling**: MUI instead of Bootstrap + custom CSS
6. **Build**: Vite instead of Gulp/Webpack

### API Compatibility

All backend endpoints remain unchanged. The React app uses the same API calls as the AngularJS version.

## Contributing

1. Follow TypeScript strict mode
2. Use functional components with hooks
3. Follow Material-UI theming patterns
4. Write descriptive commit messages
5. Test with actual backend before committing

## License

Same as main Alterante project.

## References

- [WEBAPP-SPEC.md](../../../WEBAPP-SPEC.md) - Full API specification
- [React Documentation](https://react.dev)
- [Material-UI Documentation](https://mui.com)
- [Redux Toolkit Documentation](https://redux-toolkit.js.org)
- [Vite Documentation](https://vite.dev)
