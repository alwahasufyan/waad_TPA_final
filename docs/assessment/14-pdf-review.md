# 14 — PDF Review

**Score: 62/100 (C)** · Reviewed against the Reporting & Printing Constitution's PDF Standards and the Localization Constitution's PDF section.

---

## Technology Choice

OpenPDF + Flying Saucer (`org.xhtmlrenderer.pdf.ITextRenderer`) rendering Thymeleaf-produced HTML — this is a correct, standard, well-understood choice for HTML-to-PDF generation with good CSS support, and is the right technology for a system whose PDF templates are more naturally authored as HTML/CSS than programmatically constructed page-by-page.

## Arabic Fonts

**The correct technical approach is in use**: the Cairo font is embedded via `BaseFont.IDENTITY_H` (Unicode mode) specifically to support correct Arabic glyph shaping — this is the standard, correct way to embed a Unicode font for Arabic PDF rendering with this library stack, and directly satisfies the Constitution's "Embedded Arabic Fonts... Unicode... Correct shaping" requirements.

**The confirmed risk**: `PdfExportService`'s font-loading logic checks `fontFile.exists()` *after* already constructing a `ClassPathResource(...).getFile()` call that would throw first if the resource is inside a packaged jar (a common Spring Boot deployment shape) rather than an exploded classpath directory. If the font fails to load for this reason, the code risks silently falling back rather than failing loudly — meaning a production Arabic PDF could render with broken/missing glyphs (☐☐☐ boxes) with **no obvious error signal** to either the user or the operations team. Per the Localization Constitution's explicit standard — "*No broken Arabic characters*" — this is the single most consequential PDF-specific finding in this assessment, precisely because its failure mode is silent.

## RTL

Correctly targeted via the embedded font + presumed `dir="rtl"`/CSS direction handling in the Thymeleaf templates (`reports/claim-report` template). As noted in `13-printing-review.md`, this is a **separate** RTL implementation from the frontend's `RTLLayout.jsx`/`stylis-plugin-rtl` system — correct in principle (server rendering is necessarily separate), but it means RTL correctness must be verified twice across the codebase, not once, and a fix to one path does not automatically fix the other.

## Unicode

Satisfied by the `IDENTITY_H` embedding approach described above — this is the right choice and should not be second-guessed; it's the failure-detection gap around it that needs attention, not the choice itself.

## Line Wrapping / Tables

Flying Saucer generally handles CSS-based text wrapping and table layout correctly for well-formed HTML/CSS input; whether the actual `claim-report` Thymeleaf template exercises this correctly for long Arabic text (which can have different wrapping behavior than Latin text due to word-boundary and justification differences) was not independently verified in this pass — recommend a targeted visual QA pass specifically with realistic long-Arabic-text claim data (long member names, long medical service descriptions) rather than short test strings, since wrapping defects often only surface with realistic data length.

## Printing Quality / Professional Appearance

The one-time technical investment (correct font embedding library choice, Thymeleaf templating for maintainable layout) suggests the team cared about output quality when this was built. The gap is not in the rendering technology — it's in the **failure-detection and enterprise-document-identity layers** described here and in `13-printing-review.md` (silent font-failure risk, no watermark/signature support).

## Archive / Searchable / Selectable Text

`IDENTITY_H` Unicode font embedding, combined with HTML-source rendering (rather than image-based PDF generation), should produce searchable, selectable text by default — this is a natural consequence of the technology choice already made and was not separately verified, but there is no reason to expect it doesn't hold.

---

## Findings Requiring Action

1. **(High)** Fix the font-loading failure mode in `PdfExportService` to fail loudly (clear exception, logged, surfaced) rather than risk a silent fallback — this is the single highest-value fix in the entire printing/PDF/reporting cluster of findings, because it protects against a defect class (broken Arabic PDFs) that could currently ship to a provider or regulator without anyone noticing until a complaint arrives.
2. **(High)** Add a CI/deployment smoke test that renders one sample Arabic-heavy PDF (long names, mixed Arabic/English medical terms) and asserts both successful generation and correct font embedding — turns this from a "hope it works in prod" risk into a caught-before-deploy check.
3. **(Medium)** Visual QA pass on long-Arabic-text wrapping in the actual claim-report template with realistic data.
4. **(Medium)** Confirm print (`react-to-print`) and PDF output visual parity (shared finding with `13-printing-review.md`).
5. **(Low)** Extend watermark/signature-area support (shared with `13-printing-review.md`) once designed, since both would touch the same Thymeleaf template layer.

## Decision

**⚠ Changes Required, but the foundation is correct.** The technology and font-embedding approach are right; the single fix that matters most is making failure visible instead of silent. This is a small, contained, high-value change — not a rendering-pipeline redesign.

---

*Continue to [`15-localization-review.md`](./15-localization-review.md).*
