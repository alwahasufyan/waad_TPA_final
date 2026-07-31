/**
 * ReviewerProviderAssignmentPanel — WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1
 *
 * Admin-facing (SUPER_ADMIN/WAAD_ADMIN only, enforced by the parent page's
 * route guard) UI to manage which providers a MEDICAL_REVIEWER is scoped to.
 * Backend enforcement already exists (ReviewerProviderIsolationService,
 * MedicalReviewerProviderAssignmentController) — this panel was the missing
 * piece: there was no admin UI to actually manage assignments.
 *
 * Self-contained: loads/saves independently of the parent user-edit form,
 * mirroring Step1ResetPassword's pattern in UserEdit.jsx.
 */

import { useEffect, useMemo, useState } from 'react';

import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';
import DeleteIcon from '@mui/icons-material/Delete';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';

import TbaFormSection from 'components/tba/form/TbaFormSection';
import medicalReviewersService from 'services/api/medical-reviewers.service';
import { openSnackbar } from 'api/snackbar';

const ReviewerProviderAssignmentPanel = ({ reviewerUserId, providerOptions }) => {
  const [assignedIds, setAssignedIds] = useState([]);
  const [originalIds, setOriginalIds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        setLoadError(null);
        const response = await medicalReviewersService.getReviewerAssignments(reviewerUserId);
        const ids = Array.isArray(response?.assignedProviderIds) ? response.assignedProviderIds : [];
        if (!cancelled) {
          setAssignedIds(ids);
          setOriginalIds(ids);
        }
      } catch (err) {
        if (!cancelled) {
          setLoadError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تحميل مقدمي الخدمة المرتبطين');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [reviewerUserId]);

  const selectedProviders = useMemo(
    () => providerOptions.filter((provider) => assignedIds.includes(provider.id)),
    [providerOptions, assignedIds]
  );

  const isDirty = useMemo(() => {
    const a = [...assignedIds].sort((x, y) => x - y);
    const b = [...originalIds].sort((x, y) => x - y);
    return a.length !== b.length || a.some((id, idx) => id !== b[idx]);
  }, [assignedIds, originalIds]);

  const handleRemove = (providerId) => {
    setAssignedIds((prev) => prev.filter((id) => id !== providerId));
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      await medicalReviewersService.updateReviewerAssignments(reviewerUserId, assignedIds);
      setOriginalIds(assignedIds);
      openSnackbar({
        open: true,
        message: 'تم حفظ مقدمي الخدمة المرتبطين بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });
    } catch (err) {
      const message = err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل حفظ مقدمي الخدمة المرتبطين';
      openSnackbar({ open: true, message, variant: 'alert', alert: { color: 'error' } });
    } finally {
      setSaving(false);
    }
  };

  return (
    <TbaFormSection title="مقدمو الخدمة المرتبطون" subtitle="يحدد هذا القسم أي مطالبات وطلبات موافقة مسبقة وزيارات يمكن لهذا المراجع الوصول إليها" icon={LocalHospitalIcon} sx={{ mt: '1.5rem' }}>
      <Alert severity="info" sx={{ mb: '1.0rem' }}>
        المراجع الطبي لا يرى ولا يستطيع مراجعة إلا سجلات مقدمي الخدمة المرتبطين به هنا. إزالة مقدم خدمة تسحب وصول المراجع لسجلاته فوراً.
      </Alert>

      {loadError && (
        <Alert severity="error" sx={{ mb: '1.0rem' }}>
          {loadError}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: '2.0rem' }}>
          <CircularProgress size={28} />
        </Box>
      ) : (
        <>
          <Autocomplete
            multiple
            options={providerOptions}
            value={selectedProviders}
            getOptionLabel={(option) => option?.label || ''}
            isOptionEqualToValue={(option, value) => option.id === value.id}
            onChange={(event, newValue) => setAssignedIds(newValue.map((v) => v.id))}
            renderInput={(params) => (
              <TextField {...params} label="بحث وإضافة مقدم خدمة" placeholder="اكتب لاختيار مقدمي الخدمة..." />
            )}
            renderTags={() => null}
          />

          <Box sx={{ mt: '1.0rem' }}>
            {selectedProviders.length === 0 ? (
              <Alert severity="warning">لا توجد مقدمو خدمة مسندون لهذا المراجع</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>مقدم الخدمة</TableCell>
                    <TableCell align="left">إجراء</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {selectedProviders.map((provider) => (
                    <TableRow key={provider.id}>
                      <TableCell>{provider.label}</TableCell>
                      <TableCell align="left">
                        <Chip
                          icon={<DeleteIcon sx={{ fontSize: '1rem' }} />}
                          label="إزالة"
                          size="small"
                          color="error"
                          variant="outlined"
                          onClick={() => handleRemove(provider.id)}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Box>

          <Box sx={{ mt: '1.0rem', display: 'flex', alignItems: 'center', gap: '1.0rem' }}>
            <Button
              variant="contained"
              onClick={handleSave}
              disabled={saving || !isDirty}
              startIcon={saving ? <CircularProgress size={18} color="inherit" /> : <SaveIcon />}
            >
              {saving ? 'جاري الحفظ...' : 'حفظ مقدمي الخدمة'}
            </Button>
            <Typography variant="caption" color="text.secondary">
              {selectedProviders.length} مقدم خدمة مرتبط
            </Typography>
          </Box>
        </>
      )}
    </TbaFormSection>
  );
};

export default ReviewerProviderAssignmentPanel;
