package com.codexdesktop.ui.office;

import java.util.ArrayList;
import java.util.List;

/**
 * The office floor plan.
 *
 * <p>Kept as a character map so the layout is readable and editable without touching the renderer.
 * Desks and meeting seats are discovered from the map, which means rearranging the room is a
 * one-line change.
 *
 * <pre>
 *   #  wall          .  floor         =  desk          o  desk seat
 *   M  meeting table m  meeting seat  ~  rug           T  tall plant
 *   B  bookshelf     C  cabinet       K  water cooler  D  whiteboard
 *   W  window        L  wall clock    P  wall picture
 * </pre>
 */
public final class OfficeMap {

    /**
     * 16x14 tiles: two banks of desks, a meeting table in the middle, and a lounge along the
     * bottom. Sized so the side panel renders it at 2x and the detached window at 3x.
     */
    private static final String[] LAYOUT = {
            "################",
            "#WW.L.WW..P.WW.#",
            "#..............#",
            "#.====....====.#",
            "#.oooo....oooo.#",
            "#..............#",
            "#..D........K..#",
            "#..MMMMMMMMMM..#",
            "#..mm.mm.mm.mm.#",
            "#..............#",
            "#.====....====.#",
            "#.oooo....oooo.#",
            "#.~~~~.T..B..C.#",
            "################",
    };

    /** A desk plus the seat tile the worker occupies. */
    public record Desk(int deskX, int deskY, double seatX, double seatY) { }

    /** A chair at the meeting table; workers here face the table instead of a screen. */
    public record MeetingSeat(double x, double y) { }

    private final List<Desk> desks = new ArrayList<>();
    private final List<MeetingSeat> meetingSeats = new ArrayList<>();
    private List<double[]> wanderSpots;

    public OfficeMap() {
        // A desk is a run of '=' tiles; each run holds two workstations, seats on the row below.
        for (int row = 0; row < LAYOUT.length; row++) {
            int column = 0;
            while (column < LAYOUT[row].length()) {
                if (LAYOUT[row].charAt(column) == '=') {
                    int start = column;
                    while (column < LAYOUT[row].length() && LAYOUT[row].charAt(column) == '=') {
                        column++;
                    }
                    int width = column - start;
                    for (int half = 0; half < 2; half++) {
                        double seatX = start + width * (half == 0 ? 0.25 : 0.75);
                        desks.add(new Desk(start, row, seatX, row + 1.0));
                    }
                } else {
                    column++;
                }
            }
        }
        // Meeting chairs come in pairs of 'm'; one worker sits at the centre of each pair.
        for (int row = 0; row < LAYOUT.length; row++) {
            int column = 0;
            while (column < LAYOUT[row].length()) {
                if (LAYOUT[row].charAt(column) == 'm') {
                    int start = column;
                    while (column < LAYOUT[row].length() && LAYOUT[row].charAt(column) == 'm') {
                        column++;
                    }
                    meetingSeats.add(new MeetingSeat(start + (column - start) / 2.0, row + 0.95));
                } else {
                    column++;
                }
            }
        }
    }

    public int width() {
        return LAYOUT[0].length();
    }

    public int height() {
        return LAYOUT.length;
    }

    public char tile(int x, int y) {
        if (y < 0 || y >= LAYOUT.length) {
            return '#';
        }
        String row = LAYOUT[y];
        if (x < 0 || x >= row.length()) {
            return '#';
        }
        return row.charAt(x);
    }

    public List<Desk> desks() {
        return List.copyOf(desks);
    }

    public List<MeetingSeat> meetingSeats() {
        return List.copyOf(meetingSeats);
    }

    /** Total seated capacity: desks first, then the meeting table. */
    public int capacity() {
        return desks.size() + meetingSeats.size();
    }

    /** Where workers stand when they have nothing to do: the rug in the lower left. */
    public double loungeX(int index) {
        return 2.5 + (index % 4) * 1.1;
    }

    public double loungeY(int index) {
        return 12.9 + (index / 4) * 0.6;
    }

    /**
     * Tiles an idle agent may wander to: open floor and the lounge rug, never furniture, seats or
     * the meeting table. Computed once because the layout is static.
     */
    public List<double[]> wanderSpots() {
        if (wanderSpots == null) {
            List<double[]> spots = new ArrayList<>();
            for (int y = 0; y < height(); y++) {
                for (int x = 0; x < width(); x++) {
                    char tile = tile(x, y);
                    if (tile != '.' && tile != '~') {
                        continue;
                    }
                    // Skip the row directly under a desk: that is a seat lane, not a corridor.
                    if (tile(x, y - 1) == '=') {
                        continue;
                    }
                    spots.add(new double[]{x + 0.5, y + 0.9});
                }
            }
            wanderSpots = List.copyOf(spots);
        }
        return wanderSpots;
    }
}
