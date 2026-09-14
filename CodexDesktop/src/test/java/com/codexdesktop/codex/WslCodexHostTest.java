package com.codexdesktop.codex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WslCodexHostTest {

    @Test
    void buildsCommandWithDistributionAndWorkingDirectory() {
        List<String> command = WslCodexHost.buildCommand(new CodexLaunchSpec(
                "wsl.exe", "Ubuntu-24.04", "codex",
                List.of("app-server", "--stdio"), "/mnt/c/Users/USER/dev"));

        assertEquals(List.of("wsl.exe", "-d", "Ubuntu-24.04",
                "--cd", "/mnt/c/Users/USER/dev", "--",
                "bash", "-lc", "'codex' 'app-server' '--stdio'"), command);
    }

    @Test
    void omitsDistributionWhenBlankSoTheWslDefaultIsUsed() {
        List<String> command = WslCodexHost.buildCommand(new CodexLaunchSpec(
                "wsl.exe", "  ", "codex", List.of("app-server", "--stdio"), null));

        assertEquals(List.of("wsl.exe", "--", "bash", "-lc", "'codex' 'app-server' '--stdio'"), command);
    }

    /** An absolute path skips the login shell: there is no PATH lookup to perform. */
    @Test
    void executesAbsolutePathsDirectly() {
        List<String> command = WslCodexHost.buildCommand(new CodexLaunchSpec(
                "wsl.exe", "Ubuntu-24.04", "/home/boryeong/.local/bin/codex",
                List.of("app-server", "--stdio"), null));

        assertEquals(List.of("wsl.exe", "-d", "Ubuntu-24.04", "--",
                "/home/boryeong/.local/bin/codex", "app-server", "--stdio"), command);
    }

    @Test
    void quotingPreventsShellInjectionFromSettings() {
        String quoted = WslCodexHost.shellQuote("codex'; rm -rf ~; echo '");
        assertTrue(quoted.startsWith("'") && quoted.endsWith("'"));
        // Every embedded quote is closed, escaped and reopened, so the payload stays one argument.
        assertEquals("'codex'\\''; rm -rf ~; echo '\\'''", quoted);
    }

    @Test
    void defaultsMissingExecutablesToSensibleValues() {
        List<String> command = WslCodexHost.buildCommand(new CodexLaunchSpec(
                null, null, null, List.of("app-server", "--stdio"), null));

        assertEquals("wsl.exe", command.get(0));
        assertTrue(command.contains("bash"));
    }
}
