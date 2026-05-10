package com.resumeai.templateservice.service;

import com.resumeai.templateservice.dto.ResumeDataDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate;

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
        
        return compilePdf(latex, data != null ? data.getPhotoBase64() : null);
    }

    /**
     * Compiles the given LaTeX string to PDF bytes via remote compiler service.
     */
    private byte[] compilePdf(String latex, String photoBase64) {
        
        Map<String, String> request = new HashMap<>();
        request.put("code", latex);
        request.put("photoBase64", photoBase64);

        try {
            return restTemplate.postForObject(
                "http://latex-compiler-service/api/compiler/compile",
                new HttpEntity<>(request),
                byte[].class
            );
        } catch (Exception e) {
            log.error("Remote LaTeX compilation failed: {}", e.getMessage());
            throw new RuntimeException("LaTeX compilation service unavailable or failed.", e);
        }
    }
}
