package com.dataguard.service;

import com.dataguard.dto.FixedProjectResponse;
import com.dataguard.dto.FixResponse;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Private, review-scoped source and fixed ZIP artifacts; never writes into the uploaded archive. */
@Service
public class ZipProjectArchiveService {
    private static final long DEFAULT_EXPANDED = 100L * 1024 * 1024;
    private static final long DEFAULT_FILE = 1L * 1024 * 1024;
    private static final long DEFAULT_ARCHIVE = 100L * 1024 * 1024;
    private static final int DEFAULT_FILES = 200;
    private static final java.util.regex.Pattern SECRET_ASSIGNMENT = java.util.regex.Pattern.compile(
            "(?im)^\\s*(?:export\\s+)?[A-Za-z0-9_]*(?:API_KEY|ACCESS_TOKEN|CLIENT_SECRET|PASSWORD|PRIVATE_KEY|AUTH_TOKEN)[A-Za-z0-9_]*\\s*[:=]\\s*['\\\"]?([A-Za-z0-9_./+=-]{10,})['\\\"]?\\s*(?:#.*)?$");
    private static final java.util.regex.Pattern KNOWN_TOKEN = java.util.regex.Pattern.compile(
            "(?i)(?:AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{30,}|sk-[A-Za-z0-9_-]{24,}|eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,})");
    private final Path root;
    private final ObjectMapper mapper;
    private final long maxExpanded, maxFile, maxArchive;
    private final int maxFiles;

    public ZipProjectArchiveService(ObjectMapper mapper,
            @Value("${dataguard.archive.storage-dir:${java.io.tmpdir}/dataguard-projects}") String storage,
            @Value("${dataguard.upload.max-uncompressed-bytes:104857600}") long maxExpanded,
            @Value("${dataguard.upload.max-single-file-bytes:1048576}") long maxFile,
            @Value("${dataguard.upload.max-files:200}") int maxFiles,
            @Value("${dataguard.archive.max-output-bytes:104857600}") long maxArchive) {
        this.mapper = mapper;
        this.root = Path.of(storage).toAbsolutePath().normalize();
        this.maxExpanded = maxExpanded;
        this.maxFile = maxFile;
        this.maxFiles = maxFiles;
        this.maxArchive = maxArchive;
    }

    public synchronized void retainOriginal(Long reviewId, byte[] bytes) throws IOException {
        if (reviewId == null || bytes == null || bytes.length == 0) throw new IOException("Missing uploaded ZIP data.");
        Path dir = reviewDir(reviewId);
        Files.createDirectories(dir);
        restrictDirectory(dir);
        Path original = dir.resolve("original.zip");
        Files.write(original, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        restrictFile(original);
        try { validateArchive(original); }
        catch (Exception e) { Files.deleteIfExists(original); deleteTree(dir); throw new IOException("Uploaded ZIP did not pass export validation.", e); }
    }

    public synchronized FixedProjectResponse approveFix(Review review, Finding finding, FixResponse proposal,
                                                          int totalFindings) throws IOException {
        if (review == null || finding == null || finding.getReview() == null
                || !Objects.equals(review.getId(), finding.getReview().getId()))
            throw new IllegalArgumentException("Finding does not belong to this review.");
        if (!"sec-python-sql-concat".equals(finding.getRuleId()) || proposal == null || proposal.patch() == null
                || proposal.suggestedCode() == null || proposal.originalCode() == null)
            throw new IllegalStateException("No supported safe ZIP fix is available for this finding.");
        if (!Objects.equals(finding.getId(), proposal.findingId())
                || !Objects.equals(finding.getFilePath(), proposal.filePath())
                || !Objects.equals(finding.getLineNumber(), proposal.lineNumber())
                || !Objects.equals(finding.getEvidence(), proposal.originalCode()))
            throw new IllegalStateException("The proposed change does not match this finding's reviewed source context.");
        validateRelativePath(finding.getFilePath());
        Path dir = reviewDir(review.getId());
        restrictDirectory(dir);
        Path original = dir.resolve("original.zip");
        if (!Files.isRegularFile(original)) throw new IllegalStateException("The private original project archive is unavailable.");
        Path manifest = dir.resolve("approved.json");
        Map<String, ApprovedPatch> approved = Files.exists(manifest)
                ? mapper.readValue(manifest.toFile(), new TypeReference<>() {}) : new LinkedHashMap<>();
        String key = String.valueOf(finding.getId());
        ApprovedPatch patch = new ApprovedPatch(finding.getFilePath(), finding.getLineNumber(),
                finding.getEvidence(), proposal.suggestedCode());
        ApprovedPatch existing = approved.get(key);
        if (existing != null && !existing.equals(patch)) throw new IllegalStateException("This finding has a different approved patch already.");
        approved.put(key, patch);

        Path staging = Files.createTempDirectory(dir, "export-");
        Path tempZip = Files.createTempFile(dir, "fixed-", ".zip.tmp");
        try {
            Extraction extraction = extract(original, staging);
            for (ApprovedPatch approvedPatch : approved.values()) applyPatch(staging, approvedPatch);
            Set<String> changed = new TreeSet<>();
            approved.values().forEach(p -> changed.add(p.path));
            int contentOmitted = zipTree(staging, tempZip);
            verifyArchive(tempZip, changed);
            Path fixed = dir.resolve("fixed.zip");
            try { Files.move(tempZip, fixed, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(tempZip, fixed, StandardCopyOption.REPLACE_EXISTING); }
            mapper.writeValue(manifest.toFile(), approved);
            restrictFile(manifest);
            int omitted = extraction.omitted + contentOmitted;
            Files.writeString(dir.resolve("omitted-count.txt"), Integer.toString(omitted), StandardCharsets.US_ASCII);
            restrictFile(dir.resolve("omitted-count.txt"));
            restrictFile(fixed);
            return new FixedProjectResponse(true, "/api/reviews/" + review.getId() + "/fixed-project/download",
                    "project-fixed.zip", changed.size(), Math.max(0, totalFindings - approved.size()), omitted,
                    "Approved changes are included. Review the archive before using it; findings without approved supported fixes remain unresolved.");
        } finally {
            Files.deleteIfExists(tempZip);
            deleteTree(staging);
        }
    }

    public synchronized Optional<FixedProjectResponse> status(Review review, List<Finding> findings) {
        if (review == null) return Optional.empty();
        Path dir = reviewDir(review.getId());
        if (!Files.isRegularFile(dir.resolve("original.zip"))) return Optional.empty();
        Path fixed = dir.resolve("fixed.zip");
        if (!Files.isRegularFile(fixed)) {
            return Optional.of(new FixedProjectResponse(false, null, "project-fixed.zip", 0,
                    findings == null ? 0 : findings.size(), 0,
                    "Approve a supported fix to create a verified ZIP. The original upload remains unchanged."));
        }
        try {
            Map<String, ApprovedPatch> approved = mapper.readValue(reviewDir(review.getId()).resolve("approved.json").toFile(), new TypeReference<>() {});
            int modified = (int) approved.values().stream().map(p -> p.path).distinct().count();
            long unresolved = findings == null ? 0 : findings.stream().filter(f -> !approved.containsKey(String.valueOf(f.getId()))).count();
            return Optional.of(new FixedProjectResponse(true, "/api/reviews/" + review.getId() + "/fixed-project/download",
                    "project-fixed.zip", modified, (int) unresolved, readOmitted(review.getId()),
                    "Verified archive ready. " + unresolved + " findings remain without an approved automatic patch."));
        } catch (Exception e) { return Optional.empty(); }
    }

    /** Extracts the verified fixed archive into a new temp directory for re-analysis. Caller must delete the directory. */
    public synchronized Path extractFixedProjectTree(Review review) throws IOException {
        if (review == null) throw new NoSuchElementException();
        Path fixed = reviewDir(review.getId()).resolve("fixed.zip");
        if (!Files.isRegularFile(fixed)) throw new NoSuchElementException();
        Map<String, ApprovedPatch> approved = mapper.readValue(reviewDir(review.getId()).resolve("approved.json").toFile(), new TypeReference<>() {});
        Set<String> expected = new HashSet<>();
        approved.values().forEach(p -> expected.add(p.path));
        verifyArchive(fixed, expected);
        Path destination = Files.createTempDirectory("dataguard-rereview-");
        extract(fixed, destination);
        return destination;
    }

    public synchronized File openVerified(Review review) throws IOException {
        if (review == null) throw new NoSuchElementException();
        Path fixed = reviewDir(review.getId()).resolve("fixed.zip");
        if (!Files.isRegularFile(fixed)) throw new NoSuchElementException();
        Map<String, ApprovedPatch> approved = mapper.readValue(reviewDir(review.getId()).resolve("approved.json").toFile(), new TypeReference<>() {});
        Set<String> expected = new HashSet<>(); approved.values().forEach(p -> expected.add(p.path));
        verifyArchive(fixed, expected);
        return fixed.toFile();
    }

    private Extraction extract(Path archive, Path destination) throws IOException {
        long expanded = 0; int files = 0, omitted = 0; Set<String> seen = new HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName(); validateRelativePath(name.endsWith("/") ? name.substring(0, name.length()-1) : name);
                if (!seen.add(name)) throw new IOException("ZIP contains duplicate entry paths.");
                if (entry.isDirectory()) { Path d = safeResolve(destination, name); Files.createDirectories(d); continue; }
                files++; if (files > maxFiles) throw new IOException("ZIP exceeds configured file count.");
                if (excluded(name)) { omitted++; continue; }
                Path out = safeResolve(destination, name); Files.createDirectories(out.getParent());
                long one = 0; byte[] buffer = new byte[8192];
                try (OutputStream stream = Files.newOutputStream(out, StandardOpenOption.CREATE_NEW)) {
                    int n; while ((n = zin.read(buffer)) != -1) {
                        one += n; expanded += n;
                        if (one > maxFile || expanded > maxExpanded) throw new IOException("ZIP exceeds configured extraction limits.");
                        stream.write(buffer, 0, n);
                    }
                }
            }
        }
        if (files == 0) throw new IOException("ZIP is empty or invalid.");
        return new Extraction(omitted);
    }

    private void applyPatch(Path root, ApprovedPatch patch) throws IOException {
        validateRelativePath(patch.path);
        if (patch.line < 1 || patch.line > 1_000_000 || patch.original == null || patch.replacement == null
                || patch.original.length() > 8192 || patch.replacement.length() > 8192) throw new IOException("Approved patch boundaries are invalid.");
        Path file = safeResolve(root, patch.path);
        if (!Files.isRegularFile(file) || Files.size(file) > maxFile) throw new IOException("Patched source file is missing or oversized.");
        String source = Files.readString(file, StandardCharsets.UTF_8);
        String[] lines = source.split("\\R", -1);
        if (patch.line > lines.length || !lines[patch.line - 1].equals(patch.original))
            throw new IOException("Approved patch no longer matches the original source context.");
        lines[patch.line - 1] = patch.replacement;
        Files.writeString(file, String.join("\n", lines), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private int zipTree(Path source, Path output) throws IOException {
        Files.deleteIfExists(output); long written = 0; int omitted = 0;
        try (ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
            try (var paths = Files.walk(source)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = source.relativize(file).toString().replace(File.separatorChar, '/');
                    validateRelativePath(relative);
                    if (excluded(relative) || containsSecretMaterial(file)) { omitted++; continue; }
                    if (Files.size(file) > maxFile) throw new IOException("Project file exceeds configured output limits.");
                    zout.putNextEntry(new ZipEntry(relative));
                    try (InputStream in = Files.newInputStream(file)) {
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = in.read(buffer)) != -1) { written += n; if (written > maxExpanded) throw new IOException("Fixed ZIP exceeds configured output size."); zout.write(buffer, 0, n); }
                    }
                    zout.closeEntry();
                }
            }
        }
        if (Files.size(output) > maxArchive) throw new IOException("Fixed ZIP exceeds configured archive size.");
        return omitted;
    }

    private void validateArchive(Path file) throws IOException {
        Path temp = Files.createTempDirectory("dataguard-zip-validation-");
        try { extract(file, temp); } finally { deleteTree(temp); }
    }

    private void verifyArchive(Path zip, Set<String> expectedModified) throws IOException {
        if (!Files.isRegularFile(zip) || Files.size(zip) <= 0 || Files.size(zip) > maxArchive) throw new IOException("ZIP output size is invalid.");
        Set<String> found = new HashSet<>(); long total = 0; int count = 0;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement(); if (e.isDirectory()) continue;
                validateRelativePath(e.getName()); if (excluded(e.getName())) throw new IOException("Archive contains an excluded sensitive path.");
                count++; if (count > maxFiles) throw new IOException("Archive exceeds configured file count."); found.add(e.getName());
                byte[] content;
                try (InputStream in = zf.getInputStream(e)) { content = in.readNBytes(Math.toIntExact(Math.min(maxFile + 1, Integer.MAX_VALUE))); }
                if (content.length > maxFile) throw new IOException("Archive contains a file above the configured size limit.");
                total += content.length; if (total > maxExpanded) throw new IOException("Archive exceeds expanded size limit.");
                if (containsSecretMaterial(content, e.getName())) throw new IOException("Archive contains material matching a secret pattern.");
            }
        }
        if (!found.containsAll(expectedModified)) throw new IOException("Archive is missing an expected modified file.");
    }

    private boolean excluded(String path) {
        String lower = path.toLowerCase(Locale.ROOT); String base = lower.substring(lower.lastIndexOf('/') + 1);
        return lower.startsWith(".git/") || lower.contains("/.git/") || lower.startsWith("node_modules/")
                || lower.contains("/node_modules/") || lower.startsWith(".dataguard/")
                || Arrays.stream(lower.split("/")).anyMatch(part -> part.equals(".aws") || part.equals(".ssh")
                    || part.equals("secrets") || part.equals("credentials") || part.equals("__pycache__")
                    || part.equals(".cache") || part.equals("tmp") || part.equals("temp"))
                || (base.equals(".env") || base.endsWith(".env") || base.startsWith(".env.")) && !base.equals(".env.example")
                || base.endsWith(".pem") || base.endsWith(".key") || base.endsWith(".p12") || base.endsWith(".pfx")
                || base.endsWith(".crt") || base.endsWith(".cer") || base.endsWith(".jks")
                || base.contains("secret") || base.contains("credential") || base.contains("password")
                || base.equals("id_rsa") || base.equals("id_ed25519")
                || base.endsWith(".swp") || base.endsWith(".tmp") || base.equals(".ds_store")
                || base.equals("thumbs.db") || base.endsWith("~");
    }

    private boolean containsSecretMaterial(Path file) throws IOException {
        if (Files.size(file) > maxFile) return false;
        return containsSecretMaterial(Files.readAllBytes(file), file.getFileName().toString());
    }

    private boolean containsSecretMaterial(byte[] bytes, String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.equals(".env.example")) return false;
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8))) return false;
        if (text.contains("-----BEGIN PRIVATE KEY-----") || text.contains("-----BEGIN RSA PRIVATE KEY-----")
                || text.contains("-----BEGIN OPENSSH PRIVATE KEY-----") || KNOWN_TOKEN.matcher(text).find()) return true;
        var matcher = SECRET_ASSIGNMENT.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!(value.startsWith("${") || value.startsWith("your_") || value.startsWith("example")
                    || value.startsWith("change") || value.startsWith("placeholder") || value.startsWith("replace")
                    || value.startsWith("xxxx"))) return true;
        }
        return false;
    }

    private void validateRelativePath(String name) throws IOException {
        if (name == null || name.isBlank() || name.length() > 512 || name.startsWith("/") || name.startsWith("\\")
                || name.contains("\\") || name.contains(":") || name.indexOf('\0') >= 0)
            throw new IOException("ZIP contains an invalid path.");
        for (String part : name.split("/")) if (part.isBlank() || part.equals(".") || part.equals("..")) throw new IOException("ZIP contains an unsafe path.");
    }

    private Path safeResolve(Path root, String name) throws IOException {
        Path resolved = root.resolve(name).normalize(); if (!resolved.startsWith(root.normalize())) throw new IOException("ZIP path escapes its project directory."); return resolved;
    }
    private Path reviewDir(Long id) { Path dir = root.resolve("review-" + id).normalize(); if (!dir.startsWith(root)) throw new IllegalArgumentException("Invalid review id."); return dir; }
    private int readOmitted(Long reviewId) {
        try { return Integer.parseInt(Files.readString(reviewDir(reviewId).resolve("omitted-count.txt")).trim()); }
        catch (Exception ignored) { return 0; }
    }
    private void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }
    private void restrictDirectory(Path path) {
        try { Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")); }
        catch (IOException | UnsupportedOperationException ignored) { }
    }
    private void restrictFile(Path path) {
        try { Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
        catch (IOException | UnsupportedOperationException ignored) { }
    }
    private record Extraction(int omitted) { }
    public record ApprovedPatch(String path, int line, String original, String replacement) { }
}
