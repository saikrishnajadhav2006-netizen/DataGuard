package com.dataguard.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubWebhookController {
    private final ObjectMapper objectMapper;
    private final GitHubAppClient githubAppClient;
    private final GitHubReviewService reviewService;

    @Value("${dataguard.github.webhook-secret:}")
    private String webhookSecret = "";

    public GitHubWebhookController(ObjectMapper objectMapper, GitHubAppClient githubAppClient,
                                   GitHubReviewService reviewService) {
        this.objectMapper = objectMapper;
        this.githubAppClient = githubAppClient;
        this.reviewService = reviewService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> receive(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        if (webhookSecret == null || webhookSecret.isBlank() || !githubAppClient.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "GitHub App webhook credentials are not configured on the backend."));
        }
        if (!validSignature(body, signature, webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid GitHub webhook signature."));
        }
        if (event == null || event.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing GitHub event header."));
        }

        final JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid webhook JSON payload."));
        }

        if (!"push".equals(event) && !"pull_request".equals(event)) {
            return ResponseEntity.accepted().body(Map.of("message", "Event received; no review is required for this event."));
        }
        if ("pull_request".equals(event)) {
            String action = payload.path("action").asText("");
            if (!java.util.Set.of("opened", "synchronize", "reopened", "ready_for_review").contains(action)) {
                return ResponseEntity.accepted().body(Map.of("message", "Pull request action does not require a new review."));
            }
        }
        reviewService.processEvent(event, payload);
        return ResponseEntity.accepted().body(Map.of("message", "DataGuard review queued."));
    }

    static boolean validSignature(byte[] body, String signature, String secret) {
        if (body == null || signature == null || !signature.matches("sha256=[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            return false;
        }
    }
}
