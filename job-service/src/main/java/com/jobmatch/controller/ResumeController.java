package com.jobmatch.controller;

import com.jobmatch.client.ParserClient;
import com.jobmatch.dto.TextRequest;
import com.jobmatch.model.JobResult;
import com.jobmatch.model.JobSearchResponse;
import com.jobmatch.model.ParsedResume;
import com.jobmatch.service.AdzunaService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/job")
@Tag(name = "Job Matching", description = "Endpoints to analyze a resume against job descriptions")
public class ResumeController {

    @Autowired
    private ParserClient parserClient;

    @Autowired
    private AdzunaService adzunaService;

    @Operation(summary = "Match Resume to Jobs", description = "Uploads a PDF resume, analyzes it, and returns matching jobs from Adzuna.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs matched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid resume content"),
            @ApiResponse(responseCode = "500", description = "Job search failed")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobSearchResponse> uploadResume(
            @RequestPart("file") MultipartFile file) {

        JobSearchResponse response = new JobSearchResponse();

        log.info("Job match request received. File: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
        try {
            // STEP 1: Extract text
            String rawText = parserClient.extractText(file);

            if (rawText == null || rawText.trim().length() < 50) {
                response.setMessage("Invalid resume content");
                return ResponseEntity.badRequest().body(response);
            }

            // STEP 2: Analyze text
            TextRequest textRequest = new TextRequest();
            textRequest.setText(rawText);
            ParsedResume resume = parserClient.analyzeText(textRequest);

            // STEP 3: Search jobs
            log.info("Resume parsed. Detected role: {}, skills: {}", resume.getJobTitle(), resume.getSkills());
            List<JobResult> jobs = adzunaService.searchJobs(resume);

            response.setResume(resume);
            response.setJobs(jobs);
            response.setTotalResults(jobs.size());
            response.setSearchQuery(resume.getSearchQuery());
            response.setMessage("Success");
            log.info("Job search complete. Found {} jobs for query: {}", jobs.size(), resume.getSearchQuery());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Job search failed for file: {}", file.getOriginalFilename(), e);
            response.setMessage("Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}