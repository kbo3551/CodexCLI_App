package com.codexdesktop;

/**
 * Launcher used by the packaged application.
 *
 * <p>When JavaFX is on the classpath rather than the module path, a main class that extends
 * {@code Application} is rejected with "JavaFX runtime components are missing". Delegating from a
 * plain class avoids that, which lets {@code jpackage} bundle the app as ordinary jars plus a
 * custom runtime.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        CodexDesktopApplication.main(args);
    }
}
