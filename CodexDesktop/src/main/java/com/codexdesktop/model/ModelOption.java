package com.codexdesktop.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry from {@code model/list}, reduced to what the picker needs.
 *
 * @param supportedEfforts reasoning-effort ids this model accepts; drives the effort submenu
 */
public record ModelOption(String id,
                          String displayName,
                          String description,
                          List<String> supportedEfforts,
                          String defaultEffort,
                          boolean isDefault) {

    public static ModelOption from(JsonNode node) {
        List<String> efforts = new ArrayList<>();
        node.path("supportedReasoningEfforts").forEach(effort -> {
            // Verified against codex-cli 0.154.0: entries are objects keyed by "reasoningEffort"
            // (with a description alongside). Older servers used a bare string, so both are read.
            if (effort.isTextual()) {
                efforts.add(effort.asText());
                return;
            }
            for (String field : new String[]{"reasoningEffort", "effort", "id"}) {
                String value = effort.path(field).asText("");
                if (!value.isBlank()) {
                    efforts.add(value);
                    return;
                }
            }
        });
        String model = node.path("model").asText(node.path("id").asText(""));
        String display = node.path("displayName").asText(model);
        return new ModelOption(model, display,
                node.path("description").asText(""),
                List.copyOf(efforts),
                node.path("defaultReasoningEffort").asText(""),
                node.path("isDefault").asBoolean(false));
    }
}
