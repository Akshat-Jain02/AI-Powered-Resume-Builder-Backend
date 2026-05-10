package com.resumeai.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.resumeai.client.ParserClient;
import com.resumeai.client.PaymentClient;
import com.resumeai.kafka.AiEventPublisher;
import com.resumeai.dto.ATSScoreDTO;
import com.resumeai.dto.ResumeAnalysis;
import com.resumeai.service.GeminiService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@AllArgsConstructor
@Tag(name = "AI Resume Generation", description = "Endpoints providing AI-assisted cover letters and resume summaries")
public class ResumeController {

    private static final String ERR_KEY = "error";

    private final GeminiService geminiService;
    private final ParserClient parserClient;
    private final PaymentClient paymentClient;
    private final AiEventPublisher eventPublisher;

    // ── Resume Summary / AI Feedback ───────────────────────────────

    @Operation(summary = "AI Resume Summary", description = "Uploads a resume and generates an AI summary, strengths, and improvements. Deducts 1 credit.")
    @ApiResponse(responseCode = "200", description = "Analysis generated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid file or text extraction failed")
    @ApiResponse(responseCode = "402", description = "Insufficient credits")
    @ApiResponse(responseCode = "500", description = "AI analysis failed")
    @PostMapping(value = "/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> uploadResume(
            @RequestHeader("X-Username") String username,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestParam("file") MultipartFile file) {
        try {

            // 1. Check credits
            ResponseEntity<Object> creditError = verifyCredits(username, "AI analysis");
            if (creditError != null) {
                return creditError;
            }

            // 2. Extract text
            log.debug("Starting text extraction for file: {}", file.getOriginalFilename());
            String extractedText = extractTextFromFile(file);
            if (extractedText == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERR_KEY, "Could not extract text from the file. Please check the file."));
            }
            log.info("Text extraction successful for file: {}. Length: {} chars", file.getOriginalFilename(), extractedText.length());

            // 3. Analyse with Gemini
            log.debug("Sending request to Gemini AI for resume analysis...");
            ResumeAnalysis analysis = geminiService.analyzeResume(extractedText);
            if (analysis == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(ERR_KEY, "AI analysis failed. Please try again."));
            }
            log.info("Gemini analysis completed for user: {}", username);

            // 4. Deduct credits
            deductCredits(username, userEmail, "AI Resume Analysis", file.getOriginalFilename());

            // 5. Publish Kafka event (best-effort — never fail the response)
            publishResumeAnalysisEvent(username, userEmail, file.getOriginalFilename(), analysis);

            return ResponseEntity.ok(analysis);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(ERR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERR_KEY, "Failed to process resume: " + e.getMessage()));
        }
    }

    // ── ATS Score ──────────────────────────────────────────────────

    @Operation(summary = "ATS Resume Score", description = "Uploads a resume and calculates an ATS score with feedback. Deducts 1 credit.")
    @ApiResponse(responseCode = "200", description = "ATS score calculated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid file or text extraction failed")
    @ApiResponse(responseCode = "402", description = "Insufficient credits")
    @ApiResponse(responseCode = "500", description = "AI scoring failed")
    @PostMapping(value = "/ats/score", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> getAtsScore(
            @RequestHeader("X-Username") String username,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestParam("file") MultipartFile file) {

        try {

            // 1. Check credits
            ResponseEntity<Object> creditError = verifyCredits(username, "ATS scoring");
            if (creditError != null) {
                return creditError;
            }

            // 2. Extract text
            log.debug("Starting text extraction for ATS scoring: {}", file.getOriginalFilename());
            String extractedText = extractTextFromFile(file);
            if (extractedText == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERR_KEY, "Could not extract text from the file. Please check the file."));
            }
            log.info("Text extraction for ATS scoring successful. Length: {} chars", extractedText.length());

            // 3. Get ATS score
            log.debug("Sending request to Gemini AI for ATS scoring...");
            ATSScoreDTO atsScoreDTO = geminiService.getATSScore(extractedText);
            if (atsScoreDTO == null) {
                return ResponseEntity.internalServerError()
                        .body(Map.of(ERR_KEY, "Failed to generate ATS score"));
            }
            log.info("ATS score generation completed for user: {}. Score: {}", username, atsScoreDTO.getAtsScore());

            // 4. Deduct credits
            deductCredits(username, userEmail, "ATS Score Analysis", file.getOriginalFilename());

            // 5. Publish Kafka event (best-effort)
            publishAtsScoreEvent(username, userEmail, file.getOriginalFilename(), atsScoreDTO);

            return ResponseEntity.ok(atsScoreDTO);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERR_KEY, "Failed to process ATS score: " + e.getMessage()));
        }
    }

    // ── Private helpers ───────────────────────────────────────────

    private ResponseEntity<Object> verifyCredits(String username, String featureName) {
        try {
            log.debug("Checking credits for user: {} for feature: {}", username, featureName);
            Map<String, Boolean> creditCheck = paymentClient.checkCredits(username, 1);
            boolean hasCredits = creditCheck.getOrDefault("hasCredits", false);
            if (!hasCredits) {
                log.warn("Insufficient credits for user: {}", username);
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(Map.of(
                            ERR_KEY, "Insufficient credits",
                            "message", "Please purchase credits to use " + featureName + " feature",
                            "requiresPayment", true
                        ));
            }
        } catch (Exception e) {
            log.error("Error checking credits: ", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(ERR_KEY, "Payment service unavailable. Please try again later."));
        }
        return null;
    }

    private String extractTextFromFile(MultipartFile file) {
        String extractedText = parserClient.extractText(file);
        if (extractedText == null || extractedText.trim().isEmpty()) {
            return null;
        }
        return extractedText;
    }

    private void deductCredits(String username, String userEmail, String reason, String filename) {
        try {
            paymentClient.useCredits(username, userEmail, 1, reason, filename);
            log.info("Credits deducted for user {}", username);
        } catch (Exception e) {
            log.error("Error deducting credits (analysis completed): ", e);
        }
    }

    private void publishResumeAnalysisEvent(String username, String userEmail, String filename, ResumeAnalysis analysis) {
        try {
            eventPublisher.publishResumeAnalysis(
                new AiEventPublisher.ResumeAnalysisContext(username, userEmail, filename),
                analysis.getSummary(), analysis.getStrengths(),
                analysis.getImprovements(), analysis.getKeywords(),
                analysis.getOverallScore(), analysis.getTargetRole()
            );
        } catch (Exception ke) {
            log.warn("Kafka publish failed for RESUME_ANALYSIS_RESULT: {}", ke.getMessage());
        }
    }

    private void publishAtsScoreEvent(String username, String userEmail, String filename, ATSScoreDTO atsScoreDTO) {
        try {
            eventPublisher.publishAtsScore(
                username, userEmail, filename,
                atsScoreDTO.getAtsScore(), atsScoreDTO.getFeedback(),
                atsScoreDTO.getSuggestions()
            );
        } catch (Exception ke) {
            log.warn("Kafka publish failed for ATS_SCORE_RESULT: {}", ke.getMessage());
        }
    }
}
