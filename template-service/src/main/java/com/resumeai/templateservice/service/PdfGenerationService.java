package com.resumeai.templateservice.service;

import com.resumeai.templateservice.dto.ResumeDataDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for coordinating PDF generation by delegating to the 
 * latex-compiler-service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PdfGenerationService {

    private final RestTemplate restTemplate; // Regular for external URLs
    private final RestTemplate loadBalancedRestTemplate; // For microservices

    /**
     * Generate a PDF for the given template entity and resume data.
     *
     * @param template the template entity
     * @param data     resume data containing raw LaTeX
     * @return raw PDF bytes
     */
    public byte[] generatePdf(ResumeTemplate template, ResumeDataDto data) throws Exception {
        String latex;
        
        // Prioritize custom LaTeX from the resume data (editor content)
        if (data != null && data.getLatexContent() != null && !data.getLatexContent().isBlank()) {
            log.info("Using custom LaTeX content from resume data.");
            latex = data.getLatexContent();
        } else if (template.getLatexContent() != null && !template.getLatexContent().isBlank()) {
            log.info("Using default LaTeX content from template.");
            latex = template.getLatexContent();
        } else {
            log.warn("No LaTeX content available for generation.");
            latex = "\\documentclass{article}\\begin{document}No content available.\\end{document}";
        }
        
        Map<String, String> resolvedFiles = resolveProjectFiles(data);
        return compilePdf(latex, data != null ? data.getPhotoBase64() : null, resolvedFiles);
    }

    /**
     * Downloads any Cloudinary URLs in the files map and converts them to Base64.
     */
    private Map<String, String> resolveProjectFiles(ResumeDataDto data) {
        Map<String, String> resolved = new HashMap<>();
        if (data == null || data.getFiles() == null) return resolved;

        for (Map.Entry<String, String> entry : data.getFiles().entrySet()) {
            processProjectFile(entry.getKey(), entry.getValue(), resolved);
        }
        return resolved;
    }

    private void processProjectFile(String fileName, String content, Map<String, String> resolved) {
        if (content != null && (content.startsWith("http://") || content.startsWith("https://"))) {
            if (!isValidExternalUrl(content)) {
                log.warn("Blocking potentially unsafe URL: {}", content);
                return;
            }

            try {
                log.debug("Downloading project file from URL: {}", content);
                byte[] bytes = restTemplate.getForObject(content, byte[].class);
                if (bytes != null) {
                    resolved.put(fileName, Base64.getEncoder().encodeToString(bytes));
                }
            } catch (org.springframework.web.client.RestClientException e) {
                log.error("Failed to download file {}: {}", fileName, e.getMessage());
            }
        } else {
            resolved.put(fileName, content);
        }
    }

    private boolean isValidExternalUrl(String url) {
        // Industry Best Practice: Use a whitelist for external asset downloads to prevent SSRF
        return url.contains("res.cloudinary.com") || url.contains("cloudinary.com");
    }

    /**
     * Compiles the given LaTeX string to PDF bytes via remote compiler service.
     */
    private byte[] compilePdf(String latex, String photoBase64, Map<String, String> files) {
        
        Map<String, Object> request = new HashMap<>();
        request.put("code", latex);
        request.put("photoBase64", photoBase64);
        request.put("files", files);

        try {
            return loadBalancedRestTemplate.postForObject(
                "http://latex-compiler-service/api/compiler/compile",
                new HttpEntity<>(request),
                byte[].class
            );
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("Remote LaTeX compilation failed: {}", e.getMessage());
            throw new com.resumeai.templateservice.exception.TemplateServiceException("LaTeX compilation service unavailable or failed.", e);
        }
    }
}

