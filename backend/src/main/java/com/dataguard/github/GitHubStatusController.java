package com.dataguard.github;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Diagnostic endpoint for the GitHub integration.
 */
@RestController
@RequestMapping("/api/github")
public class GitHubStatusController {

    private final GitHubProperties properties;
    private final GitHubIntegrationService integrationService;

    public GitHubStatusController(GitHubProperties properties, GitHubIntegrationService integrationService) {
        this.properties = properties;
        this.integrationService = integrationService;
    }

    /**
     * Returns the status of the GitHub App integration.
     * NEVER returns secrets or tokens.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam(required = false, defaultValue = "saikrishnajadhav2006-netizen") String owner,
                                                         @RequestParam(required = false, defaultValue = "DataGuard") String repo) {
        Map<String, Object> status = new HashMap<>();
        status.put("appIdConfigured", properties.getAppId() != null && !properties.getAppId().isBlank());
        status.put("appId", properties.getAppId());
        status.put("privateKeyPathConfigured", properties.getPrivateKeyPath() != null && !properties.getPrivateKeyPath().isBlank());
        status.put("isFullyConfigured", integrationService.isConfigured());

        if (integrationService.isConfigured()) {
            Long installationId = integrationService.getInstallationIdForRepository(owner, repo);
            status.put("targetRepo", owner + "/" + repo);
            status.put("installationIdFound", installationId != null);

            if (installationId != null) {
                String token = integrationService.getInstallationAccessToken(installationId);
                status.put("installationTokenGenerated", token != null);
                
                if (token != null) {
                    Map<String, Object> metadata = integrationService.getRepositoryMetadata(owner, repo, token);
                    status.put("repoMetadataAccessible", metadata != null);
                    if (metadata != null) {
                        status.put("repoName", metadata.get("full_name"));
                        status.put("repoPrivate", metadata.get("private"));
                    }
                }
            }
        }

        return ResponseEntity.ok(status);
    }
}
