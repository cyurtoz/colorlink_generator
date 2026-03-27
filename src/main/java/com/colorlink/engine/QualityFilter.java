package com.colorlink.engine;

import java.util.List;

/**
 * Evaluates generated boards for aesthetic quality beyond raw correctness.
 *
 * Checks applied (all must pass):
 *   1. Short-path ratio: at most maxShortPathRatio of all colors may have a path ≤ 3 cells.
 *   2. Minimum turn count: at least (colorCount / 2) total turns across all paths.
 *   3. Endpoint spread: average Manhattan distance between paired endpoints
 *      must be above a size-scaled floor to avoid trivial same-area pairs.
 *   4. No all-straight paths: at least 60 % of colors must have ≥ 1 turn.
 */
public class QualityFilter {

    private QualityFilter() {}

    public static boolean passes(List<ColorPath> paths, GeneratorConfig cfg) {
        return checkShortPathRatio(paths, cfg)
            && checkMinTurns(paths, cfg)
            && checkEndpointSpread(paths, cfg)
            && checkTurnDiversity(paths, cfg);
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

    /**
     * Require a minimum number of total turns so the puzzle isn't just
     * a collection of straight lines.
     */
    private static boolean checkMinTurns(List<ColorPath> paths, GeneratorConfig cfg) {
        int totalTurns = paths.stream().mapToInt(ColorPath::countTurns).sum();
        int minRequired = cfg.colorCount / 2; // at least half as many turns as colors
        return totalTurns >= minRequired;
    }

    /**
     * Average endpoint spread must be at least floor(gridSide / 3).
     * This ensures endpoints are not all clustered together.
     */
    private static boolean checkEndpointSpread(List<ColorPath> paths, GeneratorConfig cfg) {
        double avgSpread = paths.stream()
            .mapToInt(ColorPath::endpointSpread)
            .average()
            .orElse(0);
        int floor = Math.max(2, Math.min(cfg.rows, cfg.cols) / 3);
        return avgSpread >= floor;
    }

    /**
     * At least 60 % of colors must have ≥ 1 turn (not perfectly straight).
     */
    private static boolean checkTurnDiversity(List<ColorPath> paths, GeneratorConfig cfg) {
        long withTurns = paths.stream().filter(p -> p.countTurns() >= 1).count();
        return withTurns >= (long) Math.ceil(paths.size() * 0.60);
    }
}
