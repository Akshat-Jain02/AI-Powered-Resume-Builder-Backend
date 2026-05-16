package com.resumeai.resumeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the data needed to generate a LaTeX-based resume PDF.
 */
@Data
@NoArgsConstructor
public class ResumeDataDto {

    /** Full name of the user (used for filename and UI display) */
    private String fullName;

    /** Base64-encoded profile photo (legacy support) */
    private String photoBase64;

    /** Raw LaTeX code for the resume */
    private String latexContent;

    /** Map of project filenames to their Cloudinary URLs or Base64 content */
    private java.util.Map<String, String> files;
}
