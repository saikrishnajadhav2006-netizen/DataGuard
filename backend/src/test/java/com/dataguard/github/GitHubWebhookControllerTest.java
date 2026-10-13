package com.dataguard.github;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class GitHubWebhookControllerTest {
    @Test
    void acceptsOnlyMatchingSha256WebhookSignatures() throws Exception {
        byte[] body = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
        String secret = "test-webhook-secret";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));

        assertTrue(GitHubWebhookController.validSignature(body, signature, secret));
        assertFalse(GitHubWebhookController.validSignature(body, signature, "wrong-secret"));
        assertFalse(GitHubWebhookController.validSignature(body, "sha256=invalid", secret));
    }
}
