package com.codexdesktop.ui;

import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Registers the fonts shipped with the application.
 *
 * <p>D2Coding is bundled so the interface looks the same on every machine, without asking the user
 * to install anything. The file is a TrueType <em>collection</em> (regular plus bold), which needs
 * {@link Font#loadFonts} rather than {@code loadFont} — the singular call only reads the first face
 * and would leave bold text falling back to a different family.
 */
public final class FontLoader {

    private static final Logger log = LoggerFactory.getLogger(FontLoader.class);
    private static final String FONT_RESOURCE = "/fonts/D2Coding.ttc";

    private static final Set<String> loadedFamilies = new LinkedHashSet<>();

    private FontLoader() {
    }

    /** Idempotent: loading the same collection twice would register duplicate faces. */
    public static synchronized void loadBundledFonts() {
        if (!loadedFamilies.isEmpty()) {
            return;
        }
        try (InputStream stream = FontLoader.class.getResourceAsStream(FONT_RESOURCE)) {
            if (stream == null) {
                log.warn("Bundled font {} is missing; falling back to system fonts", FONT_RESOURCE);
                return;
            }
            Font[] fonts = Font.loadFonts(stream, 13);
            if (fonts == null || fonts.length == 0) {
                log.warn("Bundled font {} could not be registered", FONT_RESOURCE);
                return;
            }
            List<String> names = new ArrayList<>();
            for (Font font : fonts) {
                loadedFamilies.add(font.getFamily());
                names.add(font.getName());
            }
            log.info("Loaded bundled fonts: {}", names);
        } catch (Exception e) {
            log.warn("Could not load bundled font {}: {}", FONT_RESOURCE, e.getMessage());
        }
    }

    /** Families registered from the bundle; empty when the bundle was unavailable. */
    public static Set<String> loadedFamilies() {
        return Set.copyOf(loadedFamilies);
    }

    public static boolean isD2CodingAvailable() {
        return loadedFamilies.stream().anyMatch(family -> family.toLowerCase(java.util.Locale.ROOT)
                .startsWith("d2coding"));
    }

    /**
     * Interface fonts the user can choose from, filtered to what this machine actually has.
     *
     * <p>D2Coding ships with the app and reads well for code but is light for body text, so a few
     * heavier alternatives are offered. The list is ordered by how well each one renders Hangul
     * and Latin together.
     */
    public static List<String> availableUiFonts() {
        loadBundledFonts();
        List<String> candidates = List.of(
                "D2Coding",
                "Pretendard Variable", "Pretendard",
                "Segoe UI Variable Text", "Segoe UI",
                "Noto Sans KR", "Malgun Gothic",
                "Nanum Gothic", "NanumSquare", "Nanum Barun Gothic",
                "Cascadia Code", "Consolas",
                "Apple SD Gothic Neo");
        var installed = new java.util.HashSet<>(javafx.scene.text.Font.getFamilies());
        installed.addAll(loadedFamilies);
        List<String> available = new ArrayList<>();
        for (String candidate : candidates) {
            if (installed.contains(candidate) && !available.contains(candidate)) {
                available.add(candidate);
            }
        }
        return available;
    }
}
