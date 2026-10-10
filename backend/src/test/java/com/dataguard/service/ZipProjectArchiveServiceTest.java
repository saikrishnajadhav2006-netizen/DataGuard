package com.dataguard.service;

import com.dataguard.dto.FixResponse;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class ZipProjectArchiveServiceTest {
    @TempDir Path temp;
    private ZipProjectArchiveService service;
    private Review review;
    private Finding finding;
    private byte[] original;
    private static final String VULNERABLE = "import sqlite3\ndef find_user(username):\n    cursor.execute(\"SELECT * FROM users WHERE username = '\" + username + \"'\")\n";
    private static final String FIXED = "    cursor.execute(\"SELECT * FROM users WHERE username = ?\", (username,))";

    @BeforeEach void setup() throws Exception {
        service = new ZipProjectArchiveService(new ObjectMapper(), temp.resolve("artifacts").toString(),
                2_000_000, 1_000_000, 100, 2_000_000);
        review = new Review(); review.setId(8L); Project project = new Project(); review.setProject(project);
        finding = new Finding(); finding.setId(33L); finding.setReview(review); finding.setRuleId("sec-python-sql-concat");
        finding.setFilePath("src/app.py"); finding.setLineNumber(3); finding.setEvidence(VULNERABLE.split("\\n")[2]);
        finding.setRecommendation("Use bound SQL parameters.");
        original = zip("src/app.py", VULNERABLE, "src/second.py", "cursor.execute(\"SELECT * FROM t WHERE id = '\" + username + \"'\")",
                "README.md", "hello", ".env", "TOKEN=private", "data/.env.example", "SAFE=1",
                "settings/local.properties", "API_KEY=sk_live_abcd1234567890");
        service.retainOriginal(8L, original);
    }

    @Test void approvedFixCreatesOpenableZipWithChangedAndUnchangedFilesAndPreservesUpload() throws Exception {
        byte[] before = Files.readAllBytes(temp.resolve("artifacts/review-8/original.zip"));
        var result = service.approveFix(review, finding, proposal(), 1);
        assertTrue(result.ready()); assertEquals(1, result.modifiedFiles()); assertEquals(0, result.unresolvedFindings());
        assertEquals(2, result.omittedSensitiveFiles());
        File fixed = service.openVerified(review);
        try (ZipFile zip = new ZipFile(fixed)) {
            assertEquals(FIXED, new String(zip.getInputStream(zip.getEntry("src/app.py")).readAllBytes(), StandardCharsets.UTF_8).split("\\n")[2]);
            assertEquals("hello", new String(zip.getInputStream(zip.getEntry("README.md")).readAllBytes(), StandardCharsets.UTF_8));
            assertNotNull(zip.getEntry("data/.env.example"));
            assertNull(zip.getEntry(".env"));
            assertNull(zip.getEntry("settings/local.properties"));
        }
        assertArrayEquals(before, Files.readAllBytes(temp.resolve("artifacts/review-8/original.zip")));
    }

    @Test void multipleApprovedFixesRebuildFromOriginalAndKeepEarlierChanges() throws Exception {
        service.approveFix(review, finding, proposal(), 2);
        Finding second = new Finding(); second.setId(34L); second.setReview(review); second.setRuleId("sec-python-sql-concat");
        second.setFilePath("src/second.py"); second.setLineNumber(1); second.setEvidence("cursor.execute(\"SELECT * FROM t WHERE id = '\" + username + \"'\")");
        var secondProposal = new FixResponse(34L, "bind the parameter", second.getEvidence(),
                "cursor.execute(\"SELECT * FROM t WHERE id = ?\", (username,))", false,
                "diff", second.getFilePath(), 1, null, null, null, "approved locally");
        var response = service.approveFix(review, second, secondProposal, 2);
        assertEquals(2, response.modifiedFiles());
        try (ZipFile result = new ZipFile(service.openVerified(review))) {
            assertTrue(new String(result.getInputStream(result.getEntry("src/app.py")).readAllBytes()).contains("username = ?"));
            assertTrue(new String(result.getInputStream(result.getEntry("src/second.py")).readAllBytes()).contains("id = ?"));
        }
    }

    @Test void rejectsZipSlipAndDoesNotRetainInvalidArchive() throws Exception {
        assertThrows(IOException.class, () -> service.retainOriginal(9L, zip("../outside.txt", "x")));
        assertFalse(Files.exists(temp.resolve("artifacts/review-9/original.zip")));
    }

    @Test void refusesUnsupportedFindingsAndSourceMismatch() throws Exception {
        Finding unsupported = new Finding(); unsupported.setId(35L); unsupported.setReview(review);
        unsupported.setRuleId("unknown"); unsupported.setFilePath("src/app.py"); unsupported.setLineNumber(3);
        var proposal = new FixResponse(35L, "manual", "x", "y", false, null, "src/app.py", 3, null, null, null, "no safe patch");
        assertThrows(IllegalStateException.class, () -> service.approveFix(review, unsupported, proposal, 1));
        FixResponse stale = new FixResponse(33L, "safe", "wrong source", FIXED, false, "patch", "src/app.py", 3, null, null, null, null);
        assertThrows(IllegalStateException.class, () -> service.approveFix(review, finding, stale, 1));
        assertFalse(Files.exists(temp.resolve("artifacts/review-8/fixed.zip")));
    }

    @Test void downloadVerificationRejectsCorruptFixedArchive() throws Exception {
        Path fixed = temp.resolve("artifacts/review-8/fixed.zip"); Files.writeString(fixed, "not a zip");
        assertThrows(IOException.class, () -> service.openVerified(review));
    }

    private FixResponse proposal() {
        return new FixResponse(33L, "Bind SQL parameter", finding.getEvidence(), FIXED, false,
                "--- a/src/app.py\n+++ b/src/app.py\n", finding.getFilePath(), 3, null, null, null, "Inspect before approval");
    }
    private byte[] zip(String... pairs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i=0;i<pairs.length;i+=2) { out.putNextEntry(new ZipEntry(pairs[i])); out.write(pairs[i+1].getBytes(StandardCharsets.UTF_8)); out.closeEntry(); }
        }
        return bytes.toByteArray();
    }
}
