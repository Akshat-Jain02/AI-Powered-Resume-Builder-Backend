package com.resumeai.resumeservice.controller;

import com.resumeai.resumeservice.dto.GeneratePdfRequest;
import com.resumeai.resumeservice.dto.ResumeDataDto;
import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * REST controller for resume operations.
 *
 * Responsibilities:
 *  - PDF generation (delegates rendering to template-service via ResumeService).
 *  - Saved-resume CRUD (list, get, get-data, delete).
 *  - Save-and-download convenience endpoint.
 *
 * All user-scoped endpoints are protected: the API Gateway injects the
 * authenticated username via the {@code X-Username} header, which the
 * {@code HeaderAuthenticationFilter} places into the Spring Security context.
 */
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Resumes", description = "Endpoints for generating, saving, and downloading user resumes")
public class ResumeController {

    private final ResumeService resumeService;

    // ── Authentication helper ──────────────────────────────────────────────────

    /**
     * Resolves the authenticated username from the Spring Security context
     * (populated by {@code HeaderAuthenticationFilter} from the X-Username header).
     * Falls back to the raw header value for resilience.
     *
     * @throws SecurityException if no authenticated user can be determined
     */
    private String currentUser(String headerFallback) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof String name) {
                if (!name.isBlank() && !name.equals("anonymousUser")) return name;
            } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                return userDetails.getUsername();
            }
        }
        if (headerFallback != null && !headerFallback.isBlank()) return headerFallback;
        throw new SecurityException("No authenticated user found. Token may be missing or expired.");
    }

    // ── PDF Generation ─────────────────────────────────────────────────────────

    /**
     * POST /api/resume/generate
     * Generates a PDF without persisting — useful for live preview + download.
     */
    @Operation(summary = "Generate PDF (Live Preview)", description = "Generates a PDF without persisting — useful for live preview + download.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated successfully"),
            @ApiResponse(responseCode = "500", description = "PDF generation failed")
    })
    @PostMapping("/generate")
    public ResponseEntity<Object> generatePdf(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @Valid @RequestBody GeneratePdfRequest request) {
        try {
            byte[] pdf = resumeService.generatePdf(request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (com.resumeai.resumeservice.exception.ResumeServiceException e) {
            log.error("PDF generation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "PDF generation failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during PDF generation", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "An unexpected error occurred during PDF generation."));
        }
    }

    // ── Save ───────────────────────────────────────────────────────────────────

    /**
     * POST /api/resume/save
     * Persists the resume data for the authenticated user (no PDF download).
     */
    @Operation(summary = "Save Resume", description = "Persists the resume data for the authenticated user (no PDF download).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume saved successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/save")
    public ResponseEntity<Object> saveResume(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @Valid @RequestBody GeneratePdfRequest request) {
        try {
            String username = currentUser(xUsername);
            SavedResume saved = resumeService.saveResume(request, username);
            return ResponseEntity.ok(saved);
        } catch (SecurityException e) {
            log.warn("Security violation while saving resume: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid save request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to save resume", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save resume. Please try again later."));
        }
    }

    // ── Save + Download ────────────────────────────────────────────────────────

    /**
     * POST /api/resume/save-and-download
     * Persists the resume data AND returns the generated PDF in one request.
     */
    @Operation(summary = "Save and Download Resume", description = "Persists the resume data AND returns the generated PDF in one request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume saved and PDF downloaded successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Save-and-download failed")
    })
    @PostMapping("/save-and-download")
    public ResponseEntity<Object> saveAndDownload(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @Valid @RequestBody GeneratePdfRequest request) {
        try {
            String username = currentUser(xUsername);
            SavedResume saved = resumeService.saveResume(request, username);
            byte[] pdf = resumeService.generatePdf(request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume_" + saved.getId() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (SecurityException e) {
            log.warn("Security violation during save-and-download: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Save-and-download failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Save-and-download failed."));
        }
    }

    // ── Saved Resume — List ────────────────────────────────────────────────────

    /**
     * GET /api/resume/saved
     * Returns all saved resumes belonging to the authenticated user.
     */
    @Operation(summary = "Get All Saved Resumes", description = "Returns all saved resumes belonging to the authenticated user.")
    @ApiResponse(responseCode = "200", description = "List of saved resumes retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @GetMapping("/saved")
    public ResponseEntity<Object> getAllSaved(
            @RequestHeader(value = "X-Username", required = false) String xUsername) {
        try {
            String username = currentUser(xUsername);
            List<SavedResume> resumes = resumeService.getSavedResumesByUser(username);
            return ResponseEntity.ok(resumes);
        } catch (SecurityException e) {
            log.warn("Security violation while listing resumes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Saved Resume — Get by ID ───────────────────────────────────────────────

    /**
     * GET /api/resume/saved/{id}
     * Returns the saved resume metadata for the given ID, scoped to the caller.
     */
    @Operation(summary = "Get Saved Resume by ID", description = "Returns the saved resume metadata for the given ID, scoped to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume metadata retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Resume not found")
    })
    @GetMapping("/saved/{id}")
    public ResponseEntity<Object> getSavedById(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @PathVariable Long id) {
        try {
            String username = currentUser(xUsername);
            return resumeService.getSavedResumeByIdAndUser(id, username)
                    .<ResponseEntity<Object>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (SecurityException e) {
            log.warn("Security violation while getting resume {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Saved Resume — Get Data ────────────────────────────────────────────────

    /**
     * GET /api/resume/saved/{id}/data
     * Returns the deserialized {@link ResumeDataDto} for populating the editor.
     */
    @Operation(summary = "Get Saved Resume Data", description = "Returns the deserialized ResumeDataDto for populating the editor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Resume data not found")
    })
    @GetMapping("/saved/{id}/data")
    public ResponseEntity<Object> getSavedData(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @PathVariable Long id) {
        try {
            String username = currentUser(xUsername);
            ResumeDataDto data = resumeService.getSavedResumeData(id, username);
            return ResponseEntity.ok(data);
        } catch (SecurityException e) {
            log.warn("Security violation while getting data for resume {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Resume data not found for ID: {} User: {}", id, xUsername);
            return ResponseEntity.notFound().build();
        }
    }

    // ── Saved Resume — Download ────────────────────────────────────────────────

    /**
     * GET /api/resume/saved/{id}/download
     * Re-generates and streams the PDF for an existing saved resume.
     * Photo and all resume data are replayed from the stored JSON.
     */
    @Operation(summary = "Download Saved Resume", description = "Re-generates and streams the PDF for an existing saved resume.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF regenerated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Resume not found"),
            @ApiResponse(responseCode = "500", description = "Failed to generate PDF")
    })
    @GetMapping("/saved/{id}/download")
    public ResponseEntity<Object> downloadSaved(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @PathVariable Long id) {
        try {
            String username = currentUser(xUsername);
            byte[] pdf = resumeService.regeneratePdfForSaved(id, username);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=resume_" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (SecurityException e) {
            log.warn("Security violation while downloading resume {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Resume not found for download: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to generate PDF for resume {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate PDF for resume."));
        }
    }

    // ── Saved Resume — Delete ──────────────────────────────────────────────────

    /**
     * DELETE /api/resume/saved/{id}
     * Permanently removes a saved resume, scoped to the authenticated user.
     */
    @Operation(summary = "Delete Saved Resume", description = "Permanently removes a saved resume, scoped to the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Resume not found")
    })
    @DeleteMapping("/saved/{id}")
    public ResponseEntity<Object> deleteSaved(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @PathVariable Long id) {
        try {
            String username = currentUser(xUsername);
            resumeService.deleteSavedResume(id, username);
            return ResponseEntity.ok(Map.of("message", "Resume " + id + " deleted"));
        } catch (SecurityException e) {
            log.warn("Security violation while deleting resume {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Delete failed - resume not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting resume {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete resume."));
        }
    }


    // ── Admin / Analytics ──────────────────────────────────────────────────────

    /**
     * GET /api/resume/saved/by-template/{templateId}
     * Returns all saved resumes for a given template (admin/analytics use).
     */
    @Operation(summary = "Get Resumes by Template ID", description = "Returns all saved resumes for a given template (admin/analytics use).")
    @ApiResponse(responseCode = "200", description = "Resumes retrieved successfully")
    @GetMapping("/saved/by-template/{templateId}")
    public ResponseEntity<List<SavedResume>> getByTemplate(@PathVariable Long templateId) {
        return ResponseEntity.ok(resumeService.getResumesByTemplate(templateId));
    }
}
