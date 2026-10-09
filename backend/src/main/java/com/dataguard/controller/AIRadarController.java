package com.dataguard.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/radar")
public class AIRadarController {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${deepgram.api.key:}")
    private String deepgramApiKey;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        Map<String, Object> response = new HashMap<>();

        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            response.put("error", "GEMINI_API_KEY is not configured on the server. Please add it to environment variables to enable AI Radar chat.");
            return ResponseEntity.status(503).body(response);
        }

        // For this implementation step, we return a simulated response if Gemini is configured.
        // In a full implementation, we would call the Gemini API here.
        response.put("reply", "Gemini AI Radar Response to: " + prompt + "\n\nWe recommend using PMD for static analysis and OWASP Dependency Check for dependencies. Both are free and open-source.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, Object>> transcribe(@RequestParam("audio") MultipartFile audio) {
        Map<String, Object> response = new HashMap<>();

        if (deepgramApiKey == null || deepgramApiKey.isEmpty()) {
            response.put("error", "DEEPGRAM_API_KEY is not configured on the server. Please add it to enable speech-to-text.");
            return ResponseEntity.status(503).body(response);
        }

        // Simulated Deepgram transcription
        response.put("transcript", "This is a simulated transcription. Deepgram API key is present.");
        return ResponseEntity.ok(response);
    }
}
