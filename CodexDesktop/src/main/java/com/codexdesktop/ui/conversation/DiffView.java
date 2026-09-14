package com.codexdesktop.ui.conversation;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.ui.Ui;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Renders a unified diff. Built only when the user expands a change, and capped so a huge patch
 * cannot create tens of thousands of nodes.
 */
public final class DiffView extends VBox {

    private static final int MAX_LINES = 600;

    public DiffView(String unifiedDiff) {
        getStyleClass().add("diff-view");
        if (unifiedDiff == null || unifiedDiff.isBlank()) {
            getChildren().add(Ui.label(I18n.t("conversation.noChanges"), "caption"));
            return;
        }
        String[] lines = unifiedDiff.split("\n", -1);
        int rendered = 0;
        for (String line : lines) {
            if (rendered >= MAX_LINES) {
                getChildren().add(Ui.label(I18n.t("conversation.moreDiffLines", lines.length - rendered), "caption"));
                break;
            }
            if (line.isEmpty() && rendered == lines.length - 1) {
                continue;
            }
            getChildren().add(diffLine(line));
            rendered++;
        }
    }

    /** Counts +/- lines, ignoring the file header lines, for the "+N -M" badge. */
    public static int[] countChanges(String unifiedDiff) {
        int added = 0;
        int removed = 0;
        if (unifiedDiff == null) {
            return new int[]{0, 0};
        }
        for (String line : unifiedDiff.split("\n")) {
            if (line.startsWith("+++") || line.startsWith("---")) {
                continue;
            }
            if (line.startsWith("+")) {
                added++;
            } else if (line.startsWith("-")) {
                removed++;
            }
        }
        return new int[]{added, removed};
    }

    private static Label diffLine(String line) {
        Label label = new Label(line.isEmpty() ? " " : line);
        label.getStyleClass().add("diff-line");
        label.setWrapText(false);
        label.getStyleClass().add(classify(line));
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static String classify(String line) {
        if (line.startsWith("@@")) {
            return "hunk";
        }
        if (line.startsWith("diff ") || line.startsWith("index ")
                || line.startsWith("+++") || line.startsWith("---")
                || line.startsWith("new file") || line.startsWith("deleted file")
                || line.startsWith("rename ") || line.startsWith("similarity ")) {
            return "meta";
        }
        if (line.startsWith("+")) {
            return "added";
        }
        if (line.startsWith("-")) {
            return "removed";
        }
        return "context";
    }
}
