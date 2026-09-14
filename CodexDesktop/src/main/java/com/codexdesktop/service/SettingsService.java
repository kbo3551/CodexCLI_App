package com.codexdesktop.service;

import com.codexdesktop.model.AppSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads and stores {@link AppSettings} as JSON under {@code %LOCALAPPDATA%\CodexDesktop}.
 *
 * <p>An unreadable or corrupt file falls back to defaults instead of failing startup.
 */
public final class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final String FILE_NAME = "settings.json";

    private final ObjectMapper mapper;
    private final Path file;
    private volatile AppSettings settings;

    public SettingsService(ObjectMapper mapper) {
        this(mapper, appDataDirectory().resolve(FILE_NAME));
    }

    SettingsService(ObjectMapper mapper, Path file) {
        this.mapper = mapper;
        this.file = file;
        this.settings = load();
    }

    /** {@code %LOCALAPPDATA%\CodexDesktop}, falling back to the user home. */
    public static Path appDataDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = (localAppData == null || localAppData.isBlank())
                ? Paths.get(System.getProperty("user.home"), ".codexdesktop")
                : Paths.get(localAppData, "CodexDesktop");
        return base;
    }

    public AppSettings get() {
        return settings;
    }

    public synchronized void update(AppSettings updated) {
        this.settings = updated.normalized();
        save();
    }

    private AppSettings load() {
        try {
            if (Files.exists(file)) {
                AppSettings loaded = mapper.readValue(Files.readString(file), AppSettings.class);
                if (loaded != null) {
                    return loaded.normalized();
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read {} ({}), using defaults", file, e.getMessage());
        }
        return AppSettings.defaults();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings));
            log.debug("Settings saved to {}", file);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not write settings to {}: {}", file, e.getMessage());
        }
    }
}
