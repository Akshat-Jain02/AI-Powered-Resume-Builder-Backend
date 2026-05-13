package com.resumeai.controller;

import com.resumeai.client.ParserClient;
import com.resumeai.client.PaymentClient;
import com.resumeai.dto.ATSScoreDTO;
import com.resumeai.dto.ResumeAnalysis;
import com.resumeai.kafka.AiEventPublisher;
import com.resumeai.service.GeminiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
class ResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private GeminiService geminiService;
    @MockitoBean private ParserClient parserClient;
    @MockitoBean private PaymentClient paymentClient;
    @MockitoBean private AiEventPublisher eventPublisher;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "dummy content".getBytes());
    }

    @Test
    void uploadResume_success() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("extracted text");
        
        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setSummary("Great resume");
        when(geminiService.analyzeResume(anyString())).thenReturn(analysis);

        mockMvc.perform(multipart("/api/ai/summary")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Great resume"));
    }

    @Test
    void uploadResume_insufficientCredits() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", false));

        mockMvc.perform(multipart("/api/ai/summary")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("Insufficient credits"));
    }

    @Test
    void uploadResume_parserError() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("");

        mockMvc.perform(multipart("/api/ai/summary")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("extract text")));
    }

    @Test
    void getAtsScore_success() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("extracted text");
        
        ATSScoreDTO score = new ATSScoreDTO();
        score.setAtsScore("85");
        when(geminiService.getATSScore(anyString())).thenReturn(score);

        mockMvc.perform(multipart("/api/ai/ats/score")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atsScore").value("85"));
    }

    @Test
    void getAtsScore_paymentServiceDown() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenThrow(new RuntimeException("Service down"));

        mockMvc.perform(multipart("/api/ai/ats/score")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void uploadResume_geminiError() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("text");
        when(geminiService.analyzeResume(anyString())).thenThrow(new com.resumeai.exception.AiServiceException("Gemini error"));

        mockMvc.perform(multipart("/api/ai/summary")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", containsString("AI analysis failed")));
    }

    @Test
    void getAtsScore_geminiError() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("text");
        when(geminiService.getATSScore(anyString())).thenThrow(new com.resumeai.exception.AiServiceException("Gemini error"));

        mockMvc.perform(multipart("/api/ai/ats/score")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", containsString("ATS scoring failed")));
    }

    @Test
    void uploadResume_generalException() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenThrow(new RuntimeException("Unexpected"));

        mockMvc.perform(multipart("/api/ai/summary")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", containsString("unexpected error occurred while processing the resume")));
    }

    @Test
    void uploadResume_bestEffortFailures_shouldStillSucceed() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("extracted text");
        
        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setSummary("Great resume");
        when(geminiService.analyzeResume(anyString())).thenReturn(analysis);
        
        // Failures in best-effort steps
        when(paymentClient.useCredits(any(), any(), anyInt(), any(), any())).thenThrow(new RuntimeException("Deduction failed"));
        // kafka is void, but we can't easily throw unless we use doThrow
        // but it's already wrapped in try-catch in controller.

        mockMvc.perform(multipart("/api/ai/summary")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Great resume"));
    }

    @Test
    void getAtsScore_parserError() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("");

        mockMvc.perform(multipart("/api/ai/ats/score")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("extract text")));
    }

    @Test
    void getAtsScore_generalException() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenThrow(new RuntimeException("Unexpected"));

        mockMvc.perform(multipart("/api/ai/ats/score")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", containsString("unexpected error occurred while generating the ATS score")));
    }

    @Test
    void getAtsScore_bestEffortFailures_shouldStillSucceed() throws Exception {
        when(paymentClient.checkCredits(anyString(), anyInt())).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("extracted text");
        
        ATSScoreDTO score = new ATSScoreDTO();
        score.setAtsScore("85");
        when(geminiService.getATSScore(anyString())).thenReturn(score);
        
        when(paymentClient.useCredits(any(), any(), anyInt(), any(), any())).thenThrow(new RuntimeException("Deduction failed"));

        mockMvc.perform(multipart("/api/ai/ats/score")
                .file(file)
                .header("X-Username", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atsScore").value("85"));
    }
}
