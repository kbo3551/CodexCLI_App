package com.codexdesktop.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The effective configuration the app-server chose for a thread.
 *
 * <p>Read back from the {@code thread/start} response rather than assumed, so the UI reports the
 * policy Codex is actually enforcing — including the values that came from
 * {@code ~/.codex/config.toml} when the app sent no override.
 */
public record ThreadConfig(String model,
                           String modelProvider,
                           String reasoningEffort,
                           String approvalPolicy,
                           String sandboxMode,
                           String cwd) {

    public static ThreadConfig empty() {
        return new ThreadConfig("", "", "", "", "", "");
    }

    public static ThreadConfig from(JsonNode threadStartResponse) {
        return new ThreadConfig(
                threadStartResponse.path("model").asText(""),
                threadStartResponse.path("modelProvider").asText(""),
                threadStartResponse.path("reasoningEffort").asText(""),
                describe(threadStartResponse.path("approvalPolicy")),
                sandboxOf(threadStartResponse),
                threadStartResponse.path("cwd").asText(""));
    }

    /**
     * {@code SandboxPolicy} is an internally tagged union, so the readable name lives in its
     * {@code mode} / {@code type} field depending on the variant.
     */
    private static String sandboxOf(JsonNode response) {
        JsonNode profile = response.path("activePermissionProfile");
        if (profile.isObject()) {
            String id = profile.path("id").asText("");
            if (!id.isBlank()) {
                return id;
            }
        }
        return describe(response.path("sandbox"));
    }

    /** Renders either a plain string variant or the tag of an object variant. */
    private static String describe(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            for (String field : new String[]{"mode", "type", "kind"}) {
                if (node.hasNonNull(field) && node.get(field).isTextual()) {
                    return node.get(field).asText();
                }
            }
            var names = node.fieldNames();
            if (names.hasNext()) {
                return names.next();
            }
        }
        return "";
    }

    public boolean isEmpty() {
        return model.isBlank() && approvalPolicy.isBlank() && sandboxMode.isBlank();
    }

    /** Reflects a user-chosen model or effort that will apply from the next turn on. */
    public ThreadConfig withSelection(String selectedModel, String selectedEffort) {
        return new ThreadConfig(
                selectedModel == null || selectedModel.isBlank() ? model : selectedModel,
                modelProvider,
                selectedEffort == null || selectedEffort.isBlank() ? reasoningEffort : selectedEffort,
                approvalPolicy, sandboxMode, cwd);
    }
}
