import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  MenuItem,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import {
  Download as DownloadIcon,
  FactCheck as ValidateIcon,
  PlayArrow as RunIcon,
  RestartAlt as RehearseIcon,
  Save as SaveIcon,
  Schedule as ScheduleIcon,
  Shield as ShieldIcon,
  DeleteSweep as PurgeIcon,
  WarningAmber as WarningIcon,
  LockOutlined as LockIcon,
  Send as SendIcon,
  Build as MaintenanceIcon
} from '@mui/icons-material';

import systemBackupsService from 'services/api/systemBackups.service';
import dangerZoneService from 'services/api/dangerZone.service';
import maintenanceService from 'services/api/maintenance.service';

const TYPE_LABELS = {
  DATABASE_ONLY: 'نسخة قاعدة البيانات فقط',
  FILES_ONLY: 'نسخة الملفات فقط',
  FULL_SYSTEM: 'نسخة كاملة للنظام'
};

const STATUS_LABELS = {
  RUNNING: 'قيد التنفيذ',
  SUCCESS: 'ناجحة',
  FAILED: 'فشلت'
};

const statusColor = (status) => {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAILED') return 'error';
  return 'warning';
};

const formatSize = (bytes) => {
  if (!bytes) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = Number(bytes);
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
};

const formatDate = (value) => {
  if (!value) return '—';
  return new Date(value).toLocaleString('ar-LY');
};

const pad2 = (n) => String(n).padStart(2, '0');

const errMsg = (e, fallback) => e?.response?.data?.messageAr || e?.response?.data?.message || e?.userMessage || fallback;

const BackupSettingsTab = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [creating, setCreating] = useState(false);
  const [validatingId, setValidatingId] = useState(null);
  const [verifyingId, setVerifyingId] = useState(null);
  const [rehearsingId, setRehearsingId] = useState(null);
  const [rowResult, setRowResult] = useState({});
  const [status, setStatus] = useState(null);
  const [history, setHistory] = useState([]);
  const [settings, setSettings] = useState({
    localEnabled: true,
    localDisplayName: 'المسار المحلي الأساسي',
    localContainerPath: '/app/backups/local1',
    localDestinationType: 'مسار محلي على السيرفر',
    retentionDays: 30,
    autoBackupEnabled: false,
    autoBackupType: 'FULL_SYSTEM',
    autoBackupHour: 2,
    autoBackupMinute: 0,
    lastAutoBackupAt: null,
    lastAutoBackupStatus: null,
    lastAutoBackupMessage: null,
    lastPurgeAt: null,
    lastPurgeStatus: null,
    lastPurgeMessage: null
  });
  const [request, setRequest] = useState({ type: 'FILES_ONLY', note: '' });
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  // Retention purge
  const [purging, setPurging] = useState(false);
  const [purgePreview, setPurgePreview] = useState(null);

  // Danger zone
  const [dz, setDz] = useState(null);
  const [maintenance, setMaintenance] = useState(null);
  const [maintBusy, setMaintBusy] = useState(false);
  const [dzForm, setDzForm] = useState({ password: '', phrase: '', otpCode: '', backupId: '', triedLocally: false });
  const [dzBusy, setDzBusy] = useState(false);
  const [otpBusy, setOtpBusy] = useState({ RESTORE: false, RESET: false });
  const [otpInfo, setOtpInfo] = useState({ RESTORE: null, RESET: null });
  const [resetForm, setResetForm] = useState({
    password: '',
    phrase: '',
    otpCode: '',
    triedLocally: false,
    resetMonitoringLogs: false,
    resetErrorLogs: false,
    resetBackupMetadata: false
  });
  const [resetBusy, setResetBusy] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [statusResponse, settingsResponse, historyResponse, dzResponse, maintResponse] = await Promise.all([
        systemBackupsService.getStatus(),
        systemBackupsService.getSettings(),
        systemBackupsService.list(),
        dangerZoneService.status().catch(() => null),
        maintenanceService.status().catch(() => null)
      ]);
      setStatus(statusResponse);
      setSettings((prev) => ({ ...prev, ...settingsResponse }));
      setHistory(historyResponse || []);
      setDz(dzResponse);
      setMaintenance(maintResponse);
    } catch (e) {
      setError(errMsg(e, 'فشل تحميل إعدادات النسخ الاحتياطي'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const latest = status?.lastBackup;

  const saveSettings = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const saved = await systemBackupsService.updateSettings({
        localEnabled: settings.localEnabled,
        localDisplayName: settings.localDisplayName,
        retentionDays: Number(settings.retentionDays) || 30,
        autoBackupEnabled: settings.autoBackupEnabled,
        autoBackupType: settings.autoBackupType,
        autoBackupHour: Number(settings.autoBackupHour),
        autoBackupMinute: Number(settings.autoBackupMinute)
      });
      setSettings((prev) => ({ ...prev, ...saved }));
      setMessage('تم حفظ إعدادات النسخ الاحتياطي');
      await loadData();
    } catch (e) {
      setError(errMsg(e, 'فشل حفظ إعدادات النسخ الاحتياطي'));
    } finally {
      setSaving(false);
    }
  };

  const createBackup = async () => {
    setCreating(true);
    setError(null);
    setMessage(null);
    try {
      const created = await systemBackupsService.create(request);
      if (created?.status === 'SUCCESS') {
        setMessage('تم إنشاء النسخة الاحتياطية بنجاح');
      } else {
        setError(created?.errorMessage || 'فشل إنشاء النسخة الاحتياطية');
      }
      await loadData();
    } catch (e) {
      setError(errMsg(e, 'فشل إنشاء النسخة الاحتياطية'));
    } finally {
      setCreating(false);
    }
  };

  const validateBackup = async (id) => {
    setValidatingId(id);
    setError(null);
    setMessage(null);
    try {
      const result = await systemBackupsService.validate(id);
      setRowResult((p) => ({ ...p, [id]: { severity: result?.valid ? 'success' : 'error', text: result?.messageAr } }));
    } catch (e) {
      setError(errMsg(e, 'فشل التحقق من النسخة'));
    } finally {
      setValidatingId(null);
    }
  };

  const verifyRestore = async (id) => {
    setVerifyingId(id);
    setError(null);
    setMessage(null);
    try {
      const result = await systemBackupsService.verifyRestore(id);
      setRowResult((p) => ({ ...p, [id]: { severity: result?.valid ? 'success' : 'warning', text: result?.messageAr } }));
    } catch (e) {
      setError(errMsg(e, 'فشل التحقق من صلاحية الاستعادة'));
    } finally {
      setVerifyingId(null);
    }
  };

  const rehearse = async (id) => {
    setRehearsingId(id);
    setError(null);
    setMessage(null);
    try {
      const result = await systemBackupsService.rehearse(id);
      const text = `${result?.messageAr || ''}${result?.durationMs ? ` (${Math.round(result.durationMs)} ملّي ثانية)` : ''}`;
      setRowResult((p) => ({ ...p, [id]: { severity: result?.success ? 'success' : 'error', text } }));
    } catch (e) {
      setError(errMsg(e, 'فشل اختبار الاستعادة'));
    } finally {
      setRehearsingId(null);
    }
  };

  const runPurge = async (dryRun) => {
    setPurging(true);
    setError(null);
    setMessage(null);
    try {
      const result = await systemBackupsService.purge(dryRun);
      setPurgePreview(result);
      setMessage(result?.messageAr || (dryRun ? 'تمت المعاينة' : 'تم التنظيف'));
      if (!dryRun) {
        await loadData();
      }
    } catch (e) {
      setError(errMsg(e, 'فشل تنفيذ تنظيف النسخ القديمة'));
    } finally {
      setPurging(false);
    }
  };

  const toggleMaintenance = async (enabled) => {
    setMaintBusy(true);
    setError(null);
    setMessage(null);
    try {
      const saved = await maintenanceService.set({ enabled, reason: enabled ? 'إجراء خطر على الإنتاج' : null });
      setMaintenance(saved);
      setMessage(enabled ? 'تم تفعيل وضع الصيانة' : 'تم إيقاف وضع الصيانة');
      await loadData();
    } catch (e) {
      setError(errMsg(e, 'فشل تغيير وضع الصيانة'));
    } finally {
      setMaintBusy(false);
    }
  };

  const sendOtp = async (operation) => {
    setOtpBusy((p) => ({ ...p, [operation]: true }));
    setError(null);
    setMessage(null);
    try {
      const result = await dangerZoneService.sendOtp(operation);
      setOtpInfo((p) => ({ ...p, [operation]: result }));
      setMessage(result?.messageAr || 'تم إرسال كود التأكيد إلى Telegram');
    } catch (e) {
      setError(errMsg(e, 'تعذّر إرسال كود التأكيد'));
    } finally {
      setOtpBusy((p) => ({ ...p, [operation]: false }));
    }
  };

  const runDevRestore = async () => {
    if (!dzForm.backupId) {
      setError('اختر رقم النسخة المراد استعادتها.');
      return;
    }
    if (dz?.otpRequired && !dzForm.triedLocally) {
      setError('يجب تأكيد أنك جرّبت النسخة محليًا قبل تنفيذها على الإنتاج.');
      return;
    }
    setDzBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await dangerZoneService.restore(dzForm.backupId, {
        password: dzForm.password,
        confirmationPhrase: dzForm.phrase,
        otpCode: dzForm.otpCode
      });
      setMessage(result?.messageAr || 'تمت الاستعادة');
      setDzForm({ password: '', phrase: '', otpCode: '', backupId: '', triedLocally: false });
      setOtpInfo((p) => ({ ...p, RESTORE: null }));
      await loadData();
    } catch (e) {
      setError(errMsg(e, 'فشلت الاستعادة'));
    } finally {
      setDzBusy(false);
    }
  };

  const runReset = async () => {
    if (dz?.otpRequired && !resetForm.triedLocally) {
      setError('يجب تأكيد أنك جرّبت العملية محليًا قبل تنفيذها على الإنتاج.');
      return;
    }
    setResetBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await dangerZoneService.reset({
        password: resetForm.password,
        confirmationPhrase: resetForm.phrase,
        otpCode: resetForm.otpCode,
        resetMonitoringLogs: resetForm.resetMonitoringLogs,
        resetErrorLogs: resetForm.resetErrorLogs,
        resetBackupMetadata: resetForm.resetBackupMetadata
      });
      setMessage(result?.messageAr || 'تمت إعادة التهيئة المحدودة');
      setResetForm({ password: '', phrase: '', otpCode: '', triedLocally: false, resetMonitoringLogs: false, resetErrorLogs: false, resetBackupMetadata: false });
      setOtpInfo((p) => ({ ...p, RESET: null }));
      await loadData();
    } catch (e) {
      setError(errMsg(e, 'فشلت إعادة التهيئة'));
    } finally {
      setResetBusy(false);
    }
  };

  const helperText = useMemo(() => (
    'يتم حفظ النسخ الاحتياطية في مسارات معتمدة على السيرفر أو داخل Docker. لا يمكن حفظ النسخ تلقائيًا على جهاز المستخدم من المتصفح. إذا أردت نسخة على جهازك استخدم زر التحميل من سجل النسخ.'
  ), []);

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={320}>
        <CircularProgress />
      </Box>
    );
  }

  const dangerUnlocked = Boolean(dz?.enabled);

  // Per-condition lock breakdown so the admin sees exactly what is missing (mostly relevant in production).
  const condChip = (label, ok) => (
    <Chip size="small" variant="outlined" color={ok ? 'success' : 'default'}
      icon={ok ? undefined : <LockIcon />} label={label} />
  );
  const lockConditionChips = dz?.productionLike ? (
    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
      {condChip('علم الإنتاج', dz?.productionFlagEnabled)}
      {condChip('وضع الصيانة', dz?.maintenanceMode)}
      {condChip('Telegram مهيأ', dz?.telegramConfigured)}
      {condChip('كود Telegram مطلوب', dz?.otpRequired)}
    </Stack>
  ) : null;

  return (
    <Box sx={{ p: '1rem', overflow: 'auto', height: '100%' }} dir="rtl">
      <Stack spacing={2}>
        {message && <Alert severity="success" onClose={() => setMessage(null)}>{message}</Alert>}
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card variant="outlined" sx={{ p: 2, height: '100%' }}>
              <Stack spacing={1}>
                <Typography variant="h6">حالة النسخ الاحتياطي</Typography>
                <Divider />
                <Typography variant="body2">آخر نسخة احتياطية: {formatDate(latest?.completedAt || latest?.startedAt)}</Typography>
                <Typography variant="body2">
                  آخر نتيجة:{' '}
                  <Chip size="small" label={STATUS_LABELS[latest?.status] || 'لا توجد'} color={statusColor(latest?.status)} variant="outlined" />
                </Typography>
                <Typography variant="body2">عدد النسخ: {status?.backupCount ?? 0}</Typography>
                <Typography variant="body2">حجم آخر نسخة: {formatSize(status?.lastBackupSize)}</Typography>
                <Typography variant="body2">المسار المحلي المعتمد: <span dir="ltr">{status?.localContainerPath || settings.localContainerPath || status?.localPath || '—'}</span></Typography>
                <Typography variant="body2">
                  حالة المسار:{' '}
                  <Chip
                    size="small"
                    color={status?.localPathWritable ? 'success' : 'warning'}
                    label={status?.localPathStatus || 'غير معروف'}
                    variant="outlined"
                  />
                </Typography>
                <Typography variant="body2">المساحة المتاحة: {formatSize(status?.localUsableSpace)}</Typography>
                {(!status?.localPathWritable) && (
                  <Alert severity="warning" icon={<WarningIcon />}>
                    المسار المحلي المعتمد غير قابل للكتابة. راجع إعدادات Docker أو صلاحيات السيرفر.
                  </Alert>
                )}
                <Alert severity="info">{helperText}</Alert>
              </Stack>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, md: 8 }}>
            <Card variant="outlined" sx={{ p: 2 }}>
              <Stack spacing={2}>
                <Typography variant="h6">وجهة التخزين والاحتفاظ</Typography>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControlLabel
                      control={<Switch checked={Boolean(settings.localEnabled)} onChange={(e) => setSettings((p) => ({ ...p, localEnabled: e.target.checked }))} />}
                      label="تفعيل المسار المحلي"
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField fullWidth size="small" label="اسم الوجهة" value={settings.localDisplayName || ''}
                      onChange={(e) => setSettings((p) => ({ ...p, localDisplayName: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField fullWidth size="small" type="number" label="مدة الاحتفاظ بالأيام"
                      value={settings.retentionDays || 30}
                      onChange={(e) => setSettings((p) => ({ ...p, retentionDays: Number(e.target.value) }))} />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <TextField fullWidth size="small" label="مسار Docker الداخلي المعتمد"
                      value={settings.localContainerPath || status?.localContainerPath || '/app/backups/local1'}
                      InputProps={{ readOnly: true }} inputProps={{ dir: 'ltr' }}
                      helperText="الـ backend يكتب النسخ فقط داخل هذا المسار المعتمد الذي يضبطه مدير السيرفر." />
                  </Grid>
                </Grid>
                <Stack direction="row" spacing={1}>
                  <Button variant="contained" startIcon={saving ? <CircularProgress size={16} /> : <SaveIcon />} onClick={saveSettings} disabled={saving}>
                    حفظ إعدادات النسخ
                  </Button>
                </Stack>
              </Stack>
            </Card>
          </Grid>
        </Grid>

        {/* ===================== Scheduler ===================== */}
        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center">
              <ScheduleIcon color="primary" />
              <Typography variant="h6">الجدولة التلقائية</Typography>
              <Chip size="small" color={settings.autoBackupEnabled ? 'success' : 'default'} variant="outlined"
                label={settings.autoBackupEnabled ? 'مفعّلة' : 'متوقفة'} />
            </Stack>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 3 }}>
                <FormControlLabel
                  control={<Switch checked={Boolean(settings.autoBackupEnabled)} onChange={(e) => setSettings((p) => ({ ...p, autoBackupEnabled: e.target.checked }))} />}
                  label="تفعيل النسخ التلقائي"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField select fullWidth size="small" label="نوع النسخة التلقائية" value={settings.autoBackupType}
                  onChange={(e) => setSettings((p) => ({ ...p, autoBackupType: e.target.value }))}>
                  {Object.entries(TYPE_LABELS).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField fullWidth size="small" type="number" label="الساعة (0-23)" value={settings.autoBackupHour}
                  inputProps={{ min: 0, max: 23 }}
                  onChange={(e) => setSettings((p) => ({ ...p, autoBackupHour: e.target.value }))} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField fullWidth size="small" type="number" label="الدقيقة (0-59)" value={settings.autoBackupMinute}
                  inputProps={{ min: 0, max: 59 }}
                  onChange={(e) => setSettings((p) => ({ ...p, autoBackupMinute: e.target.value }))} />
              </Grid>
            </Grid>
            <Typography variant="body2" color="text.secondary">
              موعد التشغيل اليومي: {pad2(settings.autoBackupHour)}:{pad2(settings.autoBackupMinute)} بتوقيت السيرفر.
              آخر تشغيل تلقائي: {formatDate(settings.lastAutoBackupAt)} —{' '}
              <Chip size="small" variant="outlined" color={statusColor(settings.lastAutoBackupStatus)} label={STATUS_LABELS[settings.lastAutoBackupStatus] || 'لا يوجد'} />
            </Typography>
            {settings.lastAutoBackupMessage && (
              <Typography variant="body2" color="text.secondary">آخر رسالة: {settings.lastAutoBackupMessage}</Typography>
            )}
            <Alert severity="info">
              يعمل النسخ التلقائي مرة واحدة يوميًا في الموعد المحدد، ولا يبدأ نسختين في الوقت نفسه، ويُنفّذ تنظيف النسخ القديمة بعد نجاحه.
              عند الفشل يُرسل تنبيه Telegram إذا كان مفعّلًا، دون إيقاف التطبيق. اضغط «حفظ إعدادات النسخ» أعلاه لتطبيق الجدولة.
            </Alert>
          </Stack>
        </Card>

        {/* ===================== Retention purge ===================== */}
        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center">
              <PurgeIcon color="primary" />
              <Typography variant="h6">تنظيف النسخ القديمة (Retention)</Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary">
              يحذف النسخ الأقدم من {settings.retentionDays} يومًا داخل مسار النسخ المعتمد فقط، ولا يحذف آخر نسخة ناجحة أبدًا.
              آخر تنظيف: {formatDate(settings.lastPurgeAt)}{settings.lastPurgeStatus ? ` — ${settings.lastPurgeStatus}` : ''}.
            </Typography>
            <Stack direction="row" spacing={1}>
              <Button variant="outlined" startIcon={purging ? <CircularProgress size={16} /> : <ValidateIcon />} onClick={() => runPurge(true)} disabled={purging}>
                معاينة (بدون حذف)
              </Button>
              <Button variant="contained" color="warning" startIcon={purging ? <CircularProgress size={16} /> : <PurgeIcon />} onClick={() => runPurge(false)} disabled={purging}>
                تنفيذ التنظيف الآن
              </Button>
            </Stack>
            {purgePreview && (
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>رقم النسخة</TableCell>
                      <TableCell>الاسم</TableCell>
                      <TableCell>التاريخ</TableCell>
                      <TableCell>الحجم</TableCell>
                      <TableCell>الإجراء</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(purgePreview.items || []).length === 0 && (
                      <TableRow><TableCell colSpan={5} align="center">لا توجد نسخ مرشحة للحذف</TableCell></TableRow>
                    )}
                    {(purgePreview.items || []).map((it) => (
                      <TableRow key={it.backupId}>
                        <TableCell>{it.backupId}</TableCell>
                        <TableCell><Typography variant="caption" dir="ltr">{it.fileName || '—'}</Typography></TableCell>
                        <TableCell>{formatDate(it.startedAt)}</TableCell>
                        <TableCell>{formatSize(it.fileSize)}</TableCell>
                        <TableCell>
                          <Chip size="small" variant="outlined" color={it.deleted ? 'error' : 'default'} label={it.reason} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Stack>
        </Card>

        {/* ===================== Create now ===================== */}
        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Typography variant="h6">إنشاء نسخة الآن</Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField select fullWidth size="small" label="نوع النسخة" value={request.type}
                  onChange={(e) => setRequest((prev) => ({ ...prev, type: e.target.value }))}>
                  {Object.entries(TYPE_LABELS).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, md: 8 }}>
                <TextField fullWidth size="small" label="ملاحظة اختيارية" value={request.note}
                  onChange={(e) => setRequest((prev) => ({ ...prev, note: e.target.value }))} />
              </Grid>
            </Grid>
            <Button variant="contained" color="primary"
              startIcon={creating ? <CircularProgress size={16} color="inherit" /> : <RunIcon />}
              onClick={createBackup} disabled={creating || !settings.localEnabled} sx={{ alignSelf: 'flex-start' }}>
              {creating ? 'جاري إنشاء النسخة...' : 'إنشاء نسخة احتياطية الآن'}
            </Button>
            <Alert severity="info">
              نسخة قاعدة البيانات تستخدم pg_dump داخل بيئة تشغيل الـ backend. لا يتم استخدام docker.sock ولا يتم تنفيذ أي حذف أو استعادة من هذه الشاشة.
            </Alert>
          </Stack>
        </Card>

        {/* ===================== History ===================== */}
        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Typography variant="h6">سجل النسخ</Typography>
            <TableContainer component={Paper} variant="outlined" sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>التاريخ</TableCell>
                    <TableCell>النوع</TableCell>
                    <TableCell>الحالة</TableCell>
                    <TableCell>الحجم</TableCell>
                    <TableCell>أنشأها</TableCell>
                    <TableCell>تحميل</TableCell>
                    <TableCell>تحقق</TableCell>
                    <TableCell>فحص الاستعادة</TableCell>
                    <TableCell>اختبار استعادة</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {history.length === 0 && (
                    <TableRow><TableCell colSpan={9} align="center">لا توجد نسخ احتياطية بعد</TableCell></TableRow>
                  )}
                  {history.map((row) => (
                    <Fragment key={row.id}>
                      <TableRow>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(row.startedAt)}</TableCell>
                        <TableCell>{TYPE_LABELS[row.type] || row.type}</TableCell>
                        <TableCell><Chip size="small" color={statusColor(row.status)} label={STATUS_LABELS[row.status] || row.status} variant="outlined" /></TableCell>
                        <TableCell>{formatSize(row.fileSize)}</TableCell>
                        <TableCell>{row.createdBy || '—'}</TableCell>
                        <TableCell>
                          <Button size="small" startIcon={<DownloadIcon />} disabled={row.status !== 'SUCCESS'}
                            href={row.status === 'SUCCESS' ? systemBackupsService.downloadUrl(row.id) : undefined}>تحميل</Button>
                        </TableCell>
                        <TableCell>
                          <Button size="small" startIcon={validatingId === row.id ? <CircularProgress size={14} /> : <ValidateIcon />}
                            disabled={row.status !== 'SUCCESS' || validatingId === row.id} onClick={() => validateBackup(row.id)}>checksum</Button>
                        </TableCell>
                        <TableCell>
                          <Button size="small" startIcon={verifyingId === row.id ? <CircularProgress size={14} /> : <ShieldIcon />}
                            disabled={row.status !== 'SUCCESS' || verifyingId === row.id} onClick={() => verifyRestore(row.id)}>فحص</Button>
                        </TableCell>
                        <TableCell>
                          <Button size="small" startIcon={rehearsingId === row.id ? <CircularProgress size={14} /> : <RehearseIcon />}
                            disabled={row.status !== 'SUCCESS' || rehearsingId === row.id} onClick={() => rehearse(row.id)}>تجربة</Button>
                        </TableCell>
                      </TableRow>
                      {rowResult[row.id] && (
                        <TableRow>
                          <TableCell colSpan={9} sx={{ py: 0.5 }}>
                            <Alert severity={rowResult[row.id].severity} onClose={() => setRowResult((p) => { const n = { ...p }; delete n[row.id]; return n; })}>
                              {rowResult[row.id].text}
                            </Alert>
                          </TableCell>
                        </TableRow>
                      )}
                    </Fragment>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <Typography variant="body2" color="text.secondary">
              «فحص الاستعادة» يتحقق من الملف وchecksum والبيانات الوصفية وقابلية قراءة قاعدة البيانات. «اختبار استعادة» يشغّل
              <span dir="ltr"> pg_restore --list </span>على أرشيف القاعدة دون المساس بقاعدة التشغيل.
            </Typography>
          </Stack>
        </Card>

        {/* ===================== Maintenance mode ===================== */}
        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1.5}>
            <Stack direction="row" spacing={1} alignItems="center">
              <MaintenanceIcon color={maintenance?.enabled ? 'warning' : 'action'} />
              <Typography variant="h6">وضع الصيانة</Typography>
              <Chip size="small" variant="outlined" color={maintenance?.enabled ? 'warning' : 'default'}
                label={maintenance?.enabled ? 'مفعّل' : 'متوقف'} />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              أثناء وضع الصيانة تُمنع طلبات التعديل من غير مدير النظام. وهو شرط إلزامي قبل تنفيذ إجراءات الخطر على الإنتاج.
              {maintenance?.updatedAt ? ` آخر تغيير: ${formatDate(maintenance.updatedAt)}.` : ''}
            </Typography>
            <FormControlLabel
              control={<Switch checked={Boolean(maintenance?.enabled)} disabled={maintBusy} onChange={(e) => toggleMaintenance(e.target.checked)} />}
              label={maintenance?.enabled ? 'إيقاف وضع الصيانة' : 'تفعيل وضع الصيانة'}
            />
          </Stack>
        </Card>

        {/* ===================== Restore ===================== */}
        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1.5}>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
              <ShieldIcon color={dangerUnlocked ? 'warning' : 'action'} />
              <Typography variant="h6">الاستعادة</Typography>
              <Chip size="small" variant="outlined" color={dangerUnlocked ? 'warning' : 'default'}
                icon={dangerUnlocked ? undefined : <LockIcon />}
                label={dangerUnlocked ? (dz?.productionLike ? 'متاحة (إنتاج مؤمّن)' : 'متاحة (تطوير)') : 'مقفلة'} />
            </Stack>
            {lockConditionChips}
            {!dangerUnlocked && (
              <>
                <Alert severity="info">
                  {dz?.reasonAr || 'الاستعادة الفعلية مقفلة حاليًا. يمكنك التحقق من النسخ واختبار قابليتها للاستعادة وتنزيلها.'}
                </Alert>
                {dz?.serverRunbookAr && (
                  <Box component="pre" sx={{ p: 1.5, bgcolor: 'action.hover', borderRadius: 1, fontSize: '0.78rem', whiteSpace: 'pre-wrap', direction: 'rtl' }}>
                    {dz.serverRunbookAr}
                  </Box>
                )}
              </>
            )}
            {dangerUnlocked && (
              <>
                <Alert severity="warning" icon={<WarningIcon />}>
                  استعادة فعلية تكتب فوق قاعدة البيانات الحالية. يتم إنشاء نسخة أمان إجبارية تلقائيًا قبل التنفيذ.
                  البيئة: <span dir="ltr">{dz?.environment}</span>.
                </Alert>
                {dz?.otpRequired && (
                  <Alert severity="warning">يجب تجربة النسخة محليًا قبل تنفيذها على الإنتاج.</Alert>
                )}
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField select fullWidth size="small" label="رقم النسخة" value={dzForm.backupId}
                      onChange={(e) => setDzForm((p) => ({ ...p, backupId: e.target.value }))}>
                      {history.filter((h) => h.status === 'SUCCESS').map((h) => (
                        <MenuItem key={h.id} value={h.id}>#{h.id} — {formatDate(h.startedAt)}</MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField fullWidth size="small" type="password" label="كلمة مرور المدير" value={dzForm.password}
                      onChange={(e) => setDzForm((p) => ({ ...p, password: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField fullWidth size="small" label={`عبارة التأكيد: ${dz?.restoreConfirmationPhrase || ''}`}
                      value={dzForm.phrase} inputProps={{ dir: 'ltr' }}
                      onChange={(e) => setDzForm((p) => ({ ...p, phrase: e.target.value }))} />
                  </Grid>
                </Grid>
                {dz?.otpRequired && (
                  <Grid container spacing={2} alignItems="center">
                    <Grid size={{ xs: 12, md: 4 }}>
                      <Button fullWidth variant="outlined" startIcon={otpBusy.RESTORE ? <CircularProgress size={16} /> : <SendIcon />}
                        onClick={() => sendOtp('RESTORE')} disabled={otpBusy.RESTORE}>
                        إرسال كود إلى Telegram
                      </Button>
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField fullWidth size="small" label="كود Telegram" value={dzForm.otpCode} inputProps={{ dir: 'ltr', maxLength: 6 }}
                        onChange={(e) => setDzForm((p) => ({ ...p, otpCode: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      {otpInfo.RESTORE && (
                        <Typography variant="caption" color="text.secondary">تم إرسال الكود. صالح حتى {formatDate(otpInfo.RESTORE.expiresAt)}.</Typography>
                      )}
                    </Grid>
                  </Grid>
                )}
                {dz?.otpRequired && (
                  <FormControlLabel
                    control={<Checkbox checked={dzForm.triedLocally} onChange={(e) => setDzForm((p) => ({ ...p, triedLocally: e.target.checked }))} />}
                    label="أؤكد أنني جرّبت هذه النسخة محليًا أولًا" />
                )}
                <Button variant="contained" color="error" startIcon={dzBusy ? <CircularProgress size={16} color="inherit" /> : <RehearseIcon />}
                  onClick={runDevRestore} disabled={dzBusy || (dz?.otpRequired && !dzForm.triedLocally)} sx={{ alignSelf: 'flex-start' }}>
                  {dzBusy ? 'جاري الاستعادة...' : 'تنفيذ الاستعادة الآن'}
                </Button>
              </>
            )}
          </Stack>
        </Card>

        {/* ===================== Danger zone (reset) ===================== */}
        <Card variant="outlined" sx={{ p: 2, borderColor: 'warning.main' }}>
          <Stack spacing={1.5}>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
              <WarningIcon color="warning" />
              <Typography variant="h6" color="warning.dark">منطقة الخطر — إعادة التهيئة</Typography>
              <Chip size="small" variant="outlined" color={dangerUnlocked ? 'warning' : 'default'}
                icon={dangerUnlocked ? undefined : <LockIcon />}
                label={dangerUnlocked ? (dz?.productionLike ? 'مفتوحة (إنتاج مؤمّن)' : 'مفتوحة (تطوير)') : 'مقفلة'} />
            </Stack>
            {lockConditionChips}
            {!dangerUnlocked ? (
              <Alert severity="info" icon={<LockIcon />}>
                {dz?.reasonAr || 'الإجراءات الخطرة مقفلة. تُفعّل في بيئة التطوير عبر WAAD_DANGER_ZONE_ENABLED=true، وفي الإنتاج عبر حماية مشددة.'}
              </Alert>
            ) : (
              <>
                <Alert severity="warning">
                  إعادة تهيئة محدودة: لا تحذف المستخدمين أو المدير أو الهجرات، ولا تحذف ملفات النسخ. يتم إنشاء نسخة أمان إجبارية قبل التنفيذ.
                </Alert>
                {dz?.otpRequired && (
                  <Alert severity="warning">يجب تجربة العملية محليًا قبل تنفيذها على الإنتاج.</Alert>
                )}
                <Stack>
                  <FormControlLabel control={<Checkbox checked={resetForm.resetMonitoringLogs} onChange={(e) => setResetForm((p) => ({ ...p, resetMonitoringLogs: e.target.checked }))} />} label="تصفير سجلات المراقبة وحالات التنبيه" />
                  <FormControlLabel control={<Checkbox checked={resetForm.resetErrorLogs} onChange={(e) => setResetForm((p) => ({ ...p, resetErrorLogs: e.target.checked }))} />} label="تصفير سجل أخطاء النظام" />
                  <FormControlLabel control={<Checkbox checked={resetForm.resetBackupMetadata} onChange={(e) => setResetForm((p) => ({ ...p, resetBackupMetadata: e.target.checked }))} />} label="تصفير بيانات سجل النسخ (بدون حذف الملفات)" />
                </Stack>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 5 }}>
                    <TextField fullWidth size="small" type="password" label="كلمة مرور المدير" value={resetForm.password}
                      onChange={(e) => setResetForm((p) => ({ ...p, password: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 7 }}>
                    <TextField fullWidth size="small" label={`عبارة التأكيد: ${dz?.resetConfirmationPhrase || ''}`}
                      value={resetForm.phrase} inputProps={{ dir: 'ltr' }}
                      onChange={(e) => setResetForm((p) => ({ ...p, phrase: e.target.value }))} />
                  </Grid>
                </Grid>
                {dz?.otpRequired && (
                  <Grid container spacing={2} alignItems="center">
                    <Grid size={{ xs: 12, md: 4 }}>
                      <Button fullWidth variant="outlined" startIcon={otpBusy.RESET ? <CircularProgress size={16} /> : <SendIcon />}
                        onClick={() => sendOtp('RESET')} disabled={otpBusy.RESET}>
                        إرسال كود إلى Telegram
                      </Button>
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField fullWidth size="small" label="كود Telegram" value={resetForm.otpCode} inputProps={{ dir: 'ltr', maxLength: 6 }}
                        onChange={(e) => setResetForm((p) => ({ ...p, otpCode: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      {otpInfo.RESET && (
                        <Typography variant="caption" color="text.secondary">تم إرسال الكود. صالح حتى {formatDate(otpInfo.RESET.expiresAt)}.</Typography>
                      )}
                    </Grid>
                  </Grid>
                )}
                {dz?.otpRequired && (
                  <FormControlLabel
                    control={<Checkbox checked={resetForm.triedLocally} onChange={(e) => setResetForm((p) => ({ ...p, triedLocally: e.target.checked }))} />}
                    label="أؤكد أنني جرّبت هذه العملية محليًا أولًا" />
                )}
                <Button variant="contained" color="error" startIcon={resetBusy ? <CircularProgress size={16} color="inherit" /> : <WarningIcon />}
                  onClick={runReset} disabled={resetBusy || (dz?.otpRequired && !resetForm.triedLocally)} sx={{ alignSelf: 'flex-start' }}>
                  {resetBusy ? 'جاري التنفيذ...' : 'تنفيذ إعادة التهيئة المحدودة'}
                </Button>
              </>
            )}
          </Stack>
        </Card>
      </Stack>
    </Box>
  );
};

export default BackupSettingsTab;
