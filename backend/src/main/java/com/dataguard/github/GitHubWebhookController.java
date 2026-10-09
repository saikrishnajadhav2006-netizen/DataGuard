package com.dataguard.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Phase 1 GitHub webhook endpoint.
 *
 * <p>Receives webhook POST requests from GitHub, verifies the
 * HMAC-SHA-256 signature, logs the event metadata, and acknowledges.
 *
 * <p><b>Phase 2+ work (NOT implemented here):</b> PR retrieval,
 * ReviewEngine execution, GitHub Check Runs, comments, and auto-fixes.
 */
@RestController
@RequestMapping("/api/github")
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubSignatureVerifier signatureVerifier;
    private final GitHubPrReviewService prReviewService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public GitHubWebhookController(GitHubSignatureVerifier signatureVerifier, GitHubPrReviewService prReviewService) {
        this.signatureVerifier = signatureVerifier;
        this.prReviewService = prReviewService;
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /**
     * Receives GitHub webhook deliveries.
     *
     * <p>GitHub headers read (never logged in full to avoid leaking secrets):
     * <ul>
     *   <li>{@code X-GitHub-Event}      — event type, e.g. {@code push}, {@code pull_request}</li>
     *   <li>{@code X-GitHub-Delivery}   — unique delivery UUID assigned by GitHub</li>
     *   <li>{@code X-Hub-Signature-256} — HMAC-SHA-256 signature (presence logged, not value)</li>
     * </ul>
     *
     * @param eventType    value of {@code X-GitHub-Event} header
     * @param deliveryId   value of {@code X-GitHub-Delivery} header
     * @param signature    value of {@code X-Hub-Signature-256} header
     * @param rawBody      raw request body bytes (needed for HMAC verification)
     * @return {@code 401} on invalid/missing signature; {@code 200} on success
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-GitHub-Event",      required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery",   required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        // Safe header logging — NEVER log the signature value itself
        log.info("GitHub webhook received — event: '{}', delivery: '{}', signature present: {}",
                eventType, deliveryId, signature != null);

        // ── Signature verification ───────────────────────────────────────────
        if (!signatureVerifier.isSignatureValid(signature, rawBody)) {
            log.warn("Webhook rejected — invalid or missing signature. delivery: {}", deliveryId);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid webhook signature.");
        }

        // ── Signature valid — log and acknowledge ────────────────────────────
        log.info("Webhook verified — event: '{}', delivery: '{}'", eventType, deliveryId);

        // Parse payload, trigger ReviewEngine for pull_request events, etc.
        if ("pull_request".equals(eventType)) {
            try {
                log.info("Parsing pull_request payload JSON...");
                java.util.Map<String, Object> payload = objectMapper.readValue(rawBody, java.util.Map.class);
                String action = (String) payload.get("action");
                log.info("Extracted action: {}", action);
                
                if ("opened".equals(action) || "synchronize".equals(action) || "reopened".equals(action)) {
                    java.util.Map<String, Object> repo = (java.util.Map<String, Object>) payload.get("repository");
                    java.util.Map<String, Object> ownerObj = (java.util.Map<String, Object>) repo.get("owner");
                    java.util.Map<String, Object> pr = (java.util.Map<String, Object>) payload.get("pull_request");
                    java.util.Map<String, Object> head = (java.util.Map<String, Object>) pr.get("head");

                    String owner = (String) ownerObj.get("login");
                    String repoName = (String) repo.get("name");
                    int number = ((Number) pr.get("number")).intValue();
                    String headSha = (String) head.get("sha");
                    
                    log.info("Extracted PR metadata - owner: {}, repo: {}, number: {}, sha: {}", owner, repoName, number, headSha);

                    // Trigger async PR review
                    log.info("Starting background thread for GitHubPrReviewService.processPullRequest");
                    new Thread(() -> {
                        try {
                            prReviewService.processPullRequest(owner, repoName, number, headSha);
                        } catch (Exception e) {
                            log.error("Exception in background thread processing PR", e);
                        }
                    }).start();
                } else {
                    log.info("Ignoring pull_request action: {}", action);
                }
            } catch (Exception e) {
                log.error("Failed to parse pull_request payload: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            }
        }

        return ResponseEntity.ok("Webhook received.");
    }
}
