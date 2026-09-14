package com.codexdesktop.ui;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.FileMatch;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.List;
import java.util.function.Consumer;

/**
 * The file picker that drops out of the composer after an {@code @}.
 *
 * <p>Laid out like the CLI's picker: a marker on the current row, the file name, the folder it sits
 * in, and a right-aligned Dir/File tag, with the key hints along the bottom. The name column is
 * sized from the longest entry so the folder column lines up instead of ragged.
 *
 * <p>A {@link Popup} rather than a {@code ContextMenu} because a context menu takes keyboard focus,
 * which would stop the query from being refined while the list is open.
 */
public final class MentionPopup extends Popup {

    private static final int MAX_VISIBLE = 9;
    private static final double ROW_HEIGHT = 22;
    private static final double MIN_NAME_WIDTH = 130;
    private static final double MAX_NAME_WIDTH = 320;

    private final ListView<FileMatch> list = new ListView<>();
    private final Label hint = Ui.label("", "caption");
    private final HBox modeTabs = new HBox(14);
    private final VBox container = new VBox();
    private Consumer<FileMatch> onChosen = match -> { };
    private Runnable onModeChanged = () -> { };
    private double nameColumnWidth = MIN_NAME_WIDTH;
    private Mode mode = Mode.ALL;
    private List<FileMatch> lastMatches = List.of();
    private Node anchorNode;

    /**
     * Which hits to show. The CLI also offers a plugin source; this client has no equivalent, so it
     * is left out rather than shown as an option that does nothing.
     */
    public enum Mode {
        ALL("picker.modeAll"),
        FILES_ONLY("picker.modeFiles");

        private final String messageKey;

        Mode(String messageKey) {
            this.messageKey = messageKey;
        }

        String label() {
            return I18n.t(messageKey);
        }
    }

    public MentionPopup() {
        setAutoHide(true);
        setHideOnEscape(true);
        setConsumeAutoHidingEvents(false);

        list.setCellFactory(view -> new MatchCell());
        list.setFocusTraversable(false);
        list.setFixedCellSize(ROW_HEIGHT);
        list.getStyleClass().add("picker-list");
        list.setOnMouseClicked(event -> choose());

        hint.setText(I18n.t("picker.hintInsert"));
        HBox footer = Ui.row(0, hint, Ui.hSpacer(), modeTabs);
        footer.getStyleClass().add("picker-footer");
        renderModeTabs();

        container.getChildren().addAll(list, footer);
        container.getStyleClass().addAll("root", "picker-popup");
        getContent().add(container);
    }

    /** Cycles the mode, as the CLI does with the left and right arrows. */
    public void cycleMode(int delta) {
        Mode[] values = Mode.values();
        int index = (mode.ordinal() + delta % values.length + values.length) % values.length;
        mode = values[index];
        renderModeTabs();
        onModeChanged.run();
        applyMode();
    }

    public Mode mode() {
        return mode;
    }

    /** Called after the mode changes so the caller can re-render or re-query. */
    public void setOnModeChanged(Runnable handler) {
        this.onModeChanged = handler == null ? () -> { } : handler;
    }

    private void renderModeTabs() {
        modeTabs.getChildren().clear();
        for (Mode value : Mode.values()) {
            Label tab = Ui.label(value.label(), value == mode ? "picker-mode-active" : "picker-mode");
            modeTabs.getChildren().add(tab);
        }
    }

    /** Re-filters the results already fetched; no extra request is needed. */
    private void applyMode() {
        if (lastMatches.isEmpty()) {
            return;
        }
        populate(lastMatches);
    }

    public void setOnChosen(Consumer<FileMatch> handler) {
        this.onChosen = handler == null ? match -> { } : handler;
    }

    /** Applies the scene's stylesheets; a popup is its own window and inherits none. */
    public void applyStylesheets(List<String> stylesheets, String rootStyle) {
        container.getStylesheets().setAll(stylesheets);
        container.setStyle(rootStyle == null ? "" : rootStyle);
    }

    /** @return true when there was something to show */
    public boolean showFor(Node anchor, List<FileMatch> matches) {
        this.lastMatches = List.copyOf(matches);
        this.anchorNode = anchor;
        return populate(lastMatches);
    }

    private boolean populate(List<FileMatch> matches) {
        List<FileMatch> visible = mode == Mode.FILES_ONLY
                ? matches.stream().filter(match -> !match.directory()).toList()
                : matches;
        if (visible.isEmpty() || anchorNode == null) {
            hide();
            return false;
        }
        // Size the name column from the widest entry so the folder column starts at one x.
        int widest = visible.stream().mapToInt(match -> match.fileName().length()).max().orElse(12);
        double charWidth = Math.max(6.5, hint.getFont().getSize() * 0.62);
        nameColumnWidth = Math.min(MAX_NAME_WIDTH, Math.max(MIN_NAME_WIDTH, widest * charWidth + 10));

        list.setItems(FXCollections.observableArrayList(visible));
        list.getSelectionModel().selectFirst();
        double listHeight = Math.min(MAX_VISIBLE, visible.size()) * ROW_HEIGHT + 8;
        list.setPrefHeight(listHeight);
        list.setMinHeight(listHeight);

        double width = Math.max(560, anchorNode.getBoundsInLocal().getWidth() * 0.85);
        container.setPrefWidth(width);
        double totalHeight = listHeight + 34;

        var bounds = anchorNode.localToScreen(anchorNode.getBoundsInLocal());
        if (bounds == null) {
            return false;
        }
        if (!isShowing()) {
            show(anchorNode, bounds.getMinX(), bounds.getMinY() - totalHeight - 8);
        } else {
            setAnchorX(bounds.getMinX());
            setAnchorY(bounds.getMinY() - totalHeight - 8);
        }
        return true;
    }

    public void moveSelection(int delta) {
        int size = list.getItems().size();
        if (size == 0) {
            return;
        }
        int index = list.getSelectionModel().getSelectedIndex();
        int next = ((index + delta) % size + size) % size;
        list.getSelectionModel().select(next);
        list.scrollTo(next);
    }

    public void choose() {
        FileMatch selected = list.getSelectionModel().getSelectedItem();
        hide();
        if (selected != null) {
            onChosen.accept(selected);
        }
    }

    private final class MatchCell extends ListCell<FileMatch> {
        private final Label marker = Ui.label("\u203a", "picker-marker");
        private final Label name = Ui.label("", "picker-name");
        private final Label folder = Ui.label("", "picker-path");
        private final Label kind = Ui.label("", "picker-kind");
        private final HBox row;

        MatchCell() {
            marker.setMinWidth(12);
            marker.visibleProperty().bind(selectedProperty());
            kind.setMinWidth(38);
            kind.setAlignment(Pos.CENTER_RIGHT);
            folder.setMaxWidth(Double.MAX_VALUE);
            row = Ui.row(8, marker, name, folder, kind);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(folder, Priority.ALWAYS);
            getStyleClass().add("picker-cell");
        }

        @Override
        protected void updateItem(FileMatch match, boolean empty) {
            super.updateItem(match, empty);
            if (empty || match == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            name.setText(match.fileName());
            name.setMinWidth(nameColumnWidth);
            name.setPrefWidth(nameColumnWidth);
            // Directories show their own path; files show the folder that contains them.
            String path = match.directory() ? match.path() : match.parentPath();
            folder.setText(path.isBlank() ? "." : path + "/");
            kind.setText(match.directory() ? I18n.t("picker.dir") : I18n.t("picker.file"));
            Ui.toggleClass(name, "directory", match.directory());
            setGraphic(row);
            setText(null);
        }
    }
}
