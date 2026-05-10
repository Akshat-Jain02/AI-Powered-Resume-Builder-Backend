package com.resumeai.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.app.model.ParsedResume;
import com.resumeai.app.service.ResumeAnalyzer;
import com.resumeai.app.service.ResumeFileParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ParserController.class)
@WithMockUser
class ParserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ResumeFileParser fileParser;
    @MockBean ResumeAnalyzer analyzer;

    @Test
    void extractText_returnsText() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "Alice Johnson".getBytes());

        when(fileParser.extractText(any())).thenReturn("Alice Johnson Software Engineer");

        mockMvc.perform(multipart("/parser/extract-text").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Alice Johnson Software Engineer"));
    }

    @Test
    void extractText_ioException_returnsEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.txt", "text/plain", "data".getBytes());

        when(fileParser.extractText(any())).thenThrow(new java.io.IOException("read error"));

        mockMvc.perform(multipart("/parser/extract-text").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void analyzeText_returnsResumeModel() throws Exception {
        ParsedResume resume = new ParsedResume();
        resume.setName("Alice Johnson");
        resume.setJobTitle("Software Engineer");
        resume.setSkills(List.of("Java", "Spring Boot"));

        when(analyzer.analyze("Alice Johnson Software Engineer")).thenReturn(resume);

        TextRequest req = new TextRequest();
        req.setText("Alice Johnson Software Engineer");

        mockMvc.perform(post("/parser/analyze-text").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Johnson"));
    }

    @Test
    void analyzeResume_multipart_returnsResult() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "Alice Java Developer".getBytes());

        ParsedResume resume = new ParsedResume();
        resume.setName("Alice");

        when(fileParser.extractText(any())).thenReturn("Alice Java Developer");
        when(analyzer.analyze("Alice Java Developer")).thenReturn(resume);

        mockMvc.perform(multipart("/parser/analyze").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"));
    }
}
