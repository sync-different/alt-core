/**
 * Multi-Cluster Page Component
 * Placeholder for Phase 7 implementation
 */

import { Card, CardContent, Typography, Box } from '@mui/material';
import { CloudQueue as CloudIcon } from '@mui/icons-material';

export function MultiClusterPage() {
  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 3 }}>
        Multi-Cluster
      </Typography>

      <Card>
        <CardContent sx={{ textAlign: 'center', py: 8 }}>
          <CloudIcon sx={{ fontSize: 80, color: '#004080', mb: 2 }} />
          <Typography variant="h5" gutterBottom>
            Multi-Cluster - Coming in Phase 7
          </Typography>
          <Typography variant="body1" color="text.secondary">
            This page will manage connections to multiple Alterante clusters.
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
