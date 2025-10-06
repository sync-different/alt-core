# API Endpoint Comparison: AngularJS vs React Web App

## AngularJS App (uiv3) - All .fn Endpoints

1. ❌ `adduser.fn` - Add new user
2. ❌ `addmulticluster.fn` - Add remote cluster
3. ✅ `applytags.fn` - Add/remove tags from file (used with different params for add vs remove)
4. ✅ `chat_clear.fn` - Clear chat messages
5. ✅ `chat_pull.fn` - Pull chat/comment messages
6. ✅ `chat_push.fn` - Push chat/comment messages
7. ❌ `doshare_webapp.fn` - Share tag with users
8. ✅ `downloadmulti.fn` - Download multiple files
9. ❌ `fbpublish.fn` - Publish to Facebook
10. ❌ `getbackupconfig.fn` - Get backup configuration
11. ❌ `getextensions.fn` - Get file extensions
12. ✅ `getfile.fn` - Get/download file
13. ❌ `getmulticlusters.fn` - Get remote clusters list
14. ❌ `getremoteeula.fn` - Get remote EULA
15. ❌ `getsharesettingstag.fn` - Get tag share settings
16. ✅ `gettags_webapp.fn` - Get tags list
17. ✅ `gettranslate_json.fn` - Get video transcription
18. ❌ `getusersandemail.fn` - Get users and emails
19. ❌ `invitation_webapp.fn` - Get invitation link
20. ✅ `login.fn` - User login
21. ✅ `nodeinfo.fn` - Get node/server information
22. ✅ `query.fn` - Search/query files
23. ❌ `querymulticluster.fn` - Query remote cluster
24. ❌ `removemulticluster.fn` - Remove remote cluster
25. ❌ `saveloginmulticluster.fn` - Save cluster login
26. ❌ `serverproperty.fn` - Get server property
27. ❌ `serverupdateproperty.fn` - Update server property
28. ✅ `sidebar.fn` - Get sidebar data (file counts)
29. ✅ `suggest.fn` - Get search suggestions

## React App (uiv5) - Implemented .fn Endpoints

1. ✅ `applytags.fn` - Implemented in `src/services/fileApi.ts` (used for both adding and removing tags)
2. ✅ `chat_clear.fn` - Implemented in `src/services/chatApi.ts`
3. ✅ `chat_pull.fn` - Implemented in `src/services/chatApi.ts`
4. ✅ `chat_push.fn` - Implemented in `src/services/chatApi.ts`
5. ✅ `downloadmulti.fn` - Implemented in `src/services/fileApi.ts`
6. ✅ `getfile.fn` - Implemented in `src/services/fileApi.ts`
7. ✅ `gettags_webapp.fn` - Implemented in `src/services/fileApi.ts`
8. ✅ `gettranslate_json.fn` - Implemented in `src/components/transcript/TranscriptPanel.tsx`
9. ✅ `login.fn` - Implemented in `src/services/authApi.ts`
10. ✅ `nodeinfo.fn` - Implemented in `src/services/fileApi.ts`
11. ✅ `query.fn` - Implemented in `src/services/fileApi.ts`
12. ✅ `sidebar.fn` - Implemented in `src/services/fileApi.ts`
13. ✅ `suggest.fn` - Implemented in `src/services/fileApi.ts`

## Missing Endpoints (Not Yet Implemented in React)

### User Management
- ❌ `adduser.fn` - Add new user
- ❌ `getusersandemail.fn` - Get users and emails list

### Sharing Features
- ❌ `doshare_webapp.fn` - Share tag with users
- ❌ `getsharesettingstag.fn` - Get tag share settings
- ❌ `invitation_webapp.fn` - Generate invitation link

### Multi-Cluster (Remote Server) Features
- ❌ `addmulticluster.fn` - Add remote cluster connection
- ❌ `getmulticlusters.fn` - Get list of remote clusters
- ❌ `querymulticluster.fn` - Query files on remote cluster
- ❌ `removemulticluster.fn` - Remove remote cluster
- ❌ `saveloginmulticluster.fn` - Save cluster credentials
- ❌ `getremoteeula.fn` - Get remote EULA

### Server Configuration
- ❌ `serverproperty.fn` - Get server property value
- ❌ `serverupdateproperty.fn` - Update server property
- ❌ `getbackupconfig.fn` - Get backup configuration
- ❌ `getextensions.fn` - Get configured file extensions

### Social Features
- ❌ `fbpublish.fn` - Publish file to Facebook

## Summary

**Total AngularJS Endpoints**: 29
**Implemented in React**: 13 (45%)
**Missing from React**: 16 (55%)

### Priority for Implementation

#### High Priority (Core Features)
- None - All core file browsing, searching, tagging, and chat features are implemented

#### Medium Priority (Admin/Power User Features)
1. **User Management** (`adduser.fn`, `getusersandemail.fn`)
2. **Sharing** (`doshare_webapp.fn`, `getsharesettingstag.fn`, `invitation_webapp.fn`)
3. **Server Config** (`serverproperty.fn`, `serverupdateproperty.fn`)

#### Low Priority (Advanced/Optional Features)
1. **Multi-Cluster** (All cluster-related endpoints - for connecting to remote Alterante servers)
2. **Backup Config** (`getbackupconfig.fn`, `getextensions.fn`)
3. **Social** (`fbpublish.fn`)

## Notes

- The React app has successfully implemented all **core user-facing features** needed for browsing, searching, viewing, downloading, tagging, and chatting
- Missing features are primarily **administrative**, **sharing**, and **multi-cluster** functionality
- Multi-cluster features allow connecting multiple Alterante servers together - this is an advanced feature used in distributed deployments
- Sharing features allow users to share tags/files with other users via invitation links

## Implementation Details

### Tag Management
- **Add Tags**: Uses `applytags.fn` with parameters `tag=<tagname>&<md5>=on`
- **Remove Tags**: Uses `applytags.fn` with parameters `tag=<tagname>&DeleteTag=<md5>`
- Both operations are implemented in `src/services/fileApi.ts`
- Tag management is available in:
  - File list view (hover over tags to remove)
  - File grid view (hover over tags to remove)
  - Selection toolbar (bulk tagging)
  - Right sidebar Tags panel (when viewing a file)

### Search & Suggestions
- **Search Autocomplete**: Implemented with MUI Autocomplete component with 300ms debouncing
- **Suggestions API**: Returns objects with `{name, type}` structure
- Properly handles both file name suggestions and tag suggestions

### Video Transcription
- **Transcript Display**: Implemented in right sidebar as 4th tab for video files
- **API**: Fetches from `gettranslate_json.fn` with MD5 parameter
- **Format**: Converts nanosecond timestamps to seconds for video sync
- **Auto-scroll**: Highlights and scrolls to active segment based on video playback time

### Chunked Downloads
- Implemented for single file downloads with progress tracking
- Uses streaming with chunks for large files
- Shows download progress modal with speed and estimated time
