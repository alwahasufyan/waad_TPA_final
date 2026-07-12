import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { classificationService } from 'services/api/classification.service';
import { providersService } from 'services/api/providers.service';

// MUI
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputLabel,
  LinearProgress,
  MenuItem,
  Select,
  Stack,
  Switch,
  Tooltip,
  Typography
} from '@mui/material';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import RefreshIcon from '@mui/icons-material/Refresh';
import ScienceIcon from '@mui/icons-material/Science';
import CancelIcon from '@mui/icons-material/Cancel';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import AssessmentIcon from '@mui/icons-material/Assessment';

// Components
import HelpDialog from 'components/common/HelpDialog';

// Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import UnifiedMedicalTable from 'components/common/UnifiedMedicalTable';

/**
 * Medical Classification Engine — Imports (MC-1)
 *
 * Import & staging layer ONLY (owner condition #3): upload + status +
 * counters + provenance. The review workspace arrives in MC-2 and the
 * version/publish flow in MC-3 — nothing here approves or publishes.
 */

// MC-4A (design review §3): users see FOUR statuses only — internal states
// stay in the DB for audit but leave the UI vocabulary.
const STATUS_META = {
  UPLOADED: { label: 'قيد المعالجة', color: 'info' },
  PROCESSING: { label: 'قيد المعالجة', color: 'info' },
  CLASSIFIED: { label: 'بحاجة مراجعة', color: 'warning' },
  IN_REVIEW: { label: 'بحاجة مراجعة', color: 'warning' },
  REVIEW_COMPLETE: { label: 'بانتظار النشر', color: 'primary' },
  PUBLISHED: { label: 'منشورة', color: 'success' },
  FAILED: { label: 'فشل', color: 'error' },
  CANCELLED: { label: 'ملغاة', color: 'default' }
};

const HINTS = [
  { value: '', label: 'بدون تلميح' },
  { value: 'dental', label: 'أسنان' },
  { value: 'optics', label: 'بصريات' },
  { value: 'physio', label: 'علاج طبيعي' }
];

const ACTIVE_STATUSES = ['UPLOADED', 'PROCESSING'];

const arabicImportError = (message) => {
  if (!message) return 'فشلت معالجة الملف. يرجى مراجعة صيغة الملف ثم إعادة المحاولة.';
  if (message.includes('Cannot deserialize') || message.includes('BigDecimal') || message.includes('not a valid representation')) {
    return 'فشلت المعالجة لأن الملف يحتوي أسعارًا غير رقمية أو نطاقات أسعار مثل 550-650. أعد رفع الملف بعد التحديث؛ سيتم إرسال هذه الصفوف للمراجعة بدل فشل الملف كاملًا.';
  }
  if (message.includes('Engine I/O failure')) {
    return 'فشل الاتصال بمحرك التصنيف أثناء المعالجة. تحقق من جاهزية المحرك ثم أعد المحاولة.';
  }
  if (message.includes('/app/uploads') || message.includes('Failed to store')) {
    return 'فشل حفظ ملف الرفع داخل الخادم. تحقق من التخزين ثم أعد المحاولة.';
  }
  return message;
};

const ClassificationImports = () => {
  const [imports, setImports] = useState([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [engineProblem, setEngineProblem] = useState(null);
  const [hideEmptyRows, setHideEmptyRows] = useState(true);

  // Upload dialog state
  const [dialogOpen, setDialogOpen] = useState(false);
  const [providers, setProviders] = useState([]);
  const [providerId, setProviderId] = useState('');
  const [hint, setHint] = useState('');
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState(null);

  const pollRef = useRef(null);

  const activeImportsCount = useMemo(() => imports.filter((imp) => ACTIVE_STATUSES.includes(imp.status)).length, [imports]);

  const visibleImports = useMemo(() => {
    if (!hideEmptyRows) return imports;
    return imports.filter((imp) => {
      const hasNoResult = Number(imp.totalLines || 0) === 0
        && Number(imp.unknownServices || 0) === 0
        && Number(imp.lowConfidence || 0) === 0
        && Number(imp.duplicates || 0) === 0;
      return ACTIVE_STATUSES.includes(imp.status) || !hasNoResult;
    });
  }, [imports, hideEmptyRows]);

  const fetchImports = useCallback(async () => {
    try {
      setError(null);
      const data = await classificationService.getImports({ page, size: rowsPerPage });
      setImports(data?.content ?? []);
      setTotalCount(data?.totalElements ?? 0);
    } catch (err) {
      console.error('Failed to fetch imports:', err);
      setError(err?.response?.data?.message || 'تعذر تحميل قائمة الاستيرادات');
    }
  }, [page, rowsPerPage]);

  useEffect(() => {
    setLoading(true);
    fetchImports().finally(() => setLoading(false));
  }, [fetchImports]);

  // Poll while any import is still processing (status refresh without reload)
  useEffect(() => {
    const hasActive = imports.some((imp) => ACTIVE_STATUSES.includes(imp.status));
    if (hasActive && !pollRef.current) {
      pollRef.current = setInterval(fetchImports, 5000);
    }
    if (!hasActive && pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
    return () => {
      if (pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, [imports, fetchImports]);

  useEffect(() => {
    classificationService
      .engineHealth()
      .then(({ ok, problem }) => setEngineProblem(ok ? null : problem))
      .catch(() => setEngineProblem('تعذر فحص جاهزية محرك التصنيف'));
  }, []);

  // MC-4B (D3): arriving from the contract's «استيراد قائمة أسعار» —
  // open the upload dialog with the provider preselected.
  const [searchParams, setSearchParams] = useSearchParams();
  const prefillHandled = useRef(false);
  useEffect(() => {
    if (prefillHandled.current) return;
    const preProvider = searchParams.get('providerId');
    const shouldOpen = searchParams.get('open') === '1';
    if (shouldOpen) {
      prefillHandled.current = true;
      if (preProvider) setProviderId(Number(preProvider));
      openUploadDialog();
      setSearchParams({}, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const openUploadDialog = async () => {
    setUploadError(null);
    setFile(null);
    setDialogOpen(true);
    if (providers.length === 0) {
      try {
        const list = await providersService.getSelector();
        setProviders(Array.isArray(list) ? list : []);
      } catch (err) {
        console.error('Failed to fetch providers:', err);
        setUploadError('تعذر تحميل قائمة المرافق');
      }
    }
  };

  const handleUpload = async () => {
    if (!providerId || !file) {
      setUploadError('اختر المرفق والملف أولًا');
      return;
    }
    setUploading(true);
    setUploadError(null);
    try {
      await classificationService.uploadImport({ providerId, hint: hint || null, file });
      setDialogOpen(false);
      setProviderId('');
      setHint('');
      setFile(null);
      await fetchImports();
    } catch (err) {
      console.error('Upload failed:', err);
      // Idempotency rejection (same file hash) and validation errors arrive here
      setUploadError(err?.response?.data?.message || 'فشل رفع الملف');
    } finally {
      setUploading(false);
    }
  };

  const handleCancel = async (id) => {
    try {
      await classificationService.cancelImport(id);
      await fetchImports();
    } catch (err) {
      console.error('Cancel failed:', err);
      setError(err?.response?.data?.message || 'تعذر إلغاء الاستيراد');
    }
  };

  const columns = useMemo(
    () => [
      { id: 'id', label: '#', minWidth: 60 },
      { id: 'providerName', label: 'المرفق', minWidth: 160 },
      { id: 'fileName', label: 'الملف', minWidth: 200 },
      { id: 'status', label: 'الحالة', minWidth: 150 },
      { id: 'totalLines', label: 'الخدمات', minWidth: 80, align: 'center' },
      { id: 'needsReview', label: 'تحتاج مراجعة', minWidth: 110, align: 'center' },
      { id: 'duplicates', label: 'مكررة', minWidth: 70, align: 'center' },
      { id: 'provenance', label: 'تفاصيل تقنية', minWidth: 130 },
      { id: 'uploadedAt', label: 'تاريخ الرفع', minWidth: 140 },
      { id: 'actions', label: 'إجراءات', minWidth: 90, align: 'center' }
    ],
    []
  );

  const renderCell = (row, column) => {
    switch (column.id) {
      case 'status': {
        const meta = STATUS_META[row.status] || { label: row.status, color: 'default' };
        // Counts speak (design review §9.6): the pending-work number rides the chip
        const needs = (row.unknownServices ?? 0) + (row.lowConfidence ?? 0);
        const label = ['CLASSIFIED', 'IN_REVIEW'].includes(row.status) && needs > 0 ? `${meta.label} (${needs})` : meta.label;
        const tooltip = row.status === 'FAILED'
          ? arabicImportError(row.errorMessage)
          : ACTIVE_STATUSES.includes(row.status)
            ? 'جاري معالجة الملف في الخلفية. يتم تحديث الحالة تلقائيًا كل 5 ثوانٍ.'
            : row.errorMessage || '';
        return (
          <Tooltip title={tooltip}>
            <Chip
              size="small"
              color={meta.color}
              label={ACTIVE_STATUSES.includes(row.status) ? 'جاري المعالجة' : label}
              variant={row.status === 'PROCESSING' ? 'filled' : 'outlined'}
              icon={ACTIVE_STATUSES.includes(row.status) ? <CircularProgress size={14} color="inherit" /> : undefined}
            />
          </Tooltip>
        );
      }
      case 'needsReview':
        return (row.unknownServices ?? 0) + (row.lowConfidence ?? 0) || '—';
      case 'duplicates':
        return row.duplicates || '—';
      case 'provenance':
        return row.engineVersion ? (
          <Tooltip title={`hash: ${row.fileHash || '—'} · قاموس: ${row.dictionaryVersion ? 'موثق' : '—'}`}>
            <Typography variant="caption" color="text.secondary">
              {row.engineVersion}
              {row.executionMs != null ? ` · ${(row.executionMs / 1000).toFixed(1)}s` : ''}
            </Typography>
          </Tooltip>
        ) : (
          '—'
        );
      case 'uploadedAt':
        return row.uploadedAt ? new Date(row.uploadedAt).toLocaleString('en-GB') : '—';
      case 'actions':
        return (
          <Stack direction="row" spacing={0.5} justifyContent="center">
            {['CLASSIFIED', 'IN_REVIEW'].includes(row.status) && (
              <Button
                size="small"
                variant="contained"
                startIcon={<FactCheckIcon />}
                component={RouterLink}
                to={`/classification/imports/${row.id}/review`}
              >
                مراجعة
              </Button>
            )}
            {row.versionId && ['REVIEW_COMPLETE', 'PUBLISHED'].includes(row.status) && (
              <Button
                size="small"
                variant={row.status === 'REVIEW_COMPLETE' ? 'contained' : 'outlined'}
                color={row.status === 'REVIEW_COMPLETE' ? 'primary' : 'success'}
                startIcon={<AssessmentIcon />}
                component={RouterLink}
                to={`/classification/versions/${row.versionId}`}
              >
                {row.status === 'REVIEW_COMPLETE' ? 'التقرير والنشر' : 'التقرير'}
              </Button>
            )}
            {['UPLOADED', 'CLASSIFIED', 'IN_REVIEW'].includes(row.status) && (
              <Button size="small" color="error" startIcon={<CancelIcon />} onClick={() => handleCancel(row.id)}>
                إلغاء
              </Button>
            )}
          </Stack>
        );
      default:
        return row[column.id] ?? '—';
    }
  };

  return (
    <MainCard>
      <ModernPageHeader
        titleKey="قوائم أسعار المرافق"
        titleIcon={<ScienceIcon color="primary" />}
        subtitleKey="ارفع قائمة الأسعار، راجِع الحالات الحرجة فقط، ثم اعتمد وانشر"
        actions={
          <Stack direction="row" spacing={1.5} alignItems="center">
            <HelpDialog
              title="قوائم أسعار المرافق — كيف تعمل؟"
              points={[
                'ارفع ملف الأسعار كما استلمته من المرفق (Excel أو PDF) — لا تنسّقه.',
                'النظام يصنّف الخدمات تلقائيًا خلال دقائق.',
                'راجِع فقط الحالات المعلّمة «بحاجة مراجعة» — الأغلبية الموثوقة لا تحتاجك.',
                'عند إنهاء المراجعة يجهّز النظام تقرير النسخة تلقائيًا.',
                'من التقرير: «اعتماد التقرير» ثم «نشر» — ويعمل العقد بالأسعار الجديدة فورًا.',
                'نفس الملف لا يُقبل مرتين لنفس المرفق (حماية من التكرار).',
                'كل قرار تتخذه يعلّم النظام — فتقل المراجعات مع كل مرفق جديد.'
              ]}
            />
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={fetchImports} disabled={loading}>
              تحديث
            </Button>
            <Button variant="contained" startIcon={<UploadFileIcon />} onClick={openUploadDialog}>
              رفع قائمة أسعار
            </Button>
          </Stack>
        }
      />

      {engineProblem && (
        <Alert severity="warning" sx={{ mb: '1.0rem' }}>
          محرك التصنيف غير جاهز: {engineProblem}
        </Alert>
      )}
      {error && (
        <Alert severity="error" sx={{ mb: '1.0rem' }}>
          {error}
        </Alert>
      )}
      {activeImportsCount > 0 && (
        <Alert severity="info" sx={{ mb: '1.0rem' }} icon={<CircularProgress size={18} />}>
          جاري معالجة {activeImportsCount} ملف. سيتم تحديث النتائج تلقائيًا، ويمكنك ترك الصفحة والعودة لاحقًا.
          <LinearProgress sx={{ mt: 1 }} />
        </Alert>
      )}

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ xs: 'stretch', sm: 'center' }} sx={{ mb: '1.0rem' }}>
        <FormControlLabel
          control={<Switch checked={hideEmptyRows} onChange={(event) => setHideEmptyRows(event.target.checked)} />}
          label="إخفاء الاستيرادات الفارغة أو الفاشلة بدون نتائج"
        />
        {hideEmptyRows && imports.length !== visibleImports.length && (
          <Typography variant="caption" color="text.secondary">
            تم إخفاء {imports.length - visibleImports.length} صف بدون نتائج لتقليل التشتيت.
          </Typography>
        )}
      </Stack>

      {/* Empty-state guidance (design review §9.5): the 3 steps as cards */}
      {!loading && imports.length === 0 && !error && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: '1.0rem' }}>
          {[
            { n: '١', t: 'ارفع قائمة الأسعار', d: 'ملف المرفق كما هو — النظام يقرأ Excel وPDF' },
            { n: '٢', t: 'راجِع الحالات الحرجة', d: 'فقط ما يعلَّم «بحاجة مراجعة» — دقائق لا ساعات' },
            { n: '٣', t: 'اعتمد وانشر', d: 'تقرير واضح بالفروقات ثم اعتماد ونشر بزرين' }
          ].map((s) => (
            <Box
              key={s.n}
              sx={{ flex: 1, p: '1.0rem', border: '1px dashed', borderColor: 'divider', borderRadius: 1, textAlign: 'center' }}
            >
              <Typography variant="h4" color="primary">
                {s.n}
              </Typography>
              <Typography variant="subtitle2">{s.t}</Typography>
              <Typography variant="caption" color="text.secondary">
                {s.d}
              </Typography>
            </Box>
          ))}
        </Stack>
      )}

      <UnifiedMedicalTable
        persistKey="classification-imports"
        columns={columns}
        rows={visibleImports}
        loading={loading}
        totalCount={hideEmptyRows ? visibleImports.length : totalCount}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={setPage}
        onRowsPerPageChange={(size) => {
          setRowsPerPage(size);
          setPage(0);
        }}
        renderCell={renderCell}
        rowsPerPageOptions={[10, 25, 50]}
      />

      {/* Upload dialog */}
      <Dialog open={dialogOpen} onClose={() => !uploading && setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>رفع قائمة أسعار مرفق</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {uploadError && <Alert severity="error">{uploadError}</Alert>}
            <FormControl fullWidth size="small">
              <InputLabel id="cls-provider">المرفق</InputLabel>
              <Select labelId="cls-provider" value={providerId} label="المرفق" onChange={(e) => setProviderId(e.target.value)}>
                {providers.map((p) => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl fullWidth size="small">
              <InputLabel id="cls-hint">نوع المرفق (تلميح للتصنيف)</InputLabel>
              <Select labelId="cls-hint" value={hint} label="نوع المرفق (تلميح للتصنيف)" onChange={(e) => setHint(e.target.value)}>
                {HINTS.map((h) => (
                  <MenuItem key={h.value} value={h.value}>
                    {h.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}>
              {file ? file.name : 'اختر الملف (xlsx / xls / csv / pdf / pptx)'}
              <input hidden type="file" accept=".xlsx,.xls,.csv,.pdf,.pptx" onChange={(e) => setFile(e.target.files?.[0] || null)} />
            </Button>
            <Typography variant="caption" color="text.secondary">
              الرفع محمي من التكرار: نفس الملف (بنفس البصمة) لنفس المرفق لن يُقبل مرتين. التصنيف يعمل في الخلفية — تابع الحالة من الجدول.
            </Typography>
            {uploading && <LinearProgress />}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={uploading}>
            إغلاق
          </Button>
          <Button variant="contained" onClick={handleUpload} disabled={uploading || !providerId || !file}>
            رفع وبدء التصنيف
          </Button>
        </DialogActions>
      </Dialog>
    </MainCard>
  );
};

export default ClassificationImports;
