package com.codexdesktop.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * UI text lookup.
 *
 * <p>Deliberately a small static holder: the bundle is chosen once at startup (or when the user
 * changes the language and the shell is rebuilt), and every widget reads through {@link #t}.
 * A missing key returns the key itself so a gap is visible instead of throwing.
 */
public final class I18n {

    private static final Logger log = LoggerFactory.getLogger(I18n.class);
    private static final String BUNDLE = "i18n.messages";

    public static final String LANGUAGE_SYSTEM = "System";
    public static final String LANGUAGE_ENGLISH = "English";
    public static final String LANGUAGE_KOREAN = "\ud55c\uad6d\uc5b4";

    private static ResourceBundle bundle = load(Locale.getDefault());
    private static Locale locale = Locale.getDefault();
    private static String currentSetting = LANGUAGE_SYSTEM;

    private I18n() {
    }

    /** @param languageSetting one of {@link #LANGUAGE_SYSTEM}, {@link #LANGUAGE_ENGLISH}, {@link #LANGUAGE_KOREAN} */
    public static void setLanguage(String languageSetting) {
        currentSetting = languageSetting == null ? LANGUAGE_SYSTEM : languageSetting;
        Locale resolved = switch (currentSetting) {
            case LANGUAGE_KOREAN -> Locale.KOREAN;
            // The base bundle is English, so ROOT selects it without a language-specific file.
            case LANGUAGE_ENGLISH -> Locale.ROOT;
            default -> Locale.getDefault();
        };
        locale = resolved;
        bundle = load(resolved);
        log.info("UI language set to {}", resolved);
    }

    /** The setting value currently in effect, for change detection. */
    public static String currentSetting() {
        return currentSetting;
    }

    public static Locale locale() {
        return locale;
    }

    public static boolean isKorean() {
        return "ko".equals(locale.getLanguage());
    }

    public static String t(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public static String t(String key, Object... args) {
        String pattern = t(key);
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    private static ResourceBundle load(Locale requested) {
        try {
            // Properties files are UTF-8 from Java 9 onwards, so no escaping is needed.
            //
            // getNoFallbackControl matters: the default getBundle() falls back to the *default
            // locale's* bundle before the base bundle, so asking for English on a Korean Windows
            // install would otherwise return the Korean text.
            return ResourceBundle.getBundle(BUNDLE, requested,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        } catch (MissingResourceException e) {
            log.debug("No bundle for {}, using the base bundle", requested);
        }
        try {
            return ResourceBundle.getBundle(BUNDLE, Locale.ROOT,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        } catch (MissingResourceException e) {
            log.warn("Missing resource bundle {}", BUNDLE);
            throw e;
        }
    }
}
