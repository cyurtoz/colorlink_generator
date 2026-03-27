package com.colorlink.engine;

/**
 * Derives the puzzle board from a solved board.
 *
 * Rule: keep only cells marked as endpoints; clear everything else.
 */
public class PuzzleExtractor {

    private PuzzleExtractor() {}

    /**
     * @param solution  A fully-solved, endpoint-marked board.
     * @param colorCount Number of colors (used to create the new board).
     * @return A new Board where only endpoint cells are coloured; all others are 0.
     */
    public static Board extract(Board solution, int colorCount) {
        Board puzzle = new Board(solution.rows, solution.cols, colorCount);

        for (int r = 0; r < solution.rows; r++) {
            for (int c = 0; c < solution.cols; c++) {
                if (solution.isEndpoint(r, c)) {
                    puzzle.setColor(r, c, solution.getColor(r, c));
                    puzzle.setEndpoint(r, c, true);
                }
                // all other cells remain 0 (empty)
            }
        }
        return puzzle;
    }
}
