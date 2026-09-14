package com.codexdesktop.codex;

import com.codexdesktop.model.ThreadConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Covers the path behind "the model I picked is not reflected at the bottom": choosing a model has
 * to reach every listener, because that is what repaints the status bar and the top-bar button.
 */
class CodexSessionSelectionTest {

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

    private CodexSessionService newSession() {
        // No process is started: the client only needs a host to write to, and this test never
        // sends anything.
        CodexHost host = new WslCodexHost();
        return new CodexSessionService(new CodexAppServerClient(host, new ObjectMapper()));
    }

    @Test
    void selectingAModelNotifiesListenersWithTheNewConfig() throws Exception {
        assumeTrue(Platform.isFxApplicationThread() || toolkitRunning(),
                "JavaFX toolkit unavailable");
        CodexSessionService session = newSession();
        AtomicReference<ThreadConfig> seen = new AtomicReference<>();
        CountDownLatch notified = new CountDownLatch(1);
        session.addListener(new CodexSessionListener() {
            @Override
            public void onThreadConfigured(ThreadConfig config) {
                seen.set(config);
                notified.countDown();
            }
        });

        session.selectModel("gpt-5.6-luna", "high");

        assertTrue(notified.await(15, TimeUnit.SECONDS), "no onThreadConfigured callback arrived");
        assertEquals("gpt-5.6-luna", seen.get().model());
        assertEquals("high", seen.get().reasoningEffort());
        assertEquals("gpt-5.6-luna", session.threadConfig().model());
        assertEquals("gpt-5.6-luna", session.modelName());
    }

    /** Choosing only an effort must keep the current model. */
    @Test
    void selectingOnlyAnEffortKeepsTheModel() throws Exception {
        assumeTrue(toolkitRunning(), "JavaFX toolkit unavailable");
        CodexSessionService session = newSession();
        session.selectModel("gpt-6-astra", "medium");

        CountDownLatch notified = new CountDownLatch(1);
        AtomicReference<ThreadConfig> seen = new AtomicReference<>();
        session.addListener(new CodexSessionListener() {
            @Override
            public void onThreadConfigured(ThreadConfig config) {
                seen.set(config);
                notified.countDown();
            }
        });

        session.selectModel("", "xhigh");

        assertTrue(notified.await(15, TimeUnit.SECONDS), "no callback for the effort change");
        assertEquals("gpt-6-astra", seen.get().model());
        assertEquals("xhigh", seen.get().reasoningEffort());
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
