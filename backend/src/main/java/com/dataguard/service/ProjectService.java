package com.dataguard.service;

import com.dataguard.entity.Project;
import com.dataguard.entity.Review;
import com.dataguard.entity.User;
import com.dataguard.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectService {

    @Value("${dataguard.review.max-uncompressed-bytes:104857600}")
    private long maxUncompressedBytes;

    private final ProjectRepository projectRepository;
    private final ReviewEngine reviewEngine;

    public Review processProjectUpload(MultipartFile file, User user, String projectName) throws IOException {
        // 1. Save Project Entity
        Project project = new Project();
        project.setName(projectName);
        project.setUser(user);
        project.setTechnology("Java / Spring Boot");
        project.setCreatedAt(LocalDateTime.now());
        project = projectRepository.save(project);

        // 2. Extract ZIP
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "dataguard_" + project.getId());
        tempDir.mkdirs();
        extractZip(file, tempDir);

        // 3. Trigger Review Engine
        return reviewEngine.runReview(project, tempDir);
    }

    private void extractZip(MultipartFile file, File destDir) throws IOException {
        long extractedBytes = 0;
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            extractedBytes += len;
                            if (extractedBytes > maxUncompressedBytes) {
                                throw new IOException("Archive expands beyond the configured review size limit.");
                            }
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        return destFile;
    }

    public ProjectService(ProjectRepository projectRepository, ReviewEngine reviewEngine) {
        this.projectRepository = projectRepository;
        this.reviewEngine = reviewEngine;
    }
}
