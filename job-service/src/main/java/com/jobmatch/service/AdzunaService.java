package com.jobmatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmatch.model.JobResult;
import com.jobmatch.model.ParsedResume;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdzunaService {

    @Value("${adzuna.app.id}")
    private String appId;

    @Value("${adzuna.app.key}")
    private String appKey;

//    @Value("${adzuna.country:India}")
    private String country = "in";

    @Value("${adzuna.results.per.page:20}")
    private int resultsPerPage;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<JobResult> searchJobs(ParsedResume resume) {
        List<JobResult> results = new ArrayList<>();

        String titleQuery = resume.getJobTitle();
        if (titleQuery == null || titleQuery.isBlank()) {
            titleQuery = "software developer";
        }

        // System.out.println("[Adzuna] Primary query: " + titleQuery);
        results = fetchFromAdzuna(titleQuery);
        // System.out.println("[Adzuna] Primary search returned: " + results.size() + " jobs");

        if (results.isEmpty() && resume.getProgrammingLanguages() != null
                && !resume.getProgrammingLanguages().isEmpty()) {
            String skillQuery = resume.getProgrammingLanguages().get(0) + " developer";
            // System.out.println("[Adzuna] Fallback query: " + skillQuery);
            results = fetchFromAdzuna(skillQuery);
            // System.out.println("[Adzuna] Fallback returned: " + results.size() + " jobs");
        }

        if (results.isEmpty()) {
            // System.out.println("[Adzuna] Last resort query: developer");
            results = fetchFromAdzuna("developer");
            // System.out.println("[Adzuna] Last resort returned: " + results.size() + " jobs");
        }

        scoreJobs(results, resume);
        results.sort(Comparator.comparingInt(JobResult::getMatchScore).reversed());

        return results;
    }

    private List<JobResult> fetchFromAdzuna(String query) {
        try {
        	// System.out.println();
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(
                    "https://api.adzuna.com/v1/api/jobs/%s/search/1?app_id=%s&app_key=%s&results_per_page=%d&what=%s",
                    country, appId, appKey, resultsPerPage, encodedQuery
            );

            // System.out.println("[Adzuna] Calling URL: " + url.replace(appKey, "***"));

            String response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                System.err.println("[Adzuna] Empty response received");
                return new ArrayList<>();
            }

            // System.out.println("[Adzuna] Raw response (first 300 chars): "


            return parseAdzunaResponse(response);

        } catch (Exception e) {
            System.err.println("[Adzuna] API call failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<JobResult> parseAdzunaResponse(String jsonResponse) {
        List<JobResult> jobs = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            JsonNode count = root.get("count");
            if (count != null) {
                // System.out.println("[Adzuna] Total available in Adzuna: " + count.asInt());
            }

            JsonNode error = root.get("error");
            if (error != null) {
                System.err.println("[Adzuna] API returned error: " + error.asText());
                return jobs;
            }

            JsonNode exception = root.get("exception");
            if (exception != null) {
                System.err.println("[Adzuna] API returned exception: " + exception.asText());
                return jobs;
            }

            JsonNode results = root.get("results");
            if (results == null) {
                System.err.println("[Adzuna] No 'results' field in response. Full response: " + jsonResponse);
                return jobs;
            }
            if (!results.isArray()) {
                System.err.println("[Adzuna] 'results' is not an array");
                return jobs;
            }
            if (results.isEmpty()) {
                // System.out.println("[Adzuna] Results array is empty");
                return jobs;
            }

            for (JsonNode node : results) {
                JobResult job = new JobResult();
                job.setId(getText(node, "id"));
                job.setTitle(getText(node, "title"));
                job.setDescription(getText(node, "description"));
                job.setRedirectUrl(getText(node, "redirect_url"));
                job.setCreated(getText(node, "created"));
                job.setContractType(getText(node, "contract_type"));
                job.setContractTime(getText(node, "contract_time"));

                JsonNode salMin = node.get("salary_min");
                JsonNode salMax = node.get("salary_max");
                if (salMin != null && !salMin.isNull()) job.setSalaryMin(salMin.asDouble());
                if (salMax != null && !salMax.isNull()) job.setSalaryMax(salMax.asDouble());

                JsonNode company = node.get("company");
                if (company != null) job.setCompany(getText(company, "display_name"));

                JsonNode location = node.get("location");
                if (location != null) job.setLocation(getText(location, "display_name"));

                JsonNode category = node.get("category");
                if (category != null) job.setCategory(getText(category, "label"));

                jobs.add(job);
            }

            // System.out.println("[Adzuna] Successfully parsed " + jobs.size() + " jobs");

        } catch (Exception e) {
            System.err.println("[Adzuna] Parse error: " + e.getMessage());
            e.printStackTrace();
        }
        return jobs;
    }

    private void scoreJobs(List<JobResult> jobs, ParsedResume resume) {
        Set<String> allResumeTerms = new HashSet<>();
        if (resume.getSkills() != null)
            resume.getSkills().forEach(s -> allResumeTerms.add(s.toLowerCase()));
        if (resume.getProgrammingLanguages() != null)
            resume.getProgrammingLanguages().forEach(s -> allResumeTerms.add(s.toLowerCase()));
        if (resume.getFrameworks() != null)
            resume.getFrameworks().forEach(s -> allResumeTerms.add(s.toLowerCase()));
        if (resume.getDatabases() != null)
            resume.getDatabases().forEach(s -> allResumeTerms.add(s.toLowerCase()));
        if (resume.getTools() != null)
            resume.getTools().forEach(s -> allResumeTerms.add(s.toLowerCase()));

        List<String> commonTechTerms = Arrays.asList(
                "java", "python", "javascript", "typescript", "c++", "c#", "golang",
                "react", "angular", "vue", "node.js", "spring", "spring boot",
                "django", "flask", "fastapi", "mysql", "postgresql", "mongodb",
                "redis", "aws", "azure", "gcp", "docker", "kubernetes", "git",
                "machine learning", "data science", "tensorflow", "pytorch", "sql",
                "rest", "api", "microservices", "agile", "scrum", "linux"
        );

        for (JobResult job : jobs) {
            String jobText = ((job.getTitle() != null ? job.getTitle() : "") + " "
                    + (job.getDescription() != null ? job.getDescription() : "")).toLowerCase();

            List<String> matched = new ArrayList<>();
            List<String> missing = new ArrayList<>();

            for (String skill : allResumeTerms) {
                if (!skill.isBlank() && jobText.contains(skill)) {
                    matched.add(capitalize(skill));
                }
            }

            for (String term : commonTechTerms) {
                if (jobText.contains(term) && !allResumeTerms.contains(term)) {
                    missing.add(capitalize(term));
                }
            }

            int score = 0;

            if (job.getTitle() != null && resume.getJobTitle() != null) {
                String jt = job.getTitle().toLowerCase();
                String rt = resume.getJobTitle().toLowerCase();
                if (jt.contains(rt) || rt.contains(jt)) score += 25;
                else if (sharesWord(jt, rt)) score += 12;
            }

            if (!allResumeTerms.isEmpty()) {
                int cap = Math.max(1, Math.min(allResumeTerms.size(), 10));
                score += (int) Math.min(60, (matched.size() * 60.0 / cap));
            } else {
                score += 30;
            }

            String expLevel = resume.getExperienceLevel() != null ? resume.getExperienceLevel() : "";
            if ("FRESHER".equals(expLevel) || "JUNIOR".equals(expLevel)) {
                if (jobText.contains("junior") || jobText.contains("graduate")
                        || jobText.contains("entry") || jobText.contains("fresher")) score += 15;
                else if (!jobText.contains("senior") && !jobText.contains("lead")) score += 7;
            } else if ("MID".equals(expLevel)) {
                if (!jobText.contains("senior") && !jobText.contains("junior")) score += 10;
                else score += 5;
            } else if ("SENIOR".equals(expLevel) || "LEAD".equals(expLevel)) {
                if (jobText.contains("senior") || jobText.contains("lead")
                        || jobText.contains("principal")) score += 15;
                else score += 7;
            } else {
                score += 8;
            }

            score = Math.min(100, Math.max(0, score));
            job.setMatchScore(score);

            if (score >= 75)      job.setMatchLevel("EXCELLENT");
            else if (score >= 50) job.setMatchLevel("GOOD");
            else if (score >= 25) job.setMatchLevel("FAIR");
            else                  job.setMatchLevel("LOW");

            job.setMatchedSkills(matched.stream().distinct().limit(10).collect(Collectors.toList()));
            job.setMissingSkills(missing.stream().distinct().limit(6).collect(Collectors.toList()));
        }
    }

    private boolean sharesWord(String a, String b) {
        Set<String> wa = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> wb = new HashSet<>(Arrays.asList(b.split("\\s+")));
        wa.retainAll(wb);
        wa.removeAll(Set.of("and", "or", "the", "a", "an", "of", "in", "at", "for", "to"));
        return !wa.isEmpty();
    }

    private String getText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText("") : "";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}