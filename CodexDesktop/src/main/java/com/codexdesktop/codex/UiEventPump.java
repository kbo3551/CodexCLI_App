package com.codexdesktop.codex;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Moves protocol events from reader threads onto the JavaFX thread in batches.
 *
 * <p>Streaming turns emit one {@code item/agentMessage/delta} per token. Calling
 * {@link Platform#runLater} for each one floods the FX queue and makes the UI stutter, so events
 * are queued and drained on the pulse instead — at most once per frame, in arrival order. The
 * consumer therefore sees several deltas per drain and can coalesce them into a single text
 * update.
 *
 * <p>The timer only runs while events are actually flowing, so an idle window costs nothing.
 */
public final class UiEventPump {

    private final ConcurrentLinkedQueue<CodexEvent> queue = new ConcurrentLinkedQueue<>();
    private final Consumer<List<CodexEvent>> batchConsumer;
    private final long minIntervalNanos;

    private AnimationTimer timer;
    private long lastDrainNanos;
    private boolean running;

    /**
     * @param minIntervalMillis lower bound between drains; 0 means every frame. ~16-40 ms keeps
     *                          streaming visually smooth while bounding layout work.
     */
    public UiEventPump(long minIntervalMillis, Consumer<List<CodexEvent>> batchConsumer) {
        this.minIntervalNanos = minIntervalMillis * 1_000_000L;
        this.batchConsumer = batchConsumer;
    }

    /** Callable from any thread. */
    public void submit(CodexEvent event) {
        queue.add(event);
        if (!running) {
            Platform.runLater(this::ensureTimerRunning);
        }
    }

    private void ensureTimerRunning() {
        if (running) {
            return;
        }
        if (timer == null) {
            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (now - lastDrainNanos < minIntervalNanos) {
                        return;
                    }
                    lastDrainNanos = now;
                    drain();
                    if (queue.isEmpty()) {
                        stop();
                        running = false;
                    }
                }
            };
        }
        running = true;
        lastDrainNanos = 0;
        timer.start();
    }

    private void drain() {
        if (queue.isEmpty()) {
            return;
        }
        List<CodexEvent> batch = new ArrayList<>();
        CodexEvent event;
        while ((event = queue.poll()) != null) {
            batch.add(event);
        }
        batchConsumer.accept(batch);
    }

    /** Stops the timer and discards anything queued; used on shutdown. */
    public void dispose() {
        if (timer != null) {
            timer.stop();
        }
        running = false;
        queue.clear();
    }
}
