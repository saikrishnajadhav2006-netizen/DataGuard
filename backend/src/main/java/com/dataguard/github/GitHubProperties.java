package com.dataguard.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the GitHub App / webhook integration.
 *
 * <p>All values are supplied via environment variables (never hardcoded).
 */
@ConfigurationProperties(prefix = "dataguard.github")
public class GitHubProperties {

    /**
     * The GitHub App ID.
     */
    private String appId;

    /**
     * Absolute or relative path to the GitHub App private key PEM file.
     */
    private String privateKeyPath;

    /**
     * HMAC-SHA-256 secret configured on the GitHub webhook.
     */
    private String webhookSecret = "";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
