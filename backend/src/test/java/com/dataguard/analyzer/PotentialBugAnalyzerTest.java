package com.dataguard.analyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PotentialBugAnalyzerTest {
    @TempDir
    Path root;

    @Test
    void detectsPythonExceptionHandlerThatOnlyPasses() throws Exception {
        Files.writeString(root.resolve("example.py"), "try:\n    work()\nexcept Exception:\n    pass\n");

        List<Finding> findings = new PotentialBugAnalyzer().analyze(root.toFile(), new Review());

        assertEquals(1, findings.size());
        assertEquals("BUG", findings.get(0).getCategory());
        assertEquals("Exception silently swallowed (potential bug)", findings.get(0).getTitle());
        assertEquals(3, findings.get(0).getLineNumber());
    }
}
