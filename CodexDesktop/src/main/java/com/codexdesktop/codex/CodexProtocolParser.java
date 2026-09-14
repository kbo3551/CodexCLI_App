package com.codexdesktop.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Turns one raw stdout line from the app-server into a {@link CodexEvent}. */
public final class CodexProtocolParser {

    private final ObjectMapper mapper;

    public CodexProtocolParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Never throws: a malformed line yields {@link CodexEvent.Kind#MALFORMED} so a single bad
     * line can be logged and skipped instead of tearing down the connection.
     */
    public CodexEvent parse(String line) {
        try {
            JsonNode root = mapper.readTree(line);
            if (root == null || !root.isObject()) {
                return malformed(line);
            }
            JsonNode id = root.get("id");
            JsonNode method = root.get("method");
            JsonNode params = root.get("params");
            JsonNode result = root.get("result");
            JsonNode error = root.get("error");

            if (method != null && method.isTextual()) {
                CodexEvent.Kind kind = (id != null && !id.isNull())
                        ? CodexEvent.Kind.SERVER_REQUEST
                        : CodexEvent.Kind.NOTIFICATION;
                return new CodexEvent(kind, method.asText(), id, params, null, null, line);
            }
            if (error != null && !error.isNull()) {
                return new CodexEvent(CodexEvent.Kind.ERROR_RESPONSE, null, id, null, null, error, line);
            }
            if (result != null) {
                return new CodexEvent(CodexEvent.Kind.RESPONSE, null, id, null, result, null, line);
            }
            return malformed(line);
        } catch (Exception e) {
            return malformed(line);
        }
    }

    private static CodexEvent malformed(String line) {
        return new CodexEvent(CodexEvent.Kind.MALFORMED, null, null, null, null, null, line);
    }
}
