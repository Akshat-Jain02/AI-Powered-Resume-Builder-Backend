package com.resumeai.resumeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class GeneratePdfRequest {

    /** Which template to use (fetched from Template Service via Feign) */
    @NotNull(message = "Template ID is required")
    private Long templateId;

    /** Full resume data */
    @Valid
    @NotNull(message = "Resume data is required")
    private ResumeDataDto resumeData;

    /** Whether to also persist the resume to DB */
    private boolean save = false;

    /** If provided, update this existing saved resume instead of creating a new one */
    private Long savedResumeId;
}
