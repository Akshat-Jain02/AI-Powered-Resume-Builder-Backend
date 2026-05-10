package com.resumeai.templateservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class TemplateRequestDto {
    @NotBlank(message = "Template name is required")
    private String name;
    private String description;
    @NotBlank(message = "Category is required")
    private String category;
    @JsonProperty("isPremium")
    private boolean premium;
    private String previewBg;
    private String accentColor;
}
