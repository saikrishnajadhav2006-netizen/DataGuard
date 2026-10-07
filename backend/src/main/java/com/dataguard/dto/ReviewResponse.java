package com.dataguard.dto;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewResponse(
        Long id,
        String projectName,
        Integer overallScore,
        Integer securityScore,
        Integer qualityScore,
        Integer architectureScore,
        String status,
        LocalDateTime reviewDate,
        LocalDateTime completedAt,
        List<FindingResponse> findings) {

    public static ReviewResponse from(Review review, List<Finding> findings) {
        List<FindingResponse> responseFindings = findings.stream()
                .map(FindingResponse::from)
                .toList();
        return new ReviewResponse(
                review.getId(),
                review.getProject().getName(),
                review.getOverallScore(),
                review.getSecurityScore(),
                review.getQualityScore(),
                review.getArchitectureScore(),
                review.getStatus(),
                review.getReviewDate(),
                review.getCompletedAt(),
                responseFindings);
    }

    public record FindingResponse(
            Long id,
            String category,
            String severity,
            String source,
            String ruleId,
            String title,
            String description,
            String filePath,
            Integer lineNumber,
            String evidence,
            String recommendation,
            String status) {

        static FindingResponse from(Finding finding) {
            return new FindingResponse(
                    finding.getId(),
                    finding.getCategory(),
                    finding.getSeverity(),
                    finding.getSource(),
                    finding.getRuleId(),
                    finding.getTitle(),
                    finding.getDescription(),
                    finding.getFilePath(),
                    finding.getLineNumber(),
                    finding.getEvidence(),
                    finding.getRecommendation(),
                    finding.getStatus());
        }
    }
}