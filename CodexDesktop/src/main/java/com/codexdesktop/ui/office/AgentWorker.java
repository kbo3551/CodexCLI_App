package com.codexdesktop.ui.office;

/**
 * One agent in the office: a Codex thread drawn as a little worker with an assigned desk.
 *
 * <p>Positions are in tile units with fractions, so movement is smooth while the scene stays on a
 * pixel grid. The state machine is deliberately tiny — the office is a status display, and every
 * transition is driven by a real protocol event rather than a timer.
 */
public final class AgentWorker {

    /** What the worker is doing, which decides both the pose and where it walks. */
    public enum State {
        /** Standing in the lounge, no work assigned. */
        LOUNGE,
        /** Moving between the lounge and the desk. */
        WALKING,
        /** Seated and typing: a turn is running. */
        WORKING,
        /** Seated, blocked on an approval the user has to answer. */
        WAITING,
        /** Seated after finishing a turn. */
        IDLE
    }

    private final String threadId;
    private final String name;
    private final String title;
    private final int deskIndex;
    private final int paletteIndex;

    private double x;
    private double y;
    private double targetX;
    private double targetY;
    private State state = State.LOUNGE;
    private String activity = "";
    private boolean facingRight = true;
    /** Advances only while walking or typing, so idle workers cost nothing to animate. */
    private double animationClock;
    private boolean active;
    private boolean hovered;
    /** Pose to adopt when the current walk finishes. */
    private State arrivalState = State.IDLE;
    /** Seconds before this worker may wander again; huge while it has work to do. */
    private double wanderCooldown;

    public AgentWorker(String threadId, String name, String title, int deskIndex, int paletteIndex,
                       double x, double y) {
        this.threadId = threadId;
        this.name = name;
        this.title = title == null ? "" : title;
        this.deskIndex = deskIndex;
        this.paletteIndex = paletteIndex;
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
    }

    public String threadId() {
        return threadId;
    }

    public String name() {
        return name;
    }

    /** The thread title, shown on hover so the seating chart stays readable. */
    public String title() {
        return title;
    }

    public int deskIndex() {
        return deskIndex;
    }

    public int paletteIndex() {
        return paletteIndex;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public State state() {
        return state;
    }

    public String activity() {
        return activity;
    }

    public boolean facingRight() {
        return facingRight;
    }

    public double animationClock() {
        return animationClock;
    }

    /** True for the thread currently open in the app, which is highlighted. */
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** True while the pointer is over this worker, which reveals its name. */
    public boolean isHovered() {
        return hovered;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public void setActivity(String activity) {
        this.activity = activity == null ? "" : activity;
    }

    public void setState(State state) {
        this.state = state;
    }

    /** Sends the worker walking to a tile, with the pose to adopt on arrival. */
    public void walkTo(double tileX, double tileY, State onArrival) {
        this.targetX = tileX;
        this.targetY = tileY;
        this.arrivalState = onArrival;
        if (Math.abs(tileX - x) > 0.01 || Math.abs(tileY - y) > 0.01) {
            this.state = State.WALKING;
        } else {
            this.state = onArrival;
        }
    }

    public void placeAt(double tileX, double tileY) {
        this.x = tileX;
        this.y = tileY;
        this.targetX = tileX;
        this.targetY = tileY;
    }

    public boolean isSeated() {
        return state == State.WORKING || state == State.WAITING || state == State.IDLE;
    }

    /** Seconds until this worker considers moving again; only meaningful when it has no work. */
    public double wanderCooldown() {
        return wanderCooldown;
    }

    public void setWanderCooldown(double seconds) {
        this.wanderCooldown = seconds;
    }

    /**
     * Advances the walk and animation clocks.
     *
     * @param deltaSeconds frame time
     */
    public void update(double deltaSeconds) {
        if (state == State.WALKING) {
            double speed = 2.6; // tiles per second
            double step = speed * deltaSeconds;
            // Walk in an L shape: along x first, then y. Corridors in the layout are axis-aligned,
            // so this reads naturally without needing pathfinding.
            if (Math.abs(targetX - x) > 0.02) {
                facingRight = targetX > x;
                x += Math.copySign(Math.min(step, Math.abs(targetX - x)), targetX - x);
            } else if (Math.abs(targetY - y) > 0.02) {
                y += Math.copySign(Math.min(step, Math.abs(targetY - y)), targetY - y);
            } else {
                x = targetX;
                y = targetY;
                state = arrivalState;
            }
            animationClock += deltaSeconds;
        } else if (state == State.WORKING) {
            animationClock += deltaSeconds;
        } else if (wanderCooldown > 0) {
            wanderCooldown -= deltaSeconds;
        }
    }

    /** True when the scene needs to keep repainting for this worker. */
    public boolean needsAnimation() {
        return state == State.WALKING || state == State.WORKING || state == State.WAITING;
    }
}
