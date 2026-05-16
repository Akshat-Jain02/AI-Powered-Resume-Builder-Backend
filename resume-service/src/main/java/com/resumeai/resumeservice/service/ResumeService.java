package com.resumeai.resumeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.resumeservice.client.TemplateServiceClient;
import com.resumeai.resumeservice.dto.GeneratePdfRequest;
import com.resumeai.resumeservice.dto.PdfGenerationRequestDto;
import com.resumeai.resumeservice.dto.ResumeDataDto;
import com.resumeai.resumeservice.dto.TemplateDto;
import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.repository.SavedResumeRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Manages saved resume records (CRUD) and coordinates PDF generation by
 * delegating rendering to template-service via Feign.
 *
 * This service deliberately contains NO LaTeX or HTML rendering logic —
 * all PDF generation concerns live exclusively in template-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeService {

    private final SavedResumeRepository  savedResumeRepository;
    private final TemplateServiceClient  templateServiceClient;
    private final ObjectMapper           objectMapper;

    // ── PDF Generation — delegates to template-service ─────────────────────────

    /**
     * Requests a PDF from template-service for the given template + resume data.
     * The raw PDF bytes are returned directly to the caller for streaming.
     */
    public byte[] generatePdf(GeneratePdfRequest request) {
        log.debug("Delegating PDF generation to template-service for templateId={}", request.getTemplateId());
        PdfGenerationRequestDto pdfRequest = new PdfGenerationRequestDto();
        pdfRequest.setTemplateId(request.getTemplateId());
        pdfRequest.setResumeData(request.getResumeData());
        try {
            return templateServiceClient.generatePdf(pdfRequest);
        } catch (FeignException e) {
            throw new IllegalStateException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    // ── Save (user-scoped) ─────────────────────────────────────────────────────

    /**
     * Persists or updates a saved resume for the given user.
     * If {@link GeneratePdfRequest#getSavedResumeId()} is set and the record
     * belongs to this user, the existing row is updated; otherwise a new row is
     * created.
     */
    @Transactional
    public SavedResume saveResume(GeneratePdfRequest request, String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank when saving a resume");
        }

        try {
            TemplateDto template = fetchTemplate(request.getTemplateId());
            String resumeJson    = objectMapper.writeValueAsString(request.getResumeData());

            // Update existing record if the savedResumeId belongs to this user
            if (request.getSavedResumeId() != null) {
                Optional<SavedResume> existing =
                        savedResumeRepository.findByIdAndUsername(request.getSavedResumeId(), username);
                if (existing.isPresent()) {
                    SavedResume r = existing.get();
                    log.info("Updating existing resume record: {} for user: {}", r.getId(), username);
                    r.setTemplateId(request.getTemplateId());
                    r.setTemplateName(template.getName());
                    r.setFullName(nvl(request.getResumeData().getFullName()));
                    r.setResumeData(resumeJson);
                    SavedResume saved = savedResumeRepository.save(r);
                    log.debug("Resume record successfully updated and persisted.");
                    return saved;
                }
            }

            // Create new record — always bind to the authenticated user
            log.info("Creating a new saved resume record for user: {} with template: {}", username, template.getName());
            SavedResume resume = new SavedResume();
            resume.setUsername(username);
            resume.setTemplateId(request.getTemplateId());
            resume.setTemplateName(template.getName());
            resume.setFullName(nvl(request.getResumeData().getFullName()));
            resume.setResumeData(resumeJson);
            
            SavedResume saved = savedResumeRepository.save(resume);
            log.info("Successfully created and persisted new resume record ID: {}", saved.getId());
            return saved;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to serialize resume data for user {}: {}", username, e.getMessage());
            throw new com.resumeai.resumeservice.exception.ResumeServiceException("Failed to process resume data for storage", e);
        }
    }

    // ── Queries — all strictly scoped to the calling user ─────────────────────

    public List<SavedResume> getSavedResumesByUser(String username) {
        if (username == null || username.isBlank()) return Collections.emptyList();
        return savedResumeRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public Optional<SavedResume> getSavedResumeByIdAndUser(Long id, String username) {
        return savedResumeRepository.findByIdAndUsername(id, username);
    }

    public ResumeDataDto getSavedResumeData(Long id, String username) {
        SavedResume r = savedResumeRepository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resume " + id + " not found or does not belong to user " + username));
        try {
            return objectMapper.readValue(r.getResumeData(), ResumeDataDto.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse stored resume data for ID {}: {}", id, e.getMessage());
            throw new com.resumeai.resumeservice.exception.ResumeServiceException("Corrupted resume data in storage", e);
        }
    }

    @Transactional
    public void deleteSavedResume(Long id, String username) {
        SavedResume r = savedResumeRepository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resume " + id + " not found or does not belong to user " + username));
        savedResumeRepository.delete(r);
    }

    /**
     * Re-generates the PDF for a saved resume by replaying the stored data
     * through template-service. Photo data is included if it was saved.
     */
    public byte[] regeneratePdfForSaved(Long savedResumeId, String username) {
        SavedResume r = savedResumeRepository.findByIdAndUsername(savedResumeId, username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resume " + savedResumeId + " not found or does not belong to user " + username));
        try {
            ResumeDataDto data = objectMapper.readValue(r.getResumeData(), ResumeDataDto.class);

            GeneratePdfRequest req = new GeneratePdfRequest();
            req.setTemplateId(r.getTemplateId());
            req.setResumeData(data);
            return generatePdf(req);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse stored resume data for regeneration ID {}: {}", savedResumeId, e.getMessage());
            throw new com.resumeai.resumeservice.exception.ResumeServiceException("Failed to regenerate PDF from stored data", e);
        }
    }


    // ── Admin / analytics helpers ──────────────────────────────────────────────

    public List<SavedResume> getResumesByTemplate(Long templateId) {
        return savedResumeRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private TemplateDto fetchTemplate(Long templateId) {
        log.debug("Fetching template metadata from template-service for ID: {}", templateId);
        try {
            TemplateDto t = templateServiceClient.getTemplateById(templateId);
            if (t == null) {
                log.error("Template Service returned null for ID: {}", templateId);
                throw new IllegalArgumentException("Template not found: " + templateId);
            }
            log.debug("Template metadata retrieved successfully: {}", t.getName());
            return t;
        } catch (FeignException.NotFound e) {
            log.warn("Template ID: {} not found in Template Service.", templateId);
            throw new IllegalArgumentException("Template not found: " + templateId);
        } catch (FeignException e) {
            log.error("Template Service communication error: {} {}", e.status(), e.getMessage());
            throw new IllegalStateException("Template Service is unavailable: " + e.status() + " " + e.getMessage());
        }
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
