package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PmdAnalyzerTest {
    @TempDir
    Path root;

    @Test
    void mapsPmdJsonToCodeQualityFindings() throws Exception {
        String json = """
                {
                  "files": [{
                    "filename": "%s",
                    "violations": [{
                      "beginline": 14,
                      "rule": "UnusedLocalVariable",
                      "priority": 3,
                      "description": "An unused local variable is present."
                    }]
                  }]
                }
                """.formatted(root.resolve("src/Example.java").toString().replace("\\", "\\\\"));
        Review review = new Review();

        List<Finding> findings = new PmdAnalyzer(new ObjectMapper()).parseResults(json, root.toFile(), review);

        assertEquals(1, findings.size());
        assertEquals("CODE_QUALITY", findings.get(0).getCategory());
        assertEquals("MEDIUM", findings.get(0).getSeverity());
        assertEquals(14, findings.get(0).getLineNumber());
        assertTrue(findings.get(0).getTitle().contains("UnusedLocalVariable"));
    }
}
