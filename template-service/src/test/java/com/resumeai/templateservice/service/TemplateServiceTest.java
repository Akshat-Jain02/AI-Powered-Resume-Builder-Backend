package com.resumeai.templateservice.service;

import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.repository.ResumeTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private ResumeTemplateRepository templateRepository;

    @InjectMocks private TemplateService templateService;

    private ResumeTemplate template;

    @BeforeEach
    void setUp() {
        template = new ResumeTemplate();
        template.setId(1L);
        template.setName("Modern Slate");
        template.setCategory("MODERN");
        template.setActive(true);
        template.setUsageCount(10);
    }

    @Test
    void seedTemplates_whenEmpty_savesDefaultTemplates() {
        when(templateRepository.count()).thenReturn(0L);

        templateService.seedTemplates();

        verify(templateRepository, times(6)).save(any(ResumeTemplate.class));
    }

    @Test
    void seedTemplates_whenNotEmpty_doesNotSeed() {
        when(templateRepository.count()).thenReturn(3L);

        templateService.seedTemplates();

        verify(templateRepository, never()).save(any());
    }

    @Test
    void getAllActive_returnsActiveTemplates() {
        when(templateRepository.findByIsActiveTrue()).thenReturn(List.of(template));

        List<ResumeTemplate> result = templateService.getAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    void getAll_returnsAllTemplates() {
        when(templateRepository.findAll()).thenReturn(List.of(template));

        List<ResumeTemplate> result = templateService.getAll();

        assertThat(result).hasSize(1);
    }

    @Test
    void getById_found() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThat(templateService.getById(1L)).isPresent();
    }

    @Test
    void getById_notFound() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(templateService.getById(999L)).isEmpty();
    }

    @Test
    void getByCategory_delegatesToRepository() {
        when(templateRepository.findByCategoryIgnoreCase("MODERN")).thenReturn(List.of(template));

        List<ResumeTemplate> result = templateService.getByCategory("MODERN");
        assertThat(result).hasSize(1);
    }

    @Test
    void getTopByUsage_delegatesToRepository() {
        when(templateRepository.findByIsActiveTrueOrderByUsageCountDesc()).thenReturn(List.of(template));

        List<ResumeTemplate> result = templateService.getTopByUsage();
        assertThat(result).hasSize(1);
    }

    @Test
    void toggleActive_activatesToDeactivated() {
        template.setActive(true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(template)).thenReturn(template);

        ResumeTemplate result = templateService.toggleActive(1L);

        assertThat(result.isActive()).isFalse(); // toggled from true to false
        verify(templateRepository).save(template);
    }

    @Test
    void toggleActive_notFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.toggleActive(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void incrementUsage_found_incrementsCount() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        templateService.incrementUsage(1L);

        assertThat(template.getUsageCount()).isEqualTo(11);
        verify(templateRepository).save(template);
    }

    @Test
    void incrementUsage_notFound_doesNothing() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        templateService.incrementUsage(999L);

        verify(templateRepository, never()).save(any());
    }
}
