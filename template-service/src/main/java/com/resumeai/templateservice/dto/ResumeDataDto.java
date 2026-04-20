package com.resumeai.templateservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents all the data needed to render / generate a resume PDF.
 * Sent from resume-service (or frontend via gateway) to template-service.
 */
@Data
@NoArgsConstructor
public class ResumeDataDto {

    @NotBlank(message = "Full name cannot be blank")
    private String fullName;
    
    @NotBlank(message = "Target job title cannot be blank")
    private String targetJobTitle;
    
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Must be a valid email address")
    private String email;
    private String phone;
    private String address;
    private String linkedinUrl;
    private String githubUrl;
    private String summary;

    /** Base64-encoded profile photo (optional — only rendered by photo-enabled templates). */
    private String photoBase64;

    private List<ExperienceDto>    experience;
    private List<EducationDto>     education;
    private List<String>           skills;
    private List<ProjectDto>       projects;
    private List<CertificationDto> certifications;
    private List<String>           resumeLanguages;

    // ── Nested DTOs ────────────────────────────────────────────────────────────

    @Data @NoArgsConstructor
    public static class ExperienceDto {
        private String       position;    // job title / role
        private String       company;
        private String       location;
        private String       startDate;
        private String       endDate;
        private boolean      current;
        private List<String> bullets;
        private String       description; // raw-text fallback if no bullets
    }

    @Data @NoArgsConstructor
    public static class EducationDto {
        private String degree;
        private String field;
        private String institution;
        private String startDate;
        private String endDate;
        private String grade;
    }

    @Data @NoArgsConstructor
    public static class ProjectDto {
        private String name;
        private String techStack;
        private String technologies; // alias for techStack
        private String description;
        private String link;
    }

    @Data @NoArgsConstructor
    public static class CertificationDto {
        private String name;
        private String issuer;
        private String date;
        private String year; // alias for date
    }
}
