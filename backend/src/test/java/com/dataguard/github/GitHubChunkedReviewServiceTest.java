package com.dataguard.github;

import com.dataguard.entity.Finding;
import com.dataguard.entity.GitHubReviewProgress;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.GitHubReviewProgressRepository;
import com.dataguard.repository.ProjectRepository;
import com.dataguard.repository.ReviewRepository;
import com.dataguard.repository.UserRepository;
import com.dataguard.service.ReviewEngine;
import com.dataguard.service.ScoringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GitHubChunkedReviewServiceTest {
    private FakeGitHub github;
    private GitHubReviewProgressRepository progress;
    private Map<String, GitHubReviewProgress> progressById;
    private Map<String, GitHubReviewProgress> progressByKey;
    private ProjectRepository projects;
    private ReviewRepository reviews;
    private FindingRepository findings;
    private FakeReviewEngine engine;
    private GitHubChunkedReviewService service;
    private Review parent;
    private Project project;

    @BeforeEach
    void setup() {
        github = new FakeGitHub();
        progress = mock(GitHubReviewProgressRepository.class);
        progressById = new HashMap<>();
        progressByKey = new HashMap<>();
        when(progress.findByIdempotencyKey(anyString())).thenAnswer(call -> Optional.ofNullable(progressByKey.get(call.getArgument(0))));
        when(progress.findByActiveCheckRunIdForUpdate(any())).thenAnswer(call -> progressById.values().stream()
                .filter(p -> call.getArgument(0).equals(p.getActiveCheckRunId())).findFirst());
        when(progress.saveAndFlush(any(GitHubReviewProgress.class))).thenAnswer(call -> remember(call.getArgument(0)));
        when(progress.save(any(GitHubReviewProgress.class))).thenAnswer(call -> remember(call.getArgument(0)));
        projects = mock(ProjectRepository.class);
        reviews = mock(ReviewRepository.class);
        findings = mock(FindingRepository.class);
        UserRepository users = mock(UserRepository.class);
        engine = new FakeReviewEngine();
        project = new Project(); project.setId(4L); project.setName("owner/repo");
        parent = new Review(); parent.setId(90L); parent.setProject(project);
        when(projects.findByName("owner/repo")).thenReturn(Optional.of(project));
        when(reviews.save(any(Review.class))).thenAnswer(call -> {
            Review value = call.getArgument(0);
            if (value.getId() == null) value.setId(90L);
            return value;
        });
        when(reviews.findById(90L)).thenReturn(Optional.of(parent));
        when(findings.findByReviewId(any())).thenAnswer(call -> engine.findingsByReview.getOrDefault((Long) call.getArgument(0), List.of()));
        service = new GitHubChunkedReviewService(github, progress, projects, users, reviews, findings,
                engine, new ScoringService(), new ObjectMapper());
    }

    @Test
    void pushOf120EligibleFilesProcesses50Then50Then20OnlyOnExplicitRequests() {
        List<GitHubChunkedReviewService.ChangedFile> files = files(120);
        service.startPush("owner", "repo", "main", "head-120", "delivery-1", files);

        assertEquals(1, engine.calls);
        assertBatchSummary(github.outputs.get(0), 1, 3, 120, 50);
        assertEquals(1, github.actions.get(0).size());
        assertTrue(String.valueOf(github.outputs.get(0).get("summary")).contains("Provisional score (analyzed files only)"));
        assertTrue(parent.getOverallScore() < 100, "a partial review must not publish a perfect score");

        assertThrows(IllegalArgumentException.class, () -> service.continueRequestedAction(
                "owner", "repo", 1L, "head-120", "wrong_action", "bad-action"));
        assertEquals(1, engine.calls, "a batch must not run without the requested action");

        service.continueRequestedAction("owner", "repo", 1L, "head-120",
                GitHubChunkedReviewService.NEXT_BATCH_ACTION, "action-2");
        assertEquals(2, engine.calls);
        assertBatchSummary(github.outputs.get(1), 2, 3, 120, 100);

        service.continueRequestedAction("owner", "repo", 2L, "head-120",
                GitHubChunkedReviewService.NEXT_BATCH_ACTION, "action-3");
        assertEquals(3, engine.calls);
        assertBatchSummary(github.outputs.get(2), 3, 3, 120, 120);
        assertTrue(github.actions.get(2).isEmpty());
        assertEquals("COMPLETED", parent.getStatus());
        assertEquals(93, parent.getOverallScore(), "combined findings must use the existing scoring model");
        assertThrows(IllegalArgumentException.class, () -> service.continueRequestedAction(
                "owner", "repo", 2L, "head-120", GitHubChunkedReviewService.NEXT_BATCH_ACTION, "duplicate"));

        service.startPush("owner", "repo", "main", "head-120", "duplicate-delivery", files);
        assertEquals(3, engine.calls, "duplicate event for a reviewed commit must not re-run a batch");
        assertEquals(3, github.createdRuns);
    }

    @Test
    void pullRequestOf100EligibleFilesUsesTwoBatches() {
        github.pullFiles = fileMaps(100);
        service.startPullRequest("owner", "repo", 9, "feature", "base-1", "pr-head", "pr-delivery");
        assertEquals(1, engine.calls);
        assertBatchSummary(github.outputs.get(0), 1, 2, 100, 50);
        service.continueRequestedAction("owner", "repo", 1L, "pr-head",
                GitHubChunkedReviewService.NEXT_BATCH_ACTION, "pr-action");
        assertEquals(2, engine.calls);
        assertBatchSummary(github.outputs.get(1), 2, 2, 100, 100);
        assertTrue(github.actions.get(1).isEmpty());
    }

    @Test
    void failedAndUnsupportedFilesAreReportedAsIncompleteWithoutPerfectScore() {
        github.failPath = "src/fails.py";
        service.startPush("owner", "repo", "main", "failed-head", "failed-delivery", List.of(
                new GitHubChunkedReviewService.ChangedFile("assets/logo.bin", "modified", null),
                new GitHubChunkedReviewService.ChangedFile("src/fails.py", "modified", null)));

        assertEquals(0, engine.calls);
        assertEquals("INCOMPLETE", parent.getStatus());
        assertNull(parent.getOverallScore());
        String summary = String.valueOf(github.outputs.get(0).get("summary"));
        assertTrue(summary.contains("Unsupported file type: assets/logo.bin"));
        assertTrue(summary.contains("src/fails.py (download failed"));
        assertTrue(summary.contains("No score is available"));
        assertEquals(0, github.actions.get(0).size());
    }

    @Test
    void invalidRepositoryOrCommitCannotContinueAReview() {
        service.startPush("owner", "repo", "main", "valid-head", "id", files(60));
        assertThrows(IllegalArgumentException.class, () -> service.continueRequestedAction(
                "attacker", "repo", 1L, "valid-head", GitHubChunkedReviewService.NEXT_BATCH_ACTION, "x"));
        assertThrows(IllegalArgumentException.class, () -> service.continueRequestedAction(
                "owner", "repo", 1L, "other-head", GitHubChunkedReviewService.NEXT_BATCH_ACTION, "x"));
        assertEquals(1, engine.calls);
    }

    @Test
    void partitionSizesAre50_50_20And50_50() {
        assertEquals(List.of(50, 50, 20), GitHubChunkedReviewService.partition(files(120)).stream().map(List::size).toList());
        assertEquals(List.of(50, 50), GitHubChunkedReviewService.partition(files(100)).stream().map(List::size).toList());
    }

    private static List<GitHubChunkedReviewService.ChangedFile> files(int count) {
        List<GitHubChunkedReviewService.ChangedFile> files = new ArrayList<>();
        for (int i = 0; i < count; i++) files.add(new GitHubChunkedReviewService.ChangedFile("src/File%03d.py".formatted(i), "modified", null));
        return files;
    }

    private static List<Map<String, Object>> fileMaps(int count) {
        return files(count).stream().map(f -> Map.<String, Object>of("filename", f.path, "status", f.status)).toList();
    }

    private static void assertBatchSummary(Map<String, Object> output, int batch, int totalBatches, int eligible, int analyzed) {
        String summary = (String) output.get("summary");
        assertTrue(summary.contains("Batch: " + batch + "/" + totalBatches));
        assertTrue(summary.contains("Total eligible changed files: " + eligible));
        assertTrue(summary.contains("Files analyzed: " + analyzed));
    }

    private static class FakeReviewEngine extends ReviewEngine {
        int calls;
        final Map<Long, List<Finding>> findingsByReview = new HashMap<>();
        FakeReviewEngine() { super(null, null, null, null, null, null, null, null, null); }
        @Override public Review runReview(Project project, File dir) {
            calls++;
            try (var paths = Files.walk(dir.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} });
            } catch (Exception ignored) {}
            Review review = new Review(); review.setId((long) calls); review.setProject(project); review.setStatus("COMPLETED");
            Finding finding = new Finding(); finding.setReview(review); finding.setFilePath("src/File.py"); finding.setLineNumber(1);
            finding.setSeverity(calls == 1 ? "HIGH" : "MEDIUM");
            finding.setCategory(calls == 1 ? "SECURITY" : "CODE_QUALITY");
            finding.setTitle("fixture finding"); finding.setDescription("test");
            findingsByReview.put(review.getId(), List.of(finding));
            return review;
        }
    }

    private static class FakeGitHub extends GitHubIntegrationService {
        int createdRuns;
        String failPath;
        List<Map<String, Object>> pullFiles = List.of();
        List<Map<String, Object>> outputs = new ArrayList<>();
        List<List<Map<String, Object>>> actions = new ArrayList<>();
        FakeGitHub() { super(null); }
        @Override public Long getInstallationIdForRepository(String owner, String repo) { return 1L; }
        @Override public String getInstallationAccessToken(Long installationId) { return "fake-installation-token"; }
        @Override public List<Map<String, Object>> getPullRequestFiles(String owner, String repo, int number, String token) { return pullFiles; }
        @Override public boolean downloadFileSafe(String owner, String repo, String path, String ref, String token, File dest, long maxSize) {
            if (path.equals(failPath)) return false;
            try { Files.writeString(dest.toPath(), "clean source\n"); return true; }
            catch (Exception e) { return false; }
        }
        @Override public Map<String, Object> createCheckRun(String owner, String repo, String sha, String name, String status,
                String conclusion, Map<String, Object> output, String externalId, List<Map<String, Object>> actions, String token) {
            createdRuns++;
            return Map.of("id", (long) createdRuns);
        }
        @Override public Map<String, Object> updateCheckRun(String owner, String repo, Long id, String status,
                String conclusion, Map<String, Object> output, List<Map<String, Object>> actions, String token) {
            outputs.add(output); this.actions.add(actions); return Map.of();
        }
    }

    private <T extends GitHubReviewProgress> T remember(T progress) {
        progressById.put(progress.getId(), progress); progressByKey.put(progress.getIdempotencyKey(), progress); return progress;
    }
}
