package com.resumeai.templateservice.controller;

import com.resumeai.templateservice.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Endpoint for uploading images to Cloudinary.
 * Users can upload images (e.g. profile photos) and receive a public URL
 * that can be embedded directly in LaTeX code via \includegraphics.
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Tag(name = "Image Upload", description = "Upload images to get embeddable URLs for LaTeX code")
public class ImageUploadController {

    private final CloudinaryService cloudinaryService;

    /**
     * Uploads an image to Cloudinary and returns the secure URL.
     * The URL can be used directly in LaTeX code with \includegraphics{url}.
     *
     * @param file The image file to upload (JPEG, PNG, etc.)
     * @return JSON with the secure Cloudinary URL
     */
    @Operation(summary = "Upload image for LaTeX embedding",
               description = "Uploads an image to Cloudinary and returns a public URL that can be used in LaTeX \\includegraphics commands.")
    @ApiResponse(responseCode = "200", description = "Image uploaded successfully, URL returned")
    @ApiResponse(responseCode = "400", description = "No file provided or file is empty")
    @ApiResponse(responseCode = "500", description = "Upload failed")
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("┌─── Image Upload Request ───────────────────────────────────");
        log.info("│ File Name   : {}", file.getOriginalFilename());
        log.info("│ File Size   : {} bytes", file.getSize());
        log.info("│ Content Type: {}", file.getContentType());

        if (file.isEmpty()) {
            log.warn("│ Status: REJECTED — empty file");
            log.info("└────────────────────────────────────────────────────────────");
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty. Please select an image to upload."));
        }

        Map<String, String> uploadResult = cloudinaryService.uploadFile(file, "uploads", file.getOriginalFilename());
        String url = uploadResult.get("url");
        log.info("│ Cloudinary URL: {}", url);
        log.info("│ Status: SUCCESS");
        log.info("└────────────────────────────────────────────────────────────");

        return ResponseEntity.ok(Map.of("url", url));
    }
}
