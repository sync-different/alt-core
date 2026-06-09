import { useCallback, useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import type { RootState, AppDispatch } from '../store/store';
import {
  toggleFileSelection,
  toggleFolderSelection,
  selectRange,
  selectAll,
  deselectAll,
} from '../store/slices/folderSelectionSlice';
import type { Folder } from '../services/fileApi';

/**
 * Multi-select hook for the FoldersPage. Mirrors `useFileSelection`, but the
 * caller passes the current ordered list of items in the folder (since the
 * slice doesn't own the feed — see folderSelectionSlice docstring).
 *
 * Only items with `type === 'file'` and a defined `md5` are considered
 * selectable. Folders are filtered out automatically.
 *
 * See internal/PROJECT_FOLDER_MULTISELECT.md.
 */
export const useFolderFileSelection = (items: Folder[]) => {
  const dispatch = useDispatch<AppDispatch>();
  const selectedFileIds = useSelector(
    (state: RootState) => state.folderSelection.selectedFileIds,
  );
  const lastSelectedId = useSelector(
    (state: RootState) => state.folderSelection.lastSelectedId,
  );
  const selectedFolderPaths = useSelector(
    (state: RootState) => state.folderSelection.selectedFolderPaths,
  );

  // File MD5s in display order — used as the canonical id list for shift-range
  // selection and select-all.
  const allFileIds = useMemo(
    () =>
      items
        .filter((it) => it.type === 'file' && !!it.md5)
        .map((it) => it.md5 as string),
    [items],
  );

  const selectedFiles = useMemo(
    () => items.filter((it) => it.md5 && selectedFileIds.includes(it.md5)),
    [items, selectedFileIds],
  );

  // Selected FOLDER items in the current listing (keyed by folder name). Used by the
  // folder-download flow (FF2/FF3) to recursively enumerate each one.
  const selectedFolders = useMemo(
    () => items.filter((it) => it.type === 'folder' && selectedFolderPaths.includes(it.name)),
    [items, selectedFolderPaths],
  );

  const toggleSelect = useCallback(
    (md5: string) => {
      dispatch(toggleFileSelection(md5));
    },
    [dispatch],
  );

  const toggleFolderSelect = useCallback(
    (folderName: string) => {
      dispatch(toggleFolderSelection(folderName));
    },
    [dispatch],
  );

  const isFolderSelected = useCallback(
    (folderName: string | undefined) => !!folderName && selectedFolderPaths.includes(folderName),
    [selectedFolderPaths],
  );

  const handleClick = useCallback(
    (md5: string, event: React.MouseEvent) => {
      if (event.shiftKey && lastSelectedId) {
        dispatch(selectRange({ startId: lastSelectedId, endId: md5, allIds: allFileIds }));
      } else if (event.ctrlKey || event.metaKey) {
        dispatch(toggleFileSelection(md5));
      } else {
        // Plain click on the checkbox toggles just this one (without
        // clobbering the rest). Plain card click is handled separately by
        // FoldersPage's existing single-select / open-viewer logic.
        dispatch(toggleFileSelection(md5));
      }
    },
    [dispatch, lastSelectedId, allFileIds],
  );

  const handleSelectAll = useCallback(() => {
    dispatch(selectAll(allFileIds));
  }, [dispatch, allFileIds]);

  const handleDeselectAll = useCallback(() => {
    dispatch(deselectAll());
  }, [dispatch]);

  const isSelected = useCallback(
    (md5: string | undefined) => !!md5 && selectedFileIds.includes(md5),
    [selectedFileIds],
  );

  // total selected across files AND folders — drives the blue SelectionToolbar visibility
  const selectedCount = selectedFileIds.length + selectedFolderPaths.length;
  const isAllSelected =
    allFileIds.length > 0 && selectedFileIds.length === allFileIds.length;

  return {
    selectedFiles,
    selectedFileIds,
    selectedFolders,
    selectedFolderPaths,
    toggleSelect,
    toggleFolderSelect,
    isFolderSelected,
    handleClick,
    selectAll: handleSelectAll,
    deselectAll: handleDeselectAll,
    isSelected,
    isAllSelected,
    selectedCount,
  };
};
