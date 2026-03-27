package com.colorlink.engine;

import java.util.*;

/**
 * Solution-first Colorlink puzzle generator.
 *
 * Strategy
 * --------
 * 1. Seed: place one starting cell per color with distance spacing.
 * 2. Grow: backtracking search that extends the most-constrained open path-end
 *    one cell at a time until every cell is covered.
 * 3. Extract: record the two ends of each path as puzzle endpoints.
 * 4. Validate: structural check + quality filter.
 *
 * Pruning (applied before each recursive call)
 * -----
 * - Reachability: BFS from all open-end frontier cells; every empty cell must
 *   be reachable. Cuts off sealed regions early.
 * - Isolation: a newly placed cell must not create a completely isolated empty
 *   cell (no empty neighbor AND no adjacent open path-end).
 * - Short-path: if a color's both ends become sealed and its path length is
 *   below minPathLength, reject immediately.
 */
public class SolvedBoardGenerator {

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = { 0, 0,-1, 1};

    private final GeneratorConfig cfg;
    private final Random rng;
    private final int rows, cols, N, total;

    // Per-attempt mutable state (reset on each attempt)
    private int[][] grid;                        // 0=empty, 1..N=colorId
    @SuppressWarnings("unchecked")
    private ArrayDeque<int[]>[] paths = new ArrayDeque[0];
    private int filled;

    // -----------------------------------------------------------------------

    public SolvedBoardGenerator(GeneratorConfig cfg) {
        this.cfg   = cfg;
        this.rng   = new Random(cfg.randomSeed);
        this.rows  = cfg.rows;
        this.cols  = cfg.cols;
        this.N     = cfg.colorCount;
        this.total = rows * cols;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public GenerationResult generate() {
        for (int attempt = 1; attempt <= cfg.maxAttempts; attempt++) {
            // Reset per-attempt state
            grid  = new int[rows][cols];
            paths = new ArrayDeque[N];
            for (int c = 0; c < N; c++) paths[c] = new ArrayDeque<>();
            filled = 0;

            if (placeSeeds() && solve()) {
                List<ColorPath> colorPaths = extractColorPaths();
                if (QualityFilter.passes(colorPaths, cfg)) {
                    Board solution = buildSolutionBoard(colorPaths);
                    Board puzzle   = PuzzleExtractor.extract(solution, N);
                    return new GenerationResult(puzzle, solution, colorPaths, attempt, cfg.randomSeed);
                }
            }
        }
        return null; // all attempts exhausted
    }

    // -----------------------------------------------------------------------
    // Step 1 – Seed Placement
    // -----------------------------------------------------------------------

    private boolean placeSeeds() {
        // Min distance scales with √(cells per color) so seeds spread evenly
        int minDist = Math.max(2, (int)(Math.sqrt((double) total / N) * 0.85));

        List<int[]> placed = new ArrayList<>();
        for (int c = 0; c < N; c++) {
            int[] cell = trySeed(placed, minDist);
            if (cell == null) cell = trySeed(placed, 2);
            if (cell == null) cell = trySeed(placed, 1);
            if (cell == null) return false;

            grid[cell[0]][cell[1]] = c + 1; // colorId is 1-based
            paths[c].addLast(cell);
            filled++;
            placed.add(cell);
        }
        return true;
    }

    private int[] trySeed(List<int[]> placed, int minDist) {
        for (int t = 0; t < 300; t++) {
            int r = rng.nextInt(rows), c = rng.nextInt(cols);
            if (grid[r][c] != 0) continue;
            boolean ok = true;
            for (int[] p : placed) {
                if (Math.abs(p[0]-r) + Math.abs(p[1]-c) < minDist) { ok = false; break; }
            }
            if (ok) return new int[]{r, c};
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Step 2 – Backtracking Solver
    // -----------------------------------------------------------------------

    private boolean solve() {
        if (filled == total) return validateFinal();

        List<int[]> openEnds = computeOpenEnds(); // [colorIdx, 0=head|1=tail]

        // Pruning: any color that is now sealed with insufficient path length?
        for (int c = 0; c < N; c++) {
            if (paths[c].size() < cfg.minPathLength) {
                boolean isOpen = false;
                for (int[] oe : openEnds) { if (oe[0] == c) { isOpen = true; break; } }
                if (!isOpen) return false;
            }
        }

        if (openEnds.isEmpty()) return false; // stuck, board not full

        // Pruning: all empty cells must still be reachable from some open end
        if (!reachabilityOk(openEnds)) return false;

        // Pick the most constrained open end (fewest empty neighbors)
        int[] chosen   = mostConstrained(openEnds);
        int   color    = chosen[0];
        int   end      = chosen[1]; // 0=head, 1=tail
        int[] endCell  = (end == 0) ? paths[color].peekFirst() : paths[color].peekLast();

        List<int[]> candidates = emptyNeighbors(endCell[0], endCell[1]);
        // (If chosen end has no empties, collectOpenEnds() would not have included it)

        // Sort: shuffle first for randomness, then stable-sort by local constraint
        Collections.shuffle(candidates, rng);
        candidates.sort(Comparator.comparingInt((int[] c2) -> countEmpty(c2[0], c2[1])));

        for (int[] cand : candidates) {
            extend(color, end, cand);

            // Light pruning: isolation check on newly exposed neighbours
            if (isolationOk(cand[0], cand[1]) && solve()) return true;

            retract(color, end, cand);
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Open-End Computation
    // -----------------------------------------------------------------------

    /**
     * Returns [colorIdx, end] pairs where end=0 means head, end=1 means tail,
     * and that end cell has at least one empty neighbour.
     * For a path of length 1 (seed only, head==tail), only tail is returned.
     */
    private List<int[]> computeOpenEnds() {
        List<int[]> result = new ArrayList<>();
        for (int c = 0; c < N; c++) {
            ArrayDeque<int[]> path = paths[c];
            if (path.isEmpty()) continue;
            if (path.size() == 1) {
                if (hasEmptyNeighbor(path.peekFirst())) result.add(new int[]{c, 1});
            } else {
                if (hasEmptyNeighbor(path.peekFirst())) result.add(new int[]{c, 0});
                if (hasEmptyNeighbor(path.peekLast()))  result.add(new int[]{c, 1});
            }
        }
        return result;
    }

    private int[] mostConstrained(List<int[]> openEnds) {
        int min = Integer.MAX_VALUE;
        int[] best = openEnds.get(0);
        for (int[] oe : openEnds) {
            int[] cell = (oe[1] == 0) ? paths[oe[0]].peekFirst() : paths[oe[0]].peekLast();
            int cnt = countEmpty(cell[0], cell[1]);
            if (cnt < min) { min = cnt; best = oe; }
        }
        return best;
    }

    // -----------------------------------------------------------------------
    // Pruning Checks
    // -----------------------------------------------------------------------

    /**
     * BFS from every empty cell adjacent to an open end.
     * All empty cells must be reachable; otherwise some region is sealed off.
     */
    private boolean reachabilityOk(List<int[]> openEnds) {
        boolean[][] vis = new boolean[rows][cols];
        Queue<int[]> q  = new ArrayDeque<>();

        for (int[] oe : openEnds) {
            int[] cell = (oe[1] == 0) ? paths[oe[0]].peekFirst() : paths[oe[0]].peekLast();
            for (int d = 0; d < 4; d++) {
                int nr = cell[0]+DR[d], nc = cell[1]+DC[d];
                if (inBounds(nr,nc) && grid[nr][nc] == 0 && !vis[nr][nc]) {
                    vis[nr][nc] = true; q.add(new int[]{nr, nc});
                }
            }
        }

        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int d = 0; d < 4; d++) {
                int nr = cur[0]+DR[d], nc = cur[1]+DC[d];
                if (inBounds(nr,nc) && grid[nr][nc] == 0 && !vis[nr][nc]) {
                    vis[nr][nc] = true; q.add(new int[]{nr, nc});
                }
            }
        }

        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (grid[r][c] == 0 && !vis[r][c]) return false;
        return true;
    }

    /**
     * After placing cell (pr,pc), check its empty neighbours.
     * Returns false if any neighbour is now completely isolated
     * (no empty neighbours of its own, and no adjacent open path-end).
     */
    private boolean isolationOk(int pr, int pc) {
        for (int d = 0; d < 4; d++) {
            int nr = pr+DR[d], nc = pc+DC[d];
            if (inBounds(nr,nc) && grid[nr][nc] == 0 && isIsolated(nr, nc)) return false;
        }
        return true;
    }

    private boolean isIsolated(int r, int c) {
        // Has any empty neighbour?
        for (int d = 0; d < 4; d++) {
            int nr = r+DR[d], nc = c+DC[d];
            if (inBounds(nr,nc) && grid[nr][nc] == 0) return false;
        }
        // Has an adjacent open path-end?
        for (int col = 0; col < N; col++) {
            if (paths[col].isEmpty()) continue;
            if (isAdj(paths[col].peekFirst(), r, c)) return false;
            if (paths[col].size() > 1 && isAdj(paths[col].peekLast(), r, c)) return false;
        }
        return true; // isolated: cannot be filled
    }

    // -----------------------------------------------------------------------
    // Move Application
    // -----------------------------------------------------------------------

    private void extend(int color, int end, int[] cell) {
        if (end == 0) paths[color].addFirst(cell);
        else          paths[color].addLast(cell);
        grid[cell[0]][cell[1]] = color + 1;
        filled++;
    }

    private void retract(int color, int end, int[] cell) {
        if (end == 0) paths[color].pollFirst();
        else          paths[color].pollLast();
        grid[cell[0]][cell[1]] = 0;
        filled--;
    }

    // -----------------------------------------------------------------------
    // Final Validation
    // -----------------------------------------------------------------------

    private boolean validateFinal() {
        // Every cell must be filled
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (grid[r][c] == 0) return false;

        // Every path must meet minimum length
        for (int c = 0; c < N; c++)
            if (paths[c].size() < cfg.minPathLength) return false;

        // Degree check: endpoints must have degree 1, internals degree 2
        for (int c = 0; c < N; c++) {
            List<int[]> arr = new ArrayList<>(paths[c]);
            for (int i = 0; i < arr.size(); i++) {
                int[] cell = arr.get(i);
                int deg = colorDegree(cell[0], cell[1], c + 1);
                boolean isEndpoint = (i == 0 || i == arr.size()-1);
                int expected = isEndpoint ? 1 : 2;
                if (deg != expected) return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Result Assembly
    // -----------------------------------------------------------------------

    private List<ColorPath> extractColorPaths() {
        List<ColorPath> result = new ArrayList<>();
        for (int c = 0; c < N; c++) {
            List<Cell> cells = new ArrayList<>();
            for (int[] a : paths[c]) cells.add(new Cell(a[0], a[1]));
            result.add(new ColorPath(c + 1, cells));
        }
        return result;
    }

    private Board buildSolutionBoard(List<ColorPath> colorPaths) {
        Board b = new Board(rows, cols, N);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                b.setColor(r, c, grid[r][c]);
        for (ColorPath cp : colorPaths) {
            b.setEndpoint(cp.getEndpoint1().row, cp.getEndpoint1().col, true);
            b.setEndpoint(cp.getEndpoint2().row, cp.getEndpoint2().col, true);
        }
        return b;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private boolean inBounds(int r, int c) { return r >= 0 && r < rows && c >= 0 && c < cols; }

    private boolean hasEmptyNeighbor(int[] cell) {
        for (int d = 0; d < 4; d++) {
            int nr = cell[0]+DR[d], nc = cell[1]+DC[d];
            if (inBounds(nr,nc) && grid[nr][nc] == 0) return true;
        }
        return false;
    }

    private int countEmpty(int r, int c) {
        int cnt = 0;
        for (int d = 0; d < 4; d++) {
            int nr = r+DR[d], nc = c+DC[d];
            if (inBounds(nr,nc) && grid[nr][nc] == 0) cnt++;
        }
        return cnt;
    }

    private List<int[]> emptyNeighbors(int r, int c) {
        List<int[]> res = new ArrayList<>(4);
        for (int d = 0; d < 4; d++) {
            int nr = r+DR[d], nc = c+DC[d];
            if (inBounds(nr,nc) && grid[nr][nc] == 0) res.add(new int[]{nr, nc});
        }
        return res;
    }

    private boolean isAdj(int[] cell, int r, int c) {
        return Math.abs(cell[0]-r) + Math.abs(cell[1]-c) == 1;
    }

    /** Count same-color orthogonal neighbours. */
    private int colorDegree(int r, int c, int colorId) {
        int deg = 0;
        for (int d = 0; d < 4; d++) {
            int nr = r+DR[d], nc = c+DC[d];
            if (inBounds(nr,nc) && grid[nr][nc] == colorId) deg++;
        }
        return deg;
    }
}
