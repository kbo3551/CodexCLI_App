package com.codexdesktop.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;

/**
 * What the CLI's status view shows: how much of the context window the thread is using, and how
 * much of the account's rate limit has been consumed.
 *
 * <p>Built from the app-server's own {@code thread/tokenUsage/updated} and
 * {@code account/rateLimits/updated} payloads — the numbers are Codex's, not an estimate.
 */
public final class UsageSnapshot {

    /** Per-thread token counters plus the model's context window. */
    public record Tokens(long totalTokens, long inputTokens, long cachedInputTokens,
                         long outputTokens, long reasoningTokens, long contextWindow,
                         long lastTurnTokens) {

        public static Tokens empty() {
            return new Tokens(0, 0, 0, 0, 0, 0, 0);
        }

        public static Tokens from(JsonNode tokenUsage) {
            JsonNode total = tokenUsage.path("total");
            JsonNode last = tokenUsage.path("last");
            return new Tokens(
                    total.path("totalTokens").asLong(0),
                    total.path("inputTokens").asLong(0),
                    total.path("cachedInputTokens").asLong(0),
                    total.path("outputTokens").asLong(0),
                    total.path("reasoningOutputTokens").asLong(0),
                    tokenUsage.path("modelContextWindow").asLong(0),
                    last.path("totalTokens").asLong(0));
        }

        public boolean isEmpty() {
            return totalTokens == 0 && contextWindow == 0;
        }

        /**
         * Share of the context window in use.
         *
         * <p>Based on the most recent turn's input size, which is what actually competes for the
         * window; the running total across a thread can exceed it after compaction.
         */
        public int contextUsedPercent() {
            if (contextWindow <= 0) {
                return 0;
            }
            long used = Math.max(inputTokens, lastTurnTokens);
            return (int) Math.min(100, Math.round(used * 100.0 / contextWindow));
        }

        public long contextRemaining() {
            return contextWindow <= 0 ? 0 : Math.max(0, contextWindow - Math.max(inputTokens, lastTurnTokens));
        }
    }

    /** One rate-limit window as reported by the account. */
    public record Limit(int usedPercent, long windowMinutes, long resetsAtEpochSeconds) {

        public static Limit from(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
            return new Limit(
                    node.path("usedPercent").asInt(0),
                    node.path("windowDurationMins").asLong(0),
                    node.path("resetsAt").asLong(0));
        }

        /** Human window name: the CLI shows these as hourly / weekly buckets. */
        public String windowLabel() {
            if (windowMinutes <= 0) {
                return "";
            }
            if (windowMinutes % (60 * 24 * 7) == 0) {
                return (windowMinutes / (60 * 24 * 7)) + "w";
            }
            if (windowMinutes % (60 * 24) == 0) {
                return (windowMinutes / (60 * 24)) + "d";
            }
            if (windowMinutes % 60 == 0) {
                return (windowMinutes / 60) + "h";
            }
            return windowMinutes + "m";
        }

        /** Time until the window resets, or null when unknown or already past. */
        public Duration timeUntilReset() {
            if (resetsAtEpochSeconds <= 0) {
                return null;
            }
            Duration remaining = Duration.between(Instant.now(), Instant.ofEpochSecond(resetsAtEpochSeconds));
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }
    }

    private final Tokens tokens;
    private final Limit primary;
    private final Limit secondary;
    private final String planType;
    private final boolean hasCredits;
    private final String creditBalance;

    public UsageSnapshot(Tokens tokens, Limit primary, Limit secondary,
                        String planType, boolean hasCredits, String creditBalance) {
        this.tokens = tokens == null ? Tokens.empty() : tokens;
        this.primary = primary;
        this.secondary = secondary;
        this.planType = planType == null ? "" : planType;
        this.hasCredits = hasCredits;
        this.creditBalance = creditBalance == null ? "" : creditBalance;
    }

    public static UsageSnapshot empty() {
        return new UsageSnapshot(Tokens.empty(), null, null, "", false, "");
    }

    public UsageSnapshot withTokens(JsonNode tokenUsage) {
        return new UsageSnapshot(Tokens.from(tokenUsage), primary, secondary, planType, hasCredits, creditBalance);
    }

    public UsageSnapshot withRateLimits(JsonNode rateLimits) {
        JsonNode credits = rateLimits.path("credits");
        return new UsageSnapshot(tokens,
                Limit.from(rateLimits.get("primary")),
                Limit.from(rateLimits.get("secondary")),
                rateLimits.path("planType").asText(planType),
                credits.path("hasCredits").asBoolean(false),
                credits.path("balance").asText(""));
    }

    public Tokens tokens() {
        return tokens;
    }

    public Limit primary() {
        return primary;
    }

    public Limit secondary() {
        return secondary;
    }

    public String planType() {
        return planType;
    }

    public boolean hasCredits() {
        return hasCredits;
    }

    public String creditBalance() {
        return creditBalance;
    }

    public boolean hasAnyData() {
        return !tokens.isEmpty() || primary != null || !planType.isBlank();
    }
}
