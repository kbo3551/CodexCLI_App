package com.codexdesktop.codex;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One decoded message from the Codex app-server.
 *
 * <p>The app-server speaks line-delimited JSON-RPC <em>without</em> the {@code jsonrpc}
 * version field, so messages are classified by which fields are present:
 * <ul>
 *   <li>{@code method} + {@code id} -&gt; request initiated by the server (approvals, …)</li>
 *   <li>{@code method} only -&gt; notification (event stream)</li>
 *   <li>{@code id} + {@code result} -&gt; response to one of our requests</li>
 *   <li>{@code id} + {@code error} -&gt; failed response</li>
 * </ul>
 *
 * <p>Payloads stay as {@link JsonNode} on purpose: the protocol is marked experimental
 * upstream and gains fields between Codex releases, so the client reads what it needs
 * instead of binding the whole schema.
 */
public record CodexEvent(Kind kind,
                         String method,
                         JsonNode id,
                         JsonNode params,
                         JsonNode result,
                         JsonNode error,
                         String raw) {

    public enum Kind {
        NOTIFICATION,
        SERVER_REQUEST,
        RESPONSE,
        ERROR_RESPONSE,
        MALFORMED
    }

    public boolean isNotification() {
        return kind == Kind.NOTIFICATION;
    }

    public boolean isServerRequest() {
        return kind == Kind.SERVER_REQUEST;
    }

    /** Params, or an empty object node substitute-free accessor that never returns null. */
    public JsonNode paramsOrEmpty() {
        return params == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : params;
    }
}
