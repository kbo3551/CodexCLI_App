package com.codexdesktop.codex;

/**
 * Method names of the Codex app-server protocol used by this client.
 *
 * <p>All values were verified against {@code codex-cli 0.154.0} by generating the protocol
 * schema ({@code codex app-server generate-ts --experimental}) and by driving a live server.
 * See {@code docs/PROTOCOL.md}.
 */
public final class CodexProtocol {

    private CodexProtocol() {
    }

    // client -> server requests
    public static final String INITIALIZE = "initialize";
    public static final String THREAD_START = "thread/start";
    public static final String THREAD_RESUME = "thread/resume";
    public static final String THREAD_LIST = "thread/list";
    public static final String THREAD_TURNS_LIST = "thread/turns/list";
    public static final String THREAD_UNSUBSCRIBE = "thread/unsubscribe";
    public static final String THREAD_SET_NAME = "thread/name/set";
    public static final String TURN_START = "turn/start";
    public static final String TURN_INTERRUPT = "turn/interrupt";
    public static final String GET_AUTH_STATUS = "getAuthStatus";
    public static final String ACCOUNT_RATE_LIMITS_READ = "account/rateLimits/read";
    public static final String ACCOUNT_USAGE_READ = "account/usage/read";
    public static final String ACCOUNT_READ = "account/read";
    public static final String MODEL_LIST = "model/list";
    public static final String FUZZY_FILE_SEARCH = "fuzzyFileSearch";
    public static final String THREAD_COMPACT_START = "thread/compact/start";

    // client -> server notifications
    public static final String INITIALIZED = "initialized";

    // server -> client notifications
    public static final String N_THREAD_STARTED = "thread/started";
    public static final String N_THREAD_STATUS_CHANGED = "thread/status/changed";
    public static final String N_THREAD_TOKEN_USAGE = "thread/tokenUsage/updated";
    public static final String N_ACCOUNT_RATE_LIMITS = "account/rateLimits/updated";
    public static final String N_ACCOUNT_UPDATED = "account/updated";
    public static final String N_TURN_STARTED = "turn/started";
    public static final String N_TURN_COMPLETED = "turn/completed";
    public static final String N_TURN_DIFF_UPDATED = "turn/diff/updated";
    public static final String N_ITEM_STARTED = "item/started";
    public static final String N_ITEM_COMPLETED = "item/completed";
    public static final String N_AGENT_MESSAGE_DELTA = "item/agentMessage/delta";
    public static final String N_REASONING_SUMMARY_DELTA = "item/reasoning/summaryTextDelta";
    public static final String N_REASONING_TEXT_DELTA = "item/reasoning/textDelta";
    public static final String N_COMMAND_OUTPUT_DELTA = "item/commandExecution/outputDelta";
    public static final String N_FILE_CHANGE_PATCH_UPDATED = "item/fileChange/patchUpdated";
    public static final String N_ERROR = "error";
    public static final String N_WARNING = "warning";
    public static final String N_SERVER_REQUEST_RESOLVED = "serverRequest/resolved";

    // server -> client requests
    public static final String R_COMMAND_APPROVAL = "item/commandExecution/requestApproval";
    public static final String R_FILE_CHANGE_APPROVAL = "item/fileChange/requestApproval";
    public static final String R_CURRENT_TIME_READ = "currentTime/read";

    // item types
    public static final String ITEM_USER_MESSAGE = "userMessage";
    public static final String ITEM_AGENT_MESSAGE = "agentMessage";
    public static final String ITEM_REASONING = "reasoning";
    public static final String ITEM_COMMAND_EXECUTION = "commandExecution";
    public static final String ITEM_FILE_CHANGE = "fileChange";
    public static final String ITEM_MCP_TOOL_CALL = "mcpToolCall";
    public static final String ITEM_WEB_SEARCH = "webSearch";
    public static final String ITEM_PLAN = "plan";
}
