package com.codexdesktop.ui.conversation;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.ui.Ui;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@code fileChange} item: one row per edited file with a {@code +N -M} badge and a diff that
 * is rendered only when expanded.
 */
public final class FileChangeCard extends ToolCard {

    private final List<Change> changes = new ArrayList<>();
    private final HBox statsBox = new HBox(6);

    private record Change(String path, String kind, String diff) { }

    public FileChangeCard(JsonNode item) {
        super(I18n.t("tool.modified"), "");
        setTrailing(statsBox);
        setDetailSupplier(this::buildDiffNode);
        applyItem(item);
    }

    public void applyItem(JsonNode item) {
        changes.clear();
        item.path("changes").forEach(change -> changes.add(new Change(
                change.path("path").asText(""),
                change.path("kind").path("type").asText("update"),
                change.path("diff").asText(""))));

        setKind(kindLabel());
        setTarget(targetLabel());

        int added = 0;
        int removed = 0;
        for (Change change : changes) {
            int[] counts = DiffView.countChanges(change.diff());
            added += counts[0];
            removed += counts[1];
        }
        statsBox.getChildren().setAll();
        if (added > 0) {
            statsBox.getChildren().add(Ui.label("+" + added, "diff-add"));
        }
        if (removed > 0) {
            statsBox.getChildren().add(Ui.label("-" + removed, "diff-del"));
        }

        String status = item.path("status").asText("");
        if ("failed".equals(status)) {
            setState("failed");
            statsBox.getChildren().add(Ui.label(I18n.t("tool.failed"), "text-danger"));
        } else if ("inProgress".equals(status)) {
            setState("running");
        } else {
            setState("");
        }
        invalidateDetail();
    }

    /** Files touched by this item, used by the changed-files panel. */
    public List<String> paths() {
        return changes.stream().map(Change::path).toList();
    }

    private String kindLabel() {
        if (changes.size() == 1) {
            return switch (changes.get(0).kind()) {
                case "add" -> I18n.t("tool.created");
                case "delete" -> I18n.t("tool.deleted");
                default -> I18n.t("tool.modified");
            };
        }
        return I18n.t("tool.modified");
    }

    private String targetLabel() {
        if (changes.isEmpty()) {
            return I18n.t("tool.noFiles");
        }
        if (changes.size() == 1) {
            return Ui.fileName(changes.get(0).path());
        }
        return Ui.fileName(changes.get(0).path()) + " " + I18n.t("tool.andMore", changes.size() - 1);
    }

    private Node buildDiffNode() {
        VBox box = new VBox(10);
        for (Change change : changes) {
            VBox fileBlock = new VBox(4);
            fileBlock.getChildren().add(Ui.label(change.path(), "caption"));
            fileBlock.getChildren().add(new DiffView(change.diff()));
            box.getChildren().add(fileBlock);
        }
        return box;
    }
}
