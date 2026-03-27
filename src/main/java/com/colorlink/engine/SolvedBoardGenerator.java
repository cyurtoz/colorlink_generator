package com.colorlink.engine;

import java.util.*;

/**
 * Solution-first Colorlink puzzle generator.
 *
 * Uses backtracking with aggressive pruning:
 * 1. Forced moves: single-option path ends are extended immediately.
 * 2. Most-constrained-first: always expand the path end with fewest legal moves.
 * 3. Trapped region detection: BFS checks that all empty regions are reachable.
 * 4. Trapped color detection: each color's endpoints must still be connectable.
 * 5. Tiny pocket rejection: 1-cell dead holes are caught immediately.
 * 6. Dead-state memoization: previously failed board states are skipped.
 * 7. Weighted random: top-scoring moves preferred, with randomness for variety.
 */
public class SolvedBoardGenerator {

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = { 0, 0,-1, 1};

    private final GeneratorConfig cfg;
    private final Random rng;
    private final int rows, cols, N, total;

    // Per-attempt mutable state
    private int[][] grid;
    @SuppressWarnings("unchecked")
    private ArrayDeque<int[]>[] paths = new ArrayDeque[0];
    private int filled;
    private int callsRemaining;
    private Set<Long> deadStates;

    // Budget per attempt (scaled by grid size)
    private int budgetForSize() {
        if (rows <= 6 && cols <= 6)  return 20_000;
        if (rows <= 7 && cols <= 7)  return 100_000;
        if (rows <= 8 && cols <= 8)  return 300_000;
        if (rows <= 9 && cols <= 9)  return 600_000;
        return 1_000_000;
    }

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
        int budget = budgetForSize();

        for (int attempt = 1; attempt <= cfg.maxAttempts; attempt++) {
            grid  = new int[rows][cols];
            paths = new ArrayDeque[N];
            for (int c = 0; c < N; c++) paths[c] = new ArrayDeque<>();
            filled = 0;
            callsRemaining = budget;
            deadStates = new HashSet<>();

            if (!placeSeeds()) continue;
            if (!solve()) continue;
            if (!validateFinal()) continue;

            List<ColorPath> colorPaths = extractColorPaths();
            if (!QualityFilter.passes(colorPaths, cfg)) continue;

            Board solution = buildSolutionBoard(colorPaths);
            Board puzzle   = PuzzleExtractor.extract(solution, N);
            return new GenerationResult(puzzle, solution, colorPaths, attempt, cfg.randomSeed);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Seed Placement
    // -----------------------------------------------------------------------

    private boolean placeSeeds() {
        int minDist = Math.max(2, (int)(Math.sqrt((double) total / N) * 0.85));

        List<int[]> placed = new ArrayList<>();
        for (int c = 0; c < N; c++) {
            int[] cell = trySeed(placed, minDist);
            if (cell == null) cell = trySeed(placed, 2);
            if (cell == null) cell = trySeed(placed, 1);
            if (cell == null) return false;

            grid[cell[0]][cell[1]] = c + 1;
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
    // Backtracking Solver
    // -----------------------------------------------------------------------

    private boolean solve() {
        if (--callsRemaining <= 0) return false;
        if (filled == total) return true;

        // Dead state check
        long stateHash = computeStateHash();
        if (deadStates.contains(stateHash)) return false;

        // Collect all open ends with their valid moves
        List<int[]> openEnds = new ArrayList<>();   // [color, end, moveCount]
        for (int c = 0; c < N; c++) {
            if (paths[c].isEmpty()) continue;
            if (paths[c].size() == 1) {
                int cnt = countValidMoves(c, 1);
                if (cnt > 0) openEnds.add(new int[]{c, 1, cnt});
            } else {
                int cntH = countValidMoves(c, 0);
                int cntT = countValidMoves(c, 1);
                if (cntH > 0) openEnds.add(new int[]{c, 0, cntH});
                if (cntT > 0) openEnds.add(new int[]{c, 1, cntT});
            }
        }

        // Check sealed colors with insufficient path length
        for (int c = 0; c < N; c++) {
            if (paths[c].size() < cfg.minPathLength) {
                boolean isOpen = false;
                for (int[] oe : openEnds) { if (oe[0] == c) { isOpen = true; break; } }
                if (!isOpen) { deadStates.add(stateHash); return false; }
            }
        }

        if (openEnds.isEmpty()) { deadStates.add(stateHash); return false; }

        // FORCED MOVES: if any open end has exactly 1 valid move, do it immediately
        for (int[] oe : openEnds) {
            if (oe[2] == 1) {
                int[] endCell = getEndCell(oe[0], oe[1]);
                List<int[]> moves = getValidMoves(oe[0], endCell);
                if (moves.size() == 1) {
                    extend(oe[0], oe[1], moves.get(0));
                    // Quick check: did we create a tiny pocket?
                    if (!tinyPocketOk(moves.get(0)[0], moves.get(0)[1])) {
                        retract(oe[0], oe[1], moves.get(0));
                        deadStates.add(stateHash);
                        return false;
                    }
                    boolean result = solve();
                    if (result) return true;
                    retract(oe[0], oe[1], moves.get(0));
                    deadStates.add(stateHash);
                    return false;
                }
            }
        }

        // Periodic expensive checks
        int emptyCells = total - filled;
        if (emptyCells > 0 && (emptyCells % 5 == 0 || emptyCells <= N * 2)) {
            if (!reachabilityOk(openEnds)) { deadStates.add(stateHash); return false; }
        }

        // MOST CONSTRAINED FIRST: pick the open end with fewest valid moves
        int[] chosen = openEnds.get(0);
        for (int[] oe : openEnds) {
            if (oe[2] < chosen[2] || (oe[2] == chosen[2] && paths[oe[0]].size() < paths[chosen[0]].size())) {
                chosen = oe;
            }
        }

        int color = chosen[0], end = chosen[1];
        int[] endCell = getEndCell(color, end);
        List<int[]> moves = getValidMoves(color, endCell);

        // WEIGHTED RANDOM: score moves and prefer better ones
        int[] scores = new int[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            int[] m = moves.get(i);
            int emptyAfter = countEmptyExcluding(m[0], m[1]);
            scores[i] = emptyAfter * 10;
            // Bonus: doesn't create tiny pocket
            if (tinyPocketOkFast(m[0], m[1])) scores[i] += 50;
            // Small random factor for variety
            scores[i] += rng.nextInt(10);
        }

        // Sort moves by score descending (best first)
        Integer[] indices = new Integer[moves.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> scores[b] - scores[a]);

        for (int idx : indices) {
            int[] move = moves.get(idx);
            extend(color, end, move);

            if (tinyPocketOk(move[0], move[1]) && solve()) return true;

            retract(color, end, move);
        }

        deadStates.add(stateHash);
        return false;
    }

    // -----------------------------------------------------------------------
    // Move Helpers
    // -----------------------------------------------------------------------

    private int[] getEndCell(int color, int end) {
        return (end == 0) ? paths[color].peekFirst() : paths[color].peekLast();
    }

    /** Count valid moves for a color's end without allocating a list. */
    private int countValidMoves(int color, int end) {
        int[] endCell = getEndCell(color, end);
        int colorId = color + 1;
        int count = 0;
        for (int d = 0; d < 4; d++) {
            int nr = endCell[0]+DR[d], nc = endCell[1]+DC[d];
            if (inBounds(nr, nc) && grid[nr][nc] == 0
                && !hasSameColorNeighborExcept(nr, nc, colorId, endCell)) {
                count++;
            }
        }
        return count;
    }

    /** Get valid moves for a color extending from endCell. */
    private List<int[]> getValidMoves(int color, int[] endCell) {
        int colorId = color + 1;
        List<int[]> result = new ArrayList<>(4);
        for (int d = 0; d < 4; d++) {
            int nr = endCell[0]+DR[d], nc = endCell[1]+DC[d];
            if (inBounds(nr, nc) && grid[nr][nc] == 0
                && !hasSameColorNeighborExcept(nr, nc, colorId, endCell)) {
                result.add(new int[]{nr, nc});
            }
        }
        return result;
    }

    private boolean hasSameColorNeighborExcept(int r, int c, int colorId, int[] except) {
        for (int d = 0; d < 4; d++) {
            int nr = r+DR[d], nc = c+DC[d];
            if (nr == except[0] && nc == except[1]) continue;
            if (inBounds(nr, nc) && grid[nr][nc] == colorId) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Pruning Checks
    // -----------------------------------------------------------------------

    /** Check that no empty neighbor of (r,c) became a trapped tiny pocket. */
    private boolean tinyPocketOk(int pr, int pc) {
        for (int d = 0; d < 4; d++) {
            int nr = pr+DR[d], nc = pc+DC[d];
            if (!inBounds(nr, nc) || grid[nr][nc] != 0) continue;
            // Count empty neighbors of this empty cell
            int emptyN = 0;
            for (int d2 = 0; d2 < 4; d2++) {
                int nnr = nr+DR[d2], nnc = nc+DC[d2];
                if (inBounds(nnr, nnc) && grid[nnr][nnc] == 0) emptyN++;
            }
            if (emptyN == 0) {
                // This cell is completely surrounded. Is there any open path end adjacent?
                boolean adjEnd = false;
                for (int col = 0; col < N; col++) {
                    if (paths[col].isEmpty()) continue;
                    if (isAdj(paths[col].peekFirst(), nr, nc)) { adjEnd = true; break; }
                    if (paths[col].size() > 1 && isAdj(paths[col].peekLast(), nr, nc)) { adjEnd = true; break; }
                }
                if (!adjEnd) return false; // trapped 1-cell pocket
            }
        }
        return true;
    }

    /** Fast version: would filling (r,c) likely create a tiny pocket? (no allocation) */
    private boolean tinyPocketOkFast(int r, int c) {
        for (int d = 0; d < 4; d++) {
            int nr = r+DR[d], nc = c+DC[d];
            if (!inBounds(nr, nc) || grid[nr][nc] != 0) continue;
            int emptyN = 0;
            for (int d2 = 0; d2 < 4; d2++) {
                int nnr = nr+DR[d2], nnc = nc+DC[d2];
                if (nnr == r && nnc == c) continue; // this cell will be filled
                if (inBounds(nnr, nnc) && grid[nnr][nnc] == 0) emptyN++;
            }
            if (emptyN == 0) return false; // would create isolated cell
        }
        return true;
    }

    /** BFS reachability: all empty cells must be reachable from some open end. */
    private boolean reachabilityOk(List<int[]> openEnds) {
        boolean[][] vis = new boolean[rows][cols];
        Queue<int[]> q = new ArrayDeque<>();

        for (int[] oe : openEnds) {
            int[] cell = getEndCell(oe[0], oe[1]);
            for (int d = 0; d < 4; d++) {
                int nr = cell[0]+DR[d], nc = cell[1]+DC[d];
                if (inBounds(nr, nc) && grid[nr][nc] == 0 && !vis[nr][nc]) {
                    vis[nr][nc] = true;
                    q.add(new int[]{nr, nc});
                }
            }
        }

        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int d = 0; d < 4; d++) {
                int nr = cur[0]+DR[d], nc = cur[1]+DC[d];
                if (inBounds(nr, nc) && grid[nr][nc] == 0 && !vis[nr][nc]) {
                    vis[nr][nc] = true;
                    q.add(new int[]{nr, nc});
                }
            }
        }

        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (grid[r][c] == 0 && !vis[r][c]) return false;
        return true;
    }

    // -----------------------------------------------------------------------
    // Dead State Hashing
    // -----------------------------------------------------------------------

    /** Compact hash of current board + frontier state. */
    private long computeStateHash() {
        long h = 0;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                h = h * 31 + grid[r][c];
        // Include frontier positions
        for (int c = 0; c < N; c++) {
            if (!paths[c].isEmpty()) {
                int[] first = paths[c].peekFirst();
                int[] last = paths[c].peekLast();
                h = h * 31 + first[0] * cols + first[1];
                h = h * 31 + last[0] * cols + last[1];
            }
        }
        return h;
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
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (grid[r][c] == 0) return false;

        for (int c = 0; c < N; c++)
            if (paths[c].size() < cfg.minPathLength) return false;

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

    private boolean isAdj(int[] cell, int r, int c) {
        return Math.abs(cell[0]-r) + Math.abs(cell[1]-c) == 1;
    }

    /** Count empty neighbors of (r,c), excluding (r,c) itself from the count. */
    private int countEmptyExcluding(int r, int c) {
        int cnt = 0;
        for (int d = 0; d < 4; d++) {
            int nr = r+DR[d], nc = c+DC[d];
            if (inBounds(nr, nc) && grid[nr][nc] == 0) cnt++;
        }
        return cnt;
    }

    private int colorDegree(int r, int c, int colorId) {
        int deg = 0;
        for (int d = 0; d < 4; d++) {
            int nr = r+DR[d], nc = c+DC[d];
            if (inBounds(nr, nc) && grid[nr][nc] == colorId) deg++;
        }
        return deg;
    }
}
