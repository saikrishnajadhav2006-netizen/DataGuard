package com.dataguard.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubAppAuthServiceTest {

    private GitHubProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
    }

    @Test
    @DisplayName("Disabled when App ID is missing")
    void disabledWhenAppIdMissing() {
        properties.setPrivateKeyPath("some/path");
        GitHubAppAuthService authService = new GitHubAppAuthService(properties);
        assertThat(authService.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Disabled when Key Path is missing")
    void disabledWhenKeyPathMissing() {
        properties.setAppId("12345");
        GitHubAppAuthService authService = new GitHubAppAuthService(properties);
        assertThat(authService.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Disabled when Key file does not exist")
    void disabledWhenKeyFileMissing() {
        properties.setAppId("12345");
        properties.setPrivateKeyPath("does-not-exist.pem");
        GitHubAppAuthService authService = new GitHubAppAuthService(properties);
        assertThat(authService.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Generates JWT when properly configured")
    void generatesJwtWhenConfigured(@TempDir Path tempDir) throws Exception {
        // Generate a real RSA key pair for testing
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        
        // Write the private key to a temporary PEM file
        String base64Key = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String pemContent = "-----BEGIN PRIVATE KEY-----\n" +
                base64Key.replaceAll("(.{64})", "$1\n") +
                "\n-----END PRIVATE KEY-----";
        
        Path keyPath = tempDir.resolve("test-key.pem");
        Files.writeString(keyPath, pemContent);

        properties.setAppId("12345");
        properties.setPrivateKeyPath(keyPath.toString());
        
        GitHubAppAuthService authService = new GitHubAppAuthService(properties);
        
        assertThat(authService.isConfigured()).isTrue();
        
        String jwt = authService.generateAppJwt();
        assertThat(jwt).isNotNull().isNotEmpty();
        
        // Verify JWT structure (header.payload.signature)
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
    }
}
