package com.resumeai.resumeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * Mirrors com.resumeai.templateservice.entity.ResumeTemplate.
 * Used to deserialise the JSON response from Template Service via Feign.
 */
@Data
@NoArgsConstructor
public class TemplateDto {
    private Long id;
    private String name;
    private String description;
    private String category;
    private boolean isPremium;
    private boolean isActive;
    private int usageCount;
    private String previewBg;
    private String accentColor;
    private String htmlTemplateName;
    private LocalDate createdAt;
}
