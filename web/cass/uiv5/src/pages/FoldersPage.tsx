/**
 * Folders Page - Desktop-style folder browser
 */

import { useEffect, useMemo, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Box, Typography, Card, CardContent, CircularProgress, IconButton, Button, Menu, MenuItem, Checkbox } from '@mui/material';
import {
  Folder as FolderIcon,
  InsertDriveFile as FileIcon,
  ArrowBack as ArrowBackIcon,
  PictureAsPdf as PdfIcon,
  Description as DocIcon,
  TableChart as XlsIcon,
  Slideshow as PptIcon,
  Image as ImageIcon,
  VideoFile as VideoIcon,
  AudioFile as AudioIcon,
} from '@mui/icons-material';
import { fetchFolders, fetchFileInfo } from '../services/fileApi';
import { checkFolderPermission } from '../services/folderPermissionApi';
import { ImageViewer } from '../features/media/ImageViewer';
import { VideoPlayer } from '../features/media/VideoPlayer';
import { PdfViewer } from '../features/media/PdfViewer';
import { DocumentViewer } from '../features/media/DocumentViewer';
import { FolderInfoSidebar } from '../components/folders/FolderInfoSidebar';
import { FolderTreeSidebar } from '../components/folders/FolderTreeSidebar';
import { useMediaViewer } from '../hooks/useMediaViewer';
import { useDownloadManager } from '../contexts/DownloadManagerContext';
import { useFolderUpload } from '../contexts/FolderUploadContext';
import { useFolderFileSelection } from '../hooks/useFolderFileSelection';
import { SelectionToolbar } from '../features/files/SelectionToolbar';
import { selectFolder, selectSelectedFolder, selectIsSidebarOpen } from '../store/slices/folderPermissionsSlice';
import type { AppDispatch } from '../store/store';
import type { Folder } from '../services/fileApi';
import type { File } from '../types/models';

export function FoldersPage() {
  const dispatch = useDispatch<AppDispatch>();
  const selectedFolder = useSelector(selectSelectedFolder);
  const isSidebarOpen = useSelector(selectIsSidebarOpen);
  const { setCurrentFolder: setUploadCurrentFolder, setIsOnFoldersPage } = useFolderUpload();

  const [folders, setFolders] = useState<Folder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentFolder, setCurrentFolder] = useState<string>('scanfolders');
  const [breadcrumbs, setBreadcrumbs] = useState<string[]>(['scanfolders']);
  const [imageFiles, setImageFiles] = useState<File[]>([]);
  const [treeViewOpen, setTreeViewOpen] = useState(false);

  // Notify context that we're on the Folders page and update current folder
  useEffect(() => {
    setIsOnFoldersPage(true);
    return () => {
      setIsOnFoldersPage(false);
    };
  }, [setIsOnFoldersPage]);

  // Update upload context when current folder changes
  useEffect(() => {
    setUploadCurrentFolder(currentFolder);
  }, [currentFolder, setUploadCurrentFolder]);

  // Menu state
  const [fileMenuAnchor, setFileMenuAnchor] = useState<null | HTMLElement>(null);
  const [editMenuAnchor, setEditMenuAnchor] = useState<null | HTMLElement>(null);
  const [helpMenuAnchor, setHelpMenuAnchor] = useState<null | HTMLElement>(null);

  // Date and time state
  const [currentDateTime, setCurrentDateTime] = useState(new Date());

  // Update time every second
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentDateTime(new Date());
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  const {
    imageViewerOpen,
    currentImageIndex,
    openImageViewer,
    closeImageViewer,
    videoPlayerOpen,
    currentVideoFile,
    openVideoPlayer,
    closeVideoPlayer,
    pdfViewerOpen,
    currentPdfFile,
    openPdfViewer,
    closePdfViewer,
    documentViewerOpen,
    currentDocumentFile,
    openDocumentViewer,
    closeDocumentViewer,
  } = useMediaViewer();

  const { addToQueue } = useDownloadManager();

  // Multi-select for batch actions on files in the current folder.
  // See internal/PROJECT_FOLDER_MULTISELECT.md.
  const folderSelection = useFolderFileSelection(folders);

  useEffect(() => {
    loadFolders(currentFolder);
    // Clear multi-select on folder change (per spec Q2 — selection is tied
    // to the visible list, like the file view).
    folderSelection.deselectAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentFolder]);

  // Keyboard shortcuts on the FoldersPage:
  //   Cmd/Ctrl+A   → select all files in the current folder
  //   Esc          → clear selection (also closes the ribbon)
  // We skip the handler when focus is in an input/textarea/contenteditable,
  // so users typing in dialogs / search fields don't accidentally trigger
  // a select-all.
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (target) {
        const tag = target.tagName;
        if (
          tag === 'INPUT' ||
          tag === 'TEXTAREA' ||
          tag === 'SELECT' ||
          target.isContentEditable
        ) {
          return;
        }
      }
      const isCmd = e.metaKey || e.ctrlKey;
      if (isCmd && (e.key === 'a' || e.key === 'A')) {
        e.preventDefault();
        folderSelection.selectAll();
      } else if (e.key === 'Escape' && folderSelection.selectedCount > 0) {
        folderSelection.deselectAll();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [folderSelection.selectedCount, folders]);

  const loadFolders = async (sFolder: string) => {
    try {
      // Don't show loading spinner on navigation, only on initial load
      if (folders.length === 0) {
        setLoading(true);
      }
      const data = await fetchFolders(sFolder);

      // Filter folders by permission (only filter folders, not files)
      const foldersOnly = data.filter((f) => f.type !== 'file');
      const filesOnly = data.filter((f) => f.type === 'file');

      // Check permissions for each folder
      const foldersWithPermissions = await Promise.all(
        foldersOnly.map(async (folder) => {
          // Build the full path for permission check
          const folderPath = sFolder === 'scanfolders' ? folder.name : `${sFolder}/${folder.name}`;
          const permission = await checkFolderPermission(folderPath);
          return { folder, permission };
        })
      );

      // Filter out folders with 'none' permission and combine with files
      const allowedFolders = foldersWithPermissions
        .filter(({ permission }) => permission !== 'none')
        .map(({ folder }) => folder);

      setFolders([...allowedFolders, ...filesOnly]);

      // Extract image files for the viewer
      const images = data.filter(f => f.type === 'file' && getFileGroup(f.name) === 'photo').map(f => ({
        nickname: f.md5!,
        name: decodeFolderName(f.name),
        file_ext: f.name.substring(f.name.lastIndexOf('.')),
        file_group: 'photo' as const,
        file_date_long: Date.now(),
        file_size: 0,
        multiclusterid: f.md5!,
        file_thumbnail: '',
        file_tags: '',
        file_path_webapp: `/cass/getfile.fn?sNamer=${f.md5}`,
        md5hash: f.md5!,
        file_name: decodeFolderName(f.name),
        file_date: new Date().toISOString(),
        file_path: '',
      }));
      setImageFiles(images);

      setError(null);
    } catch (err: any) {
      console.error('Failed to fetch folders:', err);
      setError(err.message || 'Failed to load folders');
    } finally {
      setLoading(false);
    }
  };

  // function declarations are hoisted; arrow consts aren't. folderToFile
  // (below) uses these during the first render via useMemo, which would
  // trigger a temporal-dead-zone ReferenceError if these were const arrows.
  function decodeFolderName(name: string): string {
    try {
      return decodeURIComponent(name);
    } catch (e) {
      return name;
    }
  }

  // Convert a Folder item (file flavor) into the File shape used by viewers,
  // download manager, and the SelectionToolbar. Returns null for non-file items.
  // Function declaration (not const arrow) so it's hoisted — the toolbarSelection
  // useMemo below calls this during the first render.
  function folderToFile(folder: Folder): File | null {
    if (folder.type !== 'file' || !folder.md5) return null;
    const fileGroup = getFileGroup(folder.name);
    const filePath = `/cass/getfile.fn?sNamer=${folder.md5}`;
    const videoUrl = fileGroup === 'movie' ? `getvideo.m3u8?md5=${folder.md5}` : undefined;
    return {
      nickname: folder.md5,
      name: decodeFolderName(folder.name),
      file_ext: folder.name.substring(folder.name.lastIndexOf('.')),
      file_group: fileGroup,
      file_date_long: Date.now(),
      file_size: 0,
      multiclusterid: folder.md5,
      file_thumbnail: '',
      file_tags: '',
      file_path_webapp: filePath,
      video_url_webapp: videoUrl,
      md5hash: folder.md5,
      file_name: decodeFolderName(folder.name),
      file_date: new Date().toISOString(),
      file_path: '',
    };
  }

  // SelectionToolbar contract: it expects File[] for selectedFiles. The hook
  // returns Folder[] (file-flavor only), so map through folderToFile.
  const toolbarSelection = useMemo(
    () => ({
      selectedCount: folderSelection.selectedCount,
      selectedFiles: folderSelection.selectedFiles
        .map(folderToFile)
        .filter((f): f is File => f !== null),
      selectedFileIds: folderSelection.selectedFileIds,
      deselectAll: folderSelection.deselectAll,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [folderSelection.selectedCount, folderSelection.selectedFileIds, folderSelection.selectedFiles],
  );

  // Download handler for selected files in the folder view. We can't reuse
  // the toolbar's default (which does `selectedFiles.forEach(addToQueue)`)
  // because folderToFile() builds File objects with file_size: 0 (Folder
  // type from getfolders-json.fn doesn't carry size). The DownloadManager
  // uses file_size to (a) decide chunked-vs-direct and (b) compute
  // bytes-left progress. With size=0 it picks the direct path AND shows
  // negative bytes-left.
  //
  // Fix: hydrate via getfileinfo.fn per MD5 in parallel before queueing.
  const handleMultiDownload = async () => {
    const ids = folderSelection.selectedFileIds;
    if (ids.length === 0) return;
    try {
      const infos = await Promise.all(ids.map((id) => fetchFileInfo(id)));
      infos.forEach((info) => {
        const file: File = {
          nickname: info.nickname,
          name: info.name,
          file_ext: info.file_ext,
          file_group: info.file_group as File['file_group'],
          file_date_long: info.file_date_long,
          file_size: info.file_size,
          multiclusterid: info.nickname,
          file_thumbnail: info.file_thumbnail || '',
          file_tags: info.file_tags,
          file_path_webapp: info.file_path_webapp,
          video_url_webapp: info.video_url_webapp,
          md5hash: info.nickname,
          file_name: info.name,
          file_date: info.file_date,
          file_path: '',
        };
        addToQueue(file);
      });
    } catch (err) {
      console.error('handleMultiDownload: failed to hydrate file info', err);
      // Fall back to the size=0 path so the user at least gets the bytes,
      // even if the progress display will be off.
      toolbarSelection.selectedFiles.forEach((f) => addToQueue(f));
    }
  };

  const handleFolderClick = (folder: Folder) => {
    console.log('Item clicked:', folder);

    if (folder.type === 'file' && folder.md5) {
      const file = folderToFile(folder);
      if (!file) return;

      // Open appropriate viewer based on file type
      const ext = file.file_ext?.toLowerCase() || '';

      if (file.file_group === 'photo') {
        // Find the index of this image in the imageFiles array
        const index = imageFiles.findIndex(f => f.nickname === file.nickname);
        if (index !== -1) {
          openImageViewer(index);
        }
      } else if (file.file_group === 'movie') {
        openVideoPlayer(file);
      } else if (ext === '.pdf' || file.name?.toLowerCase().endsWith('.pdf')) {
        openPdfViewer(file);
      } else if (file.file_group === 'document') {
        openDocumentViewer(file);
      } else {
        // For other files, download
        addToQueue(file);
      }
    } else {
      // If it's a folder (or no type specified), navigate into it
      // Append the folder name to the current path (skip "scanfolders" prefix)
      const newPath = currentFolder === 'scanfolders' ? folder.name : `${currentFolder}/${folder.name}`;
      setCurrentFolder(newPath);
      // Add to breadcrumbs for navigation
      setBreadcrumbs([...breadcrumbs, folder.name]);
    }
  };

  function getFileGroup(fileName: string): string {
    const ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();

    const imageExts = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp'];
    const videoExts = ['.mp4', '.avi', '.mkv', '.mov', '.wmv', '.flv', '.webm'];
    const audioExts = ['.mp3', '.wav', '.flac', '.aac', '.ogg', '.m4a'];
    const docExts = ['.doc', '.docx', '.txt', '.rtf', '.odt'];

    if (imageExts.includes(ext)) return 'photo';
    if (videoExts.includes(ext)) return 'movie';
    if (audioExts.includes(ext)) return 'music';
    if (docExts.includes(ext)) return 'document';
    if (ext === '.pdf') return 'document';

    return 'document';
  }

  const getFileIcon = (fileName: string) => {
    const ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();

    // PDF files
    if (ext === '.pdf') {
      return { Icon: PdfIcon, color: '#EF4444' }; // Red
    }

    // Excel files
    if (['.xls', '.xlsx', '.xlsm', '.csv'].includes(ext)) {
      return { Icon: XlsIcon, color: '#10B981' }; // Green
    }

    // PowerPoint files
    if (['.ppt', '.pptx', '.pptm'].includes(ext)) {
      return { Icon: PptIcon, color: '#F59E0B' }; // Amber
    }

    // Word/Document files
    if (['.doc', '.docx', '.txt', '.rtf', '.odt'].includes(ext)) {
      return { Icon: DocIcon, color: '#3B82F6' }; // Blue
    }

    // Image files
    if (['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.svg', '.ico'].includes(ext)) {
      return { Icon: ImageIcon, color: '#8B5CF6' }; // Purple
    }

    // Video files
    if (['.mp4', '.avi', '.mkv', '.mov', '.wmv', '.flv', '.webm', '.m4v'].includes(ext)) {
      return { Icon: VideoIcon, color: '#EC4899' }; // Pink
    }

    // Audio files
    if (['.mp3', '.wav', '.flac', '.aac', '.ogg', '.m4a', '.wma'].includes(ext)) {
      return { Icon: AudioIcon, color: '#14B8A6' }; // Teal
    }

    // Default file icon
    return { Icon: FileIcon, color: 'white' };
  };

  // Single-click handler to select a folder or file and show the info sidebar
  const handleFolderSelect = (folder: Folder) => {
    // Build the full path
    const itemPath = currentFolder === 'scanfolders'
      ? folder.name
      : `${currentFolder}/${folder.name}`;

    dispatch(selectFolder({
      name: folder.name,
      path: itemPath,
      count: folder.count,
      type: folder.type,
      md5: folder.md5,
    }));
  };

  const handleGoBack = () => {
    if (breadcrumbs.length <= 1) return; // Already at root (scanfolders)

    // Remove the last breadcrumb
    const newBreadcrumbs = breadcrumbs.slice(0, -1);
    setBreadcrumbs(newBreadcrumbs);

    // If we're back to just scanfolders, set that as current
    if (newBreadcrumbs.length === 1 && newBreadcrumbs[0] === 'scanfolders') {
      setCurrentFolder('scanfolders');
    } else {
      // Rebuild the path from breadcrumbs (excluding 'scanfolders')
      const pathParts = newBreadcrumbs.filter(b => b !== 'scanfolders');
      const newPath = pathParts.join('/');
      setCurrentFolder(newPath);
    }
  };

  // Handle navigation from tree view
  const handleTreeNavigate = (folderPath: string) => {
    // Build breadcrumbs from the path
    const pathParts = folderPath.split('/');
    const newBreadcrumbs = ['scanfolders', ...pathParts];
    setBreadcrumbs(newBreadcrumbs);
    setCurrentFolder(folderPath);
  };

  const handleTreeToggle = () => {
    setTreeViewOpen((prev) => !prev);
  };

  const SIDEBAR_WIDTH = 320;
  const TREE_SIDEBAR_WIDTH = 280;

  // Render loading/error states but keep TreeSidebar mounted
  if (loading) {
    return (
      <>
        {/* Keep TreeSidebar mounted during loading */}
        <FolderTreeSidebar
          open={treeViewOpen}
          onToggle={handleTreeToggle}
          onFolderNavigate={handleTreeNavigate}
        />
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: 400,
            background: '#004080',
            marginLeft: treeViewOpen ? `${TREE_SIDEBAR_WIDTH}px` : 0,
            transition: 'margin-left 0.3s ease',
          }}
        >
          <CircularProgress sx={{ color: 'white' }} />
        </Box>
      </>
    );
  }

  if (error) {
    return (
      <>
        {/* Keep TreeSidebar mounted during error */}
        <FolderTreeSidebar
          open={treeViewOpen}
          onToggle={handleTreeToggle}
          onFolderNavigate={handleTreeNavigate}
        />
        <Box sx={{ p: 3, background: '#004080', minHeight: '100vh' }}>
          <Typography color="error">{error}</Typography>
        </Box>
      </>
    );
  }

  return (
    <>
      {/* Left Tree Sidebar */}
      <FolderTreeSidebar
        open={treeViewOpen}
        onToggle={handleTreeToggle}
        onFolderNavigate={handleTreeNavigate}
      />

      <Box
        sx={{
          height: '100%',
          background: '#004080',
          overflow: 'auto',
          display: 'flex',
          flexDirection: 'column',
          // Adjust for sidebars when open
          marginLeft: treeViewOpen ? `${TREE_SIDEBAR_WIDTH}px` : 0,
          marginRight: isSidebarOpen ? `${SIDEBAR_WIDTH}px` : 0,
          transition: 'margin-left 0.3s ease, margin-right 0.3s ease',
        }}
      >
        {/* Menu Bar */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          px: 2,
          py: 0.5,
          backgroundColor: 'rgba(255, 255, 255, 0.1)',
          borderBottom: '1px solid rgba(255, 255, 255, 0.2)',
        }}
      >
        {/* Left side - Logo and menus */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <img
            src={`${import.meta.env.BASE_URL}logo_molecula.png`}
            alt="Molecula"
            style={{ height: 20, marginRight: 8 }}
          />

          <Button
            size="small"
            sx={{ color: 'white', textTransform: 'none', minWidth: 'auto', px: 1.5 }}
            onClick={(e) => setFileMenuAnchor(e.currentTarget)}
          >
            File
          </Button>

          <Button
            size="small"
            sx={{ color: 'white', textTransform: 'none', minWidth: 'auto', px: 1.5 }}
            onClick={(e) => setEditMenuAnchor(e.currentTarget)}
          >
            Edit
          </Button>

          <Button
            size="small"
            sx={{ color: 'white', textTransform: 'none', minWidth: 'auto', px: 1.5 }}
            onClick={(e) => setHelpMenuAnchor(e.currentTarget)}
          >
            Help
          </Button>
        </Box>

        {/* Right side - Date and time */}
        <Typography
          variant="body2"
          sx={{
            color: 'white',
            fontFamily: 'monospace',
            fontSize: '0.875rem',
          }}
        >
          {currentDateTime.toLocaleDateString('en-US', {
            weekday: 'short',
            month: 'short',
            day: 'numeric',
          })}{' '}
          {currentDateTime.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </Typography>
      </Box>

      {/* Content Area
          The top of this scroll container is split in two:
            - A 64px-high "toolbar slot" that's ALWAYS present (sticky, transparent
              when empty, the blue ribbon when files are selected). Reserving the
              space unconditionally means the breadcrumb/grid never jump when
              the ribbon appears or disappears.
            - The rest scrolls normally below. */}
      <Box sx={{ flex: 1, overflow: 'auto', p: 3, pt: 0 }}>
        {/* Sticky toolbar slot — collapses to nothing when 0 files selected,
            expands smoothly to ribbon height when files are selected. The
            animated transition prevents the abrupt push-down/pull-up of the
            grid below. Always rendered (just zero-height when inactive) so
            the sticky/scroll behavior is consistent. */}
        <Box
          sx={{
            position: 'sticky',
            top: 0,
            zIndex: 10,
            mx: -3,           // span the parent's horizontal padding
            px: 3,
            pt: folderSelection.selectedCount > 0 ? 2 : 3,
            pb: folderSelection.selectedCount > 0 ? 2 : 0,
            mb: folderSelection.selectedCount > 0 ? 1 : 0,
            backgroundColor: '#004080',  // match page background; covers content scrolling underneath
            transition: 'padding-top 0.18s ease, padding-bottom 0.18s ease, margin-bottom 0.18s ease',
          }}
        >
          {folderSelection.selectedCount > 0 && (
            <SelectionToolbar
              inline
              selection={toolbarSelection}
              onAfterTag={() => loadFolders(currentFolder)}
              onDownloadOverride={handleMultiDownload}
            />
          )}
        </Box>
        {currentFolder !== 'scanfolders' && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
          {breadcrumbs.length > 1 && (
            <IconButton
              onClick={handleGoBack}
              sx={{
                color: 'white',
                backgroundColor: 'rgba(255, 255, 255, 0.2)',
                '&:hover': {
                  backgroundColor: 'rgba(255, 255, 255, 0.3)',
                },
              }}
            >
              <ArrowBackIcon />
            </IconButton>
          )}
          <Typography
            variant="h5"
            sx={{
              color: 'white',
              fontStyle: 'italic',
              fontSize: '1.5rem',
            }}
          >
            {decodeFolderName(currentFolder)}
          </Typography>
        </Box>
      )}

      {folders.length === 0 ? (
        <Box
          sx={{
            textAlign: 'center',
            py: 8,
            color: 'white',
          }}
        >
          <FolderIcon sx={{ fontSize: 80, mb: 2, opacity: 0.7 }} />
          <Typography variant="h6">No folders found</Typography>
        </Box>
      ) : (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))',
            gap: 3,
            maxWidth: '100%',
          }}
        >
          {folders.map((folder, index) => {
            const { Icon, color } = folder.type === 'file' ? getFileIcon(folder.name) : { Icon: FolderIcon, color: '#FFD700' };
            const isFile = folder.type === 'file' && !!folder.md5;
            const isMultiSelected = isFile && folderSelection.isSelected(folder.md5);

            return (
              <Card
                key={index}
                onClick={() => handleFolderSelect(folder)}
                onDoubleClick={() => handleFolderClick(folder)}
                sx={{
                  position: 'relative',
                  cursor: 'pointer',
                  backgroundColor: isMultiSelected
                    ? 'rgba(33, 150, 243, 0.25)'  // file-view-style blue tint for multi-selected
                    : selectedFolder?.name === folder.name
                      ? 'rgba(255, 255, 255, 0.2)'
                      : 'transparent',
                  boxShadow: 'none',
                  transition: 'all 0.2s ease-in-out',
                  border: isMultiSelected
                    ? '2px solid rgba(33, 150, 243, 0.9)'  // distinct blue ring for multi-select
                    : selectedFolder?.name === folder.name
                      ? '2px solid rgba(255, 255, 255, 0.5)'
                      : '2px solid transparent',
                  borderRadius: 2,
                  '&:hover': {
                    transform: 'translateY(-4px)',
                    backgroundColor: isMultiSelected
                      ? 'rgba(33, 150, 243, 0.3)'
                      : 'rgba(255, 255, 255, 0.1)',
                  },
                  // Reveal the checkbox on hover (or when already selected).
                  '&:hover .folder-multiselect-checkbox': { opacity: 1 },
                }}
              >
                {isFile && (
                  <Checkbox
                    className="folder-multiselect-checkbox"
                    size="small"
                    checked={isMultiSelected}
                    onClick={(e) => e.stopPropagation()}
                    onDoubleClick={(e) => e.stopPropagation()}
                    onChange={(e) => {
                      // React's synthetic event for the change is good enough — we only
                      // need shift/ctrl/meta from a real MouseEvent for range/multi-select.
                      // Since the change handler can't see modifier keys reliably across
                      // browsers, route through toggleSelect (plain toggle). Range
                      // selection via shift-click is wired in P3 / via card-level click.
                      e.stopPropagation();
                      folderSelection.toggleSelect(folder.md5 as string);
                    }}
                    sx={{
                      position: 'absolute',
                      top: 4,
                      left: 4,
                      zIndex: 2,
                      padding: '4px',
                      color: 'rgba(255, 255, 255, 0.8)',
                      backgroundColor: 'rgba(0, 0, 0, 0.4)',
                      borderRadius: '4px',
                      opacity: isMultiSelected ? 1 : 0,
                      transition: 'opacity 0.15s ease-in-out',
                      '&.Mui-checked': {
                        color: '#2196f3',
                        backgroundColor: 'rgba(255, 255, 255, 0.95)',
                      },
                      '&:hover': {
                        backgroundColor: 'rgba(0, 0, 0, 0.55)',
                      },
                    }}
                  />
                )}
                <CardContent
                  sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    py: 3,
                    px: 2,
                    textAlign: 'center',
                  }}
                >
                  <Icon
                    sx={{
                      fontSize: 64,
                      color,
                      mb: 1,
                      filter: 'drop-shadow(0 2px 4px rgba(0,0,0,0.3))',
                    }}
                  />
                <Typography
                  variant="caption"
                  sx={{
                    fontWeight: 600,
                    fontSize: '0.75rem',
                    wordBreak: 'break-word',
                    lineHeight: 1.3,
                    maxHeight: '3.9em', // 3 lines
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    display: '-webkit-box',
                    WebkitLineClamp: 3,
                    WebkitBoxOrient: 'vertical',
                    color: 'white',
                    textShadow: '0 1px 3px rgba(0,0,0,0.8)',
                  }}
                  title={decodeFolderName(folder.name)}
                >
                  {decodeFolderName(folder.name)}
                </Typography>
                {folder.count !== undefined && (
                  <Typography variant="caption" sx={{ mt: 0.5, color: 'rgba(255,255,255,0.9)', textShadow: '0 1px 2px rgba(0,0,0,0.6)' }}>
                    {folder.count} {folder.count === 1 ? 'item' : 'items'}
                  </Typography>
                )}
              </CardContent>
            </Card>
            );
          })}
        </Box>
      )}

      {/* Media Viewers */}
      {imageViewerOpen && imageFiles.length > 0 && (
        <ImageViewer
          open={imageViewerOpen}
          onClose={closeImageViewer}
          files={imageFiles}
          initialIndex={currentImageIndex}
        />
      )}

      {videoPlayerOpen && currentVideoFile && (
        <VideoPlayer
          open={videoPlayerOpen}
          onClose={closeVideoPlayer}
          file={currentVideoFile}
        />
      )}

      {pdfViewerOpen && currentPdfFile && (
        <PdfViewer
          onClose={closePdfViewer}
          file={currentPdfFile}
        />
      )}

      {documentViewerOpen && currentDocumentFile && (
        <DocumentViewer
          open={documentViewerOpen}
          onClose={closeDocumentViewer}
          file={currentDocumentFile}
        />
      )}

      </Box>

      {/* Menu Dropdowns */}
      <Menu
        anchorEl={fileMenuAnchor}
        open={Boolean(fileMenuAnchor)}
        onClose={() => setFileMenuAnchor(null)}
      >
        <MenuItem onClick={() => setFileMenuAnchor(null)}>New Folder</MenuItem>
        <MenuItem onClick={() => setFileMenuAnchor(null)}>Upload File</MenuItem>
        <MenuItem onClick={() => setFileMenuAnchor(null)}>Refresh</MenuItem>
      </Menu>

      <Menu
        anchorEl={editMenuAnchor}
        open={Boolean(editMenuAnchor)}
        onClose={() => setEditMenuAnchor(null)}
      >
        <MenuItem onClick={() => setEditMenuAnchor(null)}>Cut</MenuItem>
        <MenuItem onClick={() => setEditMenuAnchor(null)}>Copy</MenuItem>
        <MenuItem onClick={() => setEditMenuAnchor(null)}>Paste</MenuItem>
        <MenuItem onClick={() => setEditMenuAnchor(null)}>Delete</MenuItem>
      </Menu>

      <Menu
        anchorEl={helpMenuAnchor}
        open={Boolean(helpMenuAnchor)}
        onClose={() => setHelpMenuAnchor(null)}
      >
        <MenuItem onClick={() => setHelpMenuAnchor(null)}>About</MenuItem>
        <MenuItem onClick={() => setHelpMenuAnchor(null)}>Documentation</MenuItem>
        <MenuItem onClick={() => setHelpMenuAnchor(null)}>Keyboard Shortcuts</MenuItem>
      </Menu>

        {/* Folder Info Sidebar (admin only) */}
        <FolderInfoSidebar />
      </Box>
    </>
  );
}
