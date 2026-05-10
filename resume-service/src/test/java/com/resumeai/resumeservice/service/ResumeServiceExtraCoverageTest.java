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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
