package com.codexdesktop.ui.office;

import com.codexdesktop.codex.ApprovalRequest;
import com.codexdesktop.codex.CodexSessionListener;
import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.CodexThreadSummary;
import com.codexdesktop.ui.Ui;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The agent office: every Codex thread in the project is a worker with a desk.
 *
 * <p>It is a view of real state, not a decoration — a worker walks to its desk and starts typing
 * because a {@code turn/started} arrived, raises a marker because an approval request arrived, and
 * goes idle on {@code turn/completed}.
 *
 * <p>The animation timer only runs while the view is visible and something is actually moving, so
 * leaving the app open on another screen costs no frames.
 */
public final class AgentOfficeView extends VBox implements CodexSessionListener {

    private static final int MAX_WORKERS = 12;
    /** 16 tiles * 16 px * 2 = 512 for the room, plus the panel border and padding. Below this the
     *  renderer drops to a 1x zoom and the office becomes unreadable, so it is the hard floor. */
    private static final double PANEL_MIN_WIDTH = 530;
    private static final double PANEL_WIDTH = 560;

    private final Canvas canvas = new Canvas();
    private final OfficeRenderer renderer = new OfficeRenderer();
    private final Map<String, AgentWorker> workers = new LinkedHashMap<>();
    private final Label headline = Ui.label(I18n.t("office.title"), "title-strong");
    private final Label subtitle = Ui.label(I18n.t("office.subtitle"), "caption");
    private final Label legend = Ui.label("", "caption");
    private final AnimationTimer timer;

    private final javafx.scene.control.Button expandButton = Ui.iconButton("\u2922", null);
    private final javafx.scene.control.Button popOutButton = Ui.iconButton("\u2197", null);
    private java.util.function.Consumer<String> onWorkerActivated = id -> { };
    private Runnable onClose = () -> { };
    private Runnable onToggleExpand = () -> { };
    private Runnable onPopOut = () -> { };
    private boolean expanded;
    private boolean detached;
    private final java.util.Random random = new java.util.Random();
    private long lastFrameNanos;
    private boolean dirty = true;
    private String activeThreadId = "";

    public AgentOfficeView() {
        getStyleClass().add("office-view");
        setSpacing(0);
        setPrefWidth(PANEL_WIDTH);
        setMinWidth(PANEL_MIN_WIDTH);
        setMaxWidth(PANEL_WIDTH);

        expandButton.setOnAction(event -> onToggleExpand.run());
        Ui.attachTooltip(expandButton, I18n.t("office.expand"));
        popOutButton.setOnAction(event -> onPopOut.run());
        Ui.attachTooltip(popOutButton, I18n.t("office.popOut"));
        var closeButton = Ui.iconButton("\u00d7", I18n.t("office.close"));
        closeButton.setOnAction(event -> onClose.run());
        HBox header = Ui.row(6, new VBox(1, headline, subtitle), Ui.hSpacer(),
                popOutButton, expandButton, closeButton);
        header.getStyleClass().add("top-bar");

        Pane canvasHolder = new Pane(canvas);
        canvasHolder.getStyleClass().add("office-canvas");
        VBox.setVgrow(canvasHolder, Priority.ALWAYS);
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());
        canvas.widthProperty().addListener((observable, old, value) -> dirty = true);
        canvas.heightProperty().addListener((observable, old, value) -> dirty = true);

        getChildren().addAll(header, canvasHolder, legend);
        legend.getStyleClass().add("office-legend");

        // Hovering reveals a worker's name; clicking opens that thread.
        canvas.setOnMouseMoved(event -> {
            AgentWorker hit = workerAt(event.getX(), event.getY());
            boolean changed = false;
            for (AgentWorker worker : workers.values()) {
                boolean hovered = worker == hit;
                if (worker.isHovered() != hovered) {
                    worker.setHovered(hovered);
                    changed = true;
                }
            }
            canvas.setCursor(hit == null ? javafx.scene.Cursor.DEFAULT : javafx.scene.Cursor.HAND);
            if (changed) {
                requestFrame();
            }
        });
        canvas.setOnMouseExited(event -> {
            workers.values().forEach(worker -> worker.setHovered(false));
            requestFrame();
        });
        canvas.setOnMouseClicked(event -> {
            AgentWorker hit = workerAt(event.getX(), event.getY());
            if (hit != null && !hit.threadId().equals(activeThreadId)) {
                onWorkerActivated.accept(hit.threadId());
            }
        });

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double delta = lastFrameNanos == 0 ? 0.016 : (now - lastFrameNanos) / 1_000_000_000.0;
                lastFrameNanos = now;
                tick(Math.min(delta, 0.1));
            }
        };

        // Only animate while on screen; the scene graph keeps this node around when hidden.
        visibleProperty().addListener((observable, wasVisible, isVisible) -> {
            if (isVisible) {
                lastFrameNanos = 0;
                dirty = true;
                timer.start();
            } else {
                timer.stop();
            }
        });
    }

    /** Rebuilds the roster from the project's threads, keeping existing workers in place. */
    public void setThreads(List<CodexThreadSummary> threads, String activeId) {
        this.activeThreadId = activeId == null ? "" : activeId;
        int capacity = Math.min(MAX_WORKERS, renderer.map().capacity());

        Map<String, AgentWorker> rebuilt = new LinkedHashMap<>();
        int index = 0;
        for (CodexThreadSummary thread : threads) {
            if (index >= capacity) {
                break;
            }
            AgentWorker existing = workers.get(thread.id());
            if (existing != null && existing.deskIndex() == index) {
                rebuilt.put(thread.id(), existing);
            } else {
                // Agents are numbered by desk so the office reads as a stable seating chart; the
                // thread title is what shows on hover.
                AgentWorker worker = new AgentWorker(thread.id(),
                        I18n.t("office.agent", index + 1), threadTitle(thread), index,
                        Math.abs(thread.id().hashCode()) % 6,
                        renderer.map().loungeX(index), renderer.map().loungeY(index));
                double[] seat = seatFor(index);
                worker.placeAt(seat[0], seat[1]);
                worker.setState(AgentWorker.State.IDLE);
                rebuilt.put(thread.id(), worker);
            }
            index++;
        }

        workers.clear();
        workers.putAll(rebuilt);
        workers.forEach((id, worker) -> worker.setActive(id.equals(this.activeThreadId)));

        int hidden = Math.max(0, threads.size() - capacity);
        legend.setText(hidden > 0
                ? I18n.t("office.legendOverflow", workers.size(), hidden)
                : I18n.t("office.legend", workers.size()));
        dirty = true;
        requestFrame();
    }

    /** Invoked by the panel's close button. */
    public void setOnClose(Runnable handler) {
        this.onClose = handler == null ? () -> { } : handler;
    }

    /** Invoked by the expand button; the parent decides how to give the panel more room. */
    public void setOnToggleExpand(Runnable handler) {
        this.onToggleExpand = handler == null ? () -> { } : handler;
    }

    /**
     * Switches between the fixed side panel and filling the whole centre area.
     *
     * <p>Expanded, the renderer picks a larger integer zoom on its own, so the room simply gets
     * bigger rather than being stretched.
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        if (expanded) {
            setMaxWidth(Double.MAX_VALUE);
            setPrefWidth(Region.USE_COMPUTED_SIZE);
            HBox.setHgrow(this, Priority.ALWAYS);
            expandButton.setText("\u2921");
            Ui.attachTooltip(expandButton, I18n.t("office.collapse"));
        } else {
            setMaxWidth(PANEL_WIDTH);
            setPrefWidth(PANEL_WIDTH);
            HBox.setHgrow(this, Priority.NEVER);
            expandButton.setText("\u2922");
            Ui.attachTooltip(expandButton, I18n.t("office.expand"));
        }
        dirty = true;
        requestFrame();
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Invoked by the pop-out button. */
    public void setOnPopOut(Runnable handler) {
        this.onPopOut = handler == null ? () -> { } : handler;
    }

    /**
     * Switches between the docked panel and a standalone window.
     *
     * <p>Detached, the panel drops its width cap and the pop-out button turns into "dock again";
     * the expand button is meaningless in a window of its own, so it is hidden.
     */
    public void setDetached(boolean detached) {
        this.detached = detached;
        expandButton.setVisible(!detached);
        expandButton.setManaged(!detached);
        popOutButton.setText(detached ? "\u2913" : "\u2197");
        Ui.attachTooltip(popOutButton, I18n.t(detached ? "office.dock" : "office.popOut"));
        if (detached) {
            setMaxWidth(Double.MAX_VALUE);
            setPrefWidth(Region.USE_COMPUTED_SIZE);
        } else {
            setMaxWidth(PANEL_WIDTH);
            setPrefWidth(PANEL_WIDTH);
        }
        dirty = true;
        requestFrame();
    }

    private static String threadTitle(CodexThreadSummary thread) {
        String title = thread.title();
        if (title == null || title.isBlank()) {
            return "";
        }
        String collapsed = title.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 42 ? collapsed : collapsed.substring(0, 41) + "\u2026";
    }

    /** Sends the active worker back to the lounge, e.g. when a fresh thread is opened. */
    public void resetActiveWorker() {
        AgentWorker worker = activeWorker();
        if (worker == null) {
            return;
        }
        worker.setActivity("");
        worker.walkTo(renderer.map().loungeX(worker.deskIndex()),
                renderer.map().loungeY(worker.deskIndex()), AgentWorker.State.LOUNGE);
        requestFrame();
    }

    // --------------------------------------------------------------- session events

    @Override
    public void onTurnStarted(String turnId) {
        AgentWorker worker = activeWorker();
        if (worker == null) {
            return;
        }
        // A turn always wins over wandering: the agent heads straight back to its seat.
        double[] seat = seatFor(worker.deskIndex());
        worker.walkTo(seat[0], seat[1], AgentWorker.State.WORKING);
        worker.setWanderCooldown(Double.MAX_VALUE);
        worker.setActivity(I18n.t("activity.thinking"));
        requestFrame();
    }

    @Override
    public void onActivityChanged(String activity) {
        AgentWorker worker = activeWorker();
        if (worker == null) {
            return;
        }
        worker.setActivity(activity);
        if (!activity.isBlank() && worker.isSeated() && worker.state() != AgentWorker.State.WAITING) {
            worker.setState(AgentWorker.State.WORKING);
        }
        requestFrame();
    }

    @Override
    public void onApprovalRequested(ApprovalRequest request) {
        AgentWorker worker = activeWorker();
        if (worker != null && worker.isSeated()) {
            worker.setState(AgentWorker.State.WAITING);
            worker.setActivity(I18n.t("activity.waitingApproval"));
            requestFrame();
        }
    }

    @Override
    public void onApprovalResolved(String itemId) {
        AgentWorker worker = activeWorker();
        if (worker != null && worker.state() == AgentWorker.State.WAITING) {
            worker.setState(AgentWorker.State.WORKING);
            requestFrame();
        }
    }

    @Override
    public void onTurnCompleted(String turnId, String status, JsonNode turn) {
        AgentWorker worker = activeWorker();
        if (worker == null) {
            return;
        }
        worker.setActivity(switch (status) {
            case "interrupted" -> I18n.t("office.stopped");
            case "failed" -> I18n.t("office.failed");
            default -> I18n.t("office.done");
        });
        if (worker.isSeated()) {
            worker.setState(AgentWorker.State.IDLE);
        }
        // Clear the activity a little later so the outcome stays readable, then let it wander.
        worker.setWanderCooldown(6);
        requestFrame();
    }

    // -------------------------------------------------------------------- internals

    private AgentWorker activeWorker() {
        if (activeThreadId.isBlank()) {
            return null;
        }
        return workers.get(activeThreadId);
    }

    /** Called with the thread id when a worker is clicked. */
    public void setOnWorkerActivated(java.util.function.Consumer<String> handler) {
        this.onWorkerActivated = handler == null ? id -> { } : handler;
    }

    /**
     * Maps a canvas point back to a worker.
     *
     * <p>The renderer reports the scale and offsets it used for the last frame, so the same integer
     * zoom is applied here instead of being recomputed and drifting out of sync.
     */
    private AgentWorker workerAt(double canvasX, double canvasY) {
        int scale = renderer.lastScale();
        if (scale <= 0) {
            return null;
        }
        double tileX = (canvasX - renderer.lastOffsetX()) / (OfficeRenderer.TILE * scale);
        double tileY = (canvasY - renderer.lastOffsetY()) / (OfficeRenderer.TILE * scale);
        AgentWorker best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AgentWorker worker : workers.values()) {
            double dx = tileX - worker.x();
            // The sprite stands above its tile, so bias the hit box upwards.
            double dy = tileY - (worker.y() - 0.45);
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = worker;
            }
        }
        return bestDistance <= 0.55 ? best : null;
    }

    /**
     * Seat coordinates for a slot: desks first, then chairs at the meeting table once the desks are
     * taken, so extra threads still get a place instead of vanishing.
     */
    private double[] seatFor(int index) {
        List<OfficeMap.Desk> desks = renderer.map().desks();
        if (index < desks.size()) {
            OfficeMap.Desk desk = desks.get(index);
            return new double[]{desk.seatX(), desk.seatY()};
        }
        List<OfficeMap.MeetingSeat> seats = renderer.map().meetingSeats();
        int meetingIndex = index - desks.size();
        if (meetingIndex < seats.size()) {
            OfficeMap.MeetingSeat seat = seats.get(meetingIndex);
            return new double[]{seat.x(), seat.y()};
        }
        return new double[]{renderer.map().loungeX(index), renderer.map().loungeY(index)};
    }

    private void tick(double deltaSeconds) {
        boolean animating = advance(deltaSeconds);
        if (animating || dirty) {
            draw();
        }
        if (!animating) {
            // Nothing is moving: stop burning frames until the next event.
            timer.stop();
        }
    }

    /**
     * Advances every worker.
     *
     * <p>Idle agents drift around the room: once their cooldown expires they stroll to a random
     * open tile, loiter, then head back to their desk. Only agents with no work do this, so a
     * running turn always looks like someone sitting and typing.
     *
     * @return true while something still needs to be redrawn on the next frame
     */
    private boolean advance(double deltaSeconds) {
        boolean animating = false;
        for (AgentWorker worker : workers.values()) {
            worker.update(deltaSeconds);
            if (worker.activity().isBlank() && worker.wanderCooldown() <= 0
                    && worker.state() != AgentWorker.State.WALKING) {
                startWander(worker);
            }
            animating |= worker.needsAnimation();
        }
        return animating;
    }

    /** Alternates between strolling to a random tile and returning to the desk. */
    private void startWander(AgentWorker worker) {
        if (worker.isSeated()) {
            List<double[]> spots = renderer.map().wanderSpots();
            if (spots.isEmpty()) {
                worker.setWanderCooldown(10);
                return;
            }
            double[] spot = spots.get(random.nextInt(spots.size()));
            worker.walkTo(spot[0], spot[1], AgentWorker.State.LOUNGE);
            worker.setWanderCooldown(4 + random.nextDouble() * 8);
        } else {
            double[] seat = seatFor(worker.deskIndex());
            worker.walkTo(seat[0], seat[1], AgentWorker.State.IDLE);
            worker.setWanderCooldown(6 + random.nextDouble() * 10);
        }
    }

    private void draw() {
        renderer.render(canvas, new ArrayList<>(workers.values()));
        dirty = false;
    }

    /**
     * Runs the simulation for a fixed span and draws one frame immediately.
     *
     * <p>Needed where there is no animation pulse to wait for — the headless UI preview snapshots
     * the scene outside the JavaFX pulse, so without this every worker would be caught mid-walk.
     */
    public void renderFrameNow(double seconds) {
        int steps = Math.max(1, (int) Math.round(seconds / 0.016));
        for (int step = 0; step < steps; step++) {
            advance(0.016);
        }
        draw();
    }


    private void requestFrame() {
        dirty = true;
        if (isVisible()) {
            lastFrameNanos = 0;
            timer.start();
        }
    }
}
