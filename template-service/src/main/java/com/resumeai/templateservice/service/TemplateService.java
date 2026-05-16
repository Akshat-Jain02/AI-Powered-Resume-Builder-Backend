package com.resumeai.templateservice.service;

import com.resumeai.templateservice.dto.TemplateRequestDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.repository.ResumeTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final ResumeTemplateRepository templateRepository;
    private final CloudinaryService cloudinaryService;

    // ── Public API ─────────────────────────────────────────────────────────────

    @org.springframework.cache.annotation.Cacheable(value = "templates", key = "'active'")
    public List<ResumeTemplate> getAllActive() {
        return templateRepository.findByIsActiveTrue();
    }

    @org.springframework.cache.annotation.Cacheable(value = "templates", key = "'all'")
    public List<ResumeTemplate> getAll() {
        return templateRepository.findAll();
    }

    @org.springframework.cache.annotation.Cacheable(value = "templates", key = "#id")
    public Optional<ResumeTemplate> getById(Long id) {
        return templateRepository.findById(id);
    }

    @org.springframework.cache.annotation.Cacheable(value = "templates", key = "'category:' + #category")
    public List<ResumeTemplate> getByCategory(String category) {
        return templateRepository.findByCategoryIgnoreCase(category);
    }

    @org.springframework.cache.annotation.Cacheable(value = "templates", key = "'top_usage'")
    public List<ResumeTemplate> getTopByUsage() {
        return templateRepository.findByIsActiveTrueOrderByUsageCountDesc();
    }

    // ── Admin Operations ───────────────────────────────────────────────────────
    
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "templates", allEntries = true)
    public ResumeTemplate create(TemplateRequestDto dto, 
                               MultipartFile latexFile,
                               MultipartFile imageFile) throws IOException {
        
        ResumeTemplate t = new ResumeTemplate();
        t.setName(dto.getName());
        t.setCategory(dto.getCategory());
        t.setDescription(dto.getDescription());
        t.setPremium(dto.isPremium());
        t.setPreviewBg(dto.getPreviewBg());
        t.setAccentColor(dto.getAccentColor());
        t.setActive(true);
        t.setCreatedAt(LocalDate.now());
        
        // Process files
        log.debug("Reading LaTeX content from file: {}", latexFile.getOriginalFilename());
        t.setLatexContent(new String(latexFile.getBytes(), StandardCharsets.UTF_8));
        
        // Upload image to Cloudinary and store URL + Public ID
        log.info("Uploading template preview image to Cloudinary...");
        Map<String, String> uploadResult = cloudinaryService.uploadFile(imageFile, "templates", dto.getName());
        t.setPreviewImageUrl(uploadResult.get("url"));
        t.setPreviewImagePublicId(uploadResult.get("public_id"));
        log.info("Cloudinary upload successful. URL: {}", uploadResult.get("url"));
        
        // Set a dummy templateId for now or use the next available
        t.setTemplateId((int) (templateRepository.count() + 1));
        
        log.info("Persisting new template record: {}", t.getName());
        ResumeTemplate saved = templateRepository.save(t);
        log.info("Template successfully created with ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "templates", allEntries = true)
    public void delete(Long id) {
        ResumeTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new com.resumeai.templateservice.exception.TemplateServiceException("Template not found: " + id));
        
        // Delete image from Cloudinary if it exists
        if (t.getPreviewImagePublicId() != null) {
            log.info("Deleting associated Cloudinary image for template: {}", id);
            cloudinaryService.deleteFile(t.getPreviewImagePublicId());
        }
        
        templateRepository.delete(t);
        log.info("Template record deleted successfully for ID: {}", id);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "templates", allEntries = true)
    public ResumeTemplate toggleActive(Long id) {
        ResumeTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new com.resumeai.templateservice.exception.TemplateServiceException("Template not found: " + id));
        t.setActive(!t.isActive());
        log.info("Toggling activity for Template ID: {}. New status: ACTIVE={}", id, t.isActive());
        return templateRepository.save(t);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "templates", allEntries = true)
    public void incrementUsage(Long id) {
        templateRepository.findById(id).ifPresent(t -> {
            t.setUsageCount(t.getUsageCount() + 1);
            templateRepository.save(t);
        });
    }
}
