/**
 * Folders Page - Desktop-style folder browser
 */

import { useEffect, useState } from 'react';
import { Box, Typography, Card, CardContent, CircularProgress, IconButton, Button, Menu, MenuItem } from '@mui/material';
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
import { fetchFolders } from '../services/fileApi';
import { ImageViewer } from '../features/media/ImageViewer';
import { VideoPlayer } from '../features/media/VideoPlayer';
import { PdfViewer } from '../features/media/PdfViewer';
import { DocumentViewer } from '../features/media/DocumentViewer';
import { DownloadProgressModal } from '../components/download/DownloadProgressModal';
import { useMediaViewer } from '../hooks/useMediaViewer';
import { useFileDownload } from '../hooks/useFileDownload';
import type { Folder } from '../services/fileApi';
import type { File } from '../types/models';

export function FoldersPage() {
  const [folders, setFolders] = useState<Folder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentFolder, setCurrentFolder] = useState<string>('scanfolders');
  const [breadcrumbs, setBreadcrumbs] = useState<string[]>(['scanfolders']);
  const [imageFiles, setImageFiles] = useState<File[]>([]);

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

  const { isDownloading, downloadProgress, isComplete, currentFile, startDownload, cancelDownload, closeModal } = useFileDownload();

  useEffect(() => {
    loadFolders(currentFolder);
  }, [currentFolder]);

  const loadFolders = async (sFolder: string) => {
    try {
      // Don't show loading spinner on navigation, only on initial load
      if (folders.length === 0) {
        setLoading(true);
      }
      const data = await fetchFolders(sFolder);
      setFolders(data);

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

  const decodeFolderName = (name: string): string => {
    try {
      return decodeURIComponent(name);
    } catch (e) {
      return name;
    }
  };

  const handleFolderClick = (folder: Folder) => {
    console.log('Item clicked:', folder);

    if (folder.type === 'file' && folder.md5) {
      // Determine the appropriate URL based on file type
      const fileGroup = getFileGroup(folder.name);
      let filePath = `/cass/getfile.fn?sNamer=${folder.md5}`;
      let videoUrl = undefined;

      // For video files, use getvideo.m3u8 for HLS streaming
      if (fileGroup === 'movie') {
        videoUrl = `getvideo.m3u8?md5=${folder.md5}`;
      }

      // Convert folder to File object for viewers
      const file: File = {
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
        startDownload(file);
      }
    } else {
      // If it's a folder (or no type specified), navigate into it
      // Append the folder name to the current path (skip "scanfolders" prefix)
      const newPath = currentFolder === 'scanfolders' ? folder.name : `${currentFolder}${folder.name}`;
      setCurrentFolder(newPath);
      // Add to breadcrumbs for navigation
      setBreadcrumbs([...breadcrumbs, folder.name]);
    }
  };

  const getFileGroup = (fileName: string): string => {
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
  };

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
      const newPath = pathParts.join('');
      setCurrentFolder(newPath);
    }
  };

  if (loading) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: 400,
          background: '#004080',
        }}
      >
        <CircularProgress sx={{ color: 'white' }} />
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ p: 3, background: '#004080', minHeight: '100vh' }}>
        <Typography color="error">{error}</Typography>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        height: '100%',
        background: '#004080',
        overflow: 'auto',
        display: 'flex',
        flexDirection: 'column',
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

      {/* Content Area */}
      <Box sx={{ flex: 1, overflow: 'auto', p: 3 }}>
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

            return (
              <Card
                key={index}
                onDoubleClick={() => handleFolderClick(folder)}
                sx={{
                  cursor: 'pointer',
                  backgroundColor: 'transparent',
                  boxShadow: 'none',
                  transition: 'all 0.2s ease-in-out',
                  '&:hover': {
                    transform: 'translateY(-4px)',
                    backgroundColor: 'rgba(255, 255, 255, 0.1)',
                  },
                }}
              >
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

      <DownloadProgressModal
        open={isDownloading || isComplete}
        fileName={currentFile?.name || ''}
        progress={downloadProgress}
        onCancel={isComplete ? closeModal : cancelDownload}
        isComplete={isComplete}
      />
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
    </Box>
  );
}
