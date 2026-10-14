package com.parvnigam.careerbridge;

import java.util.*;
import java.util.regex.Pattern;

/** Versioned, explainable content checks. Does not emulate an employer's ATS. */
public final class ResumeScorer {
    private ResumeScorer() {}
    public static final String VERSION = "CONTENT_V1";
    public record Check(String key, String title, int earned, int possible, String detail, String suggestion) {}
    public record Readiness(int score, int wordCount, List<Check> checks, List<String> suggestions) {}
    public record Match(Integer score, String method, List<String> requiredSkills, List<String> matchedSkills,
                        List<String> missingSkills, String explanation) {}
    static final Map<String,List<String>> ALIASES = new LinkedHashMap<>();
    static {
        ALIASES.put("Java", List.of("java"));
        ALIASES.put("Spring Boot", List.of("spring boot", "springboot"));
        ALIASES.put("SQL", List.of("sql", "structured query language"));
        ALIASES.put("JavaScript", List.of("javascript", "js"));
        ALIASES.put("TypeScript", List.of("typescript"));
        ALIASES.put("React", List.of("react", "reactjs", "react.js"));
        ALIASES.put("Node.js", List.of("node.js", "nodejs", "node js"));
        ALIASES.put("Python", List.of("python"));
        ALIASES.put("C++", List.of("c++", "cpp"));
        ALIASES.put("C#", List.of("c#", "csharp"));
        ALIASES.put("HTML", List.of("html", "html5"));
        ALIASES.put("CSS", List.of("css", "css3"));
        ALIASES.put("Git", List.of("git"));
        ALIASES.put("Docker", List.of("docker"));
        ALIASES.put("Redis", List.of("redis"));
        ALIASES.put("PostgreSQL", List.of("postgresql", "postgres"));
        ALIASES.put("MySQL", List.of("mysql"));
        ALIASES.put("MongoDB", List.of("mongodb", "mongo db"));
        ALIASES.put("DSA", List.of("dsa", "data structures", "algorithms"));
        ALIASES.put("Machine Learning", List.of("machine learning"));
        ALIASES.put("REST APIs", List.of("rest api", "rest apis", "restful"));
        ALIASES.put("AWS", List.of("aws", "amazon web services"));
        ALIASES.put("JUnit", List.of("junit", "junit5", "junit 5"));
        ALIASES.put("Linux", List.of("linux"));
        ALIASES.put("Kubernetes", List.of("kubernetes", "k8s"));
        ALIASES.put("Excel", List.of("excel"));
        ALIASES.put("Communication", List.of("communication"));
        ALIASES.put("Figma", List.of("figma"));
        ALIASES.put("Angular", List.of("angular"));
        ALIASES.put("Django", List.of("django"));
        ALIASES.put("FastAPI", List.of("fastapi", "fast api"));
        ALIASES.put("JPA", List.of("jpa", "java persistence api"));
        ALIASES.put("Hibernate", List.of("hibernate"));
        ALIASES.put("CI/CD", List.of("ci/cd", "continuous integration", "continuous delivery"));
    }
    static boolean contains(String text, String term) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_+#])" + Pattern.quote(term)
            + "(?![\\p{L}\\p{N}_+#])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
    }
    static String canonical(String term) {
        String value = term.trim();
        return ALIASES.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(value)
            || e.getValue().stream().anyMatch(a -> a.equalsIgnoreCase(value)))
            .map(Map.Entry::getKey).findFirst().orElse(value);
    }
    static boolean mentions(String text, String skill) {
        return ALIASES.getOrDefault(skill, List.of(skill)).stream().anyMatch(alias -> contains(text, alias));
    }
    public static List<String> detectSkills(String text) {
        return ALIASES.keySet().stream().filter(skill -> mentions(text, skill)).toList();
    }
    public static Match match(String text, List<String> skills, String method) {
        // Deduplicate aliases/case variants so repeated keywords do not inflate coverage.
        Map<String,String> unique = new LinkedHashMap<>();
        skills.stream().filter(Objects::nonNull).map(ResumeScorer::canonical).filter(s -> !s.isBlank())
            .forEach(s -> unique.putIfAbsent(s.toLowerCase(Locale.ROOT), s));
        List<String> required = new ArrayList<>(unique.values());
        List<String> matched = required.stream().filter(s -> mentions(text, s)).toList();
        List<String> missing = required.stream().filter(s -> !matched.contains(s)).toList();
        Integer score = required.isEmpty() ? null : (int)Math.round(100.0 * matched.size() / required.size());
        String explanation = required.isEmpty()
            ? "No skills could be identified for this target. No job-match score was assigned."
            : matched.size() + " of " + required.size() + " target skills mentioned. Each skill has equal weight; repetition adds no points. This checks mentions, not proficiency or hiring eligibility.";
        return new Match(score, method, required, matched, missing, explanation);
    }
    static String heading(String line) {
        return line.trim().toLowerCase(Locale.ROOT).replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "").replaceAll("\\s+", " ");
    }
    static final Set<String> EDUCATION = Set.of("education", "academic background", "academic qualifications", "qualifications");
    static final Set<String> SKILLS = Set.of("skills", "technical skills", "core skills", "technical competencies", "technologies");
    static final Set<String> EVIDENCE = Set.of("projects", "personal projects", "academic projects", "technical projects", "experience", "work experience", "professional experience", "internships", "internship experience");
    static final Set<String> OTHER = Set.of("summary", "professional summary", "profile", "objective", "career objective", "certifications", "achievements", "awards", "interests", "languages", "activities", "references", "leadership");
    static boolean section(String text, Set<String> names) {
        return text.lines().map(ResumeScorer::heading).anyMatch(names::contains);
    }
    static String evidenceText(String text) {
        StringBuilder result = new StringBuilder(); boolean inside = false;
        for (String line : text.split("\\R")) {
            String key = heading(line);
            if (EVIDENCE.contains(key)) { inside = true; continue; }
            if (EDUCATION.contains(key) || SKILLS.contains(key) || OTHER.contains(key)) inside = false;
            if (inside) result.append(line).append('\n');
        }
        return result.toString();
    }
    public static Readiness readiness(String text) {
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        List<Check> checks = new ArrayList<>();
        boolean email = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE).matcher(text).find();
        boolean phone = Pattern.compile("(?<!\\d)(?:\\+\\d{1,3}[ -]?)?(?:\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}|\\d{5}[ -]?\\d{5})(?!\\d)").matcher(text).find();
        checks.add(new Check("contact", "Contact details", (email?10:0)+(phone?10:0), 20,
            "Email " + (email?"found":"not detected") + "; phone " + (phone?"found":"not detected") + ".",
            "Put a valid email and phone number in the document body. Review detection if your phone format differs."));
        boolean education = section(text, EDUCATION), skills = section(text, SKILLS), evidence = section(text, EVIDENCE);
        checks.add(new Check("sections", "Standard sections", (education?10:0)+(skills?10:0)+(evidence?10:0), 30,
            "Education " + (education?"found":"not detected") + "; Skills " + (skills?"found":"not detected") + "; Projects or Experience " + (evidence?"found":"not detected") + ".",
            "Use separate, standard section headings: Education, Skills, and Projects or Experience. Freshers can use projects instead of work experience."));
        String projectText = evidenceText(text);
        boolean action = Pattern.compile("\\b(built|developed|implemented|designed|created|tested|automated|optimized|improved|reduced|led|deployed|integrated|analyzed|analysed)\\b", Pattern.CASE_INSENSITIVE).matcher(projectText).find();
        boolean metric = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\s*(?:%|ms\\b|seconds?\\b|minutes?\\b|users?\\b|requests?\\b|tests?\\b|endpoints?\\b|features?\\b|records?\\b|hours?\\b)", Pattern.CASE_INSENSITIVE).matcher(projectText).find();
        boolean link = Pattern.compile("(?:https?://|www\\.|github\\.com/|linkedin\\.com/)", Pattern.CASE_INSENSITIVE).matcher(text).find();
        checks.add(new Check("evidence", "Contribution and evidence", (action?10:0)+(metric?10:0)+(link?5:0), 25,
            "Action in project/experience text " + (action?"found":"not detected") + "; measurable detail " + (metric?"found":"not detected") + "; portfolio/profile link " + (link?"found":"not detected") + ".",
            "Explain your contribution with an action and a real outcome or scope. Add a relevant project/profile link. Never invent metrics or experience."));
        checks.add(new Check("text", "Extracted text completeness", words>=80?15:words>=30?8:0, 15,
            words + " words extracted. Full credit at 80 words; partial credit at 30. Extraction does not prove layout compatibility.",
            "Preview the extracted text below. If content is missing or out of order, export a simpler text-based document."));
        checks.add(new Check("length", "Content length", words>=100&&words<=1000?10:words>=50&&words<=1800?5:0, 10,
            "Full credit for 100–1,000 extracted words; partial credit for 50–1,800. This is a local content heuristic, not an ATS limit.",
            "Keep relevant education, skills and project evidence concise. Do not add filler just to gain points."));
        return new Readiness(checks.stream().mapToInt(Check::earned).sum(), words, List.copyOf(checks),
            checks.stream().filter(c -> c.earned()<c.possible()).map(Check::suggestion).toList());
    }
}
