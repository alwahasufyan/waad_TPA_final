# Backlog Item BL-002 — DOCUMENTS-SERIALIZATION-1

**Status:** 📋 Open · Not fixed. **Origin:** Discovered during DOCUMENTS-IDOR-1 live verification.
**Owner:** TBD · **Priority:** Medium · **Type:** Engineering / Data Serialization.

---

## Summary

`GET /api/v1/visits/{visitId}/attachments` (`VisitAttachmentController#getVisitAttachments`)
returns `List<VisitAttachment>` — the raw JPA entity — directly as the response body.
`VisitAttachment.visit` is a `@ManyToOne(fetch = FetchType.LAZY)` relation. By the time
Spring serializes the response, there is no open Hibernate session, so if Jackson
attempts to serialize that lazy `visit` field it throws:

```
HttpMessageNotWritableException: Could not write JSON: Could not initialize proxy
[com.waad.tba.modules.visit.entity.Visit#1] - no session
```

This was not caused by the DOCUMENTS-IDOR-1 authorization fix — the endpoint's return
type and serialization were untouched by that fix. It had simply never been exercised
before, because `visit_attachments` was otherwise empty in the local dev database; it
surfaced only once DOCUMENTS-IDOR-1's live security check inserted a test row to
verify cross-provider blocking.

## Reproduction

1. Ensure at least one row exists in `visit_attachments` with a valid `visit_id`.
2. Call `GET /api/v1/visits/{visitId}/attachments` as any authorized user for that visit.
3. Response is `500 INTERNAL_ERROR` with `exception: HttpMessageNotWritableException`,
   `reason: Could not initialize proxy [...Visit#...] - no session`.

## Proposed fix (not implemented here)

Return a DTO (e.g. `VisitAttachmentDto`, mirroring the pattern
`ClaimAttachmentController` already uses with `ClaimAttachmentDto`) instead of the raw
entity, so the response only ever serializes fields that are actually needed and never
touches the lazy `visit` relation. This also avoids incidentally leaking entity
internals (e.g. `Visit`'s full nested graph) through the API.

## Why deferred

Out of scope for DOCUMENTS-IDOR-1, which was authorization-only. This is a
pre-existing serialization defect, unrelated to the ownership/IDOR fix.
