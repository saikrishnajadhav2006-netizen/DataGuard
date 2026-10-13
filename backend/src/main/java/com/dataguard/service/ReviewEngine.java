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
import java.util.List;

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

        // All entry points (ZIP uploads and future GitHub events) share this pipeline.
        // Semgrep is executed inside SecurityAgent; it is not a separate agent.
        List<Finding> findings = new ArrayList<>();
        for (ReviewAgent agent : reviewAgents) {
            List<Finding> agentFindings = agent.analyze(extractedProjectDir, review);
            if (agentFindings != null && !agentFindings.isEmpty()) {
                findings.addAll(agentFindings);
            }
        }

        if (!findings.isEmpty()) {
            findingRepository.saveAll(findings);
        }

        int score = 100;
        for (Finding finding : findings) {
            if ("CRITICAL".equalsIgnoreCase(finding.getSeverity())) score -= 15;
            else if ("HIGH".equalsIgnoreCase(finding.getSeverity())) score -= 10;
            else if ("MEDIUM".equalsIgnoreCase(finding.getSeverity())) score -= 5;
            else score -= 2;
        }
        review.setQualityScore(Math.max(0, score));
        review = reviewRepository.save(review);

        // AI explanations are optional and must never block deterministic analysis.
        if (!findings.isEmpty()) {
            try {
                aiService.generateExplanations(findings);
            } catch (RuntimeException ignored) {
                // Preserve scan results if an optional AI provider is unavailable.
            }
        }
        return review;
    }

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
