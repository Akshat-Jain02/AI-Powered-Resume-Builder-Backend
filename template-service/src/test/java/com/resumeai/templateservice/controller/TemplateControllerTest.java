package com.resumeai.templateservice.controller;

import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.resumeai.templateservice.config.SecurityConfig;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemplateController.class)
@Import(SecurityConfig.class)
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemplateService templateService;

    @Test
    @WithMockUser
    void getAllActive_ShouldReturnTemplates() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        t.setName("Test");
        when(templateService.getAllActive()).thenReturn(Arrays.asList(t));

        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test"));
    }

    @Test
    @WithMockUser
    void getById_WhenExists_ShouldReturnTemplate() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        t.setId(1L);
        when(templateService.getById(1L)).thenReturn(Optional.of(t));

        mockMvc.perform(get("/api/templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser
    void getById_WhenNotExists_ShouldReturn404() throws Exception {
        when(templateService.getById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/templates/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getByCategory_ShouldReturnTemplates() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        t.setCategory("IT");
        when(templateService.getByCategory("IT")).thenReturn(Arrays.asList(t));

        mockMvc.perform(get("/api/templates/category/IT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("IT"));
    }

    @Test
    @WithMockUser
    void incrementUsage_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/templates/1/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser
    void getTemplateImage_WhenExists_ShouldRedirect() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        t.setPreviewImageUrl("http://cloudinary.com/img.png");
        when(templateService.getById(1L)).thenReturn(Optional.of(t));

        mockMvc.perform(get("/api/templates/1/image"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://cloudinary.com/img.png"));
    }

    @Test
    @WithMockUser
    void getTemplateImage_WhenNotExists_ShouldReturn404() throws Exception {
        when(templateService.getById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/templates/1/image"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getTemplateImage_WhenUrlIsNull_ShouldReturn404() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        t.setPreviewImageUrl(null);
        when(templateService.getById(1L)).thenReturn(Optional.of(t));

        mockMvc.perform(get("/api/templates/1/image"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getTopTemplates_ShouldReturnTemplates() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        t.setName("Top Template");
        when(templateService.getTopByUsage()).thenReturn(Arrays.asList(t));

        mockMvc.perform(get("/api/templates/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Top Template"));
    }
}
