package com.codexdesktop.ui.conversation;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.ui.Ui;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/**
 * A {@code commandExecution} item.
 *
 * <p>Live output is buffered in a {@link StringBuilder} and only materialised into a
 * {@link TextArea} when expanded, so a build log with thousands of lines never becomes thousands
 * of nodes. The buffer itself is capped to keep memory bounded during long runs.
 */
public final class CommandCard extends ToolCard {

    private static final int MAX_BUFFERED_CHARS = 256 * 1024;

    private final StringBuilder output = new StringBuilder();
    private final Label statusLabel;
    private TextArea outputArea;
    private boolean truncated;

    public CommandCard(JsonNode item) {
        super(kindFor(item), displayTarget(item));
        statusLabel = Ui.label("\u25cf", "text-muted");
        setTrailing(statusLabel);
        setState("running");
        setDetailSupplier(this::buildOutputNode);
        applyItem(item);
    }

    /** Applies the latest item payload (status, exit code, duration, aggregated output). */
    public void applyItem(JsonNode item) {
        setKind(kindFor(item));
        setTarget(displayTarget(item));

        String status = item.path("status").asText("inProgress");
        JsonNode exitCode = item.path("exitCode");
        JsonNode durationMs = item.path("durationMs");

        switch (status) {
            case "completed" -> {
                boolean ok = exitCode.isMissingNode() || exitCode.isNull() || exitCode.asInt() == 0;
                statusLabel.setText(ok ? "\u2713" : "exit " + exitCode.asInt());
                statusLabel.getStyleClass().setAll("label", ok ? "text-success" : "text-danger");
                setState(ok ? "" : "failed");
            }
            case "failed" -> {
                statusLabel.setText(exitCode.isIntegralNumber() ? "exit " + exitCode.asInt() : "failed");
                statusLabel.getStyleClass().setAll("label", "text-danger");
                setState("failed");
            }
            case "declined" -> {
                statusLabel.setText(I18n.t("tool.declined"));
                statusLabel.getStyleClass().setAll("label", "text-muted");
                setState("");
            }
            default -> {
                statusLabel.setText("\u2026");
                statusLabel.getStyleClass().setAll("label", "text-muted");
                setState("running");
            }
        }

        if (durationMs.isIntegralNumber() && durationMs.asLong() > 0) {
            Ui.attachTooltip(this, I18n.t("tool.took", Ui.formatDuration(durationMs.asLong())));
        }

        String aggregated = item.path("aggregatedOutput").asText("");
        if (!aggregated.isBlank() && aggregated.length() >= output.length()) {
            output.setLength(0);
            append(aggregated);
            refreshOutputArea();
        }
    }

    public void appendOutput(String delta) {
        append(delta);
        refreshOutputArea();
    }

    private void append(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        output.append(delta);
        if (output.length() > MAX_BUFFERED_CHARS) {
            output.delete(0, output.length() - MAX_BUFFERED_CHARS);
            truncated = true;
        }
    }

    private void refreshOutputArea() {
        if (outputArea != null) {
            outputArea.setText(displayText());
            outputArea.positionCaret(outputArea.getLength());
        }
    }

    private String displayText() {
        String text = output.toString();
        return truncated ? "\u2026 earlier output trimmed \u2026\n" + text : text;
    }

    private Node buildOutputNode() {
        if (output.isEmpty()) {
            return Ui.label(I18n.t("conversation.noOutput"), "caption");
        }
        outputArea = new TextArea(displayText());
        outputArea.getStyleClass().add("output-area");
        outputArea.setEditable(false);
        outputArea.setWrapText(false);
        outputArea.setPrefRowCount(Math.min(18, Math.max(3, (int) output.chars().filter(c -> c == '\n').count() + 1)));
        VBox box = new VBox(outputArea);
        return box;
    }

    /** "Command" for a shell call, or the friendlier verb Codex already parsed for us. */
    private static String kindFor(JsonNode item) {
        JsonNode actions = item.path("commandActions");
        if (actions.isArray() && !actions.isEmpty()) {
            return switch (actions.get(0).path("type").asText("")) {
                case "read" -> I18n.t("tool.read");
                case "search" -> I18n.t("tool.search");
                case "listFiles" -> I18n.t("tool.list");
                default -> I18n.t("tool.command");
            };
        }
        return I18n.t("tool.command");
    }

    /**
     * Prefers the parsed action target (a file name, a query) over the raw
     * {@code /bin/bash -lc '…'} wrapper Codex actually executes.
     */
    private static String displayTarget(JsonNode item) {
        JsonNode actions = item.path("commandActions");
        if (actions.isArray() && !actions.isEmpty()) {
            JsonNode action = actions.get(0);
            String type = action.path("type").asText("");
            switch (type) {
                case "read" -> {
                    String name = action.path("name").asText("");
                    if (!name.isBlank()) {
                        return name;
                    }
                    return Ui.fileName(action.path("path").asText(""));
                }
                case "search" -> {
                    String query = action.path("query").asText("");
                    String path = action.path("path").asText("");
                    if (!query.isBlank()) {
                        return path.isBlank() ? query : query + "  in " + Ui.fileName(path);
                    }
                }
                case "listFiles" -> {
                    String path = action.path("path").asText("");
                    return path.isBlank() ? I18n.t("tool.projectFiles") : Ui.fileName(path);
                }
                default -> {
                    String command = action.path("command").asText("");
                    if (!command.isBlank()) {
                        return Ui.ellipsizeMiddle(oneLine(command), 96);
                    }
                }
            }
        }
        return Ui.ellipsizeMiddle(oneLine(stripShellWrapper(item.path("command").asText(""))), 96);
    }

    /** Unwraps {@code /bin/bash -lc '…'} so the card shows the command the user recognises. */
    private static String stripShellWrapper(String command) {
        String trimmed = command.trim();
        for (String prefix : new String[]{"/bin/bash -lc ", "bash -lc ", "/bin/sh -lc ", "sh -lc "}) {
            if (trimmed.startsWith(prefix)) {
                String remainder = trimmed.substring(prefix.length()).trim();
                if (remainder.length() >= 2 && remainder.startsWith("'") && remainder.endsWith("'")) {
                    return remainder.substring(1, remainder.length() - 1).replace("'\\''", "'");
                }
                return remainder;
            }
        }
        return trimmed;
    }

    private static String oneLine(String value) {
        return value.replaceAll("\\s*\\n\\s*", " \u23ce ").trim();
    }
}
