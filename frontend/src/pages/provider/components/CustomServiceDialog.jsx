import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stack,
  Alert,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  TextField,
  Typography,
  Button,
  CircularProgress
} from '@mui/material';
import { MedicalServices as MedicalServicesIcon } from '@mui/icons-material';

/**
 * "Add a new service to the price list" dialog — lets a provider add a
 * custom-priced service without leaving the claim form. Extracted verbatim
 * from the pre-Phase-3B monolith; behavior/validation/payload unchanged, only
 * adapted to receive the hook's state/handlers as props instead of local
 * component state.
 */
export function CustomServiceDialog({
  open,
  onClose,
  medicalCategories,
  normalizeId,
  customServiceData,
  onDataChange,
  customServiceError,
  onSetError,
  addingCustomService,
  onSubmit
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}>
        <MedicalServicesIcon color="primary" />
        إضافة خدمة طبية جديدة لقائمة الأسعار
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          {customServiceError && (
            <Alert severity="error" onClose={() => onSetError(null)}>
              {customServiceError}
            </Alert>
          )}

          {/* Main Category */}
          <FormControl fullWidth required>
            <InputLabel id="custom-service-main-cat-label">التصنيف الرئيسي *</InputLabel>
            <Select
              labelId="custom-service-main-cat-label"
              value={customServiceData.mainCategoryId || ''}
              onChange={(e) => onDataChange('mainCategoryId', e.target.value)}
              label="التصنيف الرئيسي *"
            >
              {medicalCategories
                .filter((c) => !c.parentId)
                .map((cat) => (
                  <MenuItem key={cat.id} value={cat.id}>
                    {cat.name} ({cat.code})
                  </MenuItem>
                ))}
            </Select>
          </FormControl>

          {/* Sub-Category */}
          <FormControl fullWidth disabled={!customServiceData.mainCategoryId}>
            <InputLabel id="custom-service-sub-cat-label">التصنيف الفرعي</InputLabel>
            <Select
              labelId="custom-service-sub-cat-label"
              value={customServiceData.subCategoryId || ''}
              onChange={(e) => onDataChange('subCategoryId', e.target.value)}
              label="التصنيف الفرعي"
            >
              <MenuItem value="">
                <em>بلا تصنيف فرعي (استخدام الرئيسي)</em>
              </MenuItem>
              {medicalCategories
                .filter((c) => c.parentId && normalizeId(c.parentId) === normalizeId(customServiceData.mainCategoryId))
                .map((cat) => (
                  <MenuItem key={cat.id} value={cat.id}>
                    {cat.name} ({cat.code})
                  </MenuItem>
                ))}
            </Select>
          </FormControl>

          {/* Service Name */}
          <TextField
            fullWidth
            required
            label="اسم الخدمة الطبية"
            placeholder="مثال: كشف طبيب عام، تحليل دم كامل..."
            value={customServiceData.serviceName}
            onChange={(e) => onDataChange('serviceName', e.target.value)}
          />

          {/* Service Code */}
          <TextField
            fullWidth
            label="رمز الخدمة (تلقائي/اختياري)"
            placeholder="سيتم إنشاؤه تلقائياً إذا ترك فارغاً"
            value={customServiceData.serviceCode}
            onChange={(e) => onDataChange('serviceCode', e.target.value)}
            helperText="رمز فريد للخدمة (مثل: SRV-01, LAB-05)"
          />

          {/* Price */}
          <TextField
            fullWidth
            required
            type="number"
            label="السعر التعاقدي (دينار ليبي)"
            placeholder="0.00"
            value={customServiceData.contractPrice}
            onChange={(e) => onDataChange('contractPrice', e.target.value)}
            InputProps={{
              endAdornment: (
                <Typography variant="body2" color="text.secondary">
                  LYD
                </Typography>
              )
            }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={addingCustomService}>
          إلغاء
        </Button>
        <Button
          variant="contained"
          onClick={onSubmit}
          disabled={
            addingCustomService || !customServiceData.mainCategoryId || !customServiceData.serviceName || !customServiceData.contractPrice
          }
        >
          {addingCustomService ? <CircularProgress size={24} color="inherit" /> : 'إضافة وحفظ لقائمة الأسعار'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
