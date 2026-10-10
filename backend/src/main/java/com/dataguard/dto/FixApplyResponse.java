package com.dataguard.dto;

public record FixApplyResponse(boolean pullRequestCreated, String branch, Integer pullRequestNumber,
                               String pullRequestUrl, String message) {}
