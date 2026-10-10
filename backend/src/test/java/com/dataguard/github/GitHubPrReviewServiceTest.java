package com.dataguard.github;
import com.dataguard.repository.AIExplanationRepository;
import com.dataguard.service.RadarChatProvider;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.repository.ProjectRepository;
import com.dataguard.repository.UserRepository;
import com.dataguard.repository.FindingRepository;
import com.dataguard.repository.ReviewRepository;
import com.dataguard.analyzer.ArchitectureAnalyzer;
import com.dataguard.analyzer.CodeQualityAnalyzer;
import com.dataguard.analyzer.DependencyCheckAnalyzer;
import com.dataguard.analyzer.PMDAnalyzer;
import com.dataguard.service.AIService;
import com.dataguard.service.ScoringService;
import com.dataguard.service.ReviewEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GitHubPrReviewServiceTest {

    private TestGitHubIntegrationService githubService;
    private TestReviewEngine reviewEngine;
    private TestProjectRepository projectRepository;
    private TestUserRepository userRepository;
    private TestFindingRepository findingRepository;
    private GitHubPrReviewService prReviewService;

    static class TestGitHubIntegrationService extends GitHubIntegrationService {
        public List<Map<String, Object>> files;
        public boolean downloadSafeResult = true;
        public String downloadContent = "";
        public boolean downloadCalled = false;
        public Long checkRunId = 100L;
        public Map<String, Object> updatedCheckRunOutput;
        public String updatedConclusion;
        
        public TestGitHubIntegrationService() { super(null); }
        
        @Override
        public Long getInstallationIdForRepository(String owner, String repo) { return 10L; }
        
        @Override
        public String getInstallationAccessToken(Long installationId) { return "token123"; }
        
        @Override
        public List<Map<String, Object>> getPullRequestFiles(String owner, String repo, int number, String token) { return files; }
        
        @Override
        public boolean downloadFileSafe(String owner, String repo, String path, String ref, String token, File dest, long maxSize) {
            downloadCalled = true;
            if (downloadSafeResult) {
                try {
                    Files.writeString(dest.toPath(), downloadContent);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return downloadSafeResult;
        }
        
        @Override
        public Map<String, Object> createCheckRun(String owner, String repo, String headSha, String name, String status, String conclusion, Map<String, Object> output, String token) {
            return Map.of("id", checkRunId);
        }
        
        @Override
        public Map<String, Object> updateCheckRun(String owner, String repo, Long checkRunId, String status, String conclusion, Map<String, Object> output, String token) {
            this.updatedCheckRunOutput = output;
            this.updatedConclusion = conclusion;
            return Map.of();
        }
    }

    static class TestReviewEngine extends ReviewEngine {
        public Review reviewToReturn;
        public ReviewEngine delegate;
        public boolean runReviewCalled = false;
        public TestReviewEngine() { super(null, null, null, null, null, null, null, null, null); }
        @Override
        public Review runReview(Project project, File extractedDir) {
            runReviewCalled = true;
            if (delegate != null) return delegate.runReview(project, extractedDir);
            return reviewToReturn;
        }
    }

    static class TestProjectRepository implements ProjectRepository {
        @Override public Optional<Project> findByName(String name) { return Optional.of(new Project()); }
        @Override public void flush() {}
        @Override public <S extends Project> S saveAndFlush(S entity) { return null; }
        @Override public <S extends Project> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<Project> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Project getOne(Long id) { return null; }
        @Override public Project getById(Long id) { return null; }
        @Override public Project getReferenceById(Long id) { return null; }
        @Override public <S extends Project> List<S> findAll(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends Project> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return null; }
        @Override public <S extends Project> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public List<Project> findAll() { return null; }
        @Override public List<Project> findAllById(Iterable<Long> ids) { return null; }
        @Override public <S extends Project> S save(S entity) { return null; }
        @Override public Optional<Project> findById(Long id) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(Project entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends Project> entities) {}
        @Override public void deleteAll() {}
        @Override public List<Project> findAll(org.springframework.data.domain.Sort sort) { return null; }
        @Override public org.springframework.data.domain.Page<Project> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends Project> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends Project> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends Project> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Project> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Project, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }

    static class TestUserRepository implements UserRepository {
        @Override public Optional<User> findByEmail(String email) { return Optional.of(new User()); }
        @Override public boolean existsByEmail(String email) { return true; }
        @Override public void flush() {}
        @Override public <S extends User> S saveAndFlush(S entity) { return null; }
        @Override public <S extends User> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<User> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public User getOne(Long id) { return null; }
        @Override public User getById(Long id) { return null; }
        @Override public User getReferenceById(Long id) { return null; }
        @Override public <S extends User> List<S> findAll(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends User> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return null; }
        @Override public <S extends User> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public List<User> findAll() { return null; }
        @Override public List<User> findAllById(Iterable<Long> ids) { return null; }
        @Override public <S extends User> S save(S entity) { return null; }
        @Override public Optional<User> findById(Long id) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(User entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends User> entities) {}
        @Override public void deleteAll() {}
        @Override public List<User> findAll(org.springframework.data.domain.Sort sort) { return null; }
        @Override public org.springframework.data.domain.Page<User> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends User> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends User> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends User> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends User> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends User, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }

    static class TestFindingRepository implements FindingRepository {
        public List<Finding> findingsToReturn;
        @Override public <S extends Finding> List<S> saveAll(Iterable<S> entities) {
            List<S> saved = new java.util.ArrayList<>();
            entities.forEach(saved::add);
            findingsToReturn = (List<Finding>) (List<?>) saved;
            return saved;
        }
        @Override public List<Finding> findByReviewId(Long reviewId) { return findingsToReturn; }
        @Override public void deleteByReviewId(Long reviewId) {}
        @Override public void flush() {}
        @Override public <S extends Finding> S saveAndFlush(S entity) { return null; }
        @Override public <S extends Finding> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<Finding> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Finding getOne(Long id) { return null; }
        @Override public Finding getById(Long id) { return null; }
        @Override public Finding getReferenceById(Long id) { return null; }
        @Override public <S extends Finding> List<S> findAll(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends Finding> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return null; }
        @Override public List<Finding> findAll() { return null; }
        @Override public List<Finding> findAllById(Iterable<Long> ids) { return null; }
        @Override public <S extends Finding> S save(S entity) { return null; }
        @Override public Optional<Finding> findById(Long id) { return null; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(Finding entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends Finding> entities) {}
        @Override public void deleteAll() {}
        @Override public List<Finding> findAll(org.springframework.data.domain.Sort sort) { return null; }
        @Override public org.springframework.data.domain.Page<Finding> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends Finding> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends Finding> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends Finding> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Finding> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Finding, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }

    @BeforeEach
    void setUp() {
        githubService = new TestGitHubIntegrationService();
        reviewEngine = new TestReviewEngine();
        projectRepository = new TestProjectRepository();
        userRepository = new TestUserRepository();
        findingRepository = new TestFindingRepository();

        prReviewService = new GitHubPrReviewService(githubService, reviewEngine, projectRepository, userRepository, findingRepository);
    }

    private void setupMocks() {
        Review emptyReview = new Review();
        emptyReview.setOverallScore(100);
        emptyReview.setId(1L);
        reviewEngine.reviewToReturn = emptyReview;
        findingRepository.findingsToReturn = List.of();
    }

    @Test
    void testProcessPullRequest_NormalSafeFile() throws Exception {
        setupMocks();

        githubService.files = List.of(Map.of("filename", "src/main/java/Safe.java", "status", "modified"));
        githubService.downloadSafeResult = true;

        prReviewService.processPullRequest("owner", "repo", 1, "sha123");

        assertTrue(githubService.downloadCalled);
    }

    @Test
    void testProcessPullRequest_PathTraversal_Skipped() throws Exception {
        setupMocks();

        githubService.files = List.of(Map.of("filename", "../../../etc/passwd", "status", "modified"));

        prReviewService.processPullRequest("owner", "repo", 1, "sha123");

        assertFalse(githubService.downloadCalled);
    }

    @Test
    void testProcessPullRequest_AbsolutePath_Skipped() throws Exception {
        setupMocks();

        String absolutePath = new File("etc", "shadow").getAbsolutePath();
        githubService.files = List.of(Map.of("filename", absolutePath, "status", "modified"));

        prReviewService.processPullRequest("owner", "repo", 1, "sha123");

        assertFalse(githubService.downloadCalled);
    }

    @Test
    void testProcessPullRequest_OversizedFile_HandledGracefully() throws Exception {
        setupMocks();

        githubService.files = List.of(Map.of("filename", "large.iso", "status", "added"));
        githubService.downloadSafeResult = false;

        prReviewService.processPullRequest("owner", "repo", 1, "sha123");

        assertTrue(githubService.downloadCalled);
        // Should still run review
        assertTrue(reviewEngine.runReviewCalled);
        assertEquals("neutral", githubService.updatedConclusion);
        String summary = (String) githubService.updatedCheckRunOutput.get("summary");
        assertTrue(summary.contains("oversized, or request failed): large.iso"));
    }

    @Test
    void unsupportedChangedFileIsReportedAndCheckRunIsNotMarkedClean() {
        setupMocks();
        githubService.files = List.of(Map.of("filename", "assets/readme.bin", "status", "modified"));

        prReviewService.processPullRequest("owner", "repo", 1, "sha123");

        assertEquals("neutral", githubService.updatedConclusion);
        String summary = (String) githubService.updatedCheckRunOutput.get("summary");
        assertTrue(summary.contains("INCOMPLETE REVIEW"));
        assertTrue(summary.contains("No analyzer supports this changed file type: assets/readme.bin"));
    }

    @Test
void vulnerablePrSourceFlowsThroughReviewEngineIntoCheckRunScoresAndAnnotations() {
    githubService.files = List.of(
            Map.of("filename", "src/app.py", "status", "added")
    );

    githubService.downloadContent = "import sqlite3\n"
            + "def find_user(username):\n"
            + "    cursor.execute(\"SELECT * FROM users WHERE username = '"
            + "\" + username + \"'\")\n";

    ReviewRepository reviewRepository = mock(ReviewRepository.class);

    when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
        Review saved = invocation.getArgument(0);

        if (saved.getId() == null) {
            saved.setId(7L);
        }

        return saved;
    });

    AIExplanationRepository explanationRepository =
            mock(AIExplanationRepository.class);

    RadarChatProvider testProvider = turns ->
            "Explanation: Test response\n"
                    + "Impact: Test impact\n"
                    + "Recommended action: Use a parameterized query.";

    AIService aiService = new AIService(
            testProvider,
            explanationRepository
    );

    ReviewEngine realEngine = new ReviewEngine(
            reviewRepository,
            findingRepository,
            explanationRepository,
            new CodeQualityAnalyzer(),
            new PMDAnalyzer(),
            new DependencyCheckAnalyzer(),
            new ArchitectureAnalyzer(),
            new ScoringService(),
            aiService
    );

    reviewEngine.delegate = realEngine;

    prReviewService.processPullRequest("owner", "repo", 1, "sha123");

    assertEquals(1, findingRepository.findingsToReturn.size());
    assertEquals(
            "SECURITY",
            findingRepository.findingsToReturn.get(0).getCategory()
    );
    assertEquals(
            "sec-python-sql-concat",
            findingRepository.findingsToReturn.get(0).getRuleId()
    );

    String summary =
            (String) githubService.updatedCheckRunOutput.get("summary");

    assertTrue(summary.contains("**Overall** | 96/100"));
    assertTrue(summary.contains("**Security** | 90/100"));
    assertTrue(summary.contains("**Quality** | 100/100"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> annotations =
            (List<Map<String, Object>>) githubService.updatedCheckRunOutput
                    .get("annotations");

    assertEquals(1, annotations.size());
    assertEquals("src/app.py", annotations.get(0).get("path"));
    assertEquals(3, annotations.get(0).get("start_line"));
    assertTrue(
            ((String) annotations.get(0).get("message"))
                    .contains("parameterized query")
    );
}

    @Test
    void testCreateCheckRunOutput_AllScoresAndAnnotationsMapped() throws Exception {
        setupMocks();
        
        Review review = new Review();
        review.setId(1L);
        review.setOverallScore(85);
        review.setSecurityScore(90);
        review.setQualityScore(80);
        review.setArchitectureScore(85);
        
        Finding f1 = new Finding();
        f1.setFilePath("src/main/Test1.java");
        f1.setLineNumber(10);
        f1.setSeverity("CRITICAL");
        f1.setTitle("Crit Title");
        f1.setDescription("Crit Desc");

        Finding f2 = new Finding();
        f2.setFilePath("src/main/Test2.java");
        f2.setLineNumber(20);
        f2.setSeverity("MEDIUM");
        f2.setTitle("Med Title");
        f2.setDescription("Med Desc");

        Finding f3 = new Finding();
        f3.setFilePath("src/main/Test3.java");
        f3.setLineNumber(30);
        f3.setSeverity("INFO");
        f3.setTitle("Info Title");
        f3.setDescription("Info Desc");
        
        reviewEngine.reviewToReturn = review;
        findingRepository.findingsToReturn = List.of(f1, f2, f3);
        githubService.files = List.of();

        prReviewService.processPullRequest("owner", "repo", 1, "sha123");

        Map<String, Object> output = githubService.updatedCheckRunOutput;
        assertNotNull(output, "Output should not be null");
        String summary = (String) output.get("summary");

        // Verify summary contains scores
        assertTrue(summary.contains("**Overall** | 85/100"));
        assertTrue(summary.contains("**Security** | 90/100"));
        assertTrue(summary.contains("**Quality** | 80/100"));
        assertTrue(summary.contains("**Architecture** | 85/100"));
        
        // Verify summary contains finding counts
        assertTrue(summary.contains("- CRITICAL: 1"));
        assertTrue(summary.contains("- MEDIUM: 1"));

        // Verify annotations mapped correctly
        List<Map<String, Object>> annotations = (List<Map<String, Object>>) output.get("annotations");
        assertEquals(3, annotations.size());

        Map<String, Object> ann1 = annotations.get(0);
        assertEquals("src/main/Test1.java", ann1.get("path"));
        assertEquals(10, ann1.get("start_line"));
        assertEquals("failure", ann1.get("annotation_level")); // CRITICAL -> failure
        assertEquals("Crit Title", ann1.get("title"));

        Map<String, Object> ann2 = annotations.get(1);
        assertEquals("src/main/Test2.java", ann2.get("path"));
        assertEquals(20, ann2.get("start_line"));
        assertEquals("warning", ann2.get("annotation_level")); // MEDIUM -> warning
        assertEquals("Med Title", ann2.get("title"));
        
        Map<String, Object> ann3 = annotations.get(2);
        assertEquals("src/main/Test3.java", ann3.get("path"));
        assertEquals(30, ann3.get("start_line"));
        assertEquals("notice", ann3.get("annotation_level")); // INFO -> notice
        assertEquals("Info Title", ann3.get("title"));
    }
}
