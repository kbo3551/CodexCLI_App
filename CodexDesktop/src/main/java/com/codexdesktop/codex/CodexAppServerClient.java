package com.codexdesktop.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Speaks the app-server protocol over a {@link CodexHost}.
 *
 * <p>Responsibilities: correlate request ids with futures, fan notifications out to listeners,
 * and answer server-initiated requests. Nothing here knows about threads, turns or the UI.
 *
 * <p>Listeners are invoked on the reader thread; callers that touch the UI must marshal to the
 * JavaFX thread themselves (see {@code CodexSessionService}).
 */
public final class CodexAppServerClient {

    private static final Logger log = LoggerFactory.getLogger(CodexAppServerClient.class);
    private static final Logger wire = LoggerFactory.getLogger("codex.wire");

    /** JSON-RPC "method not found"; used for server requests this client does not implement. */
    private static final int ERROR_METHOD_NOT_FOUND = -32601;

    private final ObjectMapper mapper;
    private final CodexProtocolParser parser;
    private final CodexHost host;

    private final AtomicLong nextRequestId = new AtomicLong(1);
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<CodexEvent>> notificationListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Function<CodexEvent, JsonNode>> serverRequestHandlers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<String>> stderrListeners = new CopyOnWriteArrayList<>();

    private volatile boolean debugWireLogging;

    public CodexAppServerClient(CodexHost host, ObjectMapper mapper) {
        this.host = host;
        this.mapper = mapper;
        this.parser = new CodexProtocolParser(mapper);

        host.setStdoutLineListener(this::onStdoutLine);
        host.setStderrLineListener(this::onStderrLine);

        // Cheap, non-sensitive server request the client can satisfy directly.
        serverRequestHandlers.put(CodexProtocol.R_CURRENT_TIME_READ, event -> {
            ObjectNode result = mapper.createObjectNode();
            result.put("currentTimeAt", System.currentTimeMillis() / 1000L);
            return result;
        });
    }

    public CodexHost host() {
        return host;
    }

    public void setDebugWireLogging(boolean enabled) {
        this.debugWireLogging = enabled;
    }

    public void start(CodexLaunchSpec spec) throws IOException {
        host.start(spec);
    }

    /**
     * Fails every in-flight request and releases listeners' futures so the UI never hangs on a
     * dead process.
     */
    public void shutdown() {
        host.stop();
        failAllPending(new IllegalStateException("Codex app-server connection closed"));
    }

    public void failAllPending(Throwable cause) {
        pending.forEach((id, future) -> future.completeExceptionally(cause));
        pending.clear();
    }

    public void addNotificationListener(Consumer<CodexEvent> listener) {
        notificationListeners.add(listener);
    }

    public void removeNotificationListener(Consumer<CodexEvent> listener) {
        notificationListeners.remove(listener);
    }

    public void addStderrListener(Consumer<String> listener) {
        stderrListeners.add(listener);
    }

    /**
     * Registers a handler for a server-initiated request. The handler returns the {@code result}
     * payload, or {@code null} to answer with a "method not supported" error.
     */
    public void registerServerRequestHandler(String method, Function<CodexEvent, JsonNode> handler) {
        serverRequestHandlers.put(method, handler);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public ObjectNode newParams() {
        return mapper.createObjectNode();
    }

    /** Sends a request and completes when the matching response arrives. */
    public CompletableFuture<JsonNode> request(String method, JsonNode params) {
        long id = nextRequestId.getAndIncrement();
        ObjectNode message = mapper.createObjectNode();
        message.put("id", id);
        message.put("method", method);
        message.set("params", params == null ? mapper.createObjectNode() : params);

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(Long.toString(id), future);
        if (!writeMessage(message)) {
            pending.remove(Long.toString(id));
            future.completeExceptionally(new IllegalStateException("Codex app-server is not running"));
        }
        return future;
    }

    public void notify(String method, JsonNode params) {
        ObjectNode message = mapper.createObjectNode();
        message.put("method", method);
        if (params != null) {
            message.set("params", params);
        }
        writeMessage(message);
    }

    /** Answers a server request with a result payload, echoing the original id verbatim. */
    public void respond(JsonNode id, JsonNode result) {
        ObjectNode message = mapper.createObjectNode();
        message.set("id", id);
        message.set("result", result == null ? mapper.createObjectNode() : result);
        writeMessage(message);
    }

    public void respondError(JsonNode id, int code, String messageText) {
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", messageText);
        ObjectNode message = mapper.createObjectNode();
        message.set("id", id);
        message.set("error", error);
        writeMessage(message);
    }

    private boolean writeMessage(ObjectNode message) {
        if (!host.isRunning()) {
            return false;
        }
        try {
            String line = mapper.writeValueAsString(message);
            if (debugWireLogging) {
                wire.debug("--> {}", line);
            }
            host.send(line);
            return true;
        } catch (Exception e) {
            log.warn("Failed to serialize outbound message: {}", e.getMessage());
            return false;
        }
    }

    private void onStdoutLine(String line) {
        if (line.isBlank()) {
            return;
        }
        if (debugWireLogging) {
            wire.debug("<-- {}", line);
        }
        CodexEvent event = parser.parse(line);
        switch (event.kind()) {
            case RESPONSE, ERROR_RESPONSE -> completePending(event);
            case NOTIFICATION -> dispatchNotification(event);
            case SERVER_REQUEST -> handleServerRequest(event);
            case MALFORMED -> log.warn("Malformed protocol line ({} chars), ignoring", line.length());
        }
    }

    private void onStderrLine(String line) {
        for (Consumer<String> listener : stderrListeners) {
            try {
                listener.accept(line);
            } catch (RuntimeException e) {
                log.debug("stderr listener failed", e);
            }
        }
    }

    private void completePending(CodexEvent event) {
        JsonNode id = event.id();
        if (id == null) {
            return;
        }
        CompletableFuture<JsonNode> future = pending.remove(id.asText());
        if (future == null) {
            log.debug("Response for unknown request id {}", id);
            return;
        }
        if (event.kind() == CodexEvent.Kind.ERROR_RESPONSE) {
            future.completeExceptionally(new CodexProtocolException(event.error()));
        } else {
            future.complete(event.result());
        }
    }

    private void dispatchNotification(CodexEvent event) {
        for (Consumer<CodexEvent> listener : notificationListeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                log.warn("Notification listener failed for {}", event.method(), e);
            }
        }
    }

    /**
     * Server requests must always be answered: an unanswered request stalls the turn inside
     * Codex. Unknown methods therefore get an explicit error rather than silence.
     */
    private void handleServerRequest(CodexEvent event) {
        Function<CodexEvent, JsonNode> handler = serverRequestHandlers.get(event.method());
        if (handler == null) {
            log.info("Unsupported server request {} - answering with error", event.method());
            respondError(event.id(), ERROR_METHOD_NOT_FOUND,
                    "CodexDesktop does not implement " + event.method());
            return;
        }
        try {
            JsonNode result = handler.apply(event);
            if (result != null) {
                respond(event.id(), result);
            }
            // A null result means the handler answers later (e.g. after the user decides).
        } catch (RuntimeException e) {
            log.warn("Server request handler for {} failed", event.method(), e);
            respondError(event.id(), ERROR_METHOD_NOT_FOUND, "handler failed: " + e.getMessage());
        }
    }

    /** Error response from the app-server, carrying the raw error node for diagnostics. */
    public static final class CodexProtocolException extends RuntimeException {
        private final transient JsonNode error;

        CodexProtocolException(JsonNode error) {
            super(error == null ? "unknown app-server error"
                    : error.path("message").asText(error.toString()));
            this.error = error;
        }

        public JsonNode error() {
            return error;
        }
    }
}
