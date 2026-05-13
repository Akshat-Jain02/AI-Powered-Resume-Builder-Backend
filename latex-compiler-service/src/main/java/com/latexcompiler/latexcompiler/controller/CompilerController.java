package com.latexcompiler.latexcompiler.controller;

import com.latexcompiler.latexcompiler.service.LaTeXCompilerService;
import com.latexcompiler.latexcompiler.service.LaTeXCompilerService.CompilationResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/compiler")
public class CompilerController {

    private final LaTeXCompilerService compilerService;

    private static final java.util.regex.Pattern CRLF_PATTERN = java.util.regex.Pattern.compile("[\\r\\n]+");
    private static final java.util.regex.Pattern PRINTABLE_ASCII_PATTERN = java.util.regex.Pattern.compile("[^\\x20-\\x7E]");

    public CompilerController(LaTeXCompilerService compilerService) {
        this.compilerService = compilerService;
    }

    /**
     * Sanitize a string for use in an HTTP header value by removing CR/LF characters.
     */
    private String sanitizeForHeader(String value) {
        if (value == null) return "";
        // Replace CR, LF, and CRLF with a single space
        String sanitized = CRLF_PATTERN.matcher(value).replaceAll(" ");
        // Strip any remaining non-printable characters or control characters to ensure strict header compliance
        return PRINTABLE_ASCII_PATTERN.matcher(sanitized).replaceAll("").trim();
    }


    @PostMapping("/compile")
    public ResponseEntity<byte[]> compile(@RequestBody CompileRequest request) {
        try {
            CompilationResult result = compilerService.compile(request.getCode(), request.getPhotoBase64(), request.getFiles());
            
            if (!result.isSuccess()) {
                String logs = result.getLogs() != null ? result.getLogs() : "Unknown compilation error";
                String sanitizedLogs = sanitizeForHeader(logs.substring(0, Math.min(logs.length(), 1000)));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("X-Compilation-Logs", sanitizedLogs)
                        .body(logs.getBytes(StandardCharsets.UTF_8));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "resume.pdf");
            
            return new ResponseEntity<>(result.getPdfBytes(), headers, HttpStatus.OK);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Compilation interrupted".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Internal server error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorMsg.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static class CompileRequest {
        private String code;
        private String photoBase64;
        private Map<String, String> files; // filename -> base64 content (Overleaf-style project files)
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getPhotoBase64() { return photoBase64; }
        public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }
        public Map<String, String> getFiles() { return files; }
        public void setFiles(Map<String, String> files) { this.files = files; }
    }
}
