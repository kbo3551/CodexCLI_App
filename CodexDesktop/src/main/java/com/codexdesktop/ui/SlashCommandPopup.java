package com.codexdesktop.ui;

import com.codexdesktop.i18n.I18n;
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
 * The command palette that drops out of the composer when a message starts with {@code /}.
 *
 * <p>Shares the picker layout with {@link MentionPopup}: a marker on the current row, aligned
 * columns, and the key hints along the bottom.
 *
 * <p>A {@link Popup} with a {@link ListView} rather than a {@code ContextMenu}: a context menu takes
 * keyboard focus, which would stop the user from carrying on typing to narrow the list.
 */
public final class SlashCommandPopup extends Popup {

    private static final int MAX_VISIBLE = 10;
    private static final double ROW_HEIGHT = 22;
    private static final double NAME_WIDTH = 120;

    private final ListView<SlashCommand> list = new ListView<>();
    private final VBox container = new VBox();
    private Consumer<SlashCommand> onChosen = command -> { };

    public SlashCommandPopup() {
        setAutoHide(true);
        setHideOnEscape(true);
        setConsumeAutoHidingEvents(false);

        list.setCellFactory(view -> new CommandCell());
        list.setFocusTraversable(false);
        list.setFixedCellSize(ROW_HEIGHT);
        list.getStyleClass().add("picker-list");
        list.setOnMouseClicked(event -> choose());

        HBox footer = Ui.row(0, Ui.label(I18n.t("picker.hintRun"), "caption"));
        footer.getStyleClass().add("picker-footer");

        container.getChildren().addAll(list, footer);
        container.getStyleClass().addAll("root", "picker-popup");
        getContent().add(container);
    }

    public void setOnChosen(Consumer<SlashCommand> handler) {
        this.onChosen = handler == null ? command -> { } : handler;
    }

    /** Applies the scene's stylesheets; a popup is its own window and inherits none. */
    public void applyStylesheets(List<String> stylesheets, String rootStyle) {
        container.getStylesheets().setAll(stylesheets);
        container.setStyle(rootStyle == null ? "" : rootStyle);
    }

    /** @return true when at least one command matched and the popup is showing */
    public boolean showFor(Node anchor, List<SlashCommand> commands, String typed) {
        List<SlashCommand> matches = commands.stream().filter(c -> c.matches(typed)).toList();
        if (matches.isEmpty()) {
            hide();
            return false;
        }
        list.setItems(FXCollections.observableArrayList(matches));
        list.getSelectionModel().selectFirst();
        double listHeight = Math.min(MAX_VISIBLE, matches.size()) * ROW_HEIGHT + 8;
        list.setPrefHeight(listHeight);
        list.setMinHeight(listHeight);
        container.setPrefWidth(Math.max(460, anchor.getBoundsInLocal().getWidth() * 0.7));
        double totalHeight = listHeight + 34;

        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds == null) {
            return false;
        }
        if (!isShowing()) {
            show(anchor, bounds.getMinX(), bounds.getMinY() - totalHeight - 8);
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

    /** Fires the selected command and hides. */
    public void choose() {
        SlashCommand selected = list.getSelectionModel().getSelectedItem();
        hide();
        if (selected != null) {
            onChosen.accept(selected);
        }
    }

    private static final class CommandCell extends ListCell<SlashCommand> {
        private final Label marker = Ui.label("\u203a", "picker-marker");
        private final Label name = Ui.label("", "picker-name");
        private final Label description = Ui.label("", "picker-path");
        private final HBox row;

        CommandCell() {
            marker.setMinWidth(12);
            marker.visibleProperty().bind(selectedProperty());
            name.setMinWidth(NAME_WIDTH);
            name.setPrefWidth(NAME_WIDTH);
            description.setMaxWidth(Double.MAX_VALUE);
            row = Ui.row(8, marker, name, description);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(description, Priority.ALWAYS);
            getStyleClass().add("picker-cell");
        }

        @Override
        protected void updateItem(SlashCommand command, boolean empty) {
            super.updateItem(command, empty);
            if (empty || command == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            name.setText(command.display());
            description.setText(command.description());
            setGraphic(row);
            setText(null);
        }
    }
}
