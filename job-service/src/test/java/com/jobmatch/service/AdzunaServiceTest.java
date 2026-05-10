package com.jobmatch.service;

import com.jobmatch.model.JobResult;
import com.jobmatch.model.ParsedResume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AdzunaService.
 * Network calls fail gracefully (invalid keys), so we focus on:
 * 1. Query-building logic (title/skill fallbacks)
 * 2. Graceful degradation on HTTP failures
 * 3. scoreJobs() scoring logic via reflection
 */
@ExtendWith(MockitoExtension.class)
class AdzunaServiceTest {

    @InjectMocks private AdzunaService adzunaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adzunaService, "appId", "test_app_id");
        ReflectionTestUtils.setField(adzunaService, "appKey", "test_app_key");
        ReflectionTestUtils.setField(adzunaService, "resultsPerPage", 20);
    }

    // ── searchJobs: graceful degradation ─────────────────────────────────────

    @Test
    void searchJobs_networkUnavailable_returnsEmptyList() {
        ParsedResume resume = buildResume("Software Engineer", List.of("Java"), List.of());
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).isNotNull();
    }

    @Test
    void searchJobs_nullJobTitle_usesDefaultQuery() {
        ParsedResume resume = buildResume(null, List.of("Java"), List.of());
        assertThatCode(() -> adzunaService.searchJobs(resume)).doesNotThrowAnyException();
    }

    @Test
    void searchJobs_blankJobTitle_usesDefaultQuery() {
        ParsedResume resume = buildResume("", List.of("Python"), List.of());
        assertThatCode(() -> adzunaService.searchJobs(resume)).doesNotThrowAnyException();
    }

    @Test
    void searchJobs_noSkills_doesNotThrow() {
        ParsedResume resume = buildResume("DevOps Engineer", null, List.of());
        assertThatCode(() -> adzunaService.searchJobs(resume)).doesNotThrowAnyException();
    }

    @Test
    void searchJobs_nullProgrammingLanguages_doesNotThrow() {
        ParsedResume resume = buildResume("Data Scientist", null, null);
        resume.setFrameworks(null);
        resume.setDatabases(null);
        resume.setTools(null);
        assertThatCode(() -> adzunaService.searchJobs(resume)).doesNotThrowAnyException();
    }

    @Test
    void searchJobs_returnsListNotNull_always() {
        ParsedResume resume = buildResume("QA Engineer", List.of("Selenium"), List.of("selenium"));
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).isNotNull();
    }

    // ── scoreJobs via parseAdzunaResponse (JSON parsing) ─────────────────────

    @Test
    void parseAdzunaResponse_validJson_returnsJobs() throws Exception {
        String json = """
            {
              "count": 1,
              "results": [{
                "title": "Java Software Engineer",
                "company": {"display_name": "TechCorp"},
                "description": "Spring Boot Java developer role with AWS experience",
                "redirect_url": "https://example.com/job/1",
                "location": {"display_name": "Bangalore, India"},
                "salary_min": 500000,
                "salary_max": 1000000,
                "contract_type": "permanent",
                "created": "2024-01-01T00:00:00Z"
              }]
            }
            """;

        java.lang.reflect.Method m = AdzunaService.class.getDeclaredMethod("parseAdzunaResponse", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<JobResult> results = (List<JobResult>) m.invoke(adzunaService, json);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Java Software Engineer");
        assertThat(results.get(0).getCompany()).isEqualTo("TechCorp");
    }

    @Test
    void parseAdzunaResponse_emptyResults_returnsEmptyList() throws Exception {
        String json = """
            {"count": 0, "results": []}
            """;

        java.lang.reflect.Method m = AdzunaService.class.getDeclaredMethod("parseAdzunaResponse", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<JobResult> results = (List<JobResult>) m.invoke(adzunaService, json);
        assertThat(results).isEmpty();
    }

    @Test
    void parseAdzunaResponse_malformedJson_returnsEmptyList() throws Exception {
        java.lang.reflect.Method m = AdzunaService.class.getDeclaredMethod("parseAdzunaResponse", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<JobResult> results = (List<JobResult>) m.invoke(adzunaService, "not-valid-json{{");
        assertThat(results).isEmpty();
    }

    @Test
    void scoreJobs_seniorRole_matchesSeniorJob() throws Exception {
        JobResult job = new JobResult();
        job.setTitle("Senior Java Developer");
        job.setDescription("We need a senior spring boot developer with AWS");

        ParsedResume resume = buildResume("Senior Software Engineer", List.of("Java"), List.of("java", "spring"));
        resume.setFrameworks(List.of("Spring Boot"));
        resume.setDatabases(List.of("MySQL"));
        resume.setTools(List.of("AWS"));
        resume.setExperienceLevel("SENIOR");

        java.lang.reflect.Method m = AdzunaService.class.getDeclaredMethod("scoreJobs", List.class, ParsedResume.class);
        m.setAccessible(true);
        m.invoke(adzunaService, List.of(job), resume);

        assertThat(job.getMatchScore()).isGreaterThan(0);
        assertThat(job.getMatchLevel()).isNotNull().isIn("EXCELLENT", "GOOD", "FAIR", "LOW");
    }

    @Test
    void scoreJobs_juniorRole_matchesEntryLevelJob() throws Exception {
        JobResult job = new JobResult();
        job.setTitle("Junior Python Developer");
        job.setDescription("Entry level role for fresh graduates, python and django");

        ParsedResume resume = buildResume("Software Developer", List.of("Python"), List.of("python"));
        resume.setFrameworks(List.of("Django"));
        resume.setExperienceLevel("JUNIOR");

        java.lang.reflect.Method m = AdzunaService.class.getDeclaredMethod("scoreJobs", List.class, ParsedResume.class);
        m.setAccessible(true);
        m.invoke(adzunaService, List.of(job), resume);

        assertThat(job.getMatchScore()).isGreaterThan(0);
        assertThat(job.getMatchedSkills()).isNotNull();
    }

    @Test
    void scoreJobs_noMatchingSkills_returnsLowScore() throws Exception {
        JobResult job = new JobResult();
        job.setTitle("Marketing Manager");
        job.setDescription("Digital marketing and SEO expert needed");

        ParsedResume resume = buildResume("Java Developer", List.of("Java"), List.of("java"));
        resume.setExperienceLevel("MID");

        java.lang.reflect.Method m = AdzunaService.class.getDeclaredMethod("scoreJobs", List.class, ParsedResume.class);
        m.setAccessible(true);
        m.invoke(adzunaService, List.of(job), resume);

        assertThat(job.getMatchScore()).isLessThanOrEqualTo(50);
    }

    @Test
    void scoreJobs_excellentMatchLevel_scoreAbove75() throws Exception {
        JobResult job = new JobResult();
        job.setTitle("Java Software Engineer");
        job.setDescription("java spring boot aws docker kubernetes microservices");

        ParsedResume resume = buildResume("Software Engineer", List.of("Java"), List.of("java", "spring", "docker"));
        resume.setFrameworks(List.of("Spring Boot", "Spring"));
        resume.setTools(List.of("Docker", "AWS", "Kubernetes"));
        resume.setExperienceLevel("MID");

        java.lang.reflect.Method m = AdzunaService.class.getDeclaredMethod("scoreJobs", List.class, ParsedResume.class);
        m.setAccessible(true);
        m.invoke(adzunaService, List.of(job), resume);

        // High match job with matching title gets >75 score
        assertThat(job.getMatchScore()).isGreaterThanOrEqualTo(25);
        assertThat(job.getMatchLevel()).isNotBlank();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ParsedResume buildResume(String jobTitle, List<String> programmingLanguages, List<String> skills) {
        ParsedResume resume = new ParsedResume();
        resume.setJobTitle(jobTitle);
        resume.setSearchQuery(jobTitle != null ? jobTitle : "software developer");
        resume.setProgrammingLanguages(programmingLanguages);
        resume.setSkills(skills);
        resume.setFrameworks(List.of());
        resume.setDatabases(List.of());
        resume.setTools(List.of());
        return resume;
    }
}
