package com.colorlink.engine;

import java.util.*;

/**
 * Rectangular grid.  color values: 0 = empty, 1..colorCount = color id.
 * Also tracks which cells are endpoints (puzzle clues).
 */
public class Board {

    public final int rows;
    public final int cols;
    public final int colorCount;

    private final int[][]     grid;      // color id per cell, 0 = empty
    private final boolean[][] endpoint;  // true = this cell is a puzzle endpoint

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = { 0, 0,-1, 1};

    public Board(int rows, int cols, int colorCount) {
        this.rows       = rows;
        this.cols       = cols;
        this.colorCount = colorCount;
        this.grid       = new int[rows][cols];
        this.endpoint   = new boolean[rows][cols];
    }

    // -----------------------------------------------------------------------
    // Cell accessors
    // -----------------------------------------------------------------------

    public int  getColor(int r, int c)              { return grid[r][c]; }
    public void setColor(int r, int c, int color)   { grid[r][c] = color; }

    public boolean isEndpoint(int r, int c)              { return endpoint[r][c]; }
    public void    setEndpoint(int r, int c, boolean v)  { endpoint[r][c] = v; }

    public boolean isEmpty(int r, int c)  { return grid[r][c] == 0; }
    public boolean inBounds(int r, int c) { return r >= 0 && r < rows && c >= 0 && c < cols; }
    public boolean isFull() {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (grid[r][c] == 0) return false;
        return true;
    }

    // -----------------------------------------------------------------------
    // Structural queries
    // -----------------------------------------------------------------------

    /** 4-connected neighbours (within bounds). */
    public List<Cell> neighbors(int r, int c) {
        List<Cell> result = new ArrayList<>(4);
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            if (inBounds(nr, nc)) result.add(new Cell(nr, nc));
        }
        return result;
    }

    /**
     * Number of same-color neighbors for a filled cell.
     * This equals the degree of the cell in the color-path graph.
     */
    public int colorDegree(int r, int c) {
        int color = grid[r][c];
        if (color == 0) return 0;
        int deg = 0;
        for (Cell nb : neighbors(r, c))
            if (grid[nb.row][nb.col] == color) deg++;
        return deg;
    }

    /**
     * Connected components of empty cells.  Returns a list of components,
     * each component being a list of (row*cols+col) packed cell keys.
     */
    public List<List<int[]>> emptyComponents() {
        boolean[][] vis = new boolean[rows][cols];
        List<List<int[]>> result = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == 0 && !vis[r][c]) {
                    List<int[]> comp = new ArrayList<>();
                    bfsEmpty(r, c, vis, comp);
                    result.add(comp);
                }
            }
        }
        return result;
    }

    private void bfsEmpty(int sr, int sc, boolean[][] vis, List<int[]> comp) {
        Queue<int[]> q = new ArrayDeque<>();
        vis[sr][sc] = true;
        q.add(new int[]{sr, sc});
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            comp.add(cur);
            for (int d = 0; d < 4; d++) {
                int nr = cur[0]+DR[d], nc = cur[1]+DC[d];
                if (inBounds(nr,nc) && grid[nr][nc]==0 && !vis[nr][nc]) {
                    vis[nr][nc] = true; q.add(new int[]{nr,nc});
                }
            }
        }
    }

    /** Deep copy. */
    public Board copy() {
        Board b = new Board(rows, cols, colorCount);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                b.grid[r][c]    = grid[r][c];
                b.endpoint[r][c] = endpoint[r][c];
            }
        return b;
    }
}
