# Summary

This MD file contains list of known bugs in alt-core uiv5 frontend.

## Frontend Bugs

FE.1 - [FIXED 2026-02-04] In the uiv5 frontend, there is a sidebar on the right that allows user to see chat messages and events.  There are tabs for chat, playlist, and events.

The events tab shows list of events such as user logins.

The event list is populated every X seconds.  The bug is that the event list is showing duplicated events.

**Fix:** The `loadEvents` function in `EventsPanel.tsx` had a stale closure bug - the `events` array was captured as empty when the useEffect first ran, so `lastEventId` was always 0 on subsequent polls. Fixed by using a ref (`lastEventIdRef`) to track the last event ID, and added deduplication logic as a safety net. 

FE.2 - [FIXED 2026-02-04] In the Files View, users can click the download icon in a file row to download a file.  This opens up a modal and starts the Download process. Let'call this modal "Download Manager". It downloads the file in chunks and shows the download progress.

When a file is clicked, the full screen mode is activated and the media is shown. While the user views photos or streams the video, they can also download file by clicking on the download icon in the nav bar.

The bug is that the download icon in full screen mode is not working, it should open the modal and start the download process, similar behavior to when the user is in the File View.

**Fix:** The full screen viewers (VideoPlayer, ImageViewer, PdfViewer, DocumentViewer) were using simple `window.open()` or `document.createElement('a')` for downloads instead of the `useFileDownload` hook. Updated all four viewers to:
1. Import and use `useFileDownload` hook
2. Replace direct download calls with `startDownload(file)`
3. Add `DownloadProgressModal` component to show download progress

FE.3 - In the Folder View, there is a sidebar on the right side that shows information about folder permissions, when a folder is clicked.  

Only the admin user can set permissions on folders. 

The bug is when a non-admin user clicks on a Folder, a blank sidebar is shown.  

Here is the desired behavior when a folder is single-clicked (selected).
1- For admin, keep current behavior. Show permissions.
2- For non-admin, When a user single clicks on a folder, the sidebar should only display Folder Details, Name and Path, but not allow user to see or change permissions.

FE.4 - In the Folder View, when a user single clicks a file , nothing happens.

Here is the desired behavior when a file is single-clicked (selected):

1-For all users (admin and non-admin) show the information about the file in the right sidebar. We can use same sidebar currently used to show information about a folder.

The information shown should be name, path, date, file size, and tags on the file.

2-For admin, we can add extra functionality later.

## Backend Bugs
