package com.dataguard.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies the {@code X-Hub-Signature-256} header sent by GitHub
 * on every webhook delivery.
 *
 * <p>GitHub computes {@code HMAC-SHA-256(secret, rawBody)} and sends the
 * result as {@code sha256=<hex>}. We do the same computation and compare
 * using a constant-time equality check to prevent timing attacks.
 */
@Service
public class GitHubSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(GitHubSignatureVerifier.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final GitHubProperties properties;

    public GitHubSignatureVerifier(GitHubProperties properties) {
        this.properties = properties;
    }

    /**
     * Verifies the incoming webhook signature against the configured secret.
     *
     * @param signatureHeader value of {@code X-Hub-Signature-256} header,
     *                        e.g. {@code sha256=abc123...}
     * @param rawBody         the raw request body bytes
     * @return {@code true} if the signature is valid, {@code false} otherwise
     */
    public boolean isSignatureValid(String signatureHeader, byte[] rawBody) {
        String secret = properties.getWebhookSecret();

        if (secret == null || secret.isBlank()) {
            log.warn("GitHub webhook secret is not configured — rejecting all requests.");
            return false;
        }

        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Missing or malformed X-Hub-Signature-256 header.");
            return false;
        }

        String receivedHex = signatureHeader.substring(SIGNATURE_PREFIX.length());

        try {
            String computedHex = computeHmacSha256(secret, rawBody);
            boolean valid = MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    receivedHex.getBytes(StandardCharsets.UTF_8)
            );
            if (!valid) {
                log.warn("GitHub webhook signature mismatch — payload rejected.");
            }
            return valid;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA-256 for webhook verification: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Computes {@code HMAC-SHA-256(secret, data)} and returns the lowercase hex string.
     */
    String computeHmacSha256(String secret, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        mac.init(keySpec);
        byte[] digest = mac.doFinal(data);
        return bytesToHex(digest);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
