package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CodeQualityAnalyzer {
    private static final Pattern PRINTLN = Pattern.compile("^\\s*System\\.out\\.println\\s*\\((.*)\\)\\s*;\\s*$");
    private static final Pattern EMPTY_CATCH = Pattern.compile("^\\s*catch\\s*\\([^)]*\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\)\\s*\\{\\s*}\\s*$");
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".next", "vendor", "__pycache__");

    public List<Finding> analyze(File projectDir, Review review) {
        List<Finding> findings = new ArrayList<>();
        analyzeDirectory(projectDir, projectDir, review, findings);
        return findings;
    }

    private void analyzeDirectory(File root, File dir, Review review, List<Finding> findings) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                if (!IGNORED_DIRS.contains(file.getName())) analyzeDirectory(root, file, review, findings);
            } else if (file.getName().endsWith(".java")) {
                analyzeFile(root, file, review, findings);
            }
        }
    }

    private void analyzeFile(File root, File file, Review review, List<Finding> findings) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            String relative = root.toPath().toAbsolutePath().normalize()
                    .relativize(file.toPath().toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/');
            for (int i = 0; i < lines.size(); i++) {
                String sourceLine = lines.get(i);
                Matcher println = PRINTLN.matcher(sourceLine);
                if (println.matches()) {
                    findings.add(createFinding(review, "CODE_QUALITY", "LOW", "System.out.println used",
                            relative, i + 1, sourceLine.trim(),
                            "Replace standard output with structured logging, or remove the diagnostic print if it is no longer needed."));
                }
                if (EMPTY_CATCH.matcher(sourceLine).matches()) {
                    findings.add(createFinding(review, "BUG", "HIGH", "Empty catch block",
                            relative, i + 1, sourceLine.trim(),
                            "Handle the exception explicitly. Log it or throw a meaningful exception rather than silently swallowing the failure."));
                }
            }
        } catch (IOException ignored) {
            // Skip unreadable files without failing the entire project review.
        }
    }

    private Finding createFinding(Review review, String category, String severity, String title,
                                  String filePath, int line, String evidence, String recommendation) {
        Finding finding = new Finding();
        finding.setReview(review);
        finding.setCategory(category);
        finding.setSeverity(severity);
        finding.setTitle(title);
        finding.setFilePath(filePath);
        finding.setLineNumber(line);
        finding.setEvidence(evidence);
        finding.setRecommendation(recommendation);
        finding.setStatus("OPEN");
        return finding;
    }
}
