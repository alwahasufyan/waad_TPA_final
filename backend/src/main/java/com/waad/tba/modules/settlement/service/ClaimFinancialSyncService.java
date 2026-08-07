package com.waad.tba.modules.settlement.service;

import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * خدمة المزامنة المالية للمطالبات — نقطة الدخول الوحيدة لتحديث حسابات المرفقين
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * الغرض: تمركز منطق تحديث الحسابات المالية في مكان واحد بدل تكراره
 * في عمليات الإضافة والحذف والاستعادة.
 *
 * كيف تعمل:
 * - تُستدعى من ClaimApprovalEventListener و ClaimReversalEventListener
 * - كلا المستمعَين يشتغلان بعد COMMIT مباشرة (بدون Async)
 * - REQUIRES_NEW يفتح transaction جديدة مستقلة لكل عملية
 *
 * عمليات:
 * creditForClaim() ← عند إضافة مطالبة معتمدة أو استعادتها
 * reverseForClaim() ← عند حذف مطالبة معتمدة
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimFinancialSyncService {

    private final ProviderAccountService providerAccountService;
    private final AccountTransactionService accountTransactionService;

    /**
     * إضافة قيد دائن لحساب مقدم الخدمة عند اعتماد مطالبة أو استعادتها.
     * يعمل في transaction مستقلة بعد commit المطالبة مباشرة (synchronous).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void creditForClaim(Long claimId, Long userId) {
        log.info("💰 [SYNC] creditForClaim: claimId={}, userId={}", claimId, userId);

        // WAAD-PROVIDER-CREDIT-INTEGRITY-1: pre-check idempotency BEFORE calling
        // creditOnClaimApproval. That method is @Transactional (default REQUIRED),
        // so it joins this method's own REQUIRES_NEW transaction — when it throws
        // on an already-credited claim, Spring marks that SHARED transaction
        // rollback-only as part of its own advice, regardless of whether the
        // exception is caught afterward here. The result was UnexpectedRollbackException
        // surfacing from this method's own commit on every retry, even though no
        // bad row was ever written. Checking first avoids ever entering that
        // poisoned-transaction path for the common, expected "retry/duplicate
        // event" case — the try/catch below remains as a safety net for the rare
        // case of two genuinely concurrent retries racing past this pre-check.
        long approvalCount = accountTransactionService.countForReference(ReferenceType.CLAIM_APPROVAL, claimId);
        long reversalCount = accountTransactionService.countForReference(ReferenceType.CLAIM_REVERSAL, claimId);
        if (approvalCount > reversalCount) {
            log.warn("⚠️ [SYNC] Claim {} already credited — skipping (idempotent)", claimId);
            return;
        }

        try {
            providerAccountService.creditOnClaimApproval(claimId, userId);
            log.info("✅ [SYNC] Provider account credited for claim {}", claimId);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Cannot credit twice")) {
                log.warn("⚠️ [SYNC] Claim {} already credited (concurrent request) — skipping (idempotent)", claimId);
            } else {
                log.error("❌ [SYNC] Failed to credit provider account for claim {}: {}", claimId, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * إضافة قيد مدين لحساب مقدم الخدمة عند حذف مطالبة معتمدة.
     * يعمل في transaction مستقلة بعد commit الحذف مباشرة (synchronous).
     *
     * ملاحظة مهمة: الحذف تمّ بالفعل (AFTER_COMMIT). إذا فشل عكس الرصيد
     * نسجّل تحذيراً للمراجعة اليدوية دون أن نوقف الحذف.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reverseForClaim(Long claimId, Long userId) {
        log.info("🔄 [SYNC] reverseForClaim: claimId={}, userId={}", claimId, userId);
        try {
            providerAccountService.debitOnClaimReversal(claimId, userId);
            log.info("✅ [SYNC] Provider account reversed for claim {}", claimId);
        } catch (IllegalStateException e) {
            // رصيد غير كافٍ أو حالة غير متوقعة — الحذف تمّ بالفعل، لا نُوقفه
            log.warn("⚠️ [SYNC] Could not reverse provider account for claim {} (claim already deleted): {}",
                    claimId, e.getMessage());
        } catch (Exception e) {
            // أي خطأ آخر — نسجّل ونتجاهل لأن الحذف لا يجب أن يُعاد
            log.error("🚨 [SYNC] Unexpected error reversing provider account for claim {}: {}",
                    claimId, e.getMessage(), e);
        }
    }
}
