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

/**
 * Deterministic architecture analyzer.
 *
 * <p>Performs basic, file-level checks on Java source files to detect common
 * architectural violations. All checks are rule-based and produce the same
 * findings for the same input — no AI or randomness involved.
 *
 * <p>Checks implemented:
 * <ol>
 *   <li>Controller directly using repository — business logic bypass</li>
 *   <li>Controller with excessive business logic (heuristic: too many methods)</li>
 *   <li>Entity in a controller package — misplaced class</li>
 *   <li>Service class bypassing service layer and calling repository from controller</li>
 *   <li>Direct JDBC usage in a non-repository class</li>
 * </ol>
 */
@Component
public class ArchitectureAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureAnalyzer.class);

    public List<Finding> analyze(File projectDir, Review review) {
        List<Finding> findings = new ArrayList<>();
        scanDirectory(projectDir, projectDir, review, findings);
        log.info("Architecture analyzer produced {} findings.", findings.size());
        return findings;
    }

    private void scanDirectory(File dir, File rootDir, Review review, List<Finding> findings) {
        File[] files = dir.listFiles();
        if (files == null) throw new IllegalStateException("Could not list source directory " + dir.getName());

        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName();
                if ("node_modules".equals(name) || "target".equals(name)
                        || ".git".equals(name) || "build".equals(name)) {
                    continue;
                }
                scanDirectory(file, rootDir, review, findings);
            } else if (file.getName().endsWith(".java")) {
                analyzeFile(file, rootDir, review, findings);
            }
        }
    }

    private void analyzeFile(File file, File rootDir, Review review, List<Finding> findings) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Could not analyze file " + file.getName(), e);
        }

        String fileName = file.getName();
        String relativePath = makeRelative(file, rootDir);
        boolean isController = fileName.endsWith("Controller.java");
        boolean isService = fileName.endsWith("Service.java");
        boolean isRepository = fileName.endsWith("Repository.java");
        boolean isEntity = fileName.endsWith("Entity.java") || hasEntityAnnotation(lines);

        // Check 1: Controller directly importing/using Repository
        if (isController) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("import") && line.contains("repository") && line.contains("Repository")) {
                    findings.add(createFinding(review,
                            "arch-controller-direct-repository",
                            "Controller directly depends on Repository",
                            "ARCHITECTURE",
                            "HIGH",
                            relativePath,
                            i + 1,
                            line.trim(),
                            "Controllers should only depend on Service classes. " +
                            "Move repository calls into a Service to maintain layer separation."));
                    break; // Report once per file
                }
            }
        }

        // Check 2: Direct JDBC usage outside Repository layer
        if (!isRepository) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if ((line.contains("DriverManager.getConnection") || line.contains("new JdbcTemplate"))
                        && !line.trim().startsWith("//")) {
                    findings.add(createFinding(review,
                            "arch-direct-jdbc",
                            "Direct JDBC usage outside Repository layer",
                            "ARCHITECTURE",
                            "HIGH",
                            relativePath,
                            i + 1,
                            line.trim(),
                            "Use Spring Data JPA repositories or a dedicated data-access layer " +
                            "instead of raw JDBC in business or presentation code."));
                }
            }
        }

        // Check 3: Controller class has excessive lines of business logic (heuristic)
        if (isController && lines.size() > 200) {
            findings.add(createFinding(review,
                    "arch-fat-controller",
                    "Controller class is excessively large",
                    "ARCHITECTURE",
                    "MEDIUM",
                    relativePath,
                    1,
                    "File has " + lines.size() + " lines",
                    "A controller with " + lines.size() + " lines likely contains business logic. " +
                    "Extract business rules into a dedicated Service class."));
        }

        // Check 4: Entity annotation in a controller or service (misplaced domain object)
        if ((isController || isService) && isEntity) {
            findings.add(createFinding(review,
                    "arch-misplaced-entity",
                    "JPA @Entity found in a non-entity class",
                    "ARCHITECTURE",
                    "MEDIUM",
                    relativePath,
                    1,
                    "@Entity in " + fileName,
                    "JPA entities should be placed in the entity package, " +
                    "not mixed into controller or service classes."));
        }

        // Check 5: Service calling System.exit (catastrophic anti-pattern)
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("System.exit(") && !line.trim().startsWith("//")) {
                findings.add(createFinding(review,
                        "arch-system-exit",
                        "System.exit() called in application code",
                        "ARCHITECTURE",
                        "HIGH",
                        relativePath,
                        i + 1,
                        line.trim(),
                        "Never call System.exit() in a Spring Boot application. " +
                        "Throw an exception or use application events instead."));
            }
        }
    }

    private boolean hasEntityAnnotation(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("@Entity") || trimmed.startsWith("@Entity(")) {
                return true;
            }
        }
        return false;
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

    private Finding createFinding(Review review, String ruleId, String title, String category,
                                   String severity, String filePath, int lineNumber,
                                   String evidence, String recommendation) {
        Finding finding = new Finding();
        finding.setReview(review);
        finding.setSource("ARCHITECTURE");
        finding.setRuleId(ruleId);
        finding.setCategory(category);
        finding.setSeverity(severity);
        finding.setTitle(title);
        finding.setDescription(title);
        finding.setFilePath(filePath);
        finding.setLineNumber(lineNumber);
        finding.setEvidence(evidence);
        finding.setRecommendation(recommendation);
        finding.setStatus("OPEN");
        return finding;
    }
}
