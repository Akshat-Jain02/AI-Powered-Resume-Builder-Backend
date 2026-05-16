package com.resumeai.resumeservice.controller;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import static org.mockito.Mockito.mock;

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
import static org.mockito.ArgumentMatchers.anyLong;
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
    void saveResume_illegalArgumentException_returns400() {
        when(resumeService.saveResume(any(), anyString())).thenThrow(new IllegalArgumentException("Bad data"));
        ResponseEntity<Object> res = resumeController.saveResume("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void saveResume_exception_returns500() {
        when(resumeService.saveResume(any(), anyString())).thenThrow(new RuntimeException("Unknown"));
        ResponseEntity<Object> res = resumeController.saveResume("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void saveAndDownload_usernameFallbackFails_returns401() {
        ResponseEntity<Object> res = resumeController.saveAndDownload(null, req);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void saveAndDownload_exception_returns500() {
        when(resumeService.generatePdf(any())).thenThrow(new RuntimeException("PDF fail"));
        ResponseEntity<Object> res = resumeController.saveAndDownload("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void saveResume_securityException_returns401() {
        when(resumeService.saveResume(any(), anyString())).thenThrow(new SecurityException("Unauth"));
        ResponseEntity<Object> res = resumeController.saveResume("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }
    
    @Test
    void saveAndDownload_securityException_returns401() {
        when(resumeService.saveResume(any(), anyString())).thenThrow(new SecurityException("Unauth"));
        ResponseEntity<Object> res = resumeController.saveAndDownload("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }
    
    @Test
    void generatePdf_exception_returns500() {
        when(resumeService.generatePdf(any())).thenThrow(new RuntimeException("fail"));
        ResponseEntity<Object> res = resumeController.generatePdf("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void currentUser_userDetailsPrincipal_returnsUsername() {
        Authentication auth = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("bob");
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        ResponseEntity<Object> res = resumeController.saveResume(null, req);
        // If it reaches resumeService.saveResume, it means currentUser worked
        assertThat(res.getStatusCode().value()).isNotEqualTo(401);
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUser_anonymousUser_returnsFallback() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        ResponseEntity<Object> res = resumeController.saveResume("fallback", req);
        assertThat(res.getStatusCode().value()).isNotEqualTo(401);
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUser_blankName_returnsFallback() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("  ");
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        ResponseEntity<Object> res = resumeController.saveResume("fallback", req);
        assertThat(res.getStatusCode().value()).isNotEqualTo(401);
        SecurityContextHolder.clearContext();
    }
    
    @Test
    void currentUser_noAuthNoFallback_returns401() {
        SecurityContextHolder.clearContext();
        ResponseEntity<Object> res = resumeController.saveResume("", req);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void generatePdf_resumeServiceException_returns500() {
        when(resumeService.generatePdf(any())).thenThrow(new com.resumeai.resumeservice.exception.ResumeServiceException("fail"));
        ResponseEntity<Object> res = resumeController.generatePdf("alice", req);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void getAllSaved_securityException_returns401() {
        // Mocking SecurityException from currentUser call inside getAllSaved
        // We do this by having currentUser throw it (triggered by no auth)
        SecurityContextHolder.clearContext();
        ResponseEntity<Object> res = resumeController.getAllSaved("");
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void getSavedById_securityException_returns401() {
        SecurityContextHolder.clearContext();
        ResponseEntity<Object> res = resumeController.getSavedById("", 1L);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void getSavedData_securityException_returns401() {
        SecurityContextHolder.clearContext();
        ResponseEntity<Object> res = resumeController.getSavedData("", 1L);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void getSavedData_exception_returns404() {
        when(resumeService.getSavedResumeData(anyLong(), anyString())).thenThrow(new RuntimeException("fail"));
        ResponseEntity<Object> res = resumeController.getSavedData("alice", 1L);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void downloadSaved_securityException_returns401() {
        SecurityContextHolder.clearContext();
        ResponseEntity<Object> res = resumeController.downloadSaved("", 1L);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void downloadSaved_illegalArgumentException_returns404() {
        when(resumeService.regeneratePdfForSaved(anyLong(), anyString())).thenThrow(new IllegalArgumentException("not found"));
        ResponseEntity<Object> res = resumeController.downloadSaved("alice", 1L);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deleteSaved_securityException_returns401() {
        SecurityContextHolder.clearContext();
        ResponseEntity<Object> res = resumeController.deleteSaved("", 1L);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void deleteSaved_illegalArgumentException_returns404() {
        doThrow(new IllegalArgumentException("fail")).when(resumeService).deleteSavedResume(anyLong(), anyString());
        ResponseEntity<Object> res = resumeController.deleteSaved("alice", 1L);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deleteSaved_exception_returns500() {
        doThrow(new RuntimeException("fail")).when(resumeService).deleteSavedResume(anyLong(), anyString());
        ResponseEntity<Object> res = resumeController.deleteSaved("alice", 1L);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }
}
