package com.resumeai.templateservice.controller;

import com.resumeai.templateservice.dto.PdfGenerationRequestDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.PdfGenerationService;
import com.resumeai.templateservice.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.Map;

/**
 * Handles PDF generation requests.
 * Resume-service calls this via Feign to delegate all rendering concerns here,
 * keeping resume-service focused purely on saved-resume data management.
 */
@Slf4j
@RestController
@RequestMapping("/api/templates/pdf")
@RequiredArgsConstructor
@Tag(name = "PDF Generation Engine", description = "Internal/Backend engine for converting HTML templates to PDF payloads")
public class PdfGenerationController {

    private final PdfGenerationService pdfGenerationService;
    private final TemplateService      templateService;

    /**
     * POST /api/templates/pdf/generate
     * <p>
     * Accepts a {@link PdfGenerationRequestDto} with the template ID and resume
     * data, generates a PDF via LaTeX and streams the bytes back.
     */
    @Operation(summary = "Generate PDF Payload", description = "Generates a PDF bytes array based on template ID and resume JSON data.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Template not found"),
            @ApiResponse(responseCode = "500", description = "Latex/PDF generation failure")
    })
    @PostMapping("/generate")
    public ResponseEntity<Object> generatePdf(@Valid @RequestBody PdfGenerationRequestDto request) {
        if (request.getTemplateId() == null || request.getResumeData() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "templateId and resumeData are required"));
        }

        ResumeTemplate template = templateService.getById(request.getTemplateId())
                .orElse(null);

        if (template == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Template not found: " + request.getTemplateId()));
        }

        try {
            byte[] pdf = pdfGenerationService.generatePdf(
                    template,
                    request.getResumeData()
            );

            // Increment usage count after successful generation
            templateService.incrementUsage(template.getId());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (com.resumeai.templateservice.exception.TemplateServiceException e) {
            log.error("Managed exception during PDF generation: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during PDF generation", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "An unexpected error occurred during PDF generation."));
        }
    }
}

