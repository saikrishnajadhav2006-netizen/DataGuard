package com.dataguard.dto;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewResponseMetricsTest {
    @Test
    void exposesCountsWithoutASemgrepSpecificMetric() {
        Project project = new Project();
        project.setName("metrics-test");
        Review review = new Review();
        review.setProject(project);
        review.setQualityScore(72);

        List<Finding> findings = List.of(
                finding("SYNTAX", "Syntax error", "OPEN"),
                finding("BUG", "Potential bug", "OPEN"),
                finding("CODE_QUALITY", "PMD: Rule", "OPEN"),
                finding("SECURITY", "Semgrep: rule", "OPEN"),
                finding("ARCHITECTURE", "Large source file", "OPEN"),
                finding("SECURITY", "Resolved security issue", "RESOLVED"));

        ReviewResponse response = ReviewResponse.from(review, findings, 4, 120L);

        assertEquals(1, response.metrics().syntaxErrors());
        assertEquals(1, response.metrics().potentialBugs());
        assertEquals(1, response.metrics().codeQualityIssues());
        assertEquals(2, response.metrics().securityFindings());
        assertEquals(1, response.metrics().architectureIssues());
        assertEquals(5, response.metrics().openIssues());
    }

    private static Finding finding(String category, String title, String status) {
        Finding finding = new Finding();
        finding.setCategory(category);
        finding.setTitle(title);
        finding.setStatus(status);
        finding.setSeverity("LOW");
        return finding;
    }
}
