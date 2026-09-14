package com.codexdesktop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads git state for the active project by running git <em>inside WSL</em>, so no Windows git
 * installation is required and the result matches what Codex itself sees.
 */
public final class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    /** Empty {@code branch} means "not a git repository". */
    public record GitStatus(boolean repository, String branch, int changedFiles, int insertions, int deletions) {
        public static GitStatus none() {
            return new GitStatus(false, "", 0, 0, 0);
        }

        public String describeChanges() {
            if (!repository || changedFiles == 0) {
                return "";
            }
            return com.codexdesktop.i18n.I18n.t("status.filesChanged", changedFiles);
        }
    }

    private final WslCommandRunner runner;

    public GitService(WslCommandRunner runner) {
        this.runner = runner;
    }

    /** Blocking; call from a background thread. */
    public GitStatus status(String wslProjectPath) {
        if (wslProjectPath == null || wslProjectPath.isBlank()) {
            return GitStatus.none();
        }
        WslCommandRunner.Result inside = runner.runLoginShellIn(20, wslProjectPath,
                "git rev-parse --is-inside-work-tree 2>/dev/null");
        if (!inside.ok() || !inside.stdoutTrimmed().startsWith("true")) {
            return GitStatus.none();
        }

        String branch = runner.runLoginShellIn(20, wslProjectPath,
                "git rev-parse --abbrev-ref HEAD 2>/dev/null").stdoutTrimmed();

        WslCommandRunner.Result porcelain = runner.runLoginShellIn(20, wslProjectPath,
                "git status --porcelain 2>/dev/null");
        int changed = (int) porcelain.stdout().lines().filter(line -> !line.isBlank()).count();

        WslCommandRunner.Result numstat = runner.runLoginShellIn(20, wslProjectPath,
                "git diff --numstat HEAD 2>/dev/null");
        int insertions = 0;
        int deletions = 0;
        for (String line : numstat.stdout().split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                insertions += parseCount(parts[0]);
                deletions += parseCount(parts[1]);
            }
        }
        log.debug("git status for {}: branch={} changed={} +{} -{}", wslProjectPath, branch, changed,
                insertions, deletions);
        return new GitStatus(true, branch, changed, insertions, deletions);
    }

    /** Unified diff of the working tree, used by the changes panel. */
    public String workingTreeDiff(String wslProjectPath) {
        if (wslProjectPath == null || wslProjectPath.isBlank()) {
            return "";
        }
        return runner.runLoginShellIn(30, wslProjectPath, "git --no-pager diff HEAD 2>/dev/null").stdout();
    }

    /** "-" appears for binary files in numstat output. */
    private static int parseCount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
