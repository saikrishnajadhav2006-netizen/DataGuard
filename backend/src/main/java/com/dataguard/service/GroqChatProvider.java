package com.dataguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GroqChatProvider implements RadarChatProvider {
    private final RestClient groqClient;
    private final RestClient geminiClient;
    private final String groqApiKey;
    private final String groqModel;
    private final String geminiApiKey;
    private final String geminiModel;

    public GroqChatProvider(
            RestClient.Builder restClientBuilder,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String groqBaseUrl,
            @Value("${groq.api-key:}") String groqApiKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String groqModel,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String geminiBaseUrl,
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String geminiModel) {
        this.groqClient = restClientBuilder.clone().baseUrl(groqBaseUrl).build();
        this.geminiClient = restClientBuilder.clone().baseUrl(geminiBaseUrl).build();
        this.groqApiKey = groqApiKey == null ? "" : groqApiKey.trim();
        this.groqModel = groqModel;
        this.geminiApiKey = geminiApiKey == null ? "" : geminiApiKey.trim();
        this.geminiModel = geminiModel;
    }

    @Override
    public boolean isConfigured() {
        return !groqApiKey.isBlank() || !geminiApiKey.isBlank();
    }

    @Override
    public String complete(List<Turn> turns) {
        if (turns == null || turns.isEmpty()) {
            throw new IllegalArgumentException("At least one chat message is required.");
        }
        List<Map<String, String>> messages = turns.stream()
                .map(turn -> Map.of("role", turn.role(), "content", turn.content()))
                .toList();

        RuntimeException groqFailure = null;
        if (!groqApiKey.isBlank()) {
            try {
                return completeWithGroq(messages);
            } catch (RuntimeException ex) {
                groqFailure = ex;
            }
        }

        if (!geminiApiKey.isBlank()) {
            try {
                return completeWithGemini(turns);
            } catch (RuntimeException geminiFailure) {
                if (groqFailure != null) geminiFailure.addSuppressed(groqFailure);
                throw new IllegalStateException("Both AI providers failed. Check Groq/Gemini quota, key, model, and backend logs.", geminiFailure);
            }
        }

        if (groqFailure != null) {
            throw new IllegalStateException("Groq request failed and GEMINI_API_KEY is not configured for fallback.", groqFailure);
        }
        throw new IllegalStateException("No AI provider is configured. Set GROQ_API_KEY and/or GEMINI_API_KEY in the backend environment.");
    }

    private String completeWithGroq(List<Map<String, String>> messages) {
        JsonNode response = groqClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", groqModel, "messages", messages, "temperature", 0.2))
                .retrieve()
                .body(JsonNode.class);
        JsonNode content = response == null ? null : response.path("choices").path(0).path("message").path("content");
        if (content == null || content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new IllegalStateException("Groq returned an empty chat completion.");
        }
        return content.asText();
    }

    private String completeWithGemini(List<Turn> turns) {
        List<Map<String, Object>> contents = new ArrayList<>();
        String systemInstruction = null;
        for (Turn turn : turns) {
            if ("system".equalsIgnoreCase(turn.role())) {
                systemInstruction = turn.content();
                continue;
            }
            String role = "assistant".equalsIgnoreCase(turn.role()) ? "model" : "user";
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", turn.content()))));
        }
        if (contents.isEmpty()) {
            throw new IllegalArgumentException("At least one user message is required for Gemini.");
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("contents", contents);
        body.put("generationConfig", Map.of("temperature", 0.2));
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        }

        JsonNode response = geminiClient.post()
                .uri("/models/{model}:generateContent?key={key}", geminiModel, geminiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode parts = response == null ? null : response.path("candidates").path(0).path("content").path("parts");
        if (parts == null || !parts.isArray()) {
            throw new IllegalStateException("Gemini returned no candidate content.");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode value = part.path("text");
            if (!value.isMissingNode() && !value.isNull()) text.append(value.asText());
        }
        if (text.toString().isBlank()) throw new IllegalStateException("Gemini returned an empty completion.");
        return text.toString();
    }
}
