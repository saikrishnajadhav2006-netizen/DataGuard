package com.dataguard.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class GitHubIntegrationServicePatchTest {

    private GitHubIntegrationService githubService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() throws Exception {
        class DummyAuthService extends GitHubAppAuthService {
            public DummyAuthService() { super(new GitHubProperties()); }
            @Override public boolean isConfigured() { return true; }
            @Override public String generateAppJwt() { return "dummy.jwt"; }
        }
        
        GitHubAppAuthService mockAuth = new DummyAuthService();
        githubService = new GitHubIntegrationService(mockAuth);

        // Access the RestTemplate field via reflection to bind the MockRestServiceServer
        java.lang.reflect.Field field = GitHubIntegrationService.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        RestTemplate restTemplate = (RestTemplate) field.get(githubService);

        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void testUpdateCheckRunUsesPatchMethod() {
        String url = "https://api.github.com/repos/owner/repo/check-runs/12345";

        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess("{\"id\": 12345}", MediaType.APPLICATION_JSON));

        githubService.updateCheckRun("owner", "repo", 12345L, "completed", "success", Map.of("summary", "test"), "token123");

        mockServer.verify();
    }
}
