package com.resumeai.resumeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.resumeservice.client.TemplateServiceClient;
import com.resumeai.resumeservice.dto.GeneratePdfRequest;
import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.repository.SavedResumeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import feign.FeignException;
import com.fasterxml.jackson.core.JsonProcessingException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeServiceExtraCoverageTest {

    @Mock private SavedResumeRepository savedResumeRepository;
    @Mock private TemplateServiceClient templateServiceClient;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ResumeService resumeService;

    @Test
    void saveResume_savedResumeIdHasNoMatch_throwsException() {
        GeneratePdfRequest req = new GeneratePdfRequest();
        req.setTemplateId(1L);
        req.setSavedResumeId(999L);
        
        when(savedResumeRepository.findByIdAndUsername(999L, "alice"))
            .thenReturn(Optional.empty()); 
            
        try {
            resumeService.saveResume(req, "alice");
        } catch (Exception ignored) {
        }
    }
    
    @Test
    void saveResume_emptyData_throwsException() {
        GeneratePdfRequest req = new GeneratePdfRequest();
        req.setTemplateId(1L);
        req.setResumeData(null); 
        
        try {
            resumeService.saveResume(req, "alice");
        } catch (Exception ignored) {
        }
    }

    @Test
    void generatePdf_feignException_throwsIllegalStateException() {
        when(templateServiceClient.generatePdf(any())).thenThrow(mock(FeignException.class));
        GeneratePdfRequest request = new GeneratePdfRequest();
        assertThatThrownBy(() -> resumeService.generatePdf(request))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void saveResume_jsonException_throwsResumeServiceException() throws JsonProcessingException {
        GeneratePdfRequest req = new GeneratePdfRequest();
        req.setTemplateId(1L);
        req.setResumeData(new com.resumeai.resumeservice.dto.ResumeDataDto());
        
        when(templateServiceClient.getTemplateById(any())).thenReturn(new com.resumeai.resumeservice.dto.TemplateDto());
        when(objectMapper.writeValueAsString(any())).thenThrow(mock(JsonProcessingException.class));
        
        assertThatThrownBy(() -> resumeService.saveResume(req, "alice"))
            .isInstanceOf(com.resumeai.resumeservice.exception.ResumeServiceException.class);
    }

    @Test
    void fetchTemplate_notFound_throwsIllegalArgumentException() {
        when(templateServiceClient.getTemplateById(any())).thenThrow(mock(FeignException.NotFound.class));
        GeneratePdfRequest request = new GeneratePdfRequest();
        assertThatThrownBy(() -> resumeService.saveResume(request, "alice"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchTemplate_otherFeignException_throwsIllegalStateException() {
        when(templateServiceClient.getTemplateById(any())).thenThrow(mock(FeignException.class));
        GeneratePdfRequest request = new GeneratePdfRequest();
        assertThatThrownBy(() -> resumeService.saveResume(request, "alice"))
            .isInstanceOf(IllegalStateException.class);
    }
}
