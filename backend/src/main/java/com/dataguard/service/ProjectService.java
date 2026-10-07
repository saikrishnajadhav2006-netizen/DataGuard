package com.dataguard.service;

import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.exception.ZipValidationException;
import com.dataguard.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles ZIP upload validation and extraction.
 *
 * <h2>Security controls</h2>
 * <ul>
 *   <li>Max upload size: {@code dataguard.upload.max-upload-bytes} (default 5 MB)</li>
 *   <li>Max extracted size: {@code dataguard.upload.max-uncompressed-bytes} (default 25 MB)</li>
 *   <li>Max file count: {@code dataguard.upload.max-files} (default 200)</li>
 *   <li>Max single file size: {@code dataguard.upload.max-single-file-bytes} (default 1 MB)</li>
 *   <li>Path traversal protection: rejects {@code ../} and absolute paths</li>
 *   <li>ZIP bomb protection: rejects archives that expand beyond the uncompressed limit</li>
 *   <li>Temp dir cleanup is delegated to ReviewEngine via the returned dir reference</li>
 * </ul>
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    @Value("${dataguard.upload.max-upload-bytes:5242880}")
    private long maxUploadBytes;

    @Value("${dataguard.upload.max-uncompressed-bytes:26214400}")
    private long maxUncompressedBytes;

    @Value("${dataguard.upload.max-files:200}")
    private int maxFiles;

    @Value("${dataguard.upload.max-single-file-bytes:1048576}")
    private long maxSingleFileBytes;

    private final ProjectRepository projectRepository;
    private final ReviewEngine reviewEngine;

    public ProjectService(ProjectRepository projectRepository, ReviewEngine reviewEngine) {
        this.projectRepository = projectRepository;
        this.reviewEngine = reviewEngine;
    }

    /**
     * Validates the uploaded ZIP, extracts it safely, runs the review pipeline,
     * and returns the completed Review.
     *
     * @throws ZipValidationException if the upload violates any security constraint
     * @throws IOException            if extraction fails due to I/O errors
     */
    public Review processProjectUpload(MultipartFile file, User user, String projectName)
            throws ZipValidationException, IOException {

        // 1. Check raw upload size
        long uploadSize = file.getSize();
        if (uploadSize > maxUploadBytes) {
            throw new ZipValidationException(
                    String.format("ZIP archive is too large. Maximum allowed: %d bytes (%s). Received: %d bytes.",
                            maxUploadBytes, humanReadable(maxUploadBytes), uploadSize));
        }

        // 2. Check Content-Type (best-effort; not relied on for security alone)
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !contentType.contains("zip") && !contentType.contains("octet-stream")) {
            log.warn("Unexpected content-type '{}' — proceeding with ZIP validation.", contentType);
        }

        // 3. Save Project entity
        Project project = new Project();
        project.setName(projectName);
        project.setUser(user);
        project.setTechnology("Auto-detected");
        project.setCreatedAt(LocalDateTime.now());
        project = projectRepository.save(project);

        // 4. Create a unique temp directory for this project
        File tempDir = Files.createTempDirectory("dataguard-" + project.getId() + "-").toFile();
        log.info("Extracting project '{}' to temp dir: {}", projectName, tempDir.getAbsolutePath());

        // 5. Extract ZIP with all security checks
        try {
            extractZip(file.getInputStream(), tempDir);
        } catch (ZipValidationException e) {
            // Clean up the empty temp dir before rethrowing
            deleteQuietly(tempDir);
            throw e;
        } catch (IOException e) {
            deleteQuietly(tempDir);
            throw new IOException("ZIP extraction failed: " + e.getMessage(), e);
        }

        // 6. Trigger Review Engine (it will clean up tempDir)
        return reviewEngine.runReview(project, tempDir);
    }

    // -------------------------------------------------------------------------
    // ZIP extraction
    // -------------------------------------------------------------------------

    private void extractZip(InputStream inputStream, File destDir) throws ZipValidationException, IOException {
        long totalExtractedBytes = 0;
        int fileCount = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry = zis.getNextEntry();

            if (zipEntry == null) {
                throw new ZipValidationException("The ZIP archive is empty or not a valid ZIP file.");
            }

            while (zipEntry != null) {
                // Security: reject absolute paths
                String entryName = zipEntry.getName();
                if (entryName.startsWith("/") || (entryName.length() > 1 && entryName.charAt(1) == ':')) {
                    throw new ZipValidationException(
                            "ZIP entry has an absolute path and was rejected for security reasons: " + entryName);
                }

                // Security: reject path traversal
                File destFile = resolveDestination(destDir, zipEntry);

                if (zipEntry.isDirectory()) {
                    if (!destFile.isDirectory() && !destFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + destFile);
                    }
                } else {
                    fileCount++;
                    if (fileCount > maxFiles) {
                        throw new ZipValidationException(
                                String.format("ZIP contains more than %d files. Reduce project size.", maxFiles));
                    }

                    // Ensure parent directory exists
                    File parent = destFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory: " + parent);
                    }

                    // Extract and check individual file and total sizes
                    long fileBytes = 0;
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fileBytes += len;
                            totalExtractedBytes += len;

                            if (fileBytes > maxSingleFileBytes) {
                                throw new ZipValidationException(
                                        String.format("A single file in the ZIP exceeds the maximum allowed size " +
                                                "of %s: %s", humanReadable(maxSingleFileBytes), entryName));
                            }

                            if (totalExtractedBytes > maxUncompressedBytes) {
                                throw new ZipValidationException(
                                        String.format("Extracted project exceeds maximum size of %s. " +
                                                "This may indicate a ZIP bomb.", humanReadable(maxUncompressedBytes)));
                            }

                            fos.write(buffer, 0, len);
                        }
                    }
                }

                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
        }

        log.info("Extracted {} files, {} total bytes.", fileCount, totalExtractedBytes);
    }

    /**
     * Resolves the destination path for a ZIP entry, rejecting any entry
     * whose canonical path escapes the destination directory.
     */
    private File resolveDestination(File destDir, ZipEntry entry) throws ZipValidationException, IOException {
        File destFile = new File(destDir, entry.getName());
        String destDirCanonical = destDir.getCanonicalPath();
        String destFileCanonical = destFile.getCanonicalPath();

        // Normalise separator for cross-platform comparison
        if (!destFileCanonical.startsWith(destDirCanonical + File.separator)
                && !destFileCanonical.equals(destDirCanonical)) {
            throw new ZipValidationException(
                    "ZIP path traversal detected and rejected: " + entry.getName());
        }
        return destFile;
    }

    private String humanReadable(long bytes) {
        if (bytes >= 1_048_576) return (bytes / 1_048_576) + " MB";
        if (bytes >= 1_024)    return (bytes / 1_024) + " KB";
        return bytes + " B";
    }

    private void deleteQuietly(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            java.nio.file.Files.walk(dir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.warn("Could not clean up temp dir {}: {}", dir, e.getMessage());
        }
    }
}

