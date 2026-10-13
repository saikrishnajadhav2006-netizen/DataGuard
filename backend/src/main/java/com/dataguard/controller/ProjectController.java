package com.dataguard.controller;

import com.dataguard.dto.FixResponse;
import com.dataguard.dto.ReviewResponse;
import com.dataguard.entity.Finding;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ReviewRepository;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.repository.UserRepository;
import com.dataguard.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final FindingRepository findingRepository;
    private final ReviewRepository reviewRepository;

    @Value("${dataguard.storage.directory:${java.io.tmpdir}}")
    private String storageDirectory = System.getProperty("java.io.tmpdir");

    @PostMapping("/upload")
    public ResponseEntity<?> uploadProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(401).body(Map.of("message", "Please sign in before uploading a project."));
        }
        String email = authenticationEmail(authentication);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Your account was not found. Please sign in again."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Choose a non-empty ZIP file to review."));
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a project name."));
        }
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only ZIP files are supported."));
        }

        try {
            Review reviewResult = projectService.processProjectUpload(file, user, name.trim());
            return ResponseEntity.ok(reviewResponse(reviewResult));
        } catch (ProjectService.InvalidArchiveException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "The server could not read or store the uploaded ZIP."));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(503)
                    .body(Map.of("message", exception.getMessage() == null
                            ? "The configured review engine is unavailable. Check backend logs." : exception.getMessage()));
        }
    }


    private ReviewResponse reviewResponse(Review review) {
        Path root = Path.of(storageDirectory, "dataguard_" + review.getProject().getId()).toAbsolutePath().normalize();
        int files = 0;
        long lines = 0;
        if (Files.isDirectory(root)) {
            try (var paths = Files.walk(root)) {
                List<Path> allFiles = paths.filter(Files::isRegularFile)
                        .map(path -> path.toAbsolutePath().normalize())
                        .filter(path -> path.startsWith(root)).toList();
                files = allFiles.size();
                for (Path path : allFiles) {
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (!name.matches(".*\\.(java|js|jsx|ts|tsx|py|html|css|json|ya?ml|xml|sql|md)$")) continue;
                    try (var reader = Files.lines(path, StandardCharsets.UTF_8)) {
                        lines += reader.count();
                    } catch (IOException ignored) {
                        // A single unreadable source file should not hide the overall review results.
                    }
                }
            } catch (IOException ignored) {
                // Keep metrics at zero when the stored review workspace is unavailable.
            }
        }
        return ReviewResponse.from(review, findingRepository.findByReviewId(review.getId()), files, lines);
    }

    private String authenticationEmail(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String email = jwtAuthentication.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return authentication.getName();
    }

    @GetMapping("/reviews")
    public ResponseEntity<?> listReviews(Authentication authentication) {
        String email = authenticationEmail(authentication);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).body("Please sign in again.");
        var reviews = reviewRepository.findAllForUser(user.getId()).stream()
                .map(this::reviewResponse)
                .toList();
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/reviews/{reviewId}/download")
    public ResponseEntity<StreamingResponseBody> downloadReviewedProject(@PathVariable Long reviewId, Authentication authentication) {
        var review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) return ResponseEntity.notFound().build();
        var project = review.getProject();
        Long projectId = project.getId();
        if (!project.getUser().getEmail().equalsIgnoreCase(authenticationEmail(authentication))) {
            return ResponseEntity.status(403).build();
        }
        Path root = Path.of(storageDirectory, "dataguard_" + projectId).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return ResponseEntity.status(410).build();
        }
        String safeName = project.getName() == null ? "dataguard-project" : project.getName().replaceAll("[^A-Za-z0-9_-]", "-");
        StreamingResponseBody stream = output -> {
            try (ZipOutputStream zip = new ZipOutputStream(output); var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    Path normalized = path.toAbsolutePath().normalize();
                    if (!normalized.startsWith(root)) continue;
                    String entryName = root.relativize(normalized).toString().replace((char)92, '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(normalized, zip);
                    zip.closeEntry();
                }
                zip.finish();
            }
        };
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "-reviewed.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(stream);
    }
    @PostMapping("/reviews/{reviewId}/findings/{findingId}/fix")
    public ResponseEntity<?> suggestFix(
            @PathVariable Long reviewId,
            @PathVariable Long findingId,
            Authentication authentication) {
        return findingRepository.findById(findingId)
                .filter(finding -> finding.getReview().getId().equals(reviewId))
                .map(finding -> createFixResponse(finding, authentication))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> createFixResponse(Finding finding, Authentication authentication) {
        if (!finding.getReview().getProject().getUser().getEmail().equals(authenticationEmail(authentication))) {
            return ResponseEntity.status(403).body("You do not have access to this finding.");
        }

        String suggestedCode;
        String description;
        if ("System.out.println used".equals(finding.getTitle())) {
            suggestedCode = "private static final Logger log = LoggerFactory.getLogger(CurrentClass.class);\n\nlog.info(\"Replace with structured logging\");";
            description = "Replace standard output with structured SLF4J logging and add the logger declaration to the class.";
        } else if ("Empty catch block".equals(finding.getTitle())) {
            suggestedCode = "catch (Exception exception) {\n    log.error(\"Operation failed\", exception);\n}";
            description = "Handle the exception explicitly by logging it or returning a controlled error.";
        } else {
            suggestedCode = finding.getRecommendation();
            description = "Use the recommendation as a reviewable manual fix.";
        }

        return ResponseEntity.ok(new FixResponse(
                finding.getId(), description, finding.getEvidence(), suggestedCode, false));
    }

    @PostMapping("/reviews/{reviewId}/findings/{findingId}/apply")
    @Transactional
    public ResponseEntity<?> applySafeFix(
            @PathVariable Long reviewId,
            @PathVariable Long findingId,
            Authentication authentication) {
        Finding finding = findingRepository.findById(findingId).orElse(null);
        if (finding == null || finding.getReview() == null
                || !finding.getReview().getId().equals(reviewId)) {
            return ResponseEntity.notFound().build();
        }
        if (!finding.getReview().getProject().getUser().getEmail()
                .equalsIgnoreCase(authenticationEmail(authentication))) {
            return ResponseEntity.status(403).body(Map.of("message", "You do not have access to this finding."));
        }
        if ("RESOLVED".equalsIgnoreCase(finding.getStatus())) {
            return ResponseEntity.ok(Map.of("message", "This finding is already marked resolved.", "status", "RESOLVED"));
        }

        Path root = Path.of(storageDirectory,
                "dataguard_" + finding.getReview().getProject().getId()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return ResponseEntity.status(410).body(Map.of("message", "The uploaded project files are no longer available. Upload the ZIP again."));
        }
        try {
            // New findings store a root-relative path, which safely handles duplicate filenames.
            // Historical reviews stored only the basename; keep a guarded fallback for those rows.
            Path source = root.resolve(finding.getFilePath()).normalize();
            if (!source.startsWith(root) || !Files.isRegularFile(source)) {
                String basename = Path.of(finding.getFilePath()).getFileName().toString();
                try (var paths = Files.walk(root)) {
                    List<Path> matches = paths.filter(Files::isRegularFile)
                            .map(path -> path.toAbsolutePath().normalize())
                            .filter(path -> path.startsWith(root))
                            .filter(path -> path.getFileName().toString().equals(basename))
                            .toList();
                    if (matches.isEmpty()) {
                        return ResponseEntity.status(404).body(Map.of("message", "The source file for this finding was not found in the uploaded project."));
                    }
                    if (matches.size() > 1) {
                        return ResponseEntity.unprocessableEntity().body(Map.of("message", "More than one file has this name. This finding needs a manual fix to avoid changing the wrong file."));
                    }
                    source = matches.get(0);
                }
            }
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            int index = finding.getLineNumber() == null ? -1 : finding.getLineNumber() - 1;
            if (index < 0 || index >= lines.size()) {
                return ResponseEntity.unprocessableEntity().body(Map.of("message", "The finding line no longer matches the uploaded source. Review the code manually."));
            }
            String original = lines.get(index);
            String updated = null;
            if ("System.out.println used".equals(finding.getTitle())) {
                Matcher matcher = Pattern.compile("^(\\s*)System\\.out\\.println\\s*\\((.*)\\)\\s*;\\s*$").matcher(original);
                if (matcher.matches()) {
                    String className = "DataGuard";
                    String sourceText = String.join("\n", lines);
                    Matcher classMatcher = Pattern.compile("\\bclass\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(sourceText);
                    if (classMatcher.find()) className = classMatcher.group(1);
                    updated = matcher.group(1) + "System.getLogger(\"" + className
                            + "\").log(System.Logger.Level.INFO, String.valueOf(" + matcher.group(2) + "));";
                }
            } else if ("Empty catch block".equals(finding.getTitle())) {
                Matcher matcher = Pattern.compile("^(\\s*)catch\\s*\\(([^)]*?\\s+([A-Za-z_$][A-Za-z0-9_$]*))\\)\\s*\\{\\s*}\\s*$").matcher(original);
                if (matcher.matches()) {
                    updated = matcher.group(1) + "catch (" + matcher.group(2)
                            + ") { throw new IllegalStateException(\"Operation failed\", "
                            + matcher.group(3) + "); }";
                }
            }
            if (updated == null) {
                return ResponseEntity.unprocessableEntity().body(Map.of(
                        "message", "A safe automatic patch is not available for this exact line. Use the suggested fix and review it manually."));
            }
            // Keep a same-directory backup so a persistence failure cannot leave the
            // source modified while the finding remains unresolved in the database.
            Path backup = Files.createTempFile(source.getParent(), ".dataguard-fix-", ".bak");
            Files.copy(source, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            boolean preserveBackup = false;
            try {
                lines.set(index, updated);
                Files.write(source, lines, StandardCharsets.UTF_8);
                finding.setStatus("RESOLVED");
                findingRepository.save(finding);
                List<Finding> all = findingRepository.findByReviewId(reviewId);
                long unresolvedCount = all.stream()
                        .filter(item -> !"RESOLVED".equalsIgnoreCase(item.getStatus()))
                        .count();
                // Keep the same score rule used for the initial scan. This makes each
                // successfully resolved finding increase the displayed score by one.
                int score = Math.max(1, 100 - (int) Math.min(99L, unresolvedCount));
                finding.getReview().setQualityScore(score);
                reviewRepository.save(finding.getReview());
                return ResponseEntity.ok(Map.of(
                        "message", "A safe fix was applied to the uploaded project file.",
                        "findingId", finding.getId(),
                        "status", "RESOLVED",
                        "qualityScore", finding.getReview().getQualityScore(),
                        "modifiedFile", finding.getFilePath()));
            } catch (IOException | RuntimeException failure) {
                try {
                    Files.copy(backup, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException restoreFailure) {
                    preserveBackup = true;
                    failure.addSuppressed(restoreFailure);
                }
                if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
                return ResponseEntity.internalServerError()
                        .body(Map.of("message", "Could not safely update the uploaded project file."));
            } finally {
                if (!preserveBackup) {
                    try { Files.deleteIfExists(backup); } catch (IOException ignored) { /* Best-effort cleanup. */ }
                }
            }
        } catch (IOException exception) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Could not safely update the uploaded project file."));
        }
    }

    public ProjectController(ProjectService projectService, UserRepository userRepository,
                             FindingRepository findingRepository, ReviewRepository reviewRepository) {
        this.projectService = projectService;
        this.userRepository = userRepository;
        this.findingRepository = findingRepository;
        this.reviewRepository = reviewRepository;
    }
}
