package com.resumeai.templateservice.service;

import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.repository.ResumeTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final ResumeTemplateRepository templateRepository;

    // ── Seed default templates on startup if DB is empty ──────────────────────
    @PostConstruct
    public void seedTemplates() {
        if (templateRepository.count() == 0) {
            log.info("Seeding default resume templates...");
            templateRepository.save(build("Executive Classic", "PROFESSIONAL",
                "A timeless two-column layout trusted by senior executives and corporate professionals.",
                false, "linear-gradient(135deg, #1a3a5c 0%, #2d6a9f 100%)", "#1a3a5c", "template1"));
            templateRepository.save(build("Modern Slate", "MODERN",
                "Clean lines and bold typography for tech professionals and product managers.",
                false, "linear-gradient(135deg, #2d3748 0%, #4a5568 100%)", "#2d3748", "template2"));
            templateRepository.save(build("Creative Crimson", "CREATIVE",
                "A bold, design-forward layout for creatives, marketers, and brand strategists.",
                true, "linear-gradient(135deg, #c0392b 0%, #e74c3c 100%)", "#c0392b", "template3"));
            templateRepository.save(build("Minimalist Ivory", "MINIMALIST",
                "Elegant whitespace and refined typography. Let your work do the talking.",
                false, "linear-gradient(135deg, #8b7355 0%, #a08060 100%)", "#8b7355", "template4"));
            templateRepository.save(build("ATS Optimised Pro", "ATS_OPTIMISED",
                "Single-column, keyword-rich format designed to pass ATS screening with maximum score.",
                false, "linear-gradient(135deg, #2e7d32 0%, #43a047 100%)", "#2e7d32", "template5"));
            templateRepository.save(build("Sapphire Split", "MODERN",
                "A striking split-header design with a rich sidebar for impact-driven professionals.",
                true, "linear-gradient(135deg, #1565c0 0%, #1e88e5 100%)", "#1565c0", "template6"));
            log.info("Seeded 6 default templates.");
        }
    }

    private ResumeTemplate build(String name, String category, String description,
                                  boolean isPremium, String previewBg,
                                  String accentColor, String htmlTemplateName) {
        ResumeTemplate t = new ResumeTemplate();
        t.setName(name);
        t.setCategory(category);
        t.setDescription(description);
        t.setPremium(isPremium);
        t.setActive(true);
        t.setPreviewBg(previewBg);
        t.setAccentColor(accentColor);
        t.setHtmlTemplateName(htmlTemplateName);
        t.setCreatedAt(LocalDate.now());
        return t;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public List<ResumeTemplate> getAllActive() {
        return templateRepository.findByIsActiveTrue();
    }

    public List<ResumeTemplate> getAll() {
        return templateRepository.findAll();
    }

    public Optional<ResumeTemplate> getById(Long id) {
        return templateRepository.findById(id);
    }

    public List<ResumeTemplate> getByCategory(String category) {
        return templateRepository.findByCategoryIgnoreCase(category);
    }

    public List<ResumeTemplate> getTopByUsage() {
        return templateRepository.findByIsActiveTrueOrderByUsageCountDesc();
    }

    // ── Admin Operations ───────────────────────────────────────────────────────

    @Transactional
    public ResumeTemplate toggleActive(Long id) {
        ResumeTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));
        t.setActive(!t.isActive());
        ResumeTemplate saved = templateRepository.save(t);
        log.info("Admin {} template: id={}, name={}", saved.isActive() ? "activated" : "deactivated", id, saved.getName());
        return saved;
    }

    @Transactional
    public void incrementUsage(Long id) {
        templateRepository.findById(id).ifPresent(t -> {
            t.setUsageCount(t.getUsageCount() + 1);
            templateRepository.save(t);
        });
    }
}
