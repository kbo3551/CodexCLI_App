package com.codexdesktop.ui.conversation;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.ui.Ui;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Codex's reasoning summary, shown as a compact expandable card.
 *
 * <p>Collapsed by default: it is useful context but should not compete with the answer.
 */
public final class ReasoningCard extends ToolCard {

    private final StringBuilder summary = new StringBuilder();

    public ReasoningCard() {
        super(I18n.t("tool.thinking"), "");
        setDetailSupplier(this::buildDetail);
    }

    public void appendDelta(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        summary.append(delta);
        setTarget(headline());
        invalidateDetail();
    }

    /** Uses the completed item's summary text, which is cleaner than the streamed fragments. */
    public void applyItem(JsonNode item) {
        StringBuilder combined = new StringBuilder();
        item.path("summary").forEach(node -> {
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append(node.asText(""));
        });
        if (combined.isEmpty()) {
            item.path("content").forEach(node -> {
                if (!combined.isEmpty()) {
                    combined.append("\n\n");
                }
                combined.append(node.asText(""));
            });
        }
        if (!combined.isEmpty()) {
            summary.setLength(0);
            summary.append(combined);
        }
        setTarget(headline());
        invalidateDetail();
    }

    public boolean isEmpty() {
        return summary.isEmpty();
    }

    private String headline() {
        String text = summary.toString().replaceAll("\\s+", " ").trim();
        // Reasoning summaries often start with a bolded title; strip the markers for the header.
        text = text.replace("**", "");
        return text.length() <= 88 ? text : text.substring(0, 87) + "\u2026";
    }

    private javafx.scene.Node buildDetail() {
        if (summary.isEmpty()) {
            return Ui.label(I18n.t("conversation.noReasoning"), "caption");
        }
        Label label = new Label(summary.toString().replace("**", ""));
        label.setWrapText(true);
        label.getStyleClass().add("text-secondary");
        return new VBox(label);
    }
}
