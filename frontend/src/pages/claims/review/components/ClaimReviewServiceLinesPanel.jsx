import {
  Alert,
  Box,
  Chip,
  CircularProgress,
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
 * CLAIM-REVIEW-SPLIT-2C: the APPROVE/REJECT/CLARIFY buttons persist a
 * structured decision per line to the server (PUT
 * /claims/{claimId}/lines/{lineId}/decision) — reviewer intent/notes only,
 * never a change to claim-level financial totals (those only ever change via
 * POST /claims/{id}/approve). `savingServiceKey` disables/spins the buttons
 * for the one line currently being saved; `lineDecisionsLocked` disables all
 * of them when the claim's status doesn't allow line decisions (locked
 * separately from the general `reviewLock`, since the allowed status set for
 * line decisions is narrower — see ClaimReviewWorkspace).
 */
const ClaimReviewServiceLinesPanel = ({
  services,
  serviceDecisions,
  activeServiceKey,
  reviewLock,
  submitting,
  selectedServicesCount,
  savingServiceKey,
  lineDecisionsLocked,
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
            <Typography variant="caption">قرارات مراجعة الخدمات محفوظة على الخادم.</Typography>
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
                  <TableCell sx={{ fontWeight: 700 }}>قرار المراجعة</TableCell>
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
                      {(() => {
                        const isSaving = savingServiceKey === service.serviceKey;
                        const disabled = reviewLock.locked || lineDecisionsLocked || submitting || isSaving;
                        const currentDecision = serviceDecisions[service.serviceKey]?.decision;
                        const needsReason = currentDecision === SERVICE_DECISION.REJECT || currentDecision === SERVICE_DECISION.CLARIFY;
                        return (
                          <>
                            <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mb: needsReason ? 0.75 : 0 }}>
                              {isSaving ? (
                                <CircularProgress size={20} sx={{ mx: 1 }} />
                              ) : (
                                <>
                                  <IconButton
                                    size="small"
                                    color={currentDecision === SERVICE_DECISION.APPROVE ? 'success' : 'default'}
                                    onClick={() => onDecisionChange(service.serviceKey, SERVICE_DECISION.APPROVE)}
                                    disabled={disabled}
                                  >
                                    <ApproveIcon fontSize="small" />
                                  </IconButton>
                                  <IconButton
                                    size="small"
                                    color={currentDecision === SERVICE_DECISION.REJECT ? 'error' : 'default'}
                                    onClick={() => onDecisionChange(service.serviceKey, SERVICE_DECISION.REJECT)}
                                    disabled={disabled}
                                  >
                                    <RejectIcon fontSize="small" />
                                  </IconButton>
                                  <IconButton
                                    size="small"
                                    color={currentDecision === SERVICE_DECISION.CLARIFY ? 'warning' : 'default'}
                                    onClick={() => onDecisionChange(service.serviceKey, SERVICE_DECISION.CLARIFY)}
                                    disabled={disabled}
                                  >
                                    <ClarifyIcon fontSize="small" />
                                  </IconButton>
                                </>
                              )}
                            </Stack>
                            {needsReason && (
                              <TextField
                                select
                                size="small"
                                fullWidth
                                value={serviceDecisions[service.serviceKey]?.reason || REJECTION_REASONS[0]}
                                onChange={(event) => onReasonChange(service.serviceKey, event.target.value)}
                                disabled={disabled}
                              >
                                {REJECTION_REASONS.map((reason) => (
                                  <MenuItem key={reason} value={reason}>
                                    {reason}
                                  </MenuItem>
                                ))}
                              </TextField>
                            )}
                          </>
                        );
                      })()}
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
