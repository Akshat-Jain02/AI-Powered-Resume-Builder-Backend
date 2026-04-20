package com.resumeai.templateservice.controller;

import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.TemplateService;
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

/**
 * Admin endpoints for template management.
 * Admins can only activate / deactivate templates — no create or delete.
 * Prefix: /api/templates/admin
 */
@Slf4j
@RestController
@RequestMapping("/api/templates/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Templates (Admin)", description = "CRUD operations for system templates")
public class AdminTemplateController {

    private final TemplateService templateService;

    /** GET /api/templates/admin — all templates (active + inactive) */
    @Operation(summary = "Get all templates (including inactive)", description = "Returns all templates for admin management.")
    @ApiResponse(responseCode = "200", description = "Templates retrieved successfully")
    @GetMapping
    public ResponseEntity<List<ResumeTemplate>> getAll() {
        return ResponseEntity.ok(templateService.getAll());
    }

    /** GET /api/templates/admin/{id} — single template by id */
    @Operation(summary = "Get template by ID", description = "Returns single template. Used by admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template found"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ResumeTemplate> getById(@PathVariable Long id) {
        return templateService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PATCH /api/templates/admin/{id}/toggle — activate or deactivate a template */
    @Operation(summary = "Toggle template status", description = "Activates or deactivates a template.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ResumeTemplate> toggleActive(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(templateService.toggleActive(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
