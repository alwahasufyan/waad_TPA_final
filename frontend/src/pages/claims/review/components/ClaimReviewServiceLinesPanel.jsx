import { useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  MedicalServices as ServiceIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  HelpOutline as ClarifyIcon,
  HelpOutline as HelpIcon,
  DoneAll as ApproveAllIcon
} from '@mui/icons-material';

import SectionCard from './SectionCard';
import { formatCurrency } from 'utils/formatters';

export const SERVICE_DECISION = {
  APPROVE: 'APPROVE',
  REJECT: 'REJECT',
  CLARIFY: 'CLARIFY'
};

export const REJECTION_REASONS = ['خدمة غير مغطاة', 'نقص مستندات', 'عدم مطابقة التشخيص', 'تجاوز حدود المنفعة', 'تكرار الخدمة'];

/**
 * Per-service-line review UI.
 *
 * CLAIM-REVIEW-SPLIT-2C: the APPROVE/REJECT/CLARIFY buttons persist a
 * structured decision per line to the server (PUT
 * /claims/{claimId}/lines/{lineId}/decision).
 *
 * DOCUMENTS-REVIEW-UX-1: REJECT now opens a full/partial dialog matching the
 * Batch entry screen's rejection UX — a partial rejection takes a real
 * amount (capped at the line's company share), which the backend now
 * genuinely uses to recompute that line's refusedAmount/companyShare (see
 * ClaimService.submitLineDecision) — this used to be purely cosmetic.
 * CLARIFY now also supports a free-text note (reviewerNotes) so the provider
 * knows exactly what's being asked, in addition to the canned reason.
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
  onReasonChange,
  onNotesChange,
  onApproveAll
}) => {
  const safeServices = Array.isArray(services) ? services : [];
  const [rejectDialogService, setRejectDialogService] = useState(null);
  const [rejectMode, setRejectMode] = useState('full');
  const [rejectAmountInput, setRejectAmountInput] = useState('');
  const [rejectReasonInput, setRejectReasonInput] = useState(REJECTION_REASONS[0]);

  const maxRejectable = rejectDialogService?.companyShareBeforeDiscount ?? rejectDialogService?.companyShare ?? 0;
  const parsedRejectAmount = parseFloat(rejectAmountInput);
  const rejectAmountValid =
    rejectMode === 'full' || (parsedRejectAmount > 0 && parsedRejectAmount <= maxRejectable + 0.001);

  const openRejectDialog = (service) => {
    const existing = serviceDecisions[service.serviceKey];
    const existingAmount = existing?.manualRefusedAmount;
    setRejectDialogService(service);
    setRejectMode(existingAmount > 0 ? 'partial' : 'full');
    setRejectAmountInput(existingAmount > 0 ? String(existingAmount) : '');
    setRejectReasonInput(existing?.reason || REJECTION_REASONS[0]);
  };

  const closeRejectDialog = () => setRejectDialogService(null);

  const confirmReject = () => {
    if (!rejectDialogService) return;
    const manualRefusedAmount = rejectMode === 'partial' ? parsedRejectAmount : undefined;
    onDecisionChange(rejectDialogService.serviceKey, SERVICE_DECISION.REJECT, {
      reason: rejectReasonInput,
      manualRefusedAmount
    });
    closeRejectDialog();
  };

  return (
    <SectionCard
      title="الخدمات المطلوبة"
      icon={ServiceIcon}
      headerExtra={
        <>
          <Chip
            size="small"
            color={selectedServicesCount > 0 ? 'success' : 'default'}
            variant="outlined"
            label={`محدد: ${selectedServicesCount} / ${safeServices.length}`}
          />
          {onApproveAll && (
            <Tooltip title="اعتماد جميع الخدمات دفعة واحدة">
              <span>
                <Button
                  size="small"
                  variant="outlined"
                  color="success"
                  startIcon={<ApproveAllIcon fontSize="small" />}
                  onClick={(e) => {
                    e.stopPropagation();
                    onApproveAll();
                  }}
                  disabled={reviewLock.locked || lineDecisionsLocked || submitting}
                >
                  اعتماد الكل
                </Button>
              </span>
            </Tooltip>
          )}
          <Tooltip title="قرارات المراجعة تُحفظ تلقائياً على الخادم فور اختيارها لكل خدمة. لا حاجة لحفظ يدوي على مستوى الخدمة.">
            <IconButton size="small">
              <HelpIcon fontSize="small" color="action" />
            </IconButton>
          </Tooltip>
        </>
      }
    >
      {safeServices.length > 0 ? (
        <Stack spacing={1.25}>
          <TableContainer sx={{ border: 1, borderColor: 'divider', borderRadius: '0.375rem' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.100' }}>
                  <TableCell sx={{ fontWeight: 700 }}>الخدمة</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>سقف المنفعة</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>الرصيد المتبقي</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    الكمية × السعر
                  </TableCell>
                  <TableCell align="center" sx={{ fontWeight: 700 }}>
                    التحمل %
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    الإجمالي
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    حصة المشترك
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    حصة الشركة
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    المرفوض
                  </TableCell>
                  <TableCell align="center" sx={{ fontWeight: 700 }}>
                    قرار المراجعة
                  </TableCell>
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
                      <Typography variant="body2" fontWeight={600} color={service.benefitLimit > 0 ? 'primary.main' : 'text.secondary'}>
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
                    <TableCell align="right" sx={{ py: 1 }}>
                      <Typography variant="body2" fontWeight={500}>
                        {service.quantity} × {formatCurrency(service.unitPrice)}
                      </Typography>
                    </TableCell>
                    <TableCell align="center" sx={{ py: 1 }}>
                      <Typography variant="body2" color="text.secondary">
                        {service.coveragePercent != null ? `${service.coveragePercent}%` : '—'}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ py: 1 }}>
                      <Typography variant="body2" fontWeight={600} color="primary">
                        {formatCurrency(service.totalAmount)}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ py: 1 }}>
                      <Typography variant="body2" fontWeight={500}>
                        {service.patientShare != null ? formatCurrency(service.patientShare) : '—'}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ py: 1 }}>
                      <Typography variant="body2" fontWeight={600} color="success.main">
                        {service.companyShare != null ? formatCurrency(service.companyShare) : '—'}
                      </Typography>
                      {service.providerDiscountAmount > 0 && (
                        <Typography variant="caption" display="block" color="text.secondary">
                          قبل خصم العقد: {formatCurrency(service.companyShareBeforeDiscount)} (خصم{' '}
                          {formatCurrency(service.providerDiscountAmount)})
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right" sx={{ py: 1 }}>
                      <Typography variant="body2" fontWeight={600} color={service.refusedAmount > 0 ? 'error.main' : 'text.secondary'}>
                        {service.refusedAmount != null ? formatCurrency(service.refusedAmount) : '—'}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ py: 1, minWidth: '12.5rem' }} onClick={(event) => event.stopPropagation()}>
                      {(() => {
                        const isSaving = savingServiceKey === service.serviceKey;
                        const disabled = reviewLock.locked || lineDecisionsLocked || submitting || isSaving;
                        const currentEntry = serviceDecisions[service.serviceKey];
                        const currentDecision = currentEntry?.decision;
                        const needsReason = currentDecision === SERVICE_DECISION.CLARIFY;
                        return (
                          <>
                            <Stack direction="row" spacing={0.5} justifyContent="center" sx={{ mb: needsReason ? 0.75 : 0 }}>
                              {isSaving ? (
                                <CircularProgress size={20} sx={{ mx: 1 }} />
                              ) : (
                                <>
                                  <Button
                                    size="small"
                                    variant={currentDecision === SERVICE_DECISION.APPROVE ? 'contained' : 'outlined'}
                                    color="success"
                                    startIcon={<ApproveIcon fontSize="small" />}
                                    onClick={() => onDecisionChange(service.serviceKey, SERVICE_DECISION.APPROVE)}
                                    disabled={disabled}
                                    sx={{ minWidth: 0, px: 1 }}
                                  >
                                    اعتماد
                                  </Button>
                                  <Button
                                    size="small"
                                    variant={currentDecision === SERVICE_DECISION.REJECT ? 'contained' : 'outlined'}
                                    color="error"
                                    startIcon={<RejectIcon fontSize="small" />}
                                    onClick={() => openRejectDialog(service)}
                                    disabled={disabled}
                                    sx={{ minWidth: 0, px: 1 }}
                                  >
                                    رفض
                                  </Button>
                                  <Button
                                    size="small"
                                    variant={currentDecision === SERVICE_DECISION.CLARIFY ? 'contained' : 'outlined'}
                                    color="warning"
                                    startIcon={<ClarifyIcon fontSize="small" />}
                                    onClick={() => onDecisionChange(service.serviceKey, SERVICE_DECISION.CLARIFY)}
                                    disabled={disabled}
                                    sx={{ minWidth: 0, px: 1 }}
                                  >
                                    استيضاح
                                  </Button>
                                </>
                              )}
                            </Stack>
                            {currentDecision === SERVICE_DECISION.REJECT && !isSaving && (
                              <Typography variant="caption" display="block" color="error.main" textAlign="center">
                                {currentEntry?.manualRefusedAmount > 0
                                  ? `رفض جزئي: ${formatCurrency(currentEntry.manualRefusedAmount)}`
                                  : 'رفض كلي'}
                                {currentEntry?.reason ? ` — ${currentEntry.reason}` : ''}
                              </Typography>
                            )}
                            {needsReason && !isSaving && (
                              <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                <TextField
                                  select
                                  size="small"
                                  fullWidth
                                  value={currentEntry?.reason || REJECTION_REASONS[0]}
                                  onChange={(event) => onReasonChange(service.serviceKey, event.target.value)}
                                  disabled={disabled}
                                >
                                  {REJECTION_REASONS.map((reason) => (
                                    <MenuItem key={reason} value={reason}>
                                      {reason}
                                    </MenuItem>
                                  ))}
                                </TextField>
                                <TextField
                                  size="small"
                                  fullWidth
                                  multiline
                                  minRows={2}
                                  placeholder="ملاحظة توضيحية لمقدم الخدمة (اختياري)..."
                                  defaultValue={currentEntry?.reviewerNotes || ''}
                                  onBlur={(event) => onNotesChange(service.serviceKey, event.target.value)}
                                  disabled={disabled}
                                />
                              </Stack>
                            )}
                          </>
                        );
                      })()}
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

      <Dialog open={!!rejectDialogService} onClose={closeRejectDialog} maxWidth="xs" fullWidth>
        <DialogTitle>رفض خدمة: {rejectDialogService?.serviceName}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <RadioGroup value={rejectMode} onChange={(e) => setRejectMode(e.target.value)}>
              <FormControlLabel value="full" control={<Radio size="small" color="error" />} label="رفض كلي (حصة الشركة كاملاً)" />
              <FormControlLabel value="partial" control={<Radio size="small" color="warning" />} label="رفض جزئي (مبلغ محدد)" />
            </RadioGroup>

            {rejectMode === 'partial' && (
              <TextField
                fullWidth
                size="small"
                type="number"
                label={`مبلغ الرفض من حصة الشركة (الحد الأقصى: ${formatCurrency(maxRejectable)})`}
                value={rejectAmountInput}
                onChange={(e) => setRejectAmountInput(e.target.value)}
                inputProps={{ min: 0.01, max: maxRejectable, step: 0.01 }}
                helperText="يُطبَّق على حصة الشركة فقط — حصة المستفيد لا تتأثر"
                error={parsedRejectAmount > maxRejectable}
                autoFocus
              />
            )}

            <TextField
              select
              fullWidth
              size="small"
              label="سبب الرفض"
              value={rejectReasonInput}
              onChange={(e) => setRejectReasonInput(e.target.value)}
            >
              {REJECTION_REASONS.map((reason) => (
                <MenuItem key={reason} value={reason}>
                  {reason}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeRejectDialog}>إلغاء</Button>
          <Button variant="contained" color="error" onClick={confirmReject} disabled={!rejectAmountValid}>
            تأكيد الرفض
          </Button>
        </DialogActions>
      </Dialog>
    </SectionCard>
  );
};

export default ClaimReviewServiceLinesPanel;
