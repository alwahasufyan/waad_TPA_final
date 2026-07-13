import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
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
  Save as SaveIcon,
  Shield as ShieldIcon,
  Storage as StorageIcon,
  WarningAmber as WarningIcon
} from '@mui/icons-material';

import systemBackupsService from 'services/api/systemBackups.service';

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

const BackupSettingsTab = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [creating, setCreating] = useState(false);
  const [validatingId, setValidatingId] = useState(null);
  const [status, setStatus] = useState(null);
  const [history, setHistory] = useState([]);
  const [settings, setSettings] = useState({
    localEnabled: true,
    localDisplayName: 'المسار المحلي الأساسي',
    localPath: '',
    localHostPath: '',
    localContainerPath: '/app/backups/local1',
    localDestinationType: 'مسار محلي على السيرفر',
    retentionDays: 30
  });
  const [request, setRequest] = useState({ type: 'FILES_ONLY', note: '' });
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [statusResponse, settingsResponse, historyResponse] = await Promise.all([
        systemBackupsService.getStatus(),
        systemBackupsService.getSettings(),
        systemBackupsService.list()
      ]);
      setStatus(statusResponse);
      setSettings(settingsResponse || settings);
      setHistory(historyResponse || []);
    } catch (e) {
      setError(e?.userMessage || 'فشل تحميل إعدادات النسخ الاحتياطي');
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
        retentionDays: settings.retentionDays
      });
      setSettings(saved);
      setMessage('تم حفظ إعدادات النسخ الاحتياطي');
      await loadData();
    } catch (e) {
      setError(e?.userMessage || 'فشل حفظ إعدادات النسخ الاحتياطي');
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
      setError(e?.response?.data?.messageAr || e?.userMessage || 'فشل إنشاء النسخة الاحتياطية');
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
      if (result?.valid) {
        setMessage(result.messageAr || 'التحقق ناجح');
      } else {
        setError(result?.messageAr || 'فشل التحقق من النسخة');
      }
    } catch (e) {
      setError(e?.userMessage || 'فشل التحقق من النسخة');
    } finally {
      setValidatingId(null);
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
                <Typography variant="h6">وجهات التخزين</Typography>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={Boolean(settings.localEnabled)}
                          onChange={(e) => setSettings((prev) => ({ ...prev, localEnabled: e.target.checked }))}
                        />
                      }
                      label="تفعيل المسار المحلي"
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                      fullWidth
                      size="small"
                      label="اسم الوجهة"
                      value={settings.localDisplayName || ''}
                      onChange={(e) => setSettings((prev) => ({ ...prev, localDisplayName: e.target.value }))}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                      fullWidth
                      size="small"
                      type="number"
                      label="مدة الاحتفاظ بالأيام"
                      value={settings.retentionDays || 30}
                      onChange={(e) => setSettings((prev) => ({ ...prev, retentionDays: Number(e.target.value) }))}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                      fullWidth
                      size="small"
                      label="نوع الوجهة"
                      value={settings.localDestinationType || 'مسار محلي على السيرفر'}
                      InputProps={{ readOnly: true }}
                    />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <TextField
                      fullWidth
                      size="small"
                      label="مسار السيرفر المعتمد"
                      value={settings.localHostPath || status?.localHostPath || 'مسار مضبوط في Docker/البيئة'}
                      InputProps={{ readOnly: true }}
                      inputProps={{ dir: 'ltr' }}
                      helperText="هذا المسار يضبطه مدير السيرفر في Docker أو ملف البيئة، وليس من جهاز المستخدم."
                    />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <TextField
                      fullWidth
                      size="small"
                      label="مسار Docker الداخلي"
                      value={settings.localContainerPath || status?.localContainerPath || '/app/backups/local1'}
                      InputProps={{ readOnly: true }}
                      inputProps={{ dir: 'ltr' }}
                      helperText="الـ backend يكتب النسخ فقط داخل هذا المسار المعتمد."
                    />
                  </Grid>
                </Grid>
                <Stack direction="row" spacing={1} justifyContent="flex-start">
                  <Button variant="contained" startIcon={saving ? <CircularProgress size={16} /> : <SaveIcon />} onClick={saveSettings} disabled={saving}>
                    حفظ إعدادات النسخ
                  </Button>
                </Stack>
                <Divider />
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Paper variant="outlined" sx={{ p: 2, opacity: 0.65 }}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <StorageIcon color="disabled" />
                        <Typography fontWeight={700}>Offsite Storage</Typography>
                        <Chip label="قريبًا" size="small" />
                      </Stack>
                    </Paper>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Paper variant="outlined" sx={{ p: 2, opacity: 0.65 }}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <StorageIcon color="disabled" />
                        <Typography fontWeight={700}>Acronis / External Backup</Typography>
                        <Chip label="قريبًا" size="small" />
                      </Stack>
                    </Paper>
                  </Grid>
                </Grid>
              </Stack>
            </Card>
          </Grid>
        </Grid>

        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Typography variant="h6">إنشاء نسخة الآن</Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="نوع النسخة"
                  value={request.type}
                  onChange={(e) => setRequest((prev) => ({ ...prev, type: e.target.value }))}
                >
                  {Object.entries(TYPE_LABELS).map(([value, label]) => (
                    <MenuItem key={value} value={value}>{label}</MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, md: 8 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="ملاحظة اختيارية"
                  value={request.note}
                  onChange={(e) => setRequest((prev) => ({ ...prev, note: e.target.value }))}
                />
              </Grid>
            </Grid>
            <Button
              variant="contained"
              color="primary"
              startIcon={creating ? <CircularProgress size={16} color="inherit" /> : <RunIcon />}
              onClick={createBackup}
              disabled={creating || !settings.localEnabled}
              sx={{ alignSelf: 'flex-start' }}
            >
              {creating ? 'جاري إنشاء النسخة...' : 'إنشاء نسخة احتياطية الآن'}
            </Button>
            <Alert severity="info">
              نسخة قاعدة البيانات تستخدم pg_dump داخل بيئة تشغيل الـ backend. لا يتم استخدام docker.sock ولا يتم تنفيذ أي حذف أو استعادة من هذه الشاشة.
            </Alert>
          </Stack>
        </Card>

        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Typography variant="h6">سجل النسخ</Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>التاريخ</TableCell>
                    <TableCell>النوع</TableCell>
                    <TableCell>الحالة</TableCell>
                    <TableCell>الحجم</TableCell>
                    <TableCell>checksum</TableCell>
                    <TableCell>أنشأها</TableCell>
                    <TableCell>المدة</TableCell>
                    <TableCell>تحميل</TableCell>
                    <TableCell>تحقق</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {history.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={9} align="center">لا توجد نسخ احتياطية بعد</TableCell>
                    </TableRow>
                  )}
                  {history.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>{formatDate(row.startedAt)}</TableCell>
                      <TableCell>{TYPE_LABELS[row.type] || row.type}</TableCell>
                      <TableCell><Chip size="small" color={statusColor(row.status)} label={STATUS_LABELS[row.status] || row.status} variant="outlined" /></TableCell>
                      <TableCell>{formatSize(row.fileSize)}</TableCell>
                      <TableCell>
                        <Typography variant="caption" dir="ltr">{row.checksum ? `${row.checksum.slice(0, 12)}…` : '—'}</Typography>
                      </TableCell>
                      <TableCell>{row.createdBy || '—'}</TableCell>
                      <TableCell>{row.durationMs ? `${Math.round(row.durationMs / 1000)} ث` : '—'}</TableCell>
                      <TableCell>
                        <Button
                          size="small"
                          startIcon={<DownloadIcon />}
                          disabled={row.status !== 'SUCCESS'}
                          href={row.status === 'SUCCESS' ? systemBackupsService.downloadUrl(row.id) : undefined}
                        >
                          تحميل
                        </Button>
                      </TableCell>
                      <TableCell>
                        <Button
                          size="small"
                          startIcon={validatingId === row.id ? <CircularProgress size={14} /> : <ValidateIcon />}
                          disabled={row.status !== 'SUCCESS' || validatingId === row.id}
                          onClick={() => validateBackup(row.id)}
                        >
                          تحقق
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Stack>
        </Card>

        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1}>
            <Typography variant="h6">الاستعادة</Typography>
            <Alert severity="warning" icon={<ShieldIcon />}>
              الاستعادة الفعلية على الإنتاج تتم عبر إجراءات السيرفر لتجنب فقدان البيانات. من هذه الشاشة يمكنك التحقق من النسخ وتنزيلها فقط.
            </Alert>
            <Button disabled variant="outlined" sx={{ alignSelf: 'flex-start' }}>
              استعادة تجريبية — قريبًا
            </Button>
          </Stack>
        </Card>

        <Card variant="outlined" sx={{ p: 2, borderColor: 'warning.main', bgcolor: 'warning.lighter' }}>
          <Stack spacing={1}>
            <Typography variant="h6" color="warning.dark">إجراءات خطرة</Typography>
            <Button disabled variant="outlined" color="warning" sx={{ alignSelf: 'flex-start' }}>
              إعادة تهيئة بيانات محددة — قريبًا
            </Button>
            <Typography variant="body2" color="text.secondary">
              لا يمكن تنفيذ أي حذف أو إعادة تهيئة قبل إنشاء نسخة احتياطية ناجحة وإدخال كلمة مرور المدير وعبارة تأكيد.
            </Typography>
          </Stack>
        </Card>
      </Stack>
    </Box>
  );
};

export default BackupSettingsTab;
