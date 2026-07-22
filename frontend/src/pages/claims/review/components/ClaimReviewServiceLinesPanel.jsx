import {
  Alert,
  Box,
  Chip,
  IconButton,
  MenuItem,
  Stack,
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
  MedicalServices as ServiceIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  HelpOutline as ClarifyIcon
} from '@mui/icons-material';

import SectionCard from './SectionCard';
import { formatCurrency } from 'utils/formatters';

export const SERVICE_DECISION = {
  APPROVE: 'APPROVE',
  REJECT: 'REJECT',
  CLARIFY: 'CLARIFY'
};

export const REJECTION_REASONS = [
  'خدمة غير مغطاة',
  'نقص مستندات',
  'عدم مطابقة التشخيص',
  'تجاوز حدود المنفعة',
  'تكرار الخدمة'
];

/**
 * Per-service-line review UI.
 *
 * CLAIM-REVIEW-SPLIT-2A: the APPROVE/REJECT/CLARIFY buttons here are LOCAL STATE
 * ONLY — they feed the running total shown in ClaimReviewFinancialSummary and the
 * composed rejection reason sent to POST /claims/{id}/reject, but they are not
 * persisted as structured per-line decisions. That gap is closed in
 * CLAIM-REVIEW-SPLIT-2C. The banner and caption below make this explicit rather
 * than silently implying these choices are already saved.
 */
const ClaimReviewServiceLinesPanel = ({
  services,
  serviceDecisions,
  activeServiceKey,
  reviewLock,
  submitting,
  selectedServicesCount,
  onRowClick,
  onDecisionChange,
  onReasonChange
}) => {
  const safeServices = Array.isArray(services) ? services : [];

  return (
    <SectionCard title="الخدمات المطلوبة" icon={ServiceIcon}>
      {safeServices.length > 0 ? (
        <Stack spacing={1.25}>
          <Alert severity="info" sx={{ py: 0.75 }}>
            <Typography variant="caption">
              قرارات الموافقة/الرفض/الاستيضاح لكل خدمة أدناه <strong>مؤقتة ومحلية فقط</strong> ولم يتم حفظها على
              الخادم بعد — سيتم تفعيل الحفظ الفعلي لكل خدمة في مرحلة لاحقة.
            </Typography>
          </Alert>

          <Alert severity={selectedServicesCount > 0 ? 'success' : 'warning'} sx={{ py: 0.75 }}>
            <Typography variant="body2" fontWeight={600}>
              الخدمات المحددة للموافقة: {selectedServicesCount} من {safeServices.length}
            </Typography>
          </Alert>

          <TableContainer sx={{ border: 1, borderColor: 'divider', borderRadius: '0.375rem' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.100' }}>
                  <TableCell sx={{ fontWeight: 700 }}>الخدمة</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>سقف المنفعة</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>الرصيد المتبقي</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>الحالة السريعة (مؤقت)</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>الكمية × السعر</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>الإجمالي</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {safeServices.map((service, index) => (
                  <TableRow
                    key={service.serviceKey || index}
                    hover
                    selected={activeServiceKey === service.serviceKey}
                    onClick={() => onRowClick(service)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell sx={{ py: 1 }}>
                      <Box>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.25 }}>
                          <Typography variant="body2" fontWeight={600}>
                            {service.serviceName}
                          </Typography>
                          {!service.medicalServiceId && service.pricingItemId && (
                            <Chip
                              label="عقد مباشر"
                              size="small"
                              color="info"
                              variant="outlined"
                              sx={{ height: '1.125rem', fontSize: '0.75rem', fontWeight: 700 }}
                            />
                          )}
                        </Box>
                        <Typography variant="caption" color="text.secondary">
                          كود: {service.serviceCode}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell sx={{ py: 1 }}>
                      <Typography
                        variant="body2"
                        fontWeight={600}
                        color={service.benefitLimit > 0 ? 'primary.main' : 'text.secondary'}
                      >
                        {service.benefitLimit > 0 ? formatCurrency(service.benefitLimit) : '-'}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ py: 1 }}>
                      <Typography
                        variant="body2"
                        fontWeight={700}
                        color={service.remainingAmount > 0 ? 'success.main' : service.benefitLimit > 0 ? 'error.main' : 'text.secondary'}
                      >
                        {service.benefitLimit > 0 ? formatCurrency(service.remainingAmount ?? 0) : '-'}
                      </Typography>
                      {service.benefitLimit > 0 && (
                        <Typography variant="caption" display="block" color="text.secondary">
                          مستهلك: {formatCurrency(service.usedAmount ?? 0)}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell sx={{ py: 1 }} onClick={(event) => event.stopPropagation()}>
                      <Stack
                        direction="row"
                        spacing={0.5}
                        alignItems="center"
                        sx={{ mb: serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.REJECT ? 0.75 : 0 }}
                      >
                        <IconButton
                          size="small"
                          color={serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.APPROVE ? 'success' : 'default'}
                          onClick={() => onDecisionChange(service.serviceKey, SERVICE_DECISION.APPROVE)}
                          disabled={reviewLock.locked || submitting}
                        >
                          <ApproveIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          color={serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.REJECT ? 'error' : 'default'}
                          onClick={() => onDecisionChange(service.serviceKey, SERVICE_DECISION.REJECT)}
                          disabled={reviewLock.locked || submitting}
                        >
                          <RejectIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          color={serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.CLARIFY ? 'warning' : 'default'}
                          onClick={() => onDecisionChange(service.serviceKey, SERVICE_DECISION.CLARIFY)}
                          disabled={reviewLock.locked || submitting}
                        >
                          <ClarifyIcon fontSize="small" />
                        </IconButton>
                      </Stack>
                      {serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.REJECT && (
                        <TextField
                          select
                          size="small"
                          fullWidth
                          value={serviceDecisions[service.serviceKey]?.reason || REJECTION_REASONS[0]}
                          onChange={(event) => onReasonChange(service.serviceKey, event.target.value)}
                          disabled={reviewLock.locked || submitting}
                        >
                          {REJECTION_REASONS.map((reason) => (
                            <MenuItem key={reason} value={reason}>
                              {reason}
                            </MenuItem>
                          ))}
                        </TextField>
                      )}
                    </TableCell>
                    <TableCell align="right" sx={{ py: 1 }}>
                      <Typography variant="body2" fontWeight={500}>
                        {service.quantity} × {formatCurrency(service.unitPrice)}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ py: 1 }}>
                      <Typography variant="body2" fontWeight={600} color="primary">
                        {formatCurrency(service.totalAmount)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <Typography variant="caption" color="text.secondary">
            بالنقر على أي خدمة سيتم فتح المستند المرتبط بها تلقائياً عند توفر تطابق بالاسم أو الكود.
          </Typography>
        </Stack>
      ) : (
        <Typography variant="body2" color="text.secondary">
          لا توجد خدمات
        </Typography>
      )}
    </SectionCard>
  );
};

export default ClaimReviewServiceLinesPanel;
