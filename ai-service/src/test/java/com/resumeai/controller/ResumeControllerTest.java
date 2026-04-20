package com.resumeai.controller;

import com.resumeai.client.ParserClient;
import com.resumeai.client.PaymentClient;
import com.resumeai.kafka.AiEventPublisher;
import com.resumeai.dto.ATSScoreDTO;
import com.resumeai.dto.ResumeAnalysis;
import com.resumeai.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResumeController.class)
@WithMockUser
class ResumeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean GeminiService geminiService;
    @MockBean ParserClient parserClient;
    @MockBean PaymentClient paymentClient;
    @MockBean AiEventPublisher eventPublisher;

    @Test
    void uploadResume_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "resume content".getBytes());

        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setSummary("Strong candidate");
        analysis.setOverallScore("85");
        analysis.setStrengths(List.of("Java"));
        analysis.setImprovements(List.of("Add ML"));
        analysis.setKeywords(List.of("Spring"));

        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("Alice Java Spring Boot Developer with experience");
        when(geminiService.analyzeResume(anyString())).thenReturn(analysis);

        mockMvc.perform(multipart("/api/ai/summary").file(file).with(csrf())
                .header("X-Username", "alice")
                .header("X-User-Email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Strong candidate"));

        verify(paymentClient).useCredits(eq("alice"), any(), eq(1), any(), any());
    }

    @Test
    void uploadResume_insufficientCredits_returns402() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", false));

        mockMvc.perform(multipart("/api/ai/summary").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.requiresPayment").value(true));
    }

    @Test
    void uploadResume_paymentServiceUnavailable_returns503() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        when(paymentClient.checkCredits("alice", 1)).thenThrow(new RuntimeException("service down"));

        mockMvc.perform(multipart("/api/ai/summary").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void uploadResume_emptyExtractedText_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("   ");

        mockMvc.perform(multipart("/api/ai/summary").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadResume_geminiReturnsNull_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("Some valid resume text here");
        when(geminiService.analyzeResume(anyString())).thenReturn(null);

        mockMvc.perform(multipart("/api/ai/summary").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void uploadResume_kafkaFails_stillReturnsOk() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setSummary("Good");
        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("Valid resume text content here");
        when(geminiService.analyzeResume(anyString())).thenReturn(analysis);
        doThrow(new RuntimeException("Kafka down")).when(eventPublisher)
                .publishResumeAnalysis(any(), any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(multipart("/api/ai/summary").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isOk());
    }

    @Test
    void getAtsScore_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        ATSScoreDTO atsScore = new ATSScoreDTO();
        atsScore.setAtsScore("78");
        atsScore.setFeedback(List.of("Good keywords"));
        atsScore.setSuggestions(List.of("Add certifications"));

        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("Alice Java Developer with Spring Boot skills");
        when(geminiService.getATSScore(anyString())).thenReturn(atsScore);

        mockMvc.perform(multipart("/api/ai/ats/score").file(file).with(csrf())
                .header("X-Username", "alice")
                .header("X-User-Email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atsScore").value("78"));

        verify(paymentClient).useCredits(eq("alice"), any(), eq(1), any(), any());
    }

    @Test
    void getAtsScore_insufficientCredits_returns402() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", false));

        mockMvc.perform(multipart("/api/ai/ats/score").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void getAtsScore_emptyText_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn(null);

        mockMvc.perform(multipart("/api/ai/ats/score").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAtsScore_geminiReturnsNull_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        when(paymentClient.checkCredits("alice", 1)).thenReturn(Map.of("hasCredits", true));
        when(parserClient.extractText(any())).thenReturn("Valid resume text for ATS scoring");
        when(geminiService.getATSScore(anyString())).thenReturn(null);

        mockMvc.perform(multipart("/api/ai/ats/score").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getAtsScore_paymentServiceDown_returns503() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());
        when(paymentClient.checkCredits("alice", 1)).thenThrow(new RuntimeException("service down"));

        mockMvc.perform(multipart("/api/ai/ats/score").file(file).with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isServiceUnavailable());
    }
}
