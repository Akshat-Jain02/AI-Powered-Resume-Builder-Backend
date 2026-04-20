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
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    private final GeminiService geminiService;
    private final ParserClient parserClient;
    private final PaymentClient paymentClient;
    private final AiEventPublisher eventPublisher;

    // ── Resume Summary / AI Feedback ───────────────────────────────

    @Operation(summary = "AI Resume Summary", description = "Uploads a resume and generates an AI summary, strengths, and improvements. Deducts 1 credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or text extraction failed"),
            @ApiResponse(responseCode = "402", description = "Insufficient credits"),
            @ApiResponse(responseCode = "500", description = "AI analysis failed")
    })
    @PostMapping(value = "/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadResume(
            @RequestHeader("X-Username") String username,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestParam("file") MultipartFile file) {
        try {
            log.info("Received file from user {}: {} ({} bytes)", username, file.getOriginalFilename(), file.getSize());

            // 1. Check credits
            try {
                Map<String, Boolean> creditCheck = paymentClient.checkCredits(username, 1);
                if (!creditCheck.getOrDefault("hasCredits", false)) {
                    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                            .body(Map.of(
                                "error", "Insufficient credits",
                                "message", "Please purchase credits to use AI analysis feature",
                                "requiresPayment", true
                            ));
                }
            } catch (Exception e) {
                log.error("Error checking credits: ", e);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Payment service unavailable. Please try again later."));
            }

            // 2. Extract text
            String extractedText = parserClient.extractText(file);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Could not extract text from the file. Please check the file."));
            }

            // 3. Analyse with Gemini
            ResumeAnalysis analysis = geminiService.analyzeResume(extractedText);
            if (analysis == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "AI analysis failed. Please try again."));
            }

            // 4. Deduct credits
            try {
                paymentClient.useCredits(username, userEmail, 1, "AI Resume Analysis", file.getOriginalFilename());
                log.info("Credits deducted for user {}", username);
            } catch (Exception e) {
                log.error("Error deducting credits (analysis completed): ", e);
            }

            // 5. Publish Kafka event (best-effort — never fail the response)
            try {
                eventPublisher.publishResumeAnalysis(
                    username,
                    userEmail,
                    file.getOriginalFilename(),
                    analysis.getSummary(),
                    analysis.getStrengths(),
                    analysis.getImprovements(),
                    analysis.getKeywords(),
                    analysis.getOverallScore(),
                    analysis.getTargetRole()
                );
            } catch (Exception ke) {
                log.warn("Kafka publish failed for RESUME_ANALYSIS_RESULT: {}", ke.getMessage());
            }

            log.info("Analysis complete for: {}", file.getOriginalFilename());
            return ResponseEntity.ok(analysis);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing resume: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process resume: " + e.getMessage()));
        }
    }

    // ── ATS Score ──────────────────────────────────────────────────

    @Operation(summary = "ATS Resume Score", description = "Uploads a resume and calculates an ATS score with feedback. Deducts 1 credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ATS score calculated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or text extraction failed"),
            @ApiResponse(responseCode = "402", description = "Insufficient credits"),
            @ApiResponse(responseCode = "500", description = "AI scoring failed")
    })
    @PostMapping(value = "/ats/score", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> getAtsScore(
            @RequestHeader("X-Username") String username,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestParam("file") MultipartFile file) {

        try {
            log.info("Received ATS score request from user {}: {}", username, file.getOriginalFilename());

            // 1. Check credits
            try {
                Map<String, Boolean> creditCheck = paymentClient.checkCredits(username, 1);
                if (!creditCheck.getOrDefault("hasCredits", false)) {
                    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                            .body(Map.of(
                                "error", "Insufficient credits",
                                "message", "Please purchase credits to use ATS scoring feature",
                                "requiresPayment", true
                            ));
                }
            } catch (Exception e) {
                log.error("Error checking credits: ", e);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Payment service unavailable. Please try again later."));
            }

            // 2. Extract text
            String extractedText = parserClient.extractText(file);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Could not extract text from the file. Please check the file."));
            }

            // 3. Get ATS score
            ATSScoreDTO atsScoreDTO = geminiService.getATSScore(extractedText);
            if (atsScoreDTO == null) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to generate ATS score"));
            }

            // 4. Deduct credits
            try {
                paymentClient.useCredits(username, userEmail, 1, "ATS Score Analysis", file.getOriginalFilename());
                log.info("Credits deducted for user {}", username);
            } catch (Exception e) {
                log.error("Error deducting credits (analysis completed): ", e);
            }

            // 5. Publish Kafka event (best-effort)
            try {
                eventPublisher.publishAtsScore(
                    username,
                    userEmail,
                    file.getOriginalFilename(),
                    atsScoreDTO.getAtsScore(),
                    atsScoreDTO.getFeedback(),
                    atsScoreDTO.getSuggestions()
                );
            } catch (Exception ke) {
                log.warn("Kafka publish failed for ATS_SCORE_RESULT: {}", ke.getMessage());
            }

            return ResponseEntity.ok(atsScoreDTO);

        } catch (Exception e) {
            log.error("Error processing ATS score: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process ATS score: " + e.getMessage()));
        }
    }
}
