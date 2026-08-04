import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  IconButton,
  Tooltip,
  Alert,
  Card,
  CardContent,
  Typography,
  Grid,
  Stack,
  Divider,
  Table,
  TableHead,
  TableBody,
  TableContainer,
  TablePagination,
  TableRow,
  TableCell,
  CircularProgress,
  Tabs,
  Tab
} from '@mui/material';
import {
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Visibility as ViewIcon,
  Refresh as RefreshIcon,
  Assignment as PreApprovalIcon,
  MedicalServices as MedicalIcon,
  PlayArrow as StartReviewIcon,
  AssignmentReturn as RequestInfoIcon,
  TaskAlt as TaskAltIcon
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import { DataGrid } from '@mui/x-data-grid';
import { ModernPageHeader } from 'components/tba';
import { preApprovalsService } from 'services/api';

/**
 * Pre-Approvals Inbox - صندوق الموافقات المسبقة
 *
 * يعرض طلبات الموافقة المسبقة المعلقة (SUBMITTED/UNDER_REVIEW) ويتيح الموافقة أو الرفض
 */
const PreApprovalsInbox = () => {
  const navigate = useNavigate();

  // State
  const [preApprovals, setPreApprovals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalRows, setTotalRows] = useState(0);

  // WAAD-PREAUTH-REVIEWER-HISTORY-1: reviewer inbox view filter — 'ACTIVE'
  // is the original pending/under-review queue; 'APPROVED'/'REJECTED' let
  // the reviewer look up previously-actioned requests, which otherwise only
  // ever appeared in this inbox while still pending and had no reference
  // afterward.
  const [statusFilter, setStatusFilter] = useState('ACTIVE');

  // Dialog states
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [requestInfoDialogOpen, setRequestInfoDialogOpen] = useState(false);
  const [selectedPreApproval, setSelectedPreApproval] = useState(null);

  // Form states
  const [approvedAmount, setApprovedAmount] = useState('');
  const [approvalNotes, setApprovalNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [requestInfoNotes, setRequestInfoNotes] = useState('');

  // Error/Success states
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Fetch pre-approvals for the active view (pending queue, or processed history)
  const fetchPreApprovals = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response =
        statusFilter === 'ACTIVE'
          ? await preApprovalsService.getPending({
              page: page + 1,
              size: pageSize,
              sortBy: 'createdAt',
              sortDir: 'asc' // FIFO - الأقدم أولاً
            })
          : await preApprovalsService.getByStatus(statusFilter, {
              page,
              size: pageSize,
              sortBy: 'updatedAt',
              sortDir: 'desc' // الأحدث أولاً
            });
      setPreApprovals(response.items || []);
      setTotalRows(response.total || 0);
    } catch (err) {
      console.error('Error fetching pre-approvals:', err);
      setError(err.userMessage || err.response?.data?.message || 'فشل في تحميل طلبات الموافقة المسبقة');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter]);

  useEffect(() => {
    fetchPreApprovals();
  }, [fetchPreApprovals]);

  // Reset to page 1 whenever the view changes (pending queue vs. a processed-status history)
  const handleStatusFilterChange = (_event, newFilter) => {
    setStatusFilter(newFilter);
    setPage(0);
  };

  // Open approve dialog
  const handleOpenApprove = (preApproval) => {
    setSelectedPreApproval(preApproval);
    setApprovedAmount(preApproval.requestedAmount?.toString() || '');
    setApprovalNotes('');
    setApproveDialogOpen(true);
  };

  // Open reject dialog
  const handleOpenReject = (preApproval) => {
    setSelectedPreApproval(preApproval);
    setRejectionReason('');
    setRejectDialogOpen(true);
  };

  // Open request-info dialog (PREAUTH-REVIEW-WORKFLOW-1)
  const handleOpenRequestInfo = (preApproval) => {
    setSelectedPreApproval(preApproval);
    setRequestInfoNotes('');
    setRequestInfoDialogOpen(true);
  };

  // Start Review - transition from SUBMITTED to UNDER_REVIEW
  const handleStartReview = async (preApproval) => {
    try {
      setActionLoading(true);
      setError(null);
      await preApprovalsService.startReview(preApproval.id);
      setSuccess('تم بدء مراجعة الطلب');
      fetchPreApprovals();
    } catch (err) {
      setError(err.userMessage || err.response?.data?.message || 'فشل في بدء المراجعة');
    } finally {
      setActionLoading(false);
    }
  };

  // Approve pre-approval
  const handleApprove = async () => {
    if (!selectedPreApproval) return;

    try {
      setActionLoading(true);
      setError(null);

      // Backend calculates approvedAmount automatically - DO NOT send it
      // Only send approval notes
      await preApprovalsService.approve(selectedPreApproval.id, {
        approvalNotes: approvalNotes || '' // Backend expects 'approvalNotes', not 'notes'
      });

      setApproveDialogOpen(false);
      setSelectedPreApproval(null);
      setSuccess('جاري معالجة الموافقة...');

      // Phase 2: Poll for final status
      const pollInterval = setInterval(async () => {
        try {
          const updated = await preApprovalsService.getById(selectedPreApproval.id);

          if (updated.status === 'APPROVED') {
            clearInterval(pollInterval);
            clearTimeout(pollTimeout);
            setActionLoading(false);
            setSuccess('تمت الموافقة على الطلب بنجاح');
            fetchPreApprovals();
          } else if (updated.status === 'REJECTED') {
            clearInterval(pollInterval);
            clearTimeout(pollTimeout);
            setActionLoading(false);
            setError('تم رفض الطلب: ' + (updated.rejectionReason || 'خطأ في المعالجة'));
            fetchPreApprovals();
          }
          // If still APPROVAL_IN_PROGRESS, continue polling
        } catch (pollError) {
          clearInterval(pollInterval);
          clearTimeout(pollTimeout);
          setActionLoading(false);
          setError('خطأ في التحقق من حالة الموافقة');
        }
      }, 3000); // Poll every 3 seconds

      // Timeout after 2 minutes
      const pollTimeout = setTimeout(() => {
        clearInterval(pollInterval);
        if (actionLoading) {
          setActionLoading(false);
          setSuccess('انتهت مهلة المعالجة. يرجى تحديث الصفحة.');
          fetchPreApprovals();
        }
      }, 120000);
    } catch (err) {
      console.error('Approve error:', err);
      setError(err.userMessage || err.response?.data?.message || 'فشل في الموافقة على الطلب');
      setActionLoading(false);
    }
  };

  // Reject pre-approval
  const handleReject = async () => {
    if (!selectedPreApproval || !rejectionReason.trim()) {
      setError('يجب إدخال سبب الرفض');
      return;
    }

    try {
      setActionLoading(true);
      setError(null);
      await preApprovalsService.reject(selectedPreApproval.id, {
        rejectionReason: rejectionReason.trim()
      });

      setSuccess('تم رفض الطلب');
      setRejectDialogOpen(false);
      fetchPreApprovals();
    } catch (err) {
      setError(err.userMessage || err.response?.data?.message || 'فشل في رفض الطلب');
    } finally {
      setActionLoading(false);
    }
  };

  // Request correction from the provider (PENDING/UNDER_REVIEW → NEEDS_CORRECTION)
  const handleRequestInfo = async () => {
    if (!selectedPreApproval || !requestInfoNotes.trim()) {
      setError('يجب توضيح المعلومات المطلوبة');
      return;
    }

    try {
      setActionLoading(true);
      setError(null);
      await preApprovalsService.requestInfo(selectedPreApproval.id, requestInfoNotes.trim());

      setSuccess('تم إرجاع الطلب إلى مقدم الخدمة لاستكمال المعلومات');
      setRequestInfoDialogOpen(false);
      fetchPreApprovals();
    } catch (err) {
      setError(err.userMessage || err.response?.data?.message || 'فشل في طلب استكمال البيانات');
    } finally {
      setActionLoading(false);
    }
  };

  // Status chip (using exact Backend enum values) - CANONICAL 2026-01-26
  // PreAuth workflow: PENDING → UNDER_REVIEW → APPROVED/REJECTED
  const getStatusChip = (status) => {
    const configs = {
      PENDING: { color: 'warning', label: 'معلق' },
      UNDER_REVIEW: { color: 'info', label: 'قيد المراجعة' },
      NEEDS_CORRECTION: { color: 'warning', label: 'بحاجة لاستكمال بيانات' },
      APPROVAL_IN_PROGRESS: { color: 'info', label: 'جارِ الاعتماد' },
      APPROVED: { color: 'success', label: 'موافق عليه' },
      // WAAD-PREAUTH-MULTI-LINE-1 (Phase 3): reached when a multi-line
      // request's lines have a mix of approved/rejected decisions.
      PARTIALLY_APPROVED: { color: 'warning', label: 'موافقة جزئية' },
      REJECTED: { color: 'error', label: 'مرفوض' },
      EXPIRED: { color: 'default', label: 'منتهي' },
      CANCELLED: { color: 'default', label: 'ملغي' },
      USED: { color: 'info', label: 'مستخدم' }
    };
    const config = configs[status] || configs.PENDING;
    return <Chip size="small" color={config.color} label={config.label} />;
  };

  // WAAD-PREAUTH-MULTI-LINE-1 (Phase 3): a multi-line request has no single
  // "the service" — show a count instead, unchanged for the (still most
  // common, and every legacy) single-line case.
  const getServiceDisplay = (row) => {
    const lines = row?.lines;
    if (Array.isArray(lines) && lines.length > 1) {
      return `${lines.length} خدمات`;
    }
    return row?.serviceName || row?.serviceCode || '-';
  };

  // Priority badge (using exact Backend enum values)
  const getUrgencyBadge = (priority) => {
    if (priority === 'EMERGENCY') {
      return <Chip size="small" color="error" label="طارئ" variant="filled" />;
    }
    if (priority === 'URGENT') {
      return <Chip size="small" color="warning" label="عاجل" variant="outlined" />;
    }
    if (priority === 'ROUTINE') {
      return <Chip size="small" color="default" label="عادي" variant="outlined" />;
    }
    return null;
  };

  // DataGrid columns (CANONICAL - follows Backend DTO exactly)
  const columns = [
    {
      field: 'id',
      headerName: '#',
      width: 150,
      valueGetter: (value, row) => row.referenceNumber || `-`
    },
    {
      field: 'memberName',
      headerName: 'اسم المؤمن عليه',
      flex: 1,
      minWidth: 150,
      valueGetter: (value, row) => row.memberName || '-'
    },
    {
      field: 'providerName',
      headerName: 'مقدم الخدمة',
      flex: 1,
      minWidth: '9.375rem',
      valueGetter: (value, row) => row.providerName || '-'
    },
    {
      field: 'serviceName',
      headerName: 'الخدمة',
      width: 160,
      valueGetter: (value, row) => getServiceDisplay(row)
    },
    {
      field: 'priority',
      headerName: 'الأولوية',
      width: 110,
      renderCell: (params) => getUrgencyBadge(params.row.priority)
    },
    {
      field: 'requestDate',
      headerName: 'تاريخ الطلب',
      width: 130,
      valueGetter: (value, row) => {
        return row.requestDate ? new Date(row.requestDate).toLocaleDateString('en-US') : '-';
      }
    },
    {
      field: 'expiryDate',
      headerName: 'تاريخ الانتهاء',
      width: 130,
      valueGetter: (value, row) => {
        const date = row?.expiryDate || row?.expiresAt;
        return date ? new Date(date).toLocaleDateString('en-US') : '-';
      }
    },
    {
      field: 'status',
      headerName: 'الحالة',
      width: 130,
      renderCell: (params) => getStatusChip(params.value)
    },
    {
      field: 'actions',
      headerName: 'الإجراءات',
      width: 190,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          <Tooltip title="عرض التفاصيل">
            <IconButton size="small" color="primary" onClick={() => navigate(`/pre-approvals/${params.row.id}`)} disabled={actionLoading}>
              <ViewIcon fontSize="small" />
            </IconButton>
          </Tooltip>

          {/* PENDING → Start Review (transition to UNDER_REVIEW)
              CANONICAL 2026-01-26: PreAuth workflow starts at PENDING, not SUBMITTED
              PENDING means newly created and awaiting initial review */}
          {params.row.status === 'PENDING' && (
            
              <Tooltip title="بدء المراجعة">
                <span>
                  <IconButton size="small" color="info" onClick={() => handleStartReview(params.row)} disabled={actionLoading}>
                    <StartReviewIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              
          )}

          {/* PENDING/UNDER_REVIEW → Approve/Reject/Request Correction
              CANONICAL: all three states allow approval/rejection/request-info actions */}
          {(params.row.status === 'PENDING' || params.row.status === 'UNDER_REVIEW') && (
            <>
              <Tooltip title="موافقة">
                <span>
                  <IconButton size="small" color="success" onClick={() => handleOpenApprove(params.row)} disabled={actionLoading}>
                    <ApproveIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              <Tooltip title="رفض">
                <span>
                  <IconButton size="small" color="error" onClick={() => handleOpenReject(params.row)} disabled={actionLoading}>
                    <RejectIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              <Tooltip title="طلب استكمال بيانات">
                <span>
                  <IconButton size="small" color="warning" onClick={() => handleOpenRequestInfo(params.row)} disabled={actionLoading}>
                    <RequestInfoIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            </>
          )}
        </Stack>
      )
    }
  ];

  return (
    <>
      <ModernPageHeader
        title="صندوق الموافقات المسبقة"
        subtitle={statusFilter === 'ACTIVE' ? 'طلبات الموافقة المسبقة المعلقة' : 'سجل الطلبات التي تمت معالجتها سابقًا'}
        icon={PreApprovalIcon}
        actions={
          <Button startIcon={<RefreshIcon />} onClick={fetchPreApprovals} disabled={loading}>
            تحديث
          </Button>
        }
      />

      <Tabs value={statusFilter} onChange={handleStatusFilterChange} sx={{ mb: '1.0rem' }}>
        <Tab value="ACTIVE" label="قيد المراجعة" />
        <Tab value="APPROVED" label="تمت الموافقة عليها" />
        <Tab value="REJECTED" label="مرفوضة" />
      </Tabs>

      {error && (
        <Alert severity="error" sx={{ mb: '1.0rem' }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: '1.0rem' }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      <Grid container spacing={2} sx={{ mb: '1.0rem' }}>
        {(statusFilter === 'ACTIVE'
          ? [
              { label: 'إجمالي الطلبات', value: totalRows, icon: <PreApprovalIcon />, color: 'primary' },
              { label: 'قيد المراجعة', value: preApprovals.filter((row) => row.status === 'UNDER_REVIEW').length, icon: <MedicalIcon />, color: 'info' },
              { label: 'معلّقة', value: preApprovals.filter((row) => row.status === 'PENDING').length, icon: <StartReviewIcon />, color: 'warning' },
              { label: 'عاجلة', value: preApprovals.filter((row) => row.priority === 'URGENT' || row.priority === 'EMERGENCY').length, icon: <ApproveIcon />, color: 'error' }
            ]
          : [
              { label: 'إجمالي السجلات', value: totalRows, icon: <PreApprovalIcon />, color: 'primary' },
              {
                label: 'تمت الموافقة عليها',
                value: preApprovals.filter((row) => row.status === 'APPROVED').length,
                icon: <ApproveIcon />,
                color: 'success'
              },
              { label: 'مرفوضة', value: preApprovals.filter((row) => row.status === 'REJECTED').length, icon: <RejectIcon />, color: 'error' }
            ]
        ).map((stat) => (
          <Grid key={stat.label} size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined" sx={{ borderRadius: '0.75rem', height: '100%' }}>
              <CardContent sx={{ p: '1rem', '&:last-child': { pb: '1rem' } }}>
                <Stack direction="row" alignItems="center" justifyContent="space-between">
                  <Typography variant="caption" color="text.secondary">{stat.label}</Typography>
                  <Box sx={{ width: 36, height: 36, borderRadius: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: `${stat.color}.lighter`, color: `${stat.color}.main` }}>{stat.icon}</Box>
                </Stack>
                <Typography variant="h4" fontWeight={700} sx={{ mt: 1 }}>{stat.value}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <MainCard>
        <Box sx={{ minHeight: '25.0rem', width: '100%' }}>
          <TableContainer sx={{ maxHeight: 620, border: 1, borderColor: 'divider', borderRadius: '0.5rem', mb: 1 }}>
            <Table stickyHeader size="small">
              <TableHead>
                <TableRow>
                  {['#', 'اسم المؤمن عليه', 'مقدم الخدمة', 'الخدمة', 'الأولوية', 'تاريخ الطلب', 'الحالة', 'الإجراءات'].map((header) => (
                    <TableCell key={header} sx={{ bgcolor: 'primary.lighter', fontWeight: 700, whiteSpace: 'nowrap' }}>{header}</TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow><TableCell colSpan={8} align="center"><CircularProgress sx={{ my: 4 }} /></TableCell></TableRow>
                ) : preApprovals.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center" sx={{ py: 5, color: 'text.secondary' }}>
                      {statusFilter === 'ACTIVE' ? 'لا توجد طلبات موافقة مسبقة معلقة' : 'لا توجد سجلات في هذا التصنيف'}
                    </TableCell>
                  </TableRow>
                ) : preApprovals.map((row) => (
                  <TableRow key={`review-${row.id}`} hover>
                    <TableCell sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>{row.referenceNumber || `PA-${row.id}`}</TableCell>
                    <TableCell>{row.memberName || row.memberFullNameArabic || '-'}</TableCell>
                    <TableCell>{row.providerName || '-'}</TableCell>
                    <TableCell>{getServiceDisplay(row)}</TableCell>
                    <TableCell>{getUrgencyBadge(row.priority) || '-'}</TableCell>
                    <TableCell>{row.requestDate ? new Date(row.requestDate).toLocaleDateString('en-GB') : '-'}</TableCell>
                    <TableCell>{getStatusChip(row.status)}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.25}>
                        <Tooltip title="عرض التفاصيل"><IconButton size="small" color="primary" onClick={() => navigate(`/pre-approvals/${row.id}`)} disabled={actionLoading}><ViewIcon fontSize="small" /></IconButton></Tooltip>
                        {(row.status === 'PENDING' || row.status === 'UNDER_REVIEW') && (
                          Array.isArray(row.lines) && row.lines.length > 1 ? (
                            // WAAD-PREAUTH-MULTI-LINE-1 (Phase 3): the whole-header
                            // approve/reject/request-info actions only work for
                            // single-line requests (enforced server-side too) — a
                            // multi-service request must be decided per line from
                            // the detail page.
                            <Tooltip title="طلب متعدد الخدمات — افتح التفاصيل لاتخاذ قرار كل خدمة">
                              <span>
                                <IconButton size="small" color="info" onClick={() => navigate(`/pre-approvals/${row.id}`)} disabled={actionLoading}>
                                  <TaskAltIcon fontSize="small" />
                                </IconButton>
                              </span>
                            </Tooltip>
                          ) : (
                            <>
                              <Tooltip title="موافقة"><IconButton size="small" color="success" onClick={() => handleOpenApprove(row)} disabled={actionLoading}><ApproveIcon fontSize="small" /></IconButton></Tooltip>
                              <Tooltip title="رفض"><IconButton size="small" color="error" onClick={() => handleOpenReject(row)} disabled={actionLoading}><RejectIcon fontSize="small" /></IconButton></Tooltip>
                              <Tooltip title="طلب استكمال"><IconButton size="small" color="warning" onClick={() => handleOpenRequestInfo(row)} disabled={actionLoading}><RequestInfoIcon fontSize="small" /></IconButton></Tooltip>
                            </>
                          )
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination component="div" count={totalRows} page={page} rowsPerPage={pageSize} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => { setPage(0); setPageSize(Number(event.target.value)); }} rowsPerPageOptions={[10, 20, 50]} labelRowsPerPage="صفوف لكل صفحة" labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count}`} />
          <DataGrid
            autoHeight
            rows={preApprovals}
            getRowId={(row) => row.id}
            columns={columns}
            loading={loading}
            paginationMode="server"
            rowCount={totalRows}
            paginationModel={{ page, pageSize }}
            onPaginationModelChange={(model) => {
              setPage(model.page);
              setPageSize(model.pageSize);
            }}
            pageSizeOptions={[10, 20, 50]}
            disableSelectionOnClick
            localeText={{
              noRowsLabel: 'لا توجد طلبات موافقة مسبقة معلقة',
              MuiTablePagination: {
                labelRowsPerPage: 'عدد الصفوف:'
              }
            }}
            sx={{
              display: 'none',
              '& .MuiDataGrid-row': {
                '&:hover': {
                  backgroundColor: 'action.hover'
                }
              }
            }}
          />
        </Box>
      </MainCard>

      {/* Approve Dialog */}
      <Dialog open={approveDialogOpen} onClose={() => !actionLoading && setApproveDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <ApproveIcon color="success" />
            <span>الموافقة على الطلب #{selectedPreApproval?.id}</span>
          </Stack>
        </DialogTitle>
        <DialogContent>
          <Card variant="outlined" sx={{ mb: '1.5rem', mt: '1.0rem' }}>
            <CardContent>
              <Typography variant="subtitle2" color="primary" gutterBottom>
                تفاصيل الطلب
              </Typography>
              <Table size="small">
                <TableBody>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 500 }}>المؤمن عليه</TableCell>
                    <TableCell>{selectedPreApproval?.memberFullNameArabic || selectedPreApproval?.memberName || selectedPreApproval?.member?.fullName || '-'}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 500 }}>مقدم الخدمة</TableCell>
                    <TableCell>{selectedPreApproval?.providerName}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 500 }}>نوع الخدمة</TableCell>
                    <TableCell>{selectedPreApproval?.serviceType || selectedPreApproval?.procedureName || selectedPreApproval?.serviceName || selectedPreApproval?.serviceCode || '-'}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <Divider sx={{ my: '1.0rem' }} />


          <TextField
            fullWidth
            label="ملاحظات (اختياري)"
            value={approvalNotes}
            onChange={(e) => setApprovalNotes(e.target.value)}
            multiline
            rows={2}
            disabled={actionLoading}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setApproveDialogOpen(false)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="success"
            onClick={handleApprove}
            disabled={actionLoading}
            startIcon={actionLoading ? <CircularProgress size={20} color="inherit" /> : <ApproveIcon />}
          >
            {actionLoading ? 'جارِ الموافقة...' : 'موافقة'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reject Dialog */}
      <Dialog open={rejectDialogOpen} onClose={() => !actionLoading && setRejectDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <RejectIcon color="error" />
            <span>رفض الطلب #{selectedPreApproval?.id}</span>
          </Stack>
        </DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: '1.0rem', mt: 1 }}>
            يرجى إدخال سبب واضح للرفض. هذا السبب سيظهر للمستشفى/العيادة.
          </Alert>

          <TextField
            fullWidth
            required
            label="سبب الرفض"
            value={rejectionReason}
            onChange={(e) => setRejectionReason(e.target.value)}
            multiline
            rows={3}
            error={!rejectionReason.trim()}
            helperText="مطلوب - اشرح سبب الرفض بوضوح"
            disabled={actionLoading}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleReject}
            disabled={!rejectionReason.trim() || actionLoading}
            startIcon={actionLoading ? <CircularProgress size={20} color="inherit" /> : <RejectIcon />}
          >
            {actionLoading ? 'جارِ الرفض...' : 'تأكيد الرفض'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Request Info Dialog (PREAUTH-REVIEW-WORKFLOW-1) */}
      <Dialog open={requestInfoDialogOpen} onClose={() => !actionLoading && setRequestInfoDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <RequestInfoIcon color="warning" />
            <span>طلب استكمال بيانات للطلب #{selectedPreApproval?.id}</span>
          </Stack>
        </DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: '1.0rem', mt: 1 }}>
            سيتم إرجاع الطلب إلى مقدم الخدمة بحالة &quot;بحاجة لاستكمال بيانات&quot; مع ملاحظتك، ويمكنه تعديل الطلب وإعادة تقديمه.
          </Alert>

          <TextField
            fullWidth
            required
            label="المعلومات المطلوبة"
            value={requestInfoNotes}
            onChange={(e) => setRequestInfoNotes(e.target.value)}
            multiline
            rows={3}
            error={!requestInfoNotes.trim()}
            helperText="مطلوب - وضّح ما هي المعلومات أو المستندات الناقصة (بحد أقصى 1000 حرف)"
            inputProps={{ maxLength: 1000 }}
            disabled={actionLoading}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRequestInfoDialogOpen(false)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="warning"
            onClick={handleRequestInfo}
            disabled={!requestInfoNotes.trim() || actionLoading}
            startIcon={actionLoading ? <CircularProgress size={20} color="inherit" /> : <RequestInfoIcon />}
          >
            {actionLoading ? 'جارِ الإرسال...' : 'إرسال الطلب'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default PreApprovalsInbox;
