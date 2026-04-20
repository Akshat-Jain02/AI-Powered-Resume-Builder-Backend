package com.jobmatch.service;

import com.jobmatch.model.JobResult;
import com.jobmatch.model.ParsedResume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdzunaServiceCoverageTest {

    @Mock private RestClient restClient;
    @Mock private RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private RequestHeadersSpec requestHeadersSpec;
    @Mock private ResponseSpec responseSpec;

    @InjectMocks private AdzunaService adzunaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adzunaService, "appId", "test_id");
        ReflectionTestUtils.setField(adzunaService, "appKey", "test_key");
        ReflectionTestUtils.setField(adzunaService, "resultsPerPage", 10);
        ReflectionTestUtils.setField(adzunaService, "country", "us");
        ReflectionTestUtils.setField(adzunaService, "restClient", restClient);
    }

    @Test
    void searchJobs_apiReturnsErrorNode_returnsEmptyList() throws Exception {
        String json = "{\"error\": \"Invalid API key\"}";
        setupRestClientToReturn(json);

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Java Developer");
        resume.setSearchQuery("Java Developer");
        
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).isEmpty();
    }

    @Test
    void searchJobs_apiReturnsExceptionNode_returnsEmptyList() throws Exception {
        String json = "{\"exception\": \"Connection timeout\"}";
        setupRestClientToReturn(json);

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Java Developer");
        resume.setSearchQuery("Java Developer");
        
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).isEmpty();
    }

    @Test
    void searchJobs_apiReturnsNullResults_returnsEmptyList() throws Exception {
        String json = "{\"count\": 10}";
        setupRestClientToReturn(json);

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Java Developer");
        resume.setSearchQuery("Java Developer");
        
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).isEmpty();
    }

    @Test
    void searchJobs_apiReturnsNonArrayResults_returnsEmptyList() throws Exception {
        String json = "{\"results\": \"not-an-array\"}";
        setupRestClientToReturn(json);

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Java Developer");
        resume.setSearchQuery("Java Developer");
        
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).isEmpty();
    }

    @Test
    void searchJobs_apiEmptyResponse_returnsEmptyList() throws Exception {
        setupRestClientToReturn("");

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Java Developer");
        resume.setSearchQuery("Java Developer");
        
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).isEmpty();
    }
    
    @Test
    void searchJobs_apiThrowsException_returnsEmptyList() {
        when(restClient.get()).thenThrow(new RuntimeException("Network error"));

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Java Developer");
        resume.setSearchQuery("Java Developer");
        
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).isEmpty();
    }

    @Test
    void searchJobs_fullValidResponse_coversAllPropertyBranches() throws Exception {
        String json = """
            {
              "count": 1,
              "results": [
                {
                  "id": "1",
                  "title": "Java Dev",
                  "description": "Good job",
                  "company": { "display_name": "Tech Corp" },
                  "location": { "display_name": "New York" },
                  "category": { "label": "Engineering" },
                  "contract_type": "permanent",
                  "contract_time": "full_time",
                  "salary_min": 100000,
                  "salary_max": 200000,
                  "redirect_url": "http://example.com"
                },
                {
                  "id": "2",
                  "title": "Missing Props",
                  "company": {},
                  "location": {},
                  "category": {}
                }
              ]
            }
            """;
        setupRestClientToReturn(json);

        ParsedResume resume = new ParsedResume();
        resume.setJobTitle("Java Developer");
        resume.setSearchQuery("Java Developer");
        
        List<JobResult> results = adzunaService.searchJobs(resume);
        assertThat(results).hasSize(2);
        
        JobResult j1 = results.get(0);
        assertThat(j1.getCompany()).isEqualTo("Tech Corp");
        assertThat(j1.getLocation()).isEqualTo("New York");
        assertThat(j1.getCategory()).isEqualTo("Engineering");
        assertThat(j1.getContractType()).isEqualTo("permanent");
        assertThat(j1.getSalaryMin()).isEqualTo(100000.0);

        JobResult j2 = results.get(1);
        assertThat(j2.getCompany()).isEqualTo("");
        assertThat(j2.getLocation()).isEqualTo("");
    }

    private void setupRestClientToReturn(String body) {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(body);
    }
}
