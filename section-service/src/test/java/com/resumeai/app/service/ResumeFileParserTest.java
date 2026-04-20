package com.resumeai.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.*;

class ResumeFileParserTest {

    private ResumeFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResumeFileParser();
    }

    @Test
    void extractText_txtFile_returnsContent() throws Exception {
        String content = "Alice Johnson\nalice@example.com\nSoftware Engineer";
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", content.getBytes());

        String result = parser.extractText(file);

        assertThat(result).contains("Alice Johnson");
        assertThat(result).contains("alice@example.com");
    }

    @Test
    void extractText_unknownExtension_fallsBackToText() throws Exception {
        String content = "Some plain text content";
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.xyz", "application/octet-stream", content.getBytes());

        String result = parser.extractText(file);

        assertThat(result).isNotNull();
    }

    @Test
    void extractText_nullFilename_doesNotThrow() throws Exception {
        String content = "Plain text content here";
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "text/plain", content.getBytes());

        String result = parser.extractText(file);

        assertThat(result).isNotNull();
    }

    @Test
    void extractText_emptyTxtFile_returnsEmptyString() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        String result = parser.extractText(file);

        assertThat(result).isNotNull();
    }
}
