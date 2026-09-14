package com.codexdesktop.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One hit from {@code fuzzyFileSearch}, used by the {@code @} mention picker.
 *
 * <p>The server returns the search root separately from a project-relative path, which is exactly
 * what the UI wants: show the short path, send the absolute one.
 *
 * @param root      absolute WSL path the search ran under
 * @param path      path relative to {@code root}
 * @param directory true when the hit is a folder rather than a file
 */
public record FileMatch(String root, String path, String fileName, boolean directory, int score) {

    public static FileMatch from(JsonNode node) {
        return new FileMatch(
                node.path("root").asText(""),
                node.path("path").asText(""),
                node.path("file_name").asText(""),
                "directory".equals(node.path("match_type").asText("file")),
                node.path("score").asInt(0));
    }

    /** Absolute WSL path, which is what Codex needs in a mention. */
    public String absolutePath() {
        if (root.isBlank()) {
            return path;
        }
        String separator = root.endsWith("/") ? "" : "/";
        return root + separator + path;
    }

    /** The folder part of the relative path, for the dim second line in the picker. */
    public String parentPath() {
        int index = path.lastIndexOf('/');
        return index <= 0 ? "" : path.substring(0, index);
    }
}
