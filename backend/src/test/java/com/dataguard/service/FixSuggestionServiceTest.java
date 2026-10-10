package com.dataguard.service;

import com.dataguard.dto.FixApprovalRequest;
import com.dataguard.entity.Finding;
import com.dataguard.entity.GitHubReviewProgress;
import com.dataguard.entity.Review;
import com.dataguard.github.GitHubIntegrationService;
import com.dataguard.repository.GitHubReviewProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixSuggestionServiceTest {
    private static final String SOURCE = "import sqlite3\n"
            + "def find_user(username):\n"
            + "    cursor.execute(\"SELECT * FROM users WHERE username = '\" + username + \"'\")\n";

    private GitHubReviewProgressRepository progressRepository;
    private FakeGitHub github;
    private FixSuggestionService service;
    private GitHubReviewProgress progress;
    private Finding finding;

    @BeforeEach
    void setup() {
        progressRepository = mock(GitHubReviewProgressRepository.class);
        github = new FakeGitHub();
        service = new FixSuggestionService(progressRepository, github, new ObjectMapper());
        Review review = new Review(); review.setId(17L);
        finding = new Finding(); finding.setId(42L); finding.setReview(review);
        finding.setRuleId("sec-python-sql-concat"); finding.setFilePath("src/app.py"); finding.setLineNumber(3);
        finding.setEvidence(SOURCE.split("\\n")[2]); finding.setRecommendation("Use a parameterized SQL query.");
        progress = new GitHubReviewProgress(); progress.setOwner("octo"); progress.setRepository("sample");
        progress.setBranch("main"); progress.setCommitSha("head123");
        progress.setBatchResultsJson("[{\"reviewIds\":[17]}]");
        when(progressRepository.findAll()).thenReturn(List.of(progress));
    }

    @Test
    void generatesReviewableSqliteParameterizedPatch() {
        var proposal = service.suggest(finding);
        assertTrue(proposal.patch().contains("-" + finding.getEvidence()));
        assertTrue(proposal.patch().contains("+    cursor.execute(\"SELECT * FROM users WHERE username = ?\", (username,))"), proposal.toString());
        assertEquals("src/app.py", proposal.filePath());
        assertEquals(3, proposal.lineNumber());
        assertTrue(proposal.safeToApply(), "current source, commit, driver, and GitHub review context are verified");
        assertNull(proposal.reason());
    }

    @Test
    void rejectsInvalidFindingPaths() {
        finding.setFilePath("../secrets.py");
        assertThrows(IllegalArgumentException.class, () -> service.suggest(finding));
    }

    @Test
    void unsupportedFindingHasNoAutomaticPatch() {
        finding.setRuleId("some-other-rule");
        var proposal = service.suggest(finding);
        assertNull(proposal.patch());
        assertFalse(proposal.safeToApply());
        assertTrue(proposal.reason().contains("No safe automatic patch"));
    }

    @Test
    void staleCommitCannotBeApplied() {
        github.branchHead = "newer-head";
        assertThrows(IllegalStateException.class, () -> service.apply(finding,
                new FixApprovalRequest(true, "octo/sample", "main", "head123")));
        assertEquals(0, github.branchCreations);
    }

    @Test
    void approvalMustMatchVerifiedRepositoryBranchAndCommit() {
        assertThrows(IllegalStateException.class, () -> service.apply(finding,
                new FixApprovalRequest(true, "someone/else", "main", "head123")));
        assertEquals(0, github.branchCreations);
    }

    @Test
    void explicitApprovalCreatesFixBranchAndPullRequest() {
        var result = service.apply(finding, new FixApprovalRequest(true, "octo/sample", "main", "head123"));
        assertTrue(result.pullRequestCreated());
        assertEquals(12, result.pullRequestNumber());
        assertTrue(result.pullRequestUrl().contains("/pull/12"));
        assertNotEquals("main", github.createdBranch);
        String updated = new String(Base64.getDecoder().decode(github.updatedContent));
        assertTrue(updated.contains("cursor.execute(\"SELECT * FROM users WHERE username = ?\", (username,))"));
    }

    @Test
    void approvalIsRequiredBeforeAnyGitHubWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.apply(finding,
                new FixApprovalRequest(false, "octo/sample", "main", "head123")));
        assertEquals(0, github.branchCreations);
    }

    private static class FakeGitHub extends GitHubIntegrationService {
        String branchHead = "head123";
        String createdBranch;
        String updatedContent;
        int branchCreations;

        FakeGitHub() { super(null); }
        @Override public Long getInstallationIdForRepository(String owner, String repo) { return 7L; }
        @Override public String getInstallationAccessToken(Long installationId) { return "test-token"; }
        @Override public String getBranchHeadSha(String owner, String repo, String branch, String token) { return branchHead; }
        @Override public boolean downloadFileSafe(String owner, String repo, String path, String ref, String token, File dest, long max) {
            try { Files.writeString(dest.toPath(), SOURCE); return true; }
            catch (Exception e) { return false; }
        }
        @Override public String getFileSha(String owner, String repo, String path, String ref, String token) { return "blob-sha"; }
        @Override public boolean createBranch(String owner, String repo, String branch, String sha, String token) {
            branchCreations++; createdBranch = branch; return true;
        }
        @Override public boolean updateFileOnBranch(String owner, String repo, String path, String message, String content,
                                                    String branch, String sha, String token) {
            updatedContent = content; return true;
        }
        @Override public Map<String, Object> createPullRequest(String owner, String repo, String title, String body,
                                                              String head, String base, String token) {
            return Map.of("number", 12, "html_url", "https://github.com/octo/sample/pull/12");
        }
    }
}
