package com.codexdesktop.codex;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A pending approval that Codex is waiting on.
 *
 * <p>The decision strings are the ones the app-server accepts
 * ({@code accept}, {@code acceptForSession}, {@code decline}, {@code cancel}); the client never
 * invents its own approval state, it only relays the user's choice.
 */
public record ApprovalRequest(JsonNode requestId,
                              Kind kind,
                              String threadId,
                              String turnId,
                              String itemId,
                              String command,
                              String cwd,
                              String reason,
                              List<String> availableDecisions) {

    public enum Kind { COMMAND, FILE_CHANGE }

    public static final String ACCEPT = "accept";
    public static final String ACCEPT_FOR_SESSION = "acceptForSession";
    public static final String DECLINE = "decline";
    public static final String CANCEL = "cancel";

    public static ApprovalRequest fromCommandRequest(CodexEvent event) {
        JsonNode params = event.paramsOrEmpty();
        return new ApprovalRequest(event.id(), Kind.COMMAND,
                text(params, "threadId"), text(params, "turnId"), text(params, "itemId"),
                text(params, "command"), text(params, "cwd"), text(params, "reason"),
                decisions(params.get("availableDecisions")));
    }

    public static ApprovalRequest fromFileChangeRequest(CodexEvent event) {
        JsonNode params = event.paramsOrEmpty();
        return new ApprovalRequest(event.id(), Kind.FILE_CHANGE,
                text(params, "threadId"), text(params, "turnId"), text(params, "itemId"),
                "", text(params, "grantRoot"), text(params, "reason"),
                List.of(ACCEPT, ACCEPT_FOR_SESSION, DECLINE));
    }

    /**
     * The server may send {@code availableDecisions}; when it does the client offers exactly
     * those, so a future Codex release can change the options without a client update.
     */
    private static List<String> decisions(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> {
                if (value.isTextual()) {
                    result.add(value.asText());
                } else if (value.isObject()) {
                    value.fieldNames().forEachRemaining(result::add);
                }
            });
        }
        if (result.isEmpty()) {
            return List.of(ACCEPT, ACCEPT_FOR_SESSION, DECLINE);
        }
        return List.copyOf(result);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }
}
