package com.dataguard.service;

import com.dataguard.analyzer.CodeQualityAnalyzer;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class ReviewEngine {

    private final ReviewRepository reviewRepository;
    private final FindingRepository findingRepository;
    private final CodeQualityAnalyzer codeQualityAnalyzer;
    private final AIService aiService;
    
    // You can inject SecurityAnalyzer and ArchitectureAnalyzer here later

    public Review runReview(Project project, File extractedProjectDir) {
        // 1. Create a new Review
        Review review = new Review();
        review.setProject(project);
        review.setQualityScore(100); // Start with perfect score
        review = reviewRepository.save(review);

        // 2. Run Deterministic Analyzers
        List<Finding> codeFindings = codeQualityAnalyzer.analyze(extractedProjectDir, review);
        
        // Save findings to database
        if (!codeFindings.isEmpty()) {
            findingRepository.saveAll(codeFindings);
            
            // Deduct points based on severity (Simple logic)
            int score = 100;
            for(Finding f : codeFindings) {
                if("HIGH".equals(f.getSeverity())) score -= 10;
                else if("MEDIUM".equals(f.getSeverity())) score -= 5;
                else score -= 2;
            }
            review.setQualityScore(Math.max(0, score));
            reviewRepository.save(review);
        }

        // 3. AI Explanation step
        if (!codeFindings.isEmpty()) {
            // Using Spring AI to generate explanations for the deterministic findings
            aiService.generateExplanations(codeFindings);
        }

        return review;
    }

    public ReviewEngine(ReviewRepository reviewRepository, FindingRepository findingRepository, CodeQualityAnalyzer codeQualityAnalyzer, AIService aiService) {
        this.reviewRepository = reviewRepository;
        this.findingRepository = findingRepository;
        this.codeQualityAnalyzer = codeQualityAnalyzer;
        this.aiService = aiService;
    }
}
