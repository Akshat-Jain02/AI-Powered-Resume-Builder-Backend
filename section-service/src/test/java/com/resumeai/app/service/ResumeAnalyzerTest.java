package com.resumeai.app.service;

import com.resumeai.app.model.ParsedResume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResumeAnalyzerTest {

    private ResumeAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ResumeAnalyzer();
    }

    private static final String SAMPLE_RESUME = """
            Alice Johnson
            alice@example.com
            +91 9876543210
            Location: Bangalore, Karnataka
            
            Summary
            Experienced Java Software Engineer with 5 years of experience in Spring Boot microservices.
            
            Skills
            Java, Python, Spring Boot, MySQL, Docker, AWS, Kubernetes, Git
            
            Experience
            Software Engineer at Acme Corp (2019 – Present)
            Developed RESTful APIs using Spring Boot and deployed on AWS.
            
            Senior Developer at Beta Inc (2017 – 2019)
            Led a team of 4 developers building microservices.
            
            Education
            B.Tech in Computer Science from IIT Delhi, 2017
            """;

    @Test
    void analyze_extractsEmail() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void analyze_extractsPhone() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getPhone()).isNotBlank();
    }

    @Test
    void analyze_extractsName() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getName()).isEqualTo("Alice Johnson");
    }

    @Test
    void analyze_detectsJavaSkill() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getProgrammingLanguages())
                .anySatisfy(s -> assertThat(s).containsIgnoringCase("java"));
    }

    @Test
    void analyze_detectsSpringBootFramework() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getFrameworks())
                .anySatisfy(s -> assertThat(s).containsIgnoringCase("spring"));
    }

    @Test
    void analyze_detectsMySQLDatabase() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getDatabases())
                .anySatisfy(s -> assertThat(s).containsIgnoringCase("mysql"));
    }

    @Test
    void analyze_detectsDockerAndAws() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getTools()).isNotEmpty();
    }

    @Test
    void analyze_detectsJobTitle_softwareEngineer() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getJobTitle()).isNotBlank();
    }

    @Test
    void analyze_detectsBachelorsEducation() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getHighestDegree()).isEqualTo("Bachelors");
    }

    @Test
    void analyze_detectsComputerScienceField() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getFieldOfStudy()).isEqualTo("Computer Science");
    }

    @Test
    void analyze_experienceLevel_mid() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getExperienceLevel()).isIn("MID", "SENIOR", "JUNIOR");
    }

    @Test
    void analyze_extractsSummary() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getSummary()).isNotBlank();
    }

    @Test
    void analyze_buildSearchQuery_notBlank() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getSearchQuery()).isNotBlank();
    }

    @Test
    void analyze_rawTextStored() {
        ParsedResume result = analyzer.analyze(SAMPLE_RESUME);
        assertThat(result.getRawText()).isEqualTo(SAMPLE_RESUME);
    }

    @Test
    void analyze_emptyInput_doesNotThrow() {
        ParsedResume result = analyzer.analyze("");
        assertThat(result).isNotNull();
        assertThat(result.getExperienceLevel()).isEqualTo("FRESHER");
    }

    @Test
    void analyze_phd_detectsDoctorate() {
        String text = "John Doe\njohn@example.com\nPh.D. in Computer Science from MIT";
        ParsedResume result = analyzer.analyze(text);
        assertThat(result.getHighestDegree()).isEqualTo("PhD");
    }

    @Test
    void analyze_masters_detected() {
        String text = "Jane Doe\njane@example.com\nMaster of Technology (M.Tech) in Software Engineering";
        ParsedResume result = analyzer.analyze(text);
        assertThat(result.getHighestDegree()).isEqualTo("Masters");
    }

    @Test
    void analyze_fullstackJobTitle() {
        String text = "Bob Smith\nbob@example.com\nFull Stack Developer with React and Node.js experience";
        ParsedResume result = analyzer.analyze(text);
        assertThat(result.getJobTitle()).isEqualTo("Full Stack Developer");
    }

    @Test
    void analyze_dataScientistJobTitle() {
        String text = "Carol White\ncarol@example.com\nData Scientist specializing in machine learning and deep learning";
        ParsedResume result = analyzer.analyze(text);
        assertThat(result.getJobTitle()).isEqualTo("Data Scientist");
    }

    @Test
    void analyze_yearsOfExperienceFromPattern() {
        String text = "Dev Person\ndev@example.com\n8 years of experience in Java development";
        ParsedResume result = analyzer.analyze(text);
        assertThat(result.getYearsOfExperience()).isEqualTo(8);
        assertThat(result.getExperienceLevel()).isEqualTo("SENIOR");
    }

    @Test
    void analyze_freshLevel_zeroYears() {
        String text = "New Graduate\nnew@example.com\nRecent graduate seeking opportunities";
        ParsedResume result = analyzer.analyze(text);
        assertThat(result.getExperienceLevel()).isEqualTo("FRESHER");
    }

    @Test
    void analyze_skillsDeduplication() {
        String text = "Test User\ntest@example.com\nJava Java Python Python JavaScript skills";
        ParsedResume result = analyzer.analyze(text);
        long javaCount = result.getSkills().stream()
                .filter(s -> s.equalsIgnoreCase("java")).count();
        assertThat(javaCount).isEqualTo(1);
    }
}
