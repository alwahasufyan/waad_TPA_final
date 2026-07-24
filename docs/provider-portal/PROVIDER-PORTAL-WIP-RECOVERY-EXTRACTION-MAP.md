# PROVIDER-PORTAL-WIP-RECOVERY-EXTRACT-1 — Extraction Map

**Status: extraction only. Nothing applied to production source. Not committed. Not pushed.**

All recovered content has been copied out of git's dangling (unreachable, ungarbage-collected) objects into:

```
.recovery/provider-portal-2026-07-20/
```

This directory mirrors the intended production paths under `frontend/` and `backend/`, plus a `reports/` folder for the recovered markdown reports and an `unidentified/` folder for leftover dangling blobs that clearly belong to this same lost session but could not be confidently matched to a filename or are unrelated (kept anyway, in case they matter later — deleting them loses the only remaining copy).

**How this was recovered:** `git fsck --unreachable --no-reflogs` found ~310 dangling blobs and 1 dangling commit — leftover objects from a `git stash` (or similar) whose ref was later dropped. Dropped-stash content is invisible to `git stash list`, `git log --all`, `git branch --all`, and `git reflog` (all checked, all empty for this work) but the blob bytes remain physically in `.git/objects` until an eventual `git gc` prunes them. Filenames were recovered by cross-referencing each content blob's hash against dangling **tree** objects (which do record filenames), via `git cat-file -p <tree-hash>` and matching the blob hash in the tree listing. Every path below marked "confirmed" was matched this exact way; every file was extracted with `git cat-file -p <blob-hash>`, which only reads git's object store — no working-tree or ref changes were made to get any of this.

---

## 1. Recovered files — confirmed filename, extracted to intended path

| Blob hash | Recovered to (`.recovery/...`) | Intended production path | Size |
|---|---|---|---|
| `9453de0098078fbf37ef55ddadabdec7a7b049de` | `frontend/src/pages/provider/ProviderClaimsSubmission.jsx` | `frontend/src/pages/provider/ProviderClaimsSubmission.jsx` | 14,651 B |
| `5cb32742770fe1a22233b3c522183b0601e91c33` | `frontend/src/pages/provider/claim-submission/hooks/useProviderClaimSubmission.js` | same | 54,908 B |
| `e32d85032e55ea3cc28e194fd5be3cc6ed6b340f` | `frontend/src/pages/provider/claim-submission/components/ServiceLinesPanel.jsx` | same (path inferred — see §3) | 20,028 B |
| `b041a16bee28de389257e1b8a36e931e02eca860` | `.../components/ClaimReviewStep.jsx` | same (path inferred) | 3,795 B |
| `9287397b438398e522589166fa824e2cd331ae8f` | `.../components/ClaimSectionPrimitives.jsx` | same (path inferred) | 4,209 B |
| `1b754716fc5de1e0c00c9ecf5613e37e4c03f64a` | `.../components/ClaimStepTabs.jsx` | same (path inferred) | 2,583 B |
| `1fefa2886736870c9ee0695148a2504dacf0398b` | `.../components/ClaimSummaryPanel.jsx` | same (path inferred) | 4,729 B |
| `e37aab19754f842e87b0760c6980865238ec00da` | `.../components/ClaimWorkspaceFooter.jsx` | same (path inferred) | 4,720 B |
| `2b546f957d5d1156cbdc1ecd3e80c86d8d1100ed` | `.../components/MemberContextPanel.jsx` | same (path inferred) | 2,476 B |
| `ce515bed12f414e0cafccf0a7670fb6846bc227d` | `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java` | same | 93,502 B |
| `fc16a414317425aca9371e53570526e3e298101b` | `backend/src/main/java/com/waad/tba/modules/claim/mapper/ClaimMapper.java` | same | 36,905 B |
| `5b0ab52510a2c043ad013c4bc196386959ce285a` | `backend/src/main/java/com/waad/tba/modules/visit/service/VisitService.java` | same | 24,089 B |
| `52f7dd063413ddd1a159a13aa201ed8b5f50240e` | `backend/src/test/java/com/waad/tba/modules/claim/mapper/ClaimMapperPricingContractTest.java` | same (confirmed by content: matches DATA-1 report's described test file exactly) | 6,171 B |
| `ee42a690efc14e63abf704a652aa9af41062ebf9` | `backend/src/test/java/com/waad/tba/modules/claim/service/ClaimServiceProviderStatusTest.java` | same (path inferred, sibling of `ClaimService.java`'s test dir; content matches STATUS-1's described 3 tests) | 3,377 B |
| `2a84913df8e444ebd07e1ff13a424baa26fe3770` | `reports/PROVIDER-PORTAL-COMPREHENSIVE-STATUS-REPORT.md` | `docs/provider-portal/` (report, no code impact) | 15,206 B |
| `00721cbf5697944cbee03a59147f475415b53d17` | `reports/PROVIDER-PORTAL-DATA-1-SERVICE-PRICING-CONTRACT-REPORT.md` | `docs/provider-portal/` | 20,094 B |
| `e8bb6c37d576fad293079f0dbce1e9f36bc75e5a` | `reports/PROVIDER-PORTAL-STATUS-1-CLAIM-CREATION-STATUS-REPORT.md` | `docs/provider-portal/` | 13,260 B |
| `e6e7c9dbd255cb67c8fbc9e40465ce7aa4ff1d58` | `reports/PROVIDER-PORTAL-UX-1-PHASE-3A-SMOKE-TEST-ADDENDUM.md` | `docs/provider-portal/` | 10,078 B |
| `7549c38b9ff03f246d8e8db3e24437324a93ab09` | `reports/PROVIDER-PORTAL-UX-1-PHASE-3B-WORKSPACE-REPORT.md` | `docs/provider-portal/` | 7,991 B |

**25 files extracted in total** (19 above + the 6 "unidentified" ones in §2).

### 1a. Bonus find — a second, separate lost fix (VISIT-BUG-1)

While searching for provider-portal files, the recovered `VisitService.java` blob turned out to contain a **different, standalone bug fix** documented in the recovered Phase 3A Smoke Test Addendum: `VisitService.findById()` was `@Transactional(readOnly = true)` while calling an audit-log **insert** inside it — Postgres enforces read-only at the transaction level, so `GET /visits/{id}` 500'd for every caller, unrelated to any provider-portal work. **Confirmed still present and unfixed in the current codebase right now** (`grep` on the live file shows line 120 still `@Transactional(readOnly = true)`, line 142 still calls `auditLogService.createAuditLog(...)` inside it) — this is a currently-live, reproducible defect, not just lost work. The recovered blob's fix is a one-line change (`@Transactional(readOnly = true)` → `@Transactional`) with **zero surrounding diff** (confirmed via `diff --strip-trailing-cr` — see the Restore Plan §2c). Flagging this as high-priority and trivially safe to restore independent of everything else in this recovery.

## 2. Preserved but not filename-confirmed ("unidentified/")

These blobs clearly belong to the same lost work session (found via the same keyword/content matching) but I could not match them to a filename via a dangling tree within the time spent searching, or their relevance is unclear. Preserved as raw content, not mapped to any path, so nothing is lost:

| File | Content signature | Likely identity |
|---|---|---|
| `unidentified/blob-814cdb22-lazyimports-33824b.txt` | starts `import { lazy } from 'react';`, 33,824 B | Possibly a `MainRoutes.jsx` snapshot from that session (has lazy-loaded route imports) — not confirmed distinct from the current file; low priority |
| `unidentified/blob-b8a3812e-lazyimports-34192b.txt` | same signature, 34,192 B | Possibly another snapshot of the same file at a different point in time |
| `unidentified/blob-c6a9d510-lazyimports-34541b.txt` | same signature, 34,541 B | Possibly a third snapshot |
| `unidentified/blob-364338ac-classification-service-9885b.txt` | `classification.service.js` (tree-confirmed filename, but this file is **unrelated to provider claim submission** — it's the medical-classification module's API service) | Likely swept up incidentally in the same stash, not part of this recovery's scope |
| `unidentified/blob-3c5fd168-classification-service-8561b.txt` | same file, different snapshot, 8,561 B | ditto |
| `unidentified/blob-4e79f262-classification-service-9648b.txt` | same file, different snapshot, 9,648 B | ditto |

**Recommendation:** the three `classification.service.js` snapshots are almost certainly out of scope for this recovery (unrelated module) and can be ignored/deleted once you confirm. The three `lazy`-import blobs are lower priority — worth a diff-check against the current `MainRoutes.jsx` later, but not blocking.

## 3. Gaps — described in the recovered reports but not found among the dangling objects

Per the recovered Phase 3B report, these Stage 3A panel components are used by the new `ProviderClaimsSubmission.jsx` but were **not** found as separate blobs in this search (they may not have changed since an even-earlier, already-lost snapshot, or my keyword search simply didn't match their content):

- `ClaimContextHeader.jsx`
- `ClinicalDataPanel.jsx`
- `AttachmentsPanel.jsx`
- `ClaimConversationPanel.jsx`

**These are hard blockers for actually running the recovered `ProviderClaimsSubmission.jsx`** — it imports them and they do not exist anywhere in the current working tree or in this recovery. See the Restore Plan for how this affects sequencing.

## 4. Path inference caveat

Every "components/" path above is **inferred**, not confirmed by a tree match — only `useProviderClaimSubmission.js`'s path (`.../claim-submission/hooks/useProviderClaimSubmission.js`) was independently confirmed both by tree lookup *and* by the DATA-1 report's own text (§3: "Files changed: `frontend/src/pages/provider/claim-submission/hooks/useProviderClaimSubmission.js`"). The sibling `components/` directory is a reasonable, standard-convention guess (hooks/ and components/ as siblings under the same feature folder), not independently verified. This should be confirmed (or corrected) before anything is copied into the real `frontend/src/pages/provider/` tree.

## 5. What was verified about content correctness (not just existence)

- `ClaimMapper.java` and `ClaimService.java`, diffed against the **current** working-tree files with `diff --strip-trailing-cr` (to remove false noise from CRLF-vs-LF line endings — the recovered blobs are LF, current files are CRLF): the actual code differences match **exactly** what the recovered DATA-1 and STATUS-1 reports describe, hunk-for-hunk. See the Restore Plan for the exact diffs.
- `VisitService.java` diffed the same way: exactly one line changed (`@Transactional(readOnly = true)` → `@Transactional`), matching VISIT-BUG-1's description exactly.
- Sizes of all extracted files match the sizes reported by `git cat-file -s` before extraction — no truncation occurred.

---

**Extraction complete. No production files modified. No commit. No push.**
