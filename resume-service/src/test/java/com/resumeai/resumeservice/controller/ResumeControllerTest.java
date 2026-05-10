package com.resumeai.resumeservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.resumeservice.dto.GeneratePdfRequest;
import com.resumeai.resumeservice.dto.ResumeDataDto;
import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.service.ResumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

@WebMvcTest(ResumeController.class)
@WithMockUser(username = "alice")
class ResumeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ResumeService resumeService;

    private GeneratePdfRequest request;

    @BeforeEach
    void setUp() {
        ResumeDataDto resumeData = new ResumeDataDto();
        resumeData.setFullName("Alice Smith");

        request = new GeneratePdfRequest();
        request.setTemplateId(1L);
        request.setResumeData(resumeData);
    }

    @Test
    void generatePdf_success() throws Exception {
        when(resumeService.generatePdf(any())).thenReturn("PDF_BYTES".getBytes());

        mockMvc.perform(post("/api/resume/generate").with(csrf())
                .header("X-Username", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=resume.pdf"));
    }

    @Test
    void generatePdf_serviceThrows_returns500() throws Exception {
        when(resumeService.generatePdf(any())).thenThrow(new RuntimeException("template-service down"));

        mockMvc.perform(post("/api/resume/generate").with(csrf())
                .header("X-Username", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void saveResume_success() throws Exception {
        SavedResume saved = new SavedResume();
        saved.setId(1L);
        when(resumeService.saveResume(any(), eq("alice"))).thenReturn(saved);

        mockMvc.perform(post("/api/resume/save").with(csrf())
                .header("X-Username", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getAllSaved_success() throws Exception {
        SavedResume r = new SavedResume();
        r.setId(1L);
        when(resumeService.getSavedResumesByUser("alice")).thenReturn(List.of(r));

        mockMvc.perform(get("/api/resume/saved").header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getSavedById_found() throws Exception {
        SavedResume r = new SavedResume();
        r.setId(1L);
        when(resumeService.getSavedResumeByIdAndUser(1L, "alice")).thenReturn(Optional.of(r));

        mockMvc.perform(get("/api/resume/saved/1").header("X-Username", "alice"))
                .andExpect(status().isOk());
    }

    @Test
    void getSavedById_notFound_returns404() throws Exception {
        when(resumeService.getSavedResumeByIdAndUser(999L, "alice")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/resume/saved/999").header("X-Username", "alice"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSavedData_success() throws Exception {
        ResumeDataDto dto = new ResumeDataDto();
        when(resumeService.getSavedResumeData(1L, "alice")).thenReturn(dto);

        mockMvc.perform(get("/api/resume/saved/1/data").header("X-Username", "alice"))
                .andExpect(status().isOk());
    }

    @Test
    void getSavedData_throws_returns404() throws Exception {
        when(resumeService.getSavedResumeData(999L, "alice")).thenThrow(new RuntimeException("not found"));

        mockMvc.perform(get("/api/resume/saved/999/data").header("X-Username", "alice"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadSaved_success() throws Exception {
        when(resumeService.regeneratePdfForSaved(1L, "alice")).thenReturn("PDF".getBytes());

        mockMvc.perform(get("/api/resume/saved/1/download").header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=resume_1.pdf"));
    }

    @Test
    void deleteSaved_success() throws Exception {
        doNothing().when(resumeService).deleteSavedResume(1L, "alice");

        mockMvc.perform(delete("/api/resume/saved/1").with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Resume 1 deleted"));
    }

    @Test
    void deleteSaved_notFound_returns404() throws Exception {
        doThrow(new RuntimeException("not found")).when(resumeService).deleteSavedResume(999L, "alice");

        mockMvc.perform(delete("/api/resume/saved/999").with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByTemplate_returnsOk() throws Exception {
        when(resumeService.getResumesByTemplate(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/resume/saved/by-template/1"))
                .andExpect(status().isOk());
    }

    @Test
    void saveAndDownload_success() throws Exception {
        SavedResume saved = new SavedResume();
        saved.setId(10L);
        when(resumeService.saveResume(any(), eq("alice"))).thenReturn(saved);
        when(resumeService.generatePdf(any())).thenReturn("PDF".getBytes());

        mockMvc.perform(post("/api/resume/save-and-download").with(csrf())
                .header("X-Username", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=resume_10.pdf"));
    }

    @Test
    void saveAndDownload_failure() throws Exception {
        when(resumeService.saveResume(any(), eq("alice"))).thenThrow(new RuntimeException("Save failed"));

        mockMvc.perform(post("/api/resume/save-and-download").with(csrf())
                .header("X-Username", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void downloadSaved_failure_returns404() throws Exception {
        when(resumeService.regeneratePdfForSaved(999L, "alice")).thenThrow(new RuntimeException("not found"));

        mockMvc.perform(get("/api/resume/saved/999/download").header("X-Username", "alice"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllSaved_fallbackToSecurityContext() throws Exception {
        // No X-Username header, should use alice from @WithMockUser
        when(resumeService.getSavedResumesByUser("alice")).thenReturn(List.of());

        mockMvc.perform(get("/api/resume/saved"))
                .andExpect(status().isOk());
        
        verify(resumeService).getSavedResumesByUser("alice");
    }
}
