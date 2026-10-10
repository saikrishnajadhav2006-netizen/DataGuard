package com.dataguard.service;

import com.dataguard.dto.FixApplyResponse;
import com.dataguard.dto.FixApprovalRequest;
import com.dataguard.dto.FixResponse;
import com.dataguard.entity.Finding;
import com.dataguard.entity.GitHubReviewProgress;
import com.dataguard.repository.GitHubReviewProgressRepository;
import com.dataguard.github.GitHubIntegrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rule-based, reviewable fixes. Repository changes require an explicit approval request. */
@Service
public class FixSuggestionService {
    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    private static final Pattern SQLITE_EXECUTE = Pattern.compile(
            "^(?<indent>\\s*)(?<cursor>[A-Za-z_]\\w*)\\.execute\\(\\s*\\\"(?<sql>SELECT\\b.*?\\bWHERE\\s+[\\w.]+\\s*=\\s*)'\\\"\\s*\\+\\s*(?<param>username|user_name|user_id|email)\\s*\\+\\s*\\\"'\\\"\\s*\\)$",
            Pattern.CASE_INSENSITIVE);

    private final GitHubReviewProgressRepository progressRepository;
    private final GitHubIntegrationService github;
    private final ObjectMapper mapper;

    public FixSuggestionService(GitHubReviewProgressRepository progressRepository,
                                GitHubIntegrationService github, ObjectMapper mapper) {
        this.progressRepository = progressRepository;
        this.github = github;
        this.mapper = mapper;
    }

    public FixResponse suggest(Finding finding) {
        validateFindingPath(finding);
        SqlFix sqlFix = sqlFix(finding);
        if (sqlFix == null) {
            String suggestion = previousRuleSuggestion(finding);
            String reason = "No safe automatic patch is available for this finding. Review the suggestion and apply changes manually.";
            if ("sec-python-sql-concat".equals(finding.getRuleId())) {
                suggestion = finding.getRecommendation();
                reason = "This SQL construction needs surrounding query/driver context; no automatic patch is safe. Apply a parameterized query manually.";
            }
            return response(finding, suggestion, null, false, reason, null);
        }

        GitHubReviewProgress progress = findReviewContext(finding);
        if (progress == null) {
            return response(finding, sqlFix.replacement, diff(finding, sqlFix), false,
                    "A patch is available to inspect, but this review has no linked GitHub branch/commit context for safe application.", null);
        }
        try {
            validatePath(finding.getFilePath());
            String token = accessToken(progress);
            boolean applicable = validateCurrentSource(finding, sqlFix, progress, token);
            String reason = applicable ? null : "Current source, commit, or SQLite driver could not be verified. Inspect and apply manually.";
            return response(finding, sqlFix.replacement, diff(finding, sqlFix), applicable, reason, progress);
        } catch (Exception e) {
            return response(finding, sqlFix.replacement, diff(finding, sqlFix), false,
                    "Could not verify the GitHub source context. No repository changes were made.", progress);
        }
    }

    public FixApplyResponse apply(Finding finding, FixApprovalRequest approval) {
        if (approval == null || !approval.approved()) {
            throw new IllegalArgumentException("Explicit approval is required before creating a fix branch and pull request.");
        }
        FixResponse proposal = suggest(finding);
        if (!proposal.safeToApply()) throw new IllegalStateException(proposal.reason());
        if (!same(proposal.repository(), approval.repository())
                || !same(proposal.branch(), approval.branch())
                || !same(proposal.commitSha(), approval.commitSha())) {
            throw new IllegalStateException("The approved repository, branch, or commit is stale or does not match this finding.");
        }

        GitHubReviewProgress progress = findReviewContext(finding);
        if (progress == null) throw new IllegalStateException("No GitHub review context exists for this finding.");
        String token = accessToken(progress);
        if (!progress.getCommitSha().equals(github.getBranchHeadSha(progress.getOwner(), progress.getRepository(), progress.getBranch(), token))) {
            throw new IllegalStateException("The reviewed branch moved after the proposal was generated.");
        }
        SqlFix sqlFix = sqlFix(finding);
        String source = fetchSource(progress, finding.getFilePath(), token);
        String changed = replaceReviewedLine(finding, sqlFix, source);
        String fileSha = github.getFileSha(progress.getOwner(), progress.getRepository(), finding.getFilePath(),
                progress.getCommitSha(), token);
        if (fileSha == null) throw new IllegalStateException("GitHub could not verify the reviewed file version.");

        String newBranch = "dataguard-fix-" + finding.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        if (!github.createBranch(progress.getOwner(), progress.getRepository(), newBranch, progress.getCommitSha(), token)) {
            throw new IllegalStateException("GitHub did not confirm creation of the fix branch.");
        }
        String encoded = Base64.getEncoder().encodeToString(changed.getBytes(StandardCharsets.UTF_8));
        if (!github.updateFileOnBranch(progress.getOwner(), progress.getRepository(), finding.getFilePath(),
                "DataGuard: parameterize SQL input", encoded, newBranch, fileSha, token)) {
            throw new IllegalStateException("GitHub did not confirm the patch on the fix branch.");
        }
        String baseBranch = progress.getBranch();
        if ("pull_request".equals(progress.getEventType())) {
            if (progress.getPullRequestNumber() == null) throw new IllegalStateException("The original pull request context is missing.");
            var originalPr = github.getPullRequest(progress.getOwner(), progress.getRepository(), progress.getPullRequestNumber(), token);
            Object base = originalPr == null ? null : originalPr.get("base");
            if (!(base instanceof Map<?, ?> baseMap) || !(baseMap.get("ref") instanceof String ref) || ref.isBlank()) {
                throw new IllegalStateException("GitHub could not verify the original pull request base branch.");
            }
            baseBranch = ref;
        }
        var pullRequest = github.createPullRequest(progress.getOwner(), progress.getRepository(),
                "Fix potential SQL injection in " + finding.getFilePath(),
                "This proposed fix parameterizes the reviewed SQL input. Review and test the change before merging.\n\n"
                        + "DataGuard finding: #" + finding.getId() + " at " + finding.getFilePath() + ":" + finding.getLineNumber(),
                newBranch, baseBranch, token);
        if (pullRequest == null || !(pullRequest.get("number") instanceof Number number)
                || !(pullRequest.get("html_url") instanceof String url)) {
            throw new IllegalStateException("GitHub did not confirm creation of the fix pull request.");
        }
        return new FixApplyResponse(true, newBranch, number.intValue(), url,
                "GitHub created a fix branch and pull request. The repository's default branch was not modified.");
    }

    private boolean validateCurrentSource(Finding finding, SqlFix fix, GitHubReviewProgress progress, String token) throws Exception {
        if (progress.getBranch() == null || progress.getBranch().isBlank()
                || !progress.getCommitSha().equals(github.getBranchHeadSha(progress.getOwner(), progress.getRepository(), progress.getBranch(), token))) {
            return false;
        }
        String source = fetchSource(progress, finding.getFilePath(), token);
        return source != null && source.matches("(?s).*\\b(?:import\\s+sqlite3|from\\s+sqlite3\\s+import)\\b.*")
                && reviewedLineMatches(finding, source) && fix != null;
    }

    private String fetchSource(GitHubReviewProgress progress, String path, String token) {
        validatePath(path);
        File temp = null;
        try {
            temp = Files.createTempFile("dataguard-fix-", ".py").toFile();
            if (!github.downloadFileSafe(progress.getOwner(), progress.getRepository(), path, progress.getCommitSha(),
                    token, temp, MAX_FILE_BYTES) || temp.length() > MAX_FILE_BYTES) return null;
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(temp.toPath()))).toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp.toPath()); } catch (Exception ignored) { }
        }
    }

    private String replaceReviewedLine(Finding finding, SqlFix fix, String source) {
        validateFindingPath(finding);
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES || fix == null) {
            throw new IllegalStateException("The source file is missing, too large, or has no safe supported patch.");
        }
        String[] lines = source.split("\\R", -1);
        int index = finding.getLineNumber() - 1;
        if (index < 0 || index >= lines.length || !lines[index].equals(finding.getEvidence())) {
            throw new IllegalStateException("The finding no longer matches the source at the reviewed commit.");
        }
        lines[index] = fix.replacement;
        return String.join("\n", lines);
    }

    private boolean reviewedLineMatches(Finding finding, String source) {
        try {
            String[] lines = source.split("\\R", -1);
            int index = finding.getLineNumber() - 1;
            return index >= 0 && index < lines.length && lines[index].equals(finding.getEvidence());
        } catch (Exception e) { return false; }
    }

    private SqlFix sqlFix(Finding finding) {
        if (!"sec-python-sql-concat".equals(finding.getRuleId()) || finding.getEvidence() == null
                || finding.getEvidence().length() > 500) return null;
        Matcher matcher = SQLITE_EXECUTE.matcher(finding.getEvidence());
        if (!matcher.matches()) return null;
        String sql = matcher.group("sql").replaceAll("\\s+$", "") + " ?";
        String replacement = matcher.group("indent") + matcher.group("cursor") + ".execute(\""
                + sql.replace("\\", "\\\\").replace("\"", "\\\"") + "\", ("
                + matcher.group("param") + ",))";
        return new SqlFix(replacement);
    }

    private String previousRuleSuggestion(Finding finding) {
        String title = finding.getTitle() == null ? "" : finding.getTitle().toLowerCase(java.util.Locale.ROOT);
        if (title.contains("debug output")) {
            return "Replace console output with the project's configured structured logger.";
        }
        if (title.contains("empty catch")) {
            return "Handle the exception explicitly by logging it or returning a controlled error response.";
        }
        if (title.contains("hardcoded")) {
            return "Read the value from an environment variable or secrets manager instead of source code.";
        }
        return finding.getRecommendation();
    }

    private FixResponse response(Finding finding, String suggested, String patch, boolean safe,
                                 String reason, GitHubReviewProgress progress) {
        return new FixResponse(finding.getId(), safe ? "Use a SQLite parameter placeholder and pass username as a bound parameter." :
                (suggested == null ? finding.getRecommendation() : "Rule-based suggestion; inspect and apply manually."),
                finding.getEvidence(), suggested, safe, patch, finding.getFilePath(), finding.getLineNumber(),
                progress == null ? null : progress.getOwner() + "/" + progress.getRepository(),
                progress == null ? null : progress.getBranch(), progress == null ? null : progress.getCommitSha(), reason);
    }

    private String diff(Finding finding, SqlFix fix) {
        if (fix == null) return null;
        return "--- a/" + finding.getFilePath() + "\n+++ b/" + finding.getFilePath() + "\n@@ -"
                + finding.getLineNumber() + ",1 +" + finding.getLineNumber() + ",1 @@\n-"
                + finding.getEvidence() + "\n+" + fix.replacement + "\n";
    }

    private GitHubReviewProgress findReviewContext(Finding finding) {
        if (finding.getReview() == null || finding.getReview().getId() == null) return null;
        for (GitHubReviewProgress progress : progressRepository.findAll()) {
            try {
                JsonNode batches = mapper.readTree(progress.getBatchResultsJson());
                for (JsonNode batch : batches) {
                    JsonNode reviewIds = batch.path("reviewIds");
                    for (JsonNode reviewId : reviewIds) if (reviewId.asLong(-1) == finding.getReview().getId()) return progress;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private String accessToken(GitHubReviewProgress progress) {
        Long installation = github.getInstallationIdForRepository(progress.getOwner(), progress.getRepository());
        String token = installation == null ? null : github.getInstallationAccessToken(installation);
        if (token == null || token.isBlank()) throw new IllegalStateException("GitHub installation access is unavailable.");
        return token;
    }

    private void validateFindingPath(Finding finding) {
        if (finding == null || finding.getId() == null || finding.getLineNumber() == null || finding.getLineNumber() < 1
                ) {
            throw new IllegalArgumentException("Finding location or evidence is invalid.");
        }
        validatePath(finding.getFilePath());
    }

    private void validatePath(String path) {
        if (path == null || path.isBlank() || path.length() > 512 || path.startsWith("/") || path.startsWith("\\")
                || path.contains("\\") || path.contains(":") || path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Finding file path is invalid.");
        }
        for (String part : path.split("/")) if (part.isBlank() || part.equals(".") || part.equals("..")) {
            throw new IllegalArgumentException("Finding file path is invalid.");
        }
    }

    private static boolean same(String left, String right) { return left != null && left.equals(right); }
    private record SqlFix(String replacement) { }
}
