package com.dataguard.controller;

import com.dataguard.service.RadarChatProvider;
import com.dataguard.service.RadarChatProvider.Turn;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AIChatController {
    private final RadarChatProvider provider;

    public AIChatController(RadarChatProvider provider) {
        this.provider = provider;
    }

    public record ChatRequest(String message) {}
    public record RadarRequest(String prompt, String mode, String category) {}

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a message for the AI assistant."));
        }
        if (!provider.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "AI is not configured on the server. Set GROQ_API_KEY in the backend environment."));
        }
        try {
            String reply = provider.complete(List.of(
                    new Turn("system", "You are DataGuard AI, a careful software code review assistant. Do not claim to have run code or applied fixes. Explain uncertainty and advise users to review suggested code."),
                    new Turn("user", request.message().trim())
            ));
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "The AI provider request failed. Check the backend provider configuration and logs."));
        }
    }

    @PostMapping("/radar")
    public ResponseEntity<?> radar(@RequestBody RadarRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Describe what you want AI Radar to help with."));
        }
        if (!provider.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "AI Radar is not configured. Set GROQ_API_KEY in the backend environment."));
        }
        String mode = "workflow".equalsIgnoreCase(request.mode()) ? "workflow" : "find";
        String system = mode.equals("workflow")
                ? "You are DataGuard AI Radar. Create a practical numbered workflow for the user's goal. For each step suggest tool categories and name tools only when you can explain their purpose. Do not fabricate prices or claim live availability."
                : "You are DataGuard AI Radar. Recommend relevant AI/software tools for the user's task. Explain each tool's purpose and note whether pricing or capabilities need verification. Do not claim these are live search results.";
        String prompt = request.prompt().trim()
                + (request.category() == null || request.category().isBlank() ? "" : "\nPreferred category: " + request.category().trim());
        try {
            String reply = provider.complete(List.of(new Turn("system", system), new Turn("user", prompt)));
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "AI Radar could not complete the request. Check the backend provider configuration and logs."));
        }
    }
}
