package com.resumeai.resumeservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void handleValidationExceptions() throws Exception {
        // Send a request with missing required fields in GeneratePdfRequest
        mockMvc.perform(post("/api/resume/generate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")) 
                .andExpect(status().isBadRequest());
    }
}
