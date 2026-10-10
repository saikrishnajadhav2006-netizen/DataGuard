package com.dataguard.controller;

import com.dataguard.dto.ReviewResponse;
import com.dataguard.entity.Finding;
import com.dataguard.repository.FindingRepository;
import com.dataguard.service.FixSuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises upload -> analysis -> suggestion -> explicit approval -> authenticated download. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.config.import=",
        "dataguard.archive.storage-dir=target/test-fixed-projects",
        "dataguard.semgrep.enabled=false"
})
class ZipFixWorkflowIntegrationTest {
    private static final String VULNERABLE = "import sqlite3\ndef find_user(username):\n    cursor.execute(\"SELECT * FROM users WHERE username = '\" + username + \"'\")\n";
    @Autowired ProjectController controller;
    @Autowired FindingRepository findingRepository;
    @Autowired FixSuggestionService suggestions;
    private UsernamePasswordAuthenticationToken auth;
    private byte[] originalZip;

    @BeforeEach void setup() throws Exception {
        Path artifactDir = Path.of("target/test-fixed-projects");
        if (Files.exists(artifactDir)) try (var paths = Files.walk(artifactDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException e) { throw new UncheckedIOException(e); } });
        }
        auth = UsernamePasswordAuthenticationToken.authenticated("zip-flow@example.test", "", List.of());
        originalZip = zip();
    }

    @Test void uploadedProjectCanBeAnalyzedFixedDownloadedAndExtractedWithoutChangingOriginal() throws Exception {
        MockMultipartFile upload = new MockMultipartFile("file", "sample.zip", "application/zip", originalZip);
        ResponseEntity<?> uploaded = controller.uploadProject(upload, "zip-flow", auth);
        assertEquals(200, uploaded.getStatusCode().value());
        ReviewResponse review = (ReviewResponse) uploaded.getBody(); assertNotNull(review);
        assertTrue(review.findings().stream().anyMatch(f -> "sec-python-sql-concat".equals(f.ruleId())), review.toString());
        var finding = findingRepository.findByReviewId(review.id()).stream()
                .filter(f -> "sec-python-sql-concat".equals(f.getRuleId())).findFirst().orElseThrow();
        assertNotNull(suggestions.suggest(finding).patch());

        ResponseEntity<?> approved = controller.approveZipFix(review.id(), finding.getId(), auth);
        assertEquals(200, approved.getStatusCode().value());
        var status = controller.fixedProjectStatus(review.id(), auth);
        assertEquals(200, status.getStatusCode().value());
        assertEquals(1, ((com.dataguard.dto.FixedProjectResponse) status.getBody()).modifiedFiles());

        ResponseEntity<?> downloaded = controller.downloadFixedProject(review.id(), auth);
        assertEquals(200, downloaded.getStatusCode().value());
        byte[] fixedBytes;
        try (InputStream in = ((Resource) downloaded.getBody()).getInputStream()) { fixedBytes = in.readAllBytes(); }
        assertNotEquals(0, fixedBytes.length);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(fixedBytes))) {
            String app = null, readme = null; ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                if (entry.getName().equals("src/app.py")) app = content;
                if (entry.getName().equals("README.md")) readme = content;
                assertNotEquals(".env", entry.getName());
            }
            assertNotNull(app); assertTrue(app.contains("WHERE username = ?\", (username,))"), app);
            assertEquals("unchanged project content", readme);
        }
        Path retainedOriginal = Path.of("target/test-fixed-projects", "review-" + review.id(), "original.zip");
        assertArrayEquals(originalZip, Files.readAllBytes(retainedOriginal));

        ResponseEntity<?> reReviewed = controller.reReviewFixedProject(review.id(), auth);
        assertEquals(200, reReviewed.getStatusCode().value());
        ReviewResponse afterFix = (ReviewResponse) reReviewed.getBody();
        assertNotNull(afterFix);
        assertFalse(afterFix.findings().stream().anyMatch(f -> "sec-python-sql-concat".equals(f.ruleId())),
                "SQL concat finding should be resolved after the approved patch");
    }

    private byte[] zip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            put(out, "src/app.py", VULNERABLE);
            put(out, "README.md", "unchanged project content");
            put(out, ".env", "API_TOKEN=should-not-export");
        }
        return bytes.toByteArray();
    }
    private void put(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name)); out.write(content.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
    }
}
