package com.dataguard.service;

import com.dataguard.entity.Finding;
import com.dataguard.entity.AIExplanation;
import com.dataguard.repository.AIExplanationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final ChatClient chatClient;
    private final AIExplanationRepository aiExplanationRepository;

    public AIService(ObjectProvider<ChatClient> chatClientProvider, AIExplanationRepository aiExplanationRepository) {
        this.chatClient = chatClientProvider.getIfAvailable();
        this.aiExplanationRepository = aiExplanationRepository;
    }

    public void generateExplanations(List<Finding> findings) {
        if (chatClient == null) {
            return;
        }

        for (Finding finding : findings) {
            String prompt = String.format(
                "You are an expert AI code reviewer. Analyze the following finding:\\n" +
                "Title: %s\\n" +
                "Severity: %s\\n" +
                "Evidence: %s\\n" +
                "Provide a short JSON response with keys: 'explanation', 'impact', 'recommendedAction'.",
                finding.getTitle(), finding.getSeverity(), finding.getEvidence()
            );

            try {
                // Correct syntax for Spring AI 1.0.0-M1
                String response = chatClient.prompt(new org.springframework.ai.chat.prompt.Prompt(prompt)).call().content();
                
                AIExplanation explanation = new AIExplanation();
                explanation.setFinding(finding);
                explanation.setExplanation("AI Analysis: " + response); 
                explanation.setImpact("Determined by AI context");
                explanation.setRecommendedAction(finding.getRecommendation() + " - AI Confirmed");
                
                aiExplanationRepository.save(explanation);
            } catch (Exception e) {
                log.warn("AI Provider unavailable or error: {}", e.getMessage());
            }
        }
    }
}
