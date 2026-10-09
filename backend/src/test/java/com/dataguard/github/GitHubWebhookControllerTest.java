package com.dataguard.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link GitHubWebhookController}.
 *
 * <p>Uses a full Spring Boot context with H2 in-memory DB.
 * The test webhook secret is supplied via {@link TestPropertySource}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "dataguard.github.webhook-secret=test-integration-secret",
    "spring.config.import="
})
class GitHubWebhookControllerTest {

    private static final String WEBHOOK_URL = "/api/github/webhook";
    private static final String TEST_SECRET = "test-integration-secret";
    private static final String TEST_PAYLOAD = "{\"action\":\"opened\",\"number\":1}";
    private static final byte[] TEST_BODY = TEST_PAYLOAD.getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GitHubSignatureVerifier signatureVerifier;

    private String validSignature;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, InvalidKeyException {
        validSignature = "sha256=" + signatureVerifier.computeHmacSha256(TEST_SECRET, TEST_BODY);
    }

    // ── Valid request ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid signature → 200 OK")
    void validRequest_returns200() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", "abc-123-delivery-id")
                .header("X-Hub-Signature-256", validSignature)
                .content(TEST_BODY))
            .andExpect(status().isOk())
            .andExpect(content().string("Webhook received."));
    }

    @Test
    @DisplayName("Valid signature with push event → 200 OK")
    void validPushEvent_returns200() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "push")
                .header("X-GitHub-Delivery", "xyz-456-push-delivery")
                .header("X-Hub-Signature-256", validSignature)
                .content(TEST_BODY))
            .andExpect(status().isOk());
    }

    // ── Invalid signature ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Wrong signature → 401 Unauthorized")
    void invalidSignature_returns401() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", "delivery-id-bad-sig")
                .header("X-Hub-Signature-256",
                        "sha256=0000000000000000000000000000000000000000000000000000000000000000")
                .content(TEST_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tampered body (valid sig for different body) → 401 Unauthorized")
    void tamperedBody_returns401() throws Exception {
        // Signature was computed for TEST_BODY but we send a different body
        byte[] tamperedBody = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", "delivery-id-tampered")
                .header("X-Hub-Signature-256", validSignature)  // valid sig for original body
                .content(tamperedBody))
            .andExpect(status().isUnauthorized());
    }

    // ── Missing signature ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing X-Hub-Signature-256 header → 401 Unauthorized")
    void missingSignature_returns401() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", "delivery-id-no-sig")
                // deliberately omit X-Hub-Signature-256
                .content(TEST_BODY))
            .andExpect(status().isUnauthorized());
    }
}
