# PROVIDER-PORTAL-WIP-RECOVERY — Restore Plan

**Status: plan only. Nothing in this plan has been applied. No production file has been modified. Not committed. Not pushed.**

This plan describes how to safely bring the content preserved in `.recovery/provider-portal-2026-07-20/` (see the Extraction Map) into the real working tree, once you approve — split by risk level, in the order that avoids re-losing anything and avoids clobbering this session's own uncommitted Claims Review work.

---

## 0. Guiding rule

**Nothing here should be applied by overwriting a file wholesale if that file has *also* been changed by unrelated work since the recovered snapshot was taken.** Two of the three backend files fall into that category (see §2). The rest are safe direct copies because nothing else has touched them.

---

## 1. Safe direct restores (no conflict — copy as-is)

These files do not exist in the current working tree at all, or exist only as the pre-recovery legacy version with zero overlap from this session's other work. Copying them from `.recovery/` to their production path is a plain add/replace, nothing to merge:

| File | Action |
|---|---|
| `frontend/src/pages/provider/ProviderClaimsSubmission.jsx` | **Replace.** Current file is the old 2,557-line monolith; recovered file is the Phase 3B recomposed workspace. Nothing else this session touched this file. |
| `frontend/src/pages/provider/claim-submission/hooks/useProviderClaimSubmission.js` | **Add** (new file/directory, doesn't exist yet). |
| `frontend/src/pages/provider/claim-submission/components/ServiceLinesPanel.jsx` | **Add.** |
| `frontend/src/pages/provider/claim-submission/components/ClaimReviewStep.jsx` | **Add.** |
| `frontend/src/pages/provider/claim-submission/components/ClaimSectionPrimitives.jsx` | **Add.** |
| `frontend/src/pages/provider/claim-submission/components/ClaimStepTabs.jsx` | **Add.** |
| `frontend/src/pages/provider/claim-submission/components/ClaimSummaryPanel.jsx` | **Add.** |
| `frontend/src/pages/provider/claim-submission/components/ClaimWorkspaceFooter.jsx` | **Add.** |
| `frontend/src/pages/provider/claim-submission/components/MemberContextPanel.jsx` | **Add.** |
| `backend/src/test/java/com/waad/tba/modules/claim/mapper/ClaimMapperPricingContractTest.java` | **Add** (new test file, no existing file to conflict with). |
| `backend/src/test/java/com/waad/tba/modules/claim/service/ClaimServiceProviderStatusTest.java` | **Add** (new test file). |
| `backend/src/main/java/com/waad/tba/modules/visit/service/VisitService.java` | **Apply the one-line diff** (see §2c — trivial, zero conflict, but listed as a diff not a wholesale copy purely because it's a live production file with 488 lines and only 1 should change). |

**Blocking issue for this group (must resolve first):** §3 of the Extraction Map — `ClaimContextHeader.jsx`, `ClinicalDataPanel.jsx`, `AttachmentsPanel.jsx`, `ClaimConversationPanel.jsx` are imported by the recovered `ProviderClaimsSubmission.jsx` but do not exist anywhere. **Restoring the workspace file as-is will not compile/build** until these four are either recovered (a further, deeper dangling-object search) or rebuilt fresh. This must be resolved — or explicitly deferred with a stub/placeholder plan — before this group is applied, not discovered after the fact via a broken build.

## 2. Conflicted restores — require a manual, small merge, not a file copy

### 2a. `backend/src/main/java/com/waad/tba/modules/claim/mapper/ClaimMapper.java`

**Not a real conflict in practice — the diff is small and additive.** Diffed against the current file with `diff --strip-trailing-cr` (removes false CRLF/LF noise): the only real difference is exactly the DATA-1 fix, isolated to one method:

```diff
+import com.waad.tba.common.exception.BusinessRuleException;
 ...
+                        boolean isFreeTextAllowed = "GEN-MEDICATION".equals(codeToLookup)
+                                        || "GEN-MEDICAL-SERVICE".equals(codeToLookup);
+                        if (!isFreeTextAllowed && resolvedUnitPrice == null) {
+                                throw new BusinessRuleException(
+                                                "Claim line has no valid contracted pricing source (no resolvable service code or pricing item)",
+                                                "تعذر استخدام هذه الخدمة لأن ربطها بسعر العقد غير مكتمل. يرجى مراجعة مسؤول العقود أو اختيار خدمة أخرى.");
+                        }
+
+                        BigDecimal amountBasis = resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice;
                         Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
-                        BigDecimal lineRequestedTotal = enteredUnitPrice.multiply(BigDecimal.valueOf(quantity));
+                        BigDecimal lineRequestedTotal = amountBasis.multiply(BigDecimal.valueOf(quantity));
```

Confirmed: nothing else in this session (CLAIM-NUMBERING-1, CLAIM-REVIEW-SPLIT-2A) touched `ClaimMapper.java` at all — `git status` shows it as untouched. **This one can actually be applied as a small patch directly on the current file with no real merge decision needed** — it's flagged as "conflicted" only in the sense that a full-file overwrite would be wrong (it would silently discard nothing here, actually, since nothing else changed this file — but treat every full-file overwrite as suspect by default, verify, don't assume).

### 2b. `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java` — **the one real conflict**

The recovered blob **predates** both `CLAIM-NUMBERING-1` and `CLAIM-REVIEW-SECURITY-1` (both already in the current file, one of them merged via PR #11, the other this session's own uncommitted work). A full-file overwrite would silently delete:
- `claimReferenceService` field + its call in `createClaim` (CLAIM-NUMBERING-1, uncommitted, this session)
- `reviewerIsolationService.validateReviewerAccess(...)` calls added in `returnForInfo` and `getClaimByReference` (CLAIM-REVIEW-SECURITY-1, already merged to `main` as `5fba4bc`)
- The `getClaimByReference` method itself (CLAIM-NUMBERING-1, uncommitted)

**Required action: apply only the isolated STATUS-1 diff onto the current file — never copy the recovered blob over it.** The exact, self-contained patch (confirmed via diff, matches the STATUS-1 report verbatim):

```diff
  validateCreateDto(dto);
  validateAndEnforceProviderId(dto, currentUser);
+ enforceProviderClaimCreationStatus(dto, currentUser);
```

plus, as a new method (placed directly after `validateAndEnforceProviderId`, matching the existing file's own convention):

```java
void enforceProviderClaimCreationStatus(ClaimCreateDto dto, User currentUser) {
    if (currentUser == null || !authorizationService.isProvider(currentUser)) {
        return;
    }
    if (dto.getStatus() != null && dto.getStatus() != ClaimStatus.DRAFT) {
        log.warn("🚨 PROVIDER_STATUS_OVERRIDE: Provider user {} requested status={} on claim creation "
                + "but it was enforced to DRAFT (potential security issue)", currentUser.getUsername(), dto.getStatus());
    }
    dto.setStatus(ClaimStatus.DRAFT);
}
```

This is a two-line insertion + one small new method — small, mechanical, and independently testable via the recovered `ClaimServiceProviderStatusTest.java` (§1). **Recommended timing: apply this only after CLAIM-NUMBERING-1 is committed** (per your own standing instruction), so this patch lands on a stable, committed base rather than getting tangled with another uncommitted diff in the same method.

### 2c. `backend/src/main/java/com/waad/tba/modules/visit/service/VisitService.java` (VISIT-BUG-1)

Zero conflict — confirmed via `diff --strip-trailing-cr`, the **only** difference between the recovered blob and the current file is:

```diff
-    @Transactional(readOnly = true)
+    @Transactional
     public VisitResponseDto findById(Long id) {
```

Nothing else in this session touched `VisitService.java`. This can be applied immediately, independent of everything else in this plan, as it fixes a currently-live, reproducible 500 on `GET /visits/{id}` (confirmed still broken in the current file, per the Extraction Map §1a).

## 3. Order of operations (recommended, once you approve going further)

1. **`VisitService.java` fix (§2c)** — trivial, zero-conflict, fixes a currently-active bug. Safe to do first, independent of everything else.
2. **`ClaimMapper.java` fix (§2a)** — small, isolated, no real conflict, independent of Claims Review branch state.
3. **Resolve the §1 blocking gap** (missing `ClaimContextHeader.jsx` / `ClinicalDataPanel.jsx` / `AttachmentsPanel.jsx` / `ClaimConversationPanel.jsx`) — either a further targeted dangling-object search, or accept they need to be rebuilt fresh, **before** restoring `ProviderClaimsSubmission.jsx` and its sibling components.
4. **Restore the frontend workspace group (§1)** — once step 3 is resolved, so the result actually builds.
5. **`ClaimService.java` STATUS-1 patch (§2b)** — after CLAIM-NUMBERING-1 is committed, per your own sequencing preference, to avoid merging two uncommitted diffs in the same method.
6. Full verification pass on everything restored: `mvn -o compile`, the two recovered backend test files actually run and pass, `npx eslint` + `npx vite build` on the frontend group, then a fresh report before any commit.

**None of steps 1–6 has been executed. This is the plan only, per your instruction to stop after producing it.**

## 4. What this plan deliberately does NOT do

- Does not touch `ClaimReviewWorkspace.jsx` or anything under `frontend/src/pages/claims/review/` — zero overlap, confirmed in the recovery report.
- Does not start `CLAIM-REVIEW-SPLIT-2B`.
- Does not commit or push anything, at any step, without a separate explicit go-ahead per step (consistent with how every other phase this session has worked).
- Does not delete the `.recovery/provider-portal-2026-07-20/` folder or any dangling git object — it stays as the safety copy until everything in it has been successfully applied and verified.

---

**PROVIDER-PORTAL-WIP RECOVERY RESTORE PLAN READY**
