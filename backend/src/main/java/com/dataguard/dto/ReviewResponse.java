package com.dataguard.dto;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public record ReviewResponse(
        Long id,
        String projectName,
        Integer qualityScore,
        LocalDateTime reviewDate,
        Integer fileCount,
        Long linesOfCode,
        List<FindingResponse> findings,
        ReviewMetrics metrics) {

    public static ReviewResponse from(Review review, List<Finding> findings) {
        return from(review, findings, null, null);
    }

    public static ReviewResponse from(Review review, List<Finding> findings, Integer fileCount, Long linesOfCode) {
        List<FindingResponse> responseFindings = findings.stream().map(FindingResponse::from).toList();
        ReviewMetrics metrics = ReviewMetrics.from(findings);
        return new ReviewResponse(review.getId(), review.getProject().getName(), review.getQualityScore(),
                review.getReviewDate(), fileCount, linesOfCode, responseFindings, metrics);
    }

    /**
     * Category counts can overlap; openIssues counts each finding once.
     * Semgrep is deliberately not exposed as a separate metric. Syntax errors
     * count only when an analyzer explicitly emits a syntax/parse/compilation finding.
     */
    public record ReviewMetrics(
            long syntaxErrors,
            long potentialBugs,
            long codeQualityIssues,
            long securityFindings,
            long architectureIssues,
            long openIssues) {

        private static ReviewMetrics from(List<Finding> findings) {
            long syntax = 0, bugs = 0, quality = 0, security = 0, architecture = 0, open = 0;
            for (Finding finding : findings) {
                String category = normalize(finding.getCategory());
                String title = normalize(finding.getTitle());
                String status = normalize(finding.getStatus());
                if (status.isEmpty() || "OPEN".equals(status)) open++;
                if ("SYNTAX".equals(category) || title.contains("SYNTAX ERROR")
                        || title.contains("PARSE ERROR") || title.contains("COMPILATION ERROR")) syntax++;
                if ("BUG".equals(category) || "POTENTIAL_BUG".equals(category)
                        || title.contains("POTENTIAL BUG")) bugs++;
                if ("CODE_QUALITY".equals(category) || "CODE QUALITY".equals(category)) quality++;
                if ("SECURITY".equals(category)) security++;
                if ("ARCHITECTURE".equals(category)) architecture++;
            }
            return new ReviewMetrics(syntax, bugs, quality, security, architecture, open);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        }
    }

    public record FindingResponse(
            Long id,
            String category,
            String severity,
            String title,
            String filePath,
            Integer lineNumber,
            String evidence,
            String recommendation,
            String status) {

        private static FindingResponse from(Finding finding) {
            return new FindingResponse(
                    finding.getId(), finding.getCategory(), finding.getSeverity(), finding.getTitle(),
                    finding.getFilePath(), finding.getLineNumber(), finding.getEvidence(),
                    finding.getRecommendation(), finding.getStatus());
        }
    }
}
