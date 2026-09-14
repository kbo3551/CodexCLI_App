package com.codexdesktop.ui;

import javafx.application.Platform;
import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the bundled-font path: a {@code .ttc} collection must register every face, otherwise bold
 * text silently falls back to a different family.
 */
class FontLoaderTest {

    @Test
    void registersD2CodingFromTheBundledCollection() throws Exception {
        assumeTrue(startToolkit(), "JavaFX toolkit unavailable in this environment");

        FontLoader.loadBundledFonts();

        assertTrue(FontLoader.isD2CodingAvailable(),
                "D2Coding should be registered, loaded families: " + FontLoader.loadedFamilies());
        assertTrue(Font.getFontNames().stream().anyMatch(name -> name.startsWith("D2Coding")),
                "D2Coding faces should be visible to JavaFX");
        // The collection carries a bold face; without loadFonts() only the regular one would appear.
        assertTrue(Font.getFontNames().stream().anyMatch(name -> name.toLowerCase().contains("bold")
                        && name.startsWith("D2Coding")),
                "the bold face from the collection should also be registered: " + Font.getFontNames());
    }

    private static boolean startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            return true;
        } catch (UnsupportedOperationException | Error unavailable) {
            return false;
        }
        return started.await(60, TimeUnit.SECONDS);
    }
}
