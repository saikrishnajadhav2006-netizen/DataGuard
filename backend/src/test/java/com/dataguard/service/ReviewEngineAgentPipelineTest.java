package com.dataguard.service;

import com.dataguard.agent.ReviewAgent;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReviewEngineAgentPipelineTest {
    @TempDir
    Path root;

    @Test
    void orchestratesAllAgentsDeduplicatesFindingsAndCalculatesScore() {
        ReviewAgent quality = mock(ReviewAgent.class);
        ReviewAgent security = mock(ReviewAgent.class);
        ReviewAgent architecture = mock(ReviewAgent.class);
        when(quality.analyze(eq(root.toFile()), any(Review.class)))
                .thenReturn(List.of(finding("CODE_QUALITY", "Repeated finding", "HIGH", "src/A.java", 10)));
        when(security.analyze(eq(root.toFile()), any(Review.class)))
                .thenReturn(List.of(
                        finding("CODE_QUALITY", "Repeated finding", "HIGH", "src/A.java", 10),
                        finding("SECURITY", "Credential detected", "MEDIUM", "src/config.js", 4)));
        when(architecture.analyze(eq(root.toFile()), any(Review.class))).thenReturn(List.of());

        ReviewEngine engine = new ReviewEngine(null, null, List.of(quality, security, architecture), null);
        ReviewEngine.AnalysisResult result = engine.analyzeDirectory(root.toFile());

        assertEquals(2, result.findings().size());
        assertEquals(85, result.qualityScore());
        verify(quality).analyze(eq(root.toFile()), any(Review.class));
        verify(security).analyze(eq(root.toFile()), any(Review.class));
        verify(architecture).analyze(eq(root.toFile()), any(Review.class));
    }

    private Finding finding(String category, String title, String severity, String path, int line) {
        Finding finding = new Finding();
        finding.setCategory(category);
        finding.setTitle(title);
        finding.setSeverity(severity);
        finding.setFilePath(path);
        finding.setLineNumber(line);
        finding.setStatus("OPEN");
        return finding;
    }
}
