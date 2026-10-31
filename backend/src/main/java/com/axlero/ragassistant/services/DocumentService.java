
package com.axlero.ragassistant.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    public String extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File is too large. Maximum size is 5MB.");
        }

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        boolean isTxt = filename != null
                && filename.toLowerCase(Locale.ROOT).endsWith(".txt")
                && "text/plain".equalsIgnoreCase(contentType);

        boolean isPdf = filename != null
                && filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
                && "application/pdf".equalsIgnoreCase(contentType);

        if (!isTxt && !isPdf) {
            throw new IllegalArgumentException(
                    "Only TXT and PDF files are supported.");
        }

        byte[] bytes = file.getBytes();

        if (isTxt) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        if (bytes.length < 5
                || bytes[0] != '%'
                || bytes[1] != 'P'
                || bytes[2] != 'D'
                || bytes[3] != 'F'
                || bytes[4] != '-') {
            throw new IllegalArgumentException("Invalid PDF file.");
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            var reader = new PagePdfDocumentReader(
                    new InputStreamResource(input));
            List<Document> pages = reader.get();

            return pages.stream()
                    .map(Document::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse("");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // The bytes are already in memory, so a parse failure here means
            // the uploaded PDF itself is unreadable. Report it as a client
            // error (400), not a server error (500).
            throw new IllegalArgumentException(
                    "The PDF could not be read. "
                            + "It may be corrupted or password-protected.",
                    e);
        }
    }
}
