package com.dataguard.dto;

public record FixResponse(
        Long findingId,
        String description,
        String originalCode,
        String suggestedCode,
        boolean safeToApply) {
}