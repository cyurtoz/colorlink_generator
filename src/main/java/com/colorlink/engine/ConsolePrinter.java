package com.colorlink.engine;

import java.util.List;

/**
 * Formats and prints a GenerationResult to the console.
 *
 * Output format
 * -------------
 *   Grid size / color count header
 *
 *   PUZZLE BOARD
 *     - empty cells  ->  .
 *     - endpoints    ->  A  (UPPERCASE)
 *
 *   SOLUTION BOARD
 *     - endpoints    ->  A  (UPPERCASE)
 *     - path cells   ->  a  (lowercase)
 *
 *   Example 5-cell path for color A: A a a a A
 *   Uppercase = the two clue/endpoint dots; lowercase = route in between.
 *
 *   METADATA SUMMARY - path lengths, turns, attempts, seed
 */
public class ConsolePrinter {

    // Color labels A-Z (spec max is 10 colors)
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";

    private ConsolePrinter() {}

    // -----------------------------------------------------------------------

    public static void print(GenerationResult result) {
        Board puzzle   = result.puzzle;
        Board solution = result.solution;

        printHeader(solution);
        System.out.println();
        printPuzzle(puzzle);
        System.out.println();
        printSolution(solution);
        System.out.println();
        printMetadata(result);
    }

    // -----------------------------------------------------------------------
    // Header
    // -----------------------------------------------------------------------

    private static void printHeader(Board solution) {
        System.out.println("╔══════════════════════════════════╗");
        System.out.printf( "║  COLORLINK  %dx%d  ·  %d colors%s║%n",
            solution.rows, solution.cols, solution.colorCount,
            " ".repeat(Math.max(1, 20 - digitLen(solution.rows) - digitLen(solution.cols)
                              - digitLen(solution.colorCount))));
        System.out.println("╚══════════════════════════════════╝");
    }

    // -----------------------------------------------------------------------
    // Puzzle Board  (only endpoints visible, all UPPERCASE)
    // -----------------------------------------------------------------------

    private static void printPuzzle(Board puzzle) {
        System.out.println("  PUZZLE BOARD  (endpoints UPPERCASE, empty = .)");
        printBorder("top", puzzle.cols);
        for (int r = 0; r < puzzle.rows; r++) {
            System.out.print("  |");
            for (int c = 0; c < puzzle.cols; c++) {
                int colorId = puzzle.getColor(r, c);
                if (colorId == 0) {
                    System.out.print(" . |");
                } else {
                    System.out.print(" " + upperLabel(colorId) + " |");
                }
            }
            System.out.println();
            if (r < puzzle.rows - 1) printBorder("mid", puzzle.cols);
        }
        printBorder("bot", puzzle.cols);
    }

    // -----------------------------------------------------------------------
    // Solution Board  (endpoints UPPERCASE, path cells lowercase)
    // -----------------------------------------------------------------------

    private static void printSolution(Board solution) {
        System.out.println("  SOLUTION BOARD  (endpoints UPPERCASE, path = lowercase)");
        printBorder("top", solution.cols);
        for (int r = 0; r < solution.rows; r++) {
            System.out.print("  |");
            for (int c = 0; c < solution.cols; c++) {
                int colorId = solution.getColor(r, c);
                boolean ep  = solution.isEndpoint(r, c);
                if (colorId == 0) {
                    System.out.print(" . |");
                } else if (ep) {
                    System.out.print(" " + upperLabel(colorId) + " |");
                } else {
                    System.out.print(" " + lowerLabel(colorId) + " |");
                }
            }
            System.out.println();
            if (r < solution.rows - 1) printBorder("mid", solution.cols);
        }
        printBorder("bot", solution.cols);
    }

    // -----------------------------------------------------------------------
    // Compact grid (no box borders) - handy for copy-paste validation
    // Called for grids >= 9x9 to avoid console line-wrap.
    // -----------------------------------------------------------------------

    public static void printCompact(GenerationResult result) {
        Board puzzle   = result.puzzle;
        Board solution = result.solution;

        printHeader(solution);
        System.out.println();
        System.out.println("  PUZZLE (compact)   . = empty  UPPER = endpoint");
        for (int r = 0; r < puzzle.rows; r++) {
            System.out.print("  ");
            for (int c = 0; c < puzzle.cols; c++) {
                int id = puzzle.getColor(r, c);
                System.out.print(id == 0 ? "." : upperLabel(id));
            }
            System.out.println();
        }
        System.out.println();
        System.out.println("  SOLUTION (compact)  UPPER = endpoint  lower = path");
        for (int r = 0; r < solution.rows; r++) {
            System.out.print("  ");
            for (int c = 0; c < solution.cols; c++) {
                int id = solution.getColor(r, c);
                boolean ep = solution.isEndpoint(r, c);
                if (id == 0) System.out.print(".");
                else System.out.print(ep ? upperLabel(id) : lowerLabel(id));
            }
            System.out.println();
        }
        System.out.println();
        printMetadata(result);
    }

    // -----------------------------------------------------------------------
    // Metadata
    // -----------------------------------------------------------------------

    private static void printMetadata(GenerationResult result) {
        System.out.println("  METADATA");
        System.out.println("  ---------------------------------");
        System.out.printf( "  Seed          : %d%n", result.seed);
        System.out.printf( "  Attempts used : %d%n", result.attempts);
        System.out.printf( "  Total cells   : %d%n", result.totalCells());
        System.out.printf( "  Total turns   : %d%n", result.totalTurns());
        System.out.printf( "  Path lengths  : min=%d  avg=%.1f  max=%d%n",
            result.minPathLen(), result.avgPathLen(), result.maxPathLen());
        System.out.println();
        System.out.println("  Color breakdown  (UPPER=endpoint  lower=path):");
        System.out.println("  +-------+--------+-------+----------------------+");
        System.out.println("  | Color | Length | Turns | Endpoints            |");
        System.out.println("  +-------+--------+-------+----------------------+");
        for (ColorPath cp : result.colorPaths) {
            System.out.printf("  |  %s/%s  |  %-5d |  %-4d | %s -> %s%s|%n",
                upperLabel(cp.colorId),
                lowerLabel(cp.colorId),
                cp.getLength(),
                cp.countTurns(),
                cp.getEndpoint1(),
                cp.getEndpoint2(),
                " ".repeat(Math.max(1, 21 - cp.getEndpoint1().toString().length()
                                       - cp.getEndpoint2().toString().length() - 3)));
        }
        System.out.println("  +-------+--------+-------+----------------------+");
        System.out.println();
        System.out.println("  Legend: endpoint=UPPERCASE  path=lowercase  empty=.");
    }

    // -----------------------------------------------------------------------
    // Box-drawing helpers
    // -----------------------------------------------------------------------

    private static void printBorder(String pos, int cols) {
        String h = "---+";
        switch (pos) {
            case "top": System.out.println("  +" + h.repeat(cols)); break;
            case "mid": System.out.println("  +" + h.repeat(cols)); break;
            case "bot": System.out.println("  +" + h.repeat(cols)); break;
        }
    }

    // -----------------------------------------------------------------------
    // Label helpers
    // -----------------------------------------------------------------------

    /** Uppercase label for a 1-based colorId: 1->A, 2->B ... */
    public static String upperLabel(int colorId) {
        if (colorId < 1 || colorId > UPPER.length()) return "?";
        return String.valueOf(UPPER.charAt(colorId - 1));
    }

    /** Lowercase label for a 1-based colorId: 1->a, 2->b ... */
    public static String lowerLabel(int colorId) {
        if (colorId < 1 || colorId > LOWER.length()) return "?";
        return String.valueOf(LOWER.charAt(colorId - 1));
    }

    /** Legacy alias used by metadata tables (returns uppercase). */
    public static String colorLabel(int colorId) {
        return upperLabel(colorId);
    }

    private static int digitLen(int n) { return String.valueOf(n).length(); }
}
