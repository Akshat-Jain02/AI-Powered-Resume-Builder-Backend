package com.resumeai.templateservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Uploads a file to Cloudinary with a deterministic public ID.
     * @param file The file to upload
     * @param folder The folder to store the file in (e.g. "templates")
     * @param fileName The preferred filename (will be hashed to prevent duplicates)
     * @return Map with url and public_id
     */
    public Map<String, String> uploadFile(MultipartFile file, String folder, String fileName) {
        try {
            byte[] fileBytes = file.getBytes();
            String hash = calculateHash(fileBytes);
            String publicId = folder + "/" + hash;

            log.info("Uploading asset to Cloudinary with ID: {}", publicId);
            Map<?, ?> uploadResult = cloudinary.uploader().upload(fileBytes, 
                    ObjectUtils.asMap("public_id", publicId, "overwrite", true));
            
            return Map.of(
                "url", (String) uploadResult.get("secure_url"),
                "public_id", (String) uploadResult.get("public_id")
            );
        } catch (IOException e) {
            throw new com.resumeai.templateservice.exception.TemplateServiceException("Failed to upload image to Cloudinary", e);
        }
    }

    public void deleteFile(String publicId) {
        try {
            log.info("Deleting asset from Cloudinary: {}", publicId);
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.error("Failed to delete Cloudinary asset {}: {}", publicId, e.getMessage());
        }
    }

    private String calculateHash(byte[] bytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
