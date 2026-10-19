
package com.axlero.ragassistant.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class DocumentService {

    public String extractText(MultipartFile file) throws IOException {
        // 1. Check if file is empty
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                "File is empty. Please upload a valid file."
            );
        }

        // 2. Check file size (Max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException(
                "File is too large. Maximum size is 5MB."
            );
        }

        // 3. Check file type (Only text files for now)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("text/plain")) {
            throw new IllegalArgumentException(
                "Only .txt files are allowed currently."
            );
        }

        // 4. Extract text
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
