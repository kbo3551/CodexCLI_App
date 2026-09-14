package com.codexdesktop.ui;

import javafx.css.CssParser;
import javafx.css.Stylesheet;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the stylesheets with the same parser JavaFX uses at runtime.
 *
 * <p>A malformed rule does not fail loudly in an application — the affected declarations are simply
 * dropped and a region silently falls back to the stock light theme. Catching that here is much
 * cheaper than noticing a white panel in a screenshot.
 */
class StylesheetTest {

    private static final List<String> STYLESHEETS =
            List.of("/css/base.css", "/css/dark.css", "/css/light.css");

    @Test
    void everyStylesheetParsesWithoutErrors() throws java.io.IOException {
        for (String resource : STYLESHEETS) {
            URL url = StylesheetTest.class.getResource(resource);
            assertNotNull(url, "missing stylesheet " + resource);

            CssParser.errorsProperty().clear();
            Stylesheet stylesheet = new CssParser().parse(url);

            assertNotNull(stylesheet, "parser returned nothing for " + resource);
            assertTrue(CssParser.errorsProperty().isEmpty(),
                    resource + " has CSS errors: " + CssParser.errorsProperty());
            // Theme files legitimately hold a single .root rule full of tokens; base.css is large.
            int expectedMinimum = resource.contains("base") ? 40 : 1;
            assertTrue(stylesheet.getRules().size() >= expectedMinimum,
                    resource + " parsed only " + stylesheet.getRules().size()
                            + " rules, which suggests it was truncated");
        }
    }

    /** The colour tokens are referenced by name from base.css, so both themes must define them. */
    @Test
    void bothThemesDefineTheSameTokens() {
        List<String> required = List.of("-c-bg", "-c-surface", "-c-elevated", "-c-card",
                "-c-input-bg", "-c-code-bg", "-c-border", "-c-border-strong", "-c-text",
                "-c-text-dim", "-c-text-muted", "-c-hover", "-c-pressed", "-c-selected",
                "-c-selection", "-c-focus", "-c-accent", "-c-accent-hover", "-c-accent-pressed",
                "-c-accent-text", "-c-accent-dim", "-c-success", "-c-danger", "-c-danger-dim",
                "-c-warn", "-c-warn-dim", "-c-user-bubble", "-c-code-inline", "-c-diff-add-bg",
                "-c-diff-add-text", "-c-diff-del-bg", "-c-diff-del-text", "-c-scroll-thumb",
                "-c-scroll-thumb-hover");

        for (String theme : List.of("/css/dark.css", "/css/light.css")) {
            String text = readResource(theme);
            for (String token : required) {
                assertTrue(text.contains(token + ":"), theme + " is missing " + token);
            }
        }
    }

    /** Every token used by base.css must exist in the themes, or that property silently drops. */
    @Test
    void baseCssOnlyUsesDefinedTokens() {
        String base = readResource("/css/base.css");
        String dark = readResource("/css/dark.css");
        var used = new java.util.TreeSet<String>();
        var matcher = java.util.regex.Pattern.compile("-c-[a-z0-9-]+").matcher(base);
        while (matcher.find()) {
            used.add(matcher.group());
        }
        var missing = used.stream().filter(token -> !dark.contains(token + ":")).sorted().toList();
        assertTrue(missing.isEmpty(), "base.css uses tokens the themes do not define: " + missing);
    }

    private static String readResource(String resource) {
        try (var stream = StylesheetTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "missing " + resource);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("could not read " + resource, e);
        }
    }
}
