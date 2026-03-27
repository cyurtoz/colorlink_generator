package com.colorlink.engine;

import java.util.Objects;

/**
 * Immutable grid coordinate.
 */
public final class Cell {

    public final int row;
    public final int col;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int manhattanDistance(Cell other) {
        return Math.abs(row - other.row) + Math.abs(col - other.col);
    }

    public boolean isAdjacentTo(Cell other) {
        return manhattanDistance(other) == 1;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Cell)) return false;
        Cell c = (Cell) o;
        return row == c.row && col == c.col;
    }

    @Override public int hashCode() { return Objects.hash(row, col); }

    @Override public String toString() { return "(" + row + "," + col + ")"; }
}
