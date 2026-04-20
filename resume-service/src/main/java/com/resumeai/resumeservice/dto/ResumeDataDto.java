package com.resumeai.resumeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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

    /** Base64-encoded profile photo (optional, used only in photo-enabled templates) */
    private String photoBase64;

    private List<ExperienceDto> experience;
    private List<EducationDto> education;
    private List<String> skills;
    private List<ProjectDto> projects;
    private List<CertificationDto> certifications;
    private List<String> resumeLanguages;

    @Data @NoArgsConstructor
    public static class ExperienceDto {
        private String position;   // job title / role
        private String company;
        private String location;
        private String startDate;
        private String endDate;
        private boolean current;
        private List<String> bullets;
        private String description; // raw text fallback
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
        private String technologies; // alias
        private String description;
        private String link;
    }

    @Data @NoArgsConstructor
    public static class CertificationDto {
        private String name;
        private String issuer;
        private String date;
        private String year; // alias
    }
}
