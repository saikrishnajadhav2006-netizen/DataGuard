package com.dataguard.controller;

import com.dataguard.service.AIProviderException;
import com.dataguard.service.AIRadarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/radar")
public class AIRadarController {
    private final AIRadarService service;

    public AIRadarController(AIRadarService service) { this.service = service; }

    @PostMapping("/chat")
    public AIRadarService.ChatResult chat(@RequestBody AIRadarService.ChatRequest request, Authentication authentication) {
        return service.chat(authentication.getName(), request);
    }

    @GetMapping("/conversations")
    public java.util.List<AIRadarService.ConversationSummary> conversations(Authentication authentication) {
        return service.list(authentication.getName());
    }

    @GetMapping("/conversations/{id}")
    public AIRadarService.ChatResult conversation(@PathVariable Long id, Authentication authentication) {
        return service.get(authentication.getName(), id);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id, Authentication authentication) {
        service.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AIProviderException.class)
    public ResponseEntity<Map<String, String>> providerError(AIProviderException error) {
        return ResponseEntity.status(error.getStatus()).body(Map.of("error", error.getMessage()));
    }
}
