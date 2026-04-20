package com.resumeai.resumeservice.controller;

import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.repository.SavedResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminResumeController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "ADMIN")
class AdminResumeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SavedResumeRepository savedResumeRepository;

    private SavedResume resume;

    @BeforeEach
    void setUp() {
        resume = new SavedResume();
        resume.setId(1L);
        resume.setUsername("alice");
        resume.setTemplateName("Modern");
        resume.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getAllResumes_returnsOk() throws Exception {
        when(savedResumeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(resume));
        mockMvc.perform(get("/api/resume/admin/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void getResumesByUser_returnsOk() throws Exception {
        when(savedResumeRepository.findByUsernameOrderByCreatedAtDesc("alice"))
                .thenReturn(List.of(resume));
        mockMvc.perform(get("/api/resume/admin/user/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getResumeById_found() throws Exception {
        when(savedResumeRepository.findById(1L)).thenReturn(Optional.of(resume));
        mockMvc.perform(get("/api/resume/admin/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void getResumeById_notFound() throws Exception {
        when(savedResumeRepository.findById(999L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/resume/admin/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteResume_success() throws Exception {
        when(savedResumeRepository.findById(1L)).thenReturn(Optional.of(resume));
        mockMvc.perform(delete("/api/resume/admin/1").with(csrf())
                .header("X-Username", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        verify(savedResumeRepository).delete(any());
    }

    @Test
    void deleteResume_notFound() throws Exception {
        when(savedResumeRepository.findById(999L)).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/resume/admin/999").with(csrf())
                .header("X-Username", "admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getResumeStats_returnsStats() throws Exception {
        SavedResume r2 = new SavedResume();
        r2.setId(2L);
        r2.setUsername("bob");
        r2.setTemplateName("Classic");

        when(savedResumeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(resume, r2));

        mockMvc.perform(get("/api/resume/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResumes").value(2))
                .andExpect(jsonPath("$.uniqueUsers").value(2));
    }
}
