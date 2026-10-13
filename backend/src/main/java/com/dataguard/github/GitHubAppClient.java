package com.dataguard.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class GitHubAppClient {
    private final RestClient api = RestClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    private final ObjectMapper objectMapper;

    @Value("${dataguard.github.app-id:}")
    private String appId;

    @Value("${dataguard.github.private-key:}")
    private String privateKeyPem;

    public GitHubAppClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && privateKeyPem != null && !privateKeyPem.isBlank();
    }

    public String createInstallationToken(long installationId) {
        if (!isConfigured()) {
            throw new IllegalStateException("GitHub App credentials are not configured.");
        }
        JsonNode response = api.post()
                .uri("/app/installations/{installationId}/access_tokens", installationId)
                .header("Authorization", "Bearer " + createAppJwt())
                .body(Map.of())
                .retrieve().body(JsonNode.class);
        String token = response == null ? null : response.path("token").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GitHub did not return an installation access token.");
        }
        return token;
    }

    public long createCheckRun(String token, String owner, String repository, String sha) {
        Map<String, Object> body = Map.of(
                "name", "DataGuard AI Review",
                "head_sha", sha,
                "status", "in_progress",
                "output", Map.of(
                        "title", "DataGuard AI review is running",
                        "summary", "The shared code-quality, syntax, security and architecture pipeline is analyzing this revision."));
        JsonNode response = api.post()
                .uri("/repos/{owner}/{repo}/check-runs", owner, repository)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve().body(JsonNode.class);
        long id = response == null ? 0 : response.path("id").asLong(0);
        if (id <= 0) throw new IllegalStateException("GitHub did not return a Check Run ID.");
        return id;
    }

    public void completeCheckRun(String token, String owner, String repository, long checkRunId,
                                 String conclusion, String title, String summary,
                                 List<Map<String, Object>> annotations) {
        Map<String, Object> output = Map.of(
                "title", truncate(title, 255),
                "summary", truncate(summary, 60000),
                "annotations", annotations);
        Map<String, Object> body = Map.of(
                "status", "completed",
                "conclusion", conclusion,
                "output", output);
        api.patch()
                .uri("/repos/{owner}/{repo}/check-runs/{checkRunId}", owner, repository, checkRunId)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve().toBodilessEntity();
    }

    public void failCheckRun(String token, String owner, String repository, long checkRunId, String message) {
        completeCheckRun(token, owner, repository, checkRunId, "failure",
                "DataGuard AI review could not complete",
                "The automated review could not complete.\n\n" + truncate(message, 3000),
                List.of());
    }

    private String createAppJwt() {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(appId.trim())
                .setIssuedAt(Date.from(now.minusSeconds(30)))
                .setExpiration(Date.from(now.plusSeconds(8 * 60)))
                .signWith(loadPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    private PrivateKey loadPrivateKey() {
        try {
            String pem = privateKeyPem.replace("\\n", "\n").trim();
            boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
            String base64 = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            if (pkcs1) der = wrapPkcs1AsPkcs8(der);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("The configured GitHub App private key is invalid. Supply the PEM private key as an environment variable.", exception);
        }
    }

    private byte[] wrapPkcs1AsPkcs8(byte[] pkcs1) throws IOException {
        byte[] algorithmIdentifier = new byte[]{
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] octetString = derValue((byte) 0x04, pkcs1);
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(version);
        inner.write(algorithmIdentifier);
        inner.write(octetString);
        return derValue((byte) 0x30, inner.toByteArray());
    }

    private byte[] derValue(byte tag, byte[] value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = value.length;
        if (length < 128) {
            out.write(length);
        } else {
            int bytes = 0;
            int remaining = length;
            while (remaining > 0) {
                bytes++;
                remaining >>>= 8;
            }
            out.write(0x80 | bytes);
            for (int shift = (bytes - 1) * 8; shift >= 0; shift -= 8) {
                out.write((length >>> shift) & 0xff);
            }
        }
        out.write(value);
        return out.toByteArray();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
