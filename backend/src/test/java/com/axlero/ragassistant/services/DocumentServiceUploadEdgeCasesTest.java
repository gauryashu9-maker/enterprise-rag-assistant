package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceUploadEdgeCasesTest {

    private static final int MAX_SIZE_BYTES = 5 * 1024 * 1024;
    private static final String UNSUPPORTED_MESSAGE =
            "Only TXT and PDF files are supported.";

    private final DocumentService documentService = new DocumentService();

    private MockMultipartFile file(
            String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    private String rejectionMessage(MockMultipartFile file) {
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> documentService.extractText(file));
        return thrown.getMessage();
    }

    // ---- size and emptiness ------------------------------------------------

    @Test
    void rejectsNullFile() {
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> documentService.extractText(null));

        assertEquals("File is empty.", thrown.getMessage());
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile empty = file("empty.txt", "text/plain", new byte[0]);

        assertEquals("File is empty.", rejectionMessage(empty));
    }

    @Test
    void rejectsFileLargerThanFiveMegabytes() {
        MockMultipartFile tooBig = file(
                "big.txt", "text/plain", new byte[MAX_SIZE_BYTES + 1]);

        assertEquals(
                "File is too large. Maximum size is 5MB.",
                rejectionMessage(tooBig));
    }

    @Test
    void acceptsFileExactlyAtTheFiveMegabyteLimit() throws Exception {
        byte[] content = new byte[MAX_SIZE_BYTES];
        Arrays.fill(content, (byte) 'a');

        String text = documentService.extractText(
                file("limit.txt", "text/plain", content));

        assertEquals(MAX_SIZE_BYTES, text.length());
    }

    // ---- file type checks ---------------------------------------------------

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile docx = file(
                "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "hello".getBytes(StandardCharsets.UTF_8));

        assertEquals(UNSUPPORTED_MESSAGE, rejectionMessage(docx));
    }

    @Test
    void rejectsTxtFileWithWrongContentType() {
        MockMultipartFile wrongType = file(
                "notes.txt",
                "application/octet-stream",
                "hello".getBytes(StandardCharsets.UTF_8));

        assertEquals(UNSUPPORTED_MESSAGE, rejectionMessage(wrongType));
    }

    @Test
    void rejectsPdfExtensionWithTextContentType() {
        MockMultipartFile mismatch = file(
                "doc.pdf",
                "text/plain",
                "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        assertEquals(UNSUPPORTED_MESSAGE, rejectionMessage(mismatch));
    }

    @Test
    void rejectsMissingFilename() {
        MockMultipartFile noName = file(
                null, "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertEquals(UNSUPPORTED_MESSAGE, rejectionMessage(noName));
    }

    @Test
    void acceptsExtensionAndContentTypeInAnyCase() throws Exception {
        String text = documentService.extractText(file(
                "NOTES.TXT",
                "TEXT/PLAIN",
                "hello".getBytes(StandardCharsets.UTF_8)));

        assertEquals("hello", text);
    }

    // ---- TXT content --------------------------------------------------------

    @Test
    void readsTxtContentAsUtf8() throws Exception {
        String original = "h\u00e9llo w\u00f6rld";

        String text = documentService.extractText(file(
                "notes.txt",
                "text/plain",
                original.getBytes(StandardCharsets.UTF_8)));

        assertEquals(original, text);
    }

    // ---- PDF checks ---------------------------------------------------------

    @Test
    void rejectsPdfWithoutPdfHeader() {
        MockMultipartFile fake = file(
                "fake.pdf",
                "application/pdf",
                "this is not a pdf".getBytes(StandardCharsets.UTF_8));

        assertEquals("Invalid PDF file.", rejectionMessage(fake));
    }

    @Test
    void rejectsPdfShorterThanTheHeader() {
        MockMultipartFile tiny = file(
                "tiny.pdf",
                "application/pdf",
                "%PD".getBytes(StandardCharsets.UTF_8));

        assertEquals("Invalid PDF file.", rejectionMessage(tiny));
    }

    @Test
    void rejectsCorruptPdfWithValidHeaderAsBadRequest() {
        MockMultipartFile corrupt = file(
                "corrupt.pdf",
                "application/pdf",
                "%PDF-1.4\nthis is not a real pdf body"
                        .getBytes(StandardCharsets.UTF_8));

        assertEquals(
                "The PDF could not be read. "
                        + "It may be corrupted or password-protected.",
                rejectionMessage(corrupt));
    }
}
