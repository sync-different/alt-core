import { useCallback, useMemo, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import type { RootState, AppDispatch } from '../store/store';
import {
  toggleFileSelection,
  selectRange,
  selectAll,
  deselectAll,
} from '../store/slices/filesSlice';

/**
 * Custom hook for managing file selection state
 */
export const useFileSelection = () => {
  const dispatch = useDispatch<AppDispatch>();
  const selectedFileIds = useSelector((state: RootState) => state.files.selectedFileIds);
  const files = useSelector((state: RootState) => state.files.files);
  const lastSelectedIdRef = useRef<string | null>(null);

  const selectedFiles = useMemo(() => {
    return files.filter((file) => selectedFileIds.includes(file.nickname));
  }, [files, selectedFileIds]);

  const toggleSelect = useCallback(
    (fileId: string) => {
      dispatch(toggleFileSelection(fileId));
      lastSelectedIdRef.current = fileId;
    },
    [dispatch]
  );

  const handleClick = useCallback(
    (fileId: string, event: React.MouseEvent) => {
      if (event.shiftKey && lastSelectedIdRef.current) {
        // Range selection
        dispatch(selectRange({ startId: lastSelectedIdRef.current, endId: fileId }));
      } else if (event.ctrlKey || event.metaKey) {
        // Multi-select (Ctrl/Cmd+Click)
        dispatch(toggleFileSelection(fileId));
      } else {
        // Single selection - deselect all others first
        dispatch(deselectAll());
        dispatch(toggleFileSelection(fileId));
      }
      lastSelectedIdRef.current = fileId;
    },
    [dispatch]
  );

  const handleSelectAll = useCallback(() => {
    dispatch(selectAll());
  }, [dispatch]);

  const handleDeselectAll = useCallback(() => {
    dispatch(deselectAll());
    lastSelectedIdRef.current = null;
  }, [dispatch]);

  const isSelected = useCallback(
    (fileId: string) => {
      return selectedFileIds.includes(fileId);
    },
    [selectedFileIds]
  );

  const isAllSelected = useMemo(() => {
    return files.length > 0 && selectedFileIds.length === files.length;
  }, [files, selectedFileIds]);

  const selectedCount = selectedFileIds.length;

  return {
    selectedFiles,
    selectedFileIds,
    toggleSelect,
    handleClick,
    selectAll: handleSelectAll,
    deselectAll: handleDeselectAll,
    isSelected,
    isAllSelected,
    selectedCount,
  };
};
