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
     * Hex accent color used for UI/Preview purposes.
     * e.g. "#1a3a5c"
     */
    @Column(name = "accent_color", length = 20)
    private String accentColor;

    /**
     * Internal LaTeX template identifier.
     * e.g. 1 (Executive Classic), 2 (Modern Slate)
     */
    @Column(name = "template_id", nullable = false)
    private int templateId;

    @Lob
    @Column(name = "latex_content", columnDefinition = "LONGTEXT")
    private String latexContent;

    @Column(name = "preview_image_url", length = 512)
    private String previewImageUrl;

    @Column(name = "preview_image_public_id", length = 255)
    private String previewImagePublicId;


    @Column(name = "created_at")
    private LocalDate createdAt = LocalDate.now();
}
