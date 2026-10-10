package com.dataguard.dto;

public record FixResponse(
        Long findingId,
        String description,
        String originalCode,
        String suggestedCode,
        boolean safeToApply,
        String patch,
        String filePath,
        Integer lineNumber,
        String repository,
        String branch,
        String commitSha,
        String reason) {
}
