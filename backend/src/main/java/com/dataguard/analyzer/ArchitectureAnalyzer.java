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

/**
 * Lightweight structural checks. These are architecture signals, not a claim
 * that the complete system architecture has been formally verified.
 */
@Component
public class ArchitectureAnalyzer {
    private static final int LARGE_SOURCE_FILE_LINES = 500;
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".next", "vendor",
            "__pycache__", ".venv", "venv", "coverage");

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
            } else if (isSource(entry.getName())) {
                inspect(root, entry, review, findings);
            }
        }
    }

    private boolean isSource(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java") || lower.endsWith(".js") || lower.endsWith(".jsx")
                || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".py")
                || lower.endsWith(".go") || lower.endsWith(".cs") || lower.endsWith(".php");
    }

    private void inspect(File root, File file, Review review, List<Finding> findings) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            String relative = root.toPath().toAbsolutePath().normalize()
                    .relativize(file.toPath().toAbsolutePath().normalize()).toString()
                    .replace(File.separatorChar, '/');
            if (lines.size() > LARGE_SOURCE_FILE_LINES) {
                add(review, findings, "LOW", "Large source file", relative, 1,
                        "This source file contains " + lines.size() + " lines; the configured review threshold is " + LARGE_SOURCE_FILE_LINES + ".",
                        "Consider separating distinct responsibilities into smaller modules or classes. Review cohesion and dependencies before refactoring.");
            }

            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".java")) {
                boolean controller = lines.stream().anyMatch(line ->
                        line.contains("@RestController") || line.contains("@Controller"));
                if (controller) {
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.startsWith("import ") && line.contains(".repository.")) {
                            add(review, findings, "MEDIUM", "Controller depends directly on repository",
                                    relative, i + 1, line,
                                    "Consider moving data-access orchestration into a service layer so controllers handle HTTP concerns and services own business workflows.");
                            break;
                        }
                    }
                }
                boolean service = lines.stream().anyMatch(line -> line.contains("@Service"));
                if (service) {
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.startsWith("import ") && line.contains(".controller.")) {
                            add(review, findings, "MEDIUM", "Service depends on controller layer",
                                    relative, i + 1, line,
                                    "Keep dependencies flowing toward the service layer. Move shared behavior into a lower-level component instead of importing a controller.");
                            break;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // An unreadable source file is skipped without failing the whole review.
        }
    }

    private void add(Review review, List<Finding> findings, String severity, String title,
                     String path, int line, String evidence, String recommendation) {
        Finding finding = new Finding();
        finding.setReview(review);
        finding.setCategory("ARCHITECTURE");
        finding.setSeverity(severity);
        finding.setTitle(title);
        finding.setFilePath(path);
        finding.setLineNumber(line);
        finding.setEvidence(evidence.length() > 1800 ? evidence.substring(0, 1800) : evidence);
        finding.setRecommendation(recommendation);
        finding.setStatus("OPEN");
        findings.add(finding);
    }
}
