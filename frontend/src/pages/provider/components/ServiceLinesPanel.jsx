import {
  Button,
  Alert,
  Stack,
  Typography,
  Chip,
  Divider,
  Box,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  Autocomplete,
  TextField,
  Tooltip,
  IconButton,
  Paper,
  alpha
} from '@mui/material';
import {
  Add as AddIcon,
  MedicalServices as MedicalServicesIcon,
  Description as DiagnosisIcon,
  Verified as ApprovalIcon,
  Warning as WarningIcon,
  Category as CategoryIcon,
  Lock as LockIcon,
  Healing as HealingIcon,
  Delete as DeleteIcon
} from '@mui/icons-material';
import { formatCurrency } from 'utils/currency-formatter';
import { MEDICAL_COLORS } from 'themes/provider-theme';
import { FormSection, SectionHeader, ContractPriceChip } from './ClaimSectionPrimitives';
import { LABELS } from '../constants';

/**
 * Row 2: service lines table (category → service → qty → price → total → remove),
 * plus the diagnosis/pre-auth context banner and category-violation warning.
 * Moved verbatim from the original ProviderClaimsSubmission.jsx (Stage 3A extraction).
 */
export function ServiceLinesPanel({
  formData,
  claimLines,
  medicalCategories,
  loadingCategories,
  availableServices,
  loadingServices,
  doesServiceMatchCategory,
  normalizeId,
  handleLineCategoryChange,
  handleServiceSelect,
  updateClaimLine,
  addClaimLine,
  removeClaimLine,
  handleOpenCustomServiceDialog,
  calculateLineTotal,
  totalClaimAmount,
  hasCategoryViolation,
  attemptedSubmit,
  submitting,
  success,
  isDark,
  tableHeaderBg,
  tableHeaderColor
}) {
  return (
    <FormSection highlighted>
      <SectionHeader
        icon={MedicalServicesIcon}
        title={LABELS.serviceLines}
        subtitle="اختر التصنيف أولاً ثم الخدمة الطبية لكل سطر"
        color="primary"
        action={
          <Button
            variant="contained"
            size="small"
            startIcon={<AddIcon />}
            onClick={addClaimLine}
            disabled={submitting || success}
            sx={{ borderRadius: '0.25rem' }}
          >
            {LABELS.addService}
          </Button>
        }
      />

      {/* 💡 Diagnosis Context Banner (New) */}
      {(formData.diagnosisCode || formData.preAuthorizationId) && (
        <Alert
          severity="info"
          icon={<DiagnosisIcon />}
          sx={{
            mb: '1.25rem',
            borderRadius: '0.25rem',
            border: '1px solid',
            borderColor: 'info.light',
            bgcolor: (theme) => alpha(theme.palette.info.main, 0.02),
            '& .MuiAlert-message': { width: '100%' }
          }}
        >
          <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
            <Typography variant="subtitle2" fontWeight={700} color="info.main">
              الارتباط الطبي الحالي:
            </Typography>
            {formData.diagnosisCode && (
              <Chip label={`التشخيص: ${formData.diagnosisCode}`} size="small" color="primary" variant="outlined" sx={{ fontWeight: 600 }} />
            )}
            {formData.preAuthorizationId && (
              <Chip
                icon={<ApprovalIcon style={{ fontSize: '1rem' }} />}
                label={`مرتبط بموافقة: #${formData.preAuthorizationId}`}
                size="small"
                color="success"
                sx={{ fontWeight: 600 }}
              />
            )}
            {formData.diagnosisDescription && (
              <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic', ml: 1 }}>
                "{formData.diagnosisDescription}"
              </Typography>
            )}
          </Stack>
        </Alert>
      )}

      <Divider sx={{ mb: '1.5rem' }} />

      {/* Category Violation Warning */}
      {hasCategoryViolation && claimLines.length > 0 && (
        <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: '1.0rem', borderRadius: '0.25rem' }}>
          ⚠️ يجب اختيار التصنيف الطبي لكل خدمة قبل اختيار الخدمة نفسها
        </Alert>
      )}

      {claimLines.length === 0 ? (
        <Box sx={{ minHeight: '0.375rem' }} />
      ) : (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: '0.25rem' }}>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: tableHeaderBg }}>
                <TableCell width="28%" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                  <Stack direction="row" alignItems="center" spacing={0.5}>
                    <Chip
                      label="1"
                      size="small"
                      sx={{
                        bgcolor: 'white',
                        color: MEDICAL_COLORS.primary.main,
                        width: '1.375rem',
                        height: '1.375rem',
                        fontSize: '0.7rem',
                        fontWeight: 700
                      }}
                    />
                    <span>{LABELS.selectCategory}</span>
                  </Stack>
                </TableCell>
                <TableCell width="28%" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                  <Stack direction="row" alignItems="center" spacing={0.5}>
                    <Chip
                      label="2"
                      size="small"
                      sx={{
                        bgcolor: 'rgba(255,255,255,0.3)',
                        color: 'white',
                        width: '1.375rem',
                        height: '1.375rem',
                        fontSize: '0.7rem',
                        fontWeight: 700
                      }}
                    />
                    <span>{LABELS.selectService}</span>
                  </Stack>
                </TableCell>
                <TableCell width="10%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                  الارتباط الطبي
                </TableCell>
                <TableCell width="8%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                  {LABELS.quantity}
                </TableCell>
                <TableCell width="12%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                  <Stack direction="row" alignItems="center" justifyContent="center" spacing={0.5}>
                    <LockIcon fontSize="small" />
                    <span>{LABELS.unitPrice}</span>
                  </Stack>
                </TableCell>
                <TableCell width="10%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                  {LABELS.totalPrice}
                </TableCell>
                <TableCell width="4%" align="center" sx={{ color: tableHeaderColor }}></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {claimLines.map((line) => (
                <TableRow key={line.id} sx={{ '&:hover': { bgcolor: 'action.hover' } }}>
                  {/* Category Selector (Step 1) */}
                  <TableCell>
                    <Autocomplete
                      size="small"
                      options={medicalCategories}
                      getOptionLabel={(option) => option?.name || ''}
                      value={medicalCategories.find((c) => normalizeId(c.id) === normalizeId(line.medicalCategoryId)) || null}
                      loading={loadingCategories}
                      onChange={(_, value) => handleLineCategoryChange(line.id, value)}
                      disabled={submitting || success}
                      isOptionEqualToValue={(option, value) => normalizeId(option?.id) === normalizeId(value?.id)}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          placeholder="اختر التصنيف أولاً..."
                          error={attemptedSubmit && !line.medicalCategoryId && claimLines.length > 0}
                          sx={{
                            '& .MuiOutlinedInput-root': {
                              bgcolor: line.medicalCategoryId
                                ? (theme) => alpha(theme.palette.success.main, 0.1)
                                : (theme) => alpha(theme.palette.warning.main, 0.1)
                            }
                          }}
                        />
                      )}
                      renderOption={(props, option) => {
                        const { key, ...otherProps } = props;
                        const serviceCount = availableServices.filter((s) => doesServiceMatchCategory(s, option)).length;
                        return (
                          <li key={key} {...otherProps}>
                            <Stack direction="row" alignItems="center" spacing={1} sx={{ width: '100%' }}>
                              <CategoryIcon fontSize="small" color="primary" />
                              <Typography variant="body2" fontWeight="medium">
                                {option.name}
                              </Typography>
                              <Chip label={`${serviceCount} خدمة`} size="small" color="primary" variant="outlined" />
                            </Stack>
                          </li>
                        );
                      }}
                    />
                  </TableCell>

                  {/* Service Selector (Step 2) */}
                  <TableCell>
                    <Autocomplete
                      size="small"
                      options={line.filteredServices || []}
                      getOptionDisabled={(option) => !!(option.requiresPreApproval || option.requiresPreAuth || option.requiresPA)}
                      getOptionLabel={(option) => {
                        const code = option.code ? `[${option.code}] ` : '';
                        return `${code}${option.name || ''}`;
                      }}
                      filterOptions={(options, { inputValue }) => {
                        const search = inputValue.toLowerCase();
                        return options.filter(
                          (opt) =>
                            (opt.code && opt.code.toLowerCase().includes(search)) || (opt.name && opt.name.toLowerCase().includes(search))
                        );
                      }}
                      value={(() => {
                        // PROVIDER-PORTAL-DATA-1 regression fix: most services are identified by
                        // pricingItemId now (medicalServiceId is legitimately null for them), so
                        // the lookup must try both identities — not medicalServiceId alone, which
                        // made a correctly-selected service render as an empty field forever
                        // (price/total stayed correct because those live on the line directly, only
                        // the Autocomplete's displayed value was broken).
                        const wantedPricingItemId = normalizeId(line.pricingItemId);
                        const wantedMedicalServiceId = normalizeId(line.medicalServiceId);
                        if (wantedPricingItemId == null && wantedMedicalServiceId == null) return null;
                        const pool = [...(line.filteredServices || []), ...availableServices];
                        return (
                          pool.find(
                            (s) =>
                              (wantedPricingItemId != null && normalizeId(s.pricingItemId) === wantedPricingItemId) ||
                              (wantedMedicalServiceId != null && normalizeId(s.medicalServiceId) === wantedMedicalServiceId)
                          ) ||
                          pool.find((s) => normalizeId(s.id) === (wantedPricingItemId ?? wantedMedicalServiceId)) ||
                          (line.serviceCode ? pool.find((s) => s.code === line.serviceCode) : null) ||
                          null
                        );
                      })()}
                      loading={loadingServices}
                      onChange={(_, value) => handleServiceSelect(line.id, value)}
                      disabled={submitting || success || !line.medicalCategoryId}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          placeholder={line.medicalCategoryId ? 'ابحث برمز الخدمة أو اسمها...' : '⚠️ اختر التصنيف أولاً'}
                          error={attemptedSubmit && line.medicalCategoryId && !line.medicalServiceId}
                          sx={{
                            '& .MuiOutlinedInput-root': {
                              bgcolor: !line.medicalCategoryId ? 'grey.100' : undefined
                            }
                          }}
                        />
                      )}
                      renderOption={(props, option) => {
                        const { key, ...otherProps } = props;
                        const requiresPA = option.requiresPreApproval || option.requiresPreAuth || option.requiresPA || false;

                        return (
                          <li key={key} {...otherProps}>
                            <Stack spacing={0.5} sx={{ width: '100%' }}>
                              <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                                <Chip
                                  label={option.code}
                                  size="small"
                                  color="primary"
                                  variant="outlined"
                                  sx={{ fontWeight: 700, fontFamily: 'monospace', fontSize: '0.75rem' }}
                                />
                                <Typography variant="body2" fontWeight="medium">
                                  {option.name}
                                </Typography>
                                {requiresPA && (
                                  <Tooltip title="يتم التحكم في هذه الخدمة عبر قواعد الوثيقة وتتطلب رقم موافقة مسبقة صالح">
                                    <Chip
                                      icon={<LockIcon sx={{ fontSize: '0.75rem !important' }} />}
                                      label="مقفلة - تتطلب موافقة مسبقة"
                                      size="small"
                                      color="error"
                                      variant="outlined"
                                      sx={{ fontSize: '0.75rem', height: '1.25rem' }}
                                    />
                                  </Tooltip>
                                )}
                              </Stack>
                              {option.price && (
                                <Typography variant="caption" color="success.main">
                                  💰 سعر العقد: {formatCurrency(option.price)}
                                </Typography>
                              )}
                            </Stack>
                          </li>
                        );
                      }}
                    />
                  </TableCell>

                  {/* 💡 Diagnosis Association column (New) */}
                  <TableCell align="center">
                    {line.requiresPA ? (
                      <Tooltip
                        title={
                          formData.preAuthorizationId
                            ? `مرتبطة بالموافقة رقم #${formData.preAuthorizationId}`
                            : 'هذه الخدمة تتطلب اختيار موافقة مسبقة'
                        }
                      >
                        <Chip
                          icon={<ApprovalIcon style={{ fontSize: '0.9rem' }} />}
                          label="عبر موافقة"
                          size="small"
                          color="success"
                          variant={formData.preAuthorizationId ? 'filled' : 'outlined'}
                          sx={{ fontWeight: 600 }}
                        />
                      </Tooltip>
                    ) : (
                      <Tooltip
                        title={formData.diagnosisCode ? `مرتبطة بالتشخيص: ${formData.diagnosisCode}` : 'سيتم ربطها بالتشخيص المكتوب أعلاه'}
                      >
                        <Chip
                          icon={<HealingIcon style={{ fontSize: '0.9rem' }} />}
                          label="تشخيص مباشر"
                          size="small"
                          color="info"
                          variant="outlined"
                          sx={{ fontWeight: 500, opacity: formData.diagnosisCode ? 1 : 0.6 }}
                        />
                      </Tooltip>
                    )}
                  </TableCell>

                  {/* Quantity */}
                  <TableCell align="center">
                    <TextField
                      type="number"
                      size="small"
                      value={line.quantity}
                      onChange={(e) => updateClaimLine(line.id, 'quantity', Math.max(1, parseInt(e.target.value) || 1))}
                      disabled={submitting || success}
                      inputProps={{ min: 1, style: { textAlign: 'center' } }}
                      sx={{ width: '4.375rem' }}
                    />
                  </TableCell>

                  {/* Unit Price */}
                  <TableCell align="center">
                    <ContractPriceChip
                      loading={line.loadingPrice}
                      price={line.unitPrice}
                      hasContract={line.hasContract}
                      error={line.priceError}
                    />
                  </TableCell>

                  {/* Line Total */}
                  <TableCell align="center">
                    <Typography fontWeight="bold" color="primary.main">
                      {formatCurrency(calculateLineTotal(line))}
                    </Typography>
                  </TableCell>

                  {/* Delete */}
                  <TableCell align="center">
                    <IconButton size="small" color="error" onClick={() => removeClaimLine(line.id)} disabled={submitting || success}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}

              {/* Total Row */}
              <TableRow sx={{ bgcolor: isDark ? alpha(MEDICAL_COLORS.primary.main, 0.15) : alpha(MEDICAL_COLORS.primary.main, 0.1) }}>
                <TableCell colSpan={5} align="left">
                  <Typography variant="h6" fontWeight={700}>
                    {LABELS.totalClaimAmount}
                  </Typography>
                </TableCell>
                <TableCell align="center">
                  <Typography variant="h5" color="primary.main" fontWeight={700}>
                    {formatCurrency(totalClaimAmount)}
                  </Typography>
                </TableCell>
                <TableCell />
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Moved outside the table (was cramped inside the service cell) — adds a service to
          the price list without needing to pre-select a row first. */}
      <Button
        size="small"
        onClick={() => handleOpenCustomServiceDialog(null)}
        disabled={submitting || success}
        sx={{ mt: '0.75rem', fontSize: '0.8rem', textTransform: 'none', fontWeight: 600 }}
      >
        + إضافة خدمة جديدة لقائمة الأسعار
      </Button>
    </FormSection>
  );
}
