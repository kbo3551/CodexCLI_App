package com.codexdesktop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts Windows paths to the WSL paths Codex needs.
 *
 * <p>Conversion is done in-process for the drive-letter case, which is what a
 * {@code DirectoryChooser} produces, because spawning {@code wslpath} per lookup costs ~200 ms.
 * WSL UNC paths (the {@code wsl$} / {@code wsl.localhost} network shares) are recognised as
 * already-Linux paths.
 * Anything else falls back to {@code wslpath -a}, whose answer is cached.
 */
public final class WindowsWslPathConverter {

    private static final Logger log = LoggerFactory.getLogger(WindowsWslPathConverter.class);

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final WslCommandRunner runner;

    public WindowsWslPathConverter(WslCommandRunner runner) {
        this.runner = runner;
    }

    /**
     * @return the WSL path, or the input unchanged when it already looks like a Linux path
     * @throws PathConversionException when the path cannot be represented inside WSL
     */
    public String toWslPath(String windowsPath) {
        if (windowsPath == null || windowsPath.isBlank()) {
            throw new PathConversionException("Empty path");
        }
        String path = windowsPath.trim();

        if (path.startsWith("/")) {
            return normalizeSlashes(path);
        }

        String unc = fromWslUnc(path);
        if (unc != null) {
            return unc;
        }

        String driveMapped = fromDriveLetter(path);
        if (driveMapped != null) {
            return driveMapped;
        }

        return cache.computeIfAbsent(path, this::viaWslPathCommand);
    }

    /** {@code C:\Users\me\dev} -> {@code /mnt/c/Users/me/dev} */
    private static String fromDriveLetter(String path) {
        if (path.length() < 2 || path.charAt(1) != ':') {
            return null;
        }
        char drive = path.charAt(0);
        if (!Character.isLetter(drive)) {
            return null;
        }
        String remainder = path.length() > 2 ? path.substring(2) : "";
        if (!remainder.isEmpty() && remainder.charAt(0) != '\\' && remainder.charAt(0) != '/') {
            // "C:relative" is resolved against a per-drive cwd; refuse rather than guess.
            return null;
        }
        String linux = "/mnt/" + Character.toLowerCase(drive) + normalizeSlashes(remainder);
        return stripTrailingSlash(linux);
    }

    /** {@code \\wsl$\Distro\home\me} or {@code \\wsl.localhost\Distro\home\me} maps to {@code /home/me} */
    private static String fromWslUnc(String path) {
        String normalized = normalizeSlashes(path);
        String lower = normalized.toLowerCase(Locale.ROOT);
        String prefix = null;
        if (lower.startsWith("//wsl$/")) {
            prefix = "//wsl$/";
        } else if (lower.startsWith("//wsl.localhost/")) {
            prefix = "//wsl.localhost/";
        }
        if (prefix == null) {
            return null;
        }
        String withoutPrefix = normalized.substring(prefix.length());
        int slash = withoutPrefix.indexOf('/');
        if (slash < 0) {
            return "/";
        }
        return stripTrailingSlash(withoutPrefix.substring(slash));
    }

    private String viaWslPathCommand(String path) {
        log.debug("Falling back to wslpath for {}", path);
        WslCommandRunner.Result result = runner.run(15, "wslpath", "-a", path);
        if (!result.ok() || result.stdoutTrimmed().isBlank()) {
            throw new PathConversionException("Could not convert '" + path + "' to a WSL path: "
                    + (result.firstErrorLine().isBlank() ? "wslpath failed" : result.firstErrorLine()));
        }
        return stripTrailingSlash(result.stdoutTrimmed());
    }

    private static String normalizeSlashes(String value) {
        return value.replace('\\', '/');
    }

    private static String stripTrailingSlash(String value) {
        if (value.length() > 1 && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** Raised when a Windows path has no WSL equivalent. */
    public static final class PathConversionException extends RuntimeException {
        public PathConversionException(String message) {
            super(message);
        }
    }
}
