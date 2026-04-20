package com.resumeai.resumeservice.controller;

import com.resumeai.resumeservice.dto.GeneratePdfRequest;
import com.resumeai.resumeservice.dto.ResumeDataDto;
import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String name) {
            if (!name.isBlank() && !name.equals("anonymousUser")) return name;
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
    public ResponseEntity<?> generatePdf(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @Valid @RequestBody GeneratePdfRequest request) {
        try {
            byte[] pdf = resumeService.generatePdf(request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "PDF generation failed: " + e.getMessage()));
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
    public ResponseEntity<?> saveResume(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @Valid @RequestBody GeneratePdfRequest request) {
        try {
            String username = currentUser(xUsername);
            log.info("Saving resume for user: {}", username);
            SavedResume saved = resumeService.saveResume(request, username);
            return ResponseEntity.ok(saved);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Save resume failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save resume"));
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
    public ResponseEntity<?> saveAndDownload(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @Valid @RequestBody GeneratePdfRequest request) {
        try {
            String username = currentUser(xUsername);
            resumeService.saveResume(request, username);
            byte[] pdf = resumeService.generatePdf(request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Save-and-download failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Save-and-download failed: " + e.getMessage()));
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
    public ResponseEntity<?> getAllSaved(
            @RequestHeader(value = "X-Username", required = false) String xUsername) {
        try {
            String username = currentUser(xUsername);
            log.info("Fetching saved resumes for user: {}", username);
            List<SavedResume> resumes = resumeService.getSavedResumesByUser(username);
            log.info("Found {} saved resumes for user: {}", resumes.size(), username);
            return ResponseEntity.ok(resumes);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
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
    public ResponseEntity<?> getSavedById(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @PathVariable Long id) {
        try {
            String username = currentUser(xUsername);
            return resumeService.getSavedResumeByIdAndUser(id, username)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
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
    public ResponseEntity<?> getSavedData(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @PathVariable Long id) {
        try {
            String username = currentUser(xUsername);
            ResumeDataDto data = resumeService.getSavedResumeData(id, username);
            return ResponseEntity.ok(data);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
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
    public ResponseEntity<?> downloadSaved(
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
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Download failed for resume id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate PDF for resume " + id));
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
    public ResponseEntity<?> deleteSaved(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @PathVariable Long id) {
        try {
            String username = currentUser(xUsername);
            resumeService.deleteSavedResume(id, username);
            return ResponseEntity.ok(Map.of("message", "Resume " + id + " deleted"));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
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
