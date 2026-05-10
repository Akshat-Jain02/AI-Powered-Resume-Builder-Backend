package com.resumeai.resumeservice.client;

import com.resumeai.resumeservice.dto.PdfGenerationRequestDto;
import com.resumeai.resumeservice.dto.TemplateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Feign client for Template Service communication.
 *
 * Base URL resolves via Eureka service discovery using the registered
 * application name {@code template-service}.
 *
 * Responsibilities:
 *  - Fetch template metadata (list, by-id).
 *  - Delegate PDF generation — all LaTeX rendering logic lives in template-service.
 *  - Notify template-service of usage increments.
 */
@FeignClient(name = "template-service")
public interface TemplateServiceClient {

    // ── Template metadata ──────────────────────────────────────────────────────

    /** All active templates — used to populate the frontend gallery. */
    @GetMapping("/api/templates")
    List<TemplateDto> getAllActiveTemplates();

    /** Single template by ID. */
    @GetMapping("/api/templates/{id}")
    TemplateDto getTemplateById(@PathVariable("id") Long id);

    // ── PDF generation (delegated to template-service) ─────────────────────────

    /**
     * Delegate PDF generation to template-service.
     * Returns raw PDF bytes via {@code application/pdf}.
     */
    @PostMapping(
        value    = "/api/templates/pdf/generate",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    byte[] generatePdf(@RequestBody PdfGenerationRequestDto request);

    // ── Usage tracking ─────────────────────────────────────────────────────────

    /** Increment usage count after a PDF has been delivered to the client. */
    @PostMapping("/api/templates/{id}/usage")
    void incrementUsage(@PathVariable("id") Long id);
}
