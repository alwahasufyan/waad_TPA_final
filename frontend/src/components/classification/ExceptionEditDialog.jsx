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
import { FormControlLabel, Radio, RadioGroup } from '@mui/material';
import MedicalServiceSelector from 'components/tba/MedicalServiceSelector';
import { CURRENCY_SYMBOL, formatCurrency } from 'utils/formatters';

export default function ExceptionEditDialog({ open, onClose, contractId, selectedItem, onSuccess }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [newPrice, setNewPrice] = useState('');
  const [reason, setReason] = useState('');
  const [mode, setMode] = useState(selectedItem ? 'PRICE_EDIT' : 'SERVICE_ADDED');
  const [service, setService] = useState(null);

  // When selectedItem changes
  React.useEffect(() => {
    if (open && selectedItem) {
      setNewPrice(selectedItem.contractPrice || '');
      setReason('');
      setMode('PRICE_EDIT');
      setError(null);
    } else if (open) {
      setMode('SERVICE_ADDED');
      setNewPrice('');
      setReason('');
      setService(null);
      setError(null);
    }
  }, [open, selectedItem]);

  const handleSave = async () => {
    if ((mode !== 'SERVICE_DEACTIVATED') && (!newPrice || isNaN(newPrice) || Number(newPrice) <= 0)) {
      setError('يجب إدخال سعر صحيح أكبر من صفر');
      return;
    }
    if (!reason.trim()) {
      setError('يجب إدخال سبب التعديل الاستثنائي');
      return;
    }
    if (mode === 'SERVICE_ADDED' && !service?.id) {
      setError('يجب اختيار خدمة مصنفة من الكتالوج');
      return;
    }
    if (mode !== 'SERVICE_ADDED' && !selectedItem) {
      setError('يجب اختيار خدمة من قائمة العقد');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      // 1. Get or create the governed PATCH draft. The backend reuses an open
      // PATCH draft for this contract instead of creating duplicates.
      const draft = await classificationService.createPatchDraft(contractId);
      const draftId = draft?.id;
      if (!draftId) {
        throw new Error('تعذر إنشاء مسودة التعديل الاستثنائي');
      }

      // 2. Record Change
      if (mode === 'PRICE_EDIT') {
        await classificationService.recordExceptionPriceChange(draftId, selectedItem.id, newPrice, reason);
      } else if (mode === 'SERVICE_ADDED') {
        await classificationService.addExceptionService(draftId, service.id, newPrice, reason);
      } else {
        await classificationService.deactivateExceptionService(draftId, selectedItem.id, reason);
      }

      // The governed report owns approval and publication (A10/D1).
      onSuccess(draftId);
      onClose();
    } catch (err) {
      console.error('Exception Edit Failed', err);
      setError(err.response?.data?.message || err.message || 'حدث خطأ أثناء حفظ التعديل');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={!loading ? onClose : undefined} maxWidth="sm" fullWidth>
      <DialogTitle>تعديل استثنائي لسعر الخدمة</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
        
        <Box sx={{ mt: 1 }}>
          <RadioGroup row value={mode} onChange={(e) => setMode(e.target.value)}>
            <FormControlLabel value="PRICE_EDIT" control={<Radio />} label="تصحيح سعر" disabled={!selectedItem} />
            <FormControlLabel value="SERVICE_ADDED" control={<Radio />} label="إضافة خدمة" />
            <FormControlLabel value="SERVICE_DEACTIVATED" control={<Radio />} label="إيقاف خدمة" disabled={!selectedItem} />
          </RadioGroup>
          {mode === 'SERVICE_ADDED' && (
            <MedicalServiceSelector value={service} onChange={setService} required label="الخدمة الطبية المصنفة" />
          )}
          {selectedItem && mode !== 'SERVICE_ADDED' && (
            <>
          <Typography variant="subtitle2" color="text.secondary">الخدمة:</Typography>
          <Typography variant="body1" sx={{ mb: 2 }}>
            [{selectedItem.serviceCode}] {selectedItem.serviceName}
          </Typography>

          <Typography variant="subtitle2" color="text.secondary">السعر الحالي:</Typography>
          <Typography variant="body1" sx={{ mb: 2 }}>{formatCurrency(Number(selectedItem.contractPrice))}</Typography>
            </>
          )}

          {mode !== 'SERVICE_DEACTIVATED' && <TextField
            fullWidth
            label={`السعر الجديد (${CURRENCY_SYMBOL})`}
            type="number"
            value={newPrice}
            onChange={(e) => setNewPrice(e.target.value)}
            disabled={loading}
            sx={{ mb: 2 }}
            InputProps={{ inputProps: { min: 0, step: '0.01' } }}
          />}

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
          disabled={loading || !reason.trim() || (mode !== 'SERVICE_DEACTIVATED' && !(Number(newPrice) > 0)) || (mode === 'SERVICE_ADDED' && !service?.id)}
          startIcon={loading && <CircularProgress size={20} />}
        >
          حفظ التعديل وفتح التقرير
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
