import { useState, useMemo } from 'react';
import { Box, Typography, LinearProgress, Alert, Stack, Chip } from '@mui/material';
import SuccessDialog from 'components/SuccessDialog';
import { Receipt as ReceiptIcon } from '@mui/icons-material';

import { useProviderClaimSubmission } from './hooks/useProviderClaimSubmission';
import { BlockedAccessPage } from './components/BlockedAccessPage';
import { CustomServiceDialog } from './components/CustomServiceDialog';
import { ServiceLinesPanel } from './components/ServiceLinesPanel';
import { ClinicalDataPanel } from './components/ClinicalDataPanel';
import { AttachmentsPanel } from './components/AttachmentsPanel';
import { ClaimConversationPanel } from './components/ClaimConversationPanel';
import { ClaimSummaryPanel } from './components/ClaimSummaryPanel';
import { MemberContextPanel } from './components/MemberContextPanel';
import { ClaimStepTabs } from './components/ClaimStepTabs';
import { ClaimReviewStep } from './components/ClaimReviewStep';
import { ClaimWorkspaceFooter } from './components/ClaimWorkspaceFooter';
import { LABELS } from './constants';

// Phase 3B revision 2 (owner feedback, 2026-07-20): steps 1+2 merged into one
// (member/visit context already lives permanently in the side panel, so a
// dedicated "بيانات المطالبة" step showing the same read-only chips again was
// pure duplication) — 4 steps instead of 5.
const WORKSPACE_STEPS = ['بيانات المطالبة والخدمات الطبية', 'البيانات السريرية', 'المرفقات', 'المراجعة والإرسال'];

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║       PROVIDER CLAIMS SUBMISSION - Visit-Centric Canonical Architecture      ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  REBUILD: 2026-01-16                                                         ║
 * ║  REDESIGNED: 2026-01-29 - Desktop-First Professional UX                      ║
 * ║  STAGE 3A: 2026-07-20 — behavior-preserving extraction into hook + panels.   ║
 * ║  STAGE 3B: 2026-07-20 — full-viewport workspace, real steps, reserved footer ║
 * ║  STAGE 3B REV.2: 2026-07-20 — merged context+services into one step, single ║
 * ║  side column (member context + compact coverage stacked) instead of three   ║
 * ║  columns, freeing full width for the services work area.                    ║
 * ║  (PROVIDER-PORTAL-UX-1 Phase 3B). Business logic unchanged from Stage 3A.   ║
 * ║  ARCHITECTURAL LAWS ENFORCED:                                                ║
 * ║  ❌ No claim without Visit (visitId is MANDATORY)                            ║
 * ║  ❌ No free-text service (must select from dropdown)                         ║
 * ║  ❌ No manual price entry (price comes from Provider Contract)               ║
 * ║  ✅ Data Flow: Visit → Member → Contract → Services → Prices → Claim         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
export default function ProviderClaimsSubmission() {
  const s = useProviderClaimSubmission();
  const [activeStep, setActiveStep] = useState(0);

  const completedSteps = useMemo(
    () => [s.hasServicesReady, s.hasVisitAndDiagnosis, s.hasAttachmentsReady, s.isFormValid && s.hasVisitAndDiagnosis],
    [s.hasServicesReady, s.hasVisitAndDiagnosis, s.hasAttachmentsReady, s.isFormValid]
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER - BLOCKED ACCESS
  // ═══════════════════════════════════════════════════════════════════════════
  if (s.accessBlocked) {
    return <BlockedAccessPage onBack={s.handleBack} />;
  }

  const isFirstStep = activeStep === 0;
  const isLastStep = activeStep === WORKSPACE_STEPS.length - 1;
  const goPrev = () => setActiveStep((prev) => Math.max(0, prev - 1));
  const goNext = () => setActiveStep((prev) => Math.min(WORKSPACE_STEPS.length - 1, prev + 1));

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER - WORKSPACE (Phase 3B rev.2: full-viewport, two-column, real steps)
  // ═══════════════════════════════════════════════════════════════════════════
  return (
    <Box sx={{ height: '100%', minHeight: 0, display: 'flex', flexDirection: 'column', bgcolor: '#F9FAFB' }}>
      {/* ═══════════════════════ COMPACT HEADER ═══════════════════════ */}
      <Box
        sx={{
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          px: '1.25rem',
          py: '0.65rem',
          borderBottom: '1px solid',
          borderColor: 'divider',
          bgcolor: 'common.white'
        }}
      >
        <Stack direction="row" spacing={1.25} alignItems="center">
          <ReceiptIcon color="primary" fontSize="small" />
          <Typography variant="subtitle1" fontWeight={700}>
            {LABELS.pageTitle}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {LABELS.pageSubtitle}
          </Typography>
        </Stack>
        {s.activeClaimId && <Chip size="small" variant="outlined" label={`مسودة رقم #${s.activeClaimId}`} />}
      </Box>

      {/* ═══════════════════════ STEP NAVIGATION ═══════════════════════ */}
      <Box sx={{ flexShrink: 0, px: '1.25rem', py: '0.5rem', borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'common.white' }}>
        <ClaimStepTabs steps={WORKSPACE_STEPS} activeStep={activeStep} completedSteps={completedSteps} onStepChange={setActiveStep} />
      </Box>

      {/* ═══════════════════════ LOADING / ERROR ═══════════════════════ */}
      {s.loading && <LinearProgress sx={{ flexShrink: 0 }} />}
      {s.error && (
        <Alert severity="error" sx={{ flexShrink: 0, borderRadius: 0 }} onClose={() => s.setError(null)}>
          {s.error}
        </Alert>
      )}

      {/* Success Dialog */}
      <SuccessDialog
        open={!!s.success}
        type="claim"
        title={s.submitMode === 'draft' ? 'تم حفظ المطالبة كمسودة بنجاح' : 'تم تقديم المطالبة بنجاح! 🎉'}
        subtitle={
          s.submitMode === 'draft' ? 'يمكنك إكمال التعديلات ثم التقديم النهائي لاحقاً' : 'تم إرسال المطالبة للمراجعة من قبل فريق التأمين'
        }
        referenceNumber={s.success?.referenceNumber || s.success?.claimId}
        attachmentsCount={s.success?.attachmentsCount || 0}
        redirectPath="/provider/visits"
        redirectLabel="العودة لسجل الزيارات"
        countdownSeconds={5}
        viewDetailsPath={s.success?.claimId ? `/provider/claims/submit?claimId=${s.success.claimId}` : null}
        additionalInfo={[
          { label: 'المؤمَّن عليه', value: s.linkedMemberName || '—' },
          { label: 'عدد الخدمات', value: `${s.claimLines.length} خدمة` }
        ]}
      />

      {/* Custom Service Pricing Addition Dialog */}
      <CustomServiceDialog
        open={s.customServiceDialogOpen}
        onClose={s.handleCloseCustomServiceDialog}
        medicalCategories={s.medicalCategories}
        normalizeId={s.normalizeId}
        customServiceData={s.customServiceData}
        onDataChange={s.handleCustomServiceDataChange}
        customServiceError={s.customServiceError}
        onSetError={s.setError}
        addingCustomService={s.addingCustomService}
        onSubmit={s.handleSubmitCustomService}
      />

      {/* ═══════════════════════ TWO-COLUMN WORKSPACE ═══════════════════════ */}
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', overflow: 'hidden' }}>
        {/* Side column: member/visit context, then compact coverage summary below it —
            one persistent column instead of two, so the work area gets the rest of the width. */}
        <Box sx={{ width: { xs: 0, md: '19rem' }, flexShrink: 0, display: { xs: 'none', md: 'block' }, overflowY: 'auto', p: '1rem' }}>
          <Stack spacing={1.5}>
            <MemberContextPanel
              linkedMemberName={s.linkedMemberName}
              linkedMemberCivilId={s.linkedMemberCivilId}
              linkedMemberCardNumber={s.linkedMemberCardNumber}
              linkedVisitId={s.linkedVisitId}
              linkedVisitDate={s.linkedVisitDate}
              visitDetails={s.visitDetails}
              linkedVisitType={s.linkedVisitType}
              linkedProviderName={s.linkedProviderName}
              userProviderName={s.userProviderName}
            />
            <ClaimSummaryPanel
              memberLimit={s.memberLimit}
              totalClaimAmount={s.totalClaimAmount}
              hasVisitAndDiagnosis={s.hasVisitAndDiagnosis}
              hasServicesReady={s.hasServicesReady}
              compact
            />
          </Stack>
        </Box>

        {/* Work area — the ONLY region that scrolls, now the full remaining width */}
        <Box sx={{ flex: 1, minWidth: 0, overflowY: 'auto', p: '1rem' }}>
          <Stack spacing={2}>
            {activeStep === 0 && (
              <ServiceLinesPanel
                formData={s.formData}
                claimLines={s.claimLines}
                medicalCategories={s.medicalCategories}
                loadingCategories={s.loadingCategories}
                availableServices={s.availableServices}
                loadingServices={s.loadingServices}
                doesServiceMatchCategory={s.doesServiceMatchCategory}
                normalizeId={s.normalizeId}
                handleLineCategoryChange={s.handleLineCategoryChange}
                handleServiceSelect={s.handleServiceSelect}
                updateClaimLine={s.updateClaimLine}
                addClaimLine={s.addClaimLine}
                removeClaimLine={s.removeClaimLine}
                handleOpenCustomServiceDialog={s.handleOpenCustomServiceDialog}
                calculateLineTotal={s.calculateLineTotal}
                totalClaimAmount={s.totalClaimAmount}
                hasCategoryViolation={s.hasCategoryViolation}
                attemptedSubmit={s.attemptedSubmit}
                submitting={s.submitting}
                success={s.success}
                isDark={s.isDark}
                tableHeaderBg={s.tableHeaderBg}
                tableHeaderColor={s.tableHeaderColor}
              />
            )}

            {activeStep === 1 && (
              <ClinicalDataPanel
                formData={s.formData}
                handleFormChange={s.handleFormChange}
                attemptedSubmit={s.attemptedSubmit}
                submitting={s.submitting}
                success={s.success}
              />
            )}

            {activeStep === 2 && (
              <>
                <AttachmentsPanel
                  attemptedSubmit={s.attemptedSubmit}
                  hasAttachmentsReady={s.hasAttachmentsReady}
                  submitting={s.submitting}
                  success={s.success}
                  handleFileSelect={s.handleFileSelect}
                  existingAttachments={s.existingAttachments}
                  claimId={s.activeClaimId}
                  handleDeleteExistingAttachment={s.handleDeleteExistingAttachment}
                  pendingFiles={s.pendingFiles}
                  handleFileTypeChange={s.handleFileTypeChange}
                  handleRemoveFile={s.handleRemoveFile}
                  uploading={s.uploading}
                  uploadProgress={s.uploadProgress}
                />
                <ClaimConversationPanel
                  providerChatMessages={s.providerChatMessages}
                  providerChatInput={s.providerChatInput}
                  setProviderChatInput={s.setProviderChatInput}
                  handleSendProviderChatMessage={s.handleSendProviderChatMessage}
                />
              </>
            )}

            {activeStep === 3 && (
              <ClaimReviewStep
                formData={s.formData}
                claimLines={s.claimLines}
                calculateLineTotal={s.calculateLineTotal}
                totalClaimAmount={s.totalClaimAmount}
                pendingFiles={s.pendingFiles}
                existingAttachments={s.existingAttachments}
                isFormValid={s.isFormValid}
              />
            )}
          </Stack>
        </Box>
      </Box>

      {/* ═══════════════════════ RESERVED-HEIGHT FOOTER ═══════════════════════ */}
      <ClaimWorkspaceFooter
        isFirstStep={isFirstStep}
        isLastStep={isLastStep}
        onPrev={goPrev}
        onNext={goNext}
        autosaveStatus={s.autosaveStatus}
        autosaveAt={s.autosaveAt}
        submitting={s.submitting}
        submitMode={s.submitMode}
        success={s.success}
        claimLines={s.claimLines}
        handleSubmit={s.handleSubmit}
        handleBack={s.handleBack}
      />
    </Box>
  );
}
