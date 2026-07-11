import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, Link as RouterLink } from 'react-router-dom';
import { classificationService } from 'services/api/classification.service';
import { formatCurrency } from 'utils/formatters';

// MUI
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Autocomplete,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  Snackbar,
  Stack,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import BlockIcon from '@mui/icons-material/Block';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DoneAllIcon from '@mui/icons-material/DoneAll';

// Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import UnifiedMedicalTable from 'components/common/UnifiedMedicalTable';
import HelpDialog from 'components/common/HelpDialog';

/**
 * Medical Classification Workspace (MC-2)
 *
 * Owner directive: not an error-fixing screen — the place where WAAD builds
 * its medical dictionary. Every decision adds knowledge (aliases + history),
 * so each new provider import needs less review. The UX makes that visible:
 * a knowledge counter, "learned" badges, and a shrinking critical queue.
 *
 * A4/A5: only the critical minority is shown; the high-confidence majority
 * stays hidden behind the explicit, audited "اعتماد المتبقي" button.
 */

const CRITICAL_TABS = [
  { key: 'UNKNOWN', label: 'غير معروفة', countKey: 'unknownQueue' },
  { key: 'LOW_CONFIDENCE', label: 'منخفضة الثقة', countKey: 'lowConfidenceQueue' },
  { key: 'DUPLICATE', label: 'مكررة', countKey: 'duplicateQueue' },
  { key: 'GUARD', label: 'محظورات', countKey: 'guardQueue' }
];
const AUDIT_TABS = [
  { key: 'PENDING_BULK', label: 'موثوقة (بانتظار اعتماد المتبقي)' },
  { key: 'APPROVED', label: 'معتمدة' },
  { key: 'REJECTED', label: 'مرفوضة' }
];

const money = (v) => (v == null ? '—' : formatCurrency(v, false));

const ClassificationReview = () => {
  const { id: importId } = useParams();
  const navigate = useNavigate();

  const [summary, setSummary] = useState(null);
  const [tab, setTab] = useState('UNKNOWN');
  const [lines, setLines] = useState([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);

  const [categories, setCategories] = useState([]);
  const [selected, setSelected] = useState([]);

  // Decision dialog (single line or bulk)
  const [decisionTarget, setDecisionTarget] = useState(null); // line | 'BULK'
  const [decisionCategory, setDecisionCategory] = useState(null);
  const [decisionService, setDecisionService] = useState(null);
  const [serviceOptions, setServiceOptions] = useState([]);
  const [decisionNote, setDecisionNote] = useState('');
  const [saving, setSaving] = useState(false);

  const [approveRemainingOpen, setApproveRemainingOpen] = useState(false);

  const isCritical = CRITICAL_TABS.some((t) => t.key === tab);

  const fetchSummary = useCallback(async () => {
    try {
      setSummary(await classificationService.getReviewSummary(importId));
    } catch (err) {
      console.error(err);
      setError(err?.response?.data?.message || 'تعذر تحميل ملخص المراجعة');
    }
  }, [importId]);

  const fetchLines = useCallback(async () => {
    setLoading(true);
    try {
      setError(null);
      const data = isCritical
        ? await classificationService.getReviewQueue(importId, tab, { page, size: rowsPerPage })
        : await classificationService.getImportLines(importId, { reviewStatus: tab, page, size: rowsPerPage });
      setLines(data?.content ?? []);
      setTotalCount(data?.totalElements ?? 0);
    } catch (err) {
      console.error(err);
      setError(err?.response?.data?.message || 'تعذر تحميل الأسطر');
    } finally {
      setLoading(false);
    }
  }, [importId, tab, page, rowsPerPage, isCritical]);

  useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);
  useEffect(() => {
    setSelected([]);
    fetchLines();
  }, [fetchLines]);
  useEffect(() => {
    classificationService
      .getCategoryPicker(importId)
      .then((list) => setCategories(Array.isArray(list) ? list : []))
      .catch((err) => console.error('categories picker failed:', err));
  }, [importId]);

  const refresh = useCallback(async () => {
    await Promise.all([fetchSummary(), fetchLines()]);
  }, [fetchSummary, fetchLines]);

  // ── decisions ─────────────────────────────────────────────────────────────

  const openDecision = (target) => {
    setDecisionTarget(target);
    setDecisionService(null);
    setServiceOptions([]);
    setDecisionNote('');
    const suggestedId = target !== 'BULK' ? target?.suggestedCategoryId : null;
    setDecisionCategory(categories.find((c) => c.id === suggestedId) || null);
  };

  const submitDecision = async (action) => {
    setSaving(true);
    try {
      const base = {
        action,
        categoryId: decisionCategory?.id ?? null,
        serviceId: decisionService?.id ?? null,
        note: decisionNote || null
      };
      if (decisionTarget === 'BULK') {
        await classificationService.decideBulk(importId, { ...base, lineIds: selected });
      } else {
        await classificationService.decideLine(importId, decisionTarget.id, base);
      }
      setToast(action === 'APPROVE' ? 'تم الاعتماد — أُضيفت معرفة جديدة إلى قاموس وعد الطبي ✨' : 'تم الرفض وتسجيل السبب');
      setDecisionTarget(null);
      setSelected([]);
      await refresh();
    } catch (err) {
      console.error(err);
      setError(err?.response?.data?.message || 'فشل تسجيل القرار');
    } finally {
      setSaving(false);
    }
  };

  // MC-4A: the confirmation dialog's confirm = finish review (Approve
  // Remaining A5 + auto version + validation), then navigate to the report.
  const handleApproveRemaining = async () => {
    setSaving(true);
    try {
      const result = await classificationService.finishReview(importId);
      setToast(`اكتملت المراجعة — اعتُمد ${result?.bulkApproved ?? ''} سطرًا موثوقًا ✔`);
      setApproveRemainingOpen(false);
      if (result?.versionId) {
        navigate(`/classification/versions/${result.versionId}`);
        return;
      }
      setError('اكتملت المراجعة، لكن لا يوجد عقد نشط للمرفق — فعّل عقدًا ثم افتح التقرير من القائمة');
      await refresh();
    } catch (err) {
      console.error(err);
      setError(err?.response?.data?.message || 'فشل إنهاء المراجعة');
    } finally {
      setSaving(false);
    }
  };

  const searchServices = async (q) => {
    if (!q || q.length < 2) return setServiceOptions([]);
    try {
      setServiceOptions((await classificationService.searchCatalogServices(importId, q)) ?? []);
    } catch (err) {
      console.error(err);
    }
  };

  // MC-4A: ONE action ends the review — Approve Remaining (A5, audited) +
  // auto-create the version + financial validation, then open the report.
  const handleFinishReview = async () => {
    setSaving(true);
    try {
      // Already-finished import with an existing report → just open it
      const imp = await classificationService.getImport(importId);
      if (imp?.versionId) {
        navigate(`/classification/versions/${imp.versionId}`);
        return;
      }
      const result = await classificationService.finishReview(importId);
      if (result?.versionId) {
        navigate(`/classification/versions/${result.versionId}`);
      } else {
        setError('اكتملت المراجعة، لكن لا يوجد عقد نشط للمرفق — فعّل عقدًا ثم افتح التقرير من القائمة');
        await refresh();
      }
    } catch (err) {
      console.error(err);
      setError(err?.response?.data?.message || 'فشل إنهاء المراجعة');
    } finally {
      setSaving(false);
    }
  };

  // ── table ─────────────────────────────────────────────────────────────────

  const columns = useMemo(() => {
    const cols = [];
    if (isCritical) cols.push({ id: 'select', label: '', minWidth: 40 });
    cols.push(
      { id: 'rowNo', label: '#', minWidth: 50 },
      { id: 'rawName', label: 'الخدمة كما وردت من المرفق', minWidth: 240 },
      { id: 'rawPrice', label: 'السعر', minWidth: 80, align: 'center' },
      { id: 'suggestion', label: 'اقتراح المحرك', minWidth: 220 },
      { id: 'confidence', label: 'الثقة', minWidth: 90, align: 'center' },
      { id: 'reason', label: 'سبب النتيجة', minWidth: 220 }
    );
    if (isCritical) cols.push({ id: 'actions', label: 'القرار', minWidth: 170, align: 'center' });
    return cols;
  }, [isCritical]);

  const toggleSelect = (id) => setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  const renderCell = (row, column) => {
    switch (column.id) {
      case 'select':
        return <Checkbox size="small" checked={selected.includes(row.id)} onChange={() => toggleSelect(row.id)} />;
      case 'rawName':
        return (
          <Stack spacing={0.25}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {row.rawName}
            </Typography>
            {row.rawNameAlt && (
              <Typography variant="caption" color="text.secondary">
                {row.rawNameAlt}
              </Typography>
            )}
            {row.flags && (
              <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                {row.flags.split(',').map((f) => (
                  <Chip key={f} size="small" color="warning" variant="outlined" label={FLAG_LABELS[f] || f} />
                ))}
              </Stack>
            )}
          </Stack>
        );
      case 'rawPrice':
        return money(row.rawPrice);
      case 'suggestion':
        return (
          <Stack spacing={0.25}>
            <Typography variant="body2">{row.suggestedSubLabel || '—'}</Typography>
            <Stack direction="row" spacing={0.5} alignItems="center">
              {row.classificationSource === 'KNOWLEDGE_BASE' && (
                <Chip size="small" color="success" icon={<AutoAwesomeIcon />} label="من قاموس وعد" />
              )}
              {row.suggestedCategoryId == null && <Chip size="small" color="error" variant="outlined" label="بدون تصنيف معتمد" />}
              {row.referenceMatch && (
                <Typography variant="caption" color="text.secondary" noWrap sx={{ maxWidth: 180 }}>
                  أقرب مرجع: {row.referenceMatch}
                </Typography>
              )}
            </Stack>
          </Stack>
        );
      case 'confidence':
        return row.confidenceScore != null ? `${row.confidenceScore}%` : '—';
      case 'reason':
        return (
          <Typography variant="caption" color="text.secondary">
            {row.engineReason || '—'}
            {row.reviewerNote ? ` · ملاحظة: ${row.reviewerNote}` : ''}
          </Typography>
        );
      case 'actions':
        return (
          <Stack direction="row" spacing={0.5} justifyContent="center">
            <Button size="small" variant="contained" color="success" startIcon={<CheckCircleIcon />} onClick={() => openDecision(row)}>
              قرار
            </Button>
          </Stack>
        );
      default:
        return row[column.id] ?? '—';
    }
  };

  const progressPct = summary?.totalLines ? Math.round(((summary.approved + summary.rejected) / summary.totalLines) * 100) : 0;

  return (
    <MainCard>
      <ModernPageHeader
        titleKey={`مساحة عمل التصنيف الطبي — استيراد #${importId}`}
        titleIcon={<FactCheckIcon color="primary" />}
        subtitleKey="كل قرار مراجعة يضيف معرفة إلى قاموس وعد الطبي ويقلل المراجعات القادمة"
        actions={
          <Stack direction="row" spacing={1.5} alignItems="center">
            <HelpDialog
              title="مراجعة قائمة الأسعار — كيف؟"
              points={[
                'أمامك فقط الحالات التي تحتاج رأيك — الأغلبية الموثوقة معتمدة الاقتراح مسبقًا.',
                'لكل سطر: «قرار» — اعتمد اقتراح النظام أو غيّر التصنيف أو ارفض.',
                'الأسطر المكررة والأسعار الصفرية تحتاج قرارًا صريحًا منك.',
                'يمكنك تحديد عدة أسطر متشابهة واتخاذ قرار جماعي واحد.',
                'عند إفراغ كل التبويبات: زر «إنهاء المراجعة» يعتمد الموثوق ويجهّز تقرير النسخة تلقائيًا.',
                'كل قرار تتخذه يُعلّم النظام — نفس الصياغة لن تسألك مرة أخرى.'
              ]}
            />
            <Button component={RouterLink} to="/classification/imports" startIcon={<ArrowBackIcon />}>
              القوائم
            </Button>
            {/* MC-4A: ONE closing action = Approve Remaining (A5) + auto version + validation */}
            {summary?.status === 'REVIEW_COMPLETE' ? (
              <Button variant="contained" color="primary" disabled={saving} onClick={handleFinishReview}>
                فتح تقرير النسخة
              </Button>
            ) : (
              <Tooltip
                title={
                  summary?.approveRemainingEnabled
                    ? 'يعتمد الأسطر الموثوقة المتبقية (إجراء بشري مسجّل) ويجهّز تقرير النسخة'
                    : 'يتفعّل بعد إنهاء كل الحالات التي بحاجة مراجعة'
                }
              >
                <span>
                  <Button
                    variant="contained"
                    color="success"
                    startIcon={<DoneAllIcon />}
                    disabled={!summary?.approveRemainingEnabled || saving}
                    onClick={() => setApproveRemainingOpen(true)}
                  >
                    إنهاء المراجعة واعتماد الموثوق ({summary?.pendingBulk ?? 0})
                  </Button>
                </span>
              </Tooltip>
            )}
          </Stack>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: '1.0rem' }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Progress + knowledge strip */}
      {summary && (
        <Box sx={{ mb: '1.0rem' }}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 0.75 }}>
            <Chip size="small" color="warning" label={`المتبقي للمراجعة: ${summary.needsReview}`} />
            <Chip size="small" color="default" variant="outlined" label={`موثوقة بانتظار اعتماد المتبقي: ${summary.pendingBulk}`} />
            <Chip size="small" color="success" variant="outlined" label={`معتمدة: ${summary.approved}`} />
            <Chip size="small" color="error" variant="outlined" label={`مرفوضة: ${summary.rejected}`} />
            <Chip size="small" color="primary" icon={<AutoAwesomeIcon />} label={`قرارات أُضيفت للقاموس: ${summary.knowledgeDecisions}`} />
            {summary.status === 'REVIEW_COMPLETE' && <Chip size="small" color="success" label="اكتملت المراجعة ✔" />}
          </Stack>
          <LinearProgress variant="determinate" value={progressPct} sx={{ height: 8, borderRadius: 1 }} />
          <Typography variant="caption" color="text.secondary">
            التقدم: {progressPct}% من {summary.totalLines} خدمة
          </Typography>
        </Box>
      )}

      {/* Tabs: critical queues first, audit tabs after */}
      <Tabs
        value={tab}
        onChange={(e, v) => {
          setTab(v);
          setPage(0);
        }}
        variant="scrollable"
        sx={{ mb: 1 }}
      >
        {CRITICAL_TABS.map((t) => (
          <Tab key={t.key} value={t.key} label={`${t.label} (${summary?.[t.countKey] ?? 0})`} />
        ))}
        {AUDIT_TABS.map((t) => (
          <Tab key={t.key} value={t.key} label={t.label} />
        ))}
      </Tabs>

      {isCritical && selected.length > 0 && (
        <Stack direction="row" spacing={1} sx={{ mb: 1 }} alignItems="center">
          <Chip size="small" label={`${selected.length} محدد`} />
          <Button size="small" variant="outlined" color="success" onClick={() => openDecision('BULK')}>
            قرار جماعي للمحدد
          </Button>
        </Stack>
      )}

      <UnifiedMedicalTable
        persistKey={`classification-review-${tab}`}
        columns={columns}
        rows={lines}
        loading={loading}
        totalCount={totalCount}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={setPage}
        onRowsPerPageChange={(s) => {
          setRowsPerPage(s);
          setPage(0);
        }}
        renderCell={renderCell}
        rowsPerPageOptions={[10, 25, 50]}
      />

      {/* Decision dialog */}
      <Dialog open={decisionTarget != null} onClose={() => !saving && setDecisionTarget(null)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {decisionTarget === 'BULK' ? `قرار جماعي (${selected.length} سطرًا)` : `قرار: ${decisionTarget?.rawName ?? ''}`}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {decisionTarget !== 'BULK' && decisionTarget && (
              <Alert severity="info" icon={<AutoAwesomeIcon />}>
                اقتراح المحرك: {decisionTarget.suggestedSubLabel || '—'}
                {decisionTarget.engineReason ? ` — ${decisionTarget.engineReason}` : ''}
              </Alert>
            )}
            <Autocomplete
              options={categories}
              value={decisionCategory}
              onChange={(e, v) => setDecisionCategory(v)}
              getOptionLabel={(o) => `${o.code} — ${o.name}`}
              isOptionEqualToValue={(o, v) => o.id === v.id}
              renderInput={(params) => <TextField {...params} label="التصنيف المعتمد (وعد)" size="small" />}
            />
            {/* Progressive disclosure (design review §9.4): expert tools folded away */}
            <Accordion variant="outlined" disableGutters>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="body2" color="text.secondary">
                  خيارات متقدمة
                </Typography>
              </AccordionSummary>
              <AccordionDetails>
                <Stack spacing={2}>
                  {decisionTarget !== 'BULK' && (
                    <Autocomplete
                      options={serviceOptions}
                      value={decisionService}
                      onChange={(e, v) => setDecisionService(v)}
                      onInputChange={(e, v) => searchServices(v)}
                      getOptionLabel={(o) => `${o.code} — ${o.name}`}
                      isOptionEqualToValue={(o, v) => o.id === v.id}
                      noOptionsText="اكتب حرفين على الأقل للبحث في الكتالوج"
                      renderInput={(params) => <TextField {...params} label="ربط بخدمة موجودة في الكتالوج" size="small" />}
                    />
                  )}
                  <TextField
                    label="ملاحظة (تُحفظ في السجل)"
                    size="small"
                    value={decisionNote}
                    onChange={(e) => setDecisionNote(e.target.value)}
                    multiline
                    minRows={2}
                  />
                </Stack>
              </AccordionDetails>
            </Accordion>
            <Typography variant="caption" color="text.secondary">
              اعتمادك يعلّم النظام هذه الصياغة — لن يسألك عنها مرة أخرى.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDecisionTarget(null)} disabled={saving}>
            إغلاق
          </Button>
          <Button color="error" startIcon={<BlockIcon />} disabled={saving} onClick={() => submitDecision('REJECT')}>
            رفض
          </Button>
          <Button
            variant="contained"
            color="success"
            startIcon={<CheckCircleIcon />}
            disabled={saving || !decisionCategory}
            onClick={() => submitDecision('APPROVE')}
          >
            اعتماد
          </Button>
        </DialogActions>
      </Dialog>

      {/* Finish review confirmation — Approve Remaining (A5) + auto report (MC-4A) */}
      <Dialog open={approveRemainingOpen} onClose={() => !saving && setApproveRemainingOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>إنهاء المراجعة</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            <Alert severity="info">
              سيُعتمد <strong>{summary?.pendingBulk ?? 0}</strong> سطرًا موثوقًا (ثقة عالية، بلا تحذيرات) باسمك، ثم يجهّز النظام{' '}
              <strong>تقرير النسخة</strong> تلقائيًا ويفتحه لك.
            </Alert>
            <Typography variant="body2" color="text.secondary">
              كل الحالات الحرجة أُنهيت ✔ — هذا إجراء بشري صريح ومسجّل، وليس اعتمادًا تلقائيًا.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setApproveRemainingOpen(false)} disabled={saving}>
            إلغاء
          </Button>
          <Button variant="contained" color="success" startIcon={<DoneAllIcon />} disabled={saving} onClick={handleApproveRemaining}>
            إنهاء المراجعة
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={toast != null}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        message={toast}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </MainCard>
  );
};

const FLAG_LABELS = {
  DUPLICATE_IN_FILE: 'مكررة في الملف',
  ZERO_OR_NEGATIVE_PRICE: 'سعر صفري/سالب',
  CATEGORY_UNRESOLVED: 'بدون تصنيف معتمد'
};

export default ClassificationReview;
