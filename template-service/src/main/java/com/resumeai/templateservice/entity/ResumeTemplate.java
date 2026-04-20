package com.resumeai.templateservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "resume_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Template name is required")
    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Category: PROFESSIONAL, MODERN, CREATIVE, MINIMALIST, ATS_OPTIMISED
     */
    @NotBlank(message = "Category is required")
    private String category;

    private boolean isPremium = false;
    private boolean isActive = true;
    private int usageCount = 0;

    /**
     * CSS gradient string shown in template gallery card.
     * e.g. "linear-gradient(135deg, #1a3a5c, #2d6a9f)"
     */
    @Column(name = "preview_bg", length = 200)
    private String previewBg;

    /**
     * Hex accent color used inside the Thymeleaf HTML template.
     * e.g. "#1a3a5c"
     */
    @Column(name = "accent_color", length = 20)
    private String accentColor;

    /**
     * Thymeleaf HTML filename (without .html extension).
     * e.g. "template1"  →  src/main/resources/templates/template1.html
     */
    @NotBlank(message = "HTML template name is required")
    @Column(name = "html_template_name", nullable = false)
    private String htmlTemplateName;

    @Column(name = "created_at")
    private LocalDate createdAt = LocalDate.now();
}
