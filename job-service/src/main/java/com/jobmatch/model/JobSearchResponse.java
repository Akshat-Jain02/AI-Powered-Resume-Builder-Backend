package com.jobmatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class JobSearchResponse {
    private ParsedResume resume;
    private List<JobResult> jobs;
    private int totalResults;
    private String searchQuery;
    private String message;
}
