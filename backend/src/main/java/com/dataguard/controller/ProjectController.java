package com.dataguard.controller;

import com.dataguard.dto.FixApprovalRequest;
import com.dataguard.dto.ReviewResponse;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.exception.ZipValidationException;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ReviewRepository;
import com.dataguard.repository.UserRepository;
import com.dataguard.service.ProjectService;
import com.dataguard.service.FixSuggestionService;
import com.dataguard.service.ReviewEngine;
import com.dataguard.service.ZipProjectArchiveService;
import com.dataguard.dto.FixedProjectResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProjectController {

    private static final Logger log =
            LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final FindingRepository findingRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final FixSuggestionService fixSuggestionService;
    private final ZipProjectArchiveService zipProjectArchiveService;
    private final ReviewEngine reviewEngine;

    public ProjectController(
            ProjectService projectService,
            UserRepository userRepository,
            FindingRepository findingRepository,
            ReviewRepository reviewRepository,
            PasswordEncoder passwordEncoder,
            FixSuggestionService fixSuggestionService,
            ZipProjectArchiveService zipProjectArchiveService,
            ReviewEngine reviewEngine) {

        this.projectService = projectService;
        this.userRepository = userRepository;
        this.findingRepository = findingRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
        this.fixSuggestionService = fixSuggestionService;
        this.zipProjectArchiveService = zipProjectArchiveService;
        this.reviewEngine = reviewEngine;
    }

    // =========================================================
    // Upload endpoint
    // =========================================================

    @PostMapping("/projects/upload")
    public ResponseEntity<?> uploadProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            Authentication authentication) {

        // Validate filename extension before doing any work
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null
                || !originalFilename.toLowerCase().endsWith(".zip")) {

            return ResponseEntity.badRequest()
                    .body("Only ZIP archives are accepted. Please upload a .zip file.");
        }

        User user = resolveUser(authentication);

        try {

            Review review =
                    projectService.processProjectUpload(file, user, name);
            if (!"FAILED".equals(review.getStatus())) {
                zipProjectArchiveService.retainOriginal(review.getId(), file.getBytes());
            }

            List<Finding> findings =
                    findingRepository.findByReviewId(review.getId());

            return ResponseEntity.ok(
                    ReviewResponse.from(review, findings)
            );

        } catch (ZipValidationException e) {

            log.warn(
                    "ZIP validation failed for user '{}': {}",
                    user.getEmail(),
                    e.getMessage()
            );

            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (IOException e) {

            log.error(
                    "I/O error processing upload for user '{}': {}",
                    user.getEmail(),
                    e.getMessage(),
                    e
            );

            return ResponseEntity.internalServerError()
                    .body("Failed to process uploaded file. Please try again.");

        } catch (Exception e) {

            log.error(
                    "Unexpected error during upload for user '{}': {}",
                    user.getEmail(),
                    e.getMessage(),
                    e
            );

            return ResponseEntity.internalServerError()
                    .body("An unexpected error occurred. Please try again.");
        }
    }

    // =========================================================
    // Review history endpoints
    // =========================================================

    /**
     * Returns all reviews for the authenticated user,
     * newest first.
     */
    @GetMapping("/reviews")
    public ResponseEntity<?> listReviews(
            Authentication authentication) {

        User user = resolveUser(authentication);

        List<Review> reviews =
                reviewRepository.findByProjectUserIdOrderByReviewDateDesc(
                        user.getId()
                );

        List<ReviewResponse> responses =
                reviews.stream()
                        .map(review ->
                                ReviewResponse.from(
                                        review,
                                        findingRepository.findByReviewId(
                                                review.getId()
                                        )
                                )
                        )
                        .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Returns a single review by ID.
     * The review must belong to the authenticated user.
     */
    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<?> getReview(
            @PathVariable Long reviewId,
            Authentication authentication) {

        User user = resolveUser(authentication);

        return reviewRepository.findById(reviewId)
                .filter(review ->
                        review.getProject()
                                .getUser()
                                .getId()
                                .equals(user.getId())
                )
                .map(review ->
                        ResponseEntity.ok(
                                ReviewResponse.from(
                                        review,
                                        findingRepository.findByReviewId(
                                                review.getId()
                                        )
                                )
                        )
                )
                .orElseGet(
                        () -> ResponseEntity.notFound().build()
                );
    }

    // =========================================================
    // Fix suggestion endpoint
    // =========================================================

    @PostMapping(
            "/projects/reviews/{reviewId}/findings/{findingId}/fix"
    )
    public ResponseEntity<?> suggestFix(
            @PathVariable Long reviewId,
            @PathVariable Long findingId,
            Authentication authentication) {

        User user = resolveUser(authentication);

        return findingRepository.findById(findingId)
                .filter(finding ->
                        finding.getReview()
                                .getId()
                                .equals(reviewId)
                )
                .map(finding -> {

                    // Owner check
                    if (!finding.getReview()
                            .getProject()
                            .getUser()
                            .getId()
                            .equals(user.getId())) {

                        return ResponseEntity
                                .status(403)
                                .body(
                                        "You do not have access to this finding."
                                );
                    }

                    try {
                        return ResponseEntity.ok(fixSuggestionService.suggest(finding));
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(e.getMessage());
                    }
                })
                .orElseGet(
                        () -> ResponseEntity.notFound().build()
                );
    }

    @PostMapping("/projects/reviews/{reviewId}/findings/{findingId}/fix/apply")
    public ResponseEntity<?> applyFix(
            @PathVariable Long reviewId,
            @PathVariable Long findingId,
            @RequestBody FixApprovalRequest approval,
            Authentication authentication) {
        User user = resolveUser(authentication);
        Finding finding = findingRepository.findById(findingId).orElse(null);
        if (finding == null || finding.getReview() == null || !reviewId.equals(finding.getReview().getId())) {
            return ResponseEntity.notFound().build();
        }
        if (!finding.getReview().getProject().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("You do not have access to this finding.");
        }
        try {
            return ResponseEntity.ok(fixSuggestionService.apply(finding, approval));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            log.warn("Approved fix could not be confirmed for finding {}: {}", findingId, e.getClass().getSimpleName());
            return ResponseEntity.status(502).body("GitHub could not confirm the fix pull request. Check the branch before retrying.");
        }
    }

    @PostMapping("/reviews/{reviewId}/fixed-project/findings/{findingId}/approve")
    public ResponseEntity<?> approveZipFix(@PathVariable Long reviewId, @PathVariable Long findingId,
                                            Authentication authentication) {
        User user = resolveUser(authentication);
        Review review = ownedReview(reviewId, user);
        if (review == null) return ResponseEntity.notFound().build();
        Finding finding = findingRepository.findById(findingId).orElse(null);
        if (finding == null || finding.getReview() == null || !reviewId.equals(finding.getReview().getId())) {
            return ResponseEntity.notFound().build();
        }
        try {
            var proposal = fixSuggestionService.suggest(finding);
            return ResponseEntity.ok(zipProjectArchiveService.approveFix(review, finding, proposal,
                    findingRepository.findByReviewId(reviewId).size()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (IOException e) {
            log.warn("Fixed project archive could not be prepared for review {}", reviewId, e);
            return ResponseEntity.internalServerError().body("The fixed archive could not be safely prepared.");
        }
    }

    @GetMapping("/reviews/{reviewId}/fixed-project")
    public ResponseEntity<?> fixedProjectStatus(@PathVariable Long reviewId, Authentication authentication) {
        User user = resolveUser(authentication);
        Review review = ownedReview(reviewId, user);
        if (review == null) return ResponseEntity.notFound().build();
        return zipProjectArchiveService.status(review, findingRepository.findByReviewId(reviewId))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reviews/{reviewId}/fixed-project/re-review")
    public ResponseEntity<?> reReviewFixedProject(@PathVariable Long reviewId, Authentication authentication) {
        User user = resolveUser(authentication);
        Review review = ownedReview(reviewId, user);
        if (review == null) return ResponseEntity.notFound().build();
        try {
            var tree = zipProjectArchiveService.extractFixedProjectTree(review);
            Review updated = reviewEngine.reRunReview(review, tree.toFile());
            List<Finding> findings = findingRepository.findByReviewId(updated.getId());
            return ResponseEntity.ok(ReviewResponse.from(updated, findings));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(409).body("Build a verified fixed project ZIP before re-running analysis.");
        } catch (IOException e) {
            log.warn("Re-review failed for review {}", reviewId, e);
            return ResponseEntity.internalServerError().body("The fixed project could not be re-analyzed safely.");
        }
    }

    @GetMapping("/reviews/{reviewId}/fixed-project/download")
    public ResponseEntity<?> downloadFixedProject(@PathVariable Long reviewId, Authentication authentication) {
        User user = resolveUser(authentication);
        Review review = ownedReview(reviewId, user);
        if (review == null) return ResponseEntity.notFound().build();
        try {
            var archive = zipProjectArchiveService.openVerified(review);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project-fixed.zip\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                    .contentLength(archive.length())
                    .body(new org.springframework.core.io.FileSystemResource(archive));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("The fixed archive failed verification. Please approve the fix again.");
        }
    }

    private Review ownedReview(Long reviewId, User user) {
        return reviewRepository.findById(reviewId)
                .filter(r -> r.getProject() != null && r.getProject().getUser() != null
                        && user.getId().equals(r.getProject().getUser().getId()))
                .orElse(null);
    }

    // =========================================================
    // Authentication / user helpers
    // =========================================================

    private User resolveUser(Authentication authentication) {

        String email = extractEmail(authentication);

        return userRepository
                .findByEmail(email)
                .orElseGet(() -> provisionUser(email));
    }

    private String extractEmail(Authentication authentication) {

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {

            String email =
                    jwtAuth.getToken()
                            .getClaimAsString("email");

            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        return authentication.getName();
    }

    private User provisionUser(String email) {

        User user = new User();

        user.setEmail(email);
        user.setFullName(email);

        user.setPassword(
                passwordEncoder.encode(
                        UUID.randomUUID().toString()
                )
        );

        user.setRole(User.Role.DEVELOPER);

        return userRepository.save(user);
    }

}
