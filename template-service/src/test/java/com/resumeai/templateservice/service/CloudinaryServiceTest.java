package com.resumeai.templateservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(cloudinary.uploader()).thenReturn(uploader);
        cloudinaryService = new CloudinaryService(cloudinary);
    }

    @Test
    void uploadFile_Success_ShouldReturnUrl() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "data".getBytes());
        Map<String, Object> result = Map.of("secure_url", "http://res.cloudinary.com/test.png");
        
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(result);

        String url = cloudinaryService.uploadFile(file);

        assertEquals("http://res.cloudinary.com/test.png", url);
    }

    @Test
    void uploadFile_Failure_ShouldThrowException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "data".getBytes());
        
        when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(new IOException("Network error"));

        assertThrows(com.resumeai.templateservice.exception.TemplateServiceException.class, () -> cloudinaryService.uploadFile(file));
    }
}
