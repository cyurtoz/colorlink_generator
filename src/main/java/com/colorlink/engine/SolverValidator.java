package com.colorlink.engine;

import java.util.*;

/**
 * Validates that a proposed "solved" board satisfies all Colorlink rules:
 *
 *   1. Every cell is filled (no empty cells).
 *   2. Each color forms exactly one connected component.
 *   3. Each color has exactly two endpoints (degree-1 cells).
 *   4. Every non-endpoint cell has degree 2 within its color.
 *   5. The puzzle board derived from the solution has exactly 2 clues per color.
 *   6. Solution endpoints match puzzle clues.
 */
public class SolverValidator {

    private SolverValidator() {}

    // -----------------------------------------------------------------------

    public static ValidationReport validate(Board solution, Board puzzle) {
        ValidationReport report = new ValidationReport();

        checkFullCoverage(solution, report);
        checkColorComponents(solution, report);
        checkDegrees(solution, report);
        checkPuzzleConsistency(solution, puzzle, report);

        return report;
    }

    // -----------------------------------------------------------------------
    // Individual checks
    // -----------------------------------------------------------------------

    private static void checkFullCoverage(Board b, ValidationReport r) {
        for (int row = 0; row < b.rows; row++)
            for (int col = 0; col < b.cols; col++)
                if (b.isEmpty(row, col)) {
                    r.addError("Empty cell at (" + row + "," + col + ")");
                    return;
                }
    }

    private static void checkColorComponents(Board b, ValidationReport r) {
        for (int colorId = 1; colorId <= b.colorCount; colorId++) {
            List<int[]> cells = cellsOfColor(b, colorId);
            if (cells.isEmpty()) {
                r.addError("Color " + colorId + " has no cells");
                continue;
            }
            // BFS from first cell – all must be reachable within same color
            int reachable = bfsColor(b, cells.get(0), colorId);
            if (reachable != cells.size())
                r.addError("Color " + colorId + " is disconnected (" +
                           reachable + " reachable of " + cells.size() + ")");
        }
    }

    private static void checkDegrees(Board b, ValidationReport r) {
        for (int row = 0; row < b.rows; row++) {
            for (int col = 0; col < b.cols; col++) {
                int colorId = b.getColor(row, col);
                if (colorId == 0) continue;
                int deg = b.colorDegree(row, col);
                boolean isEndpoint = b.isEndpoint(row, col);
                if (isEndpoint && deg != 1)
                    r.addError("Endpoint (" + row + "," + col + ") color=" + colorId + " has degree " + deg + " (expected 1)");
                else if (!isEndpoint && deg != 2)
                    r.addError("Internal (" + row + "," + col + ") color=" + colorId + " has degree " + deg + " (expected 2)");
            }
        }
    }

    private static void checkPuzzleConsistency(Board solution, Board puzzle, ValidationReport r) {
        // Count endpoints per color in puzzle
        int[] epCount = new int[solution.colorCount + 1];
        for (int row = 0; row < puzzle.rows; row++)
            for (int col = 0; col < puzzle.cols; col++)
                if (puzzle.isEndpoint(row, col)) {
                    int cid = puzzle.getColor(row, col);
                    if (cid < 1 || cid > solution.colorCount) {
                        r.addError("Puzzle clue at (" + row + "," + col + ") has invalid colorId=" + cid);
                    } else {
                        epCount[cid]++;
                        // Must match solution color
                        if (solution.getColor(row, col) != cid)
                            r.addError("Puzzle clue at (" + row + "," + col + ") mismatches solution");
                    }
                }

        for (int cid = 1; cid <= solution.colorCount; cid++) {
            if (epCount[cid] != 2)
                r.addError("Color " + cid + " has " + epCount[cid] + " puzzle endpoints (expected 2)");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static List<int[]> cellsOfColor(Board b, int colorId) {
        List<int[]> result = new ArrayList<>();
        for (int r = 0; r < b.rows; r++)
            for (int c = 0; c < b.cols; c++)
                if (b.getColor(r, c) == colorId) result.add(new int[]{r, c});
        return result;
    }

    private static int bfsColor(Board b, int[] start, int colorId) {
        boolean[][] vis = new boolean[b.rows][b.cols];
        Queue<int[]> q = new ArrayDeque<>();
        vis[start[0]][start[1]] = true; q.add(start);
        int count = 0;
        int[] DR = {-1,1,0,0}, DC = {0,0,-1,1};
        while (!q.isEmpty()) {
            int[] cur = q.poll(); count++;
            for (int d = 0; d < 4; d++) {
                int nr = cur[0]+DR[d], nc = cur[1]+DC[d];
                if (b.inBounds(nr,nc) && !vis[nr][nc] && b.getColor(nr,nc) == colorId) {
                    vis[nr][nc] = true; q.add(new int[]{nr,nc});
                }
            }
        }
        return count;
    }

    // -----------------------------------------------------------------------
    // Validation Report
    // -----------------------------------------------------------------------

    public static class ValidationReport {
        private final List<String> errors = new ArrayList<>();

        public void addError(String msg) { errors.add(msg); }

        public boolean isValid() { return errors.isEmpty(); }

        public List<String> getErrors() { return Collections.unmodifiableList(errors); }

        @Override public String toString() {
            if (isValid()) return "VALID";
            return "INVALID: " + errors;
        }
    }
}
