package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureAnalyzerTest {
    @TempDir Path root;

    @Test
    void reportsLargeSourceFileWithRelativePath() throws Exception {
        Path nested = Files.createDirectories(root.resolve("src"));
        String content = String.join("\n", java.util.Collections.nCopies(501, "class Sample {}"));
        Files.writeString(nested.resolve("Sample.java"), content);

        List<Finding> findings = new ArchitectureAnalyzer().analyze(root.toFile(), new Review());
        assertEquals(1, findings.size());
        assertEquals("ARCHITECTURE", findings.get(0).getCategory());
        assertEquals("src/Sample.java", findings.get(0).getFilePath());
        assertTrue(findings.get(0).getEvidence().contains("501 lines"));
    }

    @Test
    void reportsControllerThatDependsDirectlyOnRepository() throws Exception {
        Path source = Files.createDirectories(root.resolve("src/main/java/example"));
        Files.writeString(source.resolve("ExampleController.java"), """
                package example;
                import example.repository.UserRepository;
                @RestController
                class ExampleController {}
                """);

        List<Finding> findings = new ArchitectureAnalyzer().analyze(root.toFile(), new Review());
        assertEquals(1, findings.size());
        assertEquals("Controller depends directly on repository", findings.get(0).getTitle());
        assertEquals(2, findings.get(0).getLineNumber());
    }
}
