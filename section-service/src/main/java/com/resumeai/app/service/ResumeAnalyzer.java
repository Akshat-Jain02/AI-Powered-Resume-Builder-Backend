package com.resumeai.app.service;

import org.springframework.stereotype.Component;

import com.resumeai.app.model.ParsedResume;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Parses raw resume text into a structured ParsedResume object.
 * Uses regex + keyword matching — no paid APIs needed.
 */
@Component
public class ResumeAnalyzer {

    // ── Comprehensive skill dictionaries ──────────────────────────────────────

    private static final Set<String> PROGRAMMING_LANGUAGES = new LinkedHashSet<>(Arrays.asList(
            "java", "python", "javascript", "typescript", "c++", "c#", "c", "ruby", "go", "golang",
            "rust", "swift", "kotlin", "scala", "php", "perl", "r", "matlab", "dart", "lua",
            "bash", "shell", "powershell", "groovy", "haskell", "elixir", "clojure", "vb.net",
            "assembly", "cobol", "fortran", "objective-c"
    ));

    private static final Set<String> FRAMEWORKS = new LinkedHashSet<>(Arrays.asList(
            "spring", "spring boot", "spring mvc", "spring security", "spring cloud",
            "react", "reactjs", "react.js", "angular", "angularjs", "vue", "vuejs", "vue.js",
            "next.js", "nextjs", "nuxt", "svelte", "express", "expressjs", "node.js", "nodejs",
            "django", "flask", "fastapi", "laravel", "symfony", "rails", "ruby on rails",
            "asp.net", ".net core", "blazor", "hibernate", "jpa", "mybatis",
            "tensorflow", "pytorch", "keras", "sklearn", "scikit-learn", "pandas", "numpy",
            "spark", "hadoop", "kafka", "celery", "redux", "graphql", "rest", "restful",
            "microservices", "bootstrap", "tailwind", "jquery", "struts", "jsf",
            "junit", "mockito", "jest", "cypress", "selenium"
    ));

    private static final Set<String> DATABASES = new LinkedHashSet<>(Arrays.asList(
            "mysql", "postgresql", "postgres", "oracle", "sql server", "mssql", "sqlite",
            "mongodb", "cassandra", "redis", "elasticsearch", "dynamodb", "firebase",
            "couchdb", "neo4j", "influxdb", "mariadb", "h2", "db2"
    ));

    private static final Set<String> CLOUD_DEVOPS = new LinkedHashSet<>(Arrays.asList(
            "aws", "amazon web services", "azure", "gcp", "google cloud", "heroku", "digitalocean",
            "docker", "kubernetes", "k8s", "jenkins", "ci/cd", "github actions", "gitlab ci",
            "terraform", "ansible", "puppet", "chef", "vagrant", "nginx", "apache",
            "linux", "ubuntu", "centos", "git", "svn", "maven", "gradle", "npm", "yarn",
            "sonarqube", "jira", "confluence", "artifactory", "prometheus", "grafana",
            "datadog", "splunk", "elk", "logstash", "kibana"
    ));

    private static final Set<String> SOFT_TECH_SKILLS = new LinkedHashSet<>(Arrays.asList(
            "agile", "scrum", "kanban", "tdd", "bdd", "oop", "solid", "design patterns",
            "clean code", "code review", "system design", "data structures", "algorithms",
            "machine learning", "deep learning", "nlp", "computer vision", "data science",
            "data analysis", "big data", "etl", "api", "soap", "json", "xml", "yaml",
            "html", "css", "sass", "less", "responsive design", "mobile development",
            "android", "ios", "react native", "flutter", "blockchain", "web3", "devops",
            "sre", "cybersecurity", "penetration testing", "oauth", "jwt", "ssl"
    ));

    // ── Job title keywords for detecting role ─────────────────────────────────
    private static final Map<String, String> JOB_TITLE_KEYWORDS = new LinkedHashMap<>();
    static {
        JOB_TITLE_KEYWORDS.put("software engineer", "Software Engineer");
        JOB_TITLE_KEYWORDS.put("software developer", "Software Developer");
        JOB_TITLE_KEYWORDS.put("full stack", "Full Stack Developer");
        JOB_TITLE_KEYWORDS.put("fullstack", "Full Stack Developer");
        JOB_TITLE_KEYWORDS.put("frontend developer", "Frontend Developer");
        JOB_TITLE_KEYWORDS.put("front-end developer", "Frontend Developer");
        JOB_TITLE_KEYWORDS.put("front end developer", "Frontend Developer");
        JOB_TITLE_KEYWORDS.put("backend developer", "Backend Developer");
        JOB_TITLE_KEYWORDS.put("back-end developer", "Backend Developer");
        JOB_TITLE_KEYWORDS.put("back end developer", "Backend Developer");
        JOB_TITLE_KEYWORDS.put("data scientist", "Data Scientist");
        JOB_TITLE_KEYWORDS.put("data engineer", "Data Engineer");
        JOB_TITLE_KEYWORDS.put("data analyst", "Data Analyst");
        JOB_TITLE_KEYWORDS.put("machine learning engineer", "Machine Learning Engineer");
        JOB_TITLE_KEYWORDS.put("ml engineer", "ML Engineer");
        JOB_TITLE_KEYWORDS.put("ai engineer", "AI Engineer");
        JOB_TITLE_KEYWORDS.put("devops engineer", "DevOps Engineer");
        JOB_TITLE_KEYWORDS.put("cloud engineer", "Cloud Engineer");
        JOB_TITLE_KEYWORDS.put("site reliability", "SRE Engineer");
        JOB_TITLE_KEYWORDS.put("mobile developer", "Mobile Developer");
        JOB_TITLE_KEYWORDS.put("android developer", "Android Developer");
        JOB_TITLE_KEYWORDS.put("ios developer", "iOS Developer");
        JOB_TITLE_KEYWORDS.put("react developer", "React Developer");
        JOB_TITLE_KEYWORDS.put("java developer", "Java Developer");
        JOB_TITLE_KEYWORDS.put("python developer", "Python Developer");
        JOB_TITLE_KEYWORDS.put("php developer", "PHP Developer");
        JOB_TITLE_KEYWORDS.put("web developer", "Web Developer");
        JOB_TITLE_KEYWORDS.put("product manager", "Product Manager");
        JOB_TITLE_KEYWORDS.put("project manager", "Project Manager");
        JOB_TITLE_KEYWORDS.put("scrum master", "Scrum Master");
        JOB_TITLE_KEYWORDS.put("qa engineer", "QA Engineer");
        JOB_TITLE_KEYWORDS.put("test engineer", "Test Engineer");
        JOB_TITLE_KEYWORDS.put("automation engineer", "Automation Engineer");
        JOB_TITLE_KEYWORDS.put("ui developer", "UI Developer");
        JOB_TITLE_KEYWORDS.put("ux designer", "UX Designer");
        JOB_TITLE_KEYWORDS.put("ui/ux", "UI/UX Designer");
        JOB_TITLE_KEYWORDS.put("system architect", "System Architect");
        JOB_TITLE_KEYWORDS.put("solution architect", "Solution Architect");
        JOB_TITLE_KEYWORDS.put("technical lead", "Tech Lead");
        JOB_TITLE_KEYWORDS.put("tech lead", "Tech Lead");
        JOB_TITLE_KEYWORDS.put("engineering manager", "Engineering Manager");
        JOB_TITLE_KEYWORDS.put("database administrator", "DBA");
        JOB_TITLE_KEYWORDS.put("network engineer", "Network Engineer");
        JOB_TITLE_KEYWORDS.put("security engineer", "Security Engineer");
        JOB_TITLE_KEYWORDS.put("blockchain developer", "Blockchain Developer");
    }

    // ── Main parse method ─────────────────────────────────────────────────────
    public ParsedResume analyze(String rawText) {
        ParsedResume resume = new ParsedResume();
        resume.setRawText(rawText);

        String lower = rawText.toLowerCase();
        
     // Split text into lines, handling both Windows (\r\n) and Unix (\n) line endings
        String[] lines = rawText.split("\\r?\\n");

        // Extract in order
        extractContactInfo(resume, rawText, lower, lines);
        extractJobTitle(resume, lower);
        extractSkills(resume, lower);
        extractExperience(resume, lower, rawText);
        extractEducation(resume, lower);
        extractSummary(resume, lines);
        buildSearchQuery(resume);

        return resume;
    }

    // ── Contact Info ──────────────────────────────────────────────────────────
    private void extractContactInfo(ParsedResume r, String raw, String lower, String[] lines) {
        // Email
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
        Matcher emailMatcher = emailPattern.matcher(raw);
        if (emailMatcher.find()) r.setEmail(emailMatcher.group());

        // Phone (various formats)
        Pattern phonePattern = Pattern.compile(
                "(?:\\+?+\\d{1,3}+[\\s\\-.]?+)?+(?:\\(?+\\d{3}+\\)?+[\\s\\-.]?+)?+\\d{3}+[\\s\\-.]?+\\d{4}+"
        );
        Matcher phoneMatcher = phonePattern.matcher(raw);
        if (phoneMatcher.find()) r.setPhone(phoneMatcher.group().trim());

        // Name — usually the first non-empty, non-email, non-phone line
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 2 && trimmed.length() < 60
                    && !trimmed.contains("@") && !trimmed.matches(".*\\d{7,}.*")
                    && !trimmed.toLowerCase().matches(".*(resume|curriculum|vitae|cv|objective|summary|profile).*")
                    && trimmed.matches("[A-Za-z .,'\\-]+")) {
                r.setName(trimmed);
                break;
            }
        }

        // Location — look for city/state patterns
        Pattern locPattern = Pattern.compile(
                "(?i)(?:location|address|city)?+[:\\s]*+([A-Za-z ]++,\\s*+[A-Za-z ]++)(?:\\s|$)"
        );
        Matcher locMatcher = locPattern.matcher(raw);
        if (locMatcher.find()) r.setLocation(locMatcher.group(1).trim());
    }

    // ── Job Title ─────────────────────────────────────────────────────────────
    private void extractJobTitle(ParsedResume r, String lower) {
        for (Map.Entry<String, String> entry : JOB_TITLE_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                r.setJobTitle(entry.getValue());
                return;
            }
        }
        // fallback: generic
        if (lower.contains("engineer")) r.setJobTitle("Software Engineer");
        else if (lower.contains("developer")) r.setJobTitle("Software Developer");
        else if (lower.contains("analyst")) r.setJobTitle("Analyst");
        else if (lower.contains("designer")) r.setJobTitle("Designer");
        else if (lower.contains("manager")) r.setJobTitle("Manager");
        else r.setJobTitle("Professional");
    }

    // ── Skills ────────────────────────────────────────────────────────────────
    private void extractSkills(ParsedResume r, String lower) {
        List<String> foundProg = new ArrayList<>();
        List<String> foundFrameworks = new ArrayList<>();
        List<String> foundDbs = new ArrayList<>();
        List<String> foundTools = new ArrayList<>();
        List<String> allSkills = new ArrayList<>();

        for (String lang : PROGRAMMING_LANGUAGES) {
            if (containsWord(lower, lang)) {
                foundProg.add(capitalize(lang));
                allSkills.add(capitalize(lang));
            }
        }
        for (String fw : FRAMEWORKS) {
            if (containsWord(lower, fw)) {
                foundFrameworks.add(capitalize(fw));
                allSkills.add(capitalize(fw));
            }
        }
        for (String db : DATABASES) {
            if (containsWord(lower, db)) {
                foundDbs.add(capitalize(db));
                allSkills.add(capitalize(db));
            }
        }
        for (String tool : CLOUD_DEVOPS) {
            if (containsWord(lower, tool)) {
                foundTools.add(capitalize(tool));
                allSkills.add(capitalize(tool));
            }
        }
        for (String skill : SOFT_TECH_SKILLS) {
            if (containsWord(lower, skill)) {
                allSkills.add(capitalize(skill));
            }
        }

        r.setProgrammingLanguages(foundProg);
        r.setFrameworks(foundFrameworks);
        r.setDatabases(foundDbs);
        r.setTools(foundTools);

        // Deduplicate preserving order
        List<String> unique = allSkills.stream().distinct().collect(Collectors.toList());
        r.setSkills(unique);
    }

    // ── Experience ────────────────────────────────────────────────────────────
    private void extractExperience(ParsedResume r, String lower, String raw) {
        int years = 0;

        // Pattern 1: "X years of experience"
        Pattern p1 = Pattern.compile("(\\d+)\\+?+\\s*+(?:years?|yrs?)\\s*+(?:of\\s++)?(?:experience|exp)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(raw);
        if (m1.find()) {
            years = Integer.parseInt(m1.group(1));
        }

        // Pattern 2: Calculate from date ranges like "Jan 2019 – Present"
        if (years == 0) {
            Pattern dateRange = Pattern.compile(
                    "(20\\d{2}+|19\\d{2}+)\\s*+[–\\-—to]++\\s*+(20\\d{2}+|present|current|now)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher dm = dateRange.matcher(raw);
            int totalMonths = 0;
            int currentYear = java.time.Year.now().getValue();
            while (dm.find()) {
                try {
                    int startYear = Integer.parseInt(dm.group(1));
                    String endStr = dm.group(2).toLowerCase();
                    int endYear = (endStr.equals("present") || endStr.equals("current") || endStr.equals("now"))
                            ? currentYear : Integer.parseInt(endStr);
                    totalMonths += (endYear - startYear) * 12;
                } catch (NumberFormatException ignored) {}
            }
            years = Math.max(0, totalMonths / 12);
        }

        // Pattern 3: Count job entries as proxy
        if (years == 0) {
            long jobCount = Pattern.compile(
                    "(?i)(software engineer|developer|analyst|manager|intern|associate)",
                    Pattern.CASE_INSENSITIVE
            ).matcher(raw).results().count();
            if (jobCount >= 3) years = 4;
            else if (jobCount >= 2) years = 2;
            else if (jobCount >= 1) years = 1;
        }

        r.setYearsOfExperience(years);

        // Determine level
        if (years == 0) r.setExperienceLevel("FRESHER");
        else if (years <= 2) r.setExperienceLevel("JUNIOR");
        else if (years <= 5) r.setExperienceLevel("MID");
        else if (years <= 9) r.setExperienceLevel("SENIOR");
        else r.setExperienceLevel("LEAD");
    }

    // ── Education ────────────────────────────────────────────────────────────
    private void extractEducation(ParsedResume r, String lower) {
        if (lower.contains("ph.d") || lower.contains("phd") || lower.contains("doctorate")) {
            r.setHighestDegree("PhD");
        } else if (lower.contains("master") || lower.contains("m.tech") || lower.contains("mtech")
                || lower.contains("m.e.") || lower.contains("mba") || lower.contains("m.sc")
                || lower.contains("m.s.") || lower.contains("post graduate")) {
            r.setHighestDegree("Masters");
        } else if (lower.contains("bachelor") || lower.contains("b.tech") || lower.contains("btech")
                || lower.contains("b.e.") || lower.contains("b.sc") || lower.contains("b.s.")
                || lower.contains("undergraduate") || lower.contains("honours")) {
            r.setHighestDegree("Bachelors");
        } else if (lower.contains("diploma") || lower.contains("associate")) {
            r.setHighestDegree("Diploma");
        } else if (lower.contains("high school") || lower.contains("secondary")) {
            r.setHighestDegree("High School");
        } else {
            r.setHighestDegree("Bachelors"); // default assumption
        }

        // Field of study
        if (lower.contains("computer science") || lower.contains("cse")) r.setFieldOfStudy("Computer Science");
        else if (lower.contains("information technology") || lower.contains("i.t.") || lower.contains(" it "))
            r.setFieldOfStudy("Information Technology");
        else if (lower.contains("software engineering")) r.setFieldOfStudy("Software Engineering");
        else if (lower.contains("electronics") || lower.contains("electrical")) r.setFieldOfStudy("Electronics");
        else if (lower.contains("mechanical")) r.setFieldOfStudy("Mechanical Engineering");
        else if (lower.contains("mathematics") || lower.contains("statistics")) r.setFieldOfStudy("Mathematics");
        else if (lower.contains("data science")) r.setFieldOfStudy("Data Science");
        else r.setFieldOfStudy("Engineering");
    }

    // ── Summary ───────────────────────────────────────────────────────────────
    private void extractSummary(ParsedResume r, String[] lines) {
        boolean inSummary = false;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();
            if (lower.matches(".*(summary|objective|profile|about me|overview).*")) {
                inSummary = true;
                continue;
            }
            if (inSummary) {
                if (lower.matches(".*(experience|education|skills|projects|work history|employment).*")) break;
                if (!trimmed.isEmpty()) {
                    sb.append(trimmed).append(" ");
                    if (sb.length() > 400) break;
                }
            }
        }
        String summary = sb.toString().trim();
        // Fallback: grab first meaningful paragraph
        if (summary.isEmpty()) {
            for (String line : lines) {
                String t = line.trim();
                if (t.length() > 80 && !t.contains("@")) {
                    summary = t;
                    break;
                }
            }
        }
        r.setSummary(summary.length() > 500 ? summary.substring(0, 497) + "..." : summary);
    }

    // ── Build Adzuna search query from skills + job title ─────────────────────
    private void buildSearchQuery(ParsedResume r) {
        // Keep search query SHORT — Adzuna returns 0 results for long/complex queries.
        // Just use the job title. AdzunaService will handle skill-based fallbacks.
        String query = r.getJobTitle() != null && !r.getJobTitle().isEmpty()
                ? r.getJobTitle()
                : "software developer";
        r.setSearchQuery(query);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean containsWord(String text, String word) {
        // Match whole word or phrase
        String escaped = Pattern.quote(word);
        Pattern p = Pattern.compile("(?<![a-zA-Z0-9])" + escaped + "(?![a-zA-Z0-9])", Pattern.CASE_INSENSITIVE);
        return p.matcher(text).find();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        // Special cases
        Map<String, String> specialCase = new HashMap<>();
        specialCase.put("javascript", "JavaScript");
        specialCase.put("typescript", "TypeScript");
        specialCase.put("mysql", "MySQL");
        specialCase.put("postgresql", "PostgreSQL");
        specialCase.put("mongodb", "MongoDB");
        specialCase.put("dynamodb", "DynamoDB");
        specialCase.put("reactjs", "ReactJS");
        specialCase.put("react.js", "React.js");
        specialCase.put("nodejs", "Node.js");
        specialCase.put("node.js", "Node.js");
        specialCase.put("next.js", "Next.js");
        specialCase.put("nextjs", "Next.js");
        specialCase.put("vuejs", "Vue.js");
        specialCase.put("vue.js", "Vue.js");
        specialCase.put("expressjs", "ExpressJS");
        specialCase.put("c++", "C++");
        specialCase.put("c#", "C#");
        specialCase.put("vb.net", "VB.NET");
        specialCase.put("asp.net", "ASP.NET");
        specialCase.put("gcp", "GCP");
        specialCase.put("aws", "AWS");
        specialCase.put("ci/cd", "CI/CD");
        specialCase.put("k8s", "Kubernetes");
        specialCase.put("sql server", "SQL Server");
        specialCase.put("spring boot", "Spring Boot");
        specialCase.put("spring mvc", "Spring MVC");
        specialCase.put("spring cloud", "Spring Cloud");
        specialCase.put("spring security", "Spring Security");
        specialCase.put("ruby on rails", "Ruby on Rails");
        specialCase.put("scikit-learn", "Scikit-learn");
        specialCase.put("github actions", "GitHub Actions");
        specialCase.put("gitlab ci", "GitLab CI");

        String lc = s.toLowerCase();
        if (specialCase.containsKey(lc)) return specialCase.get(lc);

        // Capitalize first letter of each word
        return Arrays.stream(s.split(" "))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }
}