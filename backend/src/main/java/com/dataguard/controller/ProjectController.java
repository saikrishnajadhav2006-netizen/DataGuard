package com.dataguard.controller;

import com.dataguard.dto.FixResponse;
import com.dataguard.dto.ReviewResponse;
import com.dataguard.entity.Finding;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ProjectRepository;
import com.dataguard.repository.ReviewRepository;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.repository.UserRepository;
import com.dataguard.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final FindingRepository findingRepository;
    private final ProjectRepository projectRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            Authentication authentication) {
        
        try {
            // Find current user from JWT token
                String email = authenticationEmail(authentication);
                User user = userRepository.findByEmail(email).orElseGet(() -> provisionUser(email));

            if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
                return ResponseEntity.badRequest().body("Only ZIP files are supported.");
            }

            Review reviewResult = projectService.processProjectUpload(file, user, name);
            return ResponseEntity.ok(ReviewResponse.from(reviewResult, findingRepository.findByReviewId(reviewResult.getId())));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to process uploaded file: " + e.getMessage());
        }
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

    private User provisionUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(email);
        user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        user.setRole(User.Role.DEVELOPER);
        return userRepository.save(user);
    }

    @GetMapping("/reviews")
    public ResponseEntity<?> listReviews(Authentication authentication) {
        String email = authenticationEmail(authentication);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).body("Please sign in again.");
        var reviews = reviewRepository.findAllForUser(user.getId()).stream()
                .map(review -> ReviewResponse.from(review, findingRepository.findByReviewId(review.getId())))
                .toList();
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/reviews/{reviewId}/download")
    public ResponseEntity<?> downloadReviewedProject(@PathVariable Long reviewId, Authentication authentication) {
        var review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) return ResponseEntity.notFound().build();
        var project = review.getProject();
        Long projectId = project.getId();
        if (!project.getUser().getEmail().equalsIgnoreCase(authenticationEmail(authentication))) {
            return ResponseEntity.status(403).body("You do not have access to this project.");
        }
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "dataguard_" + projectId).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return ResponseEntity.status(410).body("Temporary project files are no longer available. Please upload the ZIP again.");
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

    public ProjectController(ProjectService projectService, UserRepository userRepository, FindingRepository findingRepository,
                             ProjectRepository projectRepository, ReviewRepository reviewRepository, PasswordEncoder passwordEncoder) {
        this.projectService = projectService;
        this.userRepository = userRepository;
        this.findingRepository = findingRepository;
        this.projectRepository = projectRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
    }
}
