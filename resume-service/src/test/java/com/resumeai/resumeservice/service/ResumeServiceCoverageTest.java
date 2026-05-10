package com.resumeai.resumeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.resumeservice.client.TemplateServiceClient;
import com.resumeai.resumeservice.dto.GeneratePdfRequest;
import com.resumeai.resumeservice.dto.ResumeDataDto;
import com.resumeai.resumeservice.dto.TemplateDto;
import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.repository.SavedResumeRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceCoverageTest {

    @Mock private SavedResumeRepository savedResumeRepository;
    @Mock private TemplateServiceClient templateServiceClient;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ResumeService resumeService;

    @Test
    void saveResume_usernameBlank_throwsIllegalArgumentException() {
        GeneratePdfRequest req = new GeneratePdfRequest();
        assertThatThrownBy(() -> resumeService.saveResume(req, ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveResume_updateExisting_updatesAndReturns() throws Exception {
        GeneratePdfRequest req = new GeneratePdfRequest();
        req.setTemplateId(1L);
        req.setSavedResumeId(10L);
        ResumeDataDto data = new ResumeDataDto();
        data.setFullName("Updated");
        req.setResumeData(data);

        TemplateDto template = new TemplateDto();
        template.setName("Modern");
        when(templateServiceClient.getTemplateById(1L)).thenReturn(template);

        SavedResume existing = new SavedResume();
        existing.setId(10L);
        existing.setUsername("alice");
        when(savedResumeRepository.findByIdAndUsername(10L, "alice")).thenReturn(Optional.of(existing));
        when(objectMapper.writeValueAsString(data)).thenReturn("{}");
        when(savedResumeRepository.save(existing)).thenReturn(existing);

        SavedResume saved = resumeService.saveResume(req, "alice");

        assertThat(saved.getFullName()).isEqualTo("Updated");
        verify(savedResumeRepository).save(existing);
    }

    @Test
    void fetchTemplate_notFound_throwsException() {
        GeneratePdfRequest req = new GeneratePdfRequest();
        req.setTemplateId(99L);
        
        Request mockReq = Request.create(Request.HttpMethod.GET, "/api/templates/99", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        FeignException.NotFound notFoundEx = new FeignException.NotFound("Not Found", mockReq, null, null);
        
        when(templateServiceClient.getTemplateById(99L)).thenThrow(notFoundEx);

        assertThatThrownBy(() -> resumeService.saveResume(req, "alice"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Template not found");
    }

    @Test
    void fetchTemplate_feignException_throwsUnavailable() {
        GeneratePdfRequest req = new GeneratePdfRequest();
        req.setTemplateId(99L);
        
        Request mockReq = Request.create(Request.HttpMethod.GET, "/api/templates/99", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        FeignException feignEx = new FeignException.InternalServerError("Server Error", mockReq, null, null);
        
        when(templateServiceClient.getTemplateById(99L)).thenThrow(feignEx);

        assertThatThrownBy(() -> resumeService.saveResume(req, "alice"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Template Service is unavailable");
    }
    
    @Test
    void getSavedResumesByUser_nullOrBlank_returnsEmpty() {
        assertThat(resumeService.getSavedResumesByUser("")).isEmpty();
        assertThat(resumeService.getSavedResumesByUser(null)).isEmpty();
    }
}
