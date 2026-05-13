package com.resumeai.resumeservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Uploads a file to Cloudinary with a deterministic public ID based on user and content.
     * This prevents duplicate storage for the same user and file.
     *
     * @param file The file to upload
     * @param username The authenticated username to namespace the file
     * @return Map with "url" and "public_id"
     */
    public Map<String, String> uploadFile(MultipartFile file, String username) {
        try {
            log.info("Processing file upload for user: {} file: {}", username, file.getOriginalFilename());
            byte[] fileBytes = file.getBytes();
            
            // Generate a deterministic public_id based on user and content hash
            String contentHash = calculateHash(fileBytes);
            String publicId = "users/" + username + "/" + contentHash;

            log.info("Uploading/Updating asset in Cloudinary with ID: {}", publicId);
            Map<?, ?> uploadResult = cloudinary.uploader().upload(fileBytes, 
                    ObjectUtils.asMap("public_id", publicId, "overwrite", true));
            
            String url = (String) uploadResult.get("secure_url");
            String resultPublicId = (String) uploadResult.get("public_id");
            
            log.info("File upload processed. URL: {}, PublicID: {}", url, resultPublicId);
            return Map.of("url", url, "public_id", resultPublicId);
        } catch (IOException e) {
            log.error("Cloudinary upload failed", e);
            throw new com.resumeai.resumeservice.exception.ResumeServiceException("Failed to upload image to Cloudinary", e);
        }
    }

    private String calculateHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Deletes a file from Cloudinary using its public ID.
     * @param publicId The public ID of the asset to delete
     */
    public void deleteFile(String publicId) {
        try {
            log.info("Deleting file from Cloudinary: {}", publicId);
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Cloudinary delete result: {}", result);
        } catch (IOException e) {
            log.error("Cloudinary deletion failed for {}: {}", publicId, e.getMessage());
            throw new com.resumeai.resumeservice.exception.ResumeServiceException("Failed to delete image from Cloudinary", e);
        }
    }
}
