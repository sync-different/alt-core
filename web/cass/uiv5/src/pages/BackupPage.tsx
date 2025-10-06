/**
 * Backup Page Component
 * Placeholder for Phase 7 implementation
 */

import { Card, CardContent, Typography, Box } from '@mui/material';
import { Backup as BackupIcon } from '@mui/icons-material';

export function BackupPage() {
  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 3 }}>
        Backup
      </Typography>

      <Card>
        <CardContent sx={{ textAlign: 'center', py: 8 }}>
          <BackupIcon sx={{ fontSize: 80, color: '#004080', mb: 2 }} />
          <Typography variant="h5" gutterBottom>
            Backup - Coming in Phase 7
          </Typography>
          <Typography variant="body1" color="text.secondary">
            This page will manage your backup configurations and monitor backup status.
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
