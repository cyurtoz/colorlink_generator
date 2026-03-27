package com.colorlink.engine;

import java.util.*;

/**
 * Represents one fully-solved color path: an ordered list of cells
 * from endpoint1 (index 0) to endpoint2 (index size-1).
 */
public class ColorPath {

    /** 1-based color id matching Board color values. */
    public final int colorId;

    private final List<Cell> cells; // immutable ordered sequence

    public ColorPath(int colorId, List<Cell> cells) {
        this.colorId = colorId;
        this.cells   = Collections.unmodifiableList(new ArrayList<>(cells));
    }

    public Cell getEndpoint1() { return cells.get(0); }
    public Cell getEndpoint2() { return cells.get(cells.size() - 1); }

    public int getLength() { return cells.size(); }

    public List<Cell> getCells() { return cells; }

    /** Number of direction changes (turns) along the path. */
    public int countTurns() {
        int turns = 0;
        for (int i = 1; i < cells.size() - 1; i++) {
            Cell prev = cells.get(i - 1);
            Cell curr = cells.get(i);
            Cell next = cells.get(i + 1);
            int dr1 = curr.row - prev.row, dc1 = curr.col - prev.col;
            int dr2 = next.row - curr.row, dc2 = next.col - curr.col;
            if (dr1 != dr2 || dc1 != dc2) turns++;
        }
        return turns;
    }

    /** Manhattan distance between the two endpoints. */
    public int endpointSpread() {
        return getEndpoint1().manhattanDistance(getEndpoint2());
    }

    @Override public String toString() {
        return String.format("ColorPath[id=%d, len=%d, turns=%d, ep1=%s, ep2=%s]",
            colorId, getLength(), countTurns(), getEndpoint1(), getEndpoint2());
    }
}
