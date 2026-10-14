package com.parvnigam.careerbridge;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResumeScorerTests {
    static final String STRONG = """
        Example Student
        student@example.com +91 9876543210 https://github.com/example
        Education
        BTech Computer Science 2024–2028
        Skills
        Java Spring Boot SQL Docker Git JUnit
        Projects
        Built a recruitment portal with Java and Spring Boot. Implemented 12 endpoints and tested
        authentication, validation, persistence and ownership restrictions with JUnit integration tests.
        Deployed a Docker development stack and documented the installation process for teammates.
        Created a student profile flow with education, skills and project descriptions. Integrated SQL
        persistence and measured the response behaviour when requests contain missing or invalid fields.
        Designed an application tracker that records stage changes and displays helpful explanations.
        Automated tests for success cases, failure cases and repeated submissions. Reviewed the changes
        with teammates and improved the user interface based on their feedback during project testing.
        """;
    @Test void completeStudentResumeReceivesExplainedFullCredit() {
        var result=ResumeScorer.readiness(STRONG);
        assertEquals(100,result.score());assertEquals(5,result.checks().size());assertTrue(result.suggestions().isEmpty());
        assertEquals(result.score(),result.checks().stream().mapToInt(ResumeScorer.Check::earned).sum());
    }
    @Test void shortKeywordDumpDoesNotReceiveHighReadiness() {
        var result=ResumeScorer.readiness("Java Java Java SQL Docker");
        assertEquals(0,result.score());assertFalse(result.suggestions().isEmpty());
    }
    @Test void gradesAndDatesDoNotCountAsProjectOutcomes() {
        var result=ResumeScorer.readiness("Education\n95% in 2024\nSkills\nJava\nProjects\nA recruitment portal");
        assertEquals(0,result.checks().stream().filter(c->c.key().equals("evidence")).findFirst().orElseThrow().earned());
    }
    @Test void aliasesDeduplicateAndJavaDoesNotMatchJavascript() {
        var result=ResumeScorer.match("JavaScript React.js Postgres",List.of("Java","JavaScript","JS","React","ReactJS","PostgreSQL","postgres"),"TEST");
        assertEquals(4,result.requiredSkills().size());assertEquals(75,result.score());assertEquals(List.of("Java"),result.missingSkills());
    }
    @Test void languageSymbolsAndBoundariesArePreserved() {
        assertEquals(List.of("C++","C#"),ResumeScorer.match("C++ C#",List.of("C","C++","C#"),"TEST").matchedSkills());
        assertEquals(List.of("SQL","Git"),ResumeScorer.match("MySQL GitHub",List.of("SQL","Git"),"TEST").missingSkills());
    }
    @Test void repeatedSkillMentionsDoNotInflateScore() {
        assertEquals(50,ResumeScorer.match("Java ".repeat(500),List.of("Java","SQL"),"TEST").score());
    }
    @Test void noIdentifiedSkillsMeansNoMatchScore() {
        var result=ResumeScorer.match(STRONG,ResumeScorer.detectSkills("A specialist vacancy requiring niche tools."),"DICTIONARY");
        assertNull(result.score());assertTrue(result.explanation().contains("No job-match score"));
    }
}
