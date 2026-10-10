package com.dataguard.github;

import com.dataguard.entity.Finding;
import com.dataguard.entity.GitHubReviewProgress;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.GitHubReviewProgressRepository;
import com.dataguard.repository.ProjectRepository;
import com.dataguard.repository.ReviewRepository;
import com.dataguard.repository.UserRepository;
import com.dataguard.service.ReviewEngine;
import com.dataguard.service.ScoringService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Runs persisted, explicitly continued GitHub code review batches. */
@Service
public class GitHubChunkedReviewService {
    public static final int BATCH_SIZE = 50;
    public static final String NEXT_BATCH_ACTION = "analyze_next_batch";
    private static final Logger log = LoggerFactory.getLogger(GitHubChunkedReviewService.class);
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final TypeReference<List<ChangedFile>> FILE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<BatchResult>> BATCH_LIST = new TypeReference<>() {};

    private final GitHubIntegrationService github;
    private final GitHubReviewProgressRepository progressRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final FindingRepository findingRepository;
    private final ReviewEngine reviewEngine;
    private final ScoringService scoringService;
    private final ObjectMapper mapper;

    public GitHubChunkedReviewService(GitHubIntegrationService github,
            GitHubReviewProgressRepository progressRepository, ProjectRepository projectRepository,
            UserRepository userRepository, ReviewRepository reviewRepository, FindingRepository findingRepository,
            ReviewEngine reviewEngine, ScoringService scoringService, ObjectMapper mapper) {
        this.github = github;
        this.progressRepository = progressRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.findingRepository = findingRepository;
        this.reviewEngine = reviewEngine;
        this.scoringService = scoringService;
        this.mapper = mapper;
    }

    @Transactional
    public void startPullRequest(String owner, String repo, int prNumber, String branch, String baseSha, String headSha,
                                 String deliveryId) {
        List<Map<String, Object>> files = github.getPullRequestFiles(owner, repo, prNumber, accessToken(owner, repo));
        if (files == null) throw new IllegalStateException("Could not retrieve the pull request changed-file list.");
        List<ChangedFile> changes = new ArrayList<>();
        boolean truncated = files.stream().anyMatch(file -> Boolean.TRUE.equals(file.get("_dataguard_truncated")));
        for (Map<String, Object> file : files) {
            if (Boolean.TRUE.equals(file.get("_dataguard_truncated"))) continue;
            changes.add(new ChangedFile(string(file.get("filename")), string(file.get("status")),
                    string(file.get("previous_filename"))));
        }
        if (truncated) changes.add(new ChangedFile(null, "api_limit", null));
        start("pull_request", owner, repo, branch, baseSha, headSha, prNumber, deliveryId, changes);
    }

    @Transactional
    public void startPush(String owner, String repo, String branch, String headSha, String deliveryId,
                          List<ChangedFile> changes) {
        start("push", owner, repo, branch, null, headSha, null, deliveryId, changes);
    }

    private void start(String eventType, String owner, String repo, String branch, String baseSha, String sha,
                       Integer prNumber, String deliveryId, List<ChangedFile> rawChanges) {
        if (owner == null || repo == null || sha == null || sha.isBlank()) {
            throw new IllegalArgumentException("Repository and commit SHA are required.");
        }
        String key = eventType + ":" + owner.toLowerCase(Locale.ROOT) + "/" + repo.toLowerCase(Locale.ROOT)
                + (prNumber == null ? "" : ":pr-" + prNumber) + ":" + sha + (baseSha == null ? "" : ":base-" + baseSha);
        if (progressRepository.findByIdempotencyKey(key).isPresent()) {
            log.info("Ignoring duplicate {} review for {}/{} @ {} (delivery {}).", eventType, owner, repo, sha, deliveryId);
            return;
        }

        List<ChangedFile> deduped = normalize(rawChanges);
        List<ChangedFile> eligible = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (ChangedFile file : deduped) {
            if (file.path == null || file.path.isBlank()) {
                skipped.add("api_limit".equals(file.status)
                        ? "PR changed-file listing reached GitHub's 3,000-file API limit; additional files may be missing."
                        : "GitHub reported a changed file without a path.");
            } else if (isSupported(file.path)) {
                eligible.add(file);
            } else {
                skipped.add("Unsupported file type: " + file.path);
            }
        }

        String token = accessToken(owner, repo);
        Project project = getOrCreateProject(owner + "/" + repo);
        Review parent = new Review();
        parent.setProject(project);
        parent.setStatus("RUNNING");
        parent = reviewRepository.save(parent);

        GitHubReviewProgress progress = new GitHubReviewProgress();
        progress.setId(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        progress.setIdempotencyKey(key);
        progress.setEventType(eventType);
        progress.setOwner(owner);
        progress.setRepository(repo);
        progress.setBranch(branch);
        progress.setBaseSha(baseSha);
        progress.setCommitSha(sha);
        progress.setPullRequestNumber(prNumber);
        progress.setFilesJson(write(eligible));
        progress.setSkippedJson(write(skipped));
        progress.setBatchResultsJson(write(List.of()));
        progress.setNextBatchIndex(0);
        progress.setStatus("PROCESSING");
        progress.setParentReviewId(parent.getId());
        progress.setCreatedAt(LocalDateTime.now());
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.saveAndFlush(progress);

        Map<String, Object> checkRun = github.createCheckRun(owner, repo, sha, "DataGuard Review",
                "in_progress", null, null, progress.getId(), null, token);
        if (checkRun == null || !(checkRun.get("id") instanceof Number id)) {
            throw new IllegalStateException("GitHub did not create a Check Run.");
        }
        progress.setActiveCheckRunId(id.longValue());
        progressRepository.save(progress);
        processBatch(progress, token);
    }

    @Transactional
    public void continueRequestedAction(String owner, String repo, Long checkRunId, String headSha,
                                        String identifier, String deliveryId) {
        if (!NEXT_BATCH_ACTION.equals(identifier) || owner == null || repo == null || checkRunId == null) {
            throw new IllegalArgumentException("Invalid continuation request.");
        }
        GitHubReviewProgress progress = progressRepository.findByActiveCheckRunIdForUpdate(checkRunId)
                .orElseThrow(() -> new IllegalArgumentException("This Check Run is not awaiting continuation."));
        if (!progress.getOwner().equalsIgnoreCase(owner) || !progress.getRepository().equalsIgnoreCase(repo)
                || !progress.getCommitSha().equals(headSha)) {
            throw new IllegalArgumentException("Continuation repository or commit does not match the review.");
        }
        if (!"WAITING_FOR_ACTION".equals(progress.getStatus())) {
            throw new IllegalArgumentException("This review is not waiting for another batch.");
        }
        List<ChangedFile> files = read(progress.getFilesJson(), FILE_LIST);
        if (progress.getNextBatchIndex() * BATCH_SIZE >= files.size()) {
            throw new IllegalArgumentException("There are no remaining review batches.");
        }

        progress.setStatus("PROCESSING");
        String token = accessToken(owner, repo);
        Map<String, Object> checkRun = github.createCheckRun(owner, repo, headSha, "DataGuard Review",
                "in_progress", null, null, progress.getId(), null, token);
        if (checkRun == null || !(checkRun.get("id") instanceof Number id)) {
            throw new IllegalStateException("GitHub did not create the next batch Check Run.");
        }
        progress.setActiveCheckRunId(id.longValue());
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
        log.info("Continuing review {} after requested action delivery {}.", progress.getId(), deliveryId);
        processBatch(progress, token);
    }

    private void processBatch(GitHubReviewProgress progress, String token) {
        List<ChangedFile> files = read(progress.getFilesJson(), FILE_LIST);
        List<String> allSkipped = read(progress.getSkippedJson(), STRING_LIST);
        List<BatchResult> results = read(progress.getBatchResultsJson(), BATCH_LIST);
        int totalBatches = (files.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        if (totalBatches == 0) {
            progress.setStatus(allSkipped.isEmpty() ? "COMPLETED" : "INCOMPLETE");
            progress.setUpdatedAt(LocalDateTime.now());
            Review parent = reviewRepository.findById(progress.getParentReviewId()).orElseThrow();
            parent.setStatus(progress.getStatus());
            parent.setCompletedAt(LocalDateTime.now());
            reviewRepository.save(parent);
            publish(progress, token, summary(progress, results, allSkipped, null), List.of(), allSkipped.isEmpty() ? "success" : "neutral");
            return;
        }

        int batchNo = progress.getNextBatchIndex() + 1;
        int from = progress.getNextBatchIndex() * BATCH_SIZE;
        List<ChangedFile> batch = files.subList(from, Math.min(files.size(), from + BATCH_SIZE));
        BatchResult result = new BatchResult();
        result.batchNumber = batchNo;
        result.totalBatches = totalBatches;
        result.filesAnalyzed = 0;
        result.skipped = new ArrayList<>();
        result.failed = new ArrayList<>();
        result.reviewIds = new ArrayList<>();
        result.renamed = new ArrayList<>();
        File dir = null;
        try {
            dir = Files.createTempDirectory("dg-gh-batch-").toFile();
            String root = dir.getCanonicalPath();
            for (ChangedFile change : batch) {
                if ("renamed".equalsIgnoreCase(change.status) && change.previousFilename != null) {
                    result.renamed.add(change.previousFilename + " -> " + change.path);
                }
                if ("removed".equalsIgnoreCase(change.status) || "deleted".equalsIgnoreCase(change.status)) {
                    result.skipped.add(change.path + " (deleted; reported without analyzing old contents)");
                    continue;
                }
                if (!safePath(change.path)) {
                    result.failed.add(change.path + " (unsafe path rejected)");
                    continue;
                }
                File dest = new File(dir, change.path);
                if (!dest.getCanonicalPath().startsWith(root + File.separator)) {
                    result.failed.add(change.path + " (path escaped source workspace)");
                    continue;
                }
                File parentDir = dest.getParentFile();
                if (parentDir != null) Files.createDirectories(parentDir.toPath());
                if (!github.downloadFileSafe(progress.getOwner(), progress.getRepository(), change.path,
                        progress.getCommitSha(), token, dest, MAX_FILE_SIZE)) {
                    result.failed.add(change.path + " (download failed, unsupported by GitHub, or exceeds 25 MB)");
                    continue;
                }
                result.filesAnalyzed++;
            }
            if (result.filesAnalyzed > 0) {
                Project project = reviewRepository.findById(progress.getParentReviewId()).orElseThrow().getProject();
                Review batchReview = reviewEngine.runReview(project, dir);
                dir = null; // ReviewEngine owns and deletes this directory.
                if (batchReview.getId() != null) result.reviewIds.add(batchReview.getId());
                if (batchReview.getAnalysisWarnings() != null) result.failed.addAll(batchReview.getAnalysisWarnings());
                if ("FAILED".equals(batchReview.getStatus()) || "INCOMPLETE".equals(batchReview.getStatus())) {
                    result.failed.add("One or more analyzers did not complete.");
                }
            }
        } catch (Exception e) {
            result.failed.add("Batch source processing failed: " + safeMessage(e));
            log.warn("Batch {} failed for review {}: {}", batchNo, progress.getId(), safeMessage(e));
        } finally {
            if (dir != null) deleteDirectory(dir);
        }

        results.add(result);
        progress.setBatchResultsJson(write(results));
        progress.setNextBatchIndex(progress.getNextBatchIndex() + 1);
        List<String> incompleteReasons = new ArrayList<>(allSkipped);
        for (BatchResult batchResult : results) incompleteReasons.addAll(batchResult.failed);
        for (BatchResult batchResult : results) incompleteReasons.addAll(batchResult.skipped);
        boolean hasMore = progress.getNextBatchIndex() < totalBatches;
        progress.setStatus(hasMore ? "WAITING_FOR_ACTION" : incompleteReasons.isEmpty() ? "COMPLETED" : "INCOMPLETE");
        progress.setUpdatedAt(LocalDateTime.now());

        Review parent = reviewRepository.findById(progress.getParentReviewId()).orElseThrow();
        List<Finding> combined = collectFindings(results);
        if (!combined.isEmpty() || results.stream().anyMatch(r -> !r.reviewIds.isEmpty())) {
            scoringService.applyScores(parent, combined);
            // Scores from a partial source set must never imply a clean 100/100 review.
            // Keep the existing scoring model while reserving perfect scores for a fully completed review.
            if (!"COMPLETED".equals(progress.getStatus())) capPerfectScores(parent);
        }
        parent.setStatus(progress.getStatus());
        if (!hasMore) parent.setCompletedAt(LocalDateTime.now());
        reviewRepository.save(parent);
        progressRepository.save(progress);

        List<Map<String, Object>> actions = hasMore ? List.of(Map.of(
                "label", "Analyze next 50", "description", "Analyze the next batch of up to 50 changed files.",
                "identifier", NEXT_BATCH_ACTION)) : List.of();
        String conclusion = hasMore || !incompleteReasons.isEmpty() ? "neutral" : "success";
        publish(progress, token, summary(progress, results, incompleteReasons, parent), actions, conclusion);
    }

    private void publish(GitHubReviewProgress progress, String token, Map<String, Object> output,
                         List<Map<String, Object>> actions, String conclusion) {
        if (progress.getActiveCheckRunId() == null) return;
        github.updateCheckRun(progress.getOwner(), progress.getRepository(), progress.getActiveCheckRunId(),
                "completed", conclusion, output, actions, token);
    }

    private Map<String, Object> summary(GitHubReviewProgress progress, List<BatchResult> batches,
                                         List<String> reasons, Review parent) {
        int eligible = read(progress.getFilesJson(), FILE_LIST).size();
        int analyzed = batches.stream().mapToInt(b -> b.filesAnalyzed).sum();
        int skipped = read(progress.getSkippedJson(), STRING_LIST).size()
                + batches.stream().mapToInt(b -> b.skipped.size()).sum();
        int failed = batches.stream().mapToInt(b -> b.failed.size()).sum();
        List<Finding> findings = collectFindings(batches);
        Map<String, Long> severity = new LinkedHashMap<>();
        for (String name : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO")) {
            severity.put(name, findings.stream().filter(f -> name.equalsIgnoreCase(f.getSeverity())).count());
        }
        StringBuilder text = new StringBuilder("### DataGuard changed-file review\n\n")
                .append("- Event: ").append(progress.getEventType()).append("\n")
                .append("- Repository: ").append(progress.getOwner()).append('/').append(progress.getRepository()).append("\n")
                .append("- Commit: `").append(progress.getCommitSha()).append("`\n")
                .append("- Batch: ").append(batches.isEmpty() ? 0 : batches.get(batches.size() - 1).batchNumber)
                .append("/").append((eligible + BATCH_SIZE - 1) / BATCH_SIZE).append("\n")
                .append("- Total eligible changed files: ").append(eligible).append("\n")
                .append("- Files analyzed: ").append(analyzed).append("\n")
                .append("- Files skipped: ").append(skipped).append("\n")
                .append("- Files/checks failed: ").append(failed).append("\n")
                .append("- Findings by severity: ").append(severity).append("\n\n");
        List<String> renames = batches.stream().flatMap(b -> b.renamed.stream()).toList();
        if (!renames.isEmpty()) {
            text.append("#### Renamed files\n");
            renames.forEach(rename -> text.append("- ").append(rename).append("\n"));
            text.append("\n");
        }
        if (parent != null && parent.getOverallScore() != null) {
            text.append("| Metric | ").append("COMPLETED".equals(progress.getStatus()) ? "Combined score" : "Provisional score (analyzed files only)")
                    .append(" |\n|---|---:|\n")
                    .append("| Overall | ").append(parent.getOverallScore()).append("/100 |\n")
                    .append("| Security | ").append(parent.getSecurityScore()).append("/100 |\n")
                    .append("| Quality | ").append(parent.getQualityScore()).append("/100 |\n")
                    .append("| Architecture | ").append(parent.getArchitectureScore()).append("/100 |\n\n");
        } else {
            text.append("No score is available because no source file completed analysis.\n\n");
        }
        if (!reasons.isEmpty()) {
            text.append("> **INCOMPLETE REVIEW** — scores reflect findings collected so far; unprocessed or failed files are not considered clean.\n\n")
                    .append("#### Incomplete-review details\n");
            reasons.stream().distinct().forEach(reason -> text.append("- ").append(reason).append("\n"));
            text.append("\n");
        }
        if ("WAITING_FOR_ACTION".equals(progress.getStatus())) {
            text.append("> **PARTIAL REVIEW** — only completed batches are included in these provisional scores.\n\n");
            text.append("Select **Analyze next 50** to continue. No later batch has been analyzed yet.\n\n");
        } else if ("COMPLETED".equals(progress.getStatus())) {
            text.append("All eligible changed files were processed. A completed review is not a guarantee that the code is safe.\n\n");
        }

        List<Map<String, Object>> annotations = new ArrayList<>();
        for (Finding finding : findings) {
            if (annotations.size() == 50) break;
            if (finding.getFilePath() == null) continue;
            int line = finding.getLineNumber() == null || finding.getLineNumber() < 1 ? 1 : finding.getLineNumber();
            String level = "CRITICAL".equalsIgnoreCase(finding.getSeverity()) || "HIGH".equalsIgnoreCase(finding.getSeverity())
                    ? "failure" : "MEDIUM".equalsIgnoreCase(finding.getSeverity()) ? "warning" : "notice";
            annotations.add(Map.of("path", finding.getFilePath(), "start_line", line, "end_line", line,
                    "annotation_level", level, "title", annotationTitle(finding),
                    "message", annotationMessage(finding)));
        }
        if (findings.size() > 50) text.append("Only 50 annotations are included due to the GitHub Check Run limit; counts and scores include all findings.\n");
        return Map.of("title", "DataGuard changed-file review", "summary", text.toString(), "annotations", annotations);
    }

    private List<Finding> collectFindings(List<BatchResult> batches) {
        List<Finding> all = new ArrayList<>();
        for (BatchResult batch : batches) for (Long id : batch.reviewIds) all.addAll(findingRepository.findByReviewId(id));
        return all;
    }

    private void capPerfectScores(Review review) {
        if (review.getOverallScore() != null && review.getOverallScore() >= 100) review.setOverallScore(99);
        if (review.getSecurityScore() != null && review.getSecurityScore() >= 100) review.setSecurityScore(99);
        if (review.getQualityScore() != null && review.getQualityScore() >= 100) review.setQualityScore(99);
        if (review.getArchitectureScore() != null && review.getArchitectureScore() >= 100) review.setArchitectureScore(99);
    }

    private String annotationTitle(Finding finding) {
        String source = finding.getSource() == null || finding.getSource().isBlank() ? "DataGuard" : finding.getSource();
        return source + ": " + Optional.ofNullable(finding.getTitle()).orElse("Issue");
    }

    private String annotationMessage(Finding finding) {
        String message = Optional.ofNullable(finding.getDescription()).orElse("DataGuard finding");
        if (finding.getRecommendation() != null && !finding.getRecommendation().isBlank()) {
            message += " Recommendation: " + finding.getRecommendation();
        }
        return message;
    }

    private String accessToken(String owner, String repo) {
        Long installation = github.getInstallationIdForRepository(owner, repo);
        if (installation == null) throw new IllegalStateException("GitHub App is not installed for this repository.");
        String token = github.getInstallationAccessToken(installation);
        if (token == null || token.isBlank()) throw new IllegalStateException("Could not obtain a GitHub installation token.");
        return token;
    }

    private Project getOrCreateProject(String name) {
        return projectRepository.findByName(name).orElseGet(() -> {
            User user = userRepository.findByEmail("github-bot@dataguard.local").orElseGet(() -> {
                User bot = new User(); bot.setEmail("github-bot@dataguard.local"); bot.setFullName("GitHub Bot");
                bot.setRole(User.Role.DEVELOPER); bot.setPassword("botpassword"); return userRepository.save(bot);
            });
            Project project = new Project(); project.setName(name); project.setTechnology("UNKNOWN");
            project.setUser(user); project.setCreatedAt(LocalDateTime.now()); return projectRepository.save(project);
        });
    }

    public static List<List<ChangedFile>> partition(List<ChangedFile> files) {
        List<List<ChangedFile>> batches = new ArrayList<>();
        for (int start = 0; start < files.size(); start += BATCH_SIZE) {
            batches.add(List.copyOf(files.subList(start, Math.min(files.size(), start + BATCH_SIZE))));
        }
        return batches;
    }

    private List<ChangedFile> normalize(List<ChangedFile> changes) {
        Map<String, ChangedFile> unique = new LinkedHashMap<>();
        if (changes != null) for (ChangedFile item : changes) {
            if (item == null || item.path == null) { unique.putIfAbsent("", new ChangedFile("", "unknown", null)); continue; }
            unique.put(item.path, item);
        }
        List<ChangedFile> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(f -> f.path == null ? "" : f.path));
        return result;
    }

    private boolean isSupported(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".jsx")
                || "pom.xml".equals(lower);
    }

    private boolean safePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.startsWith("\\") || path.contains("\\")) return false;
        for (String part : path.split("/")) if (part.equals("..") || part.equals(".")) return false;
        return true;
    }

    public static class ChangedFile {
        public String path;
        public String status;
        public String previousFilename;
        public ChangedFile() {}
        public ChangedFile(String path, String status, String previousFilename) {
            this.path = path; this.status = status; this.previousFilename = previousFilename;
        }
    }

    public static class BatchResult {
        public int batchNumber;
        public int totalBatches;
        public int filesAnalyzed;
        public List<String> skipped;
        public List<String> failed;
        public List<Long> reviewIds;
        public List<String> renamed;
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Could not persist GitHub review state", e); }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("Could not read saved GitHub review state", e); }
    }

    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static void deleteDirectory(File dir) {
        try (var paths = Files.walk(dir.toPath())) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
        } catch (Exception ignored) { }
    }
}
