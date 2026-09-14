package com.codexdesktop.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class I18nTest {

    @AfterEach
    void restoreDefault() {
        I18n.setLanguage(I18n.LANGUAGE_SYSTEM);
    }

    /**
     * Regression guard: {@code ResourceBundle.getBundle} falls back to the default locale's bundle
     * before the base bundle, so on a Korean Windows install "English" used to yield Korean text.
     */
    @Test
    void englishSelectsTheBaseBundleEvenWhenTheSystemLocaleIsKorean() {
        I18n.setLanguage(I18n.LANGUAGE_ENGLISH);
        assertEquals("New thread", I18n.t("sidebar.newThread"));
        assertEquals("Settings", I18n.t("sidebar.settings"));
        assertFalse(I18n.isKorean());
    }

    @Test
    void koreanSelectsTheKoreanBundle() {
        I18n.setLanguage(I18n.LANGUAGE_KOREAN);
        assertEquals("새 스레드", I18n.t("sidebar.newThread"));
        assertEquals("설정", I18n.t("sidebar.settings"));
        assertNotEquals(I18n.t("sidebar.newThread"), "New thread");
    }

    @Test
    void formatsArguments() {
        I18n.setLanguage(I18n.LANGUAGE_ENGLISH);
        assertEquals("limit 86%", I18n.t("usage.limitShort", 86));
        assertEquals("WSL Ubuntu-24.04", I18n.t("status.wsl", "Ubuntu-24.04"));
    }

    @Test
    void unknownKeysReturnTheKeyInsteadOfThrowing() {
        assertEquals("no.such.key", I18n.t("no.such.key"));
    }

    /** Every key in the base bundle must exist in the Korean bundle, or the UI shows raw keys. */
    @Test
    void koreanBundleCoversEveryBaseKey() {
        I18n.setLanguage(I18n.LANGUAGE_ENGLISH);
        var base = bundle(java.util.Locale.ROOT);
        var korean = bundle(java.util.Locale.KOREAN);
        var missing = base.keySet().stream().filter(key -> !korean.containsKey(key)).sorted().toList();
        assertEquals(java.util.List.of(), missing, "keys missing from messages_ko.properties");
    }

    /**
     * Catches two keys glued onto one line.
     *
     * <p>Appending to a properties file that does not end in a newline silently merges the last
     * existing entry with the first new one; both keys then resolve to junk. No message text
     * contains an '=', so its presence in a value means exactly that happened.
     */
    @Test
    void noValueSwallowedAnotherKey() {
        for (var locale : java.util.List.of(java.util.Locale.ROOT, java.util.Locale.KOREAN)) {
            var resource = bundle(locale);
            for (String key : resource.keySet()) {
                String value = resource.getString(key);
                assertFalse(value.contains("="),
                        locale + " bundle: '" + key + "' looks like two glued entries -> " + value);
            }
        }
    }

    /** Both bundles must have the same key set, so neither drifts ahead of the other. */
    @Test
    void bothBundlesHaveTheSameKeys() {
        var base = bundle(java.util.Locale.ROOT).keySet();
        var korean = bundle(java.util.Locale.KOREAN).keySet();
        var onlyKorean = korean.stream().filter(key -> !base.contains(key)).sorted().toList();
        assertEquals(java.util.List.of(), onlyKorean, "keys only present in messages_ko.properties");
    }

    private static java.util.ResourceBundle bundle(java.util.Locale locale) {
        return java.util.ResourceBundle.getBundle("i18n.messages", locale,
                java.util.ResourceBundle.Control.getNoFallbackControl(
                        java.util.ResourceBundle.Control.FORMAT_PROPERTIES));
    }
}
