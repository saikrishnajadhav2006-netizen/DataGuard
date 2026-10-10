package com.dataguard.dto;

public record FixApprovalRequest(boolean approved, String repository, String branch, String commitSha) {}
