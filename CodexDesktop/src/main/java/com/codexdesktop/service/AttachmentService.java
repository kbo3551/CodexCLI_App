package com.codexdesktop.service;

import com.codexdesktop.model.Attachment;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

/**
 * Turns Windows files and clipboard images into {@link Attachment}s with WSL paths.
 *
 * <p>Pasted images have no file on disk, so they are written into
 * {@code %LOCALAPPDATA%\CodexDesktop\attachments} first; that folder lives under {@code /mnt/c},
 * which Codex can read inside WSL. Old files there are pruned on startup.
 */
public final class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final long PRUNE_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final WindowsWslPathConverter pathConverter;
    private final Path attachmentDirectory;

    public AttachmentService(WindowsWslPathConverter pathConverter) {
        this(pathConverter, SettingsService.appDataDirectory().resolve("attachments"));
    }

    AttachmentService(WindowsWslPathConverter pathConverter, Path attachmentDirectory) {
        this.pathConverter = pathConverter;
        this.attachmentDirectory = attachmentDirectory;
    }

    /** @throws IllegalArgumentException when the file is unreadable or has no WSL equivalent */
    public Attachment fromFile(Path windowsFile) {
        Path absolute = windowsFile.toAbsolutePath();
        if (!Files.isRegularFile(absolute)) {
            throw new IllegalArgumentException("not a file");
        }
        long size;
        try {
            size = Files.size(absolute);
        } catch (IOException e) {
            size = 0;
        }
        String wslPath = pathConverter.toWslPath(absolute.toString());
        String name = absolute.getFileName() == null ? absolute.toString() : absolute.getFileName().toString();
        return new Attachment(name, absolute, wslPath, Attachment.looksLikeImage(absolute), size);
    }

    /** Persists a clipboard or dragged image so Codex has a real path to read. */
    public Attachment fromImage(Image image) throws IOException {
        Files.createDirectories(attachmentDirectory);
        String fileName = "pasted-" + LocalDateTime.now().format(STAMP) + ".png";
        Path target = attachmentDirectory.resolve(fileName);
        javax.imageio.ImageIO.write(toBufferedImage(image), "png", target.toFile());
        log.debug("Saved pasted image to {}", target);
        return fromFile(target);
    }

    /**
     * Copies a JavaFX image into a {@code BufferedImage} for {@code ImageIO}.
     *
     * <p>Done by hand rather than via {@code SwingFXUtils} so the app does not depend on the
     * JavaFX/Swing bridge module at all — only {@code java.desktop}'s PNG encoder is used.
     */
    private static java.awt.image.BufferedImage toBufferedImage(Image image) throws IOException {
        var reader = image.getPixelReader();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (reader == null || width <= 0 || height <= 0) {
            throw new IOException("clipboard image could not be decoded");
        }
        var buffered = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                row[x] = reader.getArgb(x, y);
            }
            buffered.setRGB(0, y, width, 1, row, 0, width);
        }
        return buffered;
    }

    /** Removes attachment scratch files older than a week; runs off the UI thread. */
    public void pruneOldAttachments() {
        Thread.ofVirtual().name("attachment-prune").start(() -> {
            if (!Files.isDirectory(attachmentDirectory)) {
                return;
            }
            long cutoff = System.currentTimeMillis() - PRUNE_AFTER_MILLIS;
            try (var files = Files.list(attachmentDirectory)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        if (Files.getLastModifiedTime(file).toMillis() < cutoff) {
                            Files.deleteIfExists(file);
                        }
                    } catch (IOException e) {
                        log.trace("Could not prune {}: {}", file, e.getMessage());
                    }
                });
            } catch (IOException e) {
                log.debug("Could not list {}: {}", attachmentDirectory, e.getMessage());
            }
        });
    }
}
