package com.resumeai.resumeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request body sent to template-service's PDF generation endpoint.
 * Mirrors {@code com.resumeai.templateservice.dto.PdfGenerationRequestDto}.
 */
@Data
@NoArgsConstructor
public class PdfGenerationRequestDto {

    /** Database ID of the template to use (1–6 seeded by default). */
        @NotNull(message = "Template ID is required")
    private Long templateId;

    /** Full resume data used to populate the template. */
    @Valid
    @NotNull(message = "Resume data is required")
    private ResumeDataDto resumeData;
}

