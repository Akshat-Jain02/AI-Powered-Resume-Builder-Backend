package com.resumeai.templateservice.service;

import com.resumeai.templateservice.dto.TemplateRequestDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.repository.ResumeTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemplateServiceTest {

    @Mock
    private ResumeTemplateRepository templateRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getAllActive_ShouldReturnActiveTemplates() {
        ResumeTemplate t = new ResumeTemplate();
        t.setActive(true);
        when(templateRepository.findByIsActiveTrue()).thenReturn(Arrays.asList(t));

        List<ResumeTemplate> result = templateService.getAllActive();

        assertEquals(1, result.size());
        assertTrue(result.get(0).isActive());
    }

    @Test
    void getById_ShouldReturnTemplate() {
        ResumeTemplate t = new ResumeTemplate();
        t.setId(1L);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));

        Optional<ResumeTemplate> result = templateService.getById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void create_ShouldSaveTemplateAndUploadImage() throws IOException {
        TemplateRequestDto dto = new TemplateRequestDto();
        dto.setName("Test Template");
        dto.setCategory("IT");
        
        MockMultipartFile latexFile = new MockMultipartFile("latex", "test.tex", "text/plain", "\\documentclass{article}".getBytes());
        MockMultipartFile imageFile = new MockMultipartFile("image", "test.png", "image/png", "dummy-image-data".getBytes());
        
        when(cloudinaryService.uploadFile(any(MultipartFile.class))).thenReturn("http://cloudinary.com/test.png");
        when(templateRepository.count()).thenReturn(5L);
        when(templateRepository.save(any(ResumeTemplate.class))).thenAnswer(i -> i.getArguments()[0]);

        ResumeTemplate result = templateService.create(dto, latexFile, imageFile);

        assertNotNull(result);
        assertEquals("Test Template", result.getName());
        assertEquals("http://cloudinary.com/test.png", result.getPreviewImageUrl());
        assertEquals("\\documentclass{article}", result.getLatexContent());
        assertEquals(6, result.getTemplateId());
        verify(cloudinaryService).uploadFile(imageFile);
        verify(templateRepository).save(any(ResumeTemplate.class));
    }

    @Test
    void delete_ShouldDelete_WhenTemplateExists() {
        when(templateRepository.existsById(1L)).thenReturn(true);

        templateService.delete(1L);

        verify(templateRepository).deleteById(1L);
    }

    @Test
    void delete_ShouldThrow_WhenTemplateDoesNotExist() {
        when(templateRepository.existsById(1L)).thenReturn(false);

        assertThrows(com.resumeai.templateservice.exception.TemplateServiceException.class, () -> templateService.delete(1L));
        verify(templateRepository, never()).deleteById(anyLong());
    }

    @Test
    void toggleActive_ShouldSwitchActiveStatus() {
        ResumeTemplate t = new ResumeTemplate();
        t.setId(1L);
        t.setActive(true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(templateRepository.save(any(ResumeTemplate.class))).thenAnswer(i -> i.getArguments()[0]);

        ResumeTemplate result = templateService.toggleActive(1L);

        assertFalse(result.isActive());
        verify(templateRepository).save(t);
    }

    @Test
    void incrementUsage_ShouldIncreaseCount() {
        ResumeTemplate t = new ResumeTemplate();
        t.setId(1L);
        t.setUsageCount(10);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));

        templateService.incrementUsage(1L);

        assertEquals(11, t.getUsageCount());
        verify(templateRepository).save(t);
    }
}
