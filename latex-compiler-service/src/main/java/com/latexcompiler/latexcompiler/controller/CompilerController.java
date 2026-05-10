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

@RestController
@RequestMapping("/api/compiler")
public class CompilerController {

    private final LaTeXCompilerService compilerService;

    public CompilerController(LaTeXCompilerService compilerService) {
        this.compilerService = compilerService;
    }

    /**
     * Sanitize a string for use in an HTTP header value by removing CR/LF characters.
     */
    private String sanitizeForHeader(String value) {
        if (value == null) return "";
        // Replace CR, LF, and CRLF with a single space
        String sanitized = value.replaceAll("[\\r\\n]+", " ");
        // Strip any remaining non-printable characters or control characters to ensure strict header compliance
        return sanitized.replaceAll("[^\\x20-\\x7E]", "").trim();
    }


    @PostMapping("/compile")
    public ResponseEntity<byte[]> compile(@RequestBody CompileRequest request) {
        try {
            CompilationResult result = compilerService.compile(request.getCode(), request.getPhotoBase64());
            
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
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Internal server error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorMsg.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static class CompileRequest {
        private String code;
        private String photoBase64;
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getPhotoBase64() { return photoBase64; }
        public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }
    }
}
