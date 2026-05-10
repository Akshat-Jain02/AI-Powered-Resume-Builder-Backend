package com.resumeai.resumeservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * Mirrors com.resumeai.templateservice.entity.ResumeTemplate.
 * Used to deserialise the JSON response from Template Service via Feign.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateDto {
    private Long id;
    private String name;
    private String description;
    private String category;
    
    @JsonProperty("isPremium")
    private boolean premium;
    
    @JsonProperty("isActive")
    private boolean active;
    
    private int usageCount;
    private String previewBg;
    private String accentColor;
    private int templateId;
    private String previewImageUrl;
    private String latexContent;
    private Object createdAt;
}
