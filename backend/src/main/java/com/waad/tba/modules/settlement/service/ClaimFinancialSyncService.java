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

        // WAAD-PROVIDER-CREDIT-INTEGRITY-1: fast-path pre-check before ever calling
        // creditOnClaimApproval, purely to avoid the cost of acquiring its pessimistic
        // locks for the common, expected "retry/duplicate event" case. This is an
        // optimization, not the correctness guarantee — creditOnClaimApproval's own
        // re-check under lock (closing the TOCTOU window a plain read like this one
        // can't) is what actually makes concurrent callers safe, and it now returns
        // null for "already credited" instead of throwing (see its own Javadoc for
        // why: throwing there used to poison this method's REQUIRES_NEW transaction
        // via Spring's rollback-only marking, even though nothing was ever
        // incorrectly written).
        long approvalCount = accountTransactionService.countForReference(ReferenceType.CLAIM_APPROVAL, claimId);
        long reversalCount = accountTransactionService.countForReference(ReferenceType.CLAIM_REVERSAL, claimId);
        if (approvalCount > reversalCount) {
            log.warn("⚠️ [SYNC] Claim {} already credited — skipping (idempotent)", claimId);
            return;
        }

        var transaction = providerAccountService.creditOnClaimApproval(claimId, userId);
        if (transaction == null) {
            log.warn("⚠️ [SYNC] Claim {} already credited (concurrent request) — skipping (idempotent)", claimId);
        } else {
            log.info("✅ [SYNC] Provider account credited for claim {}", claimId);
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
