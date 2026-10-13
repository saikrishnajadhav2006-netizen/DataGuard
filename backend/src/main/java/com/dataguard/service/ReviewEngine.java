package com.dataguard.service;

import com.dataguard.agent.ReviewAgent;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReviewEngine {
    private final ReviewRepository reviewRepository;
    private final FindingRepository findingRepository;
    private final List<ReviewAgent> reviewAgents;
    private final AIService aiService;

    public Review runReview(Project project, File extractedProjectDir) {
        Review review = new Review();
        review.setProject(project);
        review.setQualityScore(100);
        review = reviewRepository.save(review);

        AnalysisResult analysis = analyzeDirectory(extractedProjectDir, review);
        List<Finding> findings = analysis.findings();
        if (!findings.isEmpty()) {
            findingRepository.saveAll(findings);
        }

        review.setQualityScore(analysis.qualityScore());
        review = reviewRepository.save(review);

        // AI explanations are optional and must never block deterministic analysis.
        if (!findings.isEmpty()) {
            try {
                aiService.generateExplanations(findings);
            } catch (RuntimeException ignored) {
                // Preserve the completed static-analysis results if AI is unavailable.
            }
        }
        return review;
    }

    /**
     * Runs the exact same agent pipeline without persisting a website review.
     * This lets GitHub Check Runs reuse analysis without creating fake user projects.
     */
    public AnalysisResult analyzeDirectory(File projectDirectory) {
        return analyzeDirectory(projectDirectory, new Review());
    }

    private AnalysisResult analyzeDirectory(File projectDirectory, Review review) {
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            throw new IllegalArgumentException("Review requires an existing project directory.");
        }
        List<Finding> collected = new ArrayList<>();
        for (ReviewAgent agent : reviewAgents) {
            List<Finding> agentFindings = agent.analyze(projectDirectory, review);
            if (agentFindings != null) collected.addAll(agentFindings);
        }

        // Deduplicate overlapping analyzer output before scoring or reporting.
        Map<String, Finding> unique = new LinkedHashMap<>();
        for (Finding finding : collected) {
            String key = normalize(finding.getCategory()) + "|"
                    + normalize(finding.getFilePath()) + "|"
                    + (finding.getLineNumber() == null ? "" : finding.getLineNumber()) + "|"
                    + normalize(finding.getTitle());
            unique.putIfAbsent(key, finding);
        }
        List<Finding> findings = new ArrayList<>(unique.values());

        int score = 100;
        for (Finding finding : findings) {
            if ("CRITICAL".equalsIgnoreCase(finding.getSeverity())) score -= 15;
            else if ("HIGH".equalsIgnoreCase(finding.getSeverity())) score -= 10;
            else if ("MEDIUM".equalsIgnoreCase(finding.getSeverity())) score -= 5;
            else score -= 2;
        }
        return new AnalysisResult(List.copyOf(findings), Math.max(0, score));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record AnalysisResult(List<Finding> findings, int qualityScore) {}

    public ReviewEngine(ReviewRepository reviewRepository,
                        FindingRepository findingRepository,
                        List<ReviewAgent> reviewAgents,
                        AIService aiService) {
        this.reviewRepository = reviewRepository;
        this.findingRepository = findingRepository;
        this.reviewAgents = List.copyOf(reviewAgents);
        this.aiService = aiService;
    }
}
