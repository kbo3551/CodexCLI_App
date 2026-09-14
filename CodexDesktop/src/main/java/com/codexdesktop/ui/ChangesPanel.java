package com.codexdesktop.ui;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.ui.conversation.DiffView;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Right-hand panel listing the files the current turn touched, with the diff underneath.
 *
 * <p>Fed by {@code turn/diff/updated}, which carries the aggregated unified diff for the turn, so
 * the panel is a view of Codex's own record of the changes rather than a re-computation.
 */
public final class ChangesPanel extends VBox {

    private final VBox fileList = new VBox(2);
    private final VBox diffContainer = new VBox();
    private final Label summary = Ui.label(I18n.t("changes.none"), "caption");
    private String currentDiff = "";
    private String selectedFile = "";

    public ChangesPanel() {
        setPrefWidth(420);
        setMinWidth(300);
        getStyleClass().add("sidebar");
        setStyle("-fx-border-width: 0 0 0 1;");

        Label title = Ui.label(I18n.t("changes.title"), "title-strong");
        var closeButton = Ui.iconButton("\u00d7", I18n.t("changes.hide"));
        closeButton.setOnAction(event -> setVisibleManaged(false));
        HBox header = Ui.row(8, title, Ui.hSpacer(), closeButton);
        header.getStyleClass().add("top-bar");

        ScrollPane diffScroll = new ScrollPane(diffContainer);
        diffScroll.setFitToWidth(true);
        diffScroll.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(diffScroll, Priority.ALWAYS);

        VBox body = new VBox(8, summary, fileList, diffScroll);
        body.setPadding(new javafx.geometry.Insets(10));
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(header, body);
        setVisibleManaged(false);
    }

    public void setVisibleManaged(boolean visible) {
        setVisible(visible);
        setManaged(visible);
    }

    public boolean isShown() {
        return isVisible();
    }

    public void clear() {
        currentDiff = "";
        selectedFile = "";
        fileList.getChildren().clear();
        diffContainer.getChildren().clear();
        summary.setText(I18n.t("changes.none"));
    }

    /** Splits the aggregated diff per file and renders the selected one. */
    public void setDiff(String unifiedDiff) {
        this.currentDiff = unifiedDiff == null ? "" : unifiedDiff;
        Map<String, String> perFile = splitByFile(currentDiff);
        fileList.getChildren().clear();

        if (perFile.isEmpty()) {
            summary.setText(I18n.t("changes.none"));
            diffContainer.getChildren().clear();
            return;
        }

        int totalAdded = 0;
        int totalRemoved = 0;
        for (var entry : perFile.entrySet()) {
            int[] counts = DiffView.countChanges(entry.getValue());
            totalAdded += counts[0];
            totalRemoved += counts[1];
            fileList.getChildren().add(fileRow(entry.getKey(), counts, perFile));
        }
        summary.setText(I18n.t("changes.summary", perFile.size(), totalAdded, totalRemoved));

        if (!perFile.containsKey(selectedFile)) {
            selectedFile = perFile.keySet().iterator().next();
        }
        showFile(selectedFile, perFile);
    }

    private HBox fileRow(String path, int[] counts, Map<String, String> perFile) {
        Label name = Ui.label(Ui.fileName(path), "label");
        Label dir = Ui.label(Ui.ellipsizeMiddle(path, 46), "sub");
        VBox text = new VBox(1, name, dir);
        HBox row = Ui.row(8, text, Ui.hSpacer(),
                Ui.label("+" + counts[0], "diff-add"), Ui.label("-" + counts[1], "diff-del"));
        row.getStyleClass().add("sidebar-item");
        Ui.toggleClass(row, "selected", path.equals(selectedFile));
        row.setOnMouseClicked(event -> {
            selectedFile = path;
            for (var node : fileList.getChildren()) {
                Ui.toggleClass(node, "selected", false);
            }
            Ui.toggleClass(row, "selected", true);
            showFile(path, perFile);
        });
        return row;
    }

    private void showFile(String path, Map<String, String> perFile) {
        String diff = perFile.getOrDefault(path, "");
        diffContainer.getChildren().setAll(new DiffView(diff));
    }

    /**
     * Splits a multi-file {@code git diff} into per-file sections keyed by the b-side path.
     */
    static Map<String, String> splitByFile(String unifiedDiff) {
        Map<String, String> result = new LinkedHashMap<>();
        if (unifiedDiff == null || unifiedDiff.isBlank()) {
            return result;
        }
        List<String> current = new ArrayList<>();
        String currentPath = null;
        for (String line : unifiedDiff.split("\n", -1)) {
            if (line.startsWith("diff --git ")) {
                if (currentPath != null) {
                    result.put(currentPath, String.join("\n", current));
                }
                current = new ArrayList<>();
                currentPath = parsePath(line);
            }
            current.add(line);
        }
        if (currentPath != null) {
            result.put(currentPath, String.join("\n", current));
        } else if (!unifiedDiff.isBlank()) {
            result.put("changes", unifiedDiff);
        }
        return result;
    }

    /** {@code diff --git a/src/Foo.java b/src/Foo.java} -> {@code src/Foo.java} */
    private static String parsePath(String diffHeader) {
        String[] parts = diffHeader.split(" ");
        String candidate = parts[parts.length - 1];
        if (candidate.startsWith("b/")) {
            return candidate.substring(2);
        }
        return candidate;
    }
}
