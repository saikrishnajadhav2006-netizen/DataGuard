package com.dataguard.service;

import com.dataguard.analyzer.ArchitectureAnalyzer;
import com.dataguard.analyzer.CodeQualityAnalyzer;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.repository.AIExplanationRepository;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the full DataGuard review pipeline:
 *
 * CodeQualityAnalyzer
 *        +
 * SemgrepAnalyzer
 *        +
 * ArchitectureAnalyzer
 *        ↓
 * Combined Findings
 *        ↓
 * ScoringService
 *        ↓
 * Persist Review + Findings
 *        ↓
 * AIService (optional)
 *        ↓
 * Cleanup temporary directory
 */
@Service
public class ReviewEngine {

    private static final Logger log =
            LoggerFactory.getLogger(ReviewEngine.class);

    private final ReviewRepository reviewRepository;
    private final FindingRepository findingRepository;
    private final AIExplanationRepository aiExplanationRepository;
    private final CodeQualityAnalyzer codeQualityAnalyzer;
    private final com.dataguard.analyzer.PMDAnalyzer pmdAnalyzer;
    private final com.dataguard.analyzer.DependencyCheckAnalyzer dependencyCheckAnalyzer;
    private final ArchitectureAnalyzer architectureAnalyzer;
    private final ScoringService scoringService;
    private final AIService aiService;

    public ReviewEngine(
            ReviewRepository reviewRepository,
            FindingRepository findingRepository,
            AIExplanationRepository aiExplanationRepository,
            CodeQualityAnalyzer codeQualityAnalyzer,
            com.dataguard.analyzer.PMDAnalyzer pmdAnalyzer, com.dataguard.analyzer.DependencyCheckAnalyzer dependencyCheckAnalyzer,
            ArchitectureAnalyzer architectureAnalyzer,
            ScoringService scoringService,
            AIService aiService) {

        this.reviewRepository = reviewRepository;
        this.findingRepository = findingRepository;
        this.aiExplanationRepository = aiExplanationRepository;
        this.codeQualityAnalyzer = codeQualityAnalyzer;
        this.pmdAnalyzer = pmdAnalyzer;
        this.dependencyCheckAnalyzer = dependencyCheckAnalyzer;
        this.architectureAnalyzer = architectureAnalyzer;
        this.scoringService = scoringService;
        this.aiService = aiService;
    }

    /**
     * Runs the complete review pipeline and returns the persisted Review.
     *
     * @param project      already-persisted Project entity
     * @param extractedDir temporary directory containing the extracted ZIP
     * @return persisted Review
     */
    public Review runReview(Project project, File extractedDir) {

        // 1. Create and persist RUNNING review record
        Review review = new Review();
        review.setProject(project);
        review.setStatus("RUNNING");

        review = reviewRepository.save(review);

        /*
         * IMPORTANT:
         * review is reassigned above, so it is not effectively final.
         * Lambdas below therefore use currentReview instead.
         */
        final Review currentReview = review;

        List<Finding> allFindings = new ArrayList<>();
        List<String> failedAnalyzers = new ArrayList<>();

        try {

            // 2. Run all analyzers independently
            log.info(
                    "Starting review #{} for project '{}'",
                    currentReview.getId(),
                    project.getName()
            );

            // Code quality analysis
            List<Finding> qualityFindings = runAnalyzer(
                    "CodeQuality",
                    () -> codeQualityAnalyzer.analyze(
                            extractedDir,
                            currentReview
                    ), failedAnalyzers
            );

            allFindings.addAll(qualityFindings);

            // PMD security/static analysis
            List<Finding> pmdFindings = runAnalyzer(
                    "PMD",
                    () -> pmdAnalyzer.analyze(extractedDir, currentReview), failedAnalyzers
            );
            allFindings.addAll(pmdFindings);

            // Dependency Check analysis
            List<Finding> depFindings = runAnalyzer(
                    "Dependency Check",
                    () -> dependencyCheckAnalyzer.analyze(extractedDir, currentReview), failedAnalyzers
            );
            allFindings.addAll(depFindings);

            // Architecture analysis
            List<Finding> archFindings = runAnalyzer(
                    "Architecture",
                    () -> architectureAnalyzer.analyze(
                            extractedDir,
                            currentReview
                    ), failedAnalyzers
            );

            allFindings.addAll(archFindings);

            // 3. Save all findings
            if (!allFindings.isEmpty()) {
                findingRepository.saveAll(allFindings);
            }

            // 4. Calculate deterministic scores
            scoringService.applyScores(
                    currentReview,
                    allFindings
            );
            currentReview.setAnalysisWarnings(failedAnalyzers);

            // 5. Mark review as completed
            currentReview.setStatus(failedAnalyzers.isEmpty() ? "COMPLETED" : "INCOMPLETE");
            currentReview.setCompletedAt(LocalDateTime.now());

            review = reviewRepository.save(currentReview);

            log.info(
                    "Review #{} completed. {} findings. Overall score: {}",
                    currentReview.getId(),
                    allFindings.size(),
                    currentReview.getOverallScore()
            );
            if (!failedAnalyzers.isEmpty()) {
                log.warn("Review #{} is incomplete; analyzer(s) failed: {}", currentReview.getId(), failedAnalyzers);
            }

        } catch (Exception e) {

            log.error(
                    "Review #{} failed unexpectedly: {}",
                    currentReview.getId(),
                    e.getMessage(),
                    e
            );

            currentReview.setStatus("FAILED");

            review = reviewRepository.save(currentReview);

        } finally {

            // 6. Always clean up temporary extraction directory
            deleteDirectory(extractedDir);
        }

        // 7. Optional AI explanations
        // AI must never block the deterministic review.
        if (!allFindings.isEmpty()) {

            try {

                aiService.generateExplanations(allFindings);

            } catch (Exception e) {

                log.warn(
                        "AI explanation generation failed (non-fatal): {}",
                        e.getMessage()
                );
            }
        }

        return review;
    }

    /**
     * Re-runs analyzers on an existing review (for example after approved ZIP fixes).
     * Previous findings for this review are replaced; the review id and project stay the same.
     */
    public Review reRunReview(Review review, File extractedDir) {
        if (review == null || review.getId() == null || review.getProject() == null) {
            throw new IllegalArgumentException("Review and project are required for re-analysis.");
        }
        final Review currentReview = reviewRepository.findById(review.getId()).orElseThrow();
        Project project = currentReview.getProject();
        aiExplanationRepository.deleteByFinding_Review_Id(currentReview.getId());
        findingRepository.deleteByReviewId(currentReview.getId());
        currentReview.setStatus("RUNNING");
        currentReview.setCompletedAt(null);
        reviewRepository.save(currentReview);

        List<Finding> allFindings = new ArrayList<>();
        List<String> failedAnalyzers = new ArrayList<>();
        try {
            log.info("Re-running review #{} for project '{}'", currentReview.getId(), project.getName());
            allFindings.addAll(runAnalyzer("CodeQuality",
                    () -> codeQualityAnalyzer.analyze(extractedDir, currentReview), failedAnalyzers));
            allFindings.addAll(runAnalyzer("PMD",
                    () -> pmdAnalyzer.analyze(extractedDir, currentReview), failedAnalyzers));
            allFindings.addAll(runAnalyzer("Dependency Check",
                    () -> dependencyCheckAnalyzer.analyze(extractedDir, currentReview), failedAnalyzers));
            allFindings.addAll(runAnalyzer("Architecture",
                    () -> architectureAnalyzer.analyze(extractedDir, currentReview), failedAnalyzers));

            if (!allFindings.isEmpty()) {
                findingRepository.saveAll(allFindings);
            }
            scoringService.applyScores(currentReview, allFindings);
            currentReview.setAnalysisWarnings(failedAnalyzers);
            currentReview.setStatus(failedAnalyzers.isEmpty() ? "COMPLETED" : "INCOMPLETE");
            currentReview.setCompletedAt(LocalDateTime.now());
            reviewRepository.save(currentReview);
        } catch (Exception e) {
            log.error("Re-review #{} failed: {}", currentReview.getId(), e.getMessage(), e);
            currentReview.setStatus("FAILED");
            reviewRepository.save(currentReview);
        } finally {
            deleteDirectory(extractedDir);
        }

        if (!allFindings.isEmpty()) {
            try {
                aiService.generateExplanations(allFindings);
            } catch (Exception e) {
                log.warn("AI explanation generation failed during re-review (non-fatal): {}", e.getMessage());
            }
        }
        return currentReview;
    }

    /**
     * Functional interface representing an analyzer task.
     */
    @FunctionalInterface
    private interface AnalyzerTask {

        List<Finding> run();
    }

    /**
     * Runs one analyzer safely.
     *
     * An analyzer failure should not prevent the other analyzers
     * from running.
     */
    private List<Finding> runAnalyzer(
            String name,
            AnalyzerTask task,
            List<String> failedAnalyzers) {

        try {

            List<Finding> results = task.run();

            if (results == null) {
                throw new IllegalStateException("Analyzer returned no result list");
            }

            log.info(
                    "{} analyzer: {} finding(s)",
                    name,
                    results.size()
            );

            return results;

        } catch (Exception e) {

            failedAnalyzers.add(name + (e.getMessage() == null ? " failed" : ": " + e.getMessage()));

            log.error(
                    "{} analyzer threw an unexpected exception: {}",
                    name,
                    e.getMessage(),
                    e
            );

            return List.of();
        }
    }

    /**
     * Recursively deletes a temporary directory.
     */
    private void deleteDirectory(File dir) {

        if (dir == null || !dir.exists()) {
            return;
        }

        try {

            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {

                        if (!file.delete()) {

                            log.debug(
                                    "Could not delete temp file: {}",
                                    file.getAbsolutePath()
                            );
                        }
                    });

        } catch (IOException e) {

            log.warn(
                    "Could not fully clean up temp directory {}: {}",
                    dir.getAbsolutePath(),
                    e.getMessage()
            );
        }
    }
}
