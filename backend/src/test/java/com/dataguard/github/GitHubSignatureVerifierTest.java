package com.dataguard.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GitHubSignatureVerifier}.
 *
 * <p>These tests run without a Spring context (no DB, no server).
 */
class GitHubSignatureVerifierTest {

    private static final String TEST_SECRET = "test-webhook-secret-1234";
    private static final byte[] TEST_BODY = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

    private GitHubProperties properties;
    private GitHubSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setWebhookSecret(TEST_SECRET);
        verifier = new GitHubSignatureVerifier(properties);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String computeValidSignature(byte[] body)
            throws NoSuchAlgorithmException, InvalidKeyException {
        return "sha256=" + verifier.computeHmacSha256(TEST_SECRET, body);
    }

    // ── Valid signature ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid signature → accepted")
    void validSignature_accepted() throws Exception {
        String sig = computeValidSignature(TEST_BODY);
        assertThat(verifier.isSignatureValid(sig, TEST_BODY)).isTrue();
    }

    @Test
    @DisplayName("Valid signature with different payload → accepted")
    void validSignature_differentPayload_accepted() throws Exception {
        byte[] body = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
        String sig = computeValidSignature(body);
        assertThat(verifier.isSignatureValid(sig, body)).isTrue();
    }

    // ── Invalid signature ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Wrong signature value → rejected")
    void invalidSignature_rejected() {
        String wrongSig = "sha256=0000000000000000000000000000000000000000000000000000000000000000";
        assertThat(verifier.isSignatureValid(wrongSig, TEST_BODY)).isFalse();
    }

    @Test
    @DisplayName("Signature computed with wrong secret → rejected")
    void signatureWrongSecret_rejected() throws Exception {
        // Use a different secret to create the signature
        GitHubProperties otherProps = new GitHubProperties();
        otherProps.setWebhookSecret("wrong-secret");
        GitHubSignatureVerifier otherVerifier = new GitHubSignatureVerifier(otherProps);

        String wrongSig = "sha256=" + otherVerifier.computeHmacSha256("wrong-secret", TEST_BODY);
        // Validate against the correct verifier (with TEST_SECRET)
        assertThat(verifier.isSignatureValid(wrongSig, TEST_BODY)).isFalse();
    }

    @Test
    @DisplayName("Signature for different body → rejected (body tampered)")
    void signatureBodyTampered_rejected() throws Exception {
        String sig = computeValidSignature(TEST_BODY);
        byte[] tamperedBody = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isSignatureValid(sig, tamperedBody)).isFalse();
    }

    // ── Missing signature ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Null signature header → rejected")
    void nullSignature_rejected() {
        assertThat(verifier.isSignatureValid(null, TEST_BODY)).isFalse();
    }

    @Test
    @DisplayName("Empty signature header → rejected")
    void emptySignature_rejected() {
        assertThat(verifier.isSignatureValid("", TEST_BODY)).isFalse();
    }

    @Test
    @DisplayName("Signature without sha256= prefix → rejected")
    void signatureNoPrefixed_rejected() {
        assertThat(verifier.isSignatureValid("abc123", TEST_BODY)).isFalse();
    }

    // ── Missing / blank secret ────────────────────────────────────────────────

    @Test
    @DisplayName("Blank webhook secret → rejects everything")
    void blankSecret_rejectsEverything() throws Exception {
        properties.setWebhookSecret("");
        // Even a technically-correct HMAC with empty secret must be rejected
        assertThat(verifier.isSignatureValid("sha256=anything", TEST_BODY)).isFalse();
    }

    @Test
    @DisplayName("Null webhook secret → rejects everything")
    void nullSecret_rejectsEverything() {
        properties.setWebhookSecret(null);
        assertThat(verifier.isSignatureValid("sha256=anything", TEST_BODY)).isFalse();
    }
}
