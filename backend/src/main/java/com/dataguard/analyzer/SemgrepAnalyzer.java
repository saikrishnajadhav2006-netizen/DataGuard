package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs Semgrep CLI against the extracted project directory and converts the
 * JSON results into unified DataGuard Finding objects.
 *
 * <p>Configuration (application.properties / environment variables):
 * <ul>
 *   <li>{@code dataguard.semgrep.command}         — semgrep executable (default: semgrep)</li>
 *   <li>{@code dataguard.semgrep.config}           — path to semgrep.yml (default: semgrep.yml)</li>
 *   <li>{@code dataguard.semgrep.timeout-seconds}  — per-run timeout (default: 120)</li>
 *   <li>{@code dataguard.semgrep.enabled}          — disable to skip semgrep entirely (default: true)</li>
 * </ul>
 *
 * <p>Semgrep is treated as an optional, replaceable tool. If it is not installed,
 * or exits with an error, the analyzer logs the problem and returns an empty list
 * so that the rest of the pipeline can still complete.
 */
@Component
public class SemgrepAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SemgrepAnalyzer.class);

    @Value("${dataguard.semgrep.command:semgrep}")
    private String semgrepCommand;

    @Value("${dataguard.semgrep.config:semgrep.yml}")
    private String semgrepConfig;

    @Value("${dataguard.semgrep.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${dataguard.semgrep.enabled:true}")
    private boolean enabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Runs Semgrep and returns a (possibly empty) list of findings.
     * Never throws — all errors are logged and an empty list is returned.
     */
    public List<Finding> analyze(File projectDir, Review review) {
        if (!enabled) {
            log.info("Semgrep analyzer is disabled — skipping.");
            return List.of();
        }

        File outputFile = null;
        try {
            outputFile = Files.createTempFile("semgrep-output-", ".json").toFile();
            return runSemgrep(projectDir, review, outputFile);
        } catch (IOException e) {
            log.warn("Could not create Semgrep output temp file: {}", e.getMessage());
            return List.of();
        } finally {
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
        }
    }

    private List<Finding> runSemgrep(File projectDir, Review review, File outputFile) {
        // Resolve semgrep.yml relative to the working directory, then fall back to absolute
        String resolvedConfig = resolveConfig();

        ProcessBuilder pb = new ProcessBuilder(
                semgrepCommand,
                "scan",
                "--config", resolvedConfig,
                "--json",
                "--output", outputFile.getAbsolutePath(),
                "--no-rewrite-rule-ids",
                "--quiet",
                projectDir.getAbsolutePath()
        );
        pb.redirectErrorStream(false);

        log.info("Running Semgrep: {} scan --config {} --json on {}", semgrepCommand, resolvedConfig, projectDir);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            log.warn("Semgrep not found or could not be started ({}). Skipping Semgrep analysis. " +
                     "Install semgrep with: pip install semgrep", e.getMessage());
            return List.of();
        }

        // Drain stderr so the process doesn't block
        String stderrOutput = drainStream(process);

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            log.warn("Semgrep execution interrupted.");
            return List.of();
        }

        if (!finished) {
            process.destroyForcibly();
            log.warn("Semgrep timed out after {} seconds. Skipping Semgrep findings.", timeoutSeconds);
            return List.of();
        }

        int exitCode = process.exitValue();
        // Semgrep exit codes: 0 = no findings, 1 = findings found, 2 = error
        if (exitCode == 2) {
            log.warn("Semgrep exited with error code 2. Stderr: {}", stderrOutput);
            return List.of();
        }

        if (!outputFile.exists() || outputFile.length() == 0) {
            log.info("Semgrep produced no output file or empty output. Exit code: {}", exitCode);
            return List.of();
        }

        return parseSemgrepOutput(outputFile, review, projectDir);
    }

    private String drainStream(Process process) {
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getErrorStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private List<Finding> parseSemgrepOutput(File outputFile, Review review, File projectDir) {
        List<Finding> findings = new ArrayList<>();

        JsonNode root;
        try {
            root = objectMapper.readTree(outputFile);
        } catch (IOException e) {
            log.warn("Failed to parse Semgrep JSON output: {}", e.getMessage());
            return findings;
        }

        JsonNode results = root.path("results");
        if (!results.isArray()) {
            log.info("Semgrep JSON 'results' array not found or empty.");
            return findings;
        }

        String projectBase = projectDir.getAbsolutePath();

        for (JsonNode result : results) {
            try {
                findings.add(convertToFinding(result, review, projectBase));
            } catch (Exception e) {
                log.debug("Could not convert Semgrep result to finding: {}", e.getMessage());
            }
        }

        log.info("Semgrep produced {} findings.", findings.size());
        return findings;
    }

    /**
     * Converts a single Semgrep JSON result node to a DataGuard Finding.
     * File paths are made relative to the project root so no server-side
     * absolute paths are exposed to the frontend.
     */
    private Finding convertToFinding(JsonNode result, Review review, String projectBase) {
        String ruleId = result.path("check_id").asText("unknown-rule");
        String message = result.path("extra").path("message").asText("");
        String semgrepSeverity = result.path("extra").path("severity").asText("WARNING");

        String absoluteFilePath = result.path("path").asText("");
        String relativeFilePath = makeRelative(absoluteFilePath, projectBase);

        int lineNumber = result.path("start").path("line").asInt(0);
        String evidence = extractEvidence(result);

        String severity = mapSeverity(semgrepSeverity);
        String category = inferCategory(ruleId, message);
        String title = buildTitle(ruleId, message);

        Finding finding = new Finding();
        finding.setReview(review);
        finding.setSource("SEMGREP");
        finding.setRuleId(ruleId);
        finding.setCategory(category);
        finding.setSeverity(severity);
        finding.setTitle(title);
        finding.setDescription(message);
        finding.setFilePath(relativeFilePath);
        finding.setLineNumber(lineNumber > 0 ? lineNumber : null);
        finding.setEvidence(evidence);
        finding.setRecommendation(buildRecommendation(ruleId, message));
        finding.setStatus("OPEN");
        return finding;
    }

    private String makeRelative(String absolutePath, String projectBase) {
        if (absolutePath == null || absolutePath.isBlank()) return "";
        // Normalise separators for comparison
        String normalAbsolute = absolutePath.replace('\\', '/');
        String normalBase = projectBase.replace('\\', '/');
        if (!normalBase.endsWith("/")) normalBase += "/";
        if (normalAbsolute.startsWith(normalBase)) {
            return normalAbsolute.substring(normalBase.length());
        }
        // Return just the filename as a safe fallback
        return new File(absolutePath).getName();
    }

    private String extractEvidence(JsonNode result) {
        JsonNode lines = result.path("extra").path("lines");
        if (!lines.isMissingNode() && !lines.isNull()) {
            return lines.asText("").trim();
        }
        return "";
    }

    /**
     * Maps Semgrep severity strings to DataGuard severity levels.
     *
     * <p>Semgrep uses: ERROR, WARNING, INFO
     * DataGuard uses: CRITICAL, HIGH, MEDIUM, LOW, INFO
     */
    private String mapSeverity(String semgrepSeverity) {
        return switch (semgrepSeverity.toUpperCase()) {
            case "ERROR" -> "HIGH";
            case "WARNING" -> "MEDIUM";
            case "INFO", "INVENTORY" -> "LOW";
            default -> "LOW";
        };
    }

    /**
     * Infers the finding category from the rule ID and message.
     * Heuristic-based — good enough for MVP.
     */
    private String inferCategory(String ruleId, String message) {
        String combined = (ruleId + " " + message).toLowerCase();
        if (combined.contains("sql") || combined.contains("inject") || combined.contains("xss")
                || combined.contains("password") || combined.contains("hardcoded")
                || combined.contains("secret") || combined.contains("token")
                || combined.contains("crypto") || combined.contains("auth")) {
            return "SECURITY";
        }
        if (combined.contains("architecture") || combined.contains("layer")
                || combined.contains("controller") || combined.contains("service")) {
            return "ARCHITECTURE";
        }
        return "CODE_QUALITY";
    }

    private String buildTitle(String ruleId, String message) {
        // Use the last segment of the rule ID as a human-friendly title
        String lastSegment = ruleId.contains(".") ? ruleId.substring(ruleId.lastIndexOf('.') + 1) : ruleId;
        String formatted = lastSegment.replace('-', ' ');
        // Capitalise first letter
        if (!formatted.isEmpty()) {
            formatted = Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
        }
        return formatted.isBlank() ? message : formatted;
    }

    private String buildRecommendation(String ruleId, String message) {
        // Use Semgrep's message as the recommendation since it is already human-readable
        return message.isBlank() ? "Review the flagged code and follow secure coding guidelines." : message;
    }

    private String resolveConfig() {
        File configFile = new File(semgrepConfig);
        if (configFile.isAbsolute()) {
            return semgrepConfig;
        }
        // Look for the config relative to the working directory
        File cwd = new File(System.getProperty("user.dir"));
        File resolved = new File(cwd, semgrepConfig);
        if (resolved.exists()) {
            return resolved.getAbsolutePath();
        }
        // Fall back to the configured value (semgrep may handle it)
        return semgrepConfig;
    }
}
