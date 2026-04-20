package com.resumeai.templateservice.controller;

import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemplateController.class)
@WithMockUser
class TemplateControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TemplateService templateService;

    private ResumeTemplate template;

    @BeforeEach
    void setUp() {
        template = new ResumeTemplate();
        template.setId(1L);
        template.setName("Modern Slate");
        template.setCategory("MODERN");
        template.setActive(true);
    }

    @Test
    void getAllActive_returnsOk() throws Exception {
        when(templateService.getAllActive()).thenReturn(List.of(template));

        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Modern Slate"));
    }

    @Test
    void getById_found_returnsOk() throws Exception {
        when(templateService.getById(1L)).thenReturn(Optional.of(template));

        mockMvc.perform(get("/api/templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(templateService.getById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/templates/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByCategory_returnsOk() throws Exception {
        when(templateService.getByCategory("MODERN")).thenReturn(List.of(template));

        mockMvc.perform(get("/api/templates/category/MODERN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("MODERN"));
    }

    @Test
    void getTopTemplates_returnsOk() throws Exception {
        when(templateService.getTopByUsage()).thenReturn(List.of(template));

        mockMvc.perform(get("/api/templates/top"))
                .andExpect(status().isOk());
    }

    @Test
    void incrementUsage_returnsOk() throws Exception {
        doNothing().when(templateService).incrementUsage(1L);

        mockMvc.perform(post("/api/templates/1/usage").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}
