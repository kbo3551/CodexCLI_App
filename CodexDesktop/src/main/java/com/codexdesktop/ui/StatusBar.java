package com.codexdesktop.ui;

import com.codexdesktop.codex.ConnectionState;
import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.ThreadConfig;
import com.codexdesktop.model.UsageSnapshot;
import com.codexdesktop.service.GitService;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Bottom strip: WSL target, connection state, model, usage, git branch and change counts.
 *
 * <p>The usage segment mirrors the CLI's status output — context-window share and rate-limit
 * consumption — and opens {@link UsagePopup} for the full breakdown.
 */
public final class StatusBar extends HBox {

    private final Label wslDot = dot("idle");
    private final Label wslLabel = Ui.label("WSL", "label");
    private final Label codexDot = dot("idle");
    private final Label codexLabel = Ui.label(ConnectionState.DISCONNECTED.label(), "label");
    private final Label branchLabel = Ui.label("", "label");
    private final Label changesLabel = Ui.label("", "label");
    private final Label modelLabel = Ui.label("", "label");
    private final HBox usageSegment;
    private final Label contextLabel = Ui.label("", "label");
    private final Label limitLabel = Ui.label("", "label");
    private final Label limitDot = dot("idle");

    private final UsagePopup usagePopup = new UsagePopup();
    private UsageSnapshot usage = UsageSnapshot.empty();
    private ThreadConfig threadConfig = ThreadConfig.empty();

    public StatusBar() {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);

        usageSegment = Ui.row(6, contextLabel, limitDot, limitLabel);
        usageSegment.getStyleClass().add("status-clickable");
        usageSegment.setVisible(false);
        usageSegment.setManaged(false);
        Ui.attachTooltip(usageSegment, I18n.t("usage.tooltip"));
        usageSegment.setOnMouseClicked(event -> toggleUsagePopup());

        getChildren().addAll(
                wslDot, wslLabel,
                separator(),
                codexDot, codexLabel,
                Ui.hSpacer(),
                usageSegment, separator(),
                modelLabel, separator(), branchLabel, changesLabel);
    }

    public void setDistribution(String distribution) {
        wslLabel.setText(I18n.t("status.wsl", distribution == null || distribution.isBlank()
                ? I18n.t("status.wslDefault") : distribution));
    }

    public void setConnectionState(ConnectionState state) {
        codexLabel.setText(state.label());
        String styleClass = switch (state) {
            case CONNECTED -> "ok";
            case CONNECTING -> "warn";
            case FAILED -> "bad";
            case DISCONNECTED -> "idle";
        };
        setDotStyle(codexDot, styleClass);
        setDotStyle(wslDot, state == ConnectionState.CONNECTED ? "ok" : styleClass);
        wslDot.setText(state == ConnectionState.CONNECTING ? "\u25cb" : "\u25cf");
    }

    public void setModel(String model) {
        modelLabel.setText(model == null ? "" : model);
    }

    public void setThreadConfig(ThreadConfig config) {
        this.threadConfig = config == null ? ThreadConfig.empty() : config;
        setModel(this.threadConfig.model());
        refreshUsageSegment();
    }

    public void setUsage(UsageSnapshot snapshot) {
        this.usage = snapshot == null ? UsageSnapshot.empty() : snapshot;
        refreshUsageSegment();
        if (usagePopup.isShowing()) {
            usagePopup.update(usage, threadConfig, stylesheet());
        }
    }

    public void setGitStatus(GitService.GitStatus status) {
        if (status == null || !status.repository()) {
            branchLabel.setText("");
            changesLabel.setText("");
            return;
        }
        branchLabel.setText(status.branch());
        StringBuilder changes = new StringBuilder(status.describeChanges());
        if (status.insertions() > 0 || status.deletions() > 0) {
            if (!changes.isEmpty()) {
                changes.append("   ");
            }
            changes.append('+').append(status.insertions()).append(" -").append(status.deletions());
        }
        changesLabel.setText(changes.toString());
    }

    private void refreshUsageSegment() {
        UsageSnapshot.Tokens tokens = usage.tokens();
        boolean hasContext = tokens.contextWindow() > 0;
        contextLabel.setText(hasContext
                ? I18n.t("usage.contextShort", tokens.contextUsedPercent()) : "");
        contextLabel.setVisible(hasContext);
        contextLabel.setManaged(hasContext);

        UsageSnapshot.Limit primary = usage.primary();
        boolean hasLimit = primary != null;
        if (hasLimit) {
            String window = primary.windowLabel();
            limitLabel.setText(window.isBlank()
                    ? I18n.t("usage.limitShort", primary.usedPercent())
                    : I18n.t("usage.limitShortWindow", primary.usedPercent(), window));
            setDotStyle(limitDot, primary.usedPercent() >= 90 ? "bad"
                    : primary.usedPercent() >= 70 ? "warn" : "ok");
        } else {
            limitLabel.setText("");
        }
        limitLabel.setVisible(hasLimit);
        limitLabel.setManaged(hasLimit);
        limitDot.setVisible(hasLimit);
        limitDot.setManaged(hasLimit);

        boolean any = hasContext || hasLimit || !threadConfig.isEmpty();
        usageSegment.setVisible(any);
        usageSegment.setManaged(any);
    }

    /** Opens the usage panel from elsewhere, e.g. the /status command. */
    public void showUsage() {
        if (!usagePopup.isShowing()) {
            toggleUsagePopup();
        }
    }

    private void toggleUsagePopup() {
        if (usagePopup.isShowing()) {
            usagePopup.hide();
            return;
        }
        usagePopup.update(usage, threadConfig, stylesheet());
        var bounds = usageSegment.localToScreen(usageSegment.getBoundsInLocal());
        // Anchored above the status bar; width is known only after the first layout pass.
        usagePopup.show(usageSegment, bounds.getMinX() - 80, bounds.getMinY() - 8);
        usagePopup.setAnchorY(bounds.getMinY() - usagePopup.getHeight() - 8);
    }

    /** The popup is a separate window, so it needs the scene's stylesheets applied explicitly. */
    private java.util.List<String> stylesheet() {
        if (getScene() != null) {
            return java.util.List.copyOf(getScene().getStylesheets());
        }
        return java.util.List.of();
    }

    private static Label dot(String styleClass) {
        Label label = new Label("\u25cf");
        label.getStyleClass().addAll("status-dot", styleClass);
        return label;
    }

    private static void setDotStyle(Label dot, String styleClass) {
        dot.getStyleClass().setAll("status-dot", styleClass);
    }

    private static Label separator() {
        Label label = new Label("\u00b7");
        label.getStyleClass().add("label");
        return label;
    }
}
