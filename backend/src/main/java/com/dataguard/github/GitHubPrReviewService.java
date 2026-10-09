package com.dataguard.github;

import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.repository.ProjectRepository;
import com.dataguard.repository.UserRepository;
import com.dataguard.repository.FindingRepository;
import com.dataguard.service.ReviewEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

@Service
public class GitHubPrReviewService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPrReviewService.class);

    private final GitHubIntegrationService githubService;
    private final ReviewEngine reviewEngine;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FindingRepository findingRepository;

    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB // 5 MB

    public GitHubPrReviewService(GitHubIntegrationService githubService, ReviewEngine reviewEngine, ProjectRepository projectRepository, UserRepository userRepository, FindingRepository findingRepository) {
        this.githubService = githubService;
        this.reviewEngine = reviewEngine;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.findingRepository = findingRepository;
    }

    public void processPullRequest(String owner, String repo, int prNumber, String headSha) {
        log.info("processPullRequest called for {}/{} PR #{} sha: {}", owner, repo, prNumber, headSha);
        
        Long installationId = null;
        try {
            log.info("Retrieving installation ID for {}/{}", owner, repo);
            installationId = githubService.getInstallationIdForRepository(owner, repo);
        } catch (Exception e) {
            log.error("Failed getting installationId: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        if (installationId == null) {
            log.error("No installation ID for {}/{}", owner, repo);
            return;
        }

        String token = null;
        try {
            log.info("Retrieving installation token for ID {}", installationId);
            token = githubService.getInstallationAccessToken(installationId);
        } catch (Exception e) {
            log.error("Failed getting token: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        if (token == null) {
            log.error("Failed to get installation token for {}/{}", owner, repo);
            return;
        }

        // Create Check Run (InProgress)
        Map<String, Object> checkRun = null;
        try {
            log.info("Creating 'in_progress' Check Run on GitHub");
            checkRun = githubService.createCheckRun(owner, repo, headSha, "DataGuard PR Review", "in_progress", null, null, token);
        } catch (Exception e) {
            log.error("Failed creating Check Run: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
        
        Long checkRunId = checkRun != null && checkRun.containsKey("id") ? ((Number) checkRun.get("id")).longValue() : null;
        if (checkRunId != null) {
            log.info("Successfully created Check Run with ID: {}", checkRunId);
        } else {
            log.warn("Check Run creation returned no ID.");
        }

        File tempDir = null;
        try {
            List<String> incompleteReasons = new ArrayList<>();
            tempDir = Files.createTempDirectory("pr-" + owner + "-" + repo + "-" + prNumber).toFile();
            String tempDirPath = tempDir.getCanonicalPath();

            // Download PR files
            log.info("Retrieving PR metadata/files for PR #{}", prNumber);
            List<Map<String, Object>> files = githubService.getPullRequestFiles(owner, repo, prNumber, token);
            if (files != null) {
                log.info("Retrieved {} files from PR. Starting source materialization into {}", files.size(), tempDirPath);
                if (files.size() >= 3000) {
                    incompleteReasons.add("GitHub's 3,000-file changed-files API limit was reached; the changed-file list may be truncated.");
                }
                int downloadedSourceFiles = 0;
                for (Map<String, Object> file : files) {
                    String filename = (String) file.get("filename");
                    String status = (String) file.get("status");
                    if ("removed".equals(status)) continue;

                    if (filename == null || filename.isBlank()) {
                        incompleteReasons.add("GitHub returned a changed file without a path.");
                        continue;
                    }
                    
                    if (new File(filename).isAbsolute()) {
                        log.warn("Absolute path detected, skipping file: {}", filename);
                        continue;
                    }
                    File dest = new File(tempDir, filename);
                    if (!dest.getCanonicalPath().startsWith(tempDirPath)) {
                        log.warn("Path traversal detected, skipping file: {}", filename);
                        continue;
                    }

                    dest.getParentFile().mkdirs();
                    boolean downloaded = githubService.downloadFileSafe(owner, repo, filename, headSha, token, dest, MAX_FILE_SIZE);
                    if (!downloaded) {
                        String reason = "Could not download (unsupported by GitHub, oversized, or request failed): " + filename;
                        incompleteReasons.add(reason);
                        log.warn("{}", reason);
                        continue;
                    }
                    if (isAnalyzedSource(filename)) {
                        downloadedSourceFiles++;
                        if (!isValidUtf8(dest)) {
                            incompleteReasons.add("Could not decode source as UTF-8: " + filename);
                        }
                    } else {
                        incompleteReasons.add("No analyzer supports this changed file type: " + filename);
                    }
                }
                if (downloadedSourceFiles == 0) {
                    incompleteReasons.add("No supported source files were available for analysis.");
                }
                log.info("Source materialization completed.");
            } else {
                log.warn("No files retrieved for PR #{}", prNumber);
                incompleteReasons.add("GitHub did not return the changed-file list; source was not verified.");
            }

            // Get or create Project
            String projectName = owner + "/" + repo;
            Optional<Project> optProject = projectRepository.findByName(projectName);
            Project project;
            if (optProject.isPresent()) {
                project = optProject.get();
            } else {
                User botUser = getOrCreateBotUser();
                project = new Project();
                project.setName(projectName);
                project.setTechnology("UNKNOWN");
                project.setUser(botUser);
                project.setCreatedAt(LocalDateTime.now());
                project = projectRepository.save(project);
            }

            // Run Review
            log.info("Starting ReviewEngine.runReview(...)");
            Review review = reviewEngine.runReview(project, tempDir);
            log.info("ReviewEngine completed. Overall Score: {}", review.getOverallScore());
            if (review.getAnalysisWarnings() != null && !review.getAnalysisWarnings().isEmpty()) {
                incompleteReasons.addAll(review.getAnalysisWarnings());
            } else if ("INCOMPLETE".equals(review.getStatus()) || "FAILED".equals(review.getStatus())) {
                incompleteReasons.add("One or more analyzers failed; details are unavailable.");
            }

            // Fetch Findings
            List<com.dataguard.entity.Finding> findings = findingRepository.findByReviewId(review.getId());

            // Update Check Run with results
            if (checkRunId != null) {
                String conclusion = !incompleteReasons.isEmpty() ? "neutral"
                        : review.getOverallScore() != null && review.getOverallScore() >= 50 ? "success" : "failure";
                Map<String, Object> output = createCheckRunOutput(review, findings, incompleteReasons);
                githubService.updateCheckRun(owner, repo, checkRunId, "completed", conclusion, output, token);
            }

        } catch (Exception e) {
            log.error("Failed to process PR", e);
            if (checkRunId != null) {
                githubService.updateCheckRun(owner, repo, checkRunId, "completed", "failure", Map.of("title", "Review Failed", "summary", "An error occurred: " + e.getMessage()), token);
            }
        } finally {
            if (tempDir != null) {
                // cleanup if needed
            }
        }
    }

    private Map<String, Object> createCheckRunOutput(Review review, List<com.dataguard.entity.Finding> findings,
                                                     List<String> incompleteReasons) {
        StringBuilder summary = new StringBuilder();
        summary.append("### DataGuard Review Results\n\n");
        summary.append("| Metric | Score |\n");
        summary.append("|--------|-------|\n");
        summary.append("| **Overall** | ").append(review.getOverallScore() != null ? review.getOverallScore() : "N/A").append("/100 |\n");
        summary.append("| **Security** | ").append(review.getSecurityScore() != null ? review.getSecurityScore() : "N/A").append("/100 |\n");
        summary.append("| **Quality** | ").append(review.getQualityScore() != null ? review.getQualityScore() : "N/A").append("/100 |\n");
        summary.append("| **Architecture** | ").append(review.getArchitectureScore() != null ? review.getArchitectureScore() : "N/A").append("/100 |\n\n");
        if (incompleteReasons != null && !incompleteReasons.isEmpty()) {
            summary.append("> **INCOMPLETE REVIEW** — scores below only reflect the files and analyzers that completed.\n\n");
            summary.append("#### Files or checks not fully analyzed\n");
            incompleteReasons.stream().distinct().forEach(reason -> summary.append("- ").append(reason).append("\n"));
            summary.append("\n");
        }

        int critical = 0, high = 0, medium = 0, low = 0;
        List<Map<String, Object>> annotations = new ArrayList<>();
        
        if (findings != null) {
            for (com.dataguard.entity.Finding finding : findings) {
                String severity = finding.getSeverity() != null ? finding.getSeverity().toUpperCase() : "INFO";
                switch (severity) {
                    case "CRITICAL": critical++; break;
                    case "HIGH": high++; break;
                    case "MEDIUM": medium++; break;
                    default: low++; break;
                }

                if (annotations.size() >= 50) continue; // GitHub Check Runs API limit is 50 annotations per request

                String ghLevel = "notice";
                if (severity.equals("CRITICAL") || severity.equals("HIGH")) {
                    ghLevel = "failure";
                } else if (severity.equals("MEDIUM")) {
                    ghLevel = "warning";
                }

                if (finding.getFilePath() == null) continue;

                int line = finding.getLineNumber() != null && finding.getLineNumber() > 0 ? finding.getLineNumber() : 1;

                Map<String, Object> ann = new java.util.HashMap<>();
                ann.put("path", finding.getFilePath());
                ann.put("start_line", line);
                ann.put("end_line", line);
                ann.put("annotation_level", ghLevel);
                ann.put("title", finding.getTitle() != null ? finding.getTitle() : "Issue");
                ann.put("message", finding.getDescription() != null ? finding.getDescription() : "DataGuard found an issue");
                
                annotations.add(ann);
            }
            
            summary.append("#### Findings Summary\n");
            summary.append("- CRITICAL: ").append(critical).append("\n");
            summary.append("- HIGH: ").append(high).append("\n");
            summary.append("- MEDIUM: ").append(medium).append("\n");
            summary.append("- LOW: ").append(low).append("\n\n");
            
            if (findings.size() > 50) {
                summary.append("\n*Note: Only the first 50 findings are shown as annotations due to GitHub API limits. Check the DataGuard dashboard for the full report.*\n");
            }
        }
        
        return Map.of(
            "title", "DataGuard Review Completed",
            "summary", summary.toString(),
            "annotations", annotations
        );
    }

    private boolean isAnalyzedSource(String filename) {
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".jsx")
                || lower.endsWith("/pom.xml") || "pom.xml".equals(lower);
    }

    private boolean isValidUtf8(File file) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file.toPath())));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private User getOrCreateBotUser() {
        String email = "github-bot@dataguard.local";
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setFullName("GitHub Bot");
            user.setRole(User.Role.DEVELOPER);
            user.setPassword("botpassword"); 
            return userRepository.save(user);
        });
    }
}
