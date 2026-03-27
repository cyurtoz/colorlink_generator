package com.colorlink.engine;

import java.util.List;

/**
 * Evaluates generated boards for playfulness quality beyond raw correctness.
 *
 * Each check contributes to a playfulness score. A board must pass ALL
 * individual thresholds AND meet a minimum composite playfulness score.
 *
 * Checks applied (all must pass):
 *   1. Short-path ratio: at most maxShortPathRatio of all colors may have a path ≤ 3 cells.
 *   2. Minimum turn count: at least (colorCount / 2) total turns across all paths.
 *   3. Endpoint spread: average Manhattan distance between paired endpoints
 *      must be above a size-scaled floor to avoid trivial same-area pairs.
 *   4. Turn diversity: at least 60% of colors must have ≥ 1 turn.
 *   5. Path length variance: paths should not all be the same length.
 *   6. Minimum composite playfulness score.
 */
public class QualityFilter {

    private QualityFilter() {}

    public static boolean passes(List<ColorPath> paths, GeneratorConfig cfg) {
        return checkShortPathRatio(paths, cfg)
            && checkMinTurns(paths, cfg)
            && checkEndpointSpread(paths, cfg)
            && checkTurnDiversity(paths, cfg)
            && checkPathVariance(paths, cfg)
            && checkPlayfulnessScore(paths, cfg);
    }

    // -----------------------------------------------------------------------

    /** At most maxShortPathRatio fraction of colors may have length ≤ 3. */
    private static boolean checkShortPathRatio(List<ColorPath> paths, GeneratorConfig cfg) {
        long shortCount = paths.stream()
            .filter(p -> p.getLength() <= 3)
            .count();
        double ratio = (double) shortCount / paths.size();
        return ratio <= cfg.maxShortPathRatio;
    }

    private static boolean checkMinTurns(List<ColorPath> paths, GeneratorConfig cfg) {
        int totalTurns = paths.stream().mapToInt(ColorPath::countTurns).sum();
        int minRequired = cfg.colorCount / 2;
        return totalTurns >= minRequired;
    }

    private static boolean checkEndpointSpread(List<ColorPath> paths, GeneratorConfig cfg) {
        double avgSpread = paths.stream()
            .mapToInt(ColorPath::endpointSpread)
            .average()
            .orElse(0);
        int floor = Math.max(2, Math.min(cfg.rows, cfg.cols) / 3);
        return avgSpread >= floor;
    }

    private static boolean checkTurnDiversity(List<ColorPath> paths, GeneratorConfig cfg) {
        long withTurns = paths.stream().filter(p -> p.countTurns() >= 1).count();
        return withTurns >= (long) Math.ceil(paths.size() * 0.60);
    }

    /** Paths should have some length variety — not all the same length. */
    private static boolean checkPathVariance(List<ColorPath> paths, GeneratorConfig cfg) {
        if (paths.size() <= 2) return true;
        int min = paths.stream().mapToInt(ColorPath::getLength).min().orElse(0);
        int max = paths.stream().mapToInt(ColorPath::getLength).max().orElse(0);
        return (max - min) >= 2;
    }

    /**
     * Composite playfulness score: combines turn density, spread quality,
     * and path variety into a single 0-100 score. Rejects boards below threshold.
     */
    private static boolean checkPlayfulnessScore(List<ColorPath> paths, GeneratorConfig cfg) {
        int totalCells = cfg.rows * cfg.cols;
        int totalTurns = paths.stream().mapToInt(ColorPath::countTurns).sum();
        double avgSpread = paths.stream().mapToInt(ColorPath::endpointSpread).average().orElse(0);
        int minLen = paths.stream().mapToInt(ColorPath::getLength).min().orElse(0);
        int maxLen = paths.stream().mapToInt(ColorPath::getLength).max().orElse(0);
        long withTurns = paths.stream().filter(p -> p.countTurns() >= 1).count();

        // Turn density: turns per cell (more turns = more interesting)
        double turnDensity = (double) totalTurns / totalCells;
        double turnScore = Math.min(30, turnDensity * 150); // 0-30 points

        // Spread quality: how far apart endpoints are relative to grid size
        double gridDiag = Math.sqrt(cfg.rows * cfg.rows + cfg.cols * cfg.cols);
        double spreadScore = Math.min(30, (avgSpread / gridDiag) * 100); // 0-30 points

        // Turn diversity: fraction of paths with turns
        double diversityScore = ((double) withTurns / paths.size()) * 20; // 0-20 points

        // Path length variety: range relative to average
        double avgLen = (double) totalCells / cfg.colorCount;
        double varietyScore = Math.min(20, ((double)(maxLen - minLen) / avgLen) * 30); // 0-20 points

        double totalScore = turnScore + spreadScore + diversityScore + varietyScore;
        return totalScore >= 25; // minimum playfulness threshold
    }
}
