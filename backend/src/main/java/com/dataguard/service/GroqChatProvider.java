package com.dataguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class GroqChatProvider implements RadarChatProvider {

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final String provider;

    public GroqChatProvider(
            RestClient.Builder builder,
            @Value("${groq.base-url}") String baseUrl,
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.model:}") String model,
            @Value("${groq.provider:groq}") String provider) {

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build());

        requestFactory.setReadTimeout(Duration.ofSeconds(45));

        this.client = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        this.apiKey = apiKey;
        this.model = model;
        this.provider = provider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(List<Turn> turns) {

        if (!"groq".equalsIgnoreCase(provider)) {
            throw new AIProviderException(
                    "Only Groq is supported. Set GROQ_PROVIDER=groq.",
                    503);
        }

        if (apiKey == null || apiKey.isBlank()
                || model == null || model.isBlank()) {
            throw new AIProviderException(
                    "AI Radar is not configured. Set GROQ_API_KEY and GROQ_MODEL.",
                    503);
        }

        if (turns == null || turns.isEmpty()) {
            throw new AIProviderException("Enter a message.", 400);
        }

        List<Map<String, String>> messages = turns.stream()
                .map(turn -> Map.of(
                        "role", turn.role(),
                        "content", turn.content()))
                .toList();

        try {
            Map<String, Object> result = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", model,
                            "messages", messages,
                            "temperature", 0.3))
                    .retrieve()
                    .body(Map.class);

            if (result == null
                    || !(result.get("choices") instanceof List<?> choices)
                    || choices.isEmpty()) {
                throw new AIProviderException(
                        "Groq returned an empty response. Please try again.",
                        502);
            }

            Object message =
                    ((Map<String, Object>) choices.get(0)).get("message");

            Object content = message instanceof Map<?, ?> map
                    ? map.get("content")
                    : null;

            if (!(content instanceof String text) || text.isBlank()) {
                throw new AIProviderException(
                        "Groq returned an empty response. Please try again.",
                        502);
            }

            return text;

        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();

            if (status == 401 || status == 403) {
                throw new AIProviderException(
                        "Groq rejected the API key. Check GROQ_API_KEY.",
                        502);
            }

            if (status == 429) {
                throw new AIProviderException(
                        "Groq is rate limiting requests. Try again shortly.",
                        429);
            }

            throw new AIProviderException(
                    "Groq could not complete this request. Please try again later.",
                    502);

        } catch (ResourceAccessException error) {
            throw new AIProviderException(
                    "AI Radar could not reach Groq. Check your network and try again.",
                    502);
        }
    }
}