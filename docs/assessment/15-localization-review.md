# 15 — Localization Review

**Score: 78/100 (B)** · Reviewed against the full Localization Constitution. Second-highest-scoring area in this assessment.

---

## Primary Language / RTL Standards

**This is the platform's clearest architectural strength outside the financial core.** `components/RTLLayout.jsx` centralizes RTL via a single `@emotion/cache` + `stylis-plugin-rtl` pairing, with direction **derived from the active language** rather than independently toggleable — this is precisely the correct design per the Constitution ("*Arabic always takes precedence*") and prevents the class of bug where a user ends up with Arabic text in an LTR layout or vice versa. `document.documentElement.dir`/`document.body.dir` are kept in sync automatically.

**One confirmed defect:** `pages/under-development/index.jsx` hardcodes `textAlign: 'left'`, which will visibly misalign against the surrounding RTL layout — trivial to fix, flagged for completeness.

## Secondary Language (English for Medical/Technical)

The medical taxonomy consistently carries both `name`/`nameAr`/`nameEn` fields (`MedicalCategory`, confirmed in `07-database-review.md`-equivalent findings), correctly satisfying the Constitution's "never translate international medical codes... support both Arabic Name, English Name, Medical Code" requirement.

## Currency

Single-currency (LYD) system throughout, consistent with the Constitution's default. The exact display format (`1,250.500 د.ل`, 3 decimal places) was not independently verified field-by-field across every financial display surface in this pass — recommend a targeted UI audit specifically checking that every money value in the app (claims, reports, dashboards) uses the same formatting utility rather than ad hoc `toFixed()` calls scattered per component, since inconsistent decimal precision across screens would be a direct, checkable Constitution violation ("*Never mix formatting styles inside one screen*" — and by extension, across screens for the same concept).

## Number Formatting

Not independently verified against the Constitution's specific precision rules (financial: 3 decimals, percentages: 2 decimals) at the component level. Flagged as a follow-up audit item rather than a confirmed defect, since no inconsistency was directly observed — but also not affirmatively confirmed consistent.

## Date / Time Standards

`DD/MM/YYYY`, 24-hour time, `Africa/Tripoli` timezone are the Constitution's defaults; `dayjs` and `date-fns` are both present as dependencies (a minor redundancy — two date libraries where one would suffice, though not itself a Constitution violation). Consistent DD/MM/YYYY display across the app was not exhaustively verified per-screen.

## Medical Terminology

Confirmed structurally supported via the taxonomy's dual-language fields, per the Constitution's example (`صورة دم كاملة` / `Complete Blood Count` / `CBC`). Search across both languages plus medical abbreviations is the harder bar — see Searching below.

## Searching

The Constitution requires Arabic-diacritic-insensitive, letter-variation-insensitive search (`ا/أ/إ/آ`, `ى/ي`, `ة/ه` normalization) as an automatic requirement. **This specific normalization behavior was not confirmed in this pass** — `BeneficiarySearchController`/`NameSearchController`/`UnifiedSearchController` exist and support partial matching in general, but whether Arabic letter-variant normalization specifically is implemented (versus relying on exact-character matching, which would fail on the common real-world variation of writing the same name with or without hamza marks) is an open, checkable question with real day-to-day search-success-rate implications for Arabic name lookup. This is the most concrete, actionable localization gap identified in this review.

## Names

Member entity stores full names (not forced First/Middle/Last decomposition) per the Constitution's "support long Arabic names... store full legal name" requirement — confirmed via the `members` table's `full_name` field design (`07-database-review.md`-equivalent findings).

## Addresses / Phone Numbers / National Identifiers

Not independently verified in this pass against the Constitution's specific requirements (free-text address support, Libyan phone-number normalization `+218`/`218`/`0`, configurable national-ID format). Flagged as follow-up audit items.

## Reports / PDF / Printing

Covered in dedicated depth in `12`/`13`/`14`. Summary: strong technical foundation (embedded Unicode Arabic font, centralized RTL for the client-rendered path), with the PDF font-failure silent-risk (`14-pdf-review.md`) being the most consequential localization-adjacent defect found anywhere in this cluster.

## Messages / Validation Messages

`ApiError`'s bilingual `message`/`messageAr` fields confirm structured, dual-language error messaging is a first-class backend design decision, not an afterthought — this directly satisfies the Constitution's "*validation messages should explain what/why/how, available in Arabic*" requirement at the API contract level.

## Terminology Consistency

Business terminology (Claim/مطالبة, and equivalents throughout the domain) is consistent between code comments, database, and UI based on all evidence gathered across this entire assessment — a genuine, valuable form of the Constitution's "central terminology dictionary" requirement, even though no single physical terminology-dictionary document was found (the consistency exists organically, which is harder to sustain long-term than a documented dictionary would be).

## Configurable Localization

Currency/date-format/language/precision are architecturally capable of being configuration-driven (`SystemSettingsController`), though the specific extent of localization-value configurability (versus hardcoded LYD/DD-MM-YYYY assumptions baked into components) was not fully audited in this pass.

---

## Findings Requiring Action

1. **(High)** Confirm/implement Arabic diacritic and letter-variation-insensitive search normalization — this is a Constitution-explicit, day-to-day-impactful requirement with unconfirmed implementation status.
2. **(Medium)** Audit money/date/number formatting consistency across every screen — confirm one shared formatting utility is used everywhere rather than ad hoc per-component formatting.
3. **(Low)** Fix the one hardcoded `textAlign: 'left'` RTL defect.
4. **(Low)** Consolidate the two i18n data sources (`locales/` vs `utils/locales/`) noted in `09-frontend-review.md`.
5. **(Low)** Consider consolidating `dayjs`/`date-fns` to one date library.

## Decision

**✅ Approve, with Changes Required.** This is one of the platform's strongest areas — the RTL architecture, dual-language medical taxonomy, and bilingual API error contract all reflect genuine "Arabic-first, not Arabic-translated" design discipline exactly as the Constitution demands. The remaining gaps are verification/completeness items (search normalization, formatting-consistency audit), not architectural defects.

---

*Continue to [`16-notification-review.md`](./16-notification-review.md).*
