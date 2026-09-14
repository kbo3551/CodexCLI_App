package com.codexdesktop.codex;

import java.util.List;

/**
 * Everything needed to launch the app-server, resolved from settings plus the active project.
 *
 * @param wslExecutable    normally {@code wsl.exe}
 * @param distribution     WSL distribution name, e.g. {@code Ubuntu-24.04}; blank uses the default
 * @param codexExecutable  {@code codex} (resolved through a login shell) or an absolute Linux path
 * @param codexArguments   normally {@code ["app-server", "--stdio"]}
 * @param workingDirectory WSL path used as the process working directory; may be null
 */
public record CodexLaunchSpec(String wslExecutable,
                              String distribution,
                              String codexExecutable,
                              List<String> codexArguments,
                              String workingDirectory) {

    public CodexLaunchSpec {
        codexArguments = codexArguments == null ? List.of() : List.copyOf(codexArguments);
    }
}
