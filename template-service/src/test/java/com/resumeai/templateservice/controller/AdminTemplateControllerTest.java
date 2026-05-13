package com.resumeai.templateservice.controller;

import com.resumeai.templateservice.dto.TemplateRequestDto;
import com.resumeai.templateservice.entity.ResumeTemplate;
import com.resumeai.templateservice.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.resumeai.templateservice.config.SecurityConfig;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminTemplateController.class)
@Import(SecurityConfig.class)
class AdminTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemplateService templateService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_ShouldReturnAllTemplates() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        when(templateService.getAll()).thenReturn(Arrays.asList(t));

        mockMvc.perform(get("/api/templates/admin"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_WhenExists_ShouldReturnTemplate() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        t.setId(1L);
        when(templateService.getById(1L)).thenReturn(java.util.Optional.of(t));

        mockMvc.perform(get("/api/templates/admin/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_WhenNotExists_ShouldReturn404() throws Exception {
        when(templateService.getById(1L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/templates/admin/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggleActive_ShouldReturnToggledTemplate() throws Exception {
        ResumeTemplate t = new ResumeTemplate();
        t.setActive(true);
        when(templateService.toggleActive(1L)).thenReturn(t);

        mockMvc.perform(patch("/api/templates/admin/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggleActive_WhenNotFound_ShouldReturn404() throws Exception {
        when(templateService.toggleActive(1L)).thenThrow(new com.resumeai.templateservice.exception.TemplateServiceException("Not found"));

        mockMvc.perform(patch("/api/templates/admin/1/toggle"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTemplate_ShouldReturn201() throws Exception {
        MockMultipartFile templateJson = new MockMultipartFile("template", "", "application/json", "{\"name\":\"Test\"}".getBytes());
        MockMultipartFile latexFile = new MockMultipartFile("latex", "test.tex", "text/plain", "content".getBytes());
        MockMultipartFile imageFile = new MockMultipartFile("image", "test.png", "image/png", "image".getBytes());

        ResumeTemplate t = new ResumeTemplate();
        t.setId(1L);
        when(templateService.create(any(TemplateRequestDto.class), any(), any())).thenReturn(t);

        mockMvc.perform(multipart("/api/templates/admin")
                .file(templateJson)
                .file(latexFile)
                .file(imageFile))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteTemplate_ShouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/templates/admin/1"))
                .andExpect(status().isNoContent());
    }
}
