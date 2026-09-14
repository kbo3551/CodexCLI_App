package com.codexdesktop.model;

import java.nio.file.Path;
import java.util.Set;

/**
 * A file the user attached to the next message.
 *
 * <p>Both path forms are kept for the same reason as {@link ProjectInfo}: the Windows path is what
 * the file dialog produced, the WSL path is what Codex can actually open.
 *
 * @param image true when it should be sent as an image input rather than a file mention
 */
public record Attachment(String name, Path windowsPath, String wslPath, boolean image, long sizeBytes) {

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");

    public static boolean looksLikeImage(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
    }

    public String describeSize() {
        if (sizeBytes <= 0) {
            return "";
        }
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024 * 1024) {
            return Math.round(sizeBytes / 1024.0) + " KB";
        }
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
