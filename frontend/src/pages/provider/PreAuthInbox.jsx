import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Button,
  Alert,
  CircularProgress,
  Paper,
  Typography
} from '@mui/material';
import { CheckCircle } from '@mui/icons-material';
import preApprovalsService from 'services/api/pre-approvals.service';
import MainCard from 'components/MainCard';
import useAuth from 'hooks/useAuth';
import { useSnackbar } from 'notistack';

/**
 * Provider Pre-Authorization Inbox — "My Submissions" (PREAUTH-REVIEW-WORKFLOW-1)
 *
 * Shows ALL of the provider's own submitted pre-authorizations, across every
 * status (PENDING, UNDER_REVIEW, NEEDS_CORRECTION, APPROVED, REJECTED,
 * ACKNOWLEDGED, USED, EXPIRED, CANCELLED) — not just APPROVED/ACKNOWLEDGED as
 * before, so a provider can actually see when a request is rejected or needs
 * correction, not only when it's been approved.
 *
 * The previous implementation called the /inbox/pending endpoint with a
 * status query param the backend never reads (that endpoint always returns
 * PENDING/UNDER_REVIEW regardless of the param), so it was silently showing
 * the wrong data under the "موافق عليه"/"تم الاطلاع" tabs. This now uses
 * GET /pre-authorizations/provider/{providerId}, which returns everything
 * for the provider, and groups by actual status client-side.
 */
const STATUS_LABELS = {
  PENDING: 'معلق',
  UNDER_REVIEW: 'قيد المراجعة',
  NEEDS_CORRECTION: 'بحاجة لاستكمال بيانات',
  APPROVAL_IN_PROGRESS: 'جارِ الاعتماد',
  APPROVED: 'موافق عليه',
  ACKNOWLEDGED: 'تم الاطلاع',
  REJECTED: 'مرفوض',
  USED: 'مستخدم',
  EXPIRED: 'منتهي',
  CANCELLED: 'ملغي'
};

const STATUS_COLORS = {
  PENDING: 'warning',
  UNDER_REVIEW: 'info',
  NEEDS_CORRECTION: 'warning',
  APPROVAL_IN_PROGRESS: 'info',
  APPROVED: 'success',
  ACKNOWLEDGED: 'info',
  REJECTED: 'error',
  USED: 'default',
  EXPIRED: 'default',
  CANCELLED: 'default'
};

const ProviderPreAuthInbox = () => {
  const { user } = useAuth();
  const { enqueueSnackbar } = useSnackbar();

  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState([]);
  const [processingIds, setProcessingIds] = useState(new Set());
  const [error, setError] = useState(null);

  const providerId = user?.providerId;

  const loadPreAuthorizations = useCallback(async () => {
    if (!providerId) {
      setError('تعذر تحديد مقدم الخدمة الحالي');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await preApprovalsService.getByProvider(providerId, {
        page: 0,
        size: 100,
        sortBy: 'createdAt',
        sortDirection: 'DESC'
      });
      setItems(response?.items || []);
    } catch (err) {
      console.error('Failed to load pre-authorizations:', err);
      enqueueSnackbar('فشل تحميل الموافقات المسبقة', { variant: 'error' });
      setError('فشل تحميل الموافقات المسبقة');
    } finally {
      setLoading(false);
    }
  }, [providerId, enqueueSnackbar]);

  useEffect(() => {
    loadPreAuthorizations();
  }, [loadPreAuthorizations]);

  const handleAcknowledge = async (preAuthId) => {
    setProcessingIds((prev) => new Set(prev).add(preAuthId));
    try {
      await preApprovalsService.acknowledge(preAuthId);
      enqueueSnackbar('تم الاطلاع على الموافقة بنجاح', { variant: 'success' });
      await loadPreAuthorizations();
    } catch (err) {
      console.error('Failed to acknowledge pre-authorization:', err);
      enqueueSnackbar('فشل تأكيد الاطلاع', { variant: 'error' });
    } finally {
      setProcessingIds((prev) => {
        const newSet = new Set(prev);
        newSet.delete(preAuthId);
        return newSet;
      });
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Intl.DateTimeFormat('en-GB', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(dateString));
  };

  const formatCurrency = (amount) => {
    if (amount === null || amount === undefined) return '-';
    return `${parseFloat(amount).toFixed(2)} د.ل`;
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <MainCard title="صندوق الموافقات المسبقة — طلباتي">
      <Box>
        <Alert severity="info" sx={{ mb: '1.5rem' }}>
          <Typography variant="body2">
            هنا تظهر جميع طلبات الموافقة المسبقة التي قدّمتها بجميع حالاتها. عند الموافقة، اضغط على &quot;تم الاطلاع&quot; لتأكيد
            الاستلام. إذا طُلب استكمال بيانات، راجع الملاحظة وقدّم الطلب مجدداً.
          </Typography>
        </Alert>

        {error && (
          <Alert severity="error" sx={{ mb: '1.5rem' }}>
            {error}
          </Alert>
        )}

        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>رقم المرجع</TableCell>
                <TableCell>اسم العضو</TableCell>
                <TableCell>الخدمة الطبية</TableCell>
                <TableCell align="right">المبلغ الموافق عليه</TableCell>
                <TableCell>تاريخ الطلب</TableCell>
                <TableCell>الحالة</TableCell>
                <TableCell>ملاحظة المراجع</TableCell>
                <TableCell align="center">الإجراء</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} align="center">
                    <Typography variant="body2" color="textSecondary" sx={{ py: '1.5rem' }}>
                      لا توجد طلبات موافقة مسبقة
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                items.map((item) => (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight="medium">
                        {item.referenceNumber}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{item.memberName || item.memberFullNameArabic || item.memberCivilId}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{item.serviceName}</Typography>
                      <Typography variant="caption" color="textSecondary">
                        {item.serviceCode}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2" fontWeight="medium" color="success.main">
                        {formatCurrency(item.approvedAmount ?? item.insuranceCoveredAmount ?? item.contractPrice)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{formatDate(item.createdAt || item.requestDate)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label={STATUS_LABELS[item.status] || item.status} color={STATUS_COLORS[item.status] || 'default'} size="small" />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color={item.status === 'NEEDS_CORRECTION' ? 'warning.main' : 'textSecondary'}>
                        {item.reviewerNotes || item.reviewerComment || item.rejectionReason || item.notes || '-'}
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      {item.status === 'APPROVED' && (
                        <Button
                          variant="contained"
                          color="primary"
                          size="small"
                          startIcon={processingIds.has(item.id) ? <CircularProgress size={16} /> : <CheckCircle />}
                          onClick={() => handleAcknowledge(item.id)}
                          disabled={processingIds.has(item.id)}
                        >
                          تم الاطلاع
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    </MainCard>
  );
};

export default ProviderPreAuthInbox;
