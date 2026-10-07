package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic code-quality analyzer.
 *
 * <p>Walks the extracted project tree and applies rule-based checks to source files.
 * Supports Java, Python, and JavaScript/TypeScript.
 *
 * <p>Rules:
 * <ol>
 *   <li>Java: System.out.println / System.err.println (debug output)</li>
 *   <li>Java: Empty catch block  {@code catch (...) {}}</li>
 *   <li>All: Hardcoded password/secret/token literals</li>
 *   <li>Python: print() debug statement</li>
 *   <li>All: TODO / FIXME / HACK comments</li>
 *   <li>All: e.printStackTrace() (swallowed stack trace)</li>
 * </ol>
 */
@Component
public class CodeQualityAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CodeQualityAnalyzer.class);

    // Detects: password = "...", secret = "...", token = "...", api_key = "..." etc.
    private static final Pattern HARDCODED_SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[_-]?key|auth[_-]?key)\\s*[=:]\\s*[\"'][^\"']{3,}[\"']"
    );

    private static final Pattern EMPTY_CATCH = Pattern.compile(
            "catch\\s*\\([^)]*\\)\\s*\\{"
    );

    public List<Finding> analyze(File projectDir, Review review) {
        List<Finding> findings = new ArrayList<>();
        scanDirectory(projectDir, projectDir, review, findings);
        log.info("Code quality analyzer produced {} findings.", findings.size());
        return findings;
    }

    private void scanDirectory(File dir, File rootDir, Review review, List<Finding> findings) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName();
                if ("node_modules".equals(name) || "target".equals(name)
                        || ".git".equals(name) || "build".equals(name)
                        || "__pycache__".equals(name) || ".venv".equals(name)) {
                    continue;
                }
                scanDirectory(file, rootDir, review, findings);
            } else {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".java") || fileName.endsWith(".py")
                        || fileName.endsWith(".js") || fileName.endsWith(".ts")
                        || fileName.endsWith(".tsx") || fileName.endsWith(".jsx")) {
                    analyzeFile(file, rootDir, review, findings);
                }
            }
        }
    }

    private void analyzeFile(File file, File rootDir, Review review, List<Finding> findings) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            log.debug("Could not read file {}: {}", file.getName(), e.getMessage());
            return;
        }

        String relativePath = makeRelative(file, rootDir);
        boolean isJava = file.getName().endsWith(".java");
        boolean isPython = file.getName().endsWith(".py");

        boolean prevLineWasCatch = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            int lineNum = i + 1;

            // Rule 1: Java System.out / System.err debug output
            if (isJava && (trimmed.contains("System.out.println") || trimmed.contains("System.err.println"))) {
                findings.add(createFinding(review, "cq-debug-output",
                        "Debug output statement found",
                        "CODE_QUALITY", "LOW",
                        relativePath, lineNum, trimmed,
                        "Remove debug output statements and use a structured logging framework " +
                        "(SLF4J/Logback) instead."));
            }

            // Rule 2: Java empty catch block  — detect pattern across lines
            if (isJava && EMPTY_CATCH.matcher(trimmed).find()) {
                prevLineWasCatch = true;
            } else if (prevLineWasCatch) {
                if (trimmed.equals("}")) {
                    // Previous line was catch open, this line is just } — empty catch
                    findings.add(createFinding(review, "cq-empty-catch",
                            "Empty catch block",
                            "CODE_QUALITY", "HIGH",
                            relativePath, lineNum, lines.get(i - 1).trim(),
                            "Never swallow exceptions silently. At minimum, log the error. " +
                            "Prefer specific exception types over catching Exception."));
                }
                prevLineWasCatch = false;
            } else if (isJava && trimmed.contains("catch") && trimmed.contains("{") && trimmed.contains("}")) {
                // Single-line empty catch: catch (Exception e) {}
                findings.add(createFinding(review, "cq-empty-catch",
                        "Empty catch block",
                        "CODE_QUALITY", "HIGH",
                        relativePath, lineNum, trimmed,
                        "Never swallow exceptions silently. At minimum, log the error."));
            }

            // Rule 3: Hardcoded secrets / passwords
            if (!trimmed.startsWith("//") && !trimmed.startsWith("#") && !trimmed.startsWith("*")) {
                if (HARDCODED_SECRET.matcher(trimmed).find()) {
                    findings.add(createFinding(review, "cq-hardcoded-secret",
                            "Hardcoded credential or secret",
                            "SECURITY", "HIGH",
                            relativePath, lineNum, maskSecret(trimmed),
                            "Never hardcode passwords, secrets, or API keys. Use environment variables, " +
                            "a secrets manager, or a configuration vault instead."));
                }
            }

            // Rule 4: Python print() debug output
            if (isPython && trimmed.startsWith("print(") && !trimmed.startsWith("#")) {
                findings.add(createFinding(review, "cq-python-print",
                        "Python print() debug statement",
                        "CODE_QUALITY", "LOW",
                        relativePath, lineNum, trimmed,
                        "Use the Python logging module instead of print() for application output."));
            }

            // Rule 5: TODO / FIXME / HACK comments
            String upper = trimmed.toUpperCase();
            if ((upper.contains("TODO") || upper.contains("FIXME") || upper.contains("HACK"))
                    && (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("*"))) {
                findings.add(createFinding(review, "cq-todo-comment",
                        "Unresolved TODO/FIXME/HACK comment",
                        "CODE_QUALITY", "LOW",
                        relativePath, lineNum, trimmed,
                        "Address or track this comment in your issue tracker before shipping to production."));
            }

            // Rule 6: printStackTrace (swallowed exception detail)
            if (trimmed.contains(".printStackTrace()")) {
                findings.add(createFinding(review, "cq-print-stack-trace",
                        "Exception stack trace printed to stderr",
                        "CODE_QUALITY", "MEDIUM",
                        relativePath, lineNum, trimmed,
                        "Use a structured logger (log.error(\"msg\", exception)) instead of " +
                        "printStackTrace() so stack traces are captured by your logging infrastructure."));
            }
        }
    }

    private String makeRelative(File file, File rootDir) {
        try {
            String root = rootDir.getCanonicalPath().replace('\\', '/');
            String filePath = file.getCanonicalPath().replace('\\', '/');
            if (filePath.startsWith(root + "/")) {
                return filePath.substring(root.length() + 1);
            }
        } catch (IOException ignored) { /* fall through */ }
        return file.getName();
    }

    /** Masks the actual secret value to avoid storing it in the database. */
    private String maskSecret(String line) {
        return line.replaceAll("([\"'])[^\"']{3,}([\"'])", "$1***$2");
    }

    private Finding createFinding(Review review, String ruleId, String title, String category,
                                   String severity, String filePath, int lineNumber,
                                   String evidence, String recommendation) {
        Finding finding = new Finding();
        finding.setReview(review);
        finding.setSource("CODE_QUALITY");
        finding.setRuleId(ruleId);
        finding.setCategory(category);
        finding.setSeverity(severity);
        finding.setTitle(title);
        finding.setDescription(title);
        finding.setFilePath(filePath);
        finding.setLineNumber(lineNumber);
        // Truncate long evidence lines
        finding.setEvidence(evidence != null && evidence.length() > 500
                ? evidence.substring(0, 500) + "…"
                : evidence);
        finding.setRecommendation(recommendation);
        finding.setStatus("OPEN");
        return finding;
    }
}

