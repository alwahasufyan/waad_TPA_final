# Decision Required — CF-7: Hardcoded 20% Draft-Preview Co-Pay Default

**Status:** ✅ **RESOLVED — Option A approved (Stage 1 close-out).** Keep the 20% draft-preview default for now. No code change required (behavior already in place; finalized claims are unaffected). A one-line "business-approved: Option A" comment may be added to `Claim.calculateFields()` opportunistically on any future touch of that file — not scheduled as standalone work.
**Severity:** 🟡 Medium (contained to draft/preview; finalized claims are unaffected — corrected down from the original 🔴 rating after reading the real code).
**Constitution anchors:** *"Never hardcode coverage percentages / co-payment"* vs. *"Never invent business rules."* These two principles are in tension here, which is exactly why it needs your ruling.

---

## Current Implementation (confirmed by direct inspection)

In `modules/claim/entity/Claim.java`, method `calculateFields()`:

```java
// 3. For Non-Finalized statuses, we can provide a "Preview" calculation
boolean finalized = (status == ClaimStatus.APPROVED || status == ClaimStatus.SETTLED);
if (!finalized || this.approvedAmount == null) {
    BigDecimal netAccepted = this.requestedAmount.subtract(this.refusedAmount).max(ZERO);
    // Only generate a default patient co-pay if it's completely missing
    if (this.patientCoPay == null) {
        this.patientCoPay = netAccepted.multiply(new BigDecimal("0.20"))
                                        .setScale(2, RoundingMode.HALF_UP);
    }
    ...
}
```

The hardcoded `0.20` (20%) applies **only** inside the non-finalized (`!finalized`) preview branch, and **only** when `patientCoPay` is completely null.

## Runtime Behavior

- **Finalized claims (APPROVED / SETTLED) are NOT affected** — the block is skipped once `approvedAmount` is set by the reviewer/approval path. The authoritative co-pay at approval comes from the review/coverage flow, not this default.
- The 20% is a **draft/preview convenience** to populate a plausible co-pay for UI display on incomplete drafts that have no co-pay entered yet.
- `validateFinancialIdentity()` still enforces the strict financial identity regardless — the preview value is internally consistent, just not policy-derived.

So this is **not** a defect in the authoritative financial calculation; it is a hardcoded business assumption in the preview default.

## Risk

- **Low but real:** a draft preview may show a 20% patient share that does not match the member's actual benefit-policy coverage, which could mislead a user reading the draft before approval.
- **Constitution tension:** the value is a hardcoded coverage assumption (discouraged), but replacing it with a "correct" number requires knowing the intended default (a business rule) — and deriving it from the policy inside an *entity* would violate layering (entities must not load policies/services).

## Business Impact

- **Financial:** none on finalized/paid amounts. Possible cosmetic mismatch on draft previews only.
- **User experience:** a claim officer/provider viewing a draft may see a co-pay estimate that later changes at approval.

## Available Options

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A. Keep 20% as a documented draft-preview default** | Leave behavior; document clearly that it is a non-authoritative preview estimate | Zero risk; no behavior change | Still a hardcoded assumption (Constitution frowns on it) |
| **B. Derive the preview co-pay from the policy's default coverage %** | Compute `patientCoPay` from the member's `BenefitPolicyRule`/policy default in the **service layer** (not the entity), passing it in before persist | Policy-accurate previews; removes the magic number | More work; must be implemented in the service (not the entity) to respect layering; needs the policy loaded at preview time |
| **C. Show no preview co-pay until a reviewer sets it** | Leave `patientCoPay` null in preview; UI shows "—" / "pending review" | Never shows a potentially-wrong number | Changes preview UX (drafts show blank co-pay); may reduce draft usefulness |

## Recommended Option

**Option B** (derive from policy, implemented in the service layer), as the most Constitution-aligned outcome — it removes the hardcoded assumption *and* respects the "never hardcode coverage %" principle, while keeping the entity free of service dependencies. **However**, the "correct default when even the policy is silent" and whether previews should ever show an estimate at all are business/UX judgments. If the business prefers stability over accuracy for drafts, **Option A** (keep + document) is a perfectly safe choice.

## Why This Requires Business Approval

- The intended co-pay default is a **business rule** — engineering must not invent it (Constitution: *"Never invent business rules"*).
- Whether draft previews should show a policy-derived estimate, a fixed estimate, or nothing at all is a **product/UX decision** affecting how claim officers read in-progress claims.
- Any change here touches the financial calculation surface (even if only the preview branch) and therefore warrants explicit sign-off and regression validation.
