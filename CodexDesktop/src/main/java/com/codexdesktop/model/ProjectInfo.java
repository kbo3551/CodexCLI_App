package com.codexdesktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A workspace the user opened. Both path forms are kept: the Windows path for the file dialog
 * and display, the WSL path because that is what Codex receives.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectInfo(String id,
                          String name,
                          String windowsPath,
                          String wslPath,
                          String lastThreadId,
                          long lastOpenedAt) {

    public ProjectInfo withLastThread(String threadId) {
        return new ProjectInfo(id, name, windowsPath, wslPath, threadId, System.currentTimeMillis());
    }

    public ProjectInfo touched() {
        return new ProjectInfo(id, name, windowsPath, wslPath, lastThreadId, System.currentTimeMillis());
    }
}
