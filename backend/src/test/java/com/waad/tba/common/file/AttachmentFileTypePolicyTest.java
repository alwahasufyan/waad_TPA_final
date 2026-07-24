package com.waad.tba.common.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DOCUMENTS-INTEGRITY-1 — the canonical, single-source-of-truth allow-list
 * and content-signature verification used by every attachment upload path
 * (claims, visits, pre-authorizations) and by LocalFileStorageService.
 */
class AttachmentFileTypePolicyTest {

    @Test
    void gifIsNoLongerAnAllowedType() {
        assertFalse(AttachmentFileTypePolicy.isAllowedContentType("image/gif"));
    }

    @Test
    void excelTypesAreNowAllowed() {
        assertTrue(AttachmentFileTypePolicy.isAllowedContentType(AttachmentFileTypePolicy.XLS));
        assertTrue(AttachmentFileTypePolicy.isAllowedContentType(AttachmentFileTypePolicy.XLSX));
    }

    @Test
    void wordTypesAreAllowed() {
        assertTrue(AttachmentFileTypePolicy.isAllowedContentType(AttachmentFileTypePolicy.DOC));
        assertTrue(AttachmentFileTypePolicy.isAllowedContentType(AttachmentFileTypePolicy.DOCX));
    }

    @Test
    void onlyPdfAndImagesAreInlinePreviewable() {
        assertTrue(AttachmentFileTypePolicy.isInlinePreviewable(AttachmentFileTypePolicy.PDF));
        assertTrue(AttachmentFileTypePolicy.isInlinePreviewable(AttachmentFileTypePolicy.PNG));
        assertFalse(AttachmentFileTypePolicy.isInlinePreviewable(AttachmentFileTypePolicy.DOCX));
        assertFalse(AttachmentFileTypePolicy.isInlinePreviewable(AttachmentFileTypePolicy.XLSX));
    }

    @Test
    void realPdfBytesMatchPdfSignature() {
        byte[] content = "%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII);
        assertTrue(AttachmentFileTypePolicy.contentMatchesSignature(content, AttachmentFileTypePolicy.PDF));
    }

    @Test
    void renamedExecutableDeclaringPdfFailsSignatureCheck() {
        // MZ header (Windows executable), mislabeled as a PDF.
        byte[] fakeContent = new byte[] { 0x4D, 0x5A, 0x00, 0x00 };
        assertFalse(AttachmentFileTypePolicy.contentMatchesSignature(fakeContent, AttachmentFileTypePolicy.PDF));
    }

    @Test
    void pngSignatureIsValidated() {
        byte[] pngBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        assertTrue(AttachmentFileTypePolicy.contentMatchesSignature(pngBytes, AttachmentFileTypePolicy.PNG));
        assertFalse(AttachmentFileTypePolicy.contentMatchesSignature(pngBytes, AttachmentFileTypePolicy.JPEG));
    }

    @Test
    void docxAndXlsxShareTheZipSignature() {
        byte[] zipBytes = new byte[] { 0x50, 0x4B, 0x03, 0x04 };
        assertTrue(AttachmentFileTypePolicy.contentMatchesSignature(zipBytes, AttachmentFileTypePolicy.DOCX));
        assertTrue(AttachmentFileTypePolicy.contentMatchesSignature(zipBytes, AttachmentFileTypePolicy.XLSX));
    }

    @Test
    void tooShortContentNeverMatches() {
        assertFalse(AttachmentFileTypePolicy.contentMatchesSignature(new byte[] { 0x25 }, AttachmentFileTypePolicy.PDF));
        assertFalse(AttachmentFileTypePolicy.contentMatchesSignature(null, AttachmentFileTypePolicy.PDF));
    }
}
