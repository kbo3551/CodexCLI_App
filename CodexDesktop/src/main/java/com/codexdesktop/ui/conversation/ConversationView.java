package com.codexdesktop.ui.conversation;

import com.codexdesktop.codex.ApprovalRequest;
import com.codexdesktop.codex.CodexProtocol;
import com.codexdesktop.codex.CodexSessionListener;
import com.codexdesktop.codex.ConnectionState;
import com.codexdesktop.i18n.I18n;
import com.codexdesktop.ui.Ui;
import com.codexdesktop.ui.markdown.MarkdownRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The transcript. Consumes session events and maintains one node per Codex item.
 *
 * <p>Performance choices that matter here:
 * <ul>
 *   <li>Item nodes are created once and mutated in place; deltas never rebuild anything.</li>
 *   <li>Heavy content (command output, diffs) is built on expand, not on arrival.</li>
 *   <li>The node count is capped: the oldest blocks are dropped once the transcript grows past
 *       {@link #MAX_ITEM_NODES}, which keeps layout cost flat in a long session.</li>
 *   <li>Auto-scroll only follows when the user is already near the bottom, so reading scrollback
 *       is not interrupted.</li>
 * </ul>
 */
public final class ConversationView extends StackPane implements CodexSessionListener {

    private static final int MAX_ITEM_NODES = 400;
    private static final double AUTOSCROLL_THRESHOLD = 0.92;

    private final VBox items = new VBox(12);
    private final ScrollPane scroll = new ScrollPane();
    private final MarkdownRenderer renderer;
    private final Map<String, Node> nodesByItemId = new HashMap<>();
    private final Map<String, ApprovalCard> approvalCards = new LinkedHashMap<>();
    private final HBox activityRow;
    private final Label activityLabel;
    private final VBox emptyState;

    private BiConsumer<ApprovalRequest, String> approvalHandler = (request, decision) -> { };
    private Consumer<String> diffListener = diff -> { };
    private int suppressUserMessages;

    public ConversationView(MarkdownRenderer renderer) {
        this.renderer = renderer;

        items.getStyleClass().add("conversation-list");
        items.setFillWidth(true);

        scroll.setContent(items);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("conversation-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(12, 12);
        spinner.setMaxSize(12, 12);
        activityLabel = Ui.label("", "activity-label");
        activityRow = Ui.row(8, spinner, activityLabel);
        activityRow.getStyleClass().add("activity-row");
        activityRow.setVisible(false);
        activityRow.setManaged(false);

        emptyState = buildEmptyState();

        getChildren().addAll(scroll, emptyState);
        StackPane.setAlignment(emptyState, Pos.CENTER);
        items.getChildren().add(activityRow);
    }

    public void setApprovalHandler(BiConsumer<ApprovalRequest, String> handler) {
        this.approvalHandler = handler;
    }

    public void setDiffListener(Consumer<String> listener) {
        this.diffListener = listener;
    }

    /** Clears the transcript, e.g. when switching threads. */
    public void reset() {
        items.getChildren().clear();
        items.getChildren().add(activityRow);
        nodesByItemId.clear();
        approvalCards.clear();
        suppressUserMessages = 0;
        setActivity("");
        emptyState.setVisible(true);
    }

    /**
     * Adds the user's message immediately instead of waiting for the server echo, which arrives
     * seconds later. The matching {@code userMessage} item is then skipped.
     */
    public void appendLocalUserMessage(String text, java.util.List<com.codexdesktop.model.Attachment> attachments) {
        suppressUserMessages++;
        addBlock(new UserMessageBlock(text, attachments));
    }

    public void appendNotice(String message, boolean error) {
        Label label = new Label(message);
        label.setWrapText(true);
        label.getStyleClass().add(error ? "text-danger" : "text-secondary");
        VBox box = new VBox(label);
        box.getStyleClass().add("tool-card");
        box.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));
        addBlock(box);
    }

    /** Inserts the horizontal rule that separates turns. */
    public void appendTurnDivider() {
        if (items.getChildren().size() <= 1) {
            return;
        }
        Region divider = new Region();
        divider.getStyleClass().add("turn-divider");
        divider.setMinHeight(1);
        divider.setPrefHeight(1);
        VBox spacing = new VBox(divider);
        spacing.setPadding(new javafx.geometry.Insets(6, 0, 6, 0));
        addBlock(spacing);
    }

    /**
     * Renders one item loaded from thread history, reusing the same builders as the live stream so
     * a resumed conversation looks identical to one that was watched happening.
     */
    public void renderHistoryItem(JsonNode item) {
        String type = item.path("type").asText("");
        String id = item.path("id").asText("");
        onItemStarted(id, type, item);
        onItemCompleted(id, type, item);
    }

    // ------------------------------------------------------------ session events

    @Override
    public void onItemStarted(String itemId, String itemType, JsonNode item) {
        switch (itemType) {
            case CodexProtocol.ITEM_USER_MESSAGE -> {
                if (suppressUserMessages > 0) {
                    suppressUserMessages--;
                    return;
                }
                register(itemId, new UserMessageBlock(userText(item)));
            }
            case CodexProtocol.ITEM_AGENT_MESSAGE ->
                    register(itemId, new AssistantMessageBlock(renderer, item.path("phase").asText("")));
            case CodexProtocol.ITEM_REASONING -> register(itemId, new ReasoningCard());
            case CodexProtocol.ITEM_COMMAND_EXECUTION -> register(itemId, new CommandCard(item));
            case CodexProtocol.ITEM_FILE_CHANGE -> register(itemId, new FileChangeCard(item));
            case CodexProtocol.ITEM_MCP_TOOL_CALL -> register(itemId, mcpCard(item));
            case CodexProtocol.ITEM_WEB_SEARCH -> register(itemId,
                    new ToolCard(I18n.t("tool.webSearch"), item.path("query").asText("")));
            case CodexProtocol.ITEM_PLAN -> register(itemId, planCard(item));
            default -> { /* item types this UI does not visualise */ }
        }
    }

    @Override
    public void onItemCompleted(String itemId, String itemType, JsonNode item) {
        Node node = nodesByItemId.get(itemId);
        switch (itemType) {
            case CodexProtocol.ITEM_AGENT_MESSAGE -> {
                if (node instanceof AssistantMessageBlock block) {
                    block.finish(item.path("text").asText(""));
                } else {
                    AssistantMessageBlock block = new AssistantMessageBlock(renderer,
                            item.path("phase").asText(""));
                    register(itemId, block);
                    block.finish(item.path("text").asText(""));
                }
            }
            case CodexProtocol.ITEM_REASONING -> {
                if (node instanceof ReasoningCard card) {
                    card.applyItem(item);
                    if (card.isEmpty()) {
                        remove(itemId);
                    }
                }
            }
            case CodexProtocol.ITEM_COMMAND_EXECUTION -> {
                if (node instanceof CommandCard card) {
                    card.applyItem(item);
                } else {
                    register(itemId, new CommandCard(item));
                }
            }
            case CodexProtocol.ITEM_FILE_CHANGE -> {
                if (node instanceof FileChangeCard card) {
                    card.applyItem(item);
                } else {
                    register(itemId, new FileChangeCard(item));
                }
            }
            case CodexProtocol.ITEM_MCP_TOOL_CALL -> {
                if (node instanceof ToolCard card) {
                    card.setState("failed".equals(item.path("status").asText("")) ? "failed" : "");
                }
            }
            case CodexProtocol.ITEM_PLAN -> {
                if (node == null) {
                    register(itemId, planCard(item));
                }
            }
            default -> { }
        }
    }

    @Override
    public void onAgentMessageDelta(String itemId, String delta) {
        if (nodesByItemId.get(itemId) instanceof AssistantMessageBlock block) {
            block.appendDelta(delta);
            scrollIfFollowing();
        }
    }

    @Override
    public void onReasoningDelta(String itemId, String delta) {
        Node node = nodesByItemId.get(itemId);
        if (node == null) {
            ReasoningCard card = new ReasoningCard();
            register(itemId, card);
            card.appendDelta(delta);
        } else if (node instanceof ReasoningCard card) {
            card.appendDelta(delta);
        }
    }

    @Override
    public void onCommandOutputDelta(String itemId, String delta) {
        if (nodesByItemId.get(itemId) instanceof CommandCard card) {
            card.appendOutput(delta);
        }
    }

    @Override
    public void onTurnDiff(String diff) {
        diffListener.accept(diff);
    }

    @Override
    public void onError(String message, boolean willRetry) {
        appendNotice(willRetry ? I18n.t("error.retrying", message) : message, true);
    }

    @Override
    public void onNotice(String message) {
        appendNotice(message, false);
    }

    @Override
    public void onApprovalRequested(ApprovalRequest request) {
        ApprovalCard card = new ApprovalCard(request, (req, decision) -> {
            approvalHandler.accept(req, decision);
            card(req.itemId(), decision);
        });
        approvalCards.put(request.itemId(), card);
        addBlock(card);
    }

    @Override
    public void onApprovalResolved(String itemId) {
        ApprovalCard card = approvalCards.remove(itemId);
        if (card != null) {
            card.markResolved(I18n.t("approval.resolvedByCodex"));
        }
    }

    @Override
    public void onActivityChanged(String activity) {
        setActivity(activity);
    }

    @Override
    public void onConnectionStateChanged(ConnectionState state, String detail) {
        if (state == ConnectionState.FAILED && detail != null && !detail.isBlank()) {
            appendNotice("Codex connection failed: " + detail, true);
        }
    }

    // ------------------------------------------------------------------ internals

    private void card(String itemId, String decision) {
        ApprovalCard card = approvalCards.remove(itemId);
        if (card != null) {
            card.markResolved(ApprovalCard.describeDecision(decision));
        }
    }

    private void setActivity(String activity) {
        boolean visible = activity != null && !activity.isBlank();
        activityLabel.setText(visible ? activity : "");
        activityRow.setVisible(visible);
        activityRow.setManaged(visible);
        if (visible) {
            scrollIfFollowing();
        }
    }

    private void register(String itemId, Node node) {
        if (itemId != null && !itemId.isBlank()) {
            Node previous = nodesByItemId.put(itemId, node);
            if (previous != null) {
                items.getChildren().remove(previous);
            }
        }
        addBlock(node);
    }

    private void remove(String itemId) {
        Node node = nodesByItemId.remove(itemId);
        if (node != null) {
            items.getChildren().remove(node);
        }
    }

    /** Appends above the activity row, then trims and scrolls. */
    private void addBlock(Node node) {
        emptyState.setVisible(false);
        int insertAt = Math.max(0, items.getChildren().indexOf(activityRow));
        if (insertAt == items.getChildren().size()) {
            items.getChildren().add(node);
        } else {
            items.getChildren().add(insertAt, node);
        }
        trim();
        scrollIfFollowing();
    }

    private void trim() {
        int overflow = items.getChildren().size() - MAX_ITEM_NODES;
        if (overflow <= 0) {
            return;
        }
        for (int i = 0; i < overflow; i++) {
            Node removed = items.getChildren().remove(0);
            nodesByItemId.values().remove(removed);
        }
    }

    private void scrollIfFollowing() {
        if (scroll.getVvalue() >= AUTOSCROLL_THRESHOLD || scroll.getVvalue() == 0.0) {
            // Layout has not run for the new node yet, so defer one pulse.
            javafx.application.Platform.runLater(() -> scroll.setVvalue(1.0));
        }
    }

    public void scrollToBottom() {
        javafx.application.Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    private ToolCard mcpCard(JsonNode item) {
        String server = item.path("server").asText("");
        String tool = item.path("tool").asText("");
        return new ToolCard(I18n.t("tool.tool"), server.isBlank() ? tool : server + " / " + tool);
    }

    private Node planCard(JsonNode item) {
        ToolCard card = new ToolCard(I18n.t("tool.plan"), "");
        String text = item.path("text").asText("");
        card.setTarget(text.lines().findFirst().orElse("").strip());
        card.setDetailSupplier(() -> {
            Label label = new Label(text);
            label.setWrapText(true);
            label.getStyleClass().add("text-secondary");
            return new VBox(label);
        });
        return card;
    }

    private static String userText(JsonNode item) {
        StringBuilder builder = new StringBuilder();
        item.path("content").forEach(part -> {
            if ("text".equals(part.path("type").asText(""))) {
                builder.append(part.path("text").asText(""));
            }
        });
        return builder.toString();
    }

    private VBox buildEmptyState() {
        Label headline = Ui.label(I18n.t("conversation.emptyTitle"), "headline");
        Label sub = Ui.label(I18n.t("conversation.emptySubtitle"), "sub");
        VBox box = Ui.column(6, headline, sub);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        box.setMouseTransparent(true);
        return box;
    }
}
