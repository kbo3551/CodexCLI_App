package com.codexdesktop.ui.conversation;

import com.codexdesktop.codex.ApprovalRequest;
import com.codexdesktop.i18n.I18n;
import com.codexdesktop.ui.Ui;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

/**
 * Inline prompt for a Codex approval request.
 *
 * <p>Shown in the transcript rather than as a modal dialog so the surrounding context stays
 * visible. The buttons come from the request's {@code availableDecisions} when the server
 * supplies them; the chosen value is sent back verbatim, so Codex's own policy decides what
 * happens next. Nothing here bypasses or pre-empts that policy.
 */
public final class ApprovalCard extends VBox {

    private final ApprovalRequest request;
    private final HBox actions = new HBox(8);
    private final Label resolution = Ui.label("", "caption");

    public ApprovalCard(ApprovalRequest request, BiConsumer<ApprovalRequest, String> onDecision) {
        this.request = request;
        getStyleClass().add("approval-card");
        setSpacing(10);

        String title = request.kind() == ApprovalRequest.Kind.COMMAND
                ? I18n.t("approval.commandTitle")
                : I18n.t("approval.fileTitle");
        getChildren().add(Ui.label(title, "approval-title"));

        if (!request.reason().isBlank()) {
            Label reason = Ui.label(request.reason(), "text-secondary");
            reason.setWrapText(true);
            getChildren().add(reason);
        }

        if (!request.command().isBlank()) {
            Label command = new Label(request.command());
            command.getStyleClass().addAll("mono", "text-primary");
            command.setWrapText(true);
            VBox commandBox = new VBox(command);
            commandBox.getStyleClass().add("code-block");
            commandBox.setPadding(new javafx.geometry.Insets(8, 10, 8, 10));
            getChildren().add(commandBox);
        }

        if (!request.cwd().isBlank()) {
            getChildren().add(Ui.label(request.cwd(), "caption"));
        }

        for (String decision : request.availableDecisions()) {
            Button button = decisionButton(decision);
            button.setOnAction(event -> onDecision.accept(request, decision));
            actions.getChildren().add(button);
        }
        getChildren().add(actions);
    }

    public ApprovalRequest request() {
        return request;
    }

    /** Locks the card once answered, keeping the outcome visible in the transcript. */
    public void markResolved(String decisionLabel) {
        actions.setDisable(true);
        actions.setVisible(false);
        actions.setManaged(false);
        if (!getChildren().contains(resolution)) {
            getChildren().add(resolution);
        }
        resolution.setText(decisionLabel);
        getStyleClass().add("resolved");
    }

    private static Button decisionButton(String decision) {
        return switch (decision) {
            case ApprovalRequest.ACCEPT -> Ui.button(I18n.t("approval.allow"), "button", "primary");
            case ApprovalRequest.ACCEPT_FOR_SESSION -> Ui.button(I18n.t("approval.allowSession"), "button", "subtle-outline");
            case ApprovalRequest.DECLINE -> Ui.button(I18n.t("approval.deny"), "button", "danger-outline");
            case ApprovalRequest.CANCEL -> Ui.button(I18n.t("approval.cancelTurn"), "button", "subtle-outline");
            default -> Ui.button(humanize(decision), "button", "subtle-outline");
        };
    }

    /** Turns a protocol decision id into a button label for options added by newer servers. */
    private static String humanize(String decision) {
        String spaced = decision.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public static String describeDecision(String decision) {
        return switch (decision) {
            case ApprovalRequest.ACCEPT -> I18n.t("approval.allowed");
            case ApprovalRequest.ACCEPT_FOR_SESSION -> I18n.t("approval.allowedSession");
            case ApprovalRequest.DECLINE -> I18n.t("approval.denied");
            case ApprovalRequest.CANCEL -> I18n.t("approval.cancelled");
            default -> humanize(decision);
        };
    }
}
