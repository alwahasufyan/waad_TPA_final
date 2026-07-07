package com.waad.tba.modules.report.service;

import com.lowagie.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;

@Service
public class PdfExportService {

    public byte[] generatePdfFromHtml(String html) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            
            // Shared font for Arabic RTL - you need an arabic font like Cairo or Amiri.
            // But for now, we add the default ones or load from resources if exists.
            File fontFile = new ClassPathResource("fonts/Cairo-Regular.ttf").getFile();
            if (fontFile.exists()) {
                renderer.getFontResolver().addFont(fontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
            
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);
            return outputStream.toByteArray();
        }
    }
}
