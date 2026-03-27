package com.colorlink.engine;

/**
 * Console entry point for the Colorlink puzzle generator.
 *
 * Usage examples:
 *   java -jar target/colorlink-generator-1.0-SNAPSHOT.jar
 *   java -jar target/colorlink-generator-1.0-SNAPSHOT.jar generate 5 5 4
 *   java -jar target/colorlink-generator-1.0-SNAPSHOT.jar generateRandom
 *   java -jar target/colorlink-generator-1.0-SNAPSHOT.jar batch 7 7 6 5
 *   java -jar target/colorlink-generator-1.0-SNAPSHOT.jar seed 42 8 8 7
 *   java -jar target/colorlink-generator-1.0-SNAPSHOT.jar demo
 */
public class ColorlinkGeneratorApp {

    public static void main(String[] args) {
        if (args.length == 0) {
            demo7x7();
            return;
        }

        String cmd = args[0].toLowerCase();
        switch (cmd) {

            case "generate": {
                if (args.length < 3) { usage(); return; }
                int rows   = Integer.parseInt(args[1]);
                int cols   = Integer.parseInt(args[2]);
                int colors = args.length >= 4
                    ? Integer.parseInt(args[3])
                    : defaultColors(rows);
                runSingle(GeneratorConfig.forSize(rows, cols, colors));
                break;
            }

            case "generaterandom": {
                int[][] sizes = {{5,5},{6,6},{7,7},{8,8},{9,9},{10,10}};
                int[][] picked = {sizes[(int)(System.currentTimeMillis() % sizes.length)]};
                int r = picked[0][0], c = picked[0][1];
                runSingle(GeneratorConfig.forSize(r, c, defaultColors(r)));
                break;
            }

            case "batch": {
                // batch <rows> <cols> <colors> <count>
                if (args.length < 5) { usage(); return; }
                int rows   = Integer.parseInt(args[1]);
                int cols   = Integer.parseInt(args[2]);
                int colors = Integer.parseInt(args[3]);
                int count  = Integer.parseInt(args[4]);
                runBatch(rows, cols, colors, count);
                break;
            }

            case "seed": {
                // seed <seedValue> <rows> <cols> [colors]
                if (args.length < 4) { usage(); return; }
                long seed  = Long.parseLong(args[1]);
                int rows   = Integer.parseInt(args[2]);
                int cols   = Integer.parseInt(args[3]);
                int colors = args.length >= 5
                    ? Integer.parseInt(args[4])
                    : defaultColors(rows);
                GeneratorConfig cfg = new GeneratorConfig.Builder(rows, cols, colors)
                    .seed(seed).build();
                runSingle(cfg);
                break;
            }

            case "demo": {
                runDemo();
                break;
            }

            default:
                usage();
        }
    }

    // -----------------------------------------------------------------------
    // Run modes
    // -----------------------------------------------------------------------

    private static void runSingle(GeneratorConfig cfg) {
        System.out.println("Generating puzzle: " + cfg);
        long t0 = System.currentTimeMillis();
        GenerationResult result = new SolvedBoardGenerator(cfg).generate();
        long elapsed = System.currentTimeMillis() - t0;

        if (result == null) {
            System.out.println("FAILED to generate puzzle after " + cfg.maxAttempts + " attempts.");
            return;
        }

        ConsolePrinter.print(result);

        // Optional: structural validation
        if (cfg.validationEnabled) {
            SolverValidator.ValidationReport report =
                SolverValidator.validate(result.solution, result.puzzle);
            System.out.println();
            System.out.println("  Structural validation: " + report);
        }

        System.out.printf("%n  Generated in %d ms.%n", elapsed);
    }

    private static void runBatch(int rows, int cols, int colors, int count) {
        System.out.printf("Batch: %d puzzles on %dx%d grid with %d colors%n%n",
            count, rows, cols, colors);
        int success = 0;
        for (int i = 0; i < count; i++) {
            // Each run gets a fresh time-based seed so puzzles differ
            GeneratorConfig cfg = new GeneratorConfig.Builder(rows, cols, colors)
                .seed(System.currentTimeMillis() + i * 1337L)
                .build();
            GenerationResult result = new SolvedBoardGenerator(cfg).generate();
            if (result != null) {
                System.out.printf("â”€â”€ Puzzle %d (seed=%d, attempts=%d) â”€â”€%n",
                    i + 1, result.seed, result.attempts);
                ConsolePrinter.print(result);
                System.out.println();
                success++;
            } else {
                System.out.printf("â”€â”€ Puzzle %d FAILED%n", i + 1);
            }
        }
        System.out.printf("Batch complete: %d/%d succeeded.%n", success, count);
    }

    private static void runDemo() {
        int[][] spec = {{5,5,4},{6,6,5},{7,7,6},{8,8,7},{9,9,8},{10,10,9}};
        System.out.println("=== Colorlink Generator â€“ Full Demo ===");
        System.out.println();
        for (int[] s : spec) {
            System.out.printf("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€%n");
            GeneratorConfig cfg = GeneratorConfig.forSize(s[0], s[1], s[2]);
            runSingle(cfg);
            System.out.println();
        }
    }

    private static void demo7x7() {
        System.out.println("Colorlink Generator â€“ Day One (default 7x7 demo)");
        System.out.println("Run with 'demo' to see all sizes, or 'generate <r> <c> [colors]'.");
        System.out.println();
        runSingle(GeneratorConfig.forSize(8, 8, 7));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Default color count per spec table. */
    public static int defaultColors(int gridSize) {
        switch (gridSize) {
            case 5:  return 4;
            case 6:  return 5;
            case 7:  return 6;
            case 8:  return 7;
            case 9:  return 8;
            case 10: return 9;
            default: return Math.max(3, gridSize - 1);
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  generate <rows> <cols> [colors]");
        System.out.println("  generateRandom");
        System.out.println("  batch <rows> <cols> <colors> <count>");
        System.out.println("  seed <seedValue> <rows> <cols> [colors]");
        System.out.println("  demo");
    }
}
