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
            log.error("Template-service PDF generation failed: status={} body={}",
                      e.status(), e.contentUTF8());
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
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
    public SavedResume saveResume(GeneratePdfRequest request, String username) throws Exception {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank when saving a resume");
        }

        TemplateDto template = fetchTemplate(request.getTemplateId());
        String resumeJson    = objectMapper.writeValueAsString(request.getResumeData());

        // Update existing record if the savedResumeId belongs to this user
        if (request.getSavedResumeId() != null) {
            Optional<SavedResume> existing =
                    savedResumeRepository.findByIdAndUsername(request.getSavedResumeId(), username);
            if (existing.isPresent()) {
                SavedResume r = existing.get();
                r.setTemplateId(request.getTemplateId());
                r.setTemplateName(template.getName());
                r.setFullName(nvl(request.getResumeData().getFullName()));
                r.setTargetJobTitle(nvl(request.getResumeData().getTargetJobTitle()));
                r.setResumeData(resumeJson);
                SavedResume saved = savedResumeRepository.save(r);
                log.info("Updated resume id={} for user={}", saved.getId(), username);
                return saved;
            }
        }

        // Create new record — always bind to the authenticated user
        SavedResume resume = new SavedResume();
        resume.setUsername(username);
        resume.setTemplateId(request.getTemplateId());
        resume.setTemplateName(template.getName());
        resume.setFullName(nvl(request.getResumeData().getFullName()));
        resume.setTargetJobTitle(nvl(request.getResumeData().getTargetJobTitle()));
        resume.setResumeData(resumeJson);

        SavedResume saved = savedResumeRepository.save(resume);
        log.info("Created resume id={} for user={}", saved.getId(), username);
        return saved;
    }

    // ── Queries — all strictly scoped to the calling user ─────────────────────

    public List<SavedResume> getSavedResumesByUser(String username) {
        if (username == null || username.isBlank()) return Collections.emptyList();
        return savedResumeRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public Optional<SavedResume> getSavedResumeByIdAndUser(Long id, String username) {
        return savedResumeRepository.findByIdAndUsername(id, username);
    }

    public ResumeDataDto getSavedResumeData(Long id, String username) throws Exception {
        SavedResume r = savedResumeRepository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new RuntimeException(
                        "Resume " + id + " not found or does not belong to user " + username));
        return objectMapper.readValue(r.getResumeData(), ResumeDataDto.class);
    }

    @Transactional
    public void deleteSavedResume(Long id, String username) {
        SavedResume r = savedResumeRepository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new RuntimeException(
                        "Resume " + id + " not found or does not belong to user " + username));
        savedResumeRepository.delete(r);
        log.info("Deleted resume id={} for user={}", id, username);
    }

    /**
     * Re-generates the PDF for a saved resume by replaying the stored data
     * through template-service. Photo data is included if it was saved.
     */
    public byte[] regeneratePdfForSaved(Long savedResumeId, String username) throws Exception {
        SavedResume r = savedResumeRepository.findByIdAndUsername(savedResumeId, username)
                .orElseThrow(() -> new RuntimeException(
                        "Resume " + savedResumeId + " not found or does not belong to user " + username));
        ResumeDataDto data = objectMapper.readValue(r.getResumeData(), ResumeDataDto.class);

        GeneratePdfRequest req = new GeneratePdfRequest();
        req.setTemplateId(r.getTemplateId());
        req.setResumeData(data);
        return generatePdf(req);
    }

    // ── Admin / analytics helpers ──────────────────────────────────────────────

    public List<SavedResume> getResumesByTemplate(Long templateId) {
        return savedResumeRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private TemplateDto fetchTemplate(Long templateId) {
        try {
            TemplateDto t = templateServiceClient.getTemplateById(templateId);
            if (t == null) throw new RuntimeException("Template not found: " + templateId);
            return t;
        } catch (FeignException.NotFound e) {
            throw new RuntimeException("Template not found: " + templateId);
        } catch (FeignException e) {
            log.error("Template Service unavailable: {}", e.getMessage());
            throw new RuntimeException("Template Service is unavailable");
        }
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
