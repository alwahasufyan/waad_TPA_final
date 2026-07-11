package com.waad.tba.modules.report.service;

import com.lowagie.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PdfExportService {

    private static final String ARABIC_FONT = "fonts/Cairo-Regular.ttf";

    /**
     * Cached filesystem path to the embeddable Arabic font. Resolved once, lazily.
     * {@code null} means resolution failed (see loud error log in
     * {@link #resolveArabicFontPath()}).
     */
    private volatile String arabicFontPath;
    private volatile boolean fontResolutionAttempted;

    public byte[] generatePdfFromHtml(String html) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();

            // Stage 1 (D21): register the embedded Arabic (Cairo) font for correct
            // RTL glyph shaping. Previous implementation called ClassPathResource
            // .getFile() unconditionally — which throws when the resource lives inside
            // a packaged jar (the normal production layout), causing Arabic PDFs to
            // silently render with broken glyphs. We now resolve the font in a
            // jar-safe way and, on genuine failure, log loudly instead of degrading
            // in silence.
            String fontPath = resolveArabicFontPath();
            if (fontPath != null) {
                renderer.getFontResolver().addFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }

            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Resolve the Arabic font to an absolute filesystem path usable by
     * OpenPDF/Flying Saucer. Works whether the app runs from an exploded
     * classpath (dev) or a packaged jar (prod, where the resource must be copied
     * to a temp file). Result is cached; failure is logged once at ERROR level so
     * degraded Arabic rendering is observable in monitoring rather than silent.
     */
    private String resolveArabicFontPath() {
        if (fontResolutionAttempted) {
            return arabicFontPath;
        }
        synchronized (this) {
            if (fontResolutionAttempted) {
                return arabicFontPath;
            }
            arabicFontPath = doResolveArabicFontPath();
            fontResolutionAttempted = true;
            return arabicFontPath;
        }
    }

    private String doResolveArabicFontPath() {
        try {
            ClassPathResource resource = new ClassPathResource(ARABIC_FONT);
            if (!resource.exists()) {
                log.error("PDF Arabic font '{}' NOT found on the classpath. "
                        + "Arabic text in generated PDFs will render with broken glyphs. "
                        + "Ensure the font ships under src/main/resources/{}.", ARABIC_FONT, ARABIC_FONT);
                return null;
            }

            // Exploded classpath (dev): use the real file directly.
            try {
                File direct = resource.getFile();
                if (direct.exists()) {
                    log.info("PDF Arabic font resolved from filesystem: {}", direct.getAbsolutePath());
                    return direct.getAbsolutePath();
                }
            } catch (Exception notAFile) {
                // Expected when packaged inside a jar — fall through to temp-file copy.
                log.debug("Arabic font is not a filesystem resource ({}); copying to temp file.",
                        notAFile.getMessage());
            }

            // Packaged jar (prod): copy the resource stream to a temp file.
            try (InputStream in = resource.getInputStream()) {
                Path temp = Files.createTempFile("waad-arabic-font-", ".ttf");
                temp.toFile().deleteOnExit();
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                log.info("PDF Arabic font extracted to temp file: {}", temp);
                return temp.toAbsolutePath().toString();
            }
        } catch (Exception e) {
            log.error("Failed to load PDF Arabic font '{}'. Arabic PDFs will render with broken glyphs. Reason: {}",
                    ARABIC_FONT, e.getMessage(), e);
            return null;
        }
    }
}
