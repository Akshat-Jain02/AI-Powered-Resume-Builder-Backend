package com.resumeai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysis {

    private String summary;         // AI-generated summary
    private List<String> strengths; // What's good
    private List<String> improvements; // What needs improvement
    private List<String> alternatives; // Alternative phrasings/bullet points
    private List<String> keywords;  // Missing keywords to add
    private String overallScore;    // e.g. "7/10"
    private String targetRole;      // Detected or suggested role

}
