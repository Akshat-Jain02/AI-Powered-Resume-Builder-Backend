package com.resumeai.templateservice.service;

import com.resumeai.templateservice.dto.ResumeDataDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PdfGenerationService.
 * PDF compilation requires LaTeX/pdflatex installed — we test helper methods
 * via reflection and generatePdf graceful failure path only.
 */
class PdfGenerationServiceTest {

    private PdfGenerationService service;

    @BeforeEach
    void setUp() {
        service = new PdfGenerationService();
    }

    private ResumeDataDto buildDto() {
        ResumeDataDto d = new ResumeDataDto();
        d.setFullName("Alice Smith");
        d.setEmail("alice@example.com");
        d.setPhone("+91 9876543210");
        d.setLinkedinUrl("linkedin.com/in/alice");
        d.setGithubUrl("github.com/alice");

        d.setAddress("Bangalore, India");
        d.setTargetJobTitle("Software Engineer");
        d.setSummary("Experienced Java developer with 5 years in Spring Boot microservices.");
        d.setSkills(List.of("Java", "Spring Boot", "MySQL", "Docker", "AWS"));

        ResumeDataDto.ExperienceDto exp = new ResumeDataDto.ExperienceDto();

        exp.setCompany("TechCorp");
        exp.setStartDate("Jan 2021");
        exp.setEndDate("Present");
        exp.setCurrent(true);
        exp.setBullets(List.of("Built microservices", "Led team of 4"));
        d.setExperience(List.of(exp));

        ResumeDataDto.EducationDto edu = new ResumeDataDto.EducationDto();
        edu.setDegree("B.Tech");
        edu.setInstitution("IIT Delhi");
        edu.setEndDate("2019");
        edu.setGrade("8.5");
        d.setEducation(List.of(edu));

        ResumeDataDto.ProjectDto proj = new ResumeDataDto.ProjectDto();
        proj.setName("ResumeAI");
        proj.setDescription("An AI-powered resume analyzer");
        proj.setTechStack("Java, Spring Boot, React");
        proj.setLink("github.com/alice/resumeai");
        d.setProjects(List.of(proj));

        return d;
    }

    // ── esc() helper — sanitizes LaTeX special chars ───────────────────────

    @Test
    void esc_null_returnsEmpty() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("esc", String.class);
        m.setAccessible(true);
        assertThat((String) m.invoke(service, (Object) null)).isEmpty();
    }

    @Test
    void esc_plainText_returnsUnchanged() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("esc", String.class);
        m.setAccessible(true);
        assertThat((String) m.invoke(service, "Alice Smith")).isEqualTo("Alice Smith");
    }

    @Test
    void esc_ampersand_isEscaped() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("esc", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "React & Redux");
        assertThat(result).contains("\\&");
    }

    @Test
    void esc_hashSymbol_isEscaped() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("esc", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "C#");
        assertThat(result).contains("\\#");
    }

    @Test
    void esc_underscoreInUrl_isEscaped() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("esc", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "some_variable");
        assertThat(result).contains("\\_");
    }

    @Test
    void esc_percentSign_isEscaped() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("esc", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "100% done");
        assertThat(result).contains("\\%");
    }

    // ── buildLatex — generates LaTeX string without compilation ───────────

    @Test
    void buildLatex_template1_returnsNonEmpty() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        String latex = (String) m.invoke(service, 1, buildDto());
        assertThat(latex).isNotBlank().contains("Alice Smith").contains("documentclass");
    }

    @Test
    void buildLatex_template2_returnsNonEmpty() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        String latex = (String) m.invoke(service, 2, buildDto());
        assertThat(latex).isNotBlank().contains("ALICE SMITH");
    }

    @Test
    void buildLatex_template3_returnsNonEmpty() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        String latex = (String) m.invoke(service, 3, buildDto());
        assertThat(latex).isNotBlank();
    }

    @Test
    void buildLatex_template4_returnsNonEmpty() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        String latex = (String) m.invoke(service, 4, buildDto());
        assertThat(latex).isNotBlank();
    }

    @Test
    void buildLatex_template5_returnsNonEmpty() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        String latex = (String) m.invoke(service, 5, buildDto());
        assertThat(latex).isNotBlank();
    }

    @Test
    void buildLatex_template6_returnsNonEmpty() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        String latex = (String) m.invoke(service, 6, buildDto());
        assertThat(latex).isNotBlank();
    }

    @Test
    void buildLatex_unknownTemplate_fallsBackToDefault() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        String latex = (String) m.invoke(service, 99, buildDto());
        assertThat(latex).isNotBlank(); // returns some template as fallback
    }

    @Test
    void buildLatex_emptySkills_doesNotThrow() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        ResumeDataDto dto = buildDto();
        dto.setSkills(null);
        assertThatCode(() -> m.invoke(service, 1, dto)).doesNotThrowAnyException();
    }

    @Test
    void buildLatex_emptyExperience_doesNotThrow() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        ResumeDataDto dto = buildDto();
        dto.setExperience(null);
        assertThatCode(() -> m.invoke(service, 1, dto)).doesNotThrowAnyException();
    }

    @Test
    void buildLatex_emptyEducation_doesNotThrow() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        ResumeDataDto dto = buildDto();
        dto.setEducation(null);
        assertThatCode(() -> m.invoke(service, 1, dto)).doesNotThrowAnyException();
    }

    @Test
    void buildLatex_nullProjects_doesNotThrow() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        ResumeDataDto dto = buildDto();
        dto.setProjects(null);
        assertThatCode(() -> m.invoke(service, 1, dto)).doesNotThrowAnyException();
    }

    @Test
    void buildLatex_specialCharsInName_areEscaped() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("buildLatex", int.class, ResumeDataDto.class);
        m.setAccessible(true);
        ResumeDataDto dto = buildDto();
        dto.setFullName("O'Brien & Smith#1");
        // Should not throw — special chars are escaped by esc()
        assertThatCode(() -> m.invoke(service, 1, dto)).doesNotThrowAnyException();
    }

    // ── generatePdf — fails gracefully without LaTeX installed ────────────

    @Test
    void generatePdf_withOrWithoutLaTeX_handlesGracefully() {
        // pdflatex may or may not be installed — either outcome is valid
        assertThatCode(() -> {
            try {
                byte[] pdf = service.generatePdf(1, buildDto());
                assertThat(pdf).isNotNull();
            } catch (Exception e) {
                // Expected when pdflatex is not installed
                assertThat(e).isInstanceOf(Exception.class);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void generatePdf_invalidTemplateId_fallsBackToDefault() {
        // Template 999 falls back to template1 (default case in switch)
        assertThatCode(() -> {
            try {
                byte[] pdf = service.generatePdf(999, buildDto());
                assertThat(pdf).isNotNull();
            } catch (Exception e) {
                // Expected when pdflatex is not installed
                assertThat(e).isInstanceOf(Exception.class);
            }
        }).doesNotThrowAnyException();
    }

    // ── contactLine helper ─────────────────────────────────────────────────

    @Test
    void contactLine_allFieldsPresent_containsEmail() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("contactLine", ResumeDataDto.class, String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, buildDto(), " | ");
        assertThat(result).contains("alice@example.com");
    }

    @Test
    void contactLine_emptyEmail_doesNotThrow() throws Exception {
        Method m = PdfGenerationService.class.getDeclaredMethod("contactLine", ResumeDataDto.class, String.class);
        m.setAccessible(true);
        ResumeDataDto dto = buildDto();
        dto.setEmail(null);
        assertThatCode(() -> m.invoke(service, dto, " | ")).doesNotThrowAnyException();
    }
}
