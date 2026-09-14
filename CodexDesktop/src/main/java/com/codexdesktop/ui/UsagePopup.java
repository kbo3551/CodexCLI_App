package com.codexdesktop.ui;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.ThreadConfig;
import com.codexdesktop.model.UsageSnapshot;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The equivalent of the CLI status view: model and policy in effect, context-window usage, and
 * account rate limits with their reset times.
 *
 * <p>A {@link Popup} anchored to the status bar rather than a dialog, so checking usage never
 * interrupts what is on screen.
 */
public final class UsagePopup extends Popup {

    private static final DateTimeFormatter RESET_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final VBox content = new VBox(14);

    public UsagePopup() {
        setAutoHide(true);
        setHideOnEscape(true);
        content.getStyleClass().add("usage-popup");
        content.setPadding(new Insets(16));
        content.setPrefWidth(340);
        getContent().add(content);
    }

    /** Rebuilds the panel; cheap enough that it is done on every open. */
    public void update(UsageSnapshot usage, ThreadConfig config, java.util.List<String> stylesheets) {
        content.getChildren().clear();
        content.getStylesheets().setAll(stylesheets);
        if (!content.getStyleClass().contains("root")) {
            content.getStyleClass().add("root");
        }

        content.getChildren().add(Ui.label(I18n.t("usage.title"), "title-strong"));

        if (!config.isEmpty()) {
            VBox section = new VBox(4);
            section.getChildren().add(Ui.label(I18n.t("usage.session"), "section-label"));
            section.getChildren().add(keyValue(I18n.t("usage.model"), config.model()));
            if (!config.reasoningEffort().isBlank()) {
                section.getChildren().add(keyValue(I18n.t("usage.effort"), config.reasoningEffort()));
            }
            if (!config.approvalPolicy().isBlank()) {
                section.getChildren().add(keyValue(I18n.t("usage.approvals"), config.approvalPolicy()));
            }
            if (!config.sandboxMode().isBlank()) {
                section.getChildren().add(keyValue(I18n.t("usage.sandbox"), config.sandboxMode()));
            }
            content.getChildren().add(section);
        }

        UsageSnapshot.Tokens tokens = usage.tokens();
        if (!tokens.isEmpty()) {
            VBox section = new VBox(6);
            section.getChildren().add(Ui.label(I18n.t("usage.context"), "section-label"));
            if (tokens.contextWindow() > 0) {
                section.getChildren().add(meter(tokens.contextUsedPercent()));
                section.getChildren().add(Ui.label(
                        I18n.t("usage.contextDetail", tokens.contextUsedPercent(),
                                format(tokens.contextRemaining()), format(tokens.contextWindow())),
                        "caption"));
            }
            section.getChildren().add(keyValue(I18n.t("usage.tokensTotal"), format(tokens.totalTokens())));
            section.getChildren().add(keyValue(I18n.t("usage.tokensInput"),
                    format(tokens.inputTokens()) + "   "
                            + I18n.t("usage.cachedSuffix", format(tokens.cachedInputTokens()))));
            section.getChildren().add(keyValue(I18n.t("usage.tokensOutput"), format(tokens.outputTokens())));
            if (tokens.reasoningTokens() > 0) {
                section.getChildren().add(keyValue(I18n.t("usage.tokensReasoning"), format(tokens.reasoningTokens())));
            }
            if (tokens.lastTurnTokens() > 0) {
                section.getChildren().add(keyValue(I18n.t("usage.tokensLastTurn"), format(tokens.lastTurnTokens())));
            }
            content.getChildren().add(section);
        }

        if (usage.primary() != null || !usage.planType().isBlank()) {
            VBox section = new VBox(6);
            section.getChildren().add(Ui.label(I18n.t("usage.limits"), "section-label"));
            if (!usage.planType().isBlank()) {
                section.getChildren().add(keyValue(I18n.t("usage.plan"), usage.planType()));
            }
            addLimit(section, usage.primary(), I18n.t("usage.limitPrimary"));
            addLimit(section, usage.secondary(), I18n.t("usage.limitSecondary"));
            if (usage.hasCredits() && !usage.creditBalance().isBlank()) {
                section.getChildren().add(keyValue(I18n.t("usage.credits"), usage.creditBalance()));
            }
            content.getChildren().add(section);
        }

        if (content.getChildren().size() == 1) {
            content.getChildren().add(Ui.label(I18n.t("usage.empty"), "caption"));
        }
    }

    private void addLimit(VBox section, UsageSnapshot.Limit limit, String label) {
        if (limit == null) {
            return;
        }
        String window = limit.windowLabel();
        section.getChildren().add(Ui.label(window.isBlank() ? label : label + " (" + window + ")", "label"));
        section.getChildren().add(meter(limit.usedPercent()));
        StringBuilder detail = new StringBuilder(I18n.t("usage.limitUsed", limit.usedPercent()));
        Duration remaining = limit.timeUntilReset();
        if (remaining != null) {
            detail.append("   ").append(I18n.t("usage.resetsIn", humanize(remaining),
                    RESET_FORMAT.format(Instant.ofEpochSecond(limit.resetsAtEpochSeconds())
                            .atZone(ZoneId.systemDefault()))));
        }
        section.getChildren().add(Ui.label(detail.toString(), "caption"));
    }

    private static Node meter(int percent) {
        ProgressBar bar = new ProgressBar(Math.max(0, Math.min(100, percent)) / 100.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(6);
        bar.getStyleClass().add("usage-meter");
        if (percent >= 90) {
            bar.getStyleClass().add("critical");
        } else if (percent >= 70) {
            bar.getStyleClass().add("warn");
        }
        return bar;
    }

    private static HBox keyValue(String key, String value) {
        Label keyLabel = Ui.label(key, "text-muted");
        keyLabel.setMinWidth(112);
        Label valueLabel = Ui.label(value.isBlank() ? "\u2014" : value, "text-secondary");
        valueLabel.setWrapText(true);
        HBox row = Ui.row(8, keyLabel, valueLabel);
        HBox.setHgrow(valueLabel, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    /** 12.3k / 1.2M style shortening, matching how the CLI reports token counts. */
    static String format(long value) {
        if (value < 1000) {
            return Long.toString(value);
        }
        if (value < 1_000_000) {
            return String.format("%.1fk", value / 1000.0);
        }
        return String.format("%.2fM", value / 1_000_000.0);
    }

    static String humanize(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h " + (minutes % 60) + "m";
        }
        return (hours / 24) + "d " + (hours % 24) + "h";
    }

    /** Suppresses the default JavaFX popup sizing quirk when reopened after a rebuild. */
    public Region root() {
        return content;
    }
}
