package com.codexdesktop.ui.conversation;

import com.codexdesktop.model.Attachment;
import com.codexdesktop.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The user's own message: right-aligned, lightly tinted, deliberately smaller than the assistant
 * output so the transcript reads as a document with prompts in it.
 */
public final class UserMessageBlock extends HBox {

    public UserMessageBlock(String text) {
        this(text, List.of());
    }

    public UserMessageBlock(String text, List<Attachment> attachments) {
        setAlignment(Pos.TOP_RIGHT);
        VBox bubble = new VBox(6);
        bubble.getStyleClass().add("user-message");

        if (!attachments.isEmpty()) {
            FlowPane chips = new FlowPane(6, 6);
            chips.setAlignment(Pos.TOP_RIGHT);
            for (Attachment attachment : attachments) {
                HBox chip = Ui.row(5,
                        Ui.label(attachment.image() ? "IMG" : "@", "text-muted"),
                        Ui.label(Ui.ellipsizeMiddle(attachment.name(), 30), "caption"));
                chip.getStyleClass().add("attachment-chip");
                Ui.attachTooltip(chip, attachment.wslPath());
                chips.getChildren().add(chip);
            }
            bubble.getChildren().add(chips);
        }

        if (!text.isBlank()) {
            Label label = new Label(text);
            label.setWrapText(true);
            label.getStyleClass().add("text-primary");
            bubble.getChildren().add(label);
        }

        bubble.maxWidthProperty().bind(widthProperty().multiply(0.82));
        getChildren().addAll(Ui.hSpacer(), bubble);
    }
}
