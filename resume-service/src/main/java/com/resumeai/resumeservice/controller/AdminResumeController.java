package com.resumeai.resumeservice.controller;

import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.repository.SavedResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-only endpoints for resume management.
 *
 * Security is enforced at two levels:
 *   1. SecurityConfig — /api/resume/admin/** requires hasRole("ADMIN")
 *   2. @PreAuthorize — method-level double-check via @EnableMethodSecurity
 *
 * All routes: /api/resume/admin/**
 */
@Slf4j
@RestController
@RequestMapping("/api/resume/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Resumes", description = "System-wide resume analytics and management")
public class AdminResumeController {

    private final SavedResumeRepository savedResumeRepository;

    /**
     * GET /api/resume/admin/all
     * List every saved resume across all users, newest first.
     */
    @Operation(summary = "Get all saved resumes", description = "List every saved resume across all users, newest first.")
    @ApiResponse(responseCode = "200", description = "Resumes retrieved successfully")
    @GetMapping("/all")
    public ResponseEntity<List<SavedResume>> getAllResumes() {
        List<SavedResume> resumes = savedResumeRepository.findAllByOrderByCreatedAtDesc();
        log.info("Admin fetched all {} saved resumes", resumes.size());
        return ResponseEntity.ok(resumes);
    }

    /**
     * GET /api/resume/admin/user/{username}
     * List all resumes belonging to a specific user.
     */
    @Operation(summary = "Get resumes by username", description = "List all resumes belonging to a specific user.")
    @ApiResponse(responseCode = "200", description = "Resumes retrieved successfully")
    @GetMapping("/user/{username}")
    public ResponseEntity<List<SavedResume>> getResumesByUser(@PathVariable String username) {
        List<SavedResume> resumes = savedResumeRepository.findByUsernameOrderByCreatedAtDesc(username);
        log.info("Admin fetched {} resumes for user '{}'", resumes.size(), username);
        return ResponseEntity.ok(resumes);
    }

    /**
     * GET /api/resume/admin/{id}
     * Fetch any single resume by its DB id (regardless of owner).
     */
    @Operation(summary = "Get resume by ID", description = "Fetch any single resume by its DB id (regardless of owner).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Resume not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> getResumeById(@PathVariable Long id) {
        return savedResumeRepository.findById(id)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/resume/admin/{id}
     * Permanently delete any resume by id, regardless of owner.
     */
    @Operation(summary = "Delete resume", description = "Permanently delete any resume by id, regardless of owner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Resume not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteResume(
            @PathVariable Long id,
            @RequestHeader(value = "X-Username", required = false) String adminUsername) {

        return savedResumeRepository.findById(id).map(r -> {
            savedResumeRepository.delete(r);
            log.warn("Admin '{}' deleted resume id={} owned by '{}'", adminUsername, id, r.getUsername());
            return ResponseEntity.ok((Object) Map.of(
                    "message", "Resume " + id + " deleted",
                    "formerOwner", r.getUsername() != null ? r.getUsername() : "unknown"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/resume/admin/stats
     * Aggregate statistics: total resumes, per-user counts, per-template counts.
     */
    @Operation(summary = "Get resume statistics", description = "Aggregate statistics: total resumes, per-user counts, per-template counts.")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getResumeStats() {
        List<SavedResume> all = savedResumeRepository.findAllByOrderByCreatedAtDesc();

        long total = all.size();

        // Count resumes per user
        Map<String, Long> perUser = all.stream()
                .filter(r -> r.getUsername() != null)
                .collect(Collectors.groupingBy(SavedResume::getUsername, Collectors.counting()));

        // Count resumes per template
        Map<String, Long> perTemplate = all.stream()
                .filter(r -> r.getTemplateName() != null)
                .collect(Collectors.groupingBy(SavedResume::getTemplateName, Collectors.counting()));

        long uniqueUsers = perUser.size();

        Map<String, Object> stats = Map.of(
                "totalResumes", total,
                "uniqueUsers", uniqueUsers,
                "resumesPerUser", perUser,
                "resumesPerTemplate", perTemplate
        );

        log.info("Admin fetched resume stats: total={}, uniqueUsers={}", total, uniqueUsers);
        return ResponseEntity.ok(stats);
    }
}
