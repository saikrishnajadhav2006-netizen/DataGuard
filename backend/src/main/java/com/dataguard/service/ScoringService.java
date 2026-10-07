package com.dataguard.service;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deterministic scoring service.
 *
 * <p>All scores are computed algorithmically from the set of findings — no AI,
 * no randomness, no hardcoded values. The same project always produces the same
 * scores under the same analyzer configuration.
 *
 * <h2>Scoring Algorithm</h2>
 * <p>Each score starts at 100. Each finding deducts points according to its
 * severity, with the deduction weighted by the score category it belongs to:
 *
 * <pre>
 *   CRITICAL  — 20 pts (security), 15 pts (quality/architecture)
 *   HIGH      — 10 pts
 *   MEDIUM    —  5 pts
 *   LOW       —  2 pts
 *   INFO      —  1 pt
 * </pre>
 *
 * <p>Scores are clamped to [0, 100].
 *
 * <p><b>Overall score</b> = weighted average:
 * <pre>
 *   overall = (security * 0.40) + (quality * 0.35) + (architecture * 0.25)
 * </pre>
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    // Deduction per severity per finding
    private static final int DEDUCT_CRITICAL = 20;
    private static final int DEDUCT_HIGH     = 10;
    private static final int DEDUCT_MEDIUM   =  5;
    private static final int DEDUCT_LOW      =  2;
    private static final int DEDUCT_INFO     =  1;

    // Weights for overall score (must sum to 1.0)
    private static final double WEIGHT_SECURITY     = 0.40;
    private static final double WEIGHT_QUALITY      = 0.35;
    private static final double WEIGHT_ARCHITECTURE = 0.25;

    /**
     * Calculates and sets all scores on the Review entity.
     * Does NOT persist — the caller is responsible for saving.
     */
    public void applyScores(Review review, List<Finding> findings) {
        int securityScore     = 100;
        int qualityScore      = 100;
        int architectureScore = 100;

        for (Finding finding : findings) {
            String category = finding.getCategory() != null ? finding.getCategory().toUpperCase() : "";
            int deduction = deductionFor(finding.getSeverity());

            // Apply deduction to the relevant sub-score
            // SEMGREP findings go to whatever category they declare
            switch (category) {
                case "SECURITY" -> securityScore -= deduction;
                case "CODE_QUALITY" -> qualityScore -= deduction;
                case "ARCHITECTURE" -> architectureScore -= deduction;
                default -> {
                    // Uncategorised findings affect quality score
                    qualityScore -= deduction;
                }
            }
        }

        // Clamp all sub-scores
        securityScore     = clamp(securityScore);
        qualityScore      = clamp(qualityScore);
        architectureScore = clamp(architectureScore);

        // Weighted overall
        int overallScore = (int) Math.round(
                securityScore     * WEIGHT_SECURITY
              + qualityScore      * WEIGHT_QUALITY
              + architectureScore * WEIGHT_ARCHITECTURE
        );
        overallScore = clamp(overallScore);

        review.setSecurityScore(securityScore);
        review.setQualityScore(qualityScore);
        review.setArchitectureScore(architectureScore);
        review.setOverallScore(overallScore);

        log.info("Scores — overall: {}, security: {}, quality: {}, architecture: {}",
                overallScore, securityScore, qualityScore, architectureScore);
    }

    private int deductionFor(String severity) {
        if (severity == null) return DEDUCT_LOW;
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> DEDUCT_CRITICAL;
            case "HIGH"     -> DEDUCT_HIGH;
            case "MEDIUM"   -> DEDUCT_MEDIUM;
            case "LOW"      -> DEDUCT_LOW;
            case "INFO"     -> DEDUCT_INFO;
            default         -> DEDUCT_LOW;
        };
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
