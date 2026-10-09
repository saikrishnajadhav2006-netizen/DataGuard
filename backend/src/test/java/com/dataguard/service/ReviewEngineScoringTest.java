package com.dataguard.service;

import com.dataguard.analyzer.ArchitectureAnalyzer;
import com.dataguard.analyzer.CodeQualityAnalyzer;
import com.dataguard.analyzer.DependencyCheckAnalyzer;
import com.dataguard.analyzer.PMDAnalyzer;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ReviewEngineScoringTest {
    @TempDir Path tempDir;

    @Test
    void vulnerableSourceProducesSecurityAndQualityFindingsAndLowersScores() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("vulnerable"));
        Files.writeString(source.resolve("app.py"), "API_KEY = \"test-secret-123\"\n"
                + "payload = eval(input())\nprint(payload)\n");

        Review review = runReview(source);
        List<Finding> findings = findSavedFindings();

        assertEquals("COMPLETED", review.getStatus());
        assertTrue(findings.stream().anyMatch(f -> "sec-dynamic-evaluation".equals(f.getRuleId())
                && "SECURITY".equals(f.getCategory()) && "HIGH".equals(f.getSeverity())));
        assertTrue(findings.stream().anyMatch(f -> "cq-hardcoded-secret".equals(f.getRuleId())));
        assertTrue(findings.stream().anyMatch(f -> "cq-python-print".equals(f.getRuleId())));
        assertEquals(2, findings.stream().filter(f -> "SECURITY".equals(f.getCategory())).count());
        assertEquals(80, review.getSecurityScore());
        assertEquals(98, review.getQualityScore());
        assertEquals(91, review.getOverallScore());
    }

    @Test
    void cleanSourceProducesNoFabricatedFindingsAndKeepsPerfectScores() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("clean"));
        Files.writeString(source.resolve("app.py"), "import ast\n\n"
                + "def parse_payload(payload):\n    return ast.literal_eval(payload)\n");

        Review review = runReview(source);

        assertEquals("COMPLETED", review.getStatus());
        assertTrue(findSavedFindings().isEmpty());
        assertEquals(100, review.getSecurityScore());
        assertEquals(100, review.getQualityScore());
        assertEquals(100, review.getOverallScore());
    }

    @Test
    void analyzerFailureDoesNotPresentPartialPerfectScoreAsCompleted() {
        Review review = runReview(tempDir.resolve("missing-source-directory"));

        assertEquals("INCOMPLETE", review.getStatus());
        assertFalse(review.getAnalysisWarnings().isEmpty());
        assertTrue(review.getAnalysisWarnings().stream().anyMatch(w -> w.contains("CodeQuality")));
    }

    private ReviewRepository reviewRepository;
    private FindingRepository findingRepository;
    private List<Finding> savedFindings;

    private Review runReview(Path source) {
        reviewRepository = mock(ReviewRepository.class);
        findingRepository = mock(FindingRepository.class);
        savedFindings = List.of();
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            if (review.getId() == null) review.setId(1L);
            return review;
        });
        when(findingRepository.saveAll(anyList())).thenAnswer(invocation -> {
            savedFindings = List.copyOf(invocation.getArgument(0));
            return savedFindings;
        });

        ReviewEngine engine = new ReviewEngine(reviewRepository, findingRepository,
                new CodeQualityAnalyzer(), new PMDAnalyzer(), new DependencyCheckAnalyzer(),
                new ArchitectureAnalyzer(), new ScoringService(),
                new AIService(new StaticListableBeanFactory().getBeanProvider(ChatClient.class), null));
        Project project = new Project();
        project.setName("regression-fixture");
        return engine.runReview(project, source.toFile());
    }

    private List<Finding> findSavedFindings() {
        return savedFindings;
    }
}
