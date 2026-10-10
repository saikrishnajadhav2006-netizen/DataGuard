package com.dataguard.controller;

import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.entity.Finding;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.GitHubReviewProgressRepository;
import com.dataguard.repository.ReviewRepository;
import com.dataguard.repository.UserRepository;
import com.dataguard.service.FixSuggestionService;
import com.dataguard.service.ProjectService;
import com.dataguard.service.ReviewEngine;
import com.dataguard.service.ZipProjectArchiveService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.dataguard.dto.FixResponse;
import com.dataguard.github.GitHubIntegrationService;
import com.dataguard.service.ZipProjectArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.List;
import java.util.Optional;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixedProjectAuthorizationTest {
    @Test void anotherUserCannotReadStatusOrDownloadFixedArchive() {
        ProjectService projects = new ProjectService(null, null);
        UserRepository users = mock(UserRepository.class);
        FindingRepository findings = mock(FindingRepository.class);
        ReviewRepository reviews = mock(ReviewRepository.class);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        FixSuggestionService fixes = new FixSuggestionService(mock(GitHubReviewProgressRepository.class),
                new GitHubIntegrationService(null), new ObjectMapper());

        User caller = new User(); caller.setId(1L); caller.setEmail("caller@example.test");
        User owner = new User(); owner.setId(2L); owner.setEmail("owner@example.test");
        Project project = new Project(); project.setUser(owner);
        Review review = new Review(); review.setId(90L); review.setProject(project);
        PathHolder artifacts = new PathHolder();
        ZipProjectArchiveService archives = new ZipProjectArchiveService(new ObjectMapper(), artifacts.path.toString(),
                2_000_000, 1_000_000, 100, 2_000_000);
        try {
            archives.retainOriginal(90L, zip());
            Finding finding = new Finding(); finding.setId(9L); finding.setReview(review); finding.setRuleId("sec-python-sql-concat");
            finding.setFilePath("app.py"); finding.setLineNumber(1); finding.setEvidence("cursor.execute(\"SELECT * FROM t WHERE id = '\" + username + \"'\")");
            FixResponse patch = new FixResponse(9L, "bind", finding.getEvidence(),
                    "cursor.execute(\"SELECT * FROM t WHERE id = ?\", (username,))", false, "patch", "app.py", 1, null, null, null, null);
            archives.approveFix(review, finding, patch, 1);
        } catch (Exception e) { throw new AssertionError(e); }
        when(users.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(reviews.findById(90L)).thenReturn(Optional.of(review));
        var auth = UsernamePasswordAuthenticationToken.authenticated(caller.getEmail(), "", List.of());
        ReviewEngine reviewEngine = org.mockito.Mockito.mock(ReviewEngine.class);
        ProjectController controller = new ProjectController(projects, users, findings, reviews, encoder, fixes, archives, reviewEngine);

        assertEquals(HttpStatus.NOT_FOUND, controller.fixedProjectStatus(90L, auth).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.downloadFixedProject(90L, auth).getStatusCode());
    }

    private static byte[] zip() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("app.py"));
            out.write("cursor.execute(\"SELECT * FROM t WHERE id = '\" + username + \"'\")".getBytes());
            out.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static class PathHolder {
        final java.nio.file.Path path;
        PathHolder() { try { path = Files.createTempDirectory("dataguard-auth-test"); } catch (Exception e) { throw new RuntimeException(e); } }
    }
}
