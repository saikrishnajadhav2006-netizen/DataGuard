package com.dataguard.service;

import com.dataguard.entity.AIExplanation;
import com.dataguard.entity.Finding;
import com.dataguard.repository.AIExplanationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final RadarChatProvider provider;
    private final AIExplanationRepository aiExplanationRepository;

    public AIService(
            RadarChatProvider provider,
            AIExplanationRepository aiExplanationRepository) {
        this.provider = provider;
        this.aiExplanationRepository = aiExplanationRepository;
    }

    public void generateExplanations(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return;
        }

        for (Finding finding : findings) {
            try {
                String prompt = """
                        You are DataGuard AI, an expert code reviewer.
                        Explain this code-review finding using only the supplied evidence.
                        Do not invent details or claim that code has been modified.
                        Return a concise response with these sections:
                        Explanation:
                        Impact:
                        Recommended action:

                        Finding title: %s
                        Severity: %s
                        Evidence: %s
                        Existing recommendation: %s
                        """.formatted(
                        safe(finding.getTitle()),
                        safe(finding.getSeverity()),
                        safe(finding.getEvidence()),
                        safe(finding.getRecommendation()));

                String response = provider.complete(List.of(
                        new RadarChatProvider.Turn(
                                "system",
                                "You provide accurate, concise code-review explanations."),
                        new RadarChatProvider.Turn("user", prompt)));

                if (response == null || response.isBlank()) {
                    log.warn("Groq returned an empty explanation for finding {}", finding.getId());
                    continue;
                }

                AIExplanation explanation = new AIExplanation();
                explanation.setFinding(finding);
                explanation.setExplanation(response);
                explanation.setImpact("See AI-generated explanation.");
                explanation.setRecommendedAction(
                        safe(finding.getRecommendation()));

                aiExplanationRepository.save(explanation);

            } catch (Exception e) {
                log.warn(
                        "Could not generate AI explanation for finding {}: {}",
                        finding.getId(),
                        e.getMessage());
            }
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }
}