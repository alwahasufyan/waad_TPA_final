# 16 — Notification System Review

**Score: 15/100 (F)** · Lowest score in the assessment alongside Mobile Readiness. This is a **capability gap**, not an implementation defect — nothing here is broken because it was built poorly; it scores low because it was never built.

---

## Current State: No Enterprise Notification Center Exists

This finding is unambiguous and fully evidenced:

1. **No `Notification` database table.** No migration among the 67 reviewed creates any notification-persistence schema. No backend `Notification` JPA entity, repository, or controller exists anywhere in `backend/src/main/java/com/waad/tba/`.
2. **The header's notification bell is decorative mock UI.** `frontend/src/layout/Dashboard/Header/HeaderContent/Notification/data.jsx` exports a hardcoded array explicitly commented `// mock notification data`, containing fabricated users and events left over from the underlying Mantis/Able Pro admin-dashboard template this application was built on. `Notification/index.jsx` imports this mock array directly and computes an "unread count" from it client-side — **there is no API call anywhere in this component.** Any user who clicks this bell today sees fake data that has nothing to do with their actual claims, approvals, or account activity.
3. **"Notification" in the Java codebase means "email," not a domain concept.** Methods like `sendClaimSubmittedNotification`, `sendClaimApprovedNotification`, `sendClaimRejectedNotification` are all email-sending methods on `EmailService` — functional as transactional email, but they have no persistence, no read/unread state, no in-app inbox, and no way for a user to review notification history within the application itself.
4. **No push/SMS channel.** No Firebase, APNs, or SMS gateway integration exists anywhere in the codebase.
5. **No real-time infrastructure.** No WebSocket, SSE, or polling-based live-update mechanism was found — confirmed via exhaustive search for `websocket|sockjs|stomp|EventSource` across both backend and frontend, with zero genuine matches.

## Business Impact

Per the Constitution's UI/UX principles ("*notifications should be meaningful, always explain what happened, why, recommended action*"), the current state fails this requirement not by doing it poorly but by not doing it at all in-app. Concretely, today:

- A `MEDICAL_REVIEWER` has no in-app signal that a new claim is waiting for review — they must navigate to the claims list and check manually, or rely on email if configured.
- A `PROVIDER_STAFF` user who submitted a claim has no in-app way to see it was approved/rejected without checking email or navigating to their own reports.
- An `ACCOUNTANT` has no in-app alert that a settlement is overdue or a payment needs review.
- The mock notification bell **actively misleads users** by displaying a UI affordance that implies functionality that does not exist — this is arguably worse for user trust than having no bell at all, since a user who investigates it once will learn the entire notification system cannot be trusted, potentially causing them to also distrust genuinely functional parts of the UI.

## Requirements Definition (per mission instruction: "If missing, define requirements")

This section defines the requirements for an Enterprise Notification Center, to be scoped as Epic 7 in `21-enterprise-roadmap.md`.

### Core Capability

1. **Persisted, per-user notification records** — a `Notification` entity/table with at minimum: recipient user, type, title, body (bilingual, per the Localization Constitution), related entity reference (e.g. `claimId`), read/unread state, created timestamp, and optional expiry.
2. **In-app notification center UI** — replacing the current mock bell with a real, API-backed component: unread count badge, dropdown/panel list, mark-as-read (individual and bulk), and a full notification history view.
3. **Notification triggers mapped to existing domain events** — this system already has the right architectural hook: `ClaimApprovedEvent`, `ClaimReversalEvent`, `ClaimSettledEvent` (see `08-backend-review.md`) are real, working Spring domain events. A `NotificationEventListener` consuming these same events (mirroring how `ClaimApprovalEventListener` already consumes them for settlement) is the natural, low-risk integration point — **no new event infrastructure needs to be built**, only a new consumer of what already exists.
4. **Role/persona-appropriate triggers**, at minimum:
   - `MEDICAL_REVIEWER`: new claim submitted to their scope, claim returned needing correction.
   - `ACCOUNTANT`: claim ready for financial approval, payment reconciliation due.
   - `PROVIDER_STAFF`: their claim approved/rejected, pre-authorization decision made.
   - `EMPLOYER_ADMIN`: relevant policy/member-limit events (scope to be defined with the business).
   - `SUPER_ADMIN`: system-level events (feature flag changes, failed login threshold, etc. — lower priority).
5. **Delivery channel**: in-app first (satisfies the primary gap); email already exists as a secondary channel and should remain as-is, potentially becoming configurable per-notification-type ("also email me when...") in a later phase.
6. **Real-time or near-real-time delivery** — full WebSocket/SSE infrastructure is the ideal end state but is not required for a first version; a polling-based unread-count refresh (e.g., every 30–60 seconds while the app is open) is an acceptable, low-risk first implementation, consistent with the Evolution Policy's "prefer the less disruptive solution" guidance.

### Explicitly Out of Scope for a First Version

Per Evolution Policy discipline, the first version should **not** attempt: push notifications (mobile app doesn't exist yet — see `18-mobile-review.md`), SMS delivery, or a full real-time WebSocket architecture. These are legitimate future phases, not blockers to shipping meaningful in-app value quickly.

### Immediate Remediation (independent of the full build-out)

**Before the full Notification Center is built**, the mock notification bell should either be disconnected/hidden (Constitution: "never surprise users," and a fake-data UI element is a worse user experience than no element) or immediately re-pointed at a minimal real data source (e.g., "recent claim status changes for claims you submitted," a much smaller scope than the full center) as a stopgap. This is a low-effort, high-trust-impact fix that can ship well before the full Epic 7 build-out completes.

---

## Findings Requiring Action

1. **(High, immediate)** Disconnect or hide the mock notification bell — it currently misrepresents functionality that doesn't exist.
2. **(High, Epic 7)** Build the Notification Center per the requirements above, leveraging the existing domain-event infrastructure (`ClaimApprovedEvent` et al.) as the primary trigger source.
3. **(Medium, Epic 7)** Wire the Provider Portal's claim-status visibility (`11-provider-portal-review.md` gap) into the new Notification Center once built.
4. **(Low, future)** Evaluate real-time (WebSocket) delivery and push/SMS channels only after the in-app foundation is proven valuable in production.

## Decision

**❌ Reject current state / ✅ Approve requirements definition for Epic 7.** There is nothing to "fix" in the traditional sense — this is new capability work, correctly scoped as its own Epic rather than a bug-fix. The one urgent action is disconnecting the misleading mock UI, which should not wait for the full epic.

---

*Continue to [`17-medical-classification-review.md`](./17-medical-classification-review.md).*
