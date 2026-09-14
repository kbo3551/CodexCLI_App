package com.codexdesktop.codex;

import com.codexdesktop.model.AppSettings;
import com.codexdesktop.model.ThreadConfig;
import com.codexdesktop.model.UsageSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the production classes against the real {@code codex app-server} inside WSL.
 *
 * <p>This is the app's own code path — {@link WslCodexHost}, {@link CodexAppServerClient} and
 * {@link CodexSessionService} — not a stand-in script, so a pass means the desktop client really
 * talks to the installed Codex. It is tagged {@code integration} and excluded from the default
 * build because it starts a process, calls the model and consumes quota.
 *
 * <p>Run with:
 * {@code mvn test -Dgroups=integration -Dsurefire.excludedGroups=none}
 */
@Tag("integration")
class CodexSessionIntegrationTest {

    private static final long TURN_TIMEOUT_SECONDS = 240;

    private static Path workspace;

    @BeforeAll
    static void startToolkitAndWorkspace() throws Exception {
        // CodexSessionService marshals events with Platform.runLater, so the toolkit must be up.
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(60, TimeUnit.SECONDS), "JavaFX toolkit did not start");

        workspace = Files.createTempDirectory("codex-desktop-it");
        Files.writeString(workspace.resolve("README.md"), "# IT workspace\n\nline1\n");
    }

    @AfterAll
    static void stopToolkit() {
        Platform.exit();
    }

    @Test
    void connectsStartsAThreadStreamsATurnAndShutsDownCleanly() throws Exception {
        String distribution = System.getProperty("codex.it.distribution", "Ubuntu-24.04");
        String wslWorkspace = toWslPath(workspace);

        WslCodexHost host = new WslCodexHost();
        CodexAppServerClient client = new CodexAppServerClient(host, new ObjectMapper());
        CodexSessionService session = new CodexSessionService(client);

        Recorder recorder = new Recorder(session);
        runOnFxThread(() -> session.addListener(recorder));

        AppSettings settings = AppSettings.defaults()
                .withRuntime("wsl.exe", distribution, "codex", "app-server --stdio")
                .withPolicy("on-request", "read-only")
                .withLanguage("English");

        try {
            // --- connect ------------------------------------------------------------------
            session.connect(settings, wslWorkspace).get(120, TimeUnit.SECONDS);
            assertTrue(host.isRunning(), "app-server process should be running");
            assertTrue(host.pid() > 0, "app-server pid should be known");
            assertEquals(ConnectionState.CONNECTED, session.state());

            // --- thread/start -------------------------------------------------------------
            String threadId = session.startThread(wslWorkspace, settings).get(120, TimeUnit.SECONDS);
            assertNotNull(threadId);
            assertFalse(threadId.isBlank(), "thread id should be returned");
            waitUntil(() -> !recorder.threadConfig.get().model().isBlank(), 30);
            ThreadConfig config = recorder.threadConfig.get();
            assertFalse(config.model().isBlank(), "model should be reported by thread/start");
            System.out.println("[IT] thread " + threadId + " model=" + config.model()
                    + " approvals=" + config.approvalPolicy() + " sandbox=" + config.sandboxMode());

            // --- turn/start: a read-only sandbox forces a real approval for the edit -------
            session.sendUserMessage(
                    "Append the line 'it ok' to README.md with apply_patch, then reply in one short sentence.")
                    .get(120, TimeUnit.SECONDS);

            waitUntil(() -> recorder.turnStatus.get() != null, TURN_TIMEOUT_SECONDS);
            assertEquals("completed", recorder.turnStatus.get(), "turn should complete");

            assertTrue(recorder.approvals.size() >= 1,
                    "a fileChange under a read-only sandbox must request approval");
            assertTrue(recorder.itemTypes.contains(CodexProtocol.ITEM_FILE_CHANGE),
                    "expected a fileChange item, saw " + recorder.itemTypes);
            assertFalse(recorder.deltas.isEmpty(), "assistant text should stream in deltas");
            assertFalse(recorder.lastDiff.get().isBlank(), "turn/diff/updated should carry a diff");
            assertTrue(recorder.lastDiff.get().contains("it ok"),
                    "diff should contain the requested change, was: " + recorder.lastDiff.get());
            assertTrue(Files.readString(workspace.resolve("README.md")).contains("it ok"),
                    "Codex should have edited the real file in the workspace");
            UsageSnapshot usage = recorder.usage.get();
            assertNotNull(usage, "token usage should be reported");
            assertTrue(usage.tokens().totalTokens() > 0, "token usage should be non-zero");
            System.out.println("[IT] tokens=" + usage.tokens().totalTokens()
                    + " context=" + usage.tokens().contextUsedPercent() + "%"
                    + " approvals=" + recorder.approvals
                    + " items=" + recorder.itemTypes);

            // --- interrupt ----------------------------------------------------------------
            recorder.reset();
            session.sendUserMessage("Run `sleep 45` with the shell tool.").get(120, TimeUnit.SECONDS);
            waitUntil(session::isTurnActive, 60);
            Thread.sleep(8_000);
            session.interruptTurn().get(60, TimeUnit.SECONDS);
            waitUntil(() -> recorder.turnStatus.get() != null, 90);
            assertEquals("interrupted", recorder.turnStatus.get(),
                    "turn/interrupt should end the turn as interrupted");
        } finally {
            session.shutdown();
        }

        // --- shutdown ---------------------------------------------------------------------
        waitUntil(() -> !host.isRunning(), 20);
        assertFalse(host.isRunning(), "the app-server process must be gone after shutdown");
        System.out.println("[IT] app-server stopped, pid " + host.pid() + " no longer alive");
    }

    /** Collects everything the UI would react to. */
    private static final class Recorder implements CodexSessionListener {
        private final CodexSessionService session;
        final List<String> itemTypes = new CopyOnWriteArrayList<>();
        final List<String> deltas = new CopyOnWriteArrayList<>();
        final List<String> approvals = new CopyOnWriteArrayList<>();
        final AtomicReference<String> lastDiff = new AtomicReference<>("");
        final AtomicReference<String> turnStatus = new AtomicReference<>();
        final AtomicReference<UsageSnapshot> usage = new AtomicReference<>();
        final AtomicReference<ThreadConfig> threadConfig = new AtomicReference<>(ThreadConfig.empty());

        Recorder(CodexSessionService session) {
            this.session = session;
        }

        void reset() {
            turnStatus.set(null);
            approvals.clear();
        }

        @Override
        public void onItemCompleted(String itemId, String itemType, JsonNode item) {
            itemTypes.add(itemType);
        }

        @Override
        public void onAgentMessageDelta(String itemId, String delta) {
            deltas.add(delta);
        }

        @Override
        public void onTurnDiff(String diff) {
            lastDiff.set(diff);
        }

        @Override
        public void onTurnCompleted(String turnId, String status, JsonNode turn) {
            turnStatus.set(status);
        }

        @Override
        public void onUsageChanged(UsageSnapshot snapshot) {
            usage.set(snapshot);
        }

        @Override
        public void onThreadConfigured(ThreadConfig config) {
            threadConfig.set(config);
        }

        /** Approves automatically, exactly as the approval card does when the user clicks Allow. */
        @Override
        public void onApprovalRequested(ApprovalRequest request) {
            approvals.add(request.kind().name());
            session.resolveApproval(request, ApprovalRequest.ACCEPT);
        }
    }

    private static void runOnFxThread(Runnable action) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "FX task did not run");
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
    }

    /**
     * Uses the production converter — the same code the app runs — because passing a Windows path
     * through {@code wsl.exe} as an argument loses the backslashes.
     */
    private static String toWslPath(Path windowsPath) {
        var converter = new com.codexdesktop.service.WindowsWslPathConverter(
                new com.codexdesktop.service.WslCommandRunner("wsl.exe", ""));
        String converted = converter.toWslPath(windowsPath.toAbsolutePath().toString());
        assertTrue(converted.startsWith("/"), "expected a Linux path, got " + converted);
        return converted;
    }
}
