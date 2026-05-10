package com.latexcompiler.latexcompiler.controller;

import com.latexcompiler.latexcompiler.service.LaTeXCompilerService;
import com.latexcompiler.latexcompiler.service.LaTeXCompilerService.CompilationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for simple controller test
class CompilerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LaTeXCompilerService compilerService;

    @Test
    void testCompile_Success() throws Exception {
        byte[] pdfContent = "fake-pdf".getBytes();
        CompilationResult result = new CompilationResult(pdfContent, "Logs", true);
        
        when(compilerService.compile(anyString(), anyString())).thenReturn(result);

        String jsonRequest = "{\"code\": \"\\\\documentclass{article}\", \"photoBase64\": \"\"}";

        mockMvc.perform(post("/api/compiler/compile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdfContent));
    }

    @Test
    void testCompile_Failure() throws Exception {
        CompilationResult result = new CompilationResult(null, "Error: missing semicolon\nNext line", false);
        
        when(compilerService.compile(anyString(), anyString())).thenReturn(result);

        String jsonRequest = "{\"code\": \"invalid code\", \"photoBase64\": \"\"}";

        mockMvc.perform(post("/api/compiler/compile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Compilation-Logs", "Error: missing semicolon Next line"))
                .andExpect(content().string("Error: missing semicolon\nNext line"));
    }

    @Test
    void testCompile_Exception() throws Exception {
        when(compilerService.compile(anyString(), anyString())).thenThrow(new IOException("Service failure"));

        String jsonRequest = "{\"code\": \"code\", \"photoBase64\": \"\"}";

        mockMvc.perform(post("/api/compiler/compile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Service failure"));
    }
}
