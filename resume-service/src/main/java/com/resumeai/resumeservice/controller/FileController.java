package com.resumeai.resumeservice.controller;

import com.resumeai.resumeservice.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/resume/files")
@RequiredArgsConstructor
@Tag(name = "Resume Files", description = "Endpoints for managing resume project files (images, etc.)")
public class FileController {

    private final CloudinaryService cloudinaryService;
    private static final String ERROR_KEY = "error";

    @Operation(summary = "Upload a project file", description = "Uploads an image to Cloudinary and returns the secure URL.")
    @ApiResponse(responseCode = "200", description = "File uploaded successfully")
    @PostMapping("/upload")
    public ResponseEntity<Object> uploadFile(
            @RequestHeader(value = "X-Username", required = false) String xUsername,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "File is empty"));
        }
        try {
            String username = (xUsername != null && !xUsername.isBlank()) ? xUsername : "anonymous";
            Map<String, String> result = cloudinaryService.uploadFile(file, username);
            return ResponseEntity.ok((Object) Map.of(
                "url", result.get("url"),
                "public_id", result.get("public_id"),
                "name", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                "size", file.getSize(),
                "type", file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            ));
        } catch (Exception e) {
            log.error("File upload failed for {}: {}", file.getOriginalFilename(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, "File upload failed."));
        }
    }

    @Operation(summary = "Delete a project file", description = "Deletes an asset from Cloudinary using its public ID.")
    @ApiResponse(responseCode = "200", description = "File deleted successfully")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Object> deleteFile(@PathVariable String publicId) {
        try {
            cloudinaryService.deleteFile(publicId);
            return ResponseEntity.ok(Map.of("message", "File " + publicId + " deleted successfully"));
        } catch (Exception e) {
            log.error("File deletion failed for {}: {}", publicId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, "File deletion failed."));
        }
    }
}
