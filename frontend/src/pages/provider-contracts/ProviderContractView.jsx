/**
 * Provider Contract View Page
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Displays detailed view of a single provider contract including:
 * - Contract summary (code, status, dates)
 * - Provider information
 * - Pricing model and discount settings
 * - Pricing items table with search
 * - Lifecycle actions (activate, suspend, terminate)
 *
 * Uses REAL Backend API via provider-contracts.service.js
 *
 * Route: /provider-contracts/:id
 * @version 2.1.0
 * @lastUpdated 2024-05-22
 */

import { useState, useMemo, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import ContractPriceListTab from 'components/classification/ContractPriceListTab';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Collapse,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  ArrowBack as BackIcon,
  CalendarToday as CalendarIcon,
  Description as ContractIcon,
  Edit as EditIcon,
  Info as InfoIcon,
  LocalOffer as PriceIcon,
  CheckCircle as ActivateIcon,
  PauseCircle as SuspendIcon,
  Cancel as TerminateIcon,
  Refresh as RefreshIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon
} from '@mui/icons-material';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// API Service
import {
  getProviderContractById,
  activateContract,
  suspendContract,
  terminateContract,
  CONTRACT_STATUS,
  CONTRACT_STATUS_CONFIG,
  PRICING_MODEL_CONFIG
} from 'services/api/provider-contracts.service';

// Snackbar
import { useSnackbar } from 'notistack';

// ═══════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Format date for display
 */
const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  try {
    return new Date(dateStr).toLocaleDateString('en-GB', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  } catch {
    return dateStr;
  }
};

// ═══════════════════════════════════════════════════════════════════════════
// HELPER COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Info Row - displays label/value pairs
 */
const InfoRow = ({ label, value, valueColor, icon: Icon }) => (
  <Stack direction="row" spacing={1} alignItems="center" sx={{ py: 0.5 }}>
    {Icon && <Icon sx={{ fontSize: '1.125rem', color: 'text.secondary' }} />}
    <Typography variant="caption" color="text.secondary" sx={{ minWidth: '5.0rem' }}>
      {label}:
    </Typography>
    <Typography variant="body2" fontWeight={600} color={valueColor || 'text.primary'} noWrap>
      {value}
    </Typography>
  </Stack>
);

/**
 * Tab Panel for displaying tab content
 */

// ═══════════════════════════════════════════════════════════════════════════
// MAIN COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

const ProviderContractView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  // ─────────────────────────────────────────────────────────────────────────
  // STATE
  // ─────────────────────────────────────────────────────────────────────────

  const [notesExpanded, setNotesExpanded] = useState(true);

  // Dialog states
  const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);
  const [terminateDialogOpen, setTerminateDialogOpen] = useState(false);
  const [suspendReason, setSuspendReason] = useState('');
  const [terminateReason, setTerminateReason] = useState('');

  // ─────────────────────────────────────────────────────────────────────────
  // DATA FETCHING (Real API)
  // ─────────────────────────────────────────────────────────────────────────

  // Fetch contract details
  const {
    data: contract,
    isLoading,
    isError,
    error,
    refetch
  } = useQuery({
    queryKey: ['provider-contract', id],
    queryFn: () => getProviderContractById(id),
    enabled: !!id,
    retry: 1,
    staleTime: 30000
  });

  // NOTE: Medical services are now fetched dynamically by MedicalServiceSelector component

  // ─────────────────────────────────────────────────────────────────────────
  // MUTATIONS
  // ─────────────────────────────────────────────────────────────────────────

  const syncContractStatusCaches = useCallback(
    (updatedContract) => {
      if (!updatedContract) return;

      queryClient.setQueryData(['provider-contract', id], updatedContract);

      queryClient.setQueriesData({ queryKey: ['provider-contracts'] }, (oldData) => {
        if (!oldData) return oldData;

        const applyUpdate = (list) => {
          if (!Array.isArray(list)) return list;
          return list.map((item) => (String(item?.id) === String(updatedContract.id) ? { ...item, ...updatedContract } : item));
        };

        if (Array.isArray(oldData)) {
          return applyUpdate(oldData);
        }

        if (Array.isArray(oldData.content)) {
          return { ...oldData, content: applyUpdate(oldData.content) };
        }

        if (Array.isArray(oldData.items)) {
          return { ...oldData, items: applyUpdate(oldData.items) };
        }

        return oldData;
      });
    },
    [queryClient, id]
  );

  const activateMutation = useMutation({
    mutationFn: () => activateContract(id),
    onSuccess: (updatedContract) => {
      syncContractStatusCaches(updatedContract);
      enqueueSnackbar('تم تفعيل العقد بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
    },
    onError: (err) => {
      const errorMsg = err.response?.data?.message || err.message || 'فشل تفعيل العقد';
      if (errorMsg.includes('ACTIVE')) {
        enqueueSnackbar('العقد مُفعّل بالفعل', { variant: 'warning' });
        queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      } else {
        enqueueSnackbar(errorMsg, { variant: 'error' });
      }
    }
  });

  const suspendMutation = useMutation({
    mutationFn: (reason) => suspendContract(id, reason),
    onSuccess: (updatedContract) => {
      syncContractStatusCaches(updatedContract);
      enqueueSnackbar('تم إيقاف العقد بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
      setSuspendDialogOpen(false);
      setSuspendReason('');
    },
    onError: (err) => {
      enqueueSnackbar(err.message || 'فشل إيقاف العقد', { variant: 'error' });
    }
  });

  const terminateMutation = useMutation({
    mutationFn: (reason) => terminateContract(id, reason),
    onSuccess: (updatedContract) => {
      syncContractStatusCaches(updatedContract);
      enqueueSnackbar('تم إلغاء العقد بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
      setTerminateDialogOpen(false);
      setTerminateReason('');
    },
    onError: (err) => {
      enqueueSnackbar(err.message || 'فشل إلغاء العقد', { variant: 'error' });
    }
  });

  // ─────────────────────────────────────────────────────────────────────────
  // COMPUTED VALUES
  // ─────────────────────────────────────────────────────────────────────────

  const statusConfig = useMemo(() => {
    return CONTRACT_STATUS_CONFIG[contract?.status] || { label: contract?.status, color: 'default' };
  }, [contract?.status]);

  const pricingModelConfig = useMemo(() => {
    return PRICING_MODEL_CONFIG[contract?.pricingModel] || { label: contract?.pricingModel };
  }, [contract?.pricingModel]);

  // ─────────────────────────────────────────────────────────────────────────
  // HANDLERS
  // ─────────────────────────────────────────────────────────────────────────

  const handleBack = useCallback(() => {
    navigate('/provider-contracts');
  }, [navigate]);

  const handleEdit = useCallback(() => {
    navigate(`/provider-contracts/edit/${id}`);
  }, [navigate, id]);

  const handleActivate = useCallback(() => {
    activateMutation.mutate();
  }, [activateMutation]);

  const handleSuspendConfirm = useCallback(() => {
    if (suspendReason.trim()) {
      suspendMutation.mutate(suspendReason);
    }
  }, [suspendMutation, suspendReason]);

  const handleTerminateConfirm = useCallback(() => {
    if (terminateReason.trim()) {
      terminateMutation.mutate(terminateReason);
    }
  }, [terminateMutation, terminateReason]);

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER - LOADING STATE
  // ─────────────────────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={400}>
        <CircularProgress />
      </Box>
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER - ERROR STATE
  // ─────────────────────────────────────────────────────────────────────────

  if (isError || !contract) {
    return (
      <MainCard>
        <Box display="flex" flexDirection="column" justifyContent="center" alignItems="center" minHeight={300} sx={{ py: '2.0rem' }}>
          <ContractIcon sx={{ fontSize: '4.0rem', color: 'error.main', mb: '1.0rem', opacity: 0.5 }} />
          <Typography variant="h6" color="error" gutterBottom>
            {isError ? 'خطأ في تحميل العقد' : 'العقد غير موجود'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: '1.0rem' }}>
            {error?.message || 'لم يتم العثور على العقد المطلوب'}
          </Typography>
          <Stack direction="row" spacing={2}>
            <Button variant="outlined" startIcon={<BackIcon />} onClick={handleBack}>
              العودة للقائمة
            </Button>
            <Button variant="contained" startIcon={<RefreshIcon />} onClick={() => refetch()}>
              إعادة المحاولة
            </Button>
          </Stack>
        </Box>
      </MainCard>
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER - CONTRACT VIEW
  // ─────────────────────────────────────────────────────────────────────────

  return (
    <>
      {/* Page Header */}
      <ModernPageHeader
        title={`عقد: ${contract.contractCode}`}
        subtitle={contract.providerName || contract.provider?.name || 'عقد مقدم خدمة'}
        icon={ContractIcon}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/dashboard' },
          { label: 'عقود مقدمي الخدمة', path: '/provider-contracts' },
          { label: contract.contractCode, path: `/provider-contracts/${id}` }
        ]}
        actions={
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" color="inherit" startIcon={<BackIcon />} onClick={handleBack}>
              رجوع
            </Button>

            {/* Lifecycle Actions */}
            {(contract.status === CONTRACT_STATUS.DRAFT ||
              contract.status === CONTRACT_STATUS.SUSPENDED ||
              contract.status === CONTRACT_STATUS.TERMINATED) && (
              <Button
                variant="contained"
                color="success"
                startIcon={<ActivateIcon />}
                onClick={handleActivate}
                disabled={activateMutation.isLoading}
              >
                {contract.status === CONTRACT_STATUS.TERMINATED ? 'إعادة تفعيل' : 'تفعيل العقد'}
              </Button>
            )}

            {contract.status === CONTRACT_STATUS.ACTIVE && (
              <Button
                variant="outlined"
                color="warning"
                startIcon={<SuspendIcon />}
                onClick={() => setSuspendDialogOpen(true)}
                disabled={suspendMutation.isLoading}
              >
                إيقاف
              </Button>
            )}

            {(contract.status === CONTRACT_STATUS.ACTIVE || contract.status === CONTRACT_STATUS.SUSPENDED) && (
              <Button
                variant="outlined"
                color="error"
                startIcon={<TerminateIcon />}
                onClick={() => setTerminateDialogOpen(true)}
                disabled={terminateMutation.isLoading}
              >
                إلغاء
              </Button>
            )}

            <Button
              variant="outlined"
              color="primary"
              startIcon={<EditIcon />}
              onClick={handleEdit}
              disabled={contract.status === CONTRACT_STATUS.TERMINATED}
            >
              تعديل
            </Button>
          </Stack>
        }
      />

      {/* Contract Summary Card - Ultra Slim & Horizontal */}
      <MainCard sx={{ mb: '1.0rem', py: 0.5 }}>
        <Stack direction="row" spacing={4} alignItems="center" justifyContent="center" flexWrap="wrap">
          <InfoRow label="نموذج السعر" value={pricingModelConfig.label} icon={PriceIcon} />
          <InfoRow label="نسبة التخفيض" value={contract.discountPercent ? `${contract.discountPercent}%` : '-'} icon={PriceIcon} />
          <InfoRow label="عدد البنود" value={contract.pricingItemsCount ?? '—'} icon={InfoIcon} />
          <InfoRow label="بداية العقد" value={formatDate(contract.startDate)} icon={CalendarIcon} />
          <InfoRow label="نهاية العقد" value={formatDate(contract.endDate)} icon={CalendarIcon} />
          <Box flexGrow={1} />
          <Chip label={statusConfig.label} color={statusConfig.color} size="small" variant="combined" />
        </Stack>
      </MainCard>

      {/* Notes Section */}
      {contract.notes && (
        <MainCard
          title="ملاحظات"
          secondary={
            <Tooltip title={notesExpanded ? 'إخفاء الملاحظات' : 'إظهار الملاحظات'}>
              <IconButton size="small" onClick={() => setNotesExpanded((v) => !v)}>
                {notesExpanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          }
          sx={{ mb: '1.5rem' }}
        >
          <Collapse in={notesExpanded}>
            <Typography variant="body1" color="text.secondary">
              {contract.notes}
            </Typography>
          </Collapse>
        </MainCard>
      )}

      {/* MC-4B (design review §4, D3/D4): the contract is a CONSUMER of
          published price lists — read-only card + search + brief history,
          with exactly two primary actions (استيراد / تعديل استثنائي). */}
      <MainCard title="قائمة الأسعار">
        <ContractPriceListTab contractId={id} providerId={contract?.provider?.id || contract?.providerId} />
      </MainCard>

      {/* Suspend Dialog */}
      <Dialog open={suspendDialogOpen} onClose={() => setSuspendDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>إيقاف العقد</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: '1.0rem' }}>هل أنت متأكد من إيقاف العقد؟ يرجى إدخال سبب الإيقاف.</DialogContentText>
          <TextField
            autoFocus
            label="سبب الإيقاف"
            fullWidth
            multiline
            rows={3}
            value={suspendReason}
            onChange={(e) => setSuspendReason(e.target.value)}
            required
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSuspendDialogOpen(false)}>إلغاء</Button>
          <Button
            onClick={handleSuspendConfirm}
            color="warning"
            variant="contained"
            disabled={!suspendReason.trim() || suspendMutation.isLoading}
          >
            {suspendMutation.isLoading ? <CircularProgress size={20} /> : 'إيقاف العقد'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Terminate Dialog */}
      <Dialog open={terminateDialogOpen} onClose={() => setTerminateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle color="error">إلغاء العقد</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: '1.0rem' }}>
            <strong>تحذير:</strong> إلغاء العقد إجراء نهائي ولا يمكن التراجع عنه. يرجى إدخال سبب الإلغاء.
          </DialogContentText>
          <TextField
            autoFocus
            label="سبب الإلغاء"
            fullWidth
            multiline
            rows={3}
            value={terminateReason}
            onChange={(e) => setTerminateReason(e.target.value)}
            required
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTerminateDialogOpen(false)}>تراجع</Button>
          <Button
            onClick={handleTerminateConfirm}
            color="error"
            variant="contained"
            disabled={!terminateReason.trim() || terminateMutation.isLoading}
          >
            {terminateMutation.isLoading ? <CircularProgress size={20} /> : 'إلغاء العقد نهائياً'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ProviderContractView;
