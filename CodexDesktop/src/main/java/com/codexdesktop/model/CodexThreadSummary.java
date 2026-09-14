package com.codexdesktop.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** A row in the THREADS list, projected from the app-server {@code Thread} object. */
public record CodexThreadSummary(String id,
                                 String title,
                                 String cwd,
                                 long updatedAtSeconds,
                                 String gitBranch) {

    public static CodexThreadSummary from(JsonNode thread) {
        String name = text(thread.path("name"));
        String preview = text(thread.path("preview"));
        String title = !name.isBlank() ? name
                : (!preview.isBlank() ? preview : com.codexdesktop.i18n.I18n.t("thread.untitled"));
        return new CodexThreadSummary(
                text(thread.path("id")),
                oneLine(title),
                text(thread.path("cwd")),
                thread.path("updatedAt").asLong(thread.path("createdAt").asLong(0L)),
                text(thread.path("gitInfo").path("branch")));
    }

    /** "Today" / "Yesterday" / ISO date, used to group the sidebar list. */
    public String dayGroup() {
        if (updatedAtSeconds <= 0) {
            return com.codexdesktop.i18n.I18n.t("thread.earlier");
        }
        var date = Instant.ofEpochSecond(updatedAtSeconds).atZone(ZoneId.systemDefault()).toLocalDate();
        var today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
        long days = ChronoUnit.DAYS.between(date, today);
        if (days <= 0) {
            return com.codexdesktop.i18n.I18n.t("thread.today");
        }
        if (days == 1) {
            return com.codexdesktop.i18n.I18n.t("thread.yesterday");
        }
        if (days < 7) {
            return com.codexdesktop.i18n.I18n.t("thread.previous7");
        }
        return date.toString();
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private static String oneLine(String value) {
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 72 ? collapsed : collapsed.substring(0, 71) + "\u2026";
    }
}
