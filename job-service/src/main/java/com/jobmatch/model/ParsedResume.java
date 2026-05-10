package com.jobmatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds everything extracted from the uploaded resume file.
 */
@Data
@NoArgsConstructor
public class ParsedResume {

    // ── Basic Info ─────────────────────────────────────────────────────────────
    private String name = "";
    private String email = "";
    private String phone = "";
    private String location = "";

    // ── Professional Info ──────────────────────────────────────────────────────
    private String jobTitle = "";          // most likely current/target role
    private int yearsOfExperience = 0;    // estimated from experience sections
    private String experienceLevel = "";  // FRESHER / JUNIOR / MID / SENIOR / LEAD

    // ── Skills ─────────────────────────────────────────────────────────────────
    private List<String> skills = new ArrayList<>();
    private List<String> programmingLanguages = new ArrayList<>();
    private List<String> frameworks = new ArrayList<>();
    private List<String> databases = new ArrayList<>();
    private List<String> tools = new ArrayList<>();

    // ── Education ──────────────────────────────────────────────────────────────
    private String highestDegree = "";
    private String fieldOfStudy = "";

    // ── Raw text (used for keyword searches) ──────────────────────────────────
    private String rawText = "";

    // ── Summary lines (first non-empty paragraph from resume) ─────────────────
    private String summary = "";

    // ── Search keywords: top skills + job title for Adzuna query ─────────────
    private String searchQuery = "";      // auto-built for Adzuna
}
