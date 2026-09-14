package com.codexdesktop.codex;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * UI-facing view of the protocol event stream. Every method is called on the JavaFX thread.
 *
 * <p>Item payloads stay as {@link JsonNode} so the view layer can read new fields as Codex adds
 * them without a protocol class rewrite.
 */
public interface CodexSessionListener {

    /** A new thread item appeared ({@code item/started}). */
    default void onItemStarted(String itemId, String itemType, JsonNode item) { }

    /** An item reached its final state ({@code item/completed}). */
    default void onItemCompleted(String itemId, String itemType, JsonNode item) { }

    /** Assistant text delta; may be called several times per frame. */
    default void onAgentMessageDelta(String itemId, String delta) { }

    /** Reasoning summary delta, used for the "Thinking" surface. */
    default void onReasoningDelta(String itemId, String delta) { }

    /** Live stdout/stderr from a running command. */
    default void onCommandOutputDelta(String itemId, String delta) { }

    /** Aggregated unified diff for the current turn. */
    default void onTurnDiff(String diff) { }

    default void onTurnStarted(String turnId) { }

    /** @param status one of {@code completed}, {@code interrupted}, {@code failed} */
    default void onTurnCompleted(String turnId, String status, JsonNode turn) { }

    /** Codex-reported error for the current turn. */
    default void onError(String message, boolean willRetry) { }

    /** An approval is now pending; the UI must eventually call the approval service. */
    default void onApprovalRequested(ApprovalRequest request) { }

    /** The server resolved a pending approval itself (timeout, auto-review, cancellation). */
    default void onApprovalResolved(String itemId) { }

    default void onActivityChanged(String activity) { }

    /** Token counters and rate-limit consumption, as reported by Codex. */
    default void onUsageChanged(com.codexdesktop.model.UsageSnapshot usage) { }

    /** Thread configuration resolved by the server: model, effort, sandbox, approval policy. */
    default void onThreadConfigured(com.codexdesktop.model.ThreadConfig config) { }

    default void onConnectionStateChanged(ConnectionState state, String detail) { }

    /** Non-fatal notice worth surfacing in the transcript. */
    default void onNotice(String message) { }
}
