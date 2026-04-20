package com.resumeai.resumeservice.controller;

import com.resumeai.resumeservice.dto.GeneratePdfRequest;
import com.resumeai.resumeservice.service.ResumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeControllerCoverageTest {

    @Mock private ResumeService resumeService;
    @InjectMocks private ResumeController resumeController;

    private GeneratePdfRequest req;

    @BeforeEach
    void setUp() {
        req = new GeneratePdfRequest();
    }

    @Test
    void saveResume_runtimeException_returns400() throws Exception {
        when(resumeService.saveResume(any(), anyString())).thenThrow(new RuntimeException("Bad data"));
        ResponseEntity<?> res = resumeController.saveResume("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void saveResume_exception_returns500() throws Exception {
        when(resumeService.saveResume(any(), anyString())).thenThrow(new Exception("Unknown"));
        ResponseEntity<?> res = resumeController.saveResume("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void saveAndDownload_usernameFallbackFails_returns401() {
        ResponseEntity<?> res = resumeController.saveAndDownload(null, req);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void saveAndDownload_exception_returns500() throws Exception {
        when(resumeService.generatePdf(any())).thenThrow(new RuntimeException("PDF fail"));
        ResponseEntity<?> res = resumeController.saveAndDownload("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void saveResume_securityException_returns401() throws Exception {
        when(resumeService.saveResume(any(), anyString())).thenThrow(new SecurityException("Unauth"));
        ResponseEntity<?> res = resumeController.saveResume("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }
    
    @Test
    void saveAndDownload_securityException_returns401() throws Exception {
        when(resumeService.generatePdf(any())).thenThrow(new SecurityException("Unauth"));
        ResponseEntity<?> res = resumeController.saveAndDownload("alice", req);
        // SecurityException only caught if thrown BY saveResume etc. Oh wait! 
        // SecurityException usually from currentUser(). If we mock resumeService it throws RuntimeException unless
        // it's explicitly SecurityException.
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }
    
    @Test
    void generatePdf_exception_returns500() throws Exception {
        when(resumeService.generatePdf(any())).thenThrow(new RuntimeException("fail"));
        ResponseEntity<?> res = resumeController.generatePdf("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }
}
