package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Optional PMD CLI adapter for supported languages (notably Java).
 * PMD is BSD-style licensed. Rule violations become normal code-quality findings.
 */
@Component
public class PmdAnalyzer {
    @Value("${dataguard.pmd.enabled:false}")
    private boolean enabled = false;

    @Value("${dataguard.pmd.executable:pmd}")
    private String executable = "pmd";

    @Value("${dataguard.pmd.ruleset:category/java/quickstart.xml}")
    private String ruleset = "category/java/quickstart.xml";

    @Value("${dataguard.pmd.timeout-seconds:90}")
    private long timeoutSeconds = 90;

    private final ObjectMapper objectMapper;

    public PmdAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Finding> analyze(File projectDir, Review review) {
        if (!enabled) return List.of();
        if (projectDir == null || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("PMD requires an existing project directory.");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("PMD timeout must be greater than zero.");
        }
        if (!containsJavaSource(projectDir.toPath())) return List.of();

        Path report = null;
        Process process = null;
        try {
            report = Files.createTempFile("dataguard-pmd-", ".json");
            process = new ProcessBuilder(executable, "check",
                    "--dir", projectDir.getAbsolutePath(),
                    "--rulesets", ruleset,
                    "--format", "json",
                    "--report-file", report.toString(),
                    "--no-fail-on-violation",
                    "--no-progress")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("PMD exceeded its configured scan timeout.");
            }
            if (!Files.isRegularFile(report) || Files.size(report) == 0) {
                throw new IllegalStateException("PMD failed to produce a report. Check the configured PMD CLI and ruleset (exit code "
                        + process.exitValue() + ").");
            }
            // PMD can return a non-zero exit code when source parsing fails while still
            // producing useful findings for the rest of the repository.
            return parseResults(Files.readString(report, StandardCharsets.UTF_8), projectDir, review);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PMD analysis was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not run PMD. Install the CLI or set DATAGUARD_PMD_ENABLED=false.", exception);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (report != null) {
                try {
                    Files.deleteIfExists(report);
                } catch (IOException ignored) {
                    // Preserve the original result.
                }
            }
        }
    }

    List<Finding> parseResults(String json, File projectDir, Review review) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode files = root == null ? null : root.get("files");
        if (files == null || !files.isArray()) {
            throw new IllegalStateException("PMD did not return a valid JSON files array.");
        }
        Path rootPath = projectDir.toPath().toAbsolutePath().normalize();
        List<Finding> findings = new ArrayList<>();
        for (JsonNode fileNode : files) {
            String path = fileNode.path("filename").asText("");
            String relativePath = path;
            try {
                Path candidate = Path.of(path).toAbsolutePath().normalize();
                if (candidate.startsWith(rootPath)) {
                    relativePath = rootPath.relativize(candidate).toString().replace(File.separatorChar, '/');
                }
            } catch (RuntimeException ignored) {
                // Keep the reported path when it cannot be normalized.
            }
            JsonNode violations = fileNode.path("violations");
            if (!violations.isArray()) continue;
            for (JsonNode violation : violations) {
                String rule = violation.path("rule").asText("PMD rule");
                String description = violation.path("description").asText("PMD reported a code-quality issue.");
                int line = Math.max(1, violation.path("beginline").asInt(1));
                int priority = violation.path("priority").asInt(3);
                String severity = switch (priority) {
                    case 1 -> "HIGH";
                    case 2 -> "HIGH";
                    case 3 -> "MEDIUM";
                    default -> "LOW";
                };
                Finding finding = new Finding();
                finding.setReview(review);
                finding.setCategory("CODE_QUALITY");
                finding.setSeverity(severity);
                String title = "PMD: " + rule + " - " + description;
                finding.setTitle(title.substring(0, Math.min(255, title.length())));
                finding.setFilePath(relativePath);
                finding.setLineNumber(line);
                finding.setEvidence(description.substring(0, Math.min(1800, description.length())));
                finding.setRecommendation("Review the PMD rule " + rule + " and apply a correction appropriate to this project's design and coding standards.");
                finding.setStatus("OPEN");
                findings.add(finding);
            }
        }
        return findings;
    }

    private boolean containsJavaSource(Path root) {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .anyMatch(path -> {
                        String normalized = path.toString().replace(File.separatorChar, '/');
                        return !normalized.contains("/target/") && !normalized.contains("/node_modules/")
                                && !normalized.contains("/.git/");
                    });
        } catch (IOException ignored) {
            return false;
        }
    }
}
