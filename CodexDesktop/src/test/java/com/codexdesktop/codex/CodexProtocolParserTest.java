package com.codexdesktop.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample lines below are trimmed captures from a real {@code codex app-server --stdio}
 * session (codex-cli 0.154.0), so the parser is tested against the actual wire format.
 */
class CodexProtocolParserTest {

    private final CodexProtocolParser parser = new CodexProtocolParser(new ObjectMapper());

    @Test
    void classifiesNotifications() {
        CodexEvent event = parser.parse("""
                {"method":"item/agentMessage/delta","params":{"threadId":"t","turnId":"u",\
                "itemId":"m","delta":"Hel"},"emittedAtMs":1789014258003}""");

        assertEquals(CodexEvent.Kind.NOTIFICATION, event.kind());
        assertEquals("item/agentMessage/delta", event.method());
        assertEquals("Hel", event.params().path("delta").asText());
    }

    @Test
    void classifiesServerRequestsByThePresenceOfAnId() {
        CodexEvent event = parser.parse("""
                {"method":"item/fileChange/requestApproval","id":7,"params":{"threadId":"t",\
                "turnId":"u","itemId":"exec-1","startedAtMs":1}}""");

        assertEquals(CodexEvent.Kind.SERVER_REQUEST, event.kind());
        assertTrue(event.isServerRequest());
        assertEquals(7, event.id().asInt());
    }

    @Test
    void classifiesResponses() {
        CodexEvent event = parser.parse("{\"id\":3,\"result\":{\"turn\":{\"id\":\"abc\"}}}");

        assertEquals(CodexEvent.Kind.RESPONSE, event.kind());
        assertEquals("abc", event.result().path("turn").path("id").asText());
    }

    @Test
    void classifiesErrorResponses() {
        CodexEvent event = parser.parse("{\"id\":4,\"error\":{\"code\":-32601,\"message\":\"nope\"}}");

        assertEquals(CodexEvent.Kind.ERROR_RESPONSE, event.kind());
        assertEquals("nope", event.error().path("message").asText());
    }

    /** A bad line must never break the stream; it is reported and skipped. */
    @Test
    void reportsMalformedLinesInsteadOfThrowing() {
        assertEquals(CodexEvent.Kind.MALFORMED, parser.parse("{not json").kind());
        assertEquals(CodexEvent.Kind.MALFORMED, parser.parse("[1,2,3]").kind());
        assertEquals(CodexEvent.Kind.MALFORMED, parser.parse("{}").kind());
    }
}
