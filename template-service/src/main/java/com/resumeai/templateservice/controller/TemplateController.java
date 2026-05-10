package com.resumeai.templateservice.controller;

import lombok.extern.slf4j.Slf4j;

import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * Public endpoints - consumed by Resume Service via Feign client and by the frontend.
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Tag(name = "Templates (Public)", description = "Public-facing endpoints for fetching available resume templates")
public class TemplateController {

    private final TemplateService templateService;

    // GET /api/templates — all active templates (used by frontend gallery)
    @Operation(summary = "Get all active templates", description = "Returns a list of all active templates for the gallery.")
    @ApiResponse(responseCode = "200", description = "Active templates retrieved successfully")
    @GetMapping
    public ResponseEntity<List<ResumeTemplate>> getAllActive() {
        return ResponseEntity.ok(templateService.getAllActive());
    }

    // GET /api/templates/{id} — single template by id (called by Resume Service via Feign)
    @Operation(summary = "Get template by ID", description = "Returns a single template by its ID.")
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

    // GET /api/templates/category/{cat}
    @Operation(summary = "Get templates by category", description = "Returns active templates filtered by category.")
    @ApiResponse(responseCode = "200", description = "Templates retrieved successfully")
    @GetMapping("/category/{cat}")
    public ResponseEntity<List<ResumeTemplate>> getByCategory(@PathVariable String cat) {
        return ResponseEntity.ok(templateService.getByCategory(cat));
    }

    // GET /api/templates/top — ordered by usage count
    @Operation(summary = "Get top templates", description = "Returns templates ordered by usage count.")
    @ApiResponse(responseCode = "200", description = "Top templates retrieved successfully")
    @GetMapping("/top")
    public ResponseEntity<List<ResumeTemplate>> getTopTemplates() {
        return ResponseEntity.ok(templateService.getTopByUsage());
    }

    // POST /api/templates/{id}/usage — called by Resume Service via Feign after PDF generation
    @Operation(summary = "Increment template usage", description = "Increments the usage count for a specific template.")
    @ApiResponse(responseCode = "200", description = "Usage count incremented")
    @PostMapping("/{id}/usage")
    public ResponseEntity<Map<String, String>> incrementUsage(@PathVariable Long id) {
        templateService.incrementUsage(id);
        return ResponseEntity.ok(Map.of("message", "Usage count incremented for template " + id));
    }

    // GET /api/templates/{id}/image
    @Operation(summary = "Get template preview image", description = "Returns the binary image data for a template preview or redirects to Cloudinary.")
    @ApiResponse(responseCode = "200", description = "Image retrieved successfully")
    @GetMapping(value = "/{id}/image")
    public ResponseEntity<Void> getTemplateImage(@PathVariable Long id) {
        java.util.Optional<ResumeTemplate> template = templateService.getById(id);
        if (template.isPresent() && template.get().getPreviewImageUrl() != null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                    .location(java.net.URI.create(template.get().getPreviewImageUrl()))
                    .build();
        }
        return ResponseEntity.notFound().build();
    }
}
