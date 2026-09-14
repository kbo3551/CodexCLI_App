package com.codexdesktop.codex;

import com.codexdesktop.model.AppSettings;
import com.codexdesktop.model.FileMatch;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@code @} mention search against the real app-server.
 *
 * <p>No turn is started, so this costs no model quota — it only proves that the request the composer
 * sends actually comes back with usable paths.
 *
 * <p>{@code mvn test -Dgroups=integration -Dsurefire.excludedGroups=none}
 */
@Tag("integration")
class CodexFileSearchIntegrationTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(60, TimeUnit.SECONDS), "JavaFX toolkit did not start");
    }

    @Test
    void findsFilesInTheProjectThroughTheAppServer() throws Exception {
        String distribution = System.getProperty("codex.it.distribution", "Ubuntu-24.04");
        Path projectDirectory = Path.of("").toAbsolutePath();
        assertTrue(Files.exists(projectDirectory.resolve("pom.xml")),
                "expected to run from the project directory, was " + projectDirectory);

        var converter = new com.codexdesktop.service.WindowsWslPathConverter(
                new com.codexdesktop.service.WslCommandRunner("wsl.exe", distribution));
        String root = converter.toWslPath(projectDirectory.toString());

        WslCodexHost host = new WslCodexHost();
        CodexAppServerClient client = new CodexAppServerClient(host,
                new com.fasterxml.jackson.databind.ObjectMapper());
        CodexSessionService session = new CodexSessionService(client);

        AppSettings settings = AppSettings.defaults()
                .withRuntime("wsl.exe", distribution, "codex", "app-server --stdio")
                .withLanguage("English");

        try {
            session.connect(settings, root).get(120, TimeUnit.SECONDS);
            assertEquals(ConnectionState.CONNECTED, session.state());

            List<FileMatch> matches = session.searchFiles("Composer", root, 20)
                    .get(60, TimeUnit.SECONDS);
            assertFalse(matches.isEmpty(), "expected hits for 'Composer' under " + root);
            System.out.println("[IT] 'Composer' -> " + matches.size() + " hits");
            matches.stream().limit(3).forEach(match ->
                    System.out.println("[IT]   " + match.fileName() + "  " + match.path()
                            + "  dir=" + match.directory()));

            FileMatch best = matches.get(0);
            assertTrue(best.absolutePath().startsWith("/"),
                    "mentions need an absolute WSL path, got " + best.absolutePath());
            assertTrue(best.absolutePath().endsWith(best.path()),
                    "absolute path should end with the relative path");
            assertTrue(matches.stream().anyMatch(m -> "Composer.java".equals(m.fileName())),
                    "Composer.java should be among the hits: "
                            + matches.stream().map(FileMatch::fileName).toList());

            // A directory query must be reported as a directory, which the picker renders as Dir.
            List<FileMatch> office = session.searchFiles("office", root, 20)
                    .get(60, TimeUnit.SECONDS);
            assertTrue(office.stream().anyMatch(FileMatch::directory),
                    "expected at least one directory hit for 'office'");

            // Blank queries must not be sent at all.
            assertTrue(session.searchFiles("  ", root, 20).get(10, TimeUnit.SECONDS).isEmpty());
        } finally {
            session.shutdown();
        }

        assertFalse(host.isRunning(), "app-server should be gone after shutdown");
    }
}
