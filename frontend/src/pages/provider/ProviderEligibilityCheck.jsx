import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  Typography,
  Stack,
  Paper,
  Alert,
  Chip,
  IconButton,
  CircularProgress,
  InputAdornment,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  LinearProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  useTheme
} from '@mui/material';
import Grid from '@mui/material/Grid';
import Autocomplete from '@mui/material/Autocomplete';
import Link from '@mui/material/Link';
import { Html5Qrcode } from 'html5-qrcode';
import { searchMembersByName } from 'services/api/members.service';

// Icons
import QrCodeScannerIcon from '@mui/icons-material/QrCodeScanner';
import PersonIcon from '@mui/icons-material/Person';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import RefreshIcon from '@mui/icons-material/Refresh';
import CloseIcon from '@mui/icons-material/Close';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import FamilyRestroomIcon from '@mui/icons-material/FamilyRestroom';
import AssignmentIcon from '@mui/icons-material/Assignment';
import HistoryIcon from '@mui/icons-material/History';
import TipsAndUpdatesIcon from '@mui/icons-material/TipsAndUpdates';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import BadgeIcon from '@mui/icons-material/Badge';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';

// Components
import { ModernPageHeader, MemberAvatar } from '../../components/tba';

// Services
import providerApi from '../../services/providerService';

// Visit Types
const VISIT_TYPE_OPTIONS = [
  { value: 'EMERGENCY', label: 'عمليات' },
  { value: 'OUTPATIENT', label: 'عيادات خارجية' },
  { value: 'INPATIENT', label: 'إيواء' },
  { value: 'ROUTINE', label: 'تحاليل طبية' },
  { value: 'FOLLOW_UP', label: 'اسنان وقائي' },
  { value: 'PREVENTIVE', label: 'اسنان تجميلي' },
  { value: 'SPECIALIZED', label: 'اشعة' },
  { value: 'HOME_CARE', label: 'علاج طبيعي' }
];

const STAT_CHIP_TONES = {
  neutral: { bgcolor: 'grey.100', color: 'text.primary' },
  warn: { bgcolor: 'warning.lighter', color: 'warning.dark' },
  ok: { bgcolor: 'success.lighter', color: 'success.dark' }
};

function SummaryStatChip({ label, value, tone = 'neutral' }) {
  const toneSx = STAT_CHIP_TONES[tone] || STAT_CHIP_TONES.neutral;
  return (
    <Box sx={{ ...toneSx, borderRadius: '0.5rem', px: '0.875rem', py: '0.5rem', minWidth: '7.5rem' }}>
      <Typography variant="caption" sx={{ opacity: 0.85, display: 'block' }}>
        {label}
      </Typography>
      <Typography variant="subtitle2" fontWeight={700}>
        {value}
      </Typography>
    </Box>
  );
}

export default function ProviderEligibilityCheck() {
  const navigate = useNavigate();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  // ========================================
  // STATE
  // ========================================

  const [searchValue, setSearchValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const [selectedMember, setSelectedMember] = useState(null);
  const [selectedVisitType, setSelectedVisitType] = useState('');
  const [registeringVisit, setRegisteringVisit] = useState(false);
  const [checkHistory, setCheckHistory] = useState([]);

  // Scanner
  const [scannerOpen, setScannerOpen] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [cameraError, setCameraError] = useState(null);
  const html5QrCodeRef = useRef(null);
  const scannerInputRef = useRef(null);
  const autoCheckTimerRef = useRef(null);
  const lastAutoSubmittedRef = useRef('');

  // Name search (autocomplete over registered beneficiaries)
  const [nameOptions, setNameOptions] = useState([]);
  const [nameLoading, setNameLoading] = useState(false);
  const nameSearchTimerRef = useRef(null);

  const handleNameSearch = useCallback((query) => {
    if (nameSearchTimerRef.current) clearTimeout(nameSearchTimerRef.current);
    const q = (query || '').trim();
    if (q.length < 3) {
      setNameOptions([]);
      return;
    }
    nameSearchTimerRef.current = setTimeout(async () => {
      setNameLoading(true);
      try {
        const res = await searchMembersByName(q);
        const list = Array.isArray(res) ? res : res?.data || [];
        setNameOptions(list);
      } catch {
        setNameOptions([]);
      } finally {
        setNameLoading(false);
      }
    }, 300);
  }, []);

  // ========================================
  // HELPERS
  // ========================================

  const formatCurrency = (amount) => {
    if (amount === null || amount === undefined) return '—';
    // Use Western Arabic numerals with English locale, but add custom Libyan Dinar suffix
    return (
      Number(amount).toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }) + ' د.ل'
    );
  };

  const getUsageColor = (percentage) => {
    if (percentage >= 90) return 'error';
    if (percentage >= 70) return 'warning';
    return 'success';
  };

  const todayDateKey = new Date().toLocaleDateString('en-CA');

  const todayChecks = useMemo(() => {
    return checkHistory.filter((item) => item.dateKey === todayDateKey);
  }, [checkHistory, todayDateKey]);

  const todayAcceptedCount = useMemo(() => todayChecks.filter((item) => item.eligible).length, [todayChecks]);
  const todayRejectedCount = useMemo(() => todayChecks.filter((item) => !item.eligible).length, [todayChecks]);
  const recentSuccessfulChecks = useMemo(() => checkHistory.filter((item) => item.eligible).slice(0, 5), [checkHistory]);

  // ========================================
  // ELIGIBILITY CHECK API
  // ========================================

  const checkEligibility = useCallback(async (barcodeOrCardNumber) => {
    // Validate input
    const trimmedValue = barcodeOrCardNumber?.trim();

    if (!trimmedValue) {
      setError('يرجى إدخال رقم البطاقة أو الباركود أو رقم العضو');
      return;
    }

    // Only reject single "0", allow other numbers (including leading zeros like "000001")
    if (trimmedValue === '0') {
      setError('يرجى إدخال رقم صحيح (رقم البطاقة أو الباركود أو رقم العضو)');
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);
    setSelectedMember(null);
    setSelectedVisitType('');

    try {
      // Send as barcode (API accepts card number, barcode, or member ID in this field)
      const response = await providerApi.checkEligibility({ barcode: trimmedValue });

      // API returns the DTO directly (ProviderEligibilityResponse)
      if (response && (response.eligible !== undefined || response.statusCode)) {
        setResult(response);
        const firstMember = response.familyMembers?.[0] || response.principalMember;
        setCheckHistory((prev) =>
          [
            {
              id: `${Date.now()}-${trimmedValue}`,
              input: trimmedValue,
              eligible: !!response.eligible,
              memberName: firstMember?.fullName || 'غير محدد',
              checkedAt: new Date().toISOString(),
              dateKey: new Date().toLocaleDateString('en-CA')
            },
            ...prev
          ].slice(0, 50)
        );
        // Auto-select first eligible member
        if (response.familyMembers && response.familyMembers.length > 0) {
          const firstEligible = response.familyMembers.find((m) => m.eligible);
          if (firstEligible) {
            setSelectedMember(firstEligible);
          }
        }
      } else {
        setError(response?.message || 'فشل في التحقق من الأهلية - استجابة غير صالحة');
      }
    } catch (err) {
      console.error('Eligibility check failed:', err);
      setError(err.message || 'فشل في الاتصال بالخادم');
    } finally {
      setLoading(false);
    }
  }, []);

  // ========================================
  // EVENT HANDLERS
  // ========================================

  const handleInputChange = (e) => {
    setSearchValue(e.target.value);
  };

  const handleSubmit = () => {
    checkEligibility(searchValue);
  };

  const handleReset = () => {
    setSearchValue('');
    setResult(null);
    setError(null);
    setSelectedMember(null);
    setSelectedVisitType('');
    lastAutoSubmittedRef.current = '';
  };

  useEffect(() => {
    const trimmed = searchValue?.trim();

    if (!trimmed || trimmed.length < 6) {
      return;
    }

    if (loading || trimmed === lastAutoSubmittedRef.current) {
      return;
    }

    if (autoCheckTimerRef.current) {
      clearTimeout(autoCheckTimerRef.current);
    }

    autoCheckTimerRef.current = setTimeout(() => {
      lastAutoSubmittedRef.current = trimmed;
      checkEligibility(trimmed);
    }, 450);

    return () => {
      if (autoCheckTimerRef.current) {
        clearTimeout(autoCheckTimerRef.current);
      }
    };
  }, [searchValue, loading, checkEligibility]);

  // ========================================
  // QR SCANNER HANDLERS
  // ========================================

  const startQrScanner = async () => {
    setScannerOpen(true);
    setCameraError(null);
    setScanning(true);

    try {
      const html5QrCode = new Html5Qrcode('qr-reader-provider');
      html5QrCodeRef.current = html5QrCode;

      await html5QrCode.start(
        { facingMode: 'environment' },
        {
          fps: 10,
          qrbox: { width: '15.625rem', height: '15.625rem' }
        },
        (decodedText) => {
          stopQrScanner();
          setScannerOpen(false);
          setSearchValue(decodedText);
          checkEligibility(decodedText);
        },
        (errorMessage) => {
          // Ignore scan errors (continuous scanning)
        }
      );
    } catch (err) {
      console.error('Failed to start QR scanner:', err);
      setCameraError('فشل في تشغيل الكاميرا. تأكد من منح الإذن للوصول إلى الكاميرا.');
      setScanning(false);
    }
  };

  const stopQrScanner = async () => {
    if (html5QrCodeRef.current && scanning) {
      try {
        await html5QrCodeRef.current.stop();
        html5QrCodeRef.current = null;
      } catch (err) {
        console.error('Failed to stop scanner:', err);
      } finally {
        setScanning(false);
      }
    }
  };

  const handleOpenScannerDialog = () => {
    startQrScanner();
  };

  const handleCloseScannerDialog = () => {
    stopQrScanner();
    setScannerOpen(false);
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopQrScanner();
    };
  }, []);

  // Hardware Scanner Support (focus on hidden input)
  useEffect(() => {
    let buffer = '';
    let timeout;

    const handleKeyDown = (e) => {
      const target = e.target;
      if (target.id === 'scanner-input-provider' || target.tagName === 'INPUT') {
        clearTimeout(timeout);

        if (e.key === 'Enter' && buffer.trim()) {
          e.preventDefault();
          setSearchValue(buffer.trim());
          checkEligibility(buffer.trim());
          buffer = '';
        } else if (e.key.length === 1) {
          buffer += e.key;

          // Scanner typically sends all characters within 50ms, so 300ms timeout
          // allows distinguishing between scanner and manual typing
          timeout = setTimeout(() => {
            if (buffer.trim() && buffer.trim().length >= 3) {
              setSearchValue(buffer.trim());
              checkEligibility(buffer.trim());
              buffer = '';
            } else {
              // Reset buffer for short inputs (manual typing)
              buffer = '';
            }
          }, 300);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      clearTimeout(timeout);
    };
  }, [checkEligibility]);

  // ========================================
  // VISIT REGISTRATION HANDLER
  // ========================================

  const handleRegisterVisit = async () => {
    if (!selectedVisitType) {
      setError('يجب اختيار نوع الزيارة');
      return;
    }

    setRegisteringVisit(true);
    try {
      const visitResponse = await providerApi.registerVisit({
        memberId: selectedMember.memberId,
        eligibilityCheckId: result.eligibilityCheckId,
        visitType: selectedVisitType
      });

      if (visitResponse.success) {
        navigate('/provider/visits', {
          state: {
            successMessage: `تم تسجيل الزيارة بنجاح للمنتفع ${selectedMember.fullName}`,
            newVisitId: visitResponse.visitId
          }
        });
      } else {
        setError(visitResponse.message || 'فشل في تسجيل الزيارة');
      }
    } catch (err) {
      console.error('Failed to register visit:', err);
      setError(err.message || 'فشل في تسجيل الزيارة');
    } finally {
      setRegisteringVisit(false);
    }
  };

  // ========================================
  // RENDER: TABLE HEADER COLORS
  // ========================================

  const tableHeaderBg = isDark ? 'rgba(66, 66, 66, 0.5)' : 'grey.100';
  const tableHeaderColor = isDark ? 'rgba(255, 255, 255, 0.87)' : 'text.primary';

  // ========================================
  // RENDER
  // ========================================

  return (
    <Box sx={{ bgcolor: '#F5F7FA', minHeight: 'calc(100vh - 80px)', p: { xs: 1, md: 2 }, borderRadius: '0.25rem' }}>
      <ModernPageHeader
        title="فحص الأهلية"
        subtitle="تحقق من أهلية المؤمن عليه وسجل الزيارة"
        icon={LocalHospitalIcon}
        titleExtras={
          <Chip
            size="small"
            variant="outlined"
            color="success"
            icon={<FiberManualRecordIcon sx={{ fontSize: '0.5rem !important' }} />}
            label="الاتصال بالمنصة نشط"
            sx={{ fontWeight: 600 }}
          />
        }
      />

      <Box sx={{ display: 'flex', gap: '1.25rem', mt: 1, flexDirection: { xs: 'column', lg: 'row' }, alignItems: 'stretch' }}>
        {/* SEARCH / CHECK PANEL */}
        <Paper
          elevation={0}
          sx={{
            width: { xs: '100%', lg: '23.5rem' },
            flexShrink: 0,
            p: '1.25rem',
            borderRadius: '0.5rem',
            border: '1px solid',
            borderColor: 'divider',
            bgcolor: 'common.white',
            height: 'fit-content'
          }}
        >
          <Stack spacing={2}>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: '2.25rem',
                  height: '2.25rem',
                  borderRadius: '0.5rem',
                  bgcolor: 'success.lighter',
                  color: 'success.main',
                  flexShrink: 0
                }}
              >
                <LocalHospitalIcon fontSize="small" />
              </Box>
              <Box>
                <Typography variant="subtitle1" fontWeight={700}>
                  فحص الأهلية
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  تحقق من أهلية المؤمن عليه وسجل الزيارة
                </Typography>
              </Box>
            </Stack>

            {/* Search by beneficiary name → pick → runs eligibility */}
            <Autocomplete
              freeSolo
              options={nameOptions}
              loading={nameLoading}
              filterOptions={(x) => x}
              getOptionLabel={(o) => (typeof o === 'string' ? o : `${o.fullName || ''}${o.cardNumber ? ' — ' + o.cardNumber : ''}`)}
              isOptionEqualToValue={(o, v) => o.memberId === v.memberId}
              onInputChange={(_e, val, reason) => {
                if (reason === 'input') handleNameSearch(val);
              }}
              onChange={(_e, val) => {
                if (val && typeof val === 'object' && val.cardNumber) {
                  setSearchValue(val.cardNumber);
                  checkEligibility(val.cardNumber);
                }
              }}
              noOptionsText="اكتب 3 أحرف على الأقل من اسم المستفيد"
              disabled={loading}
              size="small"
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="بحث باسم المستفيد"
                  placeholder="اكتب اسم المستفيد لعرض القائمة..."
                  InputProps={{
                    ...params.InputProps,
                    startAdornment: (
                      <InputAdornment position="start">
                        <PersonIcon color="primary" fontSize="small" />
                      </InputAdornment>
                    )
                  }}
                />
              )}
            />

            <Typography variant="caption" color="text.secondary" textAlign="center">
              أو استخدم رقم البطاقة / الباركود / الكاميرا
            </Typography>

            <Stack direction="row" spacing={1}>
              <Button
                fullWidth
                size="small"
                variant="outlined"
                startIcon={<CreditCardIcon fontSize="small" />}
                onClick={() => document.getElementById('provider-eligibility-barcode-input')?.focus()}
                disabled={loading}
                sx={{ borderRadius: '0.375rem', fontSize: '0.7rem' }}
              >
                رقم البطاقة
              </Button>
              <Button
                fullWidth
                size="small"
                variant="outlined"
                startIcon={<QrCodeScannerIcon fontSize="small" />}
                onClick={() => document.getElementById('provider-eligibility-barcode-input')?.focus()}
                disabled={loading}
                sx={{ borderRadius: '0.375rem', fontSize: '0.7rem' }}
              >
                الباركود
              </Button>
              <Button
                fullWidth
                size="small"
                variant="outlined"
                startIcon={<PhotoCameraIcon fontSize="small" />}
                onClick={handleOpenScannerDialog}
                disabled={loading}
                sx={{ borderRadius: '0.375rem', fontSize: '0.7rem' }}
              >
                الكاميرا
              </Button>
            </Stack>

            <TextField
              id="provider-eligibility-barcode-input"
              fullWidth
              size="medium"
              label="رقم البطاقة / الباركود / رقم العضو"
              placeholder="ابدأ الكتابة أو المسح... يبدأ الفحص تلقائياً"
              value={searchValue}
              onChange={handleInputChange}
              disabled={loading}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <QrCodeScannerIcon color="primary" />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    {loading ? <CircularProgress size={20} /> : <CreditCardIcon color="action" />}
                  </InputAdornment>
                )
              }}
              sx={{
                direction: 'ltr',
                '& .MuiOutlinedInput-root': {
                  borderRadius: '0.375rem',
                  bgcolor: 'common.white',
                  transition: 'all 0.2s ease',
                  '& fieldset': { borderColor: 'divider' },
                  '&:hover fieldset': { borderColor: 'success.main' }
                }
              }}
            />

            <Button
              fullWidth
              variant="contained"
              color="success"
              onClick={handleSubmit}
              disabled={loading || !searchValue.trim()}
              startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <CheckCircleIcon />}
              sx={{ borderRadius: '0.375rem', fontWeight: 700, py: '0.625rem' }}
            >
              تنفيذ الفحص
            </Button>

            <Typography variant="caption" color="text.secondary" textAlign="center">
              يتم الفحص تلقائياً عند إدخال 6 أحرف أو أكثر
            </Typography>

            <Box sx={{ height: 0, overflow: 'hidden', opacity: 0 }}>
              <TextField id="scanner-input-provider" ref={scannerInputRef} fullWidth size="small" />
            </Box>

            {error && (
              <Alert severity="error" onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
          </Stack>
        </Paper>

        {/* RESULT PANEL */}
        <Paper
          elevation={0}
          sx={{
            flex: 1,
            minWidth: 0,
            borderRadius: '0.5rem',
            border: '1px solid',
            borderColor: 'divider',
            bgcolor: 'common.white',
            display: 'flex',
            flexDirection: 'column',
            minHeight: '34rem',
            overflow: 'hidden'
          }}
        >
          {result ? (
            <>
              {/* Status row */}
              <Box
                sx={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 1,
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  px: '1.25rem',
                  py: '0.875rem',
                  borderBottom: '1px solid',
                  borderColor: 'divider'
                }}
              >
                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                  <Chip
                    size="small"
                    icon={result.eligible ? <CheckCircleIcon /> : <CancelIcon />}
                    label={result.eligible ? 'العائلة مؤهلة' : 'غير مؤهل'}
                    color={result.eligible ? 'success' : 'error'}
                    sx={{ fontWeight: 700 }}
                  />
                  {result.barcode && (
                    <Chip
                      size="small"
                      variant="outlined"
                      icon={<BadgeIcon fontSize="small" />}
                      label={`#${result.barcode}`}
                      sx={{ fontFamily: 'monospace' }}
                    />
                  )}
                </Stack>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <FamilyRestroomIcon fontSize="small" color="action" />
                  <Typography variant="body2" color="text.secondary">
                    أفراد العائلة ({result.totalFamilyMembers || result.familyMembers?.length || 0})
                  </Typography>
                  <IconButton onClick={handleReset} size="small" title="إعادة ضبط" sx={{ ml: 0.5 }}>
                    <RefreshIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </Box>

              {result.warnings && result.warnings.length > 0 && (
                <Box sx={{ px: '1.25rem', pt: '0.75rem' }}>
                  {result.warnings.map((warning, index) => (
                    <Alert key={index} severity="warning" sx={{ mb: 1 }}>
                      {warning}
                    </Alert>
                  ))}
                </Box>
              )}

              {/* Member header + coverage summary */}
              {result.principalMember && (
                <Box
                  sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '1rem',
                    alignItems: 'center',
                    px: '1.25rem',
                    py: '1rem',
                    borderBottom: '1px solid',
                    borderColor: 'divider'
                  }}
                >
                  <Stack direction="row" spacing={1.5} alignItems="center" sx={{ minWidth: '12rem' }}>
                    <MemberAvatar
                      member={{
                        id: result.principalMember.memberId,
                        fullName: result.principalMember.fullName,
                        photoUrl: result.principalMember.profileImage
                      }}
                      size={44}
                      refreshTrigger={`${result.principalMember.memberId || ''}-${result.principalMember.profileImage || ''}`}
                    />
                    <Box>
                      <Typography variant="subtitle2" fontWeight={700}>
                        {result.principalMember.fullName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        العضو الرئيسي
                        {result.employerName ? ` · ${result.employerName}` : ''}
                        {result.principalMember.age ? ` · ${result.principalMember.age} سنة` : ''}
                      </Typography>
                    </Box>
                  </Stack>
                  <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ ml: { lg: 'auto' } }}>
                    <SummaryStatChip label="الحد السنوي" value={formatCurrency(result.principalAnnualLimit)} tone="neutral" />
                    <SummaryStatChip label="المستخدم" value={formatCurrency(result.principalUsedAmount)} tone="warn" />
                    <SummaryStatChip label="المتبقي" value={formatCurrency(result.principalRemainingLimit)} tone="ok" />
                  </Stack>
                </Box>
              )}

              {/* Family members table (scrollable) */}
              <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
                {result.familyMembers && result.familyMembers.length > 0 ? (
                  <TableContainer sx={{ flex: 1, overflow: 'auto' }}>
                    <Table size="small" stickyHeader>
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ bgcolor: tableHeaderBg, color: tableHeaderColor, fontWeight: 600 }}>الاسم</TableCell>
                          <TableCell sx={{ bgcolor: tableHeaderBg, color: tableHeaderColor, fontWeight: 600 }}>الصلة</TableCell>
                          <TableCell sx={{ bgcolor: tableHeaderBg, color: tableHeaderColor, fontWeight: 600 }}>العمر</TableCell>
                          <TableCell sx={{ bgcolor: tableHeaderBg, color: tableHeaderColor, fontWeight: 600 }}>الحالة</TableCell>
                          <TableCell sx={{ bgcolor: tableHeaderBg, color: tableHeaderColor, fontWeight: 600 }}>المتبقي</TableCell>
                          <TableCell sx={{ bgcolor: tableHeaderBg, color: tableHeaderColor, fontWeight: 600 }}>النسبة</TableCell>
                          <TableCell sx={{ bgcolor: tableHeaderBg, color: tableHeaderColor, fontWeight: 600 }} align="center">
                            اختيار
                          </TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {result.familyMembers.map((member) => (
                          <TableRow
                            key={member.memberId}
                            selected={selectedMember?.memberId === member.memberId}
                            hover
                            sx={{
                              cursor: member.eligible ? 'pointer' : 'default',
                              bgcolor: member.isPrincipal ? (isDark ? 'rgba(13, 71, 161, 0.15)' : 'primary.lighter') : 'inherit',
                              opacity: member.eligible ? 1 : 0.6
                            }}
                            onClick={() => member.eligible && setSelectedMember(member)}
                          >
                            <TableCell>
                              <Stack direction="row" alignItems="center" spacing={1}>
                                <PersonIcon fontSize="small" color={member.isPrincipal ? 'primary' : 'action'} />
                                <Box>
                                  <Typography variant="body2" fontWeight={member.isPrincipal ? 'bold' : 'normal'}>
                                    {member.fullName}
                                  </Typography>
                                  {member.isPrincipal && (
                                    <Chip label="رئيسي" size="small" color="primary" sx={{ height: '1.125rem', fontSize: '0.75rem' }} />
                                  )}
                                </Box>
                              </Stack>
                            </TableCell>
                            <TableCell>{member.relationship || 'SELF'}</TableCell>
                            <TableCell>{member.age ?? '—'}</TableCell>
                            <TableCell>
                              <Chip
                                label={member.eligible ? 'مؤهل' : 'غير مؤهل'}
                                color={member.eligible ? 'success' : 'error'}
                                size="small"
                              />
                            </TableCell>
                            <TableCell>{formatCurrency(member.remainingLimit)}</TableCell>
                            <TableCell>
                              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                <LinearProgress
                                  variant="determinate"
                                  value={member.usagePercentage || 0}
                                  color={getUsageColor(member.usagePercentage || 0)}
                                  sx={{ height: '0.375rem', borderRadius: 1, flex: 1, minWidth: '3.125rem' }}
                                />
                                <Typography variant="caption">{(member.usagePercentage || 0).toFixed(0)}%</Typography>
                              </Box>
                            </TableCell>
                            <TableCell align="center">
                              <Button
                                variant={selectedMember?.memberId === member.memberId ? 'contained' : 'outlined'}
                                size="small"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSelectedMember(member);
                                }}
                                disabled={!member.eligible}
                                color={selectedMember?.memberId === member.memberId ? 'primary' : 'inherit'}
                              >
                                {selectedMember?.memberId === member.memberId ? 'محدد ✓' : 'اختيار'}
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                ) : (
                  <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', p: '1.5rem' }}>
                    <Typography variant="body2" color="text.secondary">
                      لا يوجد أفراد عائلة مرتبطون بهذا الفحص
                    </Typography>
                  </Box>
                )}
              </Box>

              {result.coveredServices && result.coveredServices.length > 0 && (
                <Box sx={{ px: '1.25rem', py: '0.75rem', borderTop: '1px solid', borderColor: 'divider' }}>
                  <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
                    الخدمات المغطاة:
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {result.coveredServices.map((service, index) => (
                      <Chip key={index} label={service} size="small" color="primary" variant="outlined" />
                    ))}
                  </Stack>
                </Box>
              )}

              {/* Bottom action bar: visit type + register */}
              <Box sx={{ borderTop: '1px solid', borderColor: 'divider', bgcolor: 'grey.50', px: '1.25rem', py: '0.875rem' }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ xs: 'stretch', sm: 'center' }}>
                  {selectedMember && (
                    <Chip
                      size="small"
                      color="primary"
                      variant="outlined"
                      label={`المحدد: ${selectedMember.fullName}`}
                      sx={{ maxWidth: { xs: '100%', sm: '13rem' } }}
                    />
                  )}
                  <FormControl size="small" sx={{ flex: 1, minWidth: '12rem' }} error={!selectedVisitType}>
                    <InputLabel id="visit-type-label">اختر نوع الزيارة *</InputLabel>
                    <Select
                      labelId="visit-type-label"
                      value={selectedVisitType}
                      label="اختر نوع الزيارة *"
                      onChange={(e) => setSelectedVisitType(e.target.value)}
                    >
                      {VISIT_TYPE_OPTIONS.map((opt) => (
                        <MenuItem key={opt.value} value={opt.value}>
                          {opt.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <Button
                    variant="contained"
                    color="primary"
                    startIcon={registeringVisit ? <CircularProgress size={18} color="inherit" /> : <AssignmentIcon />}
                    disabled={!selectedMember || registeringVisit || !selectedVisitType}
                    onClick={handleRegisterVisit}
                    sx={{ whiteSpace: 'nowrap', fontWeight: 700, borderRadius: '0.375rem' }}
                  >
                    {registeringVisit ? 'جاري التسجيل...' : 'تسجيل الزيارة'}
                  </Button>
                </Stack>
                {!selectedMember && (
                  <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                    اختر منتفعاً مؤهلاً من الجدول أعلاه لتفعيل تسجيل الزيارة
                  </Typography>
                )}
              </Box>
            </>
          ) : (
            <Box sx={{ flex: 1, overflow: 'auto', p: '1.5rem' }}>
              <Box sx={{ textAlign: 'center', py: '1.5rem' }}>
                <LocalHospitalIcon sx={{ fontSize: '3rem', color: 'text.disabled', mb: 1 }} />
                <Typography variant="subtitle1" color="text.secondary" gutterBottom>
                  في انتظار الفحص
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  أدخل رقم البطاقة أو استخدم البحث بالاسم أو مسح الكاميرا للبدء
                </Typography>
              </Box>

              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 4 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.5rem', height: '100%' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <HistoryIcon color="primary" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>
                          تاريخ فحوصات اليوم
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {todayChecks.length} عملية فحص
                        </Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.5rem', height: '100%' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <TaskAltIcon color="success" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>
                          آخر منتفع تم فحصه
                        </Typography>
                        <Typography variant="body2" color="text.secondary" noWrap>
                          {checkHistory[0]?.memberName || 'لا يوجد بعد'}
                        </Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.5rem', height: '100%' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <TipsAndUpdatesIcon color="warning" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>
                          تعليمات سريعة
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          مرّر الباركود أو أدخل رقم البطاقة
                        </Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: '1.0rem',
                      borderRadius: '0.5rem',
                      bgcolor: 'success.lighter',
                      border: '1px solid',
                      borderColor: 'success.light'
                    }}
                  >
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="subtitle2" color="success.dark" fontWeight={700}>
                        حالات مقبولة اليوم
                      </Typography>
                      <Typography variant="h4" color="success.dark" fontWeight={800}>
                        {todayAcceptedCount}
                      </Typography>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Paper
                    variant="outlined"
                    sx={{ p: '1.0rem', borderRadius: '0.5rem', bgcolor: 'error.lighter', border: '1px solid', borderColor: 'error.light' }}
                  >
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="subtitle2" color="error.dark" fontWeight={700}>
                        حالات مرفوضة اليوم
                      </Typography>
                      <Typography variant="h4" color="error.dark" fontWeight={800}>
                        {todayRejectedCount}
                      </Typography>
                    </Stack>
                  </Paper>
                </Grid>
              </Grid>
            </Box>
          )}
        </Paper>
      </Box>

      <Paper
        variant="outlined"
        sx={{
          mt: '1.0rem',
          p: '0.75rem',
          borderRadius: '0.25rem',
          bgcolor: 'common.white',
          display: 'flex',
          alignItems: 'center',
          gap: '0.625rem',
          overflowX: 'auto'
        }}
      >
        <Typography variant="body2" fontWeight={700} sx={{ whiteSpace: 'nowrap' }}>
          آخر 5 عمليات فحص ناجحة:
        </Typography>
        <Stack direction="row" spacing={1}>
          {recentSuccessfulChecks.length > 0 ? (
            recentSuccessfulChecks.map((item) => (
              <Chip
                key={item.id}
                size="small"
                color="success"
                variant="outlined"
                label={`${item.memberName} • ${new Date(item.checkedAt).toLocaleTimeString('ar-LY', { hour: '2-digit', minute: '2-digit' })}`}
              />
            ))
          ) : (
            <Typography variant="caption" color="text.secondary" sx={{ py: 0.5 }}>
              لا توجد عمليات ناجحة حتى الآن
            </Typography>
          )}
        </Stack>
      </Paper>

      {/* QR Scanner Dialog */}
      <Dialog open={scannerOpen} onClose={handleCloseScannerDialog} maxWidth="sm" fullWidth>
        <DialogTitle>
          مسح الباركود / QR Code
          <IconButton onClick={handleCloseScannerDialog} sx={{ position: 'absolute', right: '0.375rem', top: '4.0rem' }}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          {cameraError ? (
            <Alert severity="warning" sx={{ mb: '1.0rem' }}>
              <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
                {cameraError}
              </Typography>
              <Typography variant="caption" sx={{ display: 'block', mb: 1 }}>
                هذه مشكلة في إعدادات/إذن الكاميرا على جهازك وليست في النظام. تأكد من السماح للكاميرا في المتصفح، ومن تفعيلها في إعدادات
                ويندوز.
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Link href="ms-settings:privacy-webcam" underline="hover" sx={{ fontWeight: 700, fontSize: '0.8rem' }}>
                  فتح إعدادات كاميرا ويندوز ←
                </Link>
                <Button size="small" variant="outlined" color="warning" startIcon={<RefreshIcon />} onClick={startQrScanner}>
                  إعادة المحاولة
                </Button>
              </Stack>
            </Alert>
          ) : (
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: '1.0rem' }}>
                وجّه الكاميرا نحو الباركود أو QR Code
              </Typography>
              <Box
                id="qr-reader-provider"
                sx={{
                  width: '100%',
                  '& video': {
                    width: '100%',
                    borderRadius: 1
                  }
                }}
              />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseScannerDialog}>إلغاء</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
