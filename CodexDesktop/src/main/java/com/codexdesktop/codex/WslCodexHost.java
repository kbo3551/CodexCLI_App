package com.codexdesktop.codex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Runs {@code codex app-server --stdio} inside WSL through {@code wsl.exe}.
 *
 * <p>Design notes:
 * <ul>
 *   <li>stdout and stderr are drained on separate virtual threads, so neither pipe can fill up
 *       and deadlock the child, and no blocking read ever happens on the JavaFX thread.</li>
 *   <li>All three streams are treated as UTF-8. {@code WSL_UTF8=1} is exported so that
 *       {@code wsl.exe}'s own error text arrives as UTF-8 rather than UTF-16LE.</li>
 *   <li>Shutdown closes stdin first — the app-server exits cleanly on EOF — and only escalates
 *       to {@code destroy()} / {@code destroyForcibly()} if it does not. Only the process this
 *       instance created is ever touched; no {@code pkill}-style sweeps.</li>
 * </ul>
 */
public final class WslCodexHost implements CodexHost {

    private static final Logger log = LoggerFactory.getLogger(WslCodexHost.class);

    private static final long GRACEFUL_EXIT_MILLIS = 3_000;
    private static final long DESTROY_EXIT_MILLIS = 2_000;

    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private volatile Process process;
    private volatile BufferedWriter stdin;
    private volatile String commandDescription = "";
    private volatile Consumer<String> stdoutListener = line -> { };
    private volatile Consumer<String> stderrListener = line -> { };
    private volatile IntConsumer exitListener = code -> { };

    @Override
    public synchronized void start(CodexLaunchSpec spec) throws IOException {
        if (isRunning()) {
            throw new IllegalStateException("Codex host is already running");
        }
        stopping.set(false);

        List<String> command = buildCommand(spec);
        commandDescription = String.join(" ", command);
        log.info("Starting Codex host: {}", commandDescription);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("WSL_UTF8", "1");
        builder.redirectErrorStream(false);

        Process started = builder.start();
        this.process = started;
        this.stdin = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));

        log.info("Codex host started, pid={}", started.pid());

        pump("codex-stdout", started.getInputStream(), line -> stdoutListener.accept(line));
        pump("codex-stderr", started.getErrorStream(), line -> stderrListener.accept(line));

        started.onExit().thenAccept(p -> {
            int code = p.exitValue();
            if (stopping.get()) {
                log.info("Codex host exited after shutdown request, code={}", code);
            } else {
                log.warn("Codex host exited unexpectedly, code={}", code);
            }
            exitListener.accept(code);
        });
    }

    /**
     * Builds the {@code wsl.exe} argument vector.
     *
     * <p>An executable without a path separator is resolved through a login shell so the user's
     * normal {@code PATH} (nvm, ~/.local/bin, …) applies exactly as it does in their terminal.
     * An absolute path is executed directly, which avoids the extra shell entirely.
     */
    static List<String> buildCommand(CodexLaunchSpec spec) {
        List<String> command = new ArrayList<>();
        command.add(blankToDefault(spec.wslExecutable(), "wsl.exe"));
        if (isNotBlank(spec.distribution())) {
            command.add("-d");
            command.add(spec.distribution().trim());
        }
        if (isNotBlank(spec.workingDirectory())) {
            command.add("--cd");
            command.add(spec.workingDirectory().trim());
        }
        command.add("--");

        String executable = blankToDefault(spec.codexExecutable(), "codex").trim();
        if (executable.contains("/")) {
            command.add(executable);
            command.addAll(spec.codexArguments());
        } else {
            StringBuilder script = new StringBuilder(shellQuote(executable));
            for (String argument : spec.codexArguments()) {
                script.append(' ').append(shellQuote(argument));
            }
            command.add("bash");
            command.add("-lc");
            command.add(script.toString());
        }
        return command;
    }

    /** Single-quotes a token for POSIX shells so user-supplied values cannot inject commands. */
    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void pump(String name, InputStream stream, Consumer<String> sink) {
        Thread.ofVirtual().name(name).start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), 1 << 16)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        sink.accept(line);
                    } catch (RuntimeException e) {
                        log.warn("{} listener failed", name, e);
                    }
                }
            } catch (IOException e) {
                if (!stopping.get()) {
                    log.debug("{} closed: {}", name, e.getMessage());
                }
            }
        });
    }

    @Override
    public void send(String jsonLine) {
        BufferedWriter writer = this.stdin;
        Process current = this.process;
        if (writer == null || current == null || !current.isAlive()) {
            log.debug("Dropping outbound message, host not running");
            return;
        }
        synchronized (this) {
            try {
                writer.write(jsonLine);
                writer.write('\n');
                writer.flush();
            } catch (IOException e) {
                log.warn("Failed to write to Codex host stdin: {}", e.getMessage());
            }
        }
    }

    @Override
    public void stop() {
        Process current = this.process;
        if (current == null) {
            return;
        }
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        log.info("Stopping Codex host pid={}", current.pid());

        BufferedWriter writer = this.stdin;
        if (writer != null) {
            synchronized (this) {
                try {
                    writer.close();
                } catch (IOException e) {
                    log.debug("Closing stdin failed: {}", e.getMessage());
                }
            }
            this.stdin = null;
        }

        try {
            if (current.waitFor(GRACEFUL_EXIT_MILLIS, TimeUnit.MILLISECONDS)) {
                log.info("Codex host stopped gracefully");
                return;
            }
            log.info("Codex host did not exit on stdin EOF, destroying process tree");
            current.descendants().forEach(ProcessHandle::destroy);
            current.destroy();
            if (current.waitFor(DESTROY_EXIT_MILLIS, TimeUnit.MILLISECONDS)) {
                return;
            }
            log.warn("Codex host still alive, forcing termination");
            current.descendants().forEach(ProcessHandle::destroyForcibly);
            current.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        }
    }

    @Override
    public boolean isRunning() {
        Process current = this.process;
        return current != null && current.isAlive();
    }

    @Override
    public long pid() {
        Process current = this.process;
        return current == null ? -1L : current.pid();
    }

    @Override
    public String describeCommand() {
        return commandDescription;
    }

    @Override
    public void setStdoutLineListener(Consumer<String> listener) {
        this.stdoutListener = listener == null ? line -> { } : listener;
    }

    @Override
    public void setStderrLineListener(Consumer<String> listener) {
        this.stderrListener = listener == null ? line -> { } : listener;
    }

    @Override
    public void setExitListener(IntConsumer listener) {
        this.exitListener = listener == null ? code -> { } : listener;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToDefault(String value, String fallback) {
        return isNotBlank(value) ? value : fallback;
    }
}
