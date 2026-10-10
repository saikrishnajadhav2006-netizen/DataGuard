package com.dataguard.dto;

public record FixedProjectResponse(boolean ready, String downloadUrl, String filename,
                                   int modifiedFiles, int unresolvedFindings, int omittedSensitiveFiles,
                                   String message) { }
