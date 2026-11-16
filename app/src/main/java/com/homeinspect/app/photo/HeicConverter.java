package com.homeinspect.app.photo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Converts HEIC (iPhone photos) to PNG with libheif's {@code heif-thumbnailer -p}, which renders
 * the primary image with its rotation applied. Java has no HEIC decoder of its own.
 */
@Slf4j
@Component
class HeicConverter {

    private static final int MAX_SIZE = 4096;

    private final String command;

    HeicConverter(PhotoProperties properties) {
        this.command = properties.heicCommand();
    }

    byte[] toPng(byte[] heic) {
        if (!StringUtils.hasText(command)) {
            throw new ImageRejectedException("photo.error.heic");
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("heic");
            Path input = dir.resolve("input.heic");
            Path output = dir.resolve("output.png");
            Files.write(input, heic);
            Process process = new ProcessBuilder(List.of(command, "-p", "-s", String.valueOf(MAX_SIZE),
                    input.toString(), output.toString()))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ImageRejectedException("photo.error.heic");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
                throw new ImageRejectedException("photo.error.invalid");
            }
            return Files.readAllBytes(output);
        } catch (IOException e) {
            // Command not installed (e.g. running outside Docker)
            log.warn("HEIC conversion unavailable ({}): {}", command, e.getMessage());
            throw new ImageRejectedException("photo.error.heic", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageRejectedException("photo.error.heic", e);
        } finally {
            if (dir != null) {
                try (var files = Files.list(dir)) {
                    for (Path file : files.toList()) {
                        Files.deleteIfExists(file);
                    }
                    Files.deleteIfExists(dir);
                } catch (IOException ignored) {
                    // temp files: best effort
                }
            }
        }
    }
}
