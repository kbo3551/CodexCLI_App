package com.codexdesktop.ui.markdown;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A fenced code block: language label, copy action, and a horizontally scrollable body.
 *
 * <p>The body is a {@link Label} rather than a text control so a long transcript stays cheap;
 * copy-to-clipboard covers the reason most users would want selection.
 */
public final class CodeBlockView extends VBox {

    private static final int MAX_VISIBLE_LINES = 400;

    public CodeBlockView(String language, String code) {
        getStyleClass().add("code-block");

        String displayed = clamp(code);

        Label languageLabel = Ui.label(language == null || language.isBlank() ? "code" : language.trim(), "caption");
        Label copyButtonHost = null;
        HBox header = Ui.row(6, languageLabel, Ui.hSpacer());
        header.getStyleClass().add("code-header");
        header.setAlignment(Pos.CENTER_LEFT);

        var copyButton = Ui.button(I18n.t("tool.copy"), "link-button");
        copyButton.setFocusTraversable(false);
        copyButton.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            Clipboard.getSystemClipboard().setContent(content);
            copyButton.setText(I18n.t("tool.copied"));
            copyButton.setDisable(true);
            // Re-enable shortly after so the feedback is visible but not sticky.
            new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(1200), e -> {
                copyButton.setText(I18n.t("tool.copy"));
                copyButton.setDisable(false);
            })).play();
        });
        header.getChildren().add(copyButton);

        Label body = new Label(displayed);
        body.getStyleClass().add("code-body");
        body.setWrapText(false);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinHeight(0);
        scroll.setPannable(true);
        scroll.getStyleClass().add("code-scroll");

        getChildren().addAll(header, scroll);
        if (copyButtonHost != null) {
            getChildren().add(copyButtonHost);
        }
    }

    /** Guards against a pathological 100k-line block being laid out in one node. */
    private static String clamp(String code) {
        String[] lines = code.split("\n", -1);
        if (lines.length <= MAX_VISIBLE_LINES) {
            return stripTrailingNewline(code);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < MAX_VISIBLE_LINES; i++) {
            builder.append(lines[i]).append('\n');
        }
        builder.append(I18n.t("conversation.moreLines", lines.length - MAX_VISIBLE_LINES));
        return builder.toString();
    }

    private static String stripTrailingNewline(String value) {
        return value.endsWith("\n") ? value.substring(0, value.length() - 1) : value;
    }
}
