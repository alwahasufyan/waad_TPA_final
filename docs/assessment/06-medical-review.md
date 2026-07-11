# 06 — Medical Integrity Review

**Score: 70/100 (B−)** · Reviewed against `.claude/reviews/medical-review.md.txt` and `.claude/Constitution/*` Medical Philosophy ("*Medical decisions must always be based on Benefit Policies, Coverage Rules, Medical Taxonomy, Provider Contracts. Never bypass medical rules. Never embed medical knowledge inside UI components.*").

---

## Medical Workflow

| Check | Status | Evidence |
|---|---|---|
| Correct review workflow | ✓ | `SUBMITTED → UNDER_REVIEW → {APPROVAL_IN_PROGRESS, REJECTED, NEEDS_CORRECTION}`, enforced by `ClaimStateMachine` |
| Correct review status | ✓ | 9-value `ClaimStatus` enum with role-gated transitions |
| Escalation supported | ⚠ | `NEEDS_CORRECTION` return-to-provider path exists; no formal multi-tier escalation (e.g., reviewer → senior reviewer) confirmed |
| Appeals supported | ⚠ | See CF-1 — the `REJECTED → APPROVED` "reopen" path is documented but unreachable, which is functionally the same gap as "no appeals workflow" |
| Medical audit trail | ✓ | `medical_audit_logs` — immutable, DB-trigger-enforced, JSONB before/after diffs |

## Clinical Validation

| Check | Status | Evidence |
|---|---|---|
| Diagnosis required | Not confirmed | Not traced to a specific required field in this pass — recommend a targeted follow-up on `ClaimLine`/`Visit` diagnosis capture |
| Procedure validation | ✓ | Every `ClaimLine` requires a valid `serviceCode` resolved against the medical taxonomy |
| Diagnosis ↔ Procedure compatibility | Not confirmed | No dedicated compatibility-rule engine found; medical necessity is currently a reviewer judgment call, not a system-enforced rule — see Medical Philosophy note below |
| Medical necessity supported | ✓ (as human judgment) | `ClaimLine.rejected` + `rejectionReasonCode` from a proper lookup table (`claim_rejection_reasons`), reviewer-driven |
| Required documentation attached | ✓ | `ClaimAttachment`, `UnifiedAttachmentViewer.jsx` for supporting document review |

**Design note (not a defect):** This system automates *financial* adjudication rigorously but *supports* (rather than automates) medical adjudication — clinical necessity is captured as structured rejection reasons plus reviewer discretion, not as an executable diagnosis-procedure compatibility rule set. Per the Constitution's Medical Philosophy, this is defensible: over-automating clinical judgment carries its own risk. This should be named explicitly as a deliberate boundary, not assumed to be a gap.

## Authorization (Pre-Authorization)

| Check | Status | Evidence |
|---|---|---|
| Prior Authorization checked | ✓ | `BenefitPolicyRule.requiresPreApproval` is the sole architectural source of PA requirement |
| Emergency exception supported | ⚠ | `isBacklog` flag bypasses the PA-required check for historical/manual entry — not confirmed to have a distinct "true emergency" pathway with its own audit trail versus general backlog entry |
| Authorization status validated | ✓ | `PreAuthorization.canBeApproved()`/`canBeRejected()`/`markAsUsed()` guard predicates |

**Finding (see CF-4):** The FK-target question on `claims.pre_authorization_id` directly threatens this checklist item's "authorization status validated" guarantee — if claims link to the wrong table, PA-status validation could be checking the wrong record.

## Benefit Integration

| Check | Status | Evidence |
|---|---|---|
| Benefit Engine consulted | ✓ | `BenefitPolicyCoverageService` is consulted at claim creation, review, and approval |
| Coverage respected | ✓ | Four-scope limit enforcement (category, annual, lifetime, family) |
| No medical override of benefit rules | ⚠ | The "internal staff backlog bypass" (ignoring policy effective-date windows) lives inside the same shared coverage service used for live decisions — a conflation of two concerns (real-time coverage vs. historical backfill) that risks an operational bypass being used where it shouldn't be |

## Documentation

| Check | Status | Evidence |
|---|---|---|
| Clinical notes | ✓ | Supported via attachments and claim notes fields |
| Laboratory/Radiology reports | ✓ | Generic attachment support covers this; no dedicated lab/radiology-typed document category confirmed |
| Referral | Not confirmed | No dedicated referral entity found |
| Operative notes | ✓ (as generic attachment) | No specialized handling beyond general attachment support |

## Decision Quality

| Check | Status | Evidence |
|---|---|---|
| Reviewer | ✓ | Captured on every status change |
| Date | ✓ | `created_at`/audit timestamps |
| Reason Code | ✓ | `claim_rejection_reasons` lookup table |
| Clinical Notes | ✓ | Free-text reviewer comment field, required on rejection (`Claim.validateBusinessRules()`) |
| Supporting Evidence | ✓ | Attachment linkage |

## Appeals

| Check | Status | Evidence |
|---|---|---|
| Appeal workflow | ✗ | No dedicated appeal entity/workflow found — the closest equivalent is the (currently unreachable) `REJECTED → APPROVED` path |
| Version history | ✓ | `medical_audit_logs` provides full before/after state |
| Reviewer reassignment | ✓ | `MedicalReviewerProviderAssignmentController` allows reassigning a reviewer's provider scope |
| Audit trail | ✓ | Comprehensive |

## Security (Medical)

| Check | Status | Evidence |
|---|---|---|
| PHI protected | ✓ | RBAC-gated access throughout; medical audit logs restricted to `SUPER_ADMIN`/`MEDICAL_REVIEWER` |
| Medical permissions enforced | ✓ | `NEEDS_CORRECTION` transition exclusively `MEDICAL_REVIEWER`-gated — genuine clinical-judgment privilege separation |
| Reviewer access restricted | ✓ | Provider-scoped reviewer assignment (`medical_reviewer_providers`) is a real clinical-governance control, not just a UI filter |

---

## Findings Requiring Action

1. **(Critical, shared with CF-3)** Dual coverage engines threaten "no medical override of benefit rules" — if `claim.ruleengine.*` and `BenefitPolicyCoverageService` can produce different pre-authorization-requirement or coverage decisions for the same input, this is a medical-integrity defect, not just a code-quality one.
2. **(Critical, shared with CF-4)** The pre-authorization FK-target question directly affects authorization-status validation reliability.
3. **(High)** No formal appeals workflow beyond the (currently blocked) reject-and-reopen path — recommend a product decision on whether appeals are in scope, and if so, design a dedicated, audited appeal entity rather than overloading the claim state machine.
4. **(Medium)** The "internal staff backlog bypass" should be extracted from the live coverage-resolution path into a clearly-separated historical-data-entry code path, so real-time medical/coverage decisions can never accidentally use relaxed rules intended only for backfill.
5. **(Low)** Confirm whether diagnosis capture and diagnosis-procedure compatibility are intentionally out of scope (acceptable, per the Medical Philosophy note above) or a genuine gap the business wants closed.

## Decision

**✅ Approve, with Changes Required.** Medical governance (reviewer scoping, audit immutability, role-separated clinical judgment) is well-designed. The score is held back almost entirely by issues that are shared with the Financial review (CF-3, CF-4) rather than medical-specific defects — fixing those two critical findings will raise this area's score substantially without any medical-workflow-specific work required.

---

*Continue to [`07-database-review.md`](./07-database-review.md).*
