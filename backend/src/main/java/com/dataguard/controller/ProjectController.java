package com.dataguard.controller;

import com.dataguard.dto.FixResponse;
import com.dataguard.dto.ReviewResponse;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.exception.ZipValidationException;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ReviewRepository;
import com.dataguard.repository.UserRepository;
import com.dataguard.service.ProjectService;

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

    public ProjectController(
            ProjectService projectService,
            UserRepository userRepository,
            FindingRepository findingRepository,
            ReviewRepository reviewRepository,
            PasswordEncoder passwordEncoder) {

        this.projectService = projectService;
        this.userRepository = userRepository;
        this.findingRepository = findingRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
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

                    return ResponseEntity.ok(
                            buildFixResponse(finding)
                    );
                })
                .orElseGet(
                        () -> ResponseEntity.notFound().build()
                );
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

    // =========================================================
    // Fix suggestion helper
    // =========================================================

    private FixResponse buildFixResponse(Finding finding) {

        String suggestedCode;
        String description;

        String title =
                finding.getTitle() != null
                        ? finding.getTitle().toLowerCase()
                        : "";

        // -----------------------------------------------------
        // Debug output
        // -----------------------------------------------------

        if (title.contains("debug output")) {

            suggestedCode =
                    "private static final Logger log =\n"
                    + "    LoggerFactory.getLogger(CurrentClass.class);\n\n"
                    + "log.info(\"Replace with structured logging\");";

            description =
                    "Replace standard output with structured SLF4J "
                    + "logging and add the logger declaration to the class.";

        // -----------------------------------------------------
        // Empty catch
        // -----------------------------------------------------

        } else if (title.contains("empty catch")) {

            suggestedCode =
                    "catch (Exception exception) {\n"
                    + "    log.error(\"Operation failed\", exception);\n"
                    + "}";

            description =
                    "Handle the exception explicitly by logging it "
                    + "or returning a controlled error response.";

        // -----------------------------------------------------
        // Hardcoded credentials
        // -----------------------------------------------------

        } else if (title.contains("hardcoded")) {

            suggestedCode =
                    "// Use environment variables or a secrets manager:\n"
                    + "String secret = System.getenv(\"MY_SECRET\");";

            description =
                    "Move the hardcoded credential to an environment "
                    + "variable or a secrets management system.";

        // -----------------------------------------------------
        // Generic recommendation
        // -----------------------------------------------------

        } else {

            suggestedCode =
                    finding.getRecommendation() != null
                            ? finding.getRecommendation()
                            : "";

            description =
                    "Review the recommendation and apply manually.";
        }

        return new FixResponse(
                finding.getId(),
                description,
                finding.getEvidence(),
                suggestedCode,
                false
        );
    }
}