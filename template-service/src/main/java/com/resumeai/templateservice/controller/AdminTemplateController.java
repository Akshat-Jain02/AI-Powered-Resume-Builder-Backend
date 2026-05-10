package com.resumeai.templateservice.controller;

import com.resumeai.templateservice.dto.TemplateRequestDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
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

    @Operation(summary = "Toggle template status", description = "Activates or deactivates a template.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ResumeTemplate> toggleActive(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(templateService.toggleActive(id));
        } catch (com.resumeai.templateservice.exception.TemplateServiceException _) {
            return ResponseEntity.notFound().build();
        }
    }

    /** POST /api/templates/admin — create a new template with files */
    @Operation(summary = "Create new template", description = "Uploads .latex file and preview image to create a new template.")
    @ApiResponse(responseCode = "201", description = "Template created successfully")
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<ResumeTemplate> createTemplate(
            @RequestPart("template") TemplateRequestDto dto,
            @RequestPart("latex") MultipartFile latexFile,
            @RequestPart("image") MultipartFile imageFile) throws IOException {
        
        ResumeTemplate template = templateService.create(dto, latexFile, imageFile);
        return new ResponseEntity<>(template, HttpStatus.CREATED);
    }

    /** DELETE /api/templates/admin/{id} — delete template */
    @Operation(summary = "Delete template", description = "Permanently removes a template from the system.")
    @ApiResponse(responseCode = "204", description = "Template deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
