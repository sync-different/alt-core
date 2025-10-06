/**
 * Media Viewer Hook
 * Manages state for opening image viewer or video player
 */

import { useState, useCallback } from 'react';
import type { File } from '../types/models';

export function useMediaViewer() {
  const [imageViewerOpen, setImageViewerOpen] = useState(false);
  const [videoPlayerOpen, setVideoPlayerOpen] = useState(false);
  const [pdfViewerOpen, setPdfViewerOpen] = useState(false);
  const [documentViewerOpen, setDocumentViewerOpen] = useState(false);
  const [currentImageIndex, setCurrentImageIndex] = useState(0);
  const [currentVideoFile, setCurrentVideoFile] = useState<File | null>(null);
  const [currentPdfFile, setCurrentPdfFile] = useState<File | null>(null);
  const [currentDocumentFile, setCurrentDocumentFile] = useState<File | null>(null);

  const openImageViewer = useCallback((index: number) => {
    setCurrentImageIndex(index);
    setImageViewerOpen(true);
  }, []);

  const closeImageViewer = useCallback(() => {
    setImageViewerOpen(false);
  }, []);

  const openVideoPlayer = useCallback((file: File) => {
    setCurrentVideoFile(file);
    setVideoPlayerOpen(true);
  }, []);

  const closeVideoPlayer = useCallback(() => {
    setVideoPlayerOpen(false);
    setCurrentVideoFile(null);
  }, []);

  const openPdfViewer = useCallback((file: File) => {
    setCurrentPdfFile(file);
    setPdfViewerOpen(true);
  }, []);

  const closePdfViewer = useCallback(() => {
    setPdfViewerOpen(false);
    setCurrentPdfFile(null);
  }, []);

  const openDocumentViewer = useCallback((file: File) => {
    setCurrentDocumentFile(file);
    setDocumentViewerOpen(true);
  }, []);

  const closeDocumentViewer = useCallback(() => {
    setDocumentViewerOpen(false);
    setCurrentDocumentFile(null);
  }, []);

  return {
    // Image viewer
    imageViewerOpen,
    currentImageIndex,
    openImageViewer,
    closeImageViewer,
    // Video player
    videoPlayerOpen,
    currentVideoFile,
    openVideoPlayer,
    closeVideoPlayer,
    // PDF viewer
    pdfViewerOpen,
    currentPdfFile,
    openPdfViewer,
    closePdfViewer,
    // Document viewer
    documentViewerOpen,
    currentDocumentFile,
    openDocumentViewer,
    closeDocumentViewer,
  };
}
