package com.jobmatch.controller;

import com.jobmatch.client.ParserClient;
import com.jobmatch.dto.TextRequest;
import com.jobmatch.model.JobResult;
import com.jobmatch.model.JobSearchResponse;
import com.jobmatch.model.ParsedResume;
import com.jobmatch.service.AdzunaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResumeController.class)
@WithMockUser
class ResumeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ParserClient parserClient;
    @MockBean AdzunaService adzunaService;

    @Test
    void uploadResume_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "resume content".getBytes());

        ParsedResume resume = new ParsedResume();
        resume.setName("Alice");
        resume.setJobTitle("Software Engineer");
        resume.setSearchQuery("Software Engineer");

        JobResult job = new JobResult();
        job.setTitle("Java Developer");
        job.setCompany("TechCorp");

        when(parserClient.extractText(any())).thenReturn("Alice is a Software Engineer skilled in Java Python Spring Boot Microservices");
        when(parserClient.analyzeText(any(TextRequest.class))).thenReturn(resume);
        when(adzunaService.searchJobs(resume)).thenReturn(List.of(job));

        mockMvc.perform(multipart("/api/job/upload").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.totalResults").value(1));
    }

    @Test
    void uploadResume_emptyText_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "x".getBytes());

        when(parserClient.extractText(any())).thenReturn("   ");

        mockMvc.perform(multipart("/api/job/upload").file(file).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid resume content"));
    }

    @Test
    void uploadResume_nullTextFromParser_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "x".getBytes());

        when(parserClient.extractText(any())).thenReturn(null);

        mockMvc.perform(multipart("/api/job/upload").file(file).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadResume_adzunaServiceThrows_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "resume data here".getBytes());

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Developer");
        resume.setSearchQuery("Developer");

        when(parserClient.extractText(any())).thenReturn("This is a detailed resume with lots of professional experience and skills information");
        when(parserClient.analyzeText(any(TextRequest.class))).thenReturn(resume);
        when(adzunaService.searchJobs(any())).thenThrow(new RuntimeException("API down"));

        mockMvc.perform(multipart("/api/job/upload").file(file).with(csrf()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void uploadResume_noJobsFound_returnsEmptyList() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "resume content".getBytes());

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Niche Developer");
        resume.setSearchQuery("Niche Developer");

        when(parserClient.extractText(any())).thenReturn("This is a niche developer resume with enough text to pass the fifty character check");
        when(parserClient.analyzeText(any(TextRequest.class))).thenReturn(resume);
        when(adzunaService.searchJobs(resume)).thenReturn(List.of());

        mockMvc.perform(multipart("/api/job/upload").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(0));
    }
}
