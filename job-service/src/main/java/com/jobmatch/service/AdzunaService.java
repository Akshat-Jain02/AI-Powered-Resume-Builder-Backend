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

    private static final String SENIOR = "senior";

    public List<JobResult> searchJobs(ParsedResume resume) {
        String titleQuery = resume.getJobTitle();
        if (titleQuery == null || titleQuery.isBlank()) {
            titleQuery = "software developer";
        }

        List<JobResult> results = fetchFromAdzuna(titleQuery);

        if (results.isEmpty() && resume.getProgrammingLanguages() != null
                && !resume.getProgrammingLanguages().isEmpty()) {
            String skillQuery = resume.getProgrammingLanguages().get(0) + " developer";
            results = fetchFromAdzuna(skillQuery);
        }

        if (results.isEmpty()) {
            results = fetchFromAdzuna("developer");
        }

        scoreJobs(results, resume);
        results.sort(Comparator.comparingInt(JobResult::getMatchScore).reversed());

        return results;
    }

    private List<JobResult> fetchFromAdzuna(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(
                    "https://api.adzuna.com/v1/api/jobs/%s/search/1?app_id=%s&app_key=%s&results_per_page=%d&what=%s",
                    country, appId, appKey, resultsPerPage, encodedQuery
            );

            String response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                log.error("[Adzuna] Empty response received");
                return new ArrayList<>();
            }

            return parseAdzunaResponse(response);

        } catch (Exception e) {
            log.error("[Adzuna] API call failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<JobResult> parseAdzunaResponse(String jsonResponse) {
        List<JobResult> jobs = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            JsonNode error = root.get("error");
            if (error != null) {
                log.error("[Adzuna] API returned error: {}", error.asText());
                return jobs;
            }

            JsonNode exception = root.get("exception");
            if (exception != null) {
                log.error("[Adzuna] API returned exception: {}", exception.asText());
                return jobs;
            }

            JsonNode results = root.get("results");
            if (results == null) {
                log.error("[Adzuna] No 'results' field in response. Full response: {}", jsonResponse);
                return jobs;
            }
            if (!results.isArray()) {
                log.error("[Adzuna] 'results' is not an array");
                return jobs;
            }
            if (results.isEmpty()) {
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

        } catch (Exception e) {
            log.error("[Adzuna] Parse error: {}", e.getMessage(), e);
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
            scoreSingleJob(job, resume, allResumeTerms, commonTechTerms);
        }
    }

    private void scoreSingleJob(JobResult job, ParsedResume resume, Set<String> allResumeTerms, List<String> commonTechTerms) {
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

        int score = calculateTitleScore(job, resume) + calculateSkillsScore(allResumeTerms, matched) + calculateExperienceScore(jobText, resume);

        score = Math.clamp(score, 0, 100);
        job.setMatchScore(score);

        if (score >= 75)      job.setMatchLevel("EXCELLENT");
        else if (score >= 50) job.setMatchLevel("GOOD");
        else if (score >= 25) job.setMatchLevel("FAIR");
        else                  job.setMatchLevel("LOW");

        job.setMatchedSkills(matched.stream().distinct().limit(10).toList());
        job.setMissingSkills(missing.stream().distinct().limit(6).toList());
    }

    private int calculateTitleScore(JobResult job, ParsedResume resume) {
        if (job.getTitle() != null && resume.getJobTitle() != null) {
            String jt = job.getTitle().toLowerCase();
            String rt = resume.getJobTitle().toLowerCase();
            if (jt.contains(rt) || rt.contains(jt)) return 25;
            if (sharesWord(jt, rt)) return 12;
        }
        return 0;
    }

    private int calculateSkillsScore(Set<String> allResumeTerms, List<String> matched) {
        if (!allResumeTerms.isEmpty()) {
            int cap = Math.clamp(allResumeTerms.size(), 1, 10);
            return (int) Math.min(60, (matched.size() * 60.0 / cap));
        }
        return 30;
    }

    private int calculateExperienceScore(String jobText, ParsedResume resume) {
        String expLevel = resume.getExperienceLevel() != null ? resume.getExperienceLevel() : "";
        return switch (expLevel) {
            case "FRESHER", "JUNIOR" -> scoreJunior(jobText);
            case "MID" -> scoreMid(jobText);
            case "SENIOR", "LEAD" -> scoreSenior(jobText);
            default -> 8;
        };
    }

    private int scoreJunior(String jobText) {
        if (jobText.contains("junior") || jobText.contains("graduate")
                || jobText.contains("entry") || jobText.contains("fresher")) return 15;
        if (!jobText.contains(SENIOR) && !jobText.contains("lead")) return 7;
        return 8;
    }

    private int scoreMid(String jobText) {
        if (!jobText.contains(SENIOR) && !jobText.contains("junior")) return 10;
        return 5;
    }

    private int scoreSenior(String jobText) {
        if (jobText.contains(SENIOR) || jobText.contains("lead")
                || jobText.contains("principal")) return 15;
        return 7;
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