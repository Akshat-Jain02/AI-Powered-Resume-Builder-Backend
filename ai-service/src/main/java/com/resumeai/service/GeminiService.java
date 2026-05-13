package com.resumeai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.resumeai.dto.ATSScoreDTO;
import com.resumeai.dto.ResumeAnalysis;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    // Using gemini-1.5-flash — fast, capable, and widely available
    private static final String MODEL = "gemini-3-flash-preview";

    private Client client;

    @PostConstruct
    public void init() {
        client = Client.builder().apiKey(apiKey).build();
    }

    public ResumeAnalysis analyzeResume(String extractedText) {
        String prompt = """
                You are a resume analysis API. Return ONLY valid JSON with no markdown, no explanation, no extra text.
                The JSON must start with { and end with }.

                Return exactly this structure:
                {
                  "summary": "",
                  "strengths": [],
                  "improvements": [],
                  "alternatives": [],
                  "keywords": [],
                  "overallScore": "",
                  "targetRole": ""
                }

                Resume Text:
                """ + extractedText;

        try {
            GenerateContentResponse response = client.models.generateContent(MODEL, prompt, null);
            String text = cleanJson(response.text());
            log.debug("Gemini analyzeResume response: {}", text.substring(0, Math.min(200, text.length())));
            return objectMapper.readValue(text, ResumeAnalysis.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini response for analyzeResume: {}", e.getMessage());
            throw new com.resumeai.exception.AiServiceException("Failed to process AI analysis response", e);
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new com.resumeai.exception.AiServiceException("Gemini AI service communication failed", e);
        }
    }

    public ATSScoreDTO getATSScore(String extractedText) {
        String prompt = """
                You are an ATS scoring API. Return ONLY valid JSON with no markdown, no explanation, no extra text.
                The JSON must start with { and end with }.

                Return exactly this structure:
                {
                  "atsScore": "",
                  "feedback": [],
                  "suggestions": []
                }

                Rules:
                - "atsScore": a numeric string from 0 to 100
                - "feedback": 2-4 sentences explaining the score
                - "suggestions": list of 3-6 actionable improvements

                Resume Text:
                """ + extractedText;

        try {
            GenerateContentResponse response = client.models.generateContent(MODEL, prompt, null);
            String text = cleanJson(response.text());
            log.debug("Gemini getATSScore response: {}", text.substring(0, Math.min(200, text.length())));
            return objectMapper.readValue(text, ATSScoreDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini response for getATSScore: {}", e.getMessage());
            throw new com.resumeai.exception.AiServiceException("Failed to process ATS score response", e);
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new com.resumeai.exception.AiServiceException("Gemini AI service scoring failed", e);
        }
    }


    /** Strip markdown code fences and leading/trailing whitespace from Gemini output */
    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        if (cleaned.startsWith("```"))     cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```"))       cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
}
