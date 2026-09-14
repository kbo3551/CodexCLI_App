package com.codexdesktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Persisted application settings. Deserialized leniently so an older or newer settings file
 * never prevents the app from starting.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppSettings(String wslExecutable,
                          String distribution,
                          String codexExecutable,
                          String codexArguments,
                          String theme,
                          String language,
                          String fontScale,
                          String uiFont,
                          boolean debugLogging,
                          String approvalPolicy,
                          String sandboxMode) {

    public static final String THEME_SYSTEM = "System";
    public static final String THEME_DARK = "Dark";
    public static final String THEME_LIGHT = "Light";

    public static final String SCALE_COMPACT = "Compact";
    public static final String SCALE_NORMAL = "Normal";
    public static final String SCALE_LARGE = "Large";

    /** Empty means "use the font stack from base.css". */
    public static final String FONT_DEFAULT = "";

    /** Empty means "inherit whatever ~/.codex/config.toml already says". */
    public static final String INHERIT = "";

    public static AppSettings defaults() {
        return new AppSettings("wsl.exe", "", "codex", "app-server --stdio",
                THEME_DARK, "System", SCALE_NORMAL, FONT_DEFAULT, false, INHERIT, INHERIT);
    }

    /** Normalizes blank fields to their defaults so callers never deal with nulls. */
    public AppSettings normalized() {
        AppSettings defaults = defaults();
        return new AppSettings(
                blankTo(wslExecutable, defaults.wslExecutable()),
                distribution == null ? "" : distribution.trim(),
                blankTo(codexExecutable, defaults.codexExecutable()),
                blankTo(codexArguments, defaults.codexArguments()),
                blankTo(theme, defaults.theme()),
                blankTo(language, defaults.language()),
                blankTo(fontScale, defaults.fontScale()),
                uiFont == null ? FONT_DEFAULT : uiFont.trim(),
                debugLogging,
                approvalPolicy == null ? INHERIT : approvalPolicy.trim(),
                sandboxMode == null ? INHERIT : sandboxMode.trim());
    }

    /** Splits {@link #codexArguments()} on whitespace into an argument vector. */
    public List<String> codexArgumentList() {
        String value = blankTo(codexArguments, defaults().codexArguments());
        return List.of(value.trim().split("\\s+"));
    }

    /** True when the user pinned a specific interface font. */
    public boolean hasCustomFont() {
        return uiFont != null && !uiFont.isBlank();
    }

    /**
     * Copy helpers used instead of the positional constructor.
     *
     * <p>Callers (including tests) build from {@link #defaults()} and override what they care about,
     * so adding a field here does not ripple out into every construction site.
     */
    public AppSettings withRuntime(String wsl, String distro, String executable, String arguments) {
        return new AppSettings(wsl, distro, executable, arguments, theme, language, fontScale,
                uiFont, debugLogging, approvalPolicy, sandboxMode).normalized();
    }

    public AppSettings withPolicy(String approval, String sandbox) {
        return new AppSettings(wslExecutable, distribution, codexExecutable, codexArguments, theme,
                language, fontScale, uiFont, debugLogging, approval, sandbox).normalized();
    }

    public AppSettings withLanguage(String newLanguage) {
        return new AppSettings(wslExecutable, distribution, codexExecutable, codexArguments, theme,
                newLanguage, fontScale, uiFont, debugLogging, approvalPolicy, sandboxMode).normalized();
    }

    /** Root font size in pixels for the selected text size. */
    public double rootFontSize() {
        return switch (blankTo(fontScale, SCALE_NORMAL)) {
            case SCALE_COMPACT -> 12.5;
            case SCALE_LARGE -> 15.0;
            default -> 13.5;
        };
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
