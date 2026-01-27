/**
 * Folder Tree Sidebar Component
 * Left sidebar showing folder structure in a tree view
 * Toggleable visibility
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { useDispatch } from 'react-redux';
import {
  Box,
  Drawer,
  Typography,
  IconButton,
  CircularProgress,
} from '@mui/material';
import {
  ChevronLeft as ChevronLeftIcon,
  Menu as MenuIcon,
  Folder as FolderIcon,
  FolderOpen as FolderOpenIcon,
} from '@mui/icons-material';
import { SimpleTreeView } from '@mui/x-tree-view/SimpleTreeView';
import { TreeItem } from '@mui/x-tree-view/TreeItem';
import { fetchFolders } from '../../services/fileApi';
import { checkFolderPermission } from '../../services/folderPermissionApi';
import { selectFolder } from '../../store/slices/folderPermissionsSlice';
import type { AppDispatch } from '../../store/store';

const SIDEBAR_WIDTH = 280;

interface TreeNode {
  id: string;
  name: string;
  path: string;
  children?: TreeNode[];
  loaded: boolean;
  loading: boolean;
  count?: number;
}

interface FolderTreeSidebarProps {
  open: boolean;
  onToggle: () => void;
  onFolderNavigate: (folderPath: string) => void;
}

export function FolderTreeSidebar({ open, onToggle, onFolderNavigate }: FolderTreeSidebarProps) {
  const dispatch = useDispatch<AppDispatch>();
  const [treeData, setTreeData] = useState<TreeNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [expandedItems, setExpandedItems] = useState<string[]>([]);
  const [selectedItem, setSelectedItem] = useState<string | null>(null);

  // Use ref to store callback to avoid re-renders causing issues
  const onFolderNavigateRef = useRef(onFolderNavigate);
  onFolderNavigateRef.current = onFolderNavigate;

  // Track if data has been loaded to prevent reloading
  const dataLoadedRef = useRef(false);

  // Load root folders on mount
  useEffect(() => {
    if (open && !dataLoadedRef.current) {
      dataLoadedRef.current = true;
      loadRootFolders();
    }
  }, [open]);

  const loadRootFolders = async () => {
    setLoading(true);
    try {
      const folders = await fetchFolders('scanfolders');
      const folderItems = folders.filter((f) => f.type !== 'file');

      // Check permissions for each folder and filter out ones with 'none'
      const nodesWithPermissions = await Promise.all(
        folderItems.map(async (folder) => {
          const permission = await checkFolderPermission(folder.name);
          return {
            folder,
            permission,
          };
        })
      );

      const nodes: TreeNode[] = nodesWithPermissions
        .filter(({ permission }) => permission !== 'none') // Only show folders user has access to
        .map(({ folder }) => ({
          id: folder.name,
          name: folder.name,
          path: folder.name,
          loaded: false,
          loading: false,
          count: folder.count,
          children: [], // Empty children, will be loaded on expand
        }));
      setTreeData(nodes);
    } catch (error) {
      console.error('Failed to load root folders:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadChildren = useCallback(async (nodeId: string, nodePath: string) => {
    // Mark node as loading
    setTreeData((prev) => updateNodeLoading(prev, nodeId, true));

    try {
      const folders = await fetchFolders(nodePath);
      const folderItems = folders.filter((f) => f.type !== 'file');

      // Check permissions for each folder and filter out ones with 'none'
      const childrenWithPermissions = await Promise.all(
        folderItems.map(async (folder) => {
          const childPath = `${nodePath}/${folder.name}`;
          const permission = await checkFolderPermission(childPath);
          return {
            folder,
            childPath,
            permission,
          };
        })
      );

      const children: TreeNode[] = childrenWithPermissions
        .filter(({ permission }) => permission !== 'none') // Only show folders user has access to
        .map(({ folder, childPath }) => ({
          id: childPath,
          name: folder.name,
          path: childPath,
          loaded: false,
          loading: false,
          count: folder.count,
          children: [],
        }));

      // Update tree with children
      setTreeData((prev) => updateNodeChildren(prev, nodeId, children));
    } catch (error) {
      console.error('Failed to load children:', error);
      setTreeData((prev) => updateNodeLoading(prev, nodeId, false));
    }
  }, []);

  const updateNodeLoading = (nodes: TreeNode[], nodeId: string, loading: boolean): TreeNode[] => {
    return nodes.map((node) => {
      if (node.id === nodeId) {
        return { ...node, loading };
      }
      if (node.children && node.children.length > 0) {
        return { ...node, children: updateNodeLoading(node.children, nodeId, loading) };
      }
      return node;
    });
  };

  const updateNodeChildren = (nodes: TreeNode[], nodeId: string, children: TreeNode[]): TreeNode[] => {
    return nodes.map((node) => {
      if (node.id === nodeId) {
        return { ...node, children, loaded: true, loading: false };
      }
      if (node.children && node.children.length > 0) {
        return { ...node, children: updateNodeChildren(node.children, nodeId, children) };
      }
      return node;
    });
  };

  const findNode = (nodes: TreeNode[], nodeId: string): TreeNode | null => {
    for (const node of nodes) {
      if (node.id === nodeId) return node;
      if (node.children) {
        const found = findNode(node.children, nodeId);
        if (found) return found;
      }
    }
    return null;
  };

  const handleExpandedItemsChange = useCallback((_event: React.SyntheticEvent | null, itemIds: string[]) => {
    // Filter out placeholder items
    const validItemIds = itemIds.filter((id) => !id.endsWith('-placeholder'));

    setExpandedItems((prevExpanded) => {
      // Find newly expanded items
      const newlyExpanded = validItemIds.filter((id) => !prevExpanded.includes(id));

      // Load children for newly expanded items that haven't been loaded yet
      newlyExpanded.forEach((nodeId) => {
        setTreeData((currentTreeData) => {
          const node = findNode(currentTreeData, nodeId);
          if (node && !node.loaded && !node.loading) {
            loadChildren(nodeId, node.path);
          }
          return currentTreeData;
        });
      });

      return validItemIds;
    });
  }, [loadChildren]);

  const handleItemClick = useCallback((node: TreeNode) => {
    // Ignore placeholder nodes
    if (node.id.endsWith('-placeholder')) return;

    // Update local selection state
    setSelectedItem(node.id);
    // Single click selects the folder for the permission sidebar AND navigates main view
    dispatch(selectFolder({
      name: node.name,
      path: node.path,
      count: node.count,
    }));
    // Navigate main folder view to show this folder's contents (use ref to avoid dependency)
    onFolderNavigateRef.current(node.path);
  }, [dispatch]);

  const renderTree = (nodes: TreeNode[], isExpanded: (id: string) => boolean) => {
    return nodes.map((node) => {
      const expanded = isExpanded(node.id);
      return (
        <TreeItem
          key={node.id}
          itemId={node.id}
          label={
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                py: 0.5,
                gap: 1,
              }}
              onClick={(e) => {
                e.stopPropagation();
                handleItemClick(node);
              }}
            >
              {expanded ? (
                <FolderOpenIcon sx={{ fontSize: 18, color: '#FFD700' }} />
              ) : (
                <FolderIcon sx={{ fontSize: 18, color: '#FFD700' }} />
              )}
              <Typography
                variant="body2"
                sx={{
                  flex: 1,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {node.name}
              </Typography>
              {node.loading && (
                <CircularProgress size={14} sx={{ ml: 1 }} />
              )}
              {node.count !== undefined && !node.loading && (
                <Typography variant="caption" color="text.secondary">
                  ({node.count})
                </Typography>
              )}
            </Box>
          }
        >
          {node.children && node.children.length > 0
            ? renderTree(node.children, isExpanded)
            : // Show placeholder if not loaded yet (allows expand icon to show)
              !node.loaded && <TreeItem itemId={`${node.id}-placeholder`} label="" />}
        </TreeItem>
      );
    });
  };

  return (
    <>
      {/* Toggle Button */}
      <IconButton
        onClick={onToggle}
        sx={{
          position: 'fixed',
          left: open ? SIDEBAR_WIDTH : 0,
          top: '50%',
          transform: 'translateY(-50%)',
          zIndex: (theme) => theme.zIndex.drawer + 2,
          backgroundColor: 'primary.main',
          color: 'white',
          '&:hover': {
            backgroundColor: 'primary.dark',
          },
          borderRadius: open ? '0 4px 4px 0' : '4px',
          transition: 'left 0.3s',
        }}
      >
        {open ? <ChevronLeftIcon /> : <MenuIcon />}
      </IconButton>

      {/* Sidebar Drawer */}
      <Drawer
        anchor="left"
        open={open}
        variant="persistent"
        hideBackdrop
        sx={{
          '& .MuiDrawer-paper': {
            width: SIDEBAR_WIDTH,
            position: 'fixed',
            left: 0,
            top: 64, // Below top nav
            height: 'calc(100% - 64px)',
            boxShadow: 3,
            zIndex: (theme) => theme.zIndex.drawer,
          },
        }}
      >
        <Box
          sx={{
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            bgcolor: 'background.paper',
          }}
        >
          {/* Header */}
          <Box
            sx={{
              p: 2,
              bgcolor: 'rgb(42, 42, 42)',
              color: 'white',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <Typography variant="h6">Folders</Typography>
            <IconButton size="small" onClick={onToggle} sx={{ color: 'white' }}>
              <ChevronLeftIcon />
            </IconButton>
          </Box>

          {/* Tree Content */}
          <Box sx={{ flex: 1, overflow: 'auto', p: 1 }}>
            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress />
              </Box>
            ) : treeData.length === 0 ? (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ textAlign: 'center', py: 4 }}
              >
                No folders found
              </Typography>
            ) : (
              <SimpleTreeView
                expandedItems={expandedItems}
                onExpandedItemsChange={handleExpandedItemsChange}
                selectedItems={selectedItem}
                onSelectedItemsChange={(_event, itemId) => {
                  // Only update if it's a valid item (not a placeholder)
                  if (itemId && !itemId.endsWith('-placeholder')) {
                    setSelectedItem(itemId);
                  }
                }}
                sx={{
                  '& .MuiTreeItem-content': {
                    py: 0.25,
                    borderRadius: 1,
                    '&.Mui-selected': {
                      backgroundColor: '#0078D4 !important',
                      '& .MuiTypography-root': {
                        color: 'white !important',
                      },
                      '&:hover': {
                        backgroundColor: '#106EBE !important',
                      },
                      '&.Mui-focused': {
                        backgroundColor: '#0078D4 !important',
                      },
                    },
                  },
                  '& .MuiTreeItem-label': {
                    fontSize: '0.875rem',
                  },
                  '& .MuiTreeItem-iconContainer': {
                    minWidth: 20,
                  },
                }}
              >
                {renderTree(treeData, (id) => expandedItems.includes(id))}
              </SimpleTreeView>
            )}
          </Box>
        </Box>
      </Drawer>
    </>
  );
}
