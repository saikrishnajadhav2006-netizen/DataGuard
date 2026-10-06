package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Component
public class CodeQualityAnalyzer {

    public List<Finding> analyze(File projectDir, Review review) {
        List<Finding> findings = new ArrayList<>();
        analyzeDirectory(projectDir, review, findings);
        return findings;
    }

    private void analyzeDirectory(File dir, Review review, List<Finding> findings) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Optimizer rule: skip node_modules, target, etc.
                String name = file.getName();
                if (name.equals("node_modules") || name.equals("target") || name.equals(".git") || name.equals("build")) {
                    continue;
                }
                analyzeDirectory(file, review, findings);
            } else if (file.getName().endsWith(".java")) {
                analyzeFile(file, review, findings);
            }
        }
    }

    private void analyzeFile(File file, Review review, List<Finding> findings) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();

                // Rule 1: System.out.println
                if (line.contains("System.out.println")) {
                    findings.add(createFinding(review, "CODE_QUALITY", "LOW", "System.out.println used", 
                            file.getName(), i + 1, line, "Use a proper logging framework like SLF4J instead of standard output."));
                }
                
                // Rule 2: Empty catch block
                if (line.contains("catch") && line.contains("{") && line.contains("}")) {
                    findings.add(createFinding(review, "CODE_QUALITY", "HIGH", "Empty catch block", 
                            file.getName(), i + 1, line, "Never swallow exceptions silently. Log the error or handle it appropriately."));
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // Simple logging for academic project
        }
    }

    private Finding createFinding(Review review, String category, String severity, String title, String filePath, int line, String evidence, String recommendation) {
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
