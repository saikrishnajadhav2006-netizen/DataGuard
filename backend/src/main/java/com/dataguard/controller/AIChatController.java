package com.dataguard.controller;

import com.dataguard.service.RadarChatProvider;
import com.dataguard.service.RadarChatProvider.Turn;
import com.dataguard.entity.AIConversationMessage;
import com.dataguard.entity.AIConversationSession;
import com.dataguard.entity.User;
import com.dataguard.repository.AIConversationMessageRepository;
import com.dataguard.repository.AIConversationSessionRepository;
import com.dataguard.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AIChatController {
    private final RadarChatProvider provider;
    private final UserRepository userRepository;
    private final AIConversationSessionRepository sessionRepository;
    private final AIConversationMessageRepository messageRepository;

    public AIChatController(RadarChatProvider provider, UserRepository userRepository,
                            AIConversationSessionRepository sessionRepository,
                            AIConversationMessageRepository messageRepository) {
        this.provider = provider;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    public record ChatRequest(String message, UUID sessionId) {}
    public record RadarRequest(String prompt, String mode, String category) {}

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request, Authentication authentication) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a message for the AI assistant."));
        }
        if (!provider.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "AI is not configured on the server. Set GROQ_API_KEY in the backend environment."));
        }
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Authenticated user was not found."));
            AIConversationSession conversation = request.sessionId() == null
                    ? sessionRepository.findFirstByUserOrderByUpdatedAtDesc(user).orElseGet(() -> newSession(user))
                    : sessionRepository.findBySessionIdAndUser(request.sessionId(), user)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation session was not found."));
            List<Turn> turns = new ArrayList<>();
            turns.add(new Turn("system", "You are DataGuard AI, a careful software code review assistant. Use the conversation history to answer follow-up questions in context. Do not claim to have run code or applied fixes. Explain uncertainty and advise users to review suggested code."));
            messageRepository.findTop20BySessionOrderByCreatedAtDesc(conversation).stream()
                    .sorted(java.util.Comparator.comparing(AIConversationMessage::getCreatedAt))
                    .forEach(message -> turns.add(new Turn(message.getRole(), message.getContent())));
            turns.add(new Turn("user", request.message().trim()));
            String reply = provider.complete(turns);
            saveMessage(conversation, "user", request.message().trim());
            saveMessage(conversation, "assistant", reply);
            conversation.setUpdatedAt(java.time.Instant.now());
            sessionRepository.save(conversation);
            return ResponseEntity.ok(Map.of("reply", reply, "sessionId", conversation.getSessionId()));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "The AI provider request failed. Check the backend provider configuration and logs."));
        }
    }

    @GetMapping("/chat/history")
    public ResponseEntity<?> history(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        AIConversationSession conversation = sessionRepository.findFirstByUserOrderByUpdatedAtDesc(user).orElse(null);
        if (conversation == null) return ResponseEntity.ok(Map.of("sessionId", "", "messages", List.of()));
        List<Map<String, String>> messages = messageRepository.findBySessionOrderByCreatedAtAsc(conversation).stream()
                .map(message -> Map.of("role", message.getRole(), "content", message.getContent()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("sessionId", conversation.getSessionId(), "messages", messages));
    }

    private AIConversationSession newSession(User user) {
        AIConversationSession session = new AIConversationSession();
        session.setUser(user);
        return sessionRepository.save(session);
    }

    private void saveMessage(AIConversationSession session, String role, String content) {
        AIConversationMessage message = new AIConversationMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        messageRepository.save(message);
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
