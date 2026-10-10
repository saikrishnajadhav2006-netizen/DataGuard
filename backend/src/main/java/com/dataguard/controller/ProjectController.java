package com.dataguard.controller;

import com.dataguard.dto.FixResponse;
import com.dataguard.dto.ReviewResponse;
import com.dataguard.entity.Finding;
import com.dataguard.repository.FindingRepository;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.repository.UserRepository;
import com.dataguard.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final FindingRepository findingRepository;
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
                             PasswordEncoder passwordEncoder) {
        this.projectService = projectService;
        this.userRepository = userRepository;
        this.findingRepository = findingRepository;
        this.passwordEncoder = passwordEncoder;
    }
}
