package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyntaxAnalyzerTest {
    @TempDir
    Path root;

    @Test
    void reportsSyntaxErrorsAcrossSupportedLanguages() throws Exception {
        Files.writeString(root.resolve("broken.py"), "def broken(:\n    print('hello')\n");
        Files.writeString(root.resolve("broken.js"), "function broken( {\n  return 1;\n}\n");

        List<Finding> findings = new SyntaxAnalyzer(new ObjectMapper()).analyze(root.toFile(), new Review());

        assertTrue(findings.size() >= 2, "Expected parser errors from both source files");
        assertTrue(findings.stream().allMatch(f -> "SYNTAX".equals(f.getCategory())));
        assertTrue(findings.stream().anyMatch(f -> f.getFilePath().equals("broken.py")));
        assertTrue(findings.stream().anyMatch(f -> f.getFilePath().equals("broken.js")));
        assertTrue(findings.stream().allMatch(f -> f.getLineNumber() != null && f.getLineNumber() > 0));
    }

    @Test
    void validSourceDoesNotProduceSyntaxFindings() throws Exception {
        Files.writeString(root.resolve("valid.py"), "def greet(name):\n    return f'Hello {name}'\n");
        Files.writeString(root.resolve("valid.js"), "function greet(name) { return name; }\n");

        assertTrue(new SyntaxAnalyzer(new ObjectMapper()).analyze(root.toFile(), new Review()).isEmpty());
    }
}
