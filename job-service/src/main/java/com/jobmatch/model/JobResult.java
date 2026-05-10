package com.jobmatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Represents a single job listing returned from Adzuna + our match score.
 */
@Data
@NoArgsConstructor
public class JobResult {

    // ── From Adzuna ────────────────────────────────────────────────────────────
    private String id;
    private String title;
    private String company;
    private String location;
    private String description;          // snippet
    private String redirectUrl;          // apply link
    private String created;              // date posted
    private Double salaryMin;
    private Double salaryMax;
    private String salaryPredicted;
    private String category;
    private String contractType;         // permanent / contract
    private String contractTime;         // full_time / part_time

    // ── Our computed match score ───────────────────────────────────────────────
    private int matchScore = 0;          // 0–100
    private String matchLevel = "";      // EXCELLENT / GOOD / FAIR / LOW
    private List<String> matchedSkills;  // which skills matched
    private List<String> missingSkills;  // skills in job desc not in resume
}
