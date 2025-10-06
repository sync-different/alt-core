/**
 * Home Page - Device Dashboard
 * Displays all connected nodes with their status and statistics
 */

import { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  LinearProgress,
  Chip,
  CircularProgress,
} from '@mui/material';
import {
  Computer as ComputerIcon,
  Cloud as CloudIcon,
  Storage as StorageIcon,
} from '@mui/icons-material';
import api from '../services/api';
import { formatRelativeTime } from '../utils/formatters';
import type { Node } from '../types/models';

export function HomePage() {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchNodes = async () => {
    try {
      const response = await api.get('/cass/nodeinfo.fn', {
        params: { view: 'json' }
      });
      setNodes(response.data?.nodes || []);
    } catch (error) {
      console.error('Failed to fetch nodes:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNodes();
    // Auto-refresh every 15 seconds
    const interval = setInterval(fetchNodes, 15000);
    return () => clearInterval(interval);
  }, []);

  const getNodeIcon = (type: string) => {
    switch (type) {
      case 'cloud':
        return <CloudIcon sx={{ fontSize: 40, color: '#004080' }} />;
      case 'server':
        return <StorageIcon sx={{ fontSize: 40, color: '#004080' }} />;
      default:
        return <ComputerIcon sx={{ fontSize: 40, color: '#004080' }} />;
    }
  };

  const getNodeTypeColor = (type: string): 'primary' | 'success' | 'info' => {
    switch (type) {
      case 'server':
        return 'primary';
      case 'cloud':
        return 'info';
      default:
        return 'success';
    }
  };

  const calculateDiskUsage = (free: number, total: number) => {
    if (!total) return 0;
    return ((total - free) / total) * 100;
  };

  const isOnline = (lastPing: number) => {
    const fiveMinutesAgo = Date.now() - 5 * 60 * 1000;
    return lastPing > fiveMinutesAgo;
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 3 }}>
        Device Dashboard
      </Typography>

      {nodes.length === 0 ? (
        <Card>
          <CardContent sx={{ textAlign: 'center', py: 8 }}>
            <ComputerIcon sx={{ fontSize: 80, color: '#ccc', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No devices found
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Make sure the backend server is running
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' }, gap: 3 }}>
          {nodes.map((node) => (
            <Card key={node.node_id}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                    {getNodeIcon(node.node_type)}
                    <Box sx={{ ml: 2, flex: 1 }}>
                      <Typography variant="h6">{node.node_machine}</Typography>
                      <Box sx={{ display: 'flex', gap: 1, mt: 0.5 }}>
                        <Chip
                          label={node.node_type.toUpperCase()}
                          size="small"
                          color={getNodeTypeColor(node.node_type)}
                        />
                        <Chip
                          label={isOnline(node.node_lastping_long) ? 'Online' : 'Offline'}
                          size="small"
                          color={isOnline(node.node_lastping_long) ? 'success' : 'default'}
                        />
                      </Box>
                    </Box>
                  </Box>

                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {node.node_ip}:{node.node_port}
                  </Typography>

                  <Box sx={{ mt: 2 }}>
                    <Box sx={{ mb: 2 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                        <Typography variant="body2">Index Progress</Typography>
                        <Typography variant="body2" color="primary">
                          {node.node_idx_percent || '0%'}
                        </Typography>
                      </Box>
                      <LinearProgress
                        variant="determinate"
                        value={parseInt(node.node_idx_percent || '0')}
                        sx={{ height: 8, borderRadius: 1 }}
                      />
                    </Box>

                    <Box sx={{ mb: 2 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                        <Typography variant="body2">Backup Progress</Typography>
                        <Typography variant="body2" color="warning.main">
                          {node.node_backup_percent || '0%'}
                        </Typography>
                      </Box>
                      <LinearProgress
                        variant="determinate"
                        value={parseInt(node.node_backup_percent || '0')}
                        color="warning"
                        sx={{ height: 8, borderRadius: 1 }}
                      />
                    </Box>

                    {node.node_disktotal > 0 && (
                      <Box sx={{ mb: 2 }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                          <Typography variant="body2">Disk Usage</Typography>
                          <Typography variant="body2" color="error">
                            {calculateDiskUsage(node.node_diskfree, node.node_disktotal).toFixed(1)}%
                          </Typography>
                        </Box>
                        <LinearProgress
                          variant="determinate"
                          value={calculateDiskUsage(node.node_diskfree, node.node_disktotal)}
                          color="error"
                          sx={{ height: 8, borderRadius: 1 }}
                        />
                      </Box>
                    )}

                    <Typography variant="caption" color="text.secondary">
                      Last seen: {formatRelativeTime(node.node_lastping_long)}
                    </Typography>
                  </Box>
                </CardContent>
              </Card>
          ))}
        </Box>
      )}
    </Box>
  );
}
