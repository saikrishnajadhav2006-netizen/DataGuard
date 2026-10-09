package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class PMDAnalyzer {

    public List<Finding> analyze(File sourceDir, Review review) {
        List<Finding> findings = new ArrayList<>();
        try {
            try (Stream<java.nio.file.Path> paths = Files.walk(sourceDir.toPath())) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        List<String> lines = Files.readAllLines(path);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (line.contains("System.out.println") || line.contains("e.printStackTrace()")) {
                                Finding f = new Finding();
                                f.setReview(review);
                                
                                f.setCategory("CODE_QUALITY");
                                f.setSeverity("LOW");
                                f.setTitle("Avoid console print statements");
                                f.setDescription("Use a logger instead of System.out or stack trace.");
                                f.setFilePath(sourceDir.toPath().relativize(path).toString().replace('\\', '/'));
                                f.setLineNumber(i + 1);
                                f.setRecommendation("Replace with a properly configured Logger.");
                                findings.add(f);
                            }
                            if (line.contains("SELECT ") && line.contains("+") && line.toLowerCase().contains("where")) {
                                Finding f = new Finding();
                                f.setReview(review);
                                
                                f.setCategory("SECURITY");
                                f.setSeverity("HIGH");
                                f.setTitle("Potential SQL Injection");
                                f.setDescription("String concatenation in SQL queries can lead to SQL injection.");
                                f.setFilePath(sourceDir.toPath().relativize(path).toString().replace('\\', '/'));
                                f.setLineNumber(i + 1);
                                f.setRecommendation("Use PreparedStatement with parameterized queries.");
                                findings.add(f);
                            }
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Could not analyze file " + path.getFileName(), e);
                    }
                });
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not scan source tree with PMD analyzer", e);
        }
        return findings;
    }
}
