package com.waad.tba.common.file;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for serving files as HTTP responses
 */
public class FileResourceUtils {

    private FileResourceUtils() {
    }

    public static String buildAttachmentContentDisposition(String fileName) {
        String safeName = normalizeFileName(fileName);
        String asciiName = toAsciiSafeFileName(safeName);
        String encodedName = rfc5987Encode(safeName);
        return "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encodedName;
    }

    public static String buildInlineContentDisposition(String fileName) {
        String safeName = normalizeFileName(fileName);
        String asciiName = toAsciiSafeFileName(safeName);
        String encodedName = rfc5987Encode(safeName);
        return "inline; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encodedName;
    }

    private static String normalizeFileName(String fileName) {
        String normalized = (fileName == null || fileName.isBlank()) ? "file" : fileName.trim();
        return normalized.replace("\r", "_").replace("\n", "_");
    }

    private static String toAsciiSafeFileName(String fileName) {
        StringBuilder builder = new StringBuilder(fileName.length());
        for (char ch : fileName.toCharArray()) {
            if (ch >= 32 && ch <= 126 && ch != '"' && ch != '\\') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        String ascii = builder.toString().trim();
        return ascii.isEmpty() ? "file" : ascii;
    }

    private static String rfc5987Encode(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Serve a file from byte array
     * 
     * @param fileBytes The file content as byte array
     * @param fileName The filename to use in Content-Disposition header
     * @param request The HTTP request
     * @return ResponseEntity with the file content
     */
    public static ResponseEntity<Resource> serveFile(byte[] fileBytes, String fileName, HttpServletRequest request) {
        if (fileBytes == null || fileBytes.length == 0) {
            return ResponseEntity.notFound().build();
        }

        // Determine content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(fileName);
        } catch (Exception e) {
            // Ignore
        }

        // Fallback to default content type if not determined
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // Build response
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, buildInlineContentDisposition(fileName))
                .body(new ByteArrayResource(fileBytes));
    }
}
