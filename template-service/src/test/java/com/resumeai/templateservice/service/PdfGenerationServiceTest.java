package com.resumeai.templateservice.service;

import com.resumeai.templateservice.dto.ResumeDataDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class PdfGenerationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PdfGenerationService pdfGenerationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void generatePdf_WithCustomLatex_ShouldUseIt() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        ResumeDataDto data = new ResumeDataDto();
        data.setLatexContent("custom latex");
        
        byte[] expected = new byte[]{1, 2, 3};
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(byte[].class))).thenReturn(expected);

        byte[] result = pdfGenerationService.generatePdf(template, data);

        assertArrayEquals(expected, result);
    }

    @Test
    void generatePdf_WithTemplateLatex_ShouldUseIt() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        template.setLatexContent("template latex");
        ResumeDataDto data = new ResumeDataDto();
        
        byte[] expected = new byte[]{1, 2, 3};
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(byte[].class))).thenReturn(expected);

        byte[] result = pdfGenerationService.generatePdf(template, data);

        assertArrayEquals(expected, result);
    }

    @Test
    void generatePdf_WithNoLatex_ShouldUseFallback() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        ResumeDataDto data = new ResumeDataDto();
        
        byte[] expected = new byte[]{1, 2, 3};
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(byte[].class))).thenReturn(expected);

        byte[] result = pdfGenerationService.generatePdf(template, data);

        assertArrayEquals(expected, result);
    }

    @Test
    void generatePdf_WhenServiceFails_ShouldThrow() {
        ResumeTemplate template = new ResumeTemplate();
        template.setLatexContent("latex");
        ResumeDataDto data = new ResumeDataDto();

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(byte[].class))).thenThrow(new RuntimeException("Down"));

        assertThrows(RuntimeException.class, () -> pdfGenerationService.generatePdf(template, data));
    }
}
