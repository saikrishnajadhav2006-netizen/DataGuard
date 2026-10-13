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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative bug checks for exception handlers that silently discard failures. */
@Component
public class PotentialBugAnalyzer {
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".next", "vendor",
            "__pycache__", ".venv", "venv", "coverage");
    private static final Pattern PYTHON_EMPTY_HANDLER =
            Pattern.compile("^\\s*except(?:\\s+[^:]+)?\\s*:\\s*(?:pass\\s*)?$");
    private static final Pattern JS_EMPTY_HANDLER =
            Pattern.compile("catch\\s*(?:\\([^)]*\\))?\\s*\\{\\s*}");

    public List<Finding> analyze(File projectDir, Review review) {
        List<Finding> findings = new ArrayList<>();
        scan(projectDir, projectDir, review, findings);
        return findings;
    }

    private void scan(File root, File current, Review review, List<Finding> findings) {
        File[] entries = current.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                if (!IGNORED_DIRS.contains(entry.getName())) scan(root, entry, review, findings);
            } else {
                inspect(root, entry, review, findings);
            }
        }
    }

    private void inspect(File root, File file, Review review, List<Finding> findings) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        boolean python = name.endsWith(".py");
        boolean javascript = name.endsWith(".js") || name.endsWith(".jsx")
                || name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".mjs");
        boolean csharp = name.endsWith(".cs");
        if (!python && !javascript && !csharp) return;

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            String relative = root.toPath().toAbsolutePath().normalize()
                    .relativize(file.toPath().toAbsolutePath().normalize()).toString()
                    .replace(File.separatorChar, '/');
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean emptyPython = python && PYTHON_EMPTY_HANDLER.matcher(line).matches()
                        && i + 1 < lines.size() && lines.get(i + 1).trim().equals("pass");
                boolean emptyJs = javascript && JS_EMPTY_HANDLER.matcher(line).find();
                boolean emptyCsharp = csharp && line.matches(".*catch\\s*(?:\\([^)]*\\))?\\s*\\{\\s*}.*");
                if (emptyPython || emptyJs || emptyCsharp) {
                    add(review, findings, relative, i + 1, line.trim());
                }
            }
        } catch (IOException ignored) {
            // Do not fail an entire review because one source file cannot be read.
        }
    }

    private void add(Review review, List<Finding> findings, String path, int line, String evidence) {
        Finding finding = new Finding();
        finding.setReview(review);
        finding.setCategory("BUG");
        finding.setSeverity("HIGH");
        finding.setTitle("Exception silently swallowed (potential bug)");
        finding.setFilePath(path);
        finding.setLineNumber(line);
        finding.setEvidence(evidence.substring(0, Math.min(1800, evidence.length())));
        finding.setRecommendation("Handle the exception explicitly, record useful diagnostic context, or rethrow it. Silently ignoring failures can leave the application in an inconsistent state.");
        finding.setStatus("OPEN");
        findings.add(finding);
    }
}
