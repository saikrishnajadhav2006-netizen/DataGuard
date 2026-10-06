package com.dataguard.dto;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewResponse(
        Long id,
        String projectName,
        Integer qualityScore,
        LocalDateTime reviewDate,
        List<FindingResponse> findings) {

    public static ReviewResponse from(Review review, List<Finding> findings) {
        List<FindingResponse> responseFindings = findings.stream()
                .map(FindingResponse::from)
                .toList();
        return new ReviewResponse(
                review.getId(),
                review.getProject().getName(),
                review.getQualityScore(),
                review.getReviewDate(),
                responseFindings);
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
                    finding.getId(),
                    finding.getCategory(),
                    finding.getSeverity(),
                    finding.getTitle(),
                    finding.getFilePath(),
                    finding.getLineNumber(),
                    finding.getEvidence(),
                    finding.getRecommendation(),
                    finding.getStatus());
        }
    }
}