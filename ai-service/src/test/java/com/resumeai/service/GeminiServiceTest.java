package com.resumeai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.resumeai.dto.ATSScoreDTO;
import com.resumeai.dto.ResumeAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private Client client;
    @Mock private com.google.genai.Models models;

    @InjectMocks
    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        // Models is a public final field in the SDK Client
        ReflectionTestUtils.setField(client, "models", models);
        ReflectionTestUtils.setField(geminiService, "client", client);
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-key");
    }

    @Test
    void analyzeResume_success() throws Exception {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn("{\"summary\":\"Good\"}");

        when(models.generateContent(anyString(), anyString(), any())).thenReturn(response);
        
        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setSummary("Good");
        when(objectMapper.readValue(anyString(), eq(ResumeAnalysis.class))).thenReturn(analysis);

        ResumeAnalysis result = geminiService.analyzeResume("extracted text");

        assertNotNull(result);
        assertEquals("Good", result.getSummary());
    }

    @Test
    void analyzeResume_longResponse_coversSubstringBranch() throws Exception {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        String longText = "{\"summary\":\"" + "a".repeat(250) + "\"}";
        when(response.text()).thenReturn(longText);

        when(models.generateContent(anyString(), anyString(), any())).thenReturn(response);
        when(objectMapper.readValue(anyString(), eq(ResumeAnalysis.class))).thenReturn(new ResumeAnalysis());

        geminiService.analyzeResume("text");
        // Verify no exception occurred during logging substring
    }

    @Test
    void analyzeResume_jsonProcessingException_returnsNull() throws Exception {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn("invalid-json");

        when(models.generateContent(anyString(), anyString(), any())).thenReturn(response);
        when(objectMapper.readValue(anyString(), eq(ResumeAnalysis.class))).thenThrow(new JsonProcessingException("error") {});

        ResumeAnalysis result = geminiService.analyzeResume("text");

        assertNull(result);
    }

    @Test
    void analyzeResume_genericException_returnsNull() {
        when(client.models.generateContent(anyString(), anyString(), any())).thenThrow(new RuntimeException("API error"));

        ResumeAnalysis result = geminiService.analyzeResume("text");

        assertNull(result);
    }

    @Test
    void getATSScore_success() throws Exception {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn("{\"atsScore\":\"85\"}");

        when(models.generateContent(anyString(), anyString(), any())).thenReturn(response);

        ATSScoreDTO dto = new ATSScoreDTO();
        dto.setAtsScore("85");
        when(objectMapper.readValue(anyString(), eq(ATSScoreDTO.class))).thenReturn(dto);

        ATSScoreDTO result = geminiService.getATSScore("text");

        assertNotNull(result);
        assertEquals("85", result.getAtsScore());
    }

    @Test
    void getATSScore_exception_returnsNull() {
        when(client.models.generateContent(anyString(), anyString(), any())).thenThrow(new RuntimeException("error"));

        ATSScoreDTO result = geminiService.getATSScore("text");

        assertNull(result);
    }

    @Test
    void cleanJson_withMarkdownFences_stripsThem() {
        // Accessing private method via reflection to test branches
        String input = "```json\n{\"key\":\"val\"}\n```";
        String result = (String) ReflectionTestUtils.invokeMethod(geminiService, "cleanJson", input);
        assertEquals("{\"key\":\"val\"}", result);

        String input2 = "```\n{\"key\":\"val\"}\n```";
        String result2 = (String) ReflectionTestUtils.invokeMethod(geminiService, "cleanJson", input2);
        assertEquals("{\"key\":\"val\"}", result2);
        
        String input3 = "  {\"key\":\"val\"}  ";
        String result3 = (String) ReflectionTestUtils.invokeMethod(geminiService, "cleanJson", input3);
        assertEquals("{\"key\":\"val\"}", result3);

        String input4 = "{\"key\":\"val\"}```";
        String result4 = (String) ReflectionTestUtils.invokeMethod(geminiService, "cleanJson", input4);
        assertEquals("{\"key\":\"val\"}", result4);
    }

    @Test
    void cleanJson_nullInput_returnsEmptyObject() {
        String result = (String) ReflectionTestUtils.invokeMethod(geminiService, "cleanJson", (Object) null);
        assertEquals("{}", result);
    }
}
