package com.codexdesktop.ui.office;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Draws the office scene onto a {@link Canvas}.
 *
 * <p>Everything is generated in code: no sprite sheets to ship, and the palette follows the app
 * theme. The scene is drawn in 16 px logical tiles through a scale transform so it stays a crisp
 * pixel grid at any size; labels are drawn afterwards in screen space so text stays smooth instead
 * of being scaled up into blocks.
 */
final class OfficeRenderer {

    static final int TILE = 16;

    /** Per-agent look: shirt, hair, skin, plus a hair style and accessory picked from the index. */
    private static final Color[][] PALETTES = {
            {Color.web("#4f7dfb"), Color.web("#2f2a26"), Color.web("#f0c9a4")},
            {Color.web("#4bb85f"), Color.web("#4a2f1d"), Color.web("#e8b58f")},
            {Color.web("#d5a03a"), Color.web("#1f1c1a"), Color.web("#d99f77")},
            {Color.web("#c96fd0"), Color.web("#6b4423"), Color.web("#f2d3b3")},
            {Color.web("#3fb6c4"), Color.web("#2b2b33"), Color.web("#c98f68")},
            {Color.web("#ef5f56"), Color.web("#8a5a2b"), Color.web("#efc19b")},
            {Color.web("#8f7ff0"), Color.web("#3b2a1b"), Color.web("#e0a983")},
            {Color.web("#5fb0e8"), Color.web("#54331c"), Color.web("#f5d9bd")},
    };

    private static final Color WALL = Color.web("#232028");
    private static final Color WALL_LOWER = Color.web("#2b2833");
    private static final Color WALL_TRIM = Color.web("#3a3646");
    private static final Color FLOOR_A = Color.web("#6f5238");
    private static final Color FLOOR_B = Color.web("#785a3f");
    private static final Color FLOOR_SEAM = Color.rgb(0, 0, 0, 0.12);
    private static final Color RUG = Color.web("#3c4b6b");
    private static final Color RUG_ALT = Color.web("#46577a");
    private static final Color DESK_TOP = Color.web("#8d6a4a");
    private static final Color DESK_LIGHT = Color.web("#9d7a58");
    private static final Color DESK_EDGE = Color.web("#5f452f");
    private static final Color TABLE_TOP = Color.web("#7a5c8f");
    private static final Color TABLE_EDGE = Color.web("#5c4269");
    private static final Color LAPTOP_BODY = Color.web("#b9bec9");
    private static final Color LAPTOP_DARK = Color.web("#8f949e");
    private static final Color SCREEN_OFF = Color.web("#15171f");
    private static final Color SCREEN_ON = Color.web("#8fdcff");
    private static final Color METAL = Color.web("#4a4f5e");
    private static final Color CHAIR = Color.web("#3a3f4d");
    private static final Color CHAIR_LIGHT = Color.web("#4c5263");
    private static final Color PLANT_POT = Color.web("#8a5238");
    private static final Color PLANT_LEAF = Color.web("#3f8a4a");
    private static final Color PLANT_LEAF_LIGHT = Color.web("#4fa95c");
    private static final Color SHELF = Color.web("#5b452f");
    private static final Color SKY_TOP = Color.web("#7fa9d8");
    private static final Color SKY_BOTTOM = Color.web("#bcd4e8");
    private static final Color SHADOW = Color.rgb(0, 0, 0, 0.26);
    private static final Color WHITEBOARD = Color.web("#e8e8ea");
    private static final Color COFFEE = Color.web("#f2f2f4");

    private final OfficeMap map = new OfficeMap();

    private int lastScale = 1;
    private double lastOffsetX;
    private double lastOffsetY;

    OfficeMap map() {
        return map;
    }

    /** Geometry of the most recent frame, used to turn pointer positions back into tiles. */
    int lastScale() {
        return lastScale;
    }

    double lastOffsetX() {
        return lastOffsetX;
    }

    double lastOffsetY() {
        return lastOffsetY;
    }

    int render(Canvas canvas, List<AgentWorker> workers) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();
        gc.clearRect(0, 0, canvasWidth, canvasHeight);
        if (canvasWidth < 4 || canvasHeight < 4) {
            return 1;
        }

        int scale = (int) Math.max(1, Math.floor(Math.min(
                canvasWidth / (map.width() * TILE), canvasHeight / (map.height() * TILE))));
        double sceneWidth = map.width() * TILE * scale;
        double sceneHeight = map.height() * TILE * scale;
        double offsetX = Math.floor((canvasWidth - sceneWidth) / 2);
        double offsetY = Math.floor(Math.max(0, (canvasHeight - sceneHeight) / 2));
        this.lastScale = scale;
        this.lastOffsetX = offsetX;
        this.lastOffsetY = offsetY;

        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(scale, scale);
        drawScenery(gc);
        drawWorkers(gc, workers);
        gc.restore();

        drawLabels(gc, workers, offsetX, offsetY, scale);
        if (workers.isEmpty()) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(Font.font("D2Coding", FontWeight.NORMAL, Math.max(11, 4.0 * scale)));
            gc.setFill(Color.web("#d5d5da", 0.85));
            gc.fillText(com.codexdesktop.i18n.I18n.t("office.empty"),
                    offsetX + sceneWidth / 2, offsetY + sceneHeight / 2);
        }
        return scale;
    }

    // ------------------------------------------------------------------ scenery

    private void drawScenery(GraphicsContext gc) {
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                drawFloorOrWall(gc, x, y);
            }
        }

        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                switch (map.tile(x, y)) {
                    case 'W' -> drawWindow(gc, x, y);
                    case 'L' -> drawClock(gc, x, y);
                    case 'P' -> drawPicture(gc, x, y);
                    case 'T' -> drawPlant(gc, x, y);
                    case 'B' -> drawBookshelf(gc, x, y);
                    case 'C' -> drawCabinet(gc, x, y);
                    case 'K' -> drawWaterCooler(gc, x, y);
                    case 'D' -> drawWhiteboard(gc, x, y);
                    default -> { }
                }
            }
        }
        drawMeetingTable(gc);
        for (OfficeMap.Desk desk : map.desks()) {
            drawWorkstation(gc, desk);
        }
        for (OfficeMap.MeetingSeat seat : map.meetingSeats()) {
            drawMeetingChair(gc, seat);
        }
    }

    private void drawFloorOrWall(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        char tile = map.tile(x, y);
        if (tile == '#') {
            gc.setFill(y == 0 ? WALL : WALL_LOWER);
            gc.fillRect(px, py, TILE, TILE);
            if (y == 0) {
                gc.setFill(WALL_TRIM);
                gc.fillRect(px, py + TILE - 2, TILE, 2);
            }
            return;
        }
        boolean checker = ((x + y) & 1) == 0;
        if (tile == '~') {
            gc.setFill(checker ? RUG : RUG_ALT);
            gc.fillRect(px, py, TILE, TILE);
            // Woven pattern: a couple of lighter threads per tile.
            gc.setFill(Color.rgb(255, 255, 255, 0.05));
            gc.fillRect(px, py + 4, TILE, 1);
            gc.fillRect(px + 6, py, 1, TILE);
            return;
        }
        gc.setFill(checker ? FLOOR_A : FLOOR_B);
        gc.fillRect(px, py, TILE, TILE);
        gc.setFill(FLOOR_SEAM);
        gc.fillRect(px, py + TILE - 1, TILE, 1);
        // A short grain mark keeps the boards from looking like flat blocks.
        gc.setFill(Color.rgb(0, 0, 0, 0.05));
        gc.fillRect(px + (checker ? 3 : 9), py + 5, 5, 1);
    }

    private void drawWindow(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        gc.setFill(WALL);
        gc.fillRect(px, py, TILE, TILE);
        // Sky gradient painted as two bands; a real gradient would blur at this scale.
        gc.setFill(SKY_TOP);
        gc.fillRect(px + 2, py + 3, TILE - 4, 4);
        gc.setFill(SKY_BOTTOM);
        gc.fillRect(px + 2, py + 7, TILE - 4, 4);
        gc.setFill(Color.web("#ffffff", 0.55));
        gc.fillRect(px + 4, py + 5, 3, 1);
        gc.fillRect(px + 8, py + 6, 4, 1);
        // Blinds and frame.
        gc.setFill(Color.rgb(0, 0, 0, 0.16));
        for (int line = 0; line < 3; line++) {
            gc.fillRect(px + 2, py + 4 + line * 3, TILE - 4, 1);
        }
        gc.setFill(WALL_TRIM);
        gc.fillRect(px + 1, py + 2, TILE - 2, 1);
        gc.fillRect(px + 1, py + 11, TILE - 2, 1);
        gc.fillRect(px + TILE / 2 - 1, py + 3, 1, 8);
    }

    private void drawClock(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        gc.setFill(WALL);
        gc.fillRect(px, py, TILE, TILE);
        gc.setFill(Color.web("#e6e6ea"));
        gc.fillOval(px + 4, py + 4, 8, 8);
        gc.setFill(Color.web("#2b2833"));
        gc.fillRect(px + 8, py + 6, 1, 3);
        gc.fillRect(px + 8, py + 8, 3, 1);
    }

    private void drawPicture(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        gc.setFill(WALL);
        gc.fillRect(px, py, TILE, TILE);
        gc.setFill(Color.web("#8a6b45"));
        gc.fillRect(px + 3, py + 3, 10, 8);
        gc.setFill(Color.web("#4f7dfb"));
        gc.fillRect(px + 4, py + 4, 8, 6);
        gc.setFill(Color.web("#4bb85f"));
        gc.fillRect(px + 4, py + 8, 8, 2);
        gc.setFill(Color.web("#f2d06b"));
        gc.fillOval(px + 9, py + 5, 2, 2);
    }

    private void drawWhiteboard(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        gc.setFill(SHADOW);
        gc.fillRect(px + 1, py + TILE - 3, TILE - 2, 2);
        gc.setFill(METAL);
        gc.fillRect(px, py + 1, TILE, 12);
        gc.setFill(WHITEBOARD);
        gc.fillRect(px + 1, py + 2, TILE - 2, 9);
        // Scribbles and a chart, so it reads as used.
        gc.setFill(Color.web("#4f7dfb"));
        gc.fillRect(px + 3, py + 4, 6, 1);
        gc.fillRect(px + 3, py + 6, 8, 1);
        gc.setFill(Color.web("#ef5f56"));
        gc.fillRect(px + 3, py + 8, 4, 1);
        gc.setFill(Color.web("#4bb85f"));
        gc.fillRect(px + 11, py + 5, 1, 4);
        gc.fillRect(px + 12, py + 7, 1, 2);
    }

    private void drawWaterCooler(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        gc.setFill(SHADOW);
        gc.fillOval(px + 3, py + TILE - 4, 10, 3);
        gc.setFill(Color.web("#dfe7ef"));
        gc.fillRect(px + 5, py + 1, 6, 5);
        gc.setFill(Color.web("#7cc4e8"));
        gc.fillRect(px + 6, py + 2, 4, 4);
        gc.setFill(METAL);
        gc.fillRect(px + 4, py + 6, 8, 8);
        gc.setFill(Color.web("#2b2833"));
        gc.fillRect(px + 7, py + 9, 2, 2);
    }

    private void drawPlant(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        gc.setFill(SHADOW);
        gc.fillOval(px + 3, py + TILE - 4, 10, 3);
        gc.setFill(PLANT_POT);
        gc.fillRect(px + 5, py + 10, 6, 5);
        gc.setFill(Color.rgb(0, 0, 0, 0.15));
        gc.fillRect(px + 5, py + 10, 6, 1);
        gc.setFill(PLANT_LEAF);
        gc.fillRect(px + 7, py + 3, 2, 8);
        gc.fillRect(px + 4, py + 5, 3, 2);
        gc.fillRect(px + 9, py + 4, 3, 2);
        gc.fillRect(px + 3, py + 8, 3, 2);
        gc.setFill(PLANT_LEAF_LIGHT);
        gc.fillRect(px + 5, py + 2, 2, 2);
        gc.fillRect(px + 10, py + 7, 2, 2);
        gc.fillRect(px + 8, py + 6, 2, 1);
    }

    private void drawBookshelf(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        gc.setFill(SHADOW);
        gc.fillRect(px + 1, py + TILE - 3, TILE - 2, 2);
        gc.setFill(SHELF);
        gc.fillRect(px + 1, py + 1, TILE - 2, TILE - 3);
        Color[] books = {Color.web("#c05a4e"), Color.web("#4f7dfb"), Color.web("#d5a03a"),
                Color.web("#4bb85f"), Color.web("#c96fd0")};
        for (int shelf = 0; shelf < 3; shelf++) {
            int shelfY = py + 3 + shelf * 4;
            for (int slot = 0; slot < 5; slot++) {
                // Deterministic pattern so the shelves are stable between frames.
                if (((x * 7 + y * 13 + shelf * 5 + slot * 3) % 5) == 0) {
                    continue;
                }
                gc.setFill(books[(shelf + slot + x) % books.length]);
                gc.fillRect(px + 2 + slot * 2.4, shelfY, 2, 3);
            }
            gc.setFill(DESK_EDGE);
            gc.fillRect(px + 1, shelfY + 3, TILE - 2, 1);
        }
    }

    private void drawCabinet(GraphicsContext gc, int x, int y) {
        int px = x * TILE;
        int py = y * TILE;
        gc.setFill(SHADOW);
        gc.fillRect(px + 2, py + TILE - 3, TILE - 4, 2);
        gc.setFill(METAL);
        gc.fillRect(px + 2, py + 2, TILE - 4, TILE - 4);
        gc.setFill(Color.rgb(255, 255, 255, 0.10));
        gc.fillRect(px + 2, py + 2, TILE - 4, 2);
        gc.setFill(WALL);
        for (int drawer = 0; drawer < 3; drawer++) {
            gc.fillRect(px + 4, py + 5 + drawer * 3, TILE - 8, 1);
        }
        // A mug left on top.
        gc.setFill(COFFEE);
        gc.fillRect(px + 5, py, 3, 2);
    }

    /** The long table in the middle, with papers, mugs and a laptop on it. */
    private void drawMeetingTable(GraphicsContext gc) {
        int startX = -1;
        int endX = -1;
        int row = -1;
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                if (map.tile(x, y) == 'M') {
                    if (startX < 0) {
                        startX = x;
                        row = y;
                    }
                    endX = x;
                }
            }
        }
        if (startX < 0) {
            return;
        }
        double left = startX * TILE;
        double top = row * TILE + 2;
        double width = (endX - startX + 1) * TILE;
        double height = TILE - 2;

        gc.setFill(SHADOW);
        gc.fillRoundRect(left + 2, top + 4, width, height, 10, 10);
        gc.setFill(TABLE_TOP);
        gc.fillRoundRect(left, top, width, height, 10, 10);
        gc.setFill(Color.rgb(255, 255, 255, 0.07));
        gc.fillRoundRect(left, top, width, 3, 8, 8);
        gc.setFill(TABLE_EDGE);
        gc.fillRoundRect(left, top + height - 3, width, 3, 8, 8);

        // Props: mugs, a stack of paper, a laptop at the head of the table.
        gc.setFill(COFFEE);
        gc.fillOval(left + 18, top + 4, 4, 4);
        gc.fillOval(left + width - 26, top + 5, 4, 4);
        gc.setFill(Color.web("#e8e8ea"));
        gc.fillRect(left + width / 2 - 6, top + 4, 9, 6);
        gc.setFill(Color.rgb(0, 0, 0, 0.18));
        gc.fillRect(left + width / 2 - 6, top + 7, 9, 1);
        drawLaptop(gc, left + 34, top + 9, false, 0);
    }

    /** An empty chair at the meeting table: backrest, cushion and a slim pedestal. */
    private void drawMeetingChair(GraphicsContext gc, OfficeMap.MeetingSeat seat) {
        double cx = Math.round(seat.x() * TILE);
        double cy = Math.round(seat.y() * TILE);
        gc.setFill(SHADOW);
        gc.fillOval(cx - 5, cy + 3, 11, 4);
        // Backrest.
        gc.setFill(CHAIR);
        gc.fillRect(cx - 5, cy - 9, 10, 7);
        gc.setFill(CHAIR_LIGHT);
        gc.fillRect(cx - 4, cy - 8, 8, 5);
        gc.setFill(Color.rgb(255, 255, 255, 0.08));
        gc.fillRect(cx - 4, cy - 8, 8, 1);
        // Cushion and pedestal.
        gc.setFill(CHAIR);
        gc.fillRect(cx - 5, cy - 2, 10, 3);
        gc.setFill(METAL);
        gc.fillRect(cx - 1, cy + 1, 2, 3);
        gc.fillRect(cx - 4, cy + 4, 8, 1);
    }

    /**
     * A desk with a laptop on it.
     *
     * <p>Drawn as a slim silver notebook rather than a boxy monitor: the screen sits on a hinge line
     * with the deck in front, which reads as a laptop even at 16 px.
     */
    private void drawWorkstation(GraphicsContext gc, OfficeMap.Desk desk) {
        double centreX = desk.seatX() * TILE + TILE / 2.0;
        int deskTopY = desk.deskY() * TILE;

        double left = centreX - TILE * 0.95;
        double width = TILE * 1.9;
        gc.setFill(SHADOW);
        gc.fillRect(left + 1, deskTopY + TILE - 2, width, 3);
        gc.setFill(DESK_TOP);
        gc.fillRect(left, deskTopY + 5, width, TILE - 7);
        gc.setFill(DESK_LIGHT);
        gc.fillRect(left, deskTopY + 5, width, 1);
        gc.setFill(DESK_EDGE);
        gc.fillRect(left, deskTopY + TILE - 3, width, 3);

        drawLaptop(gc, centreX, deskTopY + 6, false, 0);

        // Desk clutter: a mug on one side, a notepad on the other.
        gc.setFill(COFFEE);
        gc.fillOval(left + 2, deskTopY + 7, 4, 4);
        gc.setFill(Color.web("#e8e8ea"));
        gc.fillRect(left + width - 7, deskTopY + 8, 5, 3);
    }

    /**
     * @param baseY  y of the desk surface the laptop stands on
     * @param frame  typing frame, used to flicker the screen slightly
     */
    private void drawLaptop(GraphicsContext gc, double centreX, double baseY,
                           boolean glowing, int frame) {
        double lidWidth = 12;
        double lidHeight = 8;
        double lidX = Math.round(centreX - lidWidth / 2);
        double lidY = baseY - lidHeight - 1;

        // Screen lid.
        gc.setFill(LAPTOP_DARK);
        gc.fillRect(lidX, lidY, lidWidth, lidHeight);
        gc.setFill(glowing ? SCREEN_ON.deriveColor(0, 1, 1, frame == 1 ? 0.95 : 0.75) : SCREEN_OFF);
        gc.fillRect(lidX + 1, lidY + 1, lidWidth - 2, lidHeight - 2);
        if (glowing) {
            // Suggestion of text on the screen.
            gc.setFill(Color.web("#0e1b26", 0.55));
            gc.fillRect(lidX + 2, lidY + 3 + (frame == 1 ? 1 : 0), lidWidth - 5, 1);
            gc.fillRect(lidX + 2, lidY + 5, lidWidth - 7, 1);
        }
        // Keyboard deck in front, slightly wider than the lid.
        gc.setFill(LAPTOP_BODY);
        gc.fillRect(lidX - 1, baseY - 1, lidWidth + 2, 2);
        gc.setFill(LAPTOP_DARK);
        gc.fillRect(lidX + 1, baseY - 1, lidWidth - 2, 1);
    }

    // ------------------------------------------------------------------- workers

    private void drawWorkers(GraphicsContext gc, List<AgentWorker> workers) {
        for (AgentWorker worker : workers) {
            double px = worker.x() * TILE;
            double py = worker.y() * TILE;
            Color[] palette = PALETTES[worker.paletteIndex() % PALETTES.length];

            gc.setFill(SHADOW);
            gc.fillOval(px - 4, py - 2, 9, 4);

            if (worker.isActive()) {
                gc.setStroke(Color.web("#8fdcff", 0.8));
                gc.setLineWidth(1);
                gc.strokeOval(px - 6, py - 4, 13, 7);
            }

            if (worker.isSeated()) {
                drawSeatedWorker(gc, worker, px, py, palette);
            } else {
                drawStandingWorker(gc, worker, px, py, palette);
            }
        }
    }

    private void drawStandingWorker(GraphicsContext gc, AgentWorker worker,
                                    double px, double py, Color[] palette) {
        boolean walking = worker.state() == AgentWorker.State.WALKING;
        int frame = walking ? (int) (worker.animationClock() * 7) % 4 : 0;
        double bob = walking && (frame == 1 || frame == 3) ? -1 : 0;
        double headY = py - 15 + bob;

        // Legs.
        gc.setFill(Color.web("#2b3345"));
        if (walking && frame == 1) {
            gc.fillRect(px - 4, headY + 12, 3, 4);
            gc.fillRect(px + 1, headY + 12, 3, 3);
        } else if (walking && frame == 3) {
            gc.fillRect(px - 4, headY + 12, 3, 3);
            gc.fillRect(px + 1, headY + 12, 3, 4);
        } else {
            gc.fillRect(px - 4, headY + 12, 3, 4);
            gc.fillRect(px + 1, headY + 12, 3, 4);
        }
        // Torso with a collar.
        gc.setFill(palette[0]);
        gc.fillRect(px - 4, headY + 6, 8, 7);
        gc.setFill(Color.rgb(255, 255, 255, 0.14));
        gc.fillRect(px - 1, headY + 6, 2, 3);
        // Arms.
        gc.setFill(palette[0]);
        double armSwing = walking && frame == 1 ? 1 : (walking && frame == 3 ? -1 : 0);
        gc.fillRect(px - 5, headY + 7 + armSwing, 1, 4);
        gc.fillRect(px + 4, headY + 7 - armSwing, 1, 4);
        gc.setFill(palette[2]);
        gc.fillRect(px - 5, headY + 11 + armSwing, 1, 1);
        gc.fillRect(px + 4, headY + 11 - armSwing, 1, 1);

        drawHead(gc, worker, px, headY, palette, true);
    }

    private void drawSeatedWorker(GraphicsContext gc, AgentWorker worker,
                                  double px, double py, Color[] palette) {
        boolean typing = worker.state() == AgentWorker.State.WORKING;
        int frame = typing ? (int) (worker.animationClock() * 9) % 2 : 0;
        if (typing) {
            // Lit screen on the desk above; drawn first so the worker sits in front of it.
            drawLaptop(gc, px, (worker.y() - 1) * TILE + 6, true, frame);
        }
        double bob = typing && frame == 1 ? 1 : 0;
        // Sits in front of the desk: the head clears the desk edge so the laptop above stays visible
        // instead of being drawn over the face.
        double headY = py - 9 + bob;

        // Office chair behind the worker.
        gc.setFill(CHAIR);
        gc.fillRect(px - 6, py - 3, 12, 10);
        gc.setFill(CHAIR_LIGHT);
        gc.fillRect(px - 5, py - 2, 10, 8);
        gc.setFill(METAL);
        gc.fillRect(px - 1, py + 7, 2, 2);

        // Torso.
        gc.setFill(palette[0]);
        gc.fillRect(px - 4, headY + 7, 8, 8);
        gc.setFill(Color.rgb(0, 0, 0, 0.16));
        gc.fillRect(px - 4, headY + 14, 8, 1);
        gc.setFill(Color.rgb(255, 255, 255, 0.14));
        gc.fillRect(px - 1, headY + 7, 2, 3);

        // Arms reaching to the keyboard; the alternating hand suggests typing.
        gc.setFill(palette[2]);
        if (typing && frame == 1) {
            gc.fillRect(px - 5, headY + 7, 1, 3);
            gc.fillRect(px + 4, headY + 8, 1, 3);
        } else {
            gc.fillRect(px - 5, headY + 8, 1, 3);
            gc.fillRect(px + 4, headY + 7, 1, 3);
        }

        drawHead(gc, worker, px, headY, palette, false);

        if (worker.state() == AgentWorker.State.WAITING) {
            // A raised hand plus a marker: this agent is waiting on the user.
            gc.setFill(palette[2]);
            gc.fillRect(px + 4, headY + 2, 1, 5);
            gc.setFill(Color.web("#f2c14e"));
            gc.fillRect(px - 1, headY - 9, 2, 5);
            gc.fillRect(px - 1, headY - 3, 2, 1);
        }
    }

    /**
     * Head with hair, eyes and mouth.
     *
     * <p>The variant is derived from the palette index so an agent always looks the same, and the
     * expression follows the state: focused while typing, a small smile when idle, wide eyes while
     * waiting on the user.
     */
    private void drawHead(GraphicsContext gc, AgentWorker worker, double px, double headY,
                          Color[] palette, boolean standing) {
        double headX = px - 3;
        int variant = worker.paletteIndex() % 4;
        boolean glasses = worker.paletteIndex() % 3 == 0;

        // Face.
        gc.setFill(palette[2]);
        gc.fillRect(headX, headY, 6, 6);
        gc.setFill(Color.rgb(0, 0, 0, 0.10));
        gc.fillRect(headX, headY + 5, 6, 1);

        // Hair: four styles, all drawn from the same two rows plus side pieces.
        gc.setFill(palette[1]);
        gc.fillRect(headX, headY - 2, 6, 3);
        switch (variant) {
            case 0 -> { // short, sideburns
                gc.fillRect(headX - 1, headY, 1, 2);
                gc.fillRect(headX + 6, headY, 1, 2);
            }
            case 1 -> { // long, past the shoulders
                gc.fillRect(headX - 1, headY, 1, 6);
                gc.fillRect(headX + 6, headY, 1, 6);
            }
            case 2 -> { // ponytail
                gc.fillRect(headX + 6, headY, 1, 2);
                gc.fillRect(headX + 7, headY + 1, 1, 4);
            }
            default -> { // bun
                gc.fillOval(headX + 1, headY - 5, 4, 4);
                gc.fillRect(headX - 1, headY, 1, 2);
            }
        }

        if (worker.isSeated() || standing) {
            boolean waiting = worker.state() == AgentWorker.State.WAITING;
            boolean working = worker.state() == AgentWorker.State.WORKING;

            gc.setFill(Color.web("#2b2833"));
            if (working) {
                // Focused: narrowed eyes read as concentration at this size.
                gc.fillRect(headX + 1, headY + 3, 2, 1);
                gc.fillRect(headX + 4, headY + 3, 1, 1);
            } else if (waiting) {
                gc.fillRect(headX + 1, headY + 2, 2, 2);
                gc.fillRect(headX + 4, headY + 2, 1, 2);
            } else {
                gc.fillRect(headX + 1, headY + 2, 1, 2);
                gc.fillRect(headX + 4, headY + 2, 1, 2);
            }

            // Mouth.
            if (waiting) {
                gc.fillRect(headX + 2, headY + 4, 2, 1); // small open mouth
            } else if (working) {
                gc.setFill(Color.rgb(0, 0, 0, 0.45));
                gc.fillRect(headX + 2, headY + 4, 2, 1);
            } else {
                gc.setFill(Color.rgb(0, 0, 0, 0.35));
                gc.fillRect(headX + 2, headY + 4, 3, 1);
                gc.fillRect(headX + 1, headY + 3, 1, 1);
            }

            if (glasses) {
                gc.setFill(Color.web("#dfe7ef", 0.85));
                gc.fillRect(headX + 1, headY + 2, 2, 2);
                gc.fillRect(headX + 4, headY + 2, 2, 2);
                gc.setFill(Color.web("#2b2833"));
                gc.fillRect(headX + 3, headY + 2, 1, 1);
            }
        }
    }

    // -------------------------------------------------------------------- labels

    private void drawLabels(GraphicsContext gc, List<AgentWorker> workers,
                            double offsetX, double offsetY, int scale) {
        Font nameFont = Font.font("D2Coding", FontWeight.NORMAL, Math.max(10, 3.6 * scale));
        Font activityFont = Font.font("D2Coding", FontWeight.BOLD, Math.max(10, 3.6 * scale));
        gc.setTextAlign(TextAlignment.CENTER);

        for (AgentWorker worker : workers) {
            // Only the thread in focus and whatever the pointer is over get a label; all of them at
            // once overlapped badly once more than a few desks were taken.
            if (!worker.isActive() && !worker.isHovered()) {
                continue;
            }
            String subtitle = worker.isHovered() ? worker.title() : "";
            double screenX = offsetX + worker.x() * TILE * scale;
            double headTop = offsetY + (worker.y() * TILE - (worker.isSeated() ? 15 : 17)) * scale;

            gc.setFont(nameFont);
            String name = worker.name();
            double textWidth = name.length() * nameFont.getSize() * 0.62;
            gc.setFill(Color.rgb(0, 0, 0, 0.62));
            gc.fillRoundRect(screenX - textWidth / 2 - 4, headTop - nameFont.getSize() - 5,
                    textWidth + 8, nameFont.getSize() + 6, 4, 4);
            gc.setFill(worker.isActive() ? Color.web("#8fdcff") : Color.web("#d5d5da"));
            gc.fillText(name, screenX, headTop - 4);

            if (!worker.activity().isBlank()) {
                gc.setFont(activityFont);
                String activity = worker.activity();
                double bubbleWidth = activity.length() * activityFont.getSize() * 0.68 + 12;
                double bubbleY = headTop - nameFont.getSize() - activityFont.getSize() - 14;
                gc.setFill(Color.web("#4f7dfb", 0.94));
                gc.fillRoundRect(screenX - bubbleWidth / 2, bubbleY,
                        bubbleWidth, activityFont.getSize() + 7, 5, 5);
                gc.setFill(Color.WHITE);
                gc.fillText(activity, screenX, bubbleY + activityFont.getSize() + 2);
            }

            if (!subtitle.isBlank()) {
                gc.setFont(nameFont);
                double titleWidth = subtitle.length() * nameFont.getSize() * 0.62;
                double titleY = offsetY + (worker.y() * TILE + 5) * scale;
                gc.setFill(Color.rgb(0, 0, 0, 0.72));
                gc.fillRoundRect(screenX - titleWidth / 2 - 4, titleY,
                        titleWidth + 8, nameFont.getSize() + 6, 4, 4);
                gc.setFill(Color.web("#c9c9d0"));
                gc.fillText(subtitle, screenX, titleY + nameFont.getSize() + 1);
            }
        }
    }
}
