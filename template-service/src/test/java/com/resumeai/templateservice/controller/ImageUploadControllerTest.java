package com.resumeai.templateservice.controller;
import java.util.Map;

import com.resumeai.templateservice.config.SecurityConfig;
import com.resumeai.templateservice.service.CloudinaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageUploadController.class)
@Import(SecurityConfig.class)
class ImageUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Test
    @WithMockUser
    void uploadImage_WhenValidFile_ShouldReturnUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "dummy-image".getBytes());

        when(cloudinaryService.uploadFile(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn(Map.of("url", "http://cloudinary.com/test.png", "public_id", "test-id"));

        mockMvc.perform(multipart("/api/templates/upload-image")
                .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://cloudinary.com/test.png"));
    }

    @Test
    @WithMockUser
    void uploadImage_WhenEmptyFile_ShouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/templates/upload-image")
                .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
