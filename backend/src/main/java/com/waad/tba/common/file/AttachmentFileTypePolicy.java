package com.waad.tba.common.file;

import java.util.Set;

/**
 * DOCUMENTS-INTEGRITY-1: single, canonical source of truth for which file
 * types are accepted as an attachment anywhere in the system (claims,
 * visits, pre-authorizations), and a real (not just declared-Content-Type)
 * check of the file's actual bytes.
 *
 * Before this class, three services (ClaimAttachmentService,
 * VisitAttachmentService, PreAuthorizationAttachmentService) each carried
 * their own hand-copied, mutually-disagreeing allow-lists, layered on top of
 * a fourth, even-stricter list in LocalFileStorageService — the exact cause
 * of "type X works in service A but 400s in service B" bugs (e.g. GIF was
 * accepted by the frontend and by PreAuthorizationAttachmentService, but
 * rejected by ClaimAttachmentService and by LocalFileStorageService; Word
 * documents were accepted by every attachment service but rejected by
 * LocalFileStorageService, which only ever allowed application/pdf).
 *
 * Accepted types (deliberately excludes GIF — no real clinical/financial use
 * case for it here, and it was the exact source of one of the reported bugs):
 * PDF, JPEG/JPG, PNG, DOC, DOCX, XLS, XLSX.
 */
public final class AttachmentFileTypePolicy {

    private AttachmentFileTypePolicy() {
    }

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String JPG = "image/jpg";
    public static final String PNG = "image/png";
    public static final String DOC = "application/msword";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String XLS = "application/vnd.ms-excel";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(PDF, JPEG, JPG, PNG, DOC, DOCX, XLS, XLSX);

    public static final String ALLOWED_TYPES_DESCRIPTION = "PDF, JPEG, PNG, DOC, DOCX, XLS, XLSX";

    /**
     * Types the browser can actually render inline (PDF via the native PDF
     * viewer, images via &lt;img&gt;). Word/Excel are deliberately excluded —
     * there is no reliable, accurate in-browser renderer for them in this
     * system, so they always force a real download to be opened in the
     * user's own Word/Excel/LibreOffice instead of a risky, potentially
     * misleading in-browser approximation (this matters for clinical/
     * financial documents where accuracy cannot be compromised).
     */
    public static final Set<String> INLINE_PREVIEWABLE_TYPES = Set.of(PDF, JPEG, JPG, PNG);

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType);
    }

    public static boolean isInlinePreviewable(String contentType) {
        return contentType != null && INLINE_PREVIEWABLE_TYPES.contains(contentType);
    }

    /**
     * Verifies the file's actual bytes are plausible for the declared
     * content type, using each format's magic-number signature. This is a
     * real (if shallow) defense against a mislabeled or renamed file being
     * accepted purely on the browser's self-reported Content-Type — e.g. a
     * .exe renamed to "report.pdf" would declare application/pdf but fail
     * this check.
     *
     * DOCX and XLSX are both OOXML (ZIP) containers and share the same
     * outer signature — this method only verifies "is this actually a ZIP
     * archive", not the internal XML structure, matching the shallow-but-
     * real depth used everywhere else in this codebase's file handling.
     *
     * @return true if the byte signature is consistent with the declared
     *         content type, false otherwise.
     */
    public static boolean contentMatchesSignature(byte[] content, String declaredContentType) {
        if (content == null || content.length < 4 || declaredContentType == null) {
            return false;
        }

        switch (declaredContentType) {
            case PDF:
                return startsWith(content, 0x25, 0x50, 0x44, 0x46); // "%PDF"
            case PNG:
                return startsWith(content, 0x89, 0x50, 0x4E, 0x47);
            case JPEG:
            case JPG:
                return startsWith(content, 0xFF, 0xD8, 0xFF);
            case DOC:
            case XLS:
                // Legacy Office binary formats are OLE2 compound files —
                // both .doc and .xls share this exact outer signature.
                return startsWith(content, 0xD0, 0xCF, 0x11, 0xE0);
            case DOCX:
            case XLSX:
                // Modern Office formats are ZIP (OOXML) containers.
                return startsWith(content, 0x50, 0x4B, 0x03, 0x04);
            default:
                return false;
        }
    }

    private static boolean startsWith(byte[] content, int... signature) {
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
