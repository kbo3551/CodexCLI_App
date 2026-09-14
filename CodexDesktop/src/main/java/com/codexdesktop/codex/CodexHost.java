package com.codexdesktop.codex;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Owns the operating-system process that hosts {@code codex app-server --stdio}.
 *
 * <p>Only the transport differs between implementations; the protocol above it is identical.
 * {@link WslCodexHost} is the only implementation today. A future local-Windows or remote host
 * would slot in here without touching the client or the UI.
 */
public interface CodexHost {

    /** Starts the process. Throws if it cannot be spawned at all. */
    void start(CodexLaunchSpec spec) throws IOException;

    /** Writes one JSON line to the process stdin. No-op when not running. */
    void send(String jsonLine);

    /** Graceful shutdown (stdin EOF), escalating to destroy and then forcible destroy. */
    void stop();

    boolean isRunning();

    /** Native pid of the launched process, or -1. */
    long pid();

    /** Human-readable description of the command that was launched, for diagnostics. */
    String describeCommand();

    void setStdoutLineListener(Consumer<String> listener);

    void setStderrLineListener(Consumer<String> listener);

    /** Invoked once with the exit code after the process terminates. */
    void setExitListener(IntConsumer listener);
}
