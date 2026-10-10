package com.dataguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GroqChatProvider implements RadarChatProvider {
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GroqChatProvider(
            RestClient.Builder restClientBuilder,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String model) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String complete(List<Turn> turns) {
        if (!isConfigured()) {
            throw new IllegalStateException("Groq is not configured. Set GROQ_API_KEY in the backend environment.");
        }
        if (turns == null || turns.isEmpty()) {
            throw new IllegalArgumentException("At least one chat message is required.");
        }

        List<Map<String, String>> messages = turns.stream()
                .map(turn -> Map.of("role", turn.role(), "content", turn.content()))
                .toList();

        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "messages", messages, "temperature", 0.2))
                .retrieve()
                .body(JsonNode.class);

        JsonNode content = response == null
                ? null
                : response.path("choices").path(0).path("message").path("content");
        if (content == null || content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new IllegalStateException("Groq returned an empty chat completion.");
        }
        return content.asText();
    }
}
