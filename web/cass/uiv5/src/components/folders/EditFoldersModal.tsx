/**
 * Edit Scan Folders Modal
 * Allows admin users to add/remove root scan folders.
 * - Shows current scan folders with remove buttons
 * - "Add Folder" opens a volume/drive picker + folder tree browser
 * - Save writes to scan1.txt via setfolder-json.fn
 */

import { useState, useEffect, useCallback } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Typography,
  Box,
  CircularProgress,
  Divider,
  Alert,
} from '@mui/material';
import {
  Delete as DeleteIcon,
  CreateNewFolder as AddFolderIcon,
  Folder as FolderIcon,
  FolderOpen as FolderOpenIcon,
  ArrowBack as ArrowBackIcon,
} from '@mui/icons-material';
import { SimpleTreeView } from '@mui/x-tree-view/SimpleTreeView';
import { TreeItem } from '@mui/x-tree-view/TreeItem';
import { fetchFolders, fetchVolumes, setScanFolders } from '../../services/fileApi';

interface EditFoldersModalProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

interface BrowseNode {
  id: string;
  name: string;
  path: string;
  children?: BrowseNode[];
  loaded: boolean;
  loading: boolean;
}

export function EditFoldersModal({ open, onClose, onSaved }: EditFoldersModalProps) {
  const [folders, setFolders] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Browse mode state
  const [browseMode, setBrowseMode] = useState(false);
  const [volumes, setVolumes] = useState<string[]>([]);
  const [volumesLoading, setVolumesLoading] = useState(false);
  const [selectedVolume, setSelectedVolume] = useState<string | null>(null);
  const [browseTree, setBrowseTree] = useState<BrowseNode[]>([]);
  const [browseLoading, setBrowseLoading] = useState(false);
  const [expandedItems, setExpandedItems] = useState<string[]>([]);
  const [selectedBrowsePath, setSelectedBrowsePath] = useState<string | null>(null);

  // Load current scan folders when modal opens
  useEffect(() => {
    if (open) {
      loadCurrentFolders();
      setError(null);
      setBrowseMode(false);
      setSelectedVolume(null);
      setSelectedBrowsePath(null);
    }
  }, [open]);

  const loadCurrentFolders = async () => {
    setLoading(true);
    try {
      const result = await fetchFolders('scanfolders');
      setFolders(result.map((f) => f.name));
    } catch (e) {
      setError('Failed to load current scan folders.');
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveFolder = (folderPath: string) => {
    setFolders((prev) => prev.filter((f) => f !== folderPath));
  };

  const handleAddFolderClick = async () => {
    setBrowseMode(true);
    setSelectedVolume(null);
    setSelectedBrowsePath(null);
    setBrowseTree([]);
    setExpandedItems([]);
    setVolumesLoading(true);
    try {
      const vols = await fetchVolumes();
      setVolumes(vols);
    } catch (e) {
      setError('Failed to load volumes/drives.');
    } finally {
      setVolumesLoading(false);
    }
  };

  const handleVolumeSelect = async (volumePath: string) => {
    setSelectedVolume(volumePath);
    setSelectedBrowsePath(volumePath);
    setBrowseLoading(true);
    setExpandedItems([]);
    try {
      const contents = await fetchFolders(volumePath, { browse: true });
      const folderItems = contents.filter((f) => f.type !== 'file');
      const nodes: BrowseNode[] = folderItems.map((f) => ({
        id: `${volumePath}/${f.name}`,
        name: f.name,
        path: `${volumePath}/${f.name}`,
        loaded: false,
        loading: false,
        children: [],
      }));
      setBrowseTree(nodes);
    } catch (e) {
      setError('Failed to browse volume.');
    } finally {
      setBrowseLoading(false);
    }
  };

  const loadBrowseChildren = useCallback(async (nodeId: string, nodePath: string) => {
    setBrowseTree((prev) => updateNodeInTree(prev, nodeId, { loading: true }));
    try {
      const contents = await fetchFolders(nodePath, { browse: true });
      const folderItems = contents.filter((f) => f.type !== 'file');
      const children: BrowseNode[] = folderItems.map((f) => ({
        id: `${nodePath}/${f.name}`,
        name: f.name,
        path: `${nodePath}/${f.name}`,
        loaded: false,
        loading: false,
        children: [],
      }));
      setBrowseTree((prev) => updateNodeInTree(prev, nodeId, { children, loaded: true, loading: false }));
    } catch {
      setBrowseTree((prev) => updateNodeInTree(prev, nodeId, { loading: false }));
    }
  }, []);

  const updateNodeInTree = (nodes: BrowseNode[], nodeId: string, updates: Partial<BrowseNode>): BrowseNode[] => {
    return nodes.map((node) => {
      if (node.id === nodeId) {
        return { ...node, ...updates };
      }
      if (node.children && node.children.length > 0) {
        return { ...node, children: updateNodeInTree(node.children, nodeId, updates) };
      }
      return node;
    });
  };

  const findNodeInTree = (nodes: BrowseNode[], nodeId: string): BrowseNode | null => {
    for (const node of nodes) {
      if (node.id === nodeId) return node;
      if (node.children) {
        const found = findNodeInTree(node.children, nodeId);
        if (found) return found;
      }
    }
    return null;
  };

  const handleExpandedItemsChange = useCallback((_event: React.SyntheticEvent | null, itemIds: string[]) => {
    const validItemIds = itemIds.filter((id) => !id.endsWith('-placeholder'));
    setExpandedItems((prevExpanded) => {
      const newlyExpanded = validItemIds.filter((id) => !prevExpanded.includes(id));
      newlyExpanded.forEach((nodeId) => {
        setBrowseTree((currentTree) => {
          const node = findNodeInTree(currentTree, nodeId);
          if (node && !node.loaded && !node.loading) {
            loadBrowseChildren(nodeId, node.path);
          }
          return currentTree;
        });
      });
      return validItemIds;
    });
  }, [loadBrowseChildren]);

  const handleBrowseItemClick = (node: BrowseNode) => {
    if (node.id.endsWith('-placeholder')) return;
    setSelectedBrowsePath(node.path);
  };

  const handleSelectFolder = () => {
    if (selectedBrowsePath && !folders.includes(selectedBrowsePath)) {
      // Ensure trailing slash for consistency
      const pathWithSlash = selectedBrowsePath.endsWith('/') ? selectedBrowsePath : selectedBrowsePath + '/';
      setFolders((prev) => [...prev, pathWithSlash]);
    }
    setBrowseMode(false);
    setSelectedVolume(null);
    setSelectedBrowsePath(null);
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const result = await setScanFolders(folders);
      if (result.success) {
        onSaved();
        onClose();
      } else {
        setError(result.error || 'Failed to save scan folders.');
      }
    } catch (e) {
      setError('Failed to save scan folders.');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setBrowseMode(false);
    onClose();
  };

  const renderBrowseTree = (nodes: BrowseNode[]) => {
    return nodes.map((node) => (
      <TreeItem
        key={node.id}
        itemId={node.id}
        label={
          <Box
            sx={{ display: 'flex', alignItems: 'center', py: 0.5, gap: 1 }}
            onClick={(e) => {
              e.stopPropagation();
              handleBrowseItemClick(node);
            }}
          >
            {expandedItems.includes(node.id) ? (
              <FolderOpenIcon sx={{ fontSize: 18, color: '#FFD700' }} />
            ) : (
              <FolderIcon sx={{ fontSize: 18, color: '#FFD700' }} />
            )}
            <Typography variant="body2" sx={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {node.name}
            </Typography>
            {node.loading && <CircularProgress size={14} sx={{ ml: 1 }} />}
          </Box>
        }
      >
        {node.children && node.children.length > 0
          ? renderBrowseTree(node.children)
          : !node.loaded && <TreeItem itemId={`${node.id}-placeholder`} label="" />}
      </TreeItem>
    ));
  };

  return (
    <Dialog open={open} onClose={handleCancel} maxWidth="sm" fullWidth>
      <DialogTitle>Edit Scan Folders</DialogTitle>
      <DialogContent dividers>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <>
            {/* Current scan folders list */}
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Current Scan Folders:
            </Typography>
            {folders.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
                No scan folders configured.
              </Typography>
            ) : (
              <List dense sx={{ bgcolor: 'background.default', borderRadius: 1, mb: 2 }}>
                {folders.map((folder) => (
                  <ListItem key={folder}>
                    <FolderIcon sx={{ mr: 1, color: '#FFD700', fontSize: 20 }} />
                    <ListItemText
                      primary={folder}
                      primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                    />
                    <ListItemSecondaryAction>
                      <IconButton
                        edge="end"
                        size="small"
                        onClick={() => handleRemoveFolder(folder)}
                        title="Remove folder"
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            )}

            {/* Add Folder button / Browse mode */}
            {!browseMode ? (
              <Button
                variant="outlined"
                startIcon={<AddFolderIcon />}
                onClick={handleAddFolderClick}
                fullWidth
              >
                Add Folder
              </Button>
            ) : (
              <Box sx={{ mt: 1 }}>
                <Divider sx={{ mb: 2 }} />

                {!selectedVolume ? (
                  // Volume/drive selection
                  <>
                    <Typography variant="subtitle2" sx={{ mb: 1 }}>
                      Select Volume / Drive:
                    </Typography>
                    {volumesLoading ? (
                      <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
                        <CircularProgress size={24} />
                      </Box>
                    ) : (
                      <List dense sx={{ bgcolor: 'background.default', borderRadius: 1 }}>
                        {volumes.map((vol) => (
                          <ListItem
                            key={vol}
                            component="div"
                            onClick={() => handleVolumeSelect(vol)}
                            sx={{
                              cursor: 'pointer',
                              '&:hover': { bgcolor: 'action.hover' },
                              borderRadius: 1,
                            }}
                          >
                            <FolderIcon sx={{ mr: 1, color: '#FFD700', fontSize: 20 }} />
                            <ListItemText primary={vol} primaryTypographyProps={{ variant: 'body2' }} />
                          </ListItem>
                        ))}
                      </List>
                    )}
                    <Button
                      size="small"
                      onClick={() => setBrowseMode(false)}
                      sx={{ mt: 1 }}
                    >
                      Cancel
                    </Button>
                  </>
                ) : (
                  // Folder tree browser
                  <>
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                      <IconButton size="small" onClick={() => setSelectedVolume(null)} sx={{ mr: 1 }}>
                        <ArrowBackIcon fontSize="small" />
                      </IconButton>
                      <Typography variant="subtitle2">
                        Browse: {selectedVolume}
                      </Typography>
                    </Box>

                    {selectedBrowsePath && (
                      <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
                        Selected: {selectedBrowsePath}
                      </Typography>
                    )}

                    <Box sx={{ maxHeight: 300, overflow: 'auto', bgcolor: 'background.default', borderRadius: 1, p: 1 }}>
                      {browseLoading ? (
                        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
                          <CircularProgress size={24} />
                        </Box>
                      ) : browseTree.length === 0 ? (
                        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
                          No folders found.
                        </Typography>
                      ) : (
                        <SimpleTreeView
                          expandedItems={expandedItems}
                          onExpandedItemsChange={handleExpandedItemsChange}
                          selectedItems={selectedBrowsePath}
                          onSelectedItemsChange={(_event, itemId) => {
                            if (itemId && !itemId.endsWith('-placeholder')) {
                              setSelectedBrowsePath(itemId);
                            }
                          }}
                          sx={{
                            '& .MuiTreeItem-content': {
                              py: 0.25,
                              borderRadius: 1,
                              '&.Mui-selected': {
                                backgroundColor: '#0078D4 !important',
                                '& .MuiTypography-root': { color: 'white !important' },
                                '&:hover': { backgroundColor: '#106EBE !important' },
                              },
                            },
                            '& .MuiTreeItem-label': { fontSize: '0.875rem' },
                            '& .MuiTreeItem-iconContainer': { minWidth: 20 },
                          }}
                        >
                          {renderBrowseTree(browseTree)}
                        </SimpleTreeView>
                      )}
                    </Box>

                    <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                      <Button
                        variant="contained"
                        size="small"
                        onClick={handleSelectFolder}
                        disabled={!selectedBrowsePath}
                      >
                        Select This Folder
                      </Button>
                      <Button
                        size="small"
                        onClick={() => {
                          setBrowseMode(false);
                          setSelectedVolume(null);
                        }}
                      >
                        Cancel
                      </Button>
                    </Box>
                  </>
                )}
              </Box>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel} disabled={saving}>
          Cancel
        </Button>
        <Button onClick={handleSave} variant="contained" disabled={saving || loading}>
          {saving ? <CircularProgress size={20} /> : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
