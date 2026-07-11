import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { classificationService } from 'services/api/classification.service';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
  Box,
  CircularProgress,
  Alert
} from '@mui/material';

export default function ExceptionEditDialog({ open, onClose, contractId, selectedItem, onSuccess }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [newPrice, setNewPrice] = useState('');
  const [reason, setReason] = useState('');

  // When selectedItem changes
  React.useEffect(() => {
    if (open && selectedItem) {
      setNewPrice(selectedItem.contractPrice || '');
      setReason('');
      setError(null);
    }
  }, [open, selectedItem]);

  const handleSave = async () => {
    if (!newPrice || isNaN(newPrice) || Number(newPrice) <= 0) {
      setError('يجب إدخال سعر صحيح أكبر من صفر');
      return;
    }
    if (!reason.trim()) {
      setError('يجب إدخال سبب التعديل الاستثنائي');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      // 1. Create Patch Draft
      const draft = await classificationService.createPatchDraft(contractId);
      const draftId = draft?.id;
      if (!draftId) {
        throw new Error('تعذر إنشاء مسودة التعديل الاستثنائي');
      }

      // 2. Record Change
      await classificationService.recordExceptionPriceChange(
        draftId, 
        selectedItem.id, 
        newPrice, 
        reason
      );

      // 3. Publish Patch
      await classificationService.publishPatch(draftId);

      onSuccess();
      onClose();
    } catch (err) {
      console.error('Exception Edit Failed', err);
      setError(err.response?.data?.message || err.message || 'حدث خطأ أثناء حفظ التعديل');
    } finally {
      setLoading(false);
    }
  };

  if (!selectedItem) return null;

  return (
    <Dialog open={open} onClose={!loading ? onClose : undefined} maxWidth="sm" fullWidth>
      <DialogTitle>تعديل استثنائي لسعر الخدمة</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
        
        <Box sx={{ mt: 1 }}>
          <Typography variant="subtitle2" color="text.secondary">الخدمة:</Typography>
          <Typography variant="body1" sx={{ mb: 2 }}>
            [{selectedItem.serviceCode}] {selectedItem.serviceName}
          </Typography>

          <Typography variant="subtitle2" color="text.secondary">السعر الحالي:</Typography>
          <Typography variant="body1" sx={{ mb: 2 }}>{selectedItem.contractPrice} ريال</Typography>

          <TextField
            fullWidth
            label="السعر الجديد (ريال)"
            type="number"
            value={newPrice}
            onChange={(e) => setNewPrice(e.target.value)}
            disabled={loading}
            sx={{ mb: 2 }}
            InputProps={{ inputProps: { min: 0, step: '0.01' } }}
          />

          <TextField
            fullWidth
            multiline
            rows={3}
            label="سبب التعديل الاستثنائي (مطلوب)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={loading}
            placeholder="مثال: تعديل سعر بناء على اتفاق محدث"
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>إلغاء</Button>
        <Button 
          variant="contained" 
          onClick={handleSave} 
          disabled={loading || !newPrice || !reason.trim()}
          startIcon={loading && <CircularProgress size={20} />}
        >
          حفظ التعديل ونشره
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ExceptionEditDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  contractId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  selectedItem: PropTypes.object,
  onSuccess: PropTypes.func.isRequired
};
