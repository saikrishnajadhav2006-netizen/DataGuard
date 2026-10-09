package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DependencyCheckAnalyzer {

    public List<Finding> analyze(File sourceDir, Review review) {
        List<Finding> findings = new ArrayList<>();
        File pomFile = new File(sourceDir, "pom.xml");
        if (pomFile.exists()) {
            try {
                String content = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(pomFile.toPath())))
                        .toString();
                if (content.contains("log4j-core") && content.contains("2.14")) {
                    Finding f = new Finding();
                    f.setReview(review);
                    
                    f.setCategory("SECURITY");
                    f.setSeverity("CRITICAL");
                    f.setTitle("Vulnerable Component: log4j-core");
                    f.setDescription("CVE-2021-44228: Apache Log4j2 JNDI features vulnerability.");
                    f.setFilePath("pom.xml");
                    f.setLineNumber(1);
                    f.setRecommendation("Upgrade log4j-core to version 2.15.0 or later.");
                    findings.add(f);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not analyze dependency manifest pom.xml", e);
            }
        }
        return findings;
    }
}
