package com.resumeai.app.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.resumeai.app.model.ParsedResume;
import com.resumeai.app.service.ResumeAnalyzer;
import com.resumeai.app.service.ResumeFileParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/parser")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Resume Parser", description = "Endpoints for parsing uploaded PDFs into structured JSON")
public class ParserController {

    private final ResumeFileParser fileParser;
    private final ResumeAnalyzer analyzer;

    @Operation(summary = "Extract Text from PDF", description = "Extracts raw text from an uploaded resume PDF.")
    @ApiResponse(responseCode = "200", description = "Text extracted successfully (may be empty on failure)")
    @PostMapping(value = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String extractText(@RequestPart("file") MultipartFile file) {
        try {
            return fileParser.extractText(file);
        } catch (IOException e) {
            log.error("Failed to extract text: {}", e.getMessage());
            return "";
        }
    }

    @Operation(summary = "Analyze Raw Text", description = "Analyzes raw text to extract structured resume data.")
    @ApiResponse(responseCode = "200", description = "Text analyzed successfully")
    @PostMapping("/analyze-text")
    public ParsedResume analyzeText(@Valid @RequestBody TextRequest text) {
        return analyzer.analyze(text.getText());
    }

    @Operation(summary = "Analyze PDF Resume", description = "Uploads a PDF, extracts text, and returns structured resume data.")
    @ApiResponse(responseCode = "200", description = "PDF analyzed successfully")
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedResume analyzeResume(@RequestPart("file") MultipartFile file) {
        try {
            String text = fileParser.extractText(file);
            return analyzer.analyze(text);
        } catch (IOException e) {
            log.error("Failed to analyze resume: {}", e.getMessage());
            return null;
        }
    }
}
