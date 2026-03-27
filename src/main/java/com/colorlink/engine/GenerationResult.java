package com.colorlink.engine;

import java.util.List;

/**
 * Output of a successful generation run.
 */
public class GenerationResult {

    public final Board           puzzle;       // only endpoints visible
    public final Board           solution;     // fully filled
    public final List<ColorPath> colorPaths;   // ordered path data
    public final int             attempts;     // how many attempts were needed
    public final long            seed;         // random seed used

    public GenerationResult(Board puzzle, Board solution,
                            List<ColorPath> colorPaths, int attempts, long seed) {
        this.puzzle     = puzzle;
        this.solution   = solution;
        this.colorPaths = colorPaths;
        this.attempts   = attempts;
        this.seed       = seed;
    }

    // --- summary helpers ---

    public int totalCells()  { return solution.rows * solution.cols; }
    public int totalTurns()  { return colorPaths.stream().mapToInt(ColorPath::countTurns).sum(); }
    public int minPathLen()  { return colorPaths.stream().mapToInt(ColorPath::getLength).min().orElse(0); }
    public int maxPathLen()  { return colorPaths.stream().mapToInt(ColorPath::getLength).max().orElse(0); }
    public double avgPathLen(){ return colorPaths.stream().mapToInt(ColorPath::getLength).average().orElse(0); }
}
