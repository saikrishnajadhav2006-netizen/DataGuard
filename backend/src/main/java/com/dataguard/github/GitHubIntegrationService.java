package com.dataguard.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the GitHub API using GitHub App authentication.
 */
@Service
public class GitHubIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIntegrationService.class);
    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final String ACCEPT_HEADER_V3 = "application/vnd.github.v3+json";

    private final GitHubAppAuthService authService;
    private final RestTemplate restTemplate;

    public GitHubIntegrationService(GitHubAppAuthService authService) {
        this.authService = authService;
        this.restTemplate = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    /**
     * Checks if GitHub App integration is fully configured.
     */
    public boolean isConfigured() {
        return authService.isConfigured();
    }

    /**
     * Retrieves the installation ID for a specific repository.
     */
    public Long getInstallationIdForRepository(String owner, String repo) {
        if (!isConfigured()) return null;

        String url = String.format("%s/repos/%s/%s/installation", GITHUB_API_URL, owner, repo);
        HttpHeaders headers = createAuthHeaders(authService.generateAppJwt());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            
            if (response.getBody() != null && response.getBody().containsKey("id")) {
                return ((Number) response.getBody().get("id")).longValue();
            }
        } catch (HttpClientErrorException e) {
            log.error("Failed to find installation for {}/{}: {} - {}", owner, repo, e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    /**
     * Generates a short-lived installation access token for the given installation ID.
     */
    public String getInstallationAccessToken(Long installationId) {
        if (!isConfigured() || installationId == null) return null;

        String url = String.format("%s/app/installations/%d/access_tokens", GITHUB_API_URL, installationId);
        HttpHeaders headers = createAuthHeaders(authService.generateAppJwt());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(headers), Map.class);
            
            if (response.getBody() != null && response.getBody().containsKey("token")) {
                return (String) response.getBody().get("token");
            }
        } catch (HttpClientErrorException e) {
            log.error("Failed to generate installation token for ID {}: {} - {}", installationId, e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    /**
     * Retrieves basic repository metadata to verify access.
     */
    public Map<String, Object> getRepositoryMetadata(String owner, String repo, String installationToken) {
        if (installationToken == null) return null;

        String url = String.format("%s/repos/%s/%s", GITHUB_API_URL, owner, repo);
        HttpHeaders headers = createAuthHeaders(installationToken); // Uses token, not JWT!
        headers.set("Authorization", "token " + installationToken); // token prefix instead of Bearer for installation tokens (Bearer also works)

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch repo metadata for {}/{}: {}", owner, repo, e.getStatusCode());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Pull Request API (read-only) — Stage 1
    // -------------------------------------------------------------------------

    /**
     * Retrieves metadata for a single Pull Request.
     *
     * <p>Endpoint: {@code GET /repos/{owner}/{repo}/pulls/{number}}
     *
     * @param owner           repository owner
     * @param repo            repository name
     * @param number          PR number
     * @param installationToken installation access token (never logged)
     * @return raw PR payload map, or {@code null} on any error/missing token
     */
    public Map<String, Object> getPullRequest(String owner, String repo, int number, String installationToken) {
        if (installationToken == null) return null;

        String url = String.format("%s/repos/%s/%s/pulls/%d", GITHUB_API_URL, owner, repo, number);
        HttpHeaders headers = createInstallationTokenHeaders(installationToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch PR #{} for {}/{}: {} - {}", number, owner, repo, e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    /**
     * Retrieves the list of files changed in a Pull Request.
     *
     * <p>Endpoint: {@code GET /repos/{owner}/{repo}/pulls/{number}/files}
     *
     * <p>Each entry contains at minimum: {@code filename}, {@code status},
     * {@code additions}, {@code deletions}, {@code changes}, and when present
     * {@code patch}.
     *
     * @param owner           repository owner
     * @param repo            repository name
     * @param number          PR number
     * @param installationToken installation access token (never logged)
     * @return list of file-change maps, or {@code null} on any error/missing token
     */
    public List<Map<String, Object>> getPullRequestFiles(String owner, String repo, int number, String installationToken) {
        if (installationToken == null) return null;
        HttpHeaders headers = createInstallationTokenHeaders(installationToken);

        List<Map<String, Object>> allFiles = new java.util.ArrayList<>();
        // GitHub returns at most 100 changed files per page, and at most 3,000 files per PR.
        for (int page = 1; page <= 30; page++) {
            String url = String.format("%s/repos/%s/%s/pulls/%d/files?per_page=100&page=%d",
                    GITHUB_API_URL, owner, repo, number, page);
            try {
                ResponseEntity<List> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
                List<Map<String, Object>> pageFiles = response.getBody();
                if (pageFiles == null) return null;
                allFiles.addAll(pageFiles);
                if (pageFiles.size() < 100) return allFiles;
            } catch (HttpClientErrorException e) {
                log.error("Failed to fetch PR #{} files for {}/{} page {}: {} - {}",
                        number, owner, repo, page, e.getStatusCode(), e.getResponseBodyAsString());
                return null;
            }
        }
        log.warn("PR #{} for {}/{} reached GitHub's 3,000-file API limit; source list may be incomplete.", number, owner, repo);
        allFiles.add(Map.of("_dataguard_truncated", true));
        return allFiles;
    }

    /**
     * Retrieves the repository tree for a specific ref/SHA so a downstream
     * service can materialize the source files.
     *
     * <p>Endpoint: {@code GET /repos/{owner}/{repo}/git/trees/{sha}?recursive=1}
     *
     * @param owner           repository owner
     * @param repo            repository name
     * @param sha             commit SHA or branch/tag ref to inspect
     * @param installationToken installation access token (never logged)
     * @return raw tree payload map (contains {@code tree} entries with
     *         {@code path} and {@code sha}), or {@code null} on any error/missing token
     */
    public Map<String, Object> getRepositoryTree(String owner, String repo, String sha, String installationToken) {
        if (installationToken == null) return null;
        if (sha == null || sha.isBlank()) return null;

        String url = String.format("%s/repos/%s/%s/git/trees/%s?recursive=1", GITHUB_API_URL, owner, repo, sha);
        HttpHeaders headers = createInstallationTokenHeaders(installationToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch git tree for {}/{} @ {}: {} - {}", owner, repo, sha, e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    public boolean downloadFileSafe(String owner, String repo, String path, String ref, String installationToken, java.io.File dest, long maxSize) {
        if (installationToken == null) return false;
        String encodedPath = org.springframework.web.util.UriUtils.encodePath(path, java.nio.charset.StandardCharsets.UTF_8);
        String url = String.format("%s/repos/%s/%s/contents/%s?ref=%s", GITHUB_API_URL, owner, repo, encodedPath, ref);
        
        try {
        return Boolean.TRUE.equals(restTemplate.execute(url, HttpMethod.GET, request -> {
            request.getHeaders().set("Authorization", "token " + installationToken);
            request.getHeaders().set("Accept", "application/vnd.github.v3.raw");
        }, response -> {
            if (response.getStatusCode().isError()) {
                log.error("Failed to download {}: {}", path, response.getStatusCode());
                return false;
            }
            long contentLength = response.getHeaders().getContentLength();
            if (contentLength > maxSize) {
                log.warn("File {} is too large ({} bytes). Max size is {} bytes.", path, contentLength, maxSize);
                return false;
            }
            try (java.io.InputStream is = response.getBody();
                 java.io.OutputStream os = new java.io.FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int read;
                while ((read = is.read(buffer)) != -1) {
                    totalRead += read;
                    if (totalRead > maxSize) {
                        log.warn("File {} exceeded max size during streaming.", path);
                        return false;
                    }
                    os.write(buffer, 0, read);
                }
                return true;
            }
        }));
        } catch (Exception e) {
            log.warn("Could not download changed file {} from {}/{} ({}).", path, owner, repo, e.getClass().getSimpleName());
            return false;
        }
    }

    public byte[] downloadFileContent(String owner, String repo, String path, String ref, String installationToken) {
        if (installationToken == null) return null;
        String url = String.format("%s/repos/%s/%s/contents/%s?ref=%s", GITHUB_API_URL, owner, repo, path, ref);
        HttpHeaders headers = createInstallationTokenHeaders(installationToken);
        headers.set("Accept", "application/vnd.github.v3.raw");

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Failed to download file {} for {}/{}: {} - {}", path, owner, repo, e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    public String getBranchHeadSha(String owner, String repo, String branch, String installationToken) {
        String encodedBranch = org.springframework.web.util.UriUtils.encodePathSegment(branch, java.nio.charset.StandardCharsets.UTF_8);
        String url = String.format("%s/repos/%s/%s/branches/%s", GITHUB_API_URL, owner, repo, encodedBranch);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(createInstallationTokenHeaders(installationToken)), Map.class);
            Map body = response.getBody();
            Map commit = body == null ? null : (Map) body.get("commit");
            return commit == null ? null : (String) commit.get("sha");
        } catch (HttpClientErrorException e) {
            log.warn("Could not verify branch {}/{}:{} ({}).", owner, repo, branch, e.getStatusCode());
            return null;
        }
    }

    public String getFileSha(String owner, String repo, String path, String ref, String installationToken) {
        String encodedPath = org.springframework.web.util.UriUtils.encodePath(path, java.nio.charset.StandardCharsets.UTF_8);
        String encodedRef = org.springframework.web.util.UriUtils.encodeQueryParam(ref, java.nio.charset.StandardCharsets.UTF_8);
        String url = String.format("%s/repos/%s/%s/contents/%s?ref=%s", GITHUB_API_URL, owner, repo, encodedPath, encodedRef);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(createInstallationTokenHeaders(installationToken)), Map.class);
            return response.getBody() == null ? null : (String) response.getBody().get("sha");
        } catch (HttpClientErrorException e) {
            log.warn("Could not retrieve file metadata for {}/{}:{} ({}).", owner, repo, path, e.getStatusCode());
            return null;
        }
    }

    public boolean createBranch(String owner, String repo, String branch, String fromSha, String installationToken) {
        String url = String.format("%s/repos/%s/%s/git/refs", GITHUB_API_URL, owner, repo);
        Map<String, Object> body = Map.of("ref", "refs/heads/" + branch, "sha", fromSha);
        try {
            restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, createInstallationTokenHeaders(installationToken)), Map.class);
            return true;
        } catch (HttpClientErrorException e) {
            log.warn("Could not create fix branch for {}/{} ({}).", owner, repo, e.getStatusCode());
            return false;
        }
    }

    public boolean updateFileOnBranch(String owner, String repo, String path, String message, String contentBase64,
                                      String branch, String fileSha, String installationToken) {
        String encodedPath = org.springframework.web.util.UriUtils.encodePath(path, java.nio.charset.StandardCharsets.UTF_8);
        String url = String.format("%s/repos/%s/%s/contents/%s", GITHUB_API_URL, owner, repo, encodedPath);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("message", message);
        body.put("content", contentBase64);
        body.put("branch", branch);
        body.put("sha", fileSha);
        try {
            restTemplate.exchange(url, HttpMethod.PUT,
                    new HttpEntity<>(body, createInstallationTokenHeaders(installationToken)), Map.class);
            return true;
        } catch (HttpClientErrorException e) {
            log.warn("Could not write approved fix to branch {}/{}:{} ({}).", owner, repo, branch, e.getStatusCode());
            return false;
        }
    }

    public Map<String, Object> createPullRequest(String owner, String repo, String title, String bodyText,
                                                  String headBranch, String baseBranch, String installationToken) {
        String url = String.format("%s/repos/%s/%s/pulls", GITHUB_API_URL, owner, repo);
        Map<String, Object> body = Map.of("title", title, "body", bodyText, "head", headBranch, "base", baseBranch);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, createInstallationTokenHeaders(installationToken)), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.warn("Could not open approved fix PR for {}/{} ({}).", owner, repo, e.getStatusCode());
            return null;
        }
    }

    /**
     * Creates a check run.
     */
    public Map<String, Object> createCheckRun(String owner, String repo, String headSha, String name, String status, String conclusion, Map<String, Object> output, String installationToken) {
        return createCheckRun(owner, repo, headSha, name, status, conclusion, output, null, null, installationToken);
    }

    public Map<String, Object> createCheckRun(String owner, String repo, String headSha, String name, String status,
                                               String conclusion, Map<String, Object> output, String externalId,
                                               List<Map<String, Object>> actions, String installationToken) {
        if (installationToken == null) return null;
        String url = String.format("%s/repos/%s/%s/check-runs", GITHUB_API_URL, owner, repo);
        HttpHeaders headers = createInstallationTokenHeaders(installationToken);
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("head_sha", headSha);
        if (status != null) body.put("status", status);
        if (conclusion != null) body.put("conclusion", conclusion);
        if (output != null) body.put("output", output);
        if (externalId != null) body.put("external_id", externalId);
        if (actions != null && !actions.isEmpty()) body.put("actions", actions);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Failed to create check run for {}/{}: {} - {}", owner, repo, e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    /**
     * Updates a check run.
     */
    public Map<String, Object> updateCheckRun(String owner, String repo, Long checkRunId, String status, String conclusion, Map<String, Object> output, String installationToken) {
        return updateCheckRun(owner, repo, checkRunId, status, conclusion, output, null, installationToken);
    }

    public Map<String, Object> updateCheckRun(String owner, String repo, Long checkRunId, String status,
                                               String conclusion, Map<String, Object> output,
                                               List<Map<String, Object>> actions, String installationToken) {
        if (installationToken == null) return null;
        String url = String.format("%s/repos/%s/%s/check-runs/%d", GITHUB_API_URL, owner, repo, checkRunId);
        HttpHeaders headers = createInstallationTokenHeaders(installationToken);
        
        Map<String, Object> body = new java.util.HashMap<>();
        if (status != null) body.put("status", status);
        if (conclusion != null) body.put("conclusion", conclusion);
        if (output != null) body.put("output", output);
        if (actions != null) body.put("actions", actions);

        try {
            // PATCH doesn't work directly with RestTemplate in some configurations, but we can try HttpMethod.PATCH
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.PATCH, new HttpEntity<>(body, headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Failed to update check run for {}/{}: {} - {}", owner, repo, e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", ACCEPT_HEADER_V3);
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }

    private HttpHeaders createInstallationTokenHeaders(String installationToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + installationToken);
        headers.set("Accept", ACCEPT_HEADER_V3);
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }
}
