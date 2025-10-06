/**
 * Image Viewer Hook
 * Manages state for opening and navigating the image viewer
 */

import { useState, useCallback } from 'react';

export function useImageViewer() {
  const [isOpen, setIsOpen] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);

  const openViewer = useCallback((index: number) => {
    setCurrentIndex(index);
    setIsOpen(true);
  }, []);

  const closeViewer = useCallback(() => {
    setIsOpen(false);
  }, []);

  return {
    isOpen,
    currentIndex,
    openViewer,
    closeViewer,
  };
}
