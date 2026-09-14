package com.codexdesktop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs short-lived commands inside WSL and captures their output.
 *
 * <p>Used for diagnostics, git status and path conversion — never for the app-server itself,
 * which is long-lived and owned by {@code WslCodexHost}. Callers must not invoke this on the
 * JavaFX thread.
 */
public final class WslCommandRunner {

    private static final Logger log = LoggerFactory.getLogger(WslCommandRunner.class);

    /** One captured execution. */
    public record Result(int exitCode, String stdout, String stderr, String commandLine, boolean timedOut) {
        public boolean ok() {
            return exitCode == 0 && !timedOut;
        }

        public String stdoutTrimmed() {
            return stdout.strip();
        }

        public String firstErrorLine() {
            String text = stderr.isBlank() ? stdout : stderr;
            return text.lines().filter(line -> !line.isBlank()).findFirst().orElse("").strip();
        }
    }

    private final String wslExecutable;
    private final String distribution;

    public WslCommandRunner(String wslExecutable, String distribution) {
        this.wslExecutable = (wslExecutable == null || wslExecutable.isBlank()) ? "wsl.exe" : wslExecutable;
        this.distribution = distribution == null ? "" : distribution.trim();
    }

    /** Runs an argv directly, without a shell. */
    public Result run(long timeoutSeconds, String... argv) {
        List<String> command = new ArrayList<>();
        command.add(wslExecutable);
        if (!distribution.isBlank()) {
            command.add("-d");
            command.add(distribution);
        }
        command.add("--");
        command.addAll(List.of(argv));
        return execute(command, timeoutSeconds);
    }

    /** Runs a bash login-shell script, so the user's PATH applies. */
    public Result runLoginShell(long timeoutSeconds, String script) {
        return run(timeoutSeconds, "bash", "-lc", script);
    }

    /** Runs a bash login-shell script with an explicit working directory. */
    public Result runLoginShellIn(long timeoutSeconds, String wslWorkingDir, String script) {
        List<String> command = new ArrayList<>();
        command.add(wslExecutable);
        if (!distribution.isBlank()) {
            command.add("-d");
            command.add(distribution);
        }
        if (wslWorkingDir != null && !wslWorkingDir.isBlank()) {
            command.add("--cd");
            command.add(wslWorkingDir);
        }
        command.add("--");
        command.add("bash");
        command.add("-lc");
        command.add(script);
        return execute(command, timeoutSeconds);
    }

    /** Runs a plain Windows process (used to check that wsl.exe itself exists). */
    public static Result runWindows(long timeoutSeconds, List<String> command) {
        return executeCommand(command, timeoutSeconds);
    }

    private Result execute(List<String> command, long timeoutSeconds) {
        return executeCommand(command, timeoutSeconds);
    }

    private static Result executeCommand(List<String> command, long timeoutSeconds) {
        String commandLine = String.join(" ", command);
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            // Without this wsl.exe emits UTF-16LE for its own messages.
            builder.environment().put("WSL_UTF8", "1");
            process = builder.start();
            process.getOutputStream().close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            Thread outReader = drain(process.getInputStream(), out);
            Thread errReader = drain(process.getErrorStream(), err);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                outReader.join(500);
                errReader.join(500);
                return new Result(-1, decode(out), decode(err), commandLine, true);
            }
            outReader.join(2000);
            errReader.join(2000);
            return new Result(process.exitValue(), decode(out), decode(err), commandLine, false);
        } catch (IOException e) {
            return new Result(-1, "", e.getMessage() == null ? e.toString() : e.getMessage(), commandLine, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new Result(-1, "", "interrupted", commandLine, false);
        }
    }

    private static Thread drain(InputStream stream, ByteArrayOutputStream sink) {
        Thread thread = Thread.ofVirtual().start(() -> {
            try (InputStream in = stream) {
                in.transferTo(sink);
            } catch (IOException e) {
                log.trace("stream drain ended: {}", e.getMessage());
            }
        });
        return thread;
    }

    private static String decode(ByteArrayOutputStream buffer) {
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
