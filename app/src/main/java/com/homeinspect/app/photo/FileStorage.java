package com.homeinspect.app.photo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Files on disk (Docker volume): photos per report, signatures and company logo.
 * Paths are built only from ids and generated keys, never from user input.
 */
@Slf4j
@Component
public class FileStorage {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path root;

    public FileStorage(@Value("${home-inspect.storage.root}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path photo(long reportId, String fileName) {
        return resolve("photos/" + reportId, fileName);
    }

    public Path photosOf(long reportId) {
        return root.resolve("photos").resolve(Long.toString(reportId));
    }

    /** {@code dir} relative to the root (signatures, branding...). */
    public Path resolve(String dir, String fileName) {
        if (!SAFE.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Unsafe file name");
        }
        Path path = root.resolve(dir).resolve(fileName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path outside the storage");
        }
        return path;
    }

    /** Writes atomically (temporary file + move). */
    public void write(Path target, byte[] content) {
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        }
    }

    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Cannot delete {}: {}", path, e.getMessage());
        }
    }

    public void deleteDirectory(Path dir) {
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException e) {
            log.warn("Cannot delete {}: {}", dir, e.getMessage());
        }
    }
}
