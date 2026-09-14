package com.codexdesktop.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code model/list} payload shape.
 *
 * <p>The sample below is a trimmed capture from codex-cli 0.154.0. Reading the effort list from the
 * wrong field silently produced an empty list, which made the picker show no reasoning levels at
 * all — exactly the kind of failure a shape test catches.
 */
class ModelOptionTest {

    private static final String SAMPLE = """
            {"id":"gpt-5.6-luna","model":"gpt-5.6-luna","displayName":"GPT-5.6-Luna",
             "description":"","hidden":false,"isDefault":false,
             "defaultReasoningEffort":"medium",
             "supportedReasoningEfforts":[
               {"reasoningEffort":"low","description":"Fast responses with lighter reasoning"},
               {"reasoningEffort":"medium","description":"Balances speed and reasoning depth"},
               {"reasoningEffort":"high","description":"Greater reasoning depth"},
               {"reasoningEffort":"xhigh","description":"Extra high reasoning depth"}]}""";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsEffortsFromTheReasoningEffortField() throws Exception {
        ModelOption option = ModelOption.from(mapper.readTree(SAMPLE));

        assertEquals("gpt-5.6-luna", option.id());
        assertEquals("GPT-5.6-Luna", option.displayName());
        assertEquals("medium", option.defaultEffort());
        assertEquals(java.util.List.of("low", "medium", "high", "xhigh"), option.supportedEfforts());
    }

    /** Older servers sent plain strings; both forms must work. */
    @Test
    void alsoAcceptsBareStringEfforts() throws Exception {
        ModelOption option = ModelOption.from(mapper.readTree("""
                {"model":"legacy","displayName":"Legacy",
                 "supportedReasoningEfforts":["low","high"],"defaultReasoningEffort":"low"}"""));

        assertEquals(java.util.List.of("low", "high"), option.supportedEfforts());
    }

    @Test
    void fallsBackToTheIdWhenModelIsAbsent() throws Exception {
        ModelOption option = ModelOption.from(mapper.readTree(
                "{\"id\":\"only-id\",\"supportedReasoningEfforts\":[]}"));

        assertEquals("only-id", option.id());
        assertEquals("only-id", option.displayName());
        assertTrue(option.supportedEfforts().isEmpty());
    }

    /** A user pick has to survive into the status bar, which reads it from ThreadConfig. */
    @Test
    void selectionOverridesTheThreadConfig() {
        ThreadConfig config = new ThreadConfig("gpt-5.6-sol", "openai", "medium",
                "on-request", "workspace-write", "/mnt/c/dev");

        ThreadConfig updated = config.withSelection("gpt-5.6-luna", "high");
        assertEquals("gpt-5.6-luna", updated.model());
        assertEquals("high", updated.reasoningEffort());
        // Policy fields are untouched: picking a model must not silently change the sandbox.
        assertEquals("on-request", updated.approvalPolicy());
        assertEquals("workspace-write", updated.sandboxMode());

        ThreadConfig effortOnly = config.withSelection("", "low");
        assertEquals("gpt-5.6-sol", effortOnly.model());
        assertEquals("low", effortOnly.reasoningEffort());
    }
}
