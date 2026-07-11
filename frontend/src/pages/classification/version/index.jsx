import { useCallback, useEffect, useState } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import { classificationService } from 'services/api/classification.service';
import { formatCurrency } from 'utils/formatters';

// MUI
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  LinearProgress,
  Paper,
  Snackbar,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import AssessmentIcon from '@mui/icons-material/Assessment';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import GppGoodIcon from '@mui/icons-material/GppGood';
import GppBadIcon from '@mui/icons-material/GppBad';
import PublishIcon from '@mui/icons-material/Publish';
import VerifiedIcon from '@mui/icons-material/Verified';
import ReplayIcon from '@mui/icons-material/Replay';
import PriceChangeIcon from '@mui/icons-material/PriceChange';

// Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import HelpDialog from 'components/common/HelpDialog';

/**
 * Version Comparison Dashboard (MC-3 — A11)
 *
 * The approval artifact: the approver approves THIS statistical report, not a
 * raw list of services. Owner decision: the version is a FINANCIAL ARTIFACT —
 * publish requires the A10 gate green, and after publish it is immutable.
 */

const money = (v) => (v == null ? '—' : formatCurrency(Number(v), false));
const SEVERITY_META = {
  BLOCKER: { label: 'مانع', color: 'error' },
  WARNING: { label: 'تحذير', color: 'warning' },
  INFO: { label: 'معلومة', color: 'info' }
};
const FINDING_LABELS = {
  ZERO_OR_NEGATIVE_PRICE: 'سعر صفري/سالب',
  DUPLICATE_PRICE_CONFLICT: 'تضارب سعرين لنفس الخدمة',
  PRICE_SPIKE_VS_PREVIOUS: 'قفزة سعرية عن النسخة السابقة',
  PRICE_DROP_VS_PREVIOUS: 'هبوط سعري عن النسخة السابقة',
  OUTLIER_VS_CATALOG_COST: 'شاذ عن تكلفة الكتالوج',
  OUTLIER_VS_CATEGORY_NORM: 'شاذ عن نمط الفئة',
  TOTAL_VALUE_SWING: 'تأرجح القيمة الإجمالية',
  SUSPICIOUS_ROUNDING: 'تقريب مريب'
};

const StatCard = ({ label, value, hint, color }) => (
  <Paper variant="outlined" sx={{ p: '0.75rem 1rem', textAlign: 'center', minWidth: 110 }}>
    <Typography variant="h5" sx={{ fontWeight: 800, color: color || 'text.primary' }}>
      {value ?? '—'}
    </Typography>
    <Typography variant="caption" color="text.secondary">
      {label}
    </Typography>
    {hint && (
      <Typography variant="caption" display="block" color="text.disabled">
        {hint}
      </Typography>
    )}
  </Paper>
);

const ChangesTable = ({ rows, showOld = true, showCategoryChange = false }) => (
  <TableContainer sx={{ maxHeight: 420, overflowX: 'auto' }}>
    <Table size="small" stickyHeader>
      <TableHead>
        <TableRow>
          <TableCell>الخدمة</TableCell>
          <TableCell>الكود</TableCell>
          {showOld && <TableCell align="center">السعر السابق</TableCell>}
          <TableCell align="center">السعر الجديد</TableCell>
          {showOld && <TableCell align="center">التغير %</TableCell>}
          {showCategoryChange && <TableCell>التصنيف السابق → الجديد</TableCell>}
        </TableRow>
      </TableHead>
      <TableBody>
        {(rows || []).map((r, i) => (
          <TableRow key={i} hover>
            <TableCell>{r.serviceName}</TableCell>
            <TableCell>
              <Typography variant="caption">{r.serviceCode || '—'}</Typography>
            </TableCell>
            {showOld && <TableCell align="center">{money(r.oldPrice)}</TableCell>}
            <TableCell align="center">{money(r.newPrice)}</TableCell>
            {showOld && (
              <TableCell align="center">
                {r.changePercent != null ? (
                  <Chip
                    size="small"
                    variant="outlined"
                    color={Number(r.changePercent) > 0 ? 'error' : 'success'}
                    label={`${Number(r.changePercent) > 0 ? '+' : ''}${r.changePercent}%`}
                  />
                ) : (
                  '—'
                )}
              </TableCell>
            )}
            {showCategoryChange && (
              <TableCell>
                <Typography variant="caption">
                  {r.oldCategory || '—'} ← {r.newCategory || '—'}
                </Typography>
              </TableCell>
            )}
          </TableRow>
        ))}
        {(!rows || rows.length === 0) && (
          <TableRow>
            <TableCell colSpan={6} align="center">
              <Typography variant="caption" color="text.secondary">
                لا توجد عناصر
              </Typography>
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  </TableContainer>
);

const ClassificationVersion = () => {
  const { id: versionId } = useParams();

  const [comparison, setComparison] = useState(null);
  const [findings, setFindings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);
  const [busy, setBusy] = useState(false);
  const [changesTab, setChangesTab] = useState('repriced');

  // finding action dialog
  const [findingAction, setFindingAction] = useState(null); // {finding, mode:'resolve'|'waive'|'fix'}
  const [actionNote, setActionNote] = useState('');
  const [fixPrice, setFixPrice] = useState('');

  const [publishOpen, setPublishOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setError(null);
      const [cmp, fnd] = await Promise.all([
        classificationService.getVersionComparison(versionId),
        classificationService.getVersionFindings(versionId)
      ]);
      setComparison(cmp);
      setFindings(Array.isArray(fnd) ? fnd : []);
    } catch (err) {
      console.error(err);
      setError(err?.response?.data?.message || 'تعذر تحميل تقرير النسخة');
    } finally {
      setLoading(false);
    }
  }, [versionId]);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (fn, successMsg) => {
    setBusy(true);
    try {
      await fn();
      if (successMsg) setToast(successMsg);
      await load();
    } catch (err) {
      console.error(err);
      setError(err?.response?.data?.message || 'فشل تنفيذ الإجراء');
    } finally {
      setBusy(false);
    }
  };

  const submitFindingAction = async () => {
    const { finding, mode } = findingAction;
    setBusy(true);
    try {
      if (mode === 'fix') {
        await classificationService.fixLinePrice(versionId, finding.lineRef, Number(fixPrice), actionNote);
        await classificationService.revalidateVersion(versionId);
        setToast('عُدّل السعر وأعيد التحقق');
      } else if (mode === 'waive') {
        await classificationService.waiveFinding(versionId, finding.id, actionNote);
        setToast('أُعفي التحذير بسبب موثق');
      } else {
        await classificationService.resolveFinding(versionId, finding.id, actionNote);
        setToast('عُلّمت الملاحظة كمُعالجة');
      }
      setFindingAction(null);
      setActionNote('');
      setFixPrice('');
      await load();
    } catch (err) {
      console.error(err);
      setError(err?.response?.data?.message || 'فشل تنفيذ الإجراء');
    } finally {
      setBusy(false);
    }
  };

  const isDraft = comparison?.versionStatus === 'DRAFT';
  const gateOpen = comparison?.publishGateOpen;
  const openFindings = findings.filter((f) => f.status === 'OPEN');
  const handledFindings = findings.filter((f) => f.status !== 'OPEN');
  const distribution = comparison?.priceChangeDistribution || {};
  const maxBucket = Math.max(1, ...Object.values(distribution));

  const changesTabs = {
    repriced: { label: `معاد تسعيرها (${comparison?.repriced ?? 0})`, rows: comparison?.topIncreases?.concat() },
    increases: { label: 'أعلى الزيادات', rows: comparison?.topIncreases },
    decreases: { label: 'أعلى الانخفاضات', rows: comparison?.topDecreases },
    added: { label: `مضافة (${comparison?.added ?? 0})`, rows: comparison?.addedItems, showOld: false },
    removed: { label: `محذوفة (${comparison?.removed ?? 0})`, rows: comparison?.removedItems },
    reclassified: {
      label: `معاد تصنيفها (${comparison?.reclassified ?? 0})`,
      rows: comparison?.reclassifiedItems,
      showCategoryChange: true
    }
  };
  const activeChanges = changesTabs[changesTab] || changesTabs.repriced;

  return (
    <MainCard>
      <ModernPageHeader
        titleKey={`تقرير قائمة الأسعار v${comparison?.versionNo ?? ''} — عقد #${comparison?.contractId ?? ''}`}
        titleIcon={<AssessmentIcon color="primary" />}
        subtitleKey="راجِع الفروقات والملاحظات المالية هنا — بعد النشر تصبح الأسعار نهائية"
        actions={
          <Stack direction="row" spacing={1} alignItems="center">
            <HelpDialog
              title="تقرير قائمة الأسعار — كيف؟"
              points={[
                'هذا التقرير يلخّص كل الفروقات عن القائمة السارية: مضافة، محذوفة، معاد تسعيرها.',
                'الملاحظات المالية: «مانع» يجب تصحيحه، و«تحذير» يُحل أو يُعفى بسبب مكتوب.',
                'زر «اعتماد التقرير»: توقيعك أنك راجعت الفروقات — الخطوة الأولى.',
                'زر «نشر»: يجعل الأسعار سارية على العقد فورًا — الخطوة الثانية.',
                'بعد النشر لا يمكن تعديل الأسعار — أي تصحيح لاحق يمر من «تعديل استثنائي».',
                'القائمة السابقة تبقى محفوظة كاملة للمطالبات القديمة.'
              ]}
            />
            <Button component={RouterLink} to="/classification/imports" startIcon={<ArrowBackIcon />}>
              القوائم
            </Button>
            {isDraft && (
              <Button
                startIcon={<ReplayIcon />}
                disabled={busy}
                onClick={() => act(() => classificationService.revalidateVersion(versionId), 'أعيد الفحص المالي')}
              >
                إعادة الفحص
              </Button>
            )}
            {/* D1: TWO distinct stages — approve the report, then publish */}
            {isDraft && !comparison?.approvedBy && (
              <Tooltip title={gateOpen ? 'وقّع أنك راجعت هذا التقرير' : 'عالج الملاحظات المالية أولًا ثم اعتمد'}>
                <span>
                  <Button
                    variant="contained"
                    color="primary"
                    startIcon={<VerifiedIcon />}
                    disabled={!gateOpen || busy}
                    onClick={() => act(() => classificationService.approveVersion(versionId), 'اعتُمد التقرير — يمكن النشر الآن')}
                  >
                    اعتماد التقرير
                  </Button>
                </span>
              </Tooltip>
            )}
            {isDraft && comparison?.approvedBy && (
              <Chip size="small" color="primary" variant="outlined" label={`اعتُمد بواسطة ${comparison.approvedBy}`} />
            )}
            {isDraft && (
              <Tooltip
                title={
                  !comparison?.approvedBy
                    ? 'النشر يتاح بعد اعتماد التقرير'
                    : gateOpen
                      ? 'جعل الأسعار سارية على العقد'
                      : 'عالج الملاحظات المالية أولًا'
                }
              >
                <span>
                  <Button
                    variant="contained"
                    color="success"
                    startIcon={<PublishIcon />}
                    disabled={!gateOpen || !comparison?.approvedBy || busy}
                    onClick={() => setPublishOpen(true)}
                  >
                    نشر
                  </Button>
                </span>
              </Tooltip>
            )}
          </Stack>
        }
      />

      {loading && <LinearProgress sx={{ mb: 2 }} />}
      {error && (
        <Alert severity="error" sx={{ mb: '1.0rem' }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {comparison && (
        <>
          {/* Gate banner */}
          <Alert
            severity={comparison.versionStatus !== 'DRAFT' ? 'success' : gateOpen ? 'success' : 'warning'}
            icon={gateOpen || comparison.versionStatus !== 'DRAFT' ? <GppGoodIcon /> : <GppBadIcon />}
            sx={{ mb: '1.0rem' }}
          >
            {comparison.versionStatus === 'ACTIVE' &&
              'هذه القائمة منشورة وسارية على العقد — الأسعار نهائية، وأي تصحيح يمر من «تعديل استثنائي».'}
            {comparison.versionStatus === 'SUPERSEDED' && 'قائمة سابقة — محفوظة كاملة للتدقيق وللمطالبات القديمة.'}
            {comparison.versionStatus === 'DRAFT' &&
              (gateOpen
                ? 'الفحص المالي سليم: لا موانع ولا تحذيرات — يمكن الاعتماد ثم النشر.'
                : `الفحص المالي وجد: ${comparison.openBlockers} مانعًا (يُصحح ولا يُعفى) و ${comparison.openWarnings} تحذيرًا (يُحل أو يُعفى بسبب مكتوب).`)}
          </Alert>

          {/* Headline stats */}
          <Grid container spacing={1.5} sx={{ mb: '1.0rem' }}>
            <Grid size="auto">
              <StatCard label="إجمالي الخدمات" value={comparison.totalServices} hint={`السابقة: ${comparison.previousTotalServices}`} />
            </Grid>
            <Grid size="auto">
              <StatCard label="مضافة" value={comparison.added} color="success.main" />
            </Grid>
            <Grid size="auto">
              <StatCard label="محذوفة" value={comparison.removed} color="error.main" />
            </Grid>
            <Grid size="auto">
              <StatCard label="معاد تسعيرها" value={comparison.repriced} color="warning.main" />
            </Grid>
            <Grid size="auto">
              <StatCard label="معاد تصنيفها" value={comparison.reclassified} />
            </Grid>
            <Grid size="auto">
              <StatCard label="بلا تغيير" value={comparison.unchanged} />
            </Grid>
            <Grid size="auto">
              <StatCard
                label="القيمة الإجمالية"
                value={money(comparison.totalValue)}
                hint={
                  comparison.totalValueChangePercent != null
                    ? `${Number(comparison.totalValueChangePercent) > 0 ? '+' : ''}${comparison.totalValueChangePercent}% عن السابقة (${money(comparison.previousTotalValue)})`
                    : `السابقة: ${money(comparison.previousTotalValue)}`
                }
                color={Number(comparison.totalValueChangePercent) > 0 ? 'error.main' : 'success.main'}
              />
            </Grid>
          </Grid>

          {/* Price-change distribution */}
          {Object.values(distribution).some((v) => v > 0) && (
            <Paper variant="outlined" sx={{ p: '1.0rem', mb: '1.0rem' }}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                <PriceChangeIcon fontSize="small" color="action" />
                <Typography variant="subtitle2">توزيع تغيرات الأسعار (الخدمات المشتركة مع النسخة السابقة)</Typography>
              </Stack>
              <Stack direction="row" spacing={2} alignItems="flex-end" sx={{ height: 90, px: 1 }}>
                {Object.entries(distribution).map(([bucket, count]) => (
                  <Stack key={bucket} alignItems="center" spacing={0.5} sx={{ flex: 1 }}>
                    <Typography variant="caption">{count}</Typography>
                    <Box
                      sx={{
                        width: '100%',
                        maxWidth: 48,
                        height: `${(count / maxBucket) * 60 + 2}px`,
                        bgcolor: bucket.includes('+') ? 'error.light' : bucket === '-10..+10%' ? 'grey.400' : 'success.light',
                        borderRadius: 0.5
                      }}
                    />
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>
                      {bucket}
                    </Typography>
                  </Stack>
                ))}
              </Stack>
            </Paper>
          )}

          {/* Financial validation panel (A10) */}
          <Paper variant="outlined" sx={{ p: '1.0rem', mb: '1.0rem' }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              التحقق المالي (A10) — {openFindings.length} مفتوحة · {handledFindings.length} معالجة/معفاة
            </Typography>
            {openFindings.length === 0 && <Alert severity="success">لا توجد ملاحظات مالية مفتوحة.</Alert>}
            <Stack spacing={1}>
              {openFindings.map((f) => (
                <Paper key={f.id} variant="outlined" sx={{ p: '0.6rem 0.8rem' }}>
                  <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip size="small" color={SEVERITY_META[f.severity]?.color} label={SEVERITY_META[f.severity]?.label} />
                    <Chip size="small" variant="outlined" label={FINDING_LABELS[f.findingType] || f.findingType} />
                    <Typography variant="body2" sx={{ flex: 1, minWidth: 200 }}>
                      {f.message}
                    </Typography>
                    {isDraft && (
                      <Stack direction="row" spacing={0.5}>
                        {f.lineRef != null && f.severity === 'BLOCKER' && f.findingType === 'ZERO_OR_NEGATIVE_PRICE' && (
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => {
                              setFindingAction({ finding: f, mode: 'fix' });
                              setFixPrice('');
                              setActionNote('');
                            }}
                          >
                            تصحيح السعر
                          </Button>
                        )}
                        <Button
                          size="small"
                          onClick={() => {
                            setFindingAction({ finding: f, mode: 'resolve' });
                            setActionNote('');
                          }}
                        >
                          معالجة
                        </Button>
                        {f.severity !== 'BLOCKER' && (
                          <Button
                            size="small"
                            color="warning"
                            onClick={() => {
                              setFindingAction({ finding: f, mode: 'waive' });
                              setActionNote('');
                            }}
                          >
                            إعفاء
                          </Button>
                        )}
                      </Stack>
                    )}
                  </Stack>
                </Paper>
              ))}
            </Stack>
            {handledFindings.length > 0 && (
              <>
                <Divider sx={{ my: 1 }} />
                <Typography variant="caption" color="text.secondary">
                  سجل المعالجات:{' '}
                  {handledFindings
                    .map(
                      (f) =>
                        `${FINDING_LABELS[f.findingType] || f.findingType} (${f.status === 'WAIVED' ? 'إعفاء' : 'معالجة'} — ${f.resolvedBy}${f.waiverNote ? ': ' + f.waiverNote : ''})`
                    )
                    .join(' · ')}
                </Typography>
              </>
            )}
          </Paper>

          {/* Changes detail */}
          <Tabs value={changesTab} onChange={(e, v) => setChangesTab(v)} variant="scrollable" sx={{ mb: 1 }}>
            {Object.entries(changesTabs).map(([k, t]) => (
              <Tab key={k} value={k} label={t.label} />
            ))}
          </Tabs>
          <ChangesTable
            rows={activeChanges.rows}
            showOld={activeChanges.showOld !== false}
            showCategoryChange={activeChanges.showCategoryChange}
          />
        </>
      )}

      {/* Finding action dialog */}
      <Dialog open={findingAction != null} onClose={() => !busy && setFindingAction(null)} maxWidth="xs" fullWidth>
        <DialogTitle>
          {findingAction?.mode === 'fix'
            ? 'تصحيح سعر السطر'
            : findingAction?.mode === 'waive'
              ? 'إعفاء التحذير (بسبب موثق)'
              : 'معالجة الملاحظة'}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              {findingAction?.finding?.message}
            </Typography>
            {findingAction?.mode === 'fix' && (
              <TextField label="السعر الصحيح" size="small" type="number" value={fixPrice} onChange={(e) => setFixPrice(e.target.value)} />
            )}
            <TextField
              label={findingAction?.mode === 'waive' ? 'سبب الإعفاء (إلزامي)' : 'ملاحظة'}
              size="small"
              value={actionNote}
              onChange={(e) => setActionNote(e.target.value)}
              multiline
              minRows={2}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFindingAction(null)} disabled={busy}>
            إغلاق
          </Button>
          <Button
            variant="contained"
            disabled={
              busy || (findingAction?.mode === 'waive' && !actionNote.trim()) || (findingAction?.mode === 'fix' && !(Number(fixPrice) > 0))
            }
            onClick={submitFindingAction}
          >
            تنفيذ
          </Button>
        </DialogActions>
      </Dialog>

      {/* Publish confirmation */}
      <Dialog open={publishOpen} onClose={() => !busy && setPublishOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>نشر النسخة v{comparison?.versionNo}</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            <Alert severity="warning">
              النشر نهائي: تسري {comparison?.totalServices} خدمة بأسعارها الجديدة على العقد فورًا، والقائمة السابقة تُحفظ كاملة للمطالبات
              القديمة. بعد النشر <strong>لا يمكن تعديل الأسعار</strong> — أي تصحيح لاحق عبر «تعديل استثنائي».
            </Alert>
            <Typography variant="body2" color="text.secondary">
              يُسجّل النشر باسمك مع الوقت، ويعاد فحص البوابة المالية لحظة النشر.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPublishOpen(false)} disabled={busy}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="success"
            startIcon={<PublishIcon />}
            disabled={busy}
            onClick={() =>
              act(async () => {
                await classificationService.publishVersion(versionId);
                setPublishOpen(false);
              }, 'نُشرت النسخة وأصبحت المرجع المالي النافذ ✔')
            }
          >
            تأكيد النشر
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

export default ClassificationVersion;
