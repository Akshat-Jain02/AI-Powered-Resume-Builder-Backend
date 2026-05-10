package com.resumeai.resumeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.resumeservice.client.TemplateServiceClient;
import com.resumeai.resumeservice.dto.GeneratePdfRequest;
import com.resumeai.resumeservice.dto.ResumeDataDto;
import com.resumeai.resumeservice.dto.TemplateDto;
import com.resumeai.resumeservice.entity.SavedResume;
import com.resumeai.resumeservice.repository.SavedResumeRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock private SavedResumeRepository savedResumeRepository;
    @Mock private TemplateServiceClient templateServiceClient;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ResumeService resumeService;

    private GeneratePdfRequest request;
    private TemplateDto templateDto;
    private SavedResume savedResume;

    @BeforeEach
    void setUp() {
        ResumeDataDto resumeData = new ResumeDataDto();
        resumeData.setFullName("Alice Smith");

        request = new GeneratePdfRequest();
        request.setTemplateId(1L);
        request.setResumeData(resumeData);

        templateDto = new TemplateDto();
        templateDto.setId(1L);
        templateDto.setName("Modern Template");

        savedResume = new SavedResume();
        savedResume.setId(100L);
        savedResume.setUsername("alice");
        savedResume.setTemplateId(1L);
        savedResume.setTemplateName("Modern Template");
        savedResume.setFullName("Alice Smith");
        savedResume.setResumeData("{\"fullName\":\"Alice Smith\"}");
    }

    @Test
    void generatePdf_success() {
        byte[] pdfBytes = "PDF_CONTENT".getBytes();
        when(templateServiceClient.generatePdf(any())).thenReturn(pdfBytes);

        byte[] result = resumeService.generatePdf(request);

        assertThat(result).isEqualTo(pdfBytes);
    }

    @Test
    void generatePdf_feignException_throwsRuntimeException() {
        when(templateServiceClient.generatePdf(any())).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> resumeService.generatePdf(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PDF generation failed");
    }

    @Test
    void saveResume_blankUsername_throws() {
        assertThatThrownBy(() -> resumeService.saveResume(request, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username must not be blank");
    }

    @Test
    void saveResume_nullUsername_throws() {
        assertThatThrownBy(() -> resumeService.saveResume(request, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveResume_createsNewResume() throws Exception {
        when(templateServiceClient.getTemplateById(1L)).thenReturn(templateDto);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"fullName\":\"Alice Smith\"}");
        when(savedResumeRepository.save(any(SavedResume.class))).thenReturn(savedResume);

        SavedResume result = resumeService.saveResume(request, "alice");

        assertThat(result).isNotNull();
        verify(savedResumeRepository).save(any(SavedResume.class));
    }

    @Test
    void saveResume_updatesExistingResume() throws Exception {
        request.setSavedResumeId(100L);
        when(templateServiceClient.getTemplateById(1L)).thenReturn(templateDto);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"fullName\":\"Alice Smith\"}");
        when(savedResumeRepository.findByIdAndUsername(100L, "alice")).thenReturn(Optional.of(savedResume));
        when(savedResumeRepository.save(savedResume)).thenReturn(savedResume);

        SavedResume result = resumeService.saveResume(request, "alice");

        assertThat(result.getId()).isEqualTo(100L);
        verify(savedResumeRepository).save(savedResume);
    }

    @Test
    void saveResume_savedResumeIdNotFound_createsNew() throws Exception {
        request.setSavedResumeId(999L);
        when(templateServiceClient.getTemplateById(1L)).thenReturn(templateDto);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(savedResumeRepository.findByIdAndUsername(999L, "alice")).thenReturn(Optional.empty());
        when(savedResumeRepository.save(any(SavedResume.class))).thenReturn(savedResume);

        SavedResume result = resumeService.saveResume(request, "alice");
        assertThat(result).isNotNull();
    }

    @Test
    void saveResume_templateNotFound_throws() throws Exception {
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        when(templateServiceClient.getTemplateById(1L)).thenThrow(notFound);

        assertThatThrownBy(() -> resumeService.saveResume(request, "alice"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void getSavedResumesByUser_returnsResumes() {
        when(savedResumeRepository.findByUsernameOrderByCreatedAtDesc("alice"))
                .thenReturn(List.of(savedResume));

        List<SavedResume> result = resumeService.getSavedResumesByUser("alice");

        assertThat(result).hasSize(1);
    }

    @Test
    void getSavedResumesByUser_blankUsername_returnsEmpty() {
        assertThat(resumeService.getSavedResumesByUser("")).isEmpty();
        assertThat(resumeService.getSavedResumesByUser(null)).isEmpty();
    }

    @Test
    void getSavedResumeByIdAndUser_found() {
        when(savedResumeRepository.findByIdAndUsername(100L, "alice")).thenReturn(Optional.of(savedResume));

        assertThat(resumeService.getSavedResumeByIdAndUser(100L, "alice")).isPresent();
    }

    @Test
    void getSavedResumeByIdAndUser_notFound() {
        when(savedResumeRepository.findByIdAndUsername(999L, "alice")).thenReturn(Optional.empty());

        assertThat(resumeService.getSavedResumeByIdAndUser(999L, "alice")).isEmpty();
    }

    @Test
    void getSavedResumeData_success() throws Exception {
        ResumeDataDto resumeData = new ResumeDataDto();
        when(savedResumeRepository.findByIdAndUsername(100L, "alice")).thenReturn(Optional.of(savedResume));
        when(objectMapper.readValue(anyString(), eq(ResumeDataDto.class))).thenReturn(resumeData);

        ResumeDataDto result = resumeService.getSavedResumeData(100L, "alice");
        assertThat(result).isNotNull();
    }

    @Test
    void getSavedResumeData_notFound_throws() {
        when(savedResumeRepository.findByIdAndUsername(999L, "alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.getSavedResumeData(999L, "alice"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void deleteSavedResume_success() {
        when(savedResumeRepository.findByIdAndUsername(100L, "alice")).thenReturn(Optional.of(savedResume));

        resumeService.deleteSavedResume(100L, "alice");

        verify(savedResumeRepository).delete(savedResume);
    }

    @Test
    void deleteSavedResume_notFound_throws() {
        when(savedResumeRepository.findByIdAndUsername(999L, "alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.deleteSavedResume(999L, "alice"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void regeneratePdfForSaved_success() throws Exception {
        ResumeDataDto resumeData = new ResumeDataDto();
        when(savedResumeRepository.findByIdAndUsername(100L, "alice")).thenReturn(Optional.of(savedResume));
        when(objectMapper.readValue(anyString(), eq(ResumeDataDto.class))).thenReturn(resumeData);
        when(templateServiceClient.generatePdf(any())).thenReturn("PDF".getBytes());

        byte[] pdf = resumeService.regeneratePdfForSaved(100L, "alice");
        assertThat(pdf).isNotNull();
    }

    @Test
    void getResumesByTemplate_delegatesToRepository() {
        when(savedResumeRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        assertThat(resumeService.getResumesByTemplate(1L)).isEmpty();
    }
}
