package com.codexdesktop.codex;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.AppSettings;
import com.codexdesktop.model.Attachment;
import com.codexdesktop.model.CodexThreadSummary;
import com.codexdesktop.model.FileMatch;
import com.codexdesktop.model.ModelOption;
import com.codexdesktop.model.ThreadConfig;
import com.codexdesktop.model.UsageSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Owns one Codex conversation: connects the host, starts or resumes a thread, runs turns, and
 * translates the raw event stream into {@link CodexSessionListener} callbacks on the FX thread.
 *
 * <p>Codex itself keeps the conversation state — this class holds only the ids needed to address
 * it, so nothing about the conversation is re-implemented on the client side.
 */
public final class CodexSessionService {

    private static final Logger log = LoggerFactory.getLogger(CodexSessionService.class);

    private final CodexAppServerClient client;
    private final UiEventPump pump;
    private final CopyOnWriteArrayList<CodexSessionListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, ApprovalRequest> pendingApprovals = new ConcurrentHashMap<>();

    private volatile String threadId;
    private volatile String activeTurnId;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile String modelName = "";
    private volatile UsageSnapshot usage = UsageSnapshot.empty();
    private volatile String selectedModel = "";
    private volatile String selectedEffort = "";
    private volatile ThreadConfig threadConfig = ThreadConfig.empty();

    public CodexSessionService(CodexAppServerClient client) {
        this.client = client;
        this.pump = new UiEventPump(24, this::handleBatch);

        client.addNotificationListener(pump::submit);
        client.registerServerRequestHandler(CodexProtocol.R_COMMAND_APPROVAL, event -> {
            queueApproval(ApprovalRequest.fromCommandRequest(event));
            return null; // answered later, once the user decides
        });
        client.registerServerRequestHandler(CodexProtocol.R_FILE_CHANGE_APPROVAL, event -> {
            queueApproval(ApprovalRequest.fromFileChangeRequest(event));
            return null;
        });
        client.host().setExitListener(code -> Platform.runLater(() -> {
            activeTurnId = null;
            threadId = null;
            setState(ConnectionState.DISCONNECTED, "app-server exited (code " + code + ")");
        }));
    }

    public void addListener(CodexSessionListener listener) {
        listeners.add(listener);
    }

    public String threadId() {
        return threadId;
    }

    public String modelName() {
        return modelName;
    }

    public ConnectionState state() {
        return state;
    }

    public boolean isTurnActive() {
        return activeTurnId != null;
    }

    public CodexAppServerClient client() {
        return client;
    }

    public UsageSnapshot usage() {
        return usage;
    }

    public ThreadConfig threadConfig() {
        return threadConfig;
    }

    /**
     * Asks for the account's rate-limit state.
     *
     * <p>The server also pushes {@code account/rateLimits/updated} after each turn, so this is only
     * needed to populate the status bar before the first turn has run.
     */
    public void refreshRateLimits() {
        client.request(CodexProtocol.ACCOUNT_RATE_LIMITS_READ, client.newParams())
                .thenAccept(result -> {
                    JsonNode limits = result.path("rateLimits").isMissingNode()
                            ? result : result.path("rateLimits");
                    if (limits.isObject()) {
                        Platform.runLater(() -> applyRateLimits(limits));
                    }
                })
                .exceptionally(error -> {
                    log.debug("Rate limits unavailable: {}", rootMessage(error));
                    return null;
                });
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Starts the host process and performs the handshake. Runs off the FX thread.
     *
     * @param wslWorkingDirectory WSL path used as the process working directory, may be null
     */
    public CompletableFuture<Void> connect(AppSettings settings, String wslWorkingDirectory) {
        setStateAsync(ConnectionState.CONNECTING, "starting app-server");
        return CompletableFuture.runAsync(() -> {
            try {
                CodexLaunchSpec spec = new CodexLaunchSpec(
                        settings.wslExecutable(),
                        settings.distribution(),
                        settings.codexExecutable(),
                        settings.codexArgumentList(),
                        wslWorkingDirectory);
                client.setDebugWireLogging(settings.debugLogging());
                client.start(spec);
            } catch (Exception e) {
                setStateAsync(ConnectionState.FAILED, e.getMessage());
                throw new IllegalStateException("Could not start Codex: " + e.getMessage(), e);
            }
        }).thenCompose(ignored -> handshake());
    }

    private CompletableFuture<Void> handshake() {
        ObjectNode params = client.newParams();
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "codex-desktop");
        clientInfo.put("title", "CodexDesktop");
        clientInfo.put("version", "0.1.0");
        ObjectNode capabilities = params.putObject("capabilities");
        capabilities.put("experimentalApi", false);
        capabilities.put("requestAttestation", false);

        return client.request(CodexProtocol.INITIALIZE, params).thenAccept(result -> {
            log.info("Connected to app-server: {}", result.path("userAgent").asText(""));
            client.notify(CodexProtocol.INITIALIZED, null);
            setStateAsync(ConnectionState.CONNECTED, result.path("userAgent").asText(""));
        }).exceptionally(error -> {
            setStateAsync(ConnectionState.FAILED, rootMessage(error));
            throw new IllegalStateException(rootMessage(error), error);
        });
    }

    /**
     * Creates a new thread rooted at the project directory.
     *
     * <p>{@code approvalPolicy} and {@code sandbox} are only sent when the user explicitly chose
     * them in settings; otherwise they are omitted so {@code ~/.codex/config.toml} decides.
     */
    public CompletableFuture<String> startThread(String wslCwd, AppSettings settings) {
        ObjectNode params = client.newParams();
        params.put("cwd", wslCwd);
        if (!settings.approvalPolicy().isBlank()) {
            params.put("approvalPolicy", settings.approvalPolicy());
        }
        if (!settings.sandboxMode().isBlank()) {
            params.put("sandbox", settings.sandboxMode());
        }
        return client.request(CodexProtocol.THREAD_START, params).thenApply(result -> {
            String id = result.path("thread").path("id").asText("");
            this.threadId = id;
            this.modelName = result.path("model").asText("");
            this.threadConfig = ThreadConfig.from(result);
            log.info("Started thread {} (model {}, approvals {}, sandbox {})",
                    id, modelName, threadConfig.approvalPolicy(), threadConfig.sandboxMode());
            ThreadConfig snapshot = this.threadConfig;
            Platform.runLater(() -> listeners.forEach(l -> l.onThreadConfigured(snapshot)));
            return id;
        });
    }

    /** Resumes an existing thread; the returned node is the app-server {@code Thread}. */
    public CompletableFuture<JsonNode> resumeThread(String existingThreadId, String wslCwd) {
        ObjectNode params = client.newParams();
        params.put("threadId", existingThreadId);
        if (wslCwd != null && !wslCwd.isBlank()) {
            params.put("cwd", wslCwd);
        }
        params.put("excludeTurns", true);
        return client.request(CodexProtocol.THREAD_RESUME, params).thenApply(result -> {
            JsonNode thread = result.path("thread");
            this.threadId = thread.path("id").asText(existingThreadId);
            this.modelName = thread.path("model").asText(modelName);
            log.info("Resumed thread {}", this.threadId);
            return thread;
        });
    }

    /** Lists threads whose cwd matches the project, newest first. */
    public CompletableFuture<List<CodexThreadSummary>> listThreads(String wslCwd, int limit) {
        ObjectNode params = client.newParams();
        params.put("limit", limit);
        params.put("sortKey", "updated_at");
        params.put("sortDirection", "desc");
        if (wslCwd != null && !wslCwd.isBlank()) {
            params.put("cwd", wslCwd);
        }
        return client.request(CodexProtocol.THREAD_LIST, params).thenApply(result -> {
            List<CodexThreadSummary> threads = new ArrayList<>();
            result.path("data").forEach(node -> threads.add(CodexThreadSummary.from(node)));
            return threads;
        });
    }

    /** Loads recent turns of a thread so a resumed conversation can be rendered. */
    public CompletableFuture<List<JsonNode>> listTurns(String targetThreadId, int limit) {
        ObjectNode params = client.newParams();
        params.put("threadId", targetThreadId);
        params.put("limit", limit);
        params.put("itemsView", "full");
        return client.request(CodexProtocol.THREAD_TURNS_LIST, params).thenApply(result -> {
            List<JsonNode> turns = new ArrayList<>();
            result.path("data").forEach(turns::add);
            return turns;
        });
    }

    /** Sends a user message, which starts a turn. */
    public CompletableFuture<String> sendUserMessage(String text) {
        return sendUserMessage(text, List.of());
    }

    /**
     * Sends a user message with optional attachments.
     *
     * <p>Attachments are mapped onto the protocol's own input kinds: images become
     * {@code localImage} entries and other files become {@code mention} entries, which is how the
     * Codex CLI itself passes them. Paths are WSL paths because Codex resolves them inside WSL.
     */
    public CompletableFuture<String> sendUserMessage(String text, List<Attachment> attachments) {
        String currentThread = this.threadId;
        if (currentThread == null || currentThread.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No active Codex thread"));
        }
        ObjectNode params = client.newParams();
        params.put("threadId", currentThread);
        ArrayNode input = params.putArray("input");

        for (Attachment attachment : attachments) {
            ObjectNode node = input.addObject();
            if (attachment.image()) {
                node.put("type", "localImage");
                node.put("path", attachment.wslPath());
            } else {
                node.put("type", "mention");
                node.put("name", attachment.name());
                node.put("path", attachment.wslPath());
            }
        }

        ObjectNode textInput = input.addObject();
        textInput.put("type", "text");
        textInput.put("text", text);
        textInput.putArray("text_elements");

        if (!selectedModel.isBlank()) {
            params.put("model", selectedModel);
        }
        if (!selectedEffort.isBlank()) {
            params.put("effort", selectedEffort);
        }

        return client.request(CodexProtocol.TURN_START, params).thenApply(result -> {
            String turnId = result.path("turn").path("id").asText("");
            this.activeTurnId = turnId;
            return turnId;
        });
    }

    /** Interrupts the running turn using the protocol's own cancellation, not a process kill. */
    public CompletableFuture<Void> interruptTurn() {
        String currentThread = this.threadId;
        String currentTurn = this.activeTurnId;
        if (currentThread == null || currentTurn == null) {
            return CompletableFuture.completedFuture(null);
        }
        ObjectNode params = client.newParams();
        params.put("threadId", currentThread);
        params.put("turnId", currentTurn);
        log.info("Interrupting turn {}", currentTurn);
        return client.request(CodexProtocol.TURN_INTERRUPT, params).thenAccept(result -> { });
    }

    /** Models the account can use, for the model picker. */
    public CompletableFuture<List<ModelOption>> listModels() {
        ObjectNode params = client.newParams();
        params.put("limit", 40);
        return client.request(CodexProtocol.MODEL_LIST, params).thenApply(result -> {
            List<ModelOption> models = new ArrayList<>();
            result.path("data").forEach(node -> {
                if (!node.path("hidden").asBoolean(false)) {
                    models.add(ModelOption.from(node));
                }
            });
            return models;
        });
    }

    /**
     * Selects the model and reasoning effort for subsequent turns.
     *
     * <p>Applied as {@code turn/start} overrides, which the protocol documents as persisting for
     * "this turn and subsequent turns" — so no extra request is needed and the thread keeps its
     * history.
     */
    public void selectModel(String model, String effort) {
        this.selectedModel = model == null ? "" : model;
        this.selectedEffort = effort == null ? "" : effort;
        this.threadConfig = threadConfig.withSelection(this.selectedModel, this.selectedEffort);
        this.modelName = this.selectedModel.isBlank() ? modelName : this.selectedModel;
        ThreadConfig snapshot = this.threadConfig;
        Platform.runLater(() -> listeners.forEach(l -> l.onThreadConfigured(snapshot)));
    }

    /** Asks Codex to compact the thread's context, as the CLI's compact command does. */
    public CompletableFuture<Void> compactThread() {
        String currentThread = this.threadId;
        if (currentThread == null) {
            return CompletableFuture.completedFuture(null);
        }
        ObjectNode params = client.newParams();
        params.put("threadId", currentThread);
        return client.request(CodexProtocol.THREAD_COMPACT_START, params).thenAccept(result -> { });
    }

    /**
     * Fuzzy file search used by the {@code @} mention picker.
     *
     * <p>Delegated to the server rather than walking the tree from Windows: Codex already indexes
     * the workspace, respects ignore rules, and answers in about 150 ms — and it searches inside
     * WSL, so the paths it returns are the ones a mention needs.
     */
    public CompletableFuture<List<FileMatch>> searchFiles(String query, String root, int limit) {
        if (query == null || query.isBlank() || root == null || root.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        ObjectNode params = client.newParams();
        params.put("query", query);
        params.putArray("roots").add(root);
        params.putNull("cancellationToken");
        return client.request(CodexProtocol.FUZZY_FILE_SEARCH, params).thenApply(result -> {
            List<FileMatch> matches = new ArrayList<>();
            result.path("files").forEach(node -> {
                if (matches.size() < limit) {
                    matches.add(FileMatch.from(node));
                }
            });
            return matches;
        });
    }

    /** Answers a pending approval with the user's decision. */
    public void resolveApproval(ApprovalRequest request, String decision) {
        ObjectNode result = client.newParams();
        result.put("decision", decision);
        client.respond(request.requestId(), result);
        pendingApprovals.remove(request.itemId());
        log.info("Approval {} for item {} -> {}", request.kind(), request.itemId(), decision);
    }

    public void shutdown() {
        // Decline anything still pending so Codex is not left waiting on a closed window.
        pendingApprovals.values().forEach(request -> {
            try {
                resolveApproval(request, ApprovalRequest.CANCEL);
            } catch (RuntimeException e) {
                log.debug("Could not cancel pending approval: {}", e.getMessage());
            }
        });
        pump.dispose();
        client.shutdown();
        threadId = null;
        activeTurnId = null;
        state = ConnectionState.DISCONNECTED;
    }

    // ---------------------------------------------------------------- events

    private void queueApproval(ApprovalRequest request) {
        pendingApprovals.put(request.itemId(), request);
        Platform.runLater(() -> {
            fireActivity(I18n.t("activity.waitingApproval"));
            listeners.forEach(listener -> listener.onApprovalRequested(request));
        });
    }

    /** Called on the FX thread with every event received since the previous frame. */
    private void handleBatch(List<CodexEvent> batch) {
        for (CodexEvent event : batch) {
            try {
                dispatch(event);
            } catch (RuntimeException e) {
                log.warn("Failed to handle {}", event.method(), e);
            }
        }
    }

    private void dispatch(CodexEvent event) {
        JsonNode params = event.paramsOrEmpty();
        switch (event.method()) {
            case CodexProtocol.N_TURN_STARTED -> {
                activeTurnId = params.path("turn").path("id").asText(null);
                fireActivity(I18n.t("activity.thinking"));
                listeners.forEach(l -> l.onTurnStarted(activeTurnId));
            }
            case CodexProtocol.N_TURN_COMPLETED -> {
                JsonNode turn = params.path("turn");
                String status = turn.path("status").asText("completed");
                String finishedTurn = turn.path("id").asText("");
                activeTurnId = null;
                fireActivity("");
                listeners.forEach(l -> l.onTurnCompleted(finishedTurn, status, turn));
            }
            case CodexProtocol.N_ITEM_STARTED -> {
                JsonNode item = params.path("item");
                String type = item.path("type").asText("");
                fireActivity(activityFor(type, item));
                listeners.forEach(l -> l.onItemStarted(item.path("id").asText(""), type, item));
            }
            case CodexProtocol.N_ITEM_COMPLETED -> {
                JsonNode item = params.path("item");
                String type = item.path("type").asText("");
                listeners.forEach(l -> l.onItemCompleted(item.path("id").asText(""), type, item));
            }
            case CodexProtocol.N_AGENT_MESSAGE_DELTA -> {
                fireActivity(I18n.t("activity.writing"));
                String itemId = params.path("itemId").asText("");
                String delta = params.path("delta").asText("");
                listeners.forEach(l -> l.onAgentMessageDelta(itemId, delta));
            }
            case CodexProtocol.N_REASONING_SUMMARY_DELTA, CodexProtocol.N_REASONING_TEXT_DELTA -> {
                fireActivity(I18n.t("activity.thinking"));
                String itemId = params.path("itemId").asText("");
                String delta = params.path("delta").asText("");
                listeners.forEach(l -> l.onReasoningDelta(itemId, delta));
            }
            case CodexProtocol.N_COMMAND_OUTPUT_DELTA -> {
                String itemId = params.path("itemId").asText("");
                String delta = params.path("delta").asText("");
                listeners.forEach(l -> l.onCommandOutputDelta(itemId, delta));
            }
            case CodexProtocol.N_FILE_CHANGE_PATCH_UPDATED -> {
                // Rendered from the completed item; nothing to do incrementally.
            }
            case CodexProtocol.N_TURN_DIFF_UPDATED -> {
                String diff = params.path("diff").asText("");
                listeners.forEach(l -> l.onTurnDiff(diff));
            }
            case CodexProtocol.N_ERROR -> {
                String message = params.path("error").path("message").asText("Codex reported an error");
                boolean willRetry = params.path("willRetry").asBoolean(false);
                listeners.forEach(l -> l.onError(message, willRetry));
            }
            case CodexProtocol.N_WARNING -> {
                String message = params.path("message").asText("");
                if (!message.isBlank()) {
                    listeners.forEach(l -> l.onNotice(message));
                }
            }
            case CodexProtocol.N_SERVER_REQUEST_RESOLVED -> {
                String itemId = params.path("itemId").asText("");
                pendingApprovals.remove(itemId);
                listeners.forEach(l -> l.onApprovalResolved(itemId));
            }
            case CodexProtocol.N_THREAD_STATUS_CHANGED -> {
                String status = params.path("status").path("type").asText("");
                if ("idle".equals(status)) {
                    fireActivity("");
                }
            }
            case CodexProtocol.N_THREAD_TOKEN_USAGE -> {
                JsonNode tokenUsage = params.path("tokenUsage");
                if (tokenUsage.isObject()) {
                    usage = usage.withTokens(tokenUsage);
                    fireUsage();
                }
            }
            case CodexProtocol.N_ACCOUNT_RATE_LIMITS -> {
                JsonNode limits = params.path("rateLimits");
                if (limits.isObject()) {
                    applyRateLimits(limits);
                }
            }
            default -> { /* the protocol emits many events this UI does not need */ }
        }
    }

    /** Maps an item to the label shown next to the spinner. */
    private static String activityFor(String itemType, JsonNode item) {
        return switch (itemType) {
            case CodexProtocol.ITEM_REASONING -> I18n.t("activity.thinking");
            case CodexProtocol.ITEM_AGENT_MESSAGE -> I18n.t("activity.writing");
            case CodexProtocol.ITEM_FILE_CHANGE -> I18n.t("activity.editing");
            case CodexProtocol.ITEM_WEB_SEARCH -> I18n.t("activity.webSearch");
            case CodexProtocol.ITEM_MCP_TOOL_CALL -> I18n.t("activity.callingTool");
            case CodexProtocol.ITEM_PLAN -> I18n.t("activity.planning");
            case CodexProtocol.ITEM_COMMAND_EXECUTION -> commandActivity(item);
            default -> I18n.t("activity.working");
        };
    }

    private static String commandActivity(JsonNode item) {
        JsonNode actions = item.path("commandActions");
        if (actions.isArray() && !actions.isEmpty()) {
            String first = actions.get(0).path("type").asText("");
            return switch (first) {
                case "read" -> I18n.t("activity.reading");
                case "search" -> I18n.t("activity.searching");
                case "listFiles" -> I18n.t("activity.exploring");
                default -> I18n.t("activity.runningCommand");
            };
        }
        return I18n.t("activity.runningCommand");
    }

    private void fireActivity(String activity) {
        listeners.forEach(l -> l.onActivityChanged(activity));
    }

    private void applyRateLimits(JsonNode rateLimits) {
        usage = usage.withRateLimits(rateLimits);
        fireUsage();
    }

    private void fireUsage() {
        UsageSnapshot snapshot = usage;
        listeners.forEach(l -> l.onUsageChanged(snapshot));
    }

    /**
     * Updates the state immediately and notifies listeners on the FX thread.
     *
     * <p>The field is assigned synchronously so callers polling {@link #state()} right after a
     * future completes see the new value; only the UI notification is deferred.
     */
    private void setState(ConnectionState newState, String detail) {
        this.state = newState;
        String text = detail == null ? "" : detail;
        if (Platform.isFxApplicationThread()) {
            listeners.forEach(l -> l.onConnectionStateChanged(newState, text));
        } else {
            Platform.runLater(() -> listeners.forEach(l -> l.onConnectionStateChanged(newState, text)));
        }
    }

    private void setStateAsync(ConnectionState newState, String detail) {
        setState(newState, detail);
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
