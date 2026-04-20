package com.resumeai.templateservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.templateservice.dto.PdfGenerationRequestDto;
import com.resumeai.templateservice.dto.ResumeDataDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.PdfGenerationService;
import com.resumeai.templateservice.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminTemplateController.class, PdfGenerationController.class})
@WithMockUser(roles = "ADMIN")
class AdminTemplateAndPdfControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TemplateService templateService;
    @MockBean PdfGenerationService pdfGenerationService;

    private ResumeTemplate template;

    @BeforeEach
    void setUp() {
        template = new ResumeTemplate();
        template.setId(1L);
        template.setName("Modern");
        template.setCategory("MODERN");
        template.setActive(true);
    }

    // ── AdminTemplateController ───────────────────────────────

    @Test
    void adminGetAll_returnsOk() throws Exception {
        when(templateService.getAll()).thenReturn(List.of(template));

        mockMvc.perform(get("/api/templates/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Modern"));
    }

    @Test
    void adminGetById_found_returnsOk() throws Exception {
        when(templateService.getById(1L)).thenReturn(Optional.of(template));

        mockMvc.perform(get("/api/templates/admin/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void adminGetById_notFound_returns404() throws Exception {
        when(templateService.getById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/templates/admin/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminToggleActive_returnsOk() throws Exception {
        template.setActive(false);
        when(templateService.toggleActive(1L)).thenReturn(template);

        mockMvc.perform(patch("/api/templates/admin/1/toggle").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ── PdfGenerationController ───────────────────────────────

    @Test
    void generatePdf_success() throws Exception {
        ResumeDataDto resumeData = new ResumeDataDto();
        resumeData.setFullName("Alice Smith");
        resumeData.setTargetJobTitle("Engineer");
        resumeData.setEmail("alice@example.com");

        PdfGenerationRequestDto req = new PdfGenerationRequestDto();
        req.setTemplateId(1L);
        req.setResumeData(resumeData);

        when(templateService.getById(1L)).thenReturn(Optional.of(template));
        when(pdfGenerationService.generatePdf(anyInt(), any())).thenReturn("PDF_BYTES".getBytes());

        mockMvc.perform(post("/api/templates/pdf/generate").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("resume.pdf")));
    }

    @Test
    void generatePdf_templateNotFound_returns404() throws Exception {
        ResumeDataDto resumeData = new ResumeDataDto();
        resumeData.setFullName("X");
        resumeData.setTargetJobTitle("Y");
        resumeData.setEmail("x@y.com");
        PdfGenerationRequestDto req = new PdfGenerationRequestDto();
        req.setTemplateId(999L);
        req.setResumeData(resumeData);

        when(templateService.getById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/templates/pdf/generate").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void generatePdf_missingTemplateId_returnsBadRequest() throws Exception {
        PdfGenerationRequestDto req = new PdfGenerationRequestDto();
        req.setTemplateId(null);
        req.setResumeData(null);

        mockMvc.perform(post("/api/templates/pdf/generate").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatePdf_serviceThrows_returns500() throws Exception {
        ResumeDataDto resumeData = new ResumeDataDto();
        resumeData.setFullName("X");
        resumeData.setTargetJobTitle("Y");
        resumeData.setEmail("x@y.com");
        PdfGenerationRequestDto req = new PdfGenerationRequestDto();
        req.setTemplateId(1L);
        req.setResumeData(resumeData);

        when(templateService.getById(1L)).thenReturn(Optional.of(template));
        when(pdfGenerationService.generatePdf(anyInt(), any()))
                .thenThrow(new RuntimeException("LaTeX failed"));

        mockMvc.perform(post("/api/templates/pdf/generate").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError());
    }
}
