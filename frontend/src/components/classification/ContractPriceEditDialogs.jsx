import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Autocomplete,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import { formatCurrency } from 'utils/formatters';
import { getAllMedicalCategories } from 'services/api/medical-categories.service';
import { lookupMedicalServices } from 'services/api/medical-services.service';
import {
  correctPriceItem,
  addPriceListService,
  deactivatePriceItem,
  correctItemClassification,
  getContractPriceAudit
} from 'services/api/provider-contracts.service';

/**
 * MC-4C simplified row-level edit dialogs (2026-07-12).
 * Each saves DIRECTLY to the active price list (no new version, no navigation)
 * and records an audit entry. Currency is Libyan Dinar (د.ل) via formatCurrency.
 */

const money = (v) => (v == null ? '—' : formatCurrency(Number(v)));

const OPERATION_LABELS = {
  PRICE_CORRECTION: 'تعديل سعر',
  ADD_SERVICE: 'إضافة خدمة',
  DEACTIVATE_SERVICE: 'إيقاف خدمة',
  CLASSIFICATION_CORRECTION: 'تعديل تصنيف/كود',
  VERSION_IMPORT: 'استيراد قائمة',
  VERSION_RESTORE: 'استرجاع قائمة',
  PRICE_EDIT: 'تعديل سعر (قديم)',
  SERVICE_ADDED: 'إضافة خدمة (قديم)',
  SERVICE_DEACTIVATED: 'إيقاف خدمة (قديم)'
};

const useCategories = (open) => {
  const [categories, setCategories] = useState([]);
  useEffect(() => {
    if (!open) return;
    getAllMedicalCategories()
      .then((list) => setCategories(Array.isArray(list) ? list : list?.content || []))
      .catch((err) => console.error('categories load failed', err));
  }, [open]);
  return categories;
};

const catLabel = (c) => `${c.code ? c.code + ' — ' : ''}${c.nameAr || c.name || ''}`;

// ── Price correction ─────────────────────────────────────────────────────────
export const PriceCorrectionDialog = ({ open, contractId, item, onClose, onSaved }) => {
  const [newPrice, setNewPrice] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      setNewPrice(item?.contractPrice ?? '');
      setReason('');
      setError(null);
    }
  }, [open, item]);

  const save = async () => {
    if (!(Number(newPrice) > 0)) return setError('السعر الجديد يجب أن يكون أكبر من صفر');
    if (!reason.trim()) return setError('السبب مطلوب');
    setBusy(true);
    setError(null);
    try {
      await correctPriceItem(contractId, item.id, { newPrice: Number(newPrice), reason: reason.trim() });
      onSaved('تم تعديل السعر في القائمة السارية');
    } catch (err) {
      setError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تعديل السعر');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={() => !busy && onClose()} maxWidth="xs" fullWidth>
      <DialogTitle>تعديل السعر</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="اسم الخدمة" value={item?.serviceName || ''} size="small" InputProps={{ readOnly: true }} />
          <TextField label="كود الخدمة" value={item?.serviceCode || ''} size="small" InputProps={{ readOnly: true }} />
          <TextField label="السعر الحالي" value={money(item?.contractPrice)} size="small" InputProps={{ readOnly: true }} />
          <TextField
            label="السعر الجديد (د.ل)"
            type="number"
            value={newPrice}
            onChange={(e) => setNewPrice(e.target.value)}
            size="small"
            autoFocus
          />
          <TextField label="السبب" value={reason} onChange={(e) => setReason(e.target.value)} size="small" multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          إلغاء
        </Button>
        <Button variant="contained" onClick={save} disabled={busy || !(Number(newPrice) > 0) || !reason.trim()}>
          حفظ
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Add service ──────────────────────────────────────────────────────────────
export const AddServiceDialog = ({ open, contractId, onClose, onSaved }) => {
  const categories = useCategories(open);
  const [form, setForm] = useState({ serviceCode: '', serviceName: '', category: null, linked: null, price: '', reason: '' });
  const [linkedOptions, setLinkedOptions] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      setForm({ serviceCode: '', serviceName: '', category: null, linked: null, price: '', reason: '' });
      setLinkedOptions([]);
      setError(null);
    }
  }, [open]);

  const searchLinked = async (q) => {
    if (!q || q.length < 2) return setLinkedOptions([]);
    try {
      const res = await lookupMedicalServices({ q });
      setLinkedOptions(Array.isArray(res) ? res : res?.content || []);
    } catch (err) {
      console.error('lookup failed', err);
      setLinkedOptions([]); // never block add on lookup failure
    }
  };

  const set = (k, v) => setForm((p) => ({ ...p, [k]: v }));

  const save = async () => {
    if (!form.serviceName.trim()) return setError('اسم الخدمة مطلوب');
    if (!form.category) return setError('التصنيف مطلوب');
    if (!(Number(form.price) > 0)) return setError('السعر يجب أن يكون أكبر من صفر');
    if (!form.reason.trim()) return setError('السبب مطلوب');
    setBusy(true);
    setError(null);
    try {
      await addPriceListService(contractId, {
        serviceCode: form.serviceCode.trim() || null,
        serviceName: form.serviceName.trim(),
        categoryId: form.category.id,
        medicalServiceId: form.linked?.id || null,
        price: Number(form.price),
        reason: form.reason.trim()
      });
      onSaved('تمت إضافة الخدمة إلى القائمة السارية');
    } catch (err) {
      setError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل إضافة الخدمة');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={() => !busy && onClose()} maxWidth="sm" fullWidth>
      <DialogTitle>إضافة خدمة جديدة</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Autocomplete
            options={linkedOptions}
            value={form.linked}
            onChange={(e, v) => {
              set('linked', v);
              if (v) {
                if (!form.serviceCode) set('serviceCode', v.code || '');
                if (!form.serviceName) set('serviceName', v.nameAr || v.name || '');
              }
            }}
            onInputChange={(e, v) => searchLinked(v)}
            getOptionLabel={(o) => `${o.code || ''} — ${o.nameAr || o.name || ''}`}
            isOptionEqualToValue={(o, v) => o.id === v.id}
            noOptionsText="اكتب حرفين للبحث في الكتالوج (اختياري)"
            renderInput={(params) => <TextField {...params} label="ربط بخدمة من الكتالوج (اختياري)" size="small" />}
          />
          <TextField label="كود الخدمة" value={form.serviceCode} onChange={(e) => set('serviceCode', e.target.value)} size="small" />
          <TextField label="اسم الخدمة" value={form.serviceName} onChange={(e) => set('serviceName', e.target.value)} size="small" />
          <Autocomplete
            options={categories}
            value={form.category}
            onChange={(e, v) => set('category', v)}
            getOptionLabel={catLabel}
            isOptionEqualToValue={(o, v) => o.id === v.id}
            renderInput={(params) => <TextField {...params} label="التصنيف / الفئة (من النظام)" size="small" />}
          />
          <TextField label="السعر (د.ل)" type="number" value={form.price} onChange={(e) => set('price', e.target.value)} size="small" />
          <TextField label="السبب" value={form.reason} onChange={(e) => set('reason', e.target.value)} size="small" multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          إلغاء
        </Button>
        <Button variant="contained" onClick={save} disabled={busy}>
          إضافة
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Deactivate service ───────────────────────────────────────────────────────
export const DeactivateServiceDialog = ({ open, contractId, item, onClose, onSaved }) => {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      setReason('');
      setError(null);
    }
  }, [open]);

  const save = async () => {
    if (!reason.trim()) return setError('السبب مطلوب');
    setBusy(true);
    setError(null);
    try {
      await deactivatePriceItem(contractId, item.id, { reason: reason.trim() });
      onSaved('تم إيقاف الخدمة');
    } catch (err) {
      setError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل إيقاف الخدمة');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={() => !busy && onClose()} maxWidth="xs" fullWidth>
      <DialogTitle>إيقاف الخدمة</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <DialogContentText>سيتم إخفاء الخدمة من القائمة السارية (لا تُحذف فعليًا). لا تُنشأ نسخة جديدة.</DialogContentText>
          <TextField label="اسم الخدمة" value={item?.serviceName || ''} size="small" InputProps={{ readOnly: true }} />
          <TextField label="كود الخدمة" value={item?.serviceCode || ''} size="small" InputProps={{ readOnly: true }} />
          <TextField label="السعر الحالي" value={money(item?.contractPrice)} size="small" InputProps={{ readOnly: true }} />
          <TextField
            label="السبب"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            size="small"
            multiline
            minRows={2}
            autoFocus
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          إلغاء
        </Button>
        <Button variant="contained" color="error" onClick={save} disabled={busy || !reason.trim()}>
          إيقاف
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Classification / code correction ─────────────────────────────────────────
export const ClassificationCorrectionDialog = ({ open, contractId, item, onClose, onSaved }) => {
  const categories = useCategories(open);
  const [form, setForm] = useState({ newServiceCode: '', newServiceName: '', category: null, reason: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      setForm({ newServiceCode: item?.serviceCode || '', newServiceName: item?.serviceName || '', category: null, reason: '' });
      setError(null);
    }
  }, [open, item]);

  const set = (k, v) => setForm((p) => ({ ...p, [k]: v }));

  const save = async () => {
    if (!form.reason.trim()) return setError('السبب مطلوب');
    setBusy(true);
    setError(null);
    try {
      await correctItemClassification(contractId, item.id, {
        newServiceCode: form.newServiceCode.trim() || null,
        newServiceName: form.newServiceName.trim() || null,
        newCategoryId: form.category?.id || null,
        reason: form.reason.trim()
      });
      onSaved('تم تعديل التصنيف/الكود');
    } catch (err) {
      setError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل التعديل');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={() => !busy && onClose()} maxWidth="sm" fullWidth>
      <DialogTitle>تعديل التصنيف / الكود</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Typography variant="caption" color="text.secondary">
            التصنيف الحالي: {item?.categoryName || item?.medicalCategoryName || '—'}
          </Typography>
          <TextField
            label="الكود الجديد"
            value={form.newServiceCode}
            onChange={(e) => set('newServiceCode', e.target.value)}
            size="small"
          />
          <TextField
            label="الاسم المعروض الجديد (اختياري)"
            value={form.newServiceName}
            onChange={(e) => set('newServiceName', e.target.value)}
            size="small"
          />
          <Autocomplete
            options={categories}
            value={form.category}
            onChange={(e, v) => set('category', v)}
            getOptionLabel={catLabel}
            isOptionEqualToValue={(o, v) => o.id === v.id}
            renderInput={(params) => <TextField {...params} label="التصنيف الجديد (اختياري)" size="small" />}
          />
          <TextField label="السبب" value={form.reason} onChange={(e) => set('reason', e.target.value)} size="small" multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          إلغاء
        </Button>
        <Button variant="contained" onClick={save} disabled={busy || !form.reason.trim()}>
          حفظ
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Audit trail ──────────────────────────────────────────────────────────────
export const PriceAuditDialog = ({ open, contractId, onClose }) => {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    getContractPriceAudit(contractId)
      .then((r) => setRows(Array.isArray(r) ? r : []))
      .catch((err) => console.error('audit load failed', err))
      .finally(() => setLoading(false));
  }, [open, contractId]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>سجل التعديلات</DialogTitle>
      <DialogContent>
        <TableContainer sx={{ maxHeight: 480 }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell>العملية</TableCell>
                <TableCell>الكود</TableCell>
                <TableCell>الخدمة</TableCell>
                <TableCell>القيمة السابقة</TableCell>
                <TableCell>القيمة الجديدة</TableCell>
                <TableCell>السبب</TableCell>
                <TableCell>المستخدم</TableCell>
                <TableCell>التاريخ</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell>
                    <Chip size="small" variant="outlined" label={OPERATION_LABELS[r.operationType] || r.operationType} />
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                      {r.serviceCode || '—'}
                    </Typography>
                  </TableCell>
                  <TableCell>{r.serviceName || '—'}</TableCell>
                  <TableCell>{r.oldPrice != null ? money(r.oldPrice) : r.oldValue || '—'}</TableCell>
                  <TableCell>{r.newPrice != null ? money(r.newPrice) : r.newValue || '—'}</TableCell>
                  <TableCell>
                    <Typography variant="caption">{r.reason || '—'}</Typography>
                  </TableCell>
                  <TableCell>{r.changedBy || '—'}</TableCell>
                  <TableCell>
                    <Typography variant="caption">{r.changedAt ? new Date(r.changedAt).toLocaleString('en-GB') : '—'}</Typography>
                  </TableCell>
                </TableRow>
              ))}
              {!loading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 3 }}>
                    <Typography variant="caption" color="text.secondary">
                      لا توجد تعديلات مسجّلة بعد
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>إغلاق</Button>
      </DialogActions>
    </Dialog>
  );
};

const itemShape = PropTypes.shape({
  id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  serviceName: PropTypes.string,
  serviceCode: PropTypes.string,
  contractPrice: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  categoryName: PropTypes.string,
  medicalCategoryName: PropTypes.string
});
const commonProps = {
  open: PropTypes.bool.isRequired,
  contractId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  onClose: PropTypes.func.isRequired,
  onSaved: PropTypes.func.isRequired
};
PriceCorrectionDialog.propTypes = { ...commonProps, item: itemShape };
AddServiceDialog.propTypes = commonProps;
DeactivateServiceDialog.propTypes = { ...commonProps, item: itemShape };
ClassificationCorrectionDialog.propTypes = { ...commonProps, item: itemShape };
PriceAuditDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  contractId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  onClose: PropTypes.func.isRequired
};
