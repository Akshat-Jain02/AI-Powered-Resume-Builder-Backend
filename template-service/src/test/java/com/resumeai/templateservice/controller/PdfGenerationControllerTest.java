package com.resumeai.templateservice.controller;

import com.resumeai.templateservice.dto.PdfGenerationRequestDto;
import com.resumeai.templateservice.dto.ResumeDataDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.PdfGenerationService;
import com.resumeai.templateservice.service.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.resumeai.templateservice.config.SecurityConfig;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PdfGenerationController.class)
@Import(SecurityConfig.class)
class PdfGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PdfGenerationService pdfGenerationService;

    @MockitoBean
    private TemplateService templateService;

    @Test
    @WithMockUser
    void generatePdf_WhenValid_ShouldReturnPdf() throws Exception {
        PdfGenerationRequestDto request = new PdfGenerationRequestDto();
        request.setTemplateId(1L);
        request.setResumeData(new ResumeDataDto());

        ResumeTemplate t = new ResumeTemplate();
        t.setId(1L);
        t.setTemplateId(101);
        t.setName("Test");

        when(templateService.getById(1L)).thenReturn(Optional.of(t));
        when(pdfGenerationService.generatePdf(any(), any())).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(post("/api/templates/pdf/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        verify(templateService).incrementUsage(1L);
    }

    @Test
    @WithMockUser
    void generatePdf_WhenTemplateNotFound_ShouldReturn404() throws Exception {
        PdfGenerationRequestDto request = new PdfGenerationRequestDto();
        request.setTemplateId(1L);
        request.setResumeData(new ResumeDataDto());

        when(templateService.getById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/templates/pdf/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void generatePdf_WhenServiceFails_ShouldReturn500() throws Exception {
        PdfGenerationRequestDto request = new PdfGenerationRequestDto();
        request.setTemplateId(1L);
        request.setResumeData(new ResumeDataDto());

        ResumeTemplate t = new ResumeTemplate();
        t.setId(1L);

        when(templateService.getById(1L)).thenReturn(Optional.of(t));
        when(pdfGenerationService.generatePdf(any(), any())).thenThrow(new RuntimeException("Latex Error"));

        mockMvc.perform(post("/api/templates/pdf/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser
    void generatePdf_WhenMissingData_ShouldReturn400() throws Exception {
        PdfGenerationRequestDto request = new PdfGenerationRequestDto();
        // missing both fields
        mockMvc.perform(post("/api/templates/pdf/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void generatePdf_WhenTemplateIdNull_ShouldReturn400() throws Exception {
        PdfGenerationRequestDto request = new PdfGenerationRequestDto();
        request.setResumeData(new ResumeDataDto());
        mockMvc.perform(post("/api/templates/pdf/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void generatePdf_WhenResumeDataNull_ShouldReturn400() throws Exception {
        PdfGenerationRequestDto request = new PdfGenerationRequestDto();
        request.setTemplateId(1L);
        mockMvc.perform(post("/api/templates/pdf/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
