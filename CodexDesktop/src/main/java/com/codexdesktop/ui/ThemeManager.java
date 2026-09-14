package com.codexdesktop.ui;

import com.codexdesktop.model.AppSettings;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Objects;

/**
 * Applies the stylesheet pair (base + theme) to a scene and swaps themes at runtime.
 *
 * <p>"System" resolves through the Windows apps-use-light-theme registry value; when that cannot
 * be read the app stays dark, which is the better default for a tool kept open all day.
 */
public final class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    private final Scene scene;
    private String appliedTheme = "";

    public ThemeManager(Scene scene) {
        this.scene = scene;
    }

    public void apply(String themeSetting) {
        String resolved = resolve(themeSetting);
        if (resolved.equals(appliedTheme)) {
            return;
        }
        appliedTheme = resolved;
        scene.getStylesheets().setAll(
                stylesheet("/css/base.css"),
                stylesheet(AppSettings.THEME_LIGHT.equals(resolved) ? "/css/light.css" : "/css/dark.css"));
        log.debug("Applied {} theme", resolved);
    }

    public String appliedTheme() {
        return appliedTheme;
    }

    private static String resolve(String themeSetting) {
        if (AppSettings.THEME_LIGHT.equals(themeSetting) || AppSettings.THEME_DARK.equals(themeSetting)) {
            return themeSetting;
        }
        return detectWindowsTheme();
    }

    private static String detectWindowsTheme() {
        try {
            ProcessBuilder builder = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            if (output.contains("0x1")) {
                return AppSettings.THEME_LIGHT;
            }
        } catch (Exception e) {
            log.debug("Could not read Windows theme preference: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
        return AppSettings.THEME_DARK;
    }

    private static String stylesheet(String resourcePath) {
        URL url = Objects.requireNonNull(ThemeManager.class.getResource(resourcePath),
                "Missing stylesheet " + resourcePath);
        return url.toExternalForm();
    }
}
