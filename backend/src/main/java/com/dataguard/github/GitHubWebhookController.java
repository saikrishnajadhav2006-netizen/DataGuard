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
 * <p>Verified push/PR events start resumable reviews; requested Check Run
 * actions advance one persisted batch at a time.
 */
@RestController
@RequestMapping("/api/github")
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubSignatureVerifier signatureVerifier;
    private final GitHubChunkedReviewService chunkedReviewService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public GitHubWebhookController(GitHubSignatureVerifier signatureVerifier, GitHubChunkedReviewService chunkedReviewService) {
        this.signatureVerifier = signatureVerifier;
        this.chunkedReviewService = chunkedReviewService;
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

        if ("pull_request".equals(eventType) || "push".equals(eventType) || "check_run".equals(eventType)) {
            try {
                java.util.Map<String, Object> payload = objectMapper.readValue(rawBody, java.util.Map.class);
                String action = string(payload.get("action"));
                java.util.Map<String, Object> repository = map(payload.get("repository"));
                java.util.Map<String, Object> ownerData = map(repository.get("owner"));
                String owner = firstNonBlank(string(ownerData.get("login")), string(ownerData.get("name")));
                String repoName = string(repository.get("name"));
                if (owner == null || repoName == null) {
                    log.warn("Ignoring GitHub event without repository identity. event={}", eventType);
                    return ResponseEntity.ok("Webhook received.");
                }

                if ("pull_request".equals(eventType) && action != null
                        && java.util.Set.of("opened", "synchronize", "reopened", "ready_for_review", "edited").contains(action)) {
                    java.util.Map<String, Object> pr = map(payload.get("pull_request"));
                    java.util.Map<String, Object> head = map(pr.get("head"));
                    java.util.Map<String, Object> base = map(pr.get("base"));
                    String sha = string(head.get("sha"));
                    String baseSha = string(base.get("sha"));
                    String branch = string(head.get("ref"));
                    int number = ((Number) pr.get("number")).intValue();
                    dispatch("pull_request", () -> chunkedReviewService.startPullRequest(owner, repoName, number, branch, baseSha, sha, deliveryId));
                } else if ("push".equals(eventType)) {
                    if (Boolean.TRUE.equals(payload.get("deleted"))) return ResponseEntity.ok("Deleted branch ignored.");
                    String ref = string(payload.get("ref"));
                    String branch = ref != null && ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
                    String sha = string(payload.get("after"));
                    java.util.List<GitHubChunkedReviewService.ChangedFile> files = pushFiles(payload);
                    dispatch("push", () -> chunkedReviewService.startPush(owner, repoName, branch, sha, deliveryId, files));
                } else if ("check_run".equals(eventType) && "requested_action".equals(action)) {
                    java.util.Map<String, Object> checkRun = map(payload.get("check_run"));
                    java.util.Map<String, Object> requested = map(payload.get("requested_action"));
                    Long checkRunId = ((Number) checkRun.get("id")).longValue();
                    String sha = string(checkRun.get("head_sha"));
                    String identifier = string(requested.get("identifier"));
                    dispatch("requested Check Run action", () -> chunkedReviewService.continueRequestedAction(
                            owner, repoName, checkRunId, sha, identifier, deliveryId));
                }
            } catch (Exception e) {
                log.error("Failed to handle GitHub event {}: {} - {}", eventType, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        return ResponseEntity.ok("Webhook received.");
    }

    private void dispatch(String description, Runnable operation) {
        Thread thread = new Thread(() -> {
            try { operation.run(); }
            catch (Exception e) { log.error("GitHub {} failed: {}", description, e.getMessage()); }
        }, "dataguard-github-review");
        thread.setDaemon(true);
        thread.start();
    }

    private java.util.List<GitHubChunkedReviewService.ChangedFile> pushFiles(java.util.Map<String, Object> payload) {
        java.util.Map<String, GitHubChunkedReviewService.ChangedFile> files = new java.util.LinkedHashMap<>();
        Object rawCommits = payload.get("commits");
        if (rawCommits instanceof java.util.List<?> commits) {
            for (Object rawCommit : commits) {
                java.util.Map<String, Object> commit = map(rawCommit);
                addPaths(files, commit.get("added"), "added");
                addPaths(files, commit.get("modified"), "modified");
                addPaths(files, commit.get("removed"), "removed");
            }
        }
        return new java.util.ArrayList<>(files.values());
    }

    private void addPaths(java.util.Map<String, GitHubChunkedReviewService.ChangedFile> files, Object value, String status) {
        if (value instanceof java.util.List<?> paths) for (Object path : paths) {
            String name = string(path);
            if (name != null) files.put(name, new GitHubChunkedReviewService.ChangedFile(name, status, null));
        }
    }

    @SuppressWarnings("unchecked") private static java.util.Map<String, Object> map(Object value) {
        return value instanceof java.util.Map<?, ?> ? (java.util.Map<String, Object>) value : java.util.Map.of();
    }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static String firstNonBlank(String first, String second) { return first == null || first.isBlank() ? second : first; }
}
