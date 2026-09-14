package com.codexdesktop.ui;

import com.codexdesktop.model.FileMatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drives the real {@link Composer} to prove the {@code @} pipeline is wired end to end:
 * text change -> caret -> fragment -> debounce -> search.
 *
 * <p>Written because manual checks were inconclusive — synthetic keystrokes never reached the field,
 * so there was no evidence either way about the wiring itself. Here the text is set through the
 * control found in the scene graph, which exercises exactly the listeners the app relies on.
 */
class ComposerMentionWiringTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        } catch (UnsupportedOperationException | Error unavailable) {
            return;
        }
        started.await(60, TimeUnit.SECONDS);
    }

    @Test
    void typingAnAtTokenTriggersAFileSearch() throws Exception {
        assumeTrue(toolkitRunning(), "JavaFX toolkit unavailable");

        AtomicReference<String> queried = new AtomicReference<>();
        CountDownLatch searched = new CountDownLatch(1);
        AtomicReference<Composer> holder = new AtomicReference<>();

        runFx(() -> {
            Composer composer = new Composer();
            composer.setFileSearch(query -> {
                queried.set(query);
                searched.countDown();
                return CompletableFuture.completedFuture(List.of(
                        new FileMatch("/mnt/c/dev", "src/log4j.xml", "log4j.xml", false, 100)));
            });
            // A scene is needed for the controls to be laid out and for CSS to resolve.
            new Scene(new StackPane(composer), 900, 200);
            composer.readyProperty().set(true);
            holder.set(composer);
        });

        TextArea input = findInput(holder.get());
        runFx(() -> input.replaceText(0, 0, "@log"));

        assertTrue(searched.await(10, TimeUnit.SECONDS),
                "no file search was triggered for '@log'");
        assertEquals("log", queried.get());
    }

    /** A bare '@' is not a query yet, and an email address must not trigger anything. */
    @Test
    void doesNotSearchWithoutAUsableFragment() throws Exception {
        assumeTrue(toolkitRunning(), "JavaFX toolkit unavailable");

        AtomicReference<String> queried = new AtomicReference<>();
        AtomicReference<Composer> holder = new AtomicReference<>();
        runFx(() -> {
            Composer composer = new Composer();
            composer.setFileSearch(query -> {
                queried.set(query);
                return CompletableFuture.completedFuture(List.of());
            });
            new Scene(new StackPane(composer), 900, 200);
            composer.readyProperty().set(true);
            holder.set(composer);
        });

        TextArea input = findInput(holder.get());
        runFx(() -> input.replaceText(0, 0, "mail@example"));
        Thread.sleep(600);
        assertEquals(null, queried.get(), "an email address must not open the picker");

        runFx(() -> input.replaceText(0, input.getText().length(), "@"));
        Thread.sleep(600);
        assertEquals(null, queried.get(), "a bare @ is not a query");
    }

    private static TextArea findInput(Composer composer) {
        assertTrue(composer != null, "composer was not created");
        var node = composer.lookup(".composer-input");
        assertTrue(node instanceof TextArea, "composer input not found in the scene graph");
        return (TextArea) node;
    }

    private static void runFx(Runnable action) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "FX task did not run");
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

    private static boolean toolkitRunning() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(latch::countDown);
            return latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IllegalStateException notStarted) {
            return false;
        }
    }
}
