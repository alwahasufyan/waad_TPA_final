import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Stack,
  Typography,
  Alert,
  TextField
} from '@mui/material';
import {
  ArrowBack,
  AssignmentTurnedIn as PreApprovalIcon,
  Receipt as ClaimIcon,
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import { usePreApprovalDetails } from 'hooks/usePreApprovals';
import preApprovalsService from 'services/api/pre-approvals.service';
import { FileUploader, AttachmentList } from 'components/upload';
import {
  uploadPreAuthAttachment,
  getPreAuthAttachments,
  downloadPreAuthAttachment,
  deletePreAuthAttachment
} from 'services/api/files.service';
// import MedicalDocumentSidePreview from 'components/medical/MedicalDocumentSidePreview';
// import DocumentSideViewer from 'components/documents/DocumentSideViewer';

// Insurance UX Components - Phase B2 Step 3
import {
  CardStatusBadge,
  PriorityBadge
} from 'components/insurance';

// Pre-Approval Status Mapping for CardStatusBadge
const PREAPPROVAL_STATUS_MAP = {
  PENDING: 'PENDING',
  UNDER_REVIEW: 'PENDING',
  APPROVAL_IN_PROGRESS: 'PENDING',
  NEEDS_CORRECTION: 'SUSPENDED',
  APPROVED: 'ACTIVE',
  ACKNOWLEDGED: 'ACTIVE',
  REJECTED: 'BLOCKED',
  EXPIRED: 'EXPIRED',
  CANCELLED: 'INACTIVE',
  USED: 'INACTIVE'
};

// Arabic labels for statuses
const STATUS_LABELS = {
  PENDING: 'قيد المراجعة',
  UNDER_REVIEW: 'قيد المراجعة الطبية',
  APPROVAL_IN_PROGRESS: 'جاري معالجة الموافقة',
  NEEDS_CORRECTION: 'تحتاج تصحيح',
  APPROVED: 'تمت الموافقة',
  ACKNOWLEDGED: 'تم الاطلاع',
  REJECTED: 'مرفوض',
  EXPIRED: 'منتهية الصلاحية',
  CANCELLED: 'ملغى',
  USED: 'مستخدمة'
};

// Helper Info Row Component
const InfoRow = ({ label, value, valueColor }) => (
  <Grid container spacing={2} sx={{ mb: '0.75rem' }}>
    <Grid size={{ xs: 12, sm: 4 }}>
      <Typography variant="subtitle2" color="text.secondary">
        {label}
      </Typography>
    </Grid>
    <Grid size={{ xs: 12, sm: 8 }}>
      <Typography variant="body1" color={valueColor || 'text.primary'}>
        {value ?? '-'}
      </Typography>
    </Grid>
  </Grid>
);

const PreApprovalView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { preApproval, loading, error } = usePreApprovalDetails(id);

  // Attachments state
  const [attachments, setAttachments] = useState([]);
  const [loadingAttachments, setLoadingAttachments] = useState(false);
  const [activeTab, setActiveTab] = useState('documents');
  const [decisionDialog, setDecisionDialog] = useState(null);
  const [decisionNotes, setDecisionNotes] = useState('');
  const [decisionLoading, setDecisionLoading] = useState(false);
  const [decisionMessage, setDecisionMessage] = useState(null);

  // Document side panel state (old)
  // Medical Document Side Preview (new)

  // Fetch attachments when preApproval loads
  const fetchAttachments = useCallback(async () => {
    if (!id) return;
    try {
      setLoadingAttachments(true);
      const result = await getPreAuthAttachments(id);
      setAttachments(result?.data || result || []);
    } catch (err) {
      console.error('Error fetching attachments:', err);
    } finally {
      setLoadingAttachments(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) {
      fetchAttachments();
    }
  }, [id, fetchAttachments]);

  // Upload success handler
  const handleUploadSuccess = async () => {
    await fetchAttachments();
  };

  // Download attachment
  const handleDownloadAttachment = async (attachmentId) => {
    try {
      const blob = await downloadPreAuthAttachment(id, attachmentId);
      const attachment = attachments.find((a) => a.id === attachmentId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = attachment?.fileName || attachment?.originalFileName || 'attachment';
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Error downloading attachment:', err);
    }
  };

  // Delete attachment
  const handleDeleteAttachment = async (attachmentId) => {
    try {
      await deletePreAuthAttachment(id, attachmentId);
      await fetchAttachments();
    } catch (err) {
      console.error('Error deleting attachment:', err);
    }
  };

  const handleDecision = async (decision) => {
    if (!preApproval?.id) return;
    if ((decision === 'reject' || decision === 'info') && !decisionNotes.trim()) return;
    try {
      setDecisionLoading(true);
      if (decision === 'approve') {
        await preApprovalsService.approve(preApproval.id, { approvalNotes: decisionNotes.trim() });
        setDecisionMessage('تم إرسال الموافقة للمعالجة');
      } else if (decision === 'reject') {
        await preApprovalsService.reject(preApproval.id, { rejectionReason: decisionNotes.trim() });
        setDecisionMessage('تم رفض طلب الموافقة');
      } else {
        await preApprovalsService.requestInfo(preApproval.id, decisionNotes.trim());
        setDecisionMessage('تم طلب استكمال المعلومات من مقدم الخدمة');
      }
      setDecisionDialog(null);
      setDecisionNotes('');
      window.setTimeout(handleBack, 700);
    } catch (err) {
      setDecisionMessage(err.userMessage || 'تعذر تنفيذ القرار');
    } finally {
      setDecisionLoading(false);
    }
  };

  const handleBack = () => {
    // Return to the reviewer queue. The old provider inbox route issues a
    // different API request and shows a misleading details-load error.
    navigate('/pre-approvals/review', { replace: true });
  };

  // Navigate to Provider Portal claims submission pre-filled with this pre-auth data
  const handleConvertToClaim = () => {
    if (!preApproval) return;
    navigate('/provider/claims/submit', {
      state: {
        fromPreAuth: true,
        preAuthorizationId: preApproval.id,
        preAuthNumber: preApproval.preAuthNumber,
        visitId: preApproval.visitId,
        memberId: preApproval.memberId,
        memberName: preApproval.memberName,
        memberCivilId: preApproval.memberNationalNumber,
        memberCardNumber: preApproval.memberCardNumber,
        employerName: preApproval.employerName,
        providerId: preApproval.providerId,
        providerName: preApproval.providerName,
        approvedAmount: preApproval.approvedAmount
      }
    });
  };

  if (loading) {
    return (
      <MainCard>
        <Box display="flex" justifyContent="center" alignItems="center" minHeight={400}>
          <CircularProgress />
        </Box>
      </MainCard>
    );
  }

  if (error) {
    return (
      <MainCard>
        <Alert severity="error">{error}</Alert>
      </MainCard>
    );
  }

  if (!preApproval) {
    return (
      <MainCard>
        <Alert severity="warning">لم يتم العثور على الطلب</Alert>
      </MainCard>
    );
  }

  return (
    <>
      <Box sx={{ bgcolor: 'grey.50', minHeight: '100vh', pb: 9, fontFamily: 'Tajawal, IBM Plex Sans Arabic, sans-serif' }} dir="rtl">
        <Box sx={{ maxWidth: '1400px', mx: 'auto', px: { xs: 1.5, md: 3 }, pt: 1.5 }}>
          <Card sx={{ mb: 1.5, border: '1px solid', borderColor: 'divider', boxShadow: 1 }}>
            <CardContent sx={{ p: { xs: 1.5, md: 2 } }}>
              <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', md: 'center' }} gap={1.5}>
                <Stack direction="row" alignItems="center" gap={1.25}>
                  <Box sx={{ bgcolor: 'primary.light', color: 'primary.dark', borderRadius: 1, p: 1, display: 'flex' }}><PreApprovalIcon /></Box>
                  <Box>
                    <Typography variant="h5" fontWeight={800}>طلب موافقة مسبقة {preApproval.referenceNumber || `PA-${preApproval.id}`}</Typography>
                    <Typography variant="body2" color="text.secondary">{preApproval.memberName || preApproval.member?.fullName || '-'} · {preApproval.providerName || '-'}</Typography>
                  </Box>
                </Stack>
                <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                  <CardStatusBadge status={PREAPPROVAL_STATUS_MAP[preApproval.status] ?? 'PENDING'} customLabel={STATUS_LABELS[preApproval.status] ?? preApproval.status} size="medium" variant="detailed" />
                  <PriorityBadge priority={preApproval.priority ?? 'ROUTINE'} size="medium" variant="chip" showResponseTime={false} language="ar" />
                  <Button variant="outlined" startIcon={<ArrowBack />} onClick={handleBack}>رجوع</Button>
                </Stack>
              </Stack>
              <Divider sx={{ my: 1.25 }} />
              <Stack direction="row" gap={2.5} flexWrap="wrap" color="text.secondary" sx={{ fontSize: 13 }}>
                <span><strong>تاريخ الطلب:</strong> {preApproval.createdAt ? new Date(preApproval.createdAt).toLocaleDateString('ar-LY') : '-'}</span>
                <span><strong>الخدمة:</strong> {preApproval.serviceName || preApproval.serviceCode || '-'}</span>
                <span><strong>التشخيص:</strong> {preApproval.diagnosisDescription || preApproval.diagnosisCode || '-'}</span>
              </Stack>
            </CardContent>
          </Card>

          <Stack direction="row" gap={1} sx={{ mb: 1.5, overflowX: 'auto' }}>
            {['معلقة', 'قيد المراجعة', 'موافق عليه'].map((label, index) => <Box key={label} sx={{ minWidth: 150, flex: 1, p: 1.25, bgcolor: index === 1 && !['APPROVED', 'REJECTED'].includes(preApproval.status) ? 'primary.light' : index === 2 && ['APPROVED', 'ACKNOWLEDGED'].includes(preApproval.status) ? 'success.lighter' : 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 1.5, textAlign: 'center', fontWeight: 700 }}>{label}</Box>)}
          </Stack>

          <Card sx={{ mb: 1.5, border: '1px solid', borderColor: 'divider', boxShadow: 1 }}>
            <CardContent sx={{ py: 1.25, px: 2 }}>
              <Stack direction="row" gap={2.5} flexWrap="wrap" alignItems="center" sx={{ fontSize: 13 }}>
                <span><strong>المؤمَّن عليه:</strong> {preApproval.memberName || preApproval.member?.fullName || '-'}</span>
                <span><strong>رقم البطاقة:</strong> {preApproval.memberCardNumber || '-'}</span>
                <span><strong>جهة العمل:</strong> {preApproval.member?.employerName || preApproval.employerName || '-'}</span>
                <span><strong>مقدم الخدمة:</strong> {preApproval.providerName || '-'}</span>
                <span><strong>الخدمة:</strong> {preApproval.serviceName || preApproval.serviceCode || '-'}</span>
                <span><strong>التشخيص:</strong> {preApproval.diagnosisDescription || preApproval.diagnosisCode || '-'}</span>
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ mb: 1.5, border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ p: 0 }}><Box sx={{ px: 2, py: 1.25, bgcolor: 'primary.lighter', borderBottom: '1px solid', borderColor: 'divider' }}><Typography variant="h6" fontWeight={800}>تفاصيل الموافقة والخدمة المطلوبة</Typography></Box><Box sx={{ overflowX: 'auto' }}><table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}><thead><tr style={{ background: '#e8f3f4' }}>{['الخدمة', 'التشخيص', 'الكمية', 'الجلسات المطلوبة', 'الجلسات المعتمدة', 'قرار الطلب'].map((head) => <th key={head} style={{ padding: 12, textAlign: 'right', borderBottom: '2px solid #397f86' }}>{head}</th>)}</tr></thead><tbody><tr><td style={{ padding: 14, fontWeight: 700 }}>{preApproval.serviceName || preApproval.serviceCode || '-'}</td><td style={{ padding: 14 }}>{preApproval.diagnosisDescription || preApproval.diagnosisCode || '-'}</td><td style={{ padding: 14 }}>{preApproval.quantity ?? 1}</td><td style={{ padding: 14 }}>{preApproval.requestedSessions ?? 0}</td><td style={{ padding: 14, color: '#2e9b52', fontWeight: 700 }}>{preApproval.approvedSessions ?? 0}</td><td style={{ padding: 14 }}><CardStatusBadge status={PREAPPROVAL_STATUS_MAP[preApproval.status] ?? 'PENDING'} customLabel={STATUS_LABELS[preApproval.status] ?? preApproval.status} size="small" variant="detailed" /></td></tr></tbody></table></Box></CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider' }}>
            <Stack direction="row" sx={{ borderBottom: '1px solid', borderColor: 'divider', overflowX: 'auto' }}>{[['documents', `المرفقات والمستندات (${attachments.length})`], ['financial', 'المعلومات المالية والموافقة'], ['audit', 'التدقيق'], ['action', 'الإجراء']].map(([key, label]) => <Button key={key} onClick={() => setActiveTab(key)} sx={{ minWidth: 150, borderRadius: 0, borderBottom: activeTab === key ? 3 : 0, borderColor: 'primary.main', color: activeTab === key ? 'primary.main' : 'text.secondary', fontWeight: activeTab === key ? 800 : 500 }}>{label}</Button>)}</Stack>
            <CardContent sx={{ minHeight: 180 }}>
              {activeTab === 'documents' && <><Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}><Typography variant="h6" fontWeight={800}>المرفقات والمستندات</Typography><Typography variant="caption" color="text.secondary">يمكن رفع التقارير والفحوصات والأشعة</Typography></Stack>{preApproval.status !== 'APPROVED' && preApproval.status !== 'REJECTED' && preApproval.status !== 'CANCELLED' && <Box sx={{ mb: 2 }}><FileUploader uploadFn={async (file, attachmentType) => uploadPreAuthAttachment(id, file, attachmentType)} attachmentTypes={[{ value: 'MEDICAL_REPORT', label: 'تقرير طبي' }, { value: 'LAB_RESULT', label: 'نتائج مختبر' }, { value: 'RADIOLOGY', label: 'أشعة / تصوير' }, { value: 'PRESCRIPTION', label: 'وصفة طبية' }, { value: 'REFERRAL', label: 'تحويل طبي' }, { value: 'OTHER', label: 'مستند آخر' }]} onUploadSuccess={handleUploadSuccess} maxSize={10 * 1024 * 1024} accept="application/pdf,image/jpeg,image/png" label="رفع مستند داعم" /></Box>}{loadingAttachments ? <Box display="flex" justifyContent="center" py={3}><CircularProgress size={24} /></Box> : <AttachmentList attachments={attachments} onDownload={handleDownloadAttachment} onDelete={handleDeleteAttachment} canDelete={!['APPROVED', 'REJECTED', 'CANCELLED'].includes(preApproval.status)} emptyMessage="لا توجد مستندات مرفقة بهذا الطلب" />}</>}
              {activeTab === 'financial' && <><Typography variant="h6" fontWeight={800} gutterBottom>المعلومات المالية والموافقة</Typography><Alert severity="info">هذه الموافقة إدارية وطبية فقط ولا تترتب عليها التزامات مالية في هذه المرحلة.</Alert>{preApproval.reviewerComment && <Box sx={{ mt: 1.5 }}><Typography variant="caption" color="text.secondary">تعليق المراجع</Typography><Typography sx={{ whiteSpace: 'pre-wrap' }}>{preApproval.reviewerComment}</Typography></Box>}</>}
              {activeTab === 'audit' && <><Typography variant="h6" fontWeight={800} gutterBottom>معلومات التدقيق</Typography><Grid container spacing={1}><Grid item xs={12} md={6}><InfoRow label="تاريخ الإنشاء" value={preApproval.createdAt ? new Date(preApproval.createdAt).toLocaleString('ar-LY') : '-'} /></Grid><Grid item xs={12} md={6}><InfoRow label="تاريخ آخر تحديث" value={preApproval.updatedAt ? new Date(preApproval.updatedAt).toLocaleString('ar-LY') : '-'} /></Grid><Grid item xs={12} md={6}><InfoRow label="أنشئ بواسطة" value={preApproval.createdBy || '-'} /></Grid><Grid item xs={12} md={6}><InfoRow label="آخر تحديث بواسطة" value={preApproval.updatedBy || '-'} /></Grid></Grid></>}
              {activeTab === 'action' && <>{preApproval.status === 'APPROVED' || preApproval.status === 'ACKNOWLEDGED' ? <Stack direction="row" justifyContent="space-between" alignItems="center" gap={2}><Typography color="text.secondary">يمكن تحويل الموافقة المعتمدة إلى مطالبة عند تقديم الخدمة.</Typography><Button variant="contained" startIcon={<ClaimIcon />} onClick={handleConvertToClaim}>تحويل إلى مطالبة</Button></Stack> : <Typography color="text.secondary">سيظهر التحويل إلى مطالبة بعد اعتماد الموافقة بالكامل.</Typography>}</>}
            </CardContent>
          </Card>
        </Box>
      </Box>

      {decisionMessage && <Alert severity={decisionMessage.startsWith('تعذر') ? 'error' : 'success'} onClose={() => setDecisionMessage(null)} sx={{ position: 'fixed', bottom: 82, right: 24, zIndex: 1400, boxShadow: 3 }}>{decisionMessage}</Alert>}
      {!['APPROVED', 'ACKNOWLEDGED', 'REJECTED', 'CANCELLED', 'USED'].includes(preApproval.status) && <Box sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1200, bgcolor: 'background.paper', borderTop: '1px solid', borderColor: 'divider', boxShadow: 6, px: { xs: 1.5, md: 4 }, py: 1 }}><Stack direction="row" justifyContent="flex-start" gap={1} flexWrap="wrap"><Button variant="contained" color="success" onClick={() => handleDecision('approve')} disabled={decisionLoading}>اعتماد</Button><Button variant="outlined" color="error" onClick={() => { setDecisionDialog('reject'); setDecisionNotes(''); }} disabled={decisionLoading}>رفض</Button><Button variant="outlined" color="warning" onClick={() => { setDecisionDialog('info'); setDecisionNotes(''); }} disabled={decisionLoading}>طلب إيضاح</Button></Stack></Box>}
      <Dialog open={!!decisionDialog} onClose={() => !decisionLoading && setDecisionDialog(null)} fullWidth maxWidth="sm" dir="rtl"><DialogTitle>{decisionDialog === 'reject' ? 'رفض الموافقة المسبقة' : 'طلب إيضاح من مقدم الخدمة'}</DialogTitle><DialogContent><TextField autoFocus fullWidth multiline minRows={4} label={decisionDialog === 'reject' ? 'سبب الرفض' : 'المعلومات المطلوبة'} value={decisionNotes} onChange={(event) => setDecisionNotes(event.target.value)} required sx={{ mt: 1 }} /></DialogContent><DialogActions><Button onClick={() => setDecisionDialog(null)} disabled={decisionLoading}>إلغاء</Button><Button variant="contained" color={decisionDialog === 'reject' ? 'error' : 'warning'} onClick={() => handleDecision(decisionDialog)} disabled={decisionLoading || !decisionNotes.trim()}>{decisionLoading ? 'جارٍ التنفيذ...' : 'تأكيد'}</Button></DialogActions></Dialog>
    </>
  );
};

export default PreApprovalView;
