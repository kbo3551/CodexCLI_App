package com.codexdesktop.service;

import com.codexdesktop.model.AppSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the pieces the app depends on and reports the exact command used for each check, so a
 * failure points at something the user can reproduce in a terminal.
 */
public final class DiagnosticsService {

    public enum Status { OK, WARN, FAIL }

    public record Check(String name, Status status, String detail, String command) { }

    public record Report(List<Check> checks, String detectedCodexPath) {
        public boolean allOk() {
            return checks.stream().allMatch(c -> c.status() == Status.OK);
        }
    }

    /** Blocking; runs several WSL commands. Call from a background thread. */
    public Report run(AppSettings settings) {
        List<Check> checks = new ArrayList<>();
        String wslExe = settings.wslExecutable();

        WslCommandRunner.Result version = WslCommandRunner.runWindows(20, List.of(wslExe, "--version"));
        boolean wslPresent = version.exitCode() == 0 || !version.stdoutTrimmed().isBlank();
        checks.add(new Check("WSL", wslPresent ? Status.OK : Status.FAIL,
                wslPresent ? firstLine(version.stdout()) : orDefault(version.firstErrorLine(), "wsl.exe not found"),
                wslExe + " --version"));
        if (!wslPresent) {
            return new Report(checks, "");
        }

        WslCommandRunner.Result list = WslCommandRunner.runWindows(20, List.of(wslExe, "--list", "--quiet"));
        List<String> distributions = list.stdout().lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
        String wanted = settings.distribution();
        boolean distroOk = wanted.isBlank()
                ? !distributions.isEmpty()
                : distributions.stream().anyMatch(d -> d.equalsIgnoreCase(wanted));
        checks.add(new Check("Distribution", distroOk ? Status.OK : Status.FAIL,
                distroOk
                        ? (wanted.isBlank() ? "default (" + String.join(", ", distributions) + ")" : wanted)
                        : "'" + wanted + "' not found. Available: " + String.join(", ", distributions),
                wslExe + " --list --quiet"));
        if (!distroOk) {
            return new Report(checks, "");
        }

        WslCommandRunner runner = new WslCommandRunner(wslExe, settings.distribution());

        String executable = settings.codexExecutable();
        String resolveScript = executable.contains("/")
                ? "test -x " + shellQuote(executable) + " && echo " + shellQuote(executable)
                : "command -v " + shellQuote(executable);
        WslCommandRunner.Result which = runner.runLoginShell(25, resolveScript);
        String codexPath = which.stdoutTrimmed();
        boolean codexOk = which.ok() && !codexPath.isBlank();
        checks.add(new Check("Codex executable", codexOk ? Status.OK : Status.FAIL,
                codexOk ? codexPath : orDefault(which.firstErrorLine(), "command not found: " + executable),
                "bash -lc \"" + resolveScript + "\""));
        if (!codexOk) {
            return new Report(checks, "");
        }

        WslCommandRunner.Result versionResult = runner.runLoginShell(30, shellQuote(codexPath) + " --version");
        checks.add(new Check("Codex version", versionResult.ok() ? Status.OK : Status.FAIL,
                versionResult.ok() ? versionResult.stdoutTrimmed() : orDefault(versionResult.firstErrorLine(), "failed"),
                "codex --version"));

        WslCommandRunner.Result appServer = runner.runLoginShell(30,
                shellQuote(codexPath) + " app-server --help");
        boolean stdioSupported = appServer.ok() && appServer.stdout().contains("--stdio");
        checks.add(new Check("App server", stdioSupported ? Status.OK : Status.FAIL,
                stdioSupported ? "stdio transport available"
                        : orDefault(appServer.firstErrorLine(), "app-server --stdio not supported"),
                "codex app-server --help"));

        WslCommandRunner.Result auth = runner.runLoginShell(20,
                "test -f \"${CODEX_HOME:-$HOME/.codex}/auth.json\" && echo present || echo missing");
        boolean authOk = auth.stdoutTrimmed().contains("present");
        checks.add(new Check("Authentication", authOk ? Status.OK : Status.WARN,
                authOk ? "existing codex login found"
                        : "no auth.json - run 'codex login' inside WSL",
                "test -f ~/.codex/auth.json"));

        return new Report(checks, codexPath);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String firstLine(String text) {
        return text.lines().filter(line -> !line.isBlank()).findFirst().orElse("").strip();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
