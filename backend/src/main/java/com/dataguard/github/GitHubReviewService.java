package com.dataguard.github;

import com.dataguard.dto.ReviewResponse;
import com.dataguard.entity.Finding;
import com.dataguard.service.ReviewEngine;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class GitHubReviewService {
    private static final Logger log = LoggerFactory.getLogger(GitHubReviewService.class);
    private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 10000;
    private static final int MAX_ANNOTATIONS = 50;

    private final GitHubAppClient github;
    private final ReviewEngine reviewEngine;

    public GitHubReviewService(GitHubAppClient github, ReviewEngine reviewEngine) {
        this.github = github;
        this.reviewEngine = reviewEngine;
    }

    @Async("githubReviewExecutor")
    public void processEvent(String event, JsonNode payload) {
        if (!github.isConfigured()) {
            log.error("GitHub App review skipped because App ID/private key are not configured.");
            return;
        }

        String fullName = payload.path("repository").path("full_name").asText("");
        String[] repoParts = fullName.split("/", 2);
        long installationId = payload.path("installation").path("id").asLong(0);
        String sha = revisionFor(event, payload);
        if (repoParts.length != 2 || repoParts[0].isBlank() || repoParts[1].isBlank()
                || installationId <= 0 || !sha.matches("[0-9a-fA-F]{40}")) {
            log.warn("Ignoring GitHub {} event with incomplete repository, installation, or commit metadata.", event);
            return;
        }
        String owner = repoParts[0];
        String repository = repoParts[1];
        String token = null;
        Long checkRunId = null;
        Path workspace = null;
        try {
            token = github.createInstallationToken(installationId);
            checkRunId = github.createCheckRun(token, owner, repository, sha);
            workspace = Files.createTempDirectory("dataguard-github-review-");
            byte[] archive = github.downloadRepositoryArchive(owner, repository, sha);
            extractArchive(archive, workspace);
            Path sourceRoot = findSourceRoot(workspace);
            ReviewEngine.AnalysisResult analysis = reviewEngine.analyzeDirectory(sourceRoot.toFile());
            List<Finding> findings = analysis.findings();
            ReviewResponse.ReviewMetrics metrics = ReviewResponse.ReviewMetrics.fromFindings(findings);
            String summary = buildSummary(metrics, findings.size());
            List<Map<String, Object>> annotations = buildAnnotations(findings);
            String conclusion = conclusion(findings);
            github.completeCheckRun(token, owner, repository, checkRunId, conclusion,
                    findings.isEmpty() ? "No issues found" : "Code review completed", summary, annotations);
            log.info("Completed DataGuard Check Run for {}/{} at {} with {} unique findings.",
                    owner, repository, sha, findings.size());
        } catch (Exception exception) {
            log.error("GitHub review failed for {}/{} at {} ({})", owner, repository, sha,
                    exception.getClass().getSimpleName());
            if (token != null && checkRunId != null) {
                try {
                    github.failCheckRun(token, owner, repository, checkRunId,
                            "The review pipeline could not complete. Check backend logs for the failure category.");
                } catch (Exception updateException) {
                    log.error("Could not mark GitHub Check Run {} as failed ({})", checkRunId,
                            updateException.getClass().getSimpleName());
                }
            }
        } finally {
            deleteQuietly(workspace);
        }
    }

    private String revisionFor(String event, JsonNode payload) {
        if ("push".equals(event)) return payload.path("after").asText("");
        if ("pull_request".equals(event)) return payload.path("pull_request").path("head").path("sha").asText("");
        return "";
    }

    private String buildSummary(ReviewResponse.ReviewMetrics metrics, int totalFindings) {
        return """
                ## DataGuard AI review

                | Metric | Count |
                |---|---:|
                | Syntax errors | %d |
                | Potential bugs | %d |
                | Code quality issues | %d |
                | Security findings | %d |
                | Architecture issues | %d |
                | Open issues | %d |

                **Unique findings:** %d

                Category counts may overlap when one issue fits more than one category. No percentage breakdown is used. Syntax parsing detects grammar errors for supported languages; it does not prove successful compilation, type correctness, or runtime behavior.
                """.formatted(metrics.syntaxErrors(), metrics.potentialBugs(), metrics.codeQualityIssues(),
                metrics.securityFindings(), metrics.architectureIssues(), metrics.openIssues(), totalFindings);
    }

    private String conclusion(List<Finding> findings) {
        if (findings.isEmpty()) return "success";
        boolean highRisk = findings.stream().anyMatch(f ->
                "CRITICAL".equalsIgnoreCase(f.getSeverity()) || "HIGH".equalsIgnoreCase(f.getSeverity()));
        return highRisk ? "failure" : "neutral";
    }

    private List<Map<String, Object>> buildAnnotations(List<Finding> findings) {
        List<Map<String, Object>> annotations = new ArrayList<>();
        for (Finding finding : findings) {
            if (annotations.size() >= MAX_ANNOTATIONS) break;
            String path = safeRelativePath(finding.getFilePath());
            if (path == null) continue;
            int line = finding.getLineNumber() == null ? 1 : Math.max(1, finding.getLineNumber());
            String severity = finding.getSeverity() == null ? "LOW" : finding.getSeverity().toUpperCase(Locale.ROOT);
            String level = ("CRITICAL".equals(severity) || "HIGH".equals(severity))
                    ? "failure" : ("MEDIUM".equals(severity) ? "warning" : "notice");
            String message = safe(finding.getEvidence()) + "\n\nRecommendation: " + safe(finding.getRecommendation());
            annotations.add(Map.of(
                    "path", path,
                    "start_line", line,
                    "end_line", line,
                    "annotation_level", level,
                    "title", truncate(safe(finding.getTitle()), 255),
                    "message", truncate(message, 6000)));
        }
        return annotations;
    }

    private String safeRelativePath(String value) {
        if (value == null || value.isBlank()) return null;
        String path = value.replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) return null;
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) return null;
        }
        return path;
    }

    private void extractArchive(byte[] archive, Path destination) throws IOException {
        long expanded = 0;
        int entries = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Repository archive contains too many entries.");
                }
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("Repository archive contains a path outside its workspace.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (var output = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            expanded += read;
                            if (expanded > MAX_UNCOMPRESSED_BYTES) {
                                throw new IOException("Repository archive expands beyond the 100 MB limit.");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                input.closeEntry();
            }
        }
        if (entries == 0) throw new IOException("GitHub returned an empty repository archive.");
    }

    private Path findSourceRoot(Path workspace) throws IOException {
        try (var entries = Files.list(workspace)) {
            List<Path> roots = entries.toList();
            if (roots.size() == 1 && Files.isDirectory(roots.get(0))) return roots.get(0);
        }
        return workspace;
    }

    private void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Preserve the original review result.
                }
            });
        } catch (IOException ignored) {
            // Temporary workspace cleanup must not hide the review result.
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "No additional evidence supplied." : value;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
