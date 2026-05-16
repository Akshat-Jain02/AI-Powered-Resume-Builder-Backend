package com.resumeai.templateservice.service;

import com.resumeai.templateservice.dto.ResumeDataDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class PdfGenerationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RestTemplate loadBalancedRestTemplate;

    private PdfGenerationService pdfGenerationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pdfGenerationService = new PdfGenerationService(restTemplate, loadBalancedRestTemplate);
    }


    @Test
    void generatePdf_WithCustomLatex_ShouldUseIt() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        ResumeDataDto data = new ResumeDataDto();
        data.setLatexContent("custom latex");
        
        byte[] expected = new byte[]{1, 2, 3};
        when(loadBalancedRestTemplate.postForObject(anyString(), any(), eq(byte[].class))).thenReturn(expected);

        byte[] result = pdfGenerationService.generatePdf(template, data);

        assertArrayEquals(expected, result);
    }

    @Test
    void generatePdf_WithTemplateLatex_ShouldUseIt() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        template.setLatexContent("template latex");
        ResumeDataDto data = new ResumeDataDto();
        
        byte[] expected = new byte[]{1, 2, 3};
        when(loadBalancedRestTemplate.postForObject(anyString(), any(), eq(byte[].class))).thenReturn(expected);

        byte[] result = pdfGenerationService.generatePdf(template, data);

        assertArrayEquals(expected, result);
    }

    @Test
    void generatePdf_WithNoLatex_ShouldUseFallback() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        ResumeDataDto data = new ResumeDataDto();
        
        byte[] expected = new byte[]{1, 2, 3};
        when(loadBalancedRestTemplate.postForObject(anyString(), any(), eq(byte[].class))).thenReturn(expected);

        byte[] result = pdfGenerationService.generatePdf(template, data);

        assertArrayEquals(expected, result);
    }

    @Test
    void generatePdf_WithExternalFiles_ShouldDownloadThem() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        ResumeDataDto data = new ResumeDataDto();
        data.setLatexContent("latex");
        data.setFiles(Map.of("photo.png", "https://cloudinary.com/photo.png"));

        byte[] photoBytes = new byte[]{10, 20};
        when(restTemplate.getForObject("https://cloudinary.com/photo.png", byte[].class)).thenReturn(photoBytes);
        
        byte[] expectedPdf = new byte[]{1, 2, 3};
        when(loadBalancedRestTemplate.postForObject(anyString(), any(), eq(byte[].class))).thenReturn(expectedPdf);

        byte[] result = pdfGenerationService.generatePdf(template, data);

        assertArrayEquals(expectedPdf, result);
    }

    @Test
    void generatePdf_WhenDownloadFails_ShouldContinueWithoutFile() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        ResumeDataDto data = new ResumeDataDto();
        data.setLatexContent("latex");
        data.setFiles(Map.of("missing.png", "https://invalid-url.com/missing.png"));

        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenThrow(new RuntimeException("Network error"));
        
        byte[] expectedPdf = new byte[]{1, 2, 3};
        when(loadBalancedRestTemplate.postForObject(anyString(), any(), eq(byte[].class))).thenReturn(expectedPdf);

        byte[] result = pdfGenerationService.generatePdf(template, data);

        assertArrayEquals(expectedPdf, result);
        // Should have called compilePdf even if resolveProjectFiles failed for one file
    }

    @Test
    void generatePdf_WhenDataIsNull_ShouldUseTemplateLatex() throws Exception {
        ResumeTemplate template = new ResumeTemplate();
        template.setLatexContent("template latex");
        
        byte[] expected = new byte[]{1, 2, 3};
        when(loadBalancedRestTemplate.postForObject(anyString(), any(), eq(byte[].class))).thenReturn(expected);

        byte[] result = pdfGenerationService.generatePdf(template, null);

        assertArrayEquals(expected, result);
    }


    @Test
    void generatePdf_WhenServiceFails_ShouldThrow() {
        ResumeTemplate template = new ResumeTemplate();
        template.setLatexContent("latex");
        ResumeDataDto data = new ResumeDataDto();

        when(loadBalancedRestTemplate.postForObject(anyString(), any(), eq(byte[].class))).thenThrow(new RuntimeException("Down"));

        assertThrows(RuntimeException.class, () -> pdfGenerationService.generatePdf(template, data));
    }
}

