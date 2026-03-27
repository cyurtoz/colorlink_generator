# Colorlink Generator

## Technical Class Design

### Full Method Signatures & Responsibilities

**Day One Edition · Q1 Design Document**

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [GeneratorConfig](#2-generatorconfig)
3. [Cell](#3-cell)
4. [ColorPath](#4-colorpath)
5. [Board](#5-board)
6. [GenerationResult](#6-generationresult)
7. [SolvedBoardGenerator](#7-solvedboardgenerator)
8. [PuzzleExtractor](#8-puzzleextractor)
9. [SolverValidator](#9-solvervalidator)
10. [QualityFilter](#10-qualityfilter)
11. [ConsolePrinter](#11-consoleprinter)
12. [ColorlinkGeneratorApp](#12-colorlinkgeneratorapp)
13. [End-to-End Call Flow](#13-end-to-end-call-flow)
14. [Dependency Graph](#14-dependency-graph)
15. [Extension Points for Future Versions](#15-extension-points-for-future-versions)

---

## 1. System Overview

The Colorlink Generator uses a solution-first architecture: it fills the entire grid with valid colour paths, then derives the puzzle board by exposing only the path endpoints as clues. This guarantees full-coverage correctness by construction.

| Class | Role | One-line responsibility |
| --- | --- | --- |
| GeneratorConfig | Config | Immutable config bean — holds all tuning knobs |
| Cell | Value | Immutable (row, col) coordinate — used everywhere |
| ColorPath | Model | Ordered cell sequence for one colour + metrics |
| Board | Model | 2-D grid with colour + endpoint accessors, BFS utils |
| GenerationResult | Output | Wraps puzzle board, solution board, paths, and stats |
| SolvedBoardGenerator | Core | Solution-first backtracking search with pruning |
| PuzzleExtractor | Transform | Strips non-endpoint cells to produce the puzzle board |
| SolverValidator | Validator | Full structural correctness check on any solved board |
| QualityFilter | Filter | Aesthetic quality gates — turns, spread, ratio |
| ConsolePrinter | UI | Formatted console output for puzzle + metadata |
| ColorlinkGeneratorApp | Entry | CLI entry point; command parsing; batch/demo modes |

---

## 2. GeneratorConfig

> **Class — Immutable Bean**
>
> Holds all generation tuning parameters. Constructed via a Builder. Shared read-only across all modules.
>
> **Depends on:** none

### 2.1 Fields

| Field | Type | Description |
| --- | --- | --- |
| rows | int | Grid row count (5–10) |
| cols | int | Grid column count (5–10) |
| colorCount | int | Number of colours (3–10, constrained by grid) |
| randomSeed | long | RNG seed — same seed reproduces identical output |
| maxAttempts | int | Max generation retries before reporting failure |
| minPathLength | int | Minimum allowed cells per colour path |
| maxShortPathRatio | double | Max fraction of colours allowed to have length ≤ 3 |
| minEndpointDistance | int | Minimum Manhattan distance between paired endpoints |
| validationEnabled | boolean | If true, run SolverValidator after each success |

### 2.2 Methods

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| forSize(int rows, int cols, int n) | GeneratorConfig | Static factory — applies spec-recommended defaults for the given grid size |
| Builder(int rows, int cols, int n) | Builder | Constructor — sets per-size defaults for minPathLength |
| Builder.seed(long s) | Builder | Override RNG seed |
| Builder.attempts(int n) | Builder | Override maxAttempts |
| Builder.minPathLen(int n) | Builder | Override minimum path length |
| Builder.shortRatio(double d) | Builder | Override maxShortPathRatio |
| Builder.minEpDist(int n) | Builder | Override minEndpointDistance |
| Builder.validate(boolean v) | Builder | Toggle SolverValidator |
| Builder.build() | GeneratorConfig | Produce the immutable config object |
| toString() | String | Human-readable config summary for logging |

---

## 3. Cell

> **Class — Immutable Value**
>
> Represents a single grid coordinate (row, col). Used as keys in lists, maps, and deques. Equality is structural.
>
> **Depends on:** none

### 3.1 Fields

| Field | Type | Description |
| --- | --- | --- |
| row | int (final) | Zero-based row index |
| col | int (final) | Zero-based column index |

### 3.2 Methods

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| Cell(int row, int col) | Cell | Canonical constructor |
| manhattanDistance(Cell other) | int | Returns \|row - other.row\| + \|col - other.col\| |
| isAdjacentTo(Cell other) | boolean | True if manhattanDistance == 1 (4-connected neighbours) |
| equals(Object o) | boolean | Structural equality on (row, col) pair |
| hashCode() | int | Objects.hash(row, col) — consistent with equals |
| toString() | String | "(row,col)" — used in console output and debug logs |

---

## 4. ColorPath

> **Class — Model**
>
> Ordered sequence of cells from endpoint1 (index 0) to endpoint2 (index size−1) for one colour. Immutable after construction. Provides metric queries used by QualityFilter and ConsolePrinter.
>
> **Depends on:** Cell

### 4.1 Fields

| Field | Type | Description |
| --- | --- | --- |
| colorId | int (final) | 1-based colour identifier (1 = A, 2 = B …) |
| cells | List\<Cell\> (final) | Unmodifiable ordered path; index 0 = endpoint1, last = endpoint2 |

### 4.2 Methods

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| ColorPath(int colorId, List\<Cell\> cells) | ColorPath | Constructor — defensively copies the list and makes it unmodifiable |
| getEndpoint1() | Cell | Returns cells.get(0) — first endpoint (puzzle clue) |
| getEndpoint2() | Cell | Returns cells.get(size−1) — second endpoint (puzzle clue) |
| getLength() | int | Total cell count including both endpoints |
| getCells() | List\<Cell\> | Returns the unmodifiable ordered cell list |
| countTurns() | int | Counts direction changes: increments whenever consecutive direction vectors differ |
| endpointSpread() | int | Manhattan distance between the two endpoints — quality proxy for non-trivial routing |
| toString() | String | Debug-friendly summary: colorId, length, turns, endpoint coords |

---

## 5. Board

> **Class — Model**
>
> Mutable rectangular grid. Stores an integer colour ID per cell (0 = empty) and an endpoint flag. Provides spatial utilities — neighbour enumeration, degree computation, BFS-based connected-component analysis — shared by the generator, extractor, and validator.
>
> **Depends on:** Cell

### 5.1 Fields

| Field | Type | Description |
| --- | --- | --- |
| rows | int (final) | Grid height |
| cols | int (final) | Grid width |
| colorCount | int (final) | Number of colours (1-based IDs run 1..colorCount) |
| grid | int[][] (private) | Colour ID per cell; 0 = empty |
| endpoint | boolean[][] (private) | True where a cell is a puzzle clue endpoint |

### 5.2 Methods

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| Board(int rows, int cols, int colorCount) | Board | Constructor — allocates zero-filled grid and endpoint arrays |
| getColor(int r, int c) | int | Returns colour ID at (r,c); 0 means empty |
| setColor(int r, int c, int color) | void | Writes colour ID to (r,c) |
| isEmpty(int r, int c) | boolean | True when getColor == 0 |
| isEndpoint(int r, int c) | boolean | True when the endpoint flag is set |
| setEndpoint(int r, int c, boolean v) | void | Sets or clears the endpoint flag |
| inBounds(int r, int c) | boolean | Returns r ∈ [0,rows) && c ∈ [0,cols) |
| isFull() | boolean | True when no cell is empty — O(rows×cols) |
| neighbors(int r, int c) | List\<Cell\> | Returns 2–4 orthogonal neighbours that are in-bounds |
| colorDegree(int r, int c) | int | Count of same-colour orthogonal neighbours; 0 if cell is empty |
| emptyComponents() | List\<List\<int[]\>\> | BFS-based connected components of empty cells; used for sealed-region detection |
| copy() | Board | Deep copy — all grid and endpoint values duplicated into a new Board |

---

## 6. GenerationResult

> **Class — Output DTO**
>
> Aggregates every artefact produced by a successful generation run. Passed to ConsolePrinter and SolverValidator. All fields are public-final.
>
> **Depends on:** Board, ColorPath

### 6.1 Fields

| Field | Type | Description |
| --- | --- | --- |
| puzzle | Board (final) | Puzzle board: only endpoint cells have a colour; all others are 0 |
| solution | Board (final) | Fully-filled solution board: every cell has a colour |
| colorPaths | List\<ColorPath\> (final) | One ColorPath per colour, in colorId order |
| attempts | int (final) | How many generation attempts were needed |
| seed | long (final) | RNG seed used — enables exact reproduction |

### 6.2 Methods

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| GenerationResult(Board puzzle, Board solution, List\<ColorPath\> colorPaths, int attempts, long seed) | – | Constructor; assigns all fields |
| totalCells() | int | rows × cols — convenience shorthand |
| totalTurns() | int | Sum of countTurns() across all colour paths |
| minPathLen() | int | Minimum path length across all colours |
| maxPathLen() | int | Maximum path length across all colours |
| avgPathLen() | double | Mean path length (stream average over all colour paths) |

---

## 7. SolvedBoardGenerator

> **Class — Core Generator**
>
> The central workhorse. Runs a solution-first backtracking search: seeds one cell per colour, grows paths using a most-constrained heuristic, and applies pruning to abandon dead branches early. Retries up to maxAttempts times, then delegates to QualityFilter before returning a result.
>
> **Depends on:** GeneratorConfig, ColorPath, Board, GenerationResult, PuzzleExtractor, QualityFilter

### 7.1 Fields (per-instance)

| Field | Type | Description |
| --- | --- | --- |
| cfg | GeneratorConfig | Read-only config reference |
| rng | Random | Seeded RNG — same seed ⇒ same output |
| rows / cols / N / total | int | Grid dimensions and colour count (cached from cfg) |
| grid | int[][] | Per-attempt colour grid; 0 = empty, 1..N = colour id |
| paths | ArrayDeque\<int[]\>[] | Per-colour deque of [row,col] arrays — head = endpoint1, tail = endpoint2 |
| filled | int | Count of non-empty cells — triggers termination when == total |

### 7.2 Public Methods

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| SolvedBoardGenerator(GeneratorConfig cfg) | – | Constructor — seeds RNG, caches grid dimensions |
| generate() | GenerationResult | Main entry point. Loops up to maxAttempts; returns first quality-passing result, or null on failure |

### 7.3 Seed Placement (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| placeSeeds() | boolean | Places exactly N seed cells on the empty grid; returns false if minimum spacing cannot be satisfied after relaxation |
| trySeed(List\<int[]\> placed, int minDist) | int[] | Single seed placement attempt: draws a random cell 300 times, accepts first that satisfies Manhattan distance from all placed seeds. Returns null on failure |

### 7.4 Backtracking Search (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| solve() | boolean | Recursive DFS. Selects the most-constrained open end, shuffles + sorts candidates, extends and retracts with pruning. Returns true on first full valid board |
| computeOpenEnds() | List\<int[]\> | Returns [colourIdx, end] pairs where end=0 means head, end=1 means tail, and that end cell has ≥1 empty neighbour |
| mostConstrained(List\<int[]\> openEnds) | int[] | Selects the open end whose frontier cell has the fewest empty neighbours (MRV heuristic) |
| extend(int color, int end, int[] cell) | void | Appends cell to the chosen end of paths[color]; sets grid; increments filled |
| retract(int color, int end, int[] cell) | void | Removes cell from the chosen end of paths[color]; clears grid; decrements filled |

### 7.5 Pruning Checks (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| reachabilityOk(List\<int[]\> openEnds) | boolean | BFS from all open-end frontier cells over empty cells. Returns false if any empty cell is not visited — i.e. it is sealed in an unreachable island |
| isolationOk(int pr, int pc) | boolean | After placing (pr,pc), checks each empty neighbour. Returns false if any neighbour is now completely isolated: no empty neighbours of its own and no adjacent open path-end |
| isIsolated(int r, int c) | boolean | Returns true if cell (r,c) has zero empty neighbours AND no adjacent colour-path open end — making it permanently unfillable |

### 7.6 Final Validation (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| validateFinal() | boolean | Confirms: all cells filled; every colour meets minPathLength; every endpoint has degree 1; every internal cell has degree 2 |
| colorDegree(int r, int c, int colorId) | int | Counts same-colorId orthogonal neighbours — local helper used during validateFinal() |

### 7.7 Result Assembly (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| extractColorPaths() | List\<ColorPath\> | Converts each ArrayDeque\<int[]\> into an ordered List\<Cell\> and wraps in ColorPath |
| buildSolutionBoard(List\<ColorPath\>) | Board | Creates a Board from the current grid[][] and marks all path endpoints using setEndpoint() |

### 7.8 Utility Helpers (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| inBounds(int r, int c) | boolean | Bounds check — faster than delegating to Board during hot inner loop |
| hasEmptyNeighbor(int[] cell) | boolean | Returns true if any 4-connected neighbour of cell is empty |
| countEmpty(int r, int c) | int | Number of empty 4-connected neighbours of (r,c) — used by MRV heuristic |
| emptyNeighbors(int r, int c) | List\<int[]\> | List of empty 4-connected neighbour cells — candidate moves |
| isAdj(int[] cell, int r, int c) | boolean | True if cell is orthogonally adjacent to (r,c) |

---

## 8. PuzzleExtractor

> **Class — Transform (static utility)**
>
> Stateless utility. Derives the puzzle board from the fully-solved board by copying only endpoint cells; all internal path cells are set to 0 (hidden from the solver).
>
> **Depends on:** Board

### 8.1 Methods

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| PuzzleExtractor() | – | Private constructor — class is not instantiated; all methods are static |
| extract(Board solution, int colorCount) | Board | Iterates every cell. If isEndpoint(r,c) is true, copies colour and endpoint flag to the new Board; otherwise leaves the cell empty. Returns the puzzle Board. |

---

## 9. SolverValidator

> **Class — Validator (static utility)**
>
> Runs a suite of structural correctness checks on any (solution, puzzle) pair. Returns a ValidationReport listing all errors found. Used after every successful generation and can also validate externally-supplied boards.
>
> **Depends on:** Board

### 9.1 Methods — Public

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| SolverValidator() | – | Private constructor — static utility class |
| validate(Board solution, Board puzzle) | ValidationReport | Entry point. Runs all four checks in order; collects all errors into one report |

### 9.2 Methods — Individual Checks (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| checkFullCoverage(Board b, ValidationReport r) | void | Scans every cell; adds error on first empty cell found |
| checkColorComponents(Board b, ValidationReport r) | void | For each colour, BFS within same-colour cells from an arbitrary start; error if reachable count ≠ total cells of that colour (disconnected path) |
| checkDegrees(Board b, ValidationReport r) | void | For every filled cell: endpoint → expected degree 1; internal → expected degree 2. Adds error on mismatch |
| checkPuzzleConsistency(Board sol, Board puz, ValidationReport r) | void | Counts endpoint occurrences per colour in puzzle; errors if count ≠ 2 per colour or if puzzle colour ≠ solution colour at same cell |

### 9.3 Helper Methods (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| cellsOfColor(Board b, int colorId) | List\<int[]\> | Collects all cells assigned to colorId |
| bfsColor(Board b, int[] start, int colorId) | int | BFS restricted to cells of colorId; returns number of reachable cells from start |

### 9.4 Inner Class — ValidationReport

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| addError(String msg) | void | Appends an error message to the internal list |
| isValid() | boolean | Returns true when the error list is empty |
| getErrors() | List\<String\> | Unmodifiable view of all accumulated error messages |
| toString() | String | "VALID" or "INVALID: [msg1, msg2, ...]" |

---

## 10. QualityFilter

> **Class — Filter (static utility)**
>
> Applies four aesthetic quality gates to a candidate set of colour paths. All four must pass for the board to be accepted. Runs after structural validation and before result assembly.
>
> **Depends on:** ColorPath, GeneratorConfig

### 10.1 Methods — Public

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| QualityFilter() | – | Private constructor — static utility class |
| passes(List\<ColorPath\>, GeneratorConfig cfg) | boolean | Returns true only if all four individual checks pass. Short-circuits on first failure. |

### 10.2 Methods — Individual Checks (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| checkShortPathRatio(List\<ColorPath\>, GeneratorConfig) | boolean | Counts paths with length ≤ 3; rejects if fraction > maxShortPathRatio (default 25%) |
| checkMinTurns(List\<ColorPath\>, GeneratorConfig) | boolean | Sums countTurns() across all paths; rejects if total < colorCount / 2 |
| checkEndpointSpread(List\<ColorPath\>, GeneratorConfig) | boolean | Averages endpointSpread() across all paths; rejects if average < floor(min(rows,cols) / 3) |
| checkTurnDiversity(List\<ColorPath\>, GeneratorConfig) | boolean | Counts colours with ≥1 turn; rejects if proportion < 60% |

---

## 11. ConsolePrinter

> **Class — Presentation (static utility)**
>
> Formats a GenerationResult for human-readable console output. Renders the puzzle and solution boards with Unicode box-drawing, then prints per-colour statistics and generation metadata.
>
> **Depends on:** GenerationResult, Board, ColorPath

### 11.1 Methods — Public

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| ConsolePrinter() | – | Private constructor — static utility class |
| print(GenerationResult) | void | Orchestrates all output: header → puzzle → solution → metadata. Calls private helpers in sequence. |
| colorLabel(int colorId) | String | Maps 1-based colour id to a letter: 1→A, 2→B, … 26→Z. Returns "?" for out-of-range ids. Static and public so other classes can reuse it. |

### 11.2 Methods — Rendering Helpers (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| printHeader(Board solution) | void | Prints the top banner with grid size and colour count |
| printBoard(String title, Board, boolean filled) | void | Renders one board with Unicode box-drawing borders. When filled=false, hides non-endpoint cells as '.'; when filled=true, renders all cells. Endpoints shown as [A], internals as  A . |
| printMetadata(GenerationResult) | void | Prints seed, attempts, total cells, total turns, path length stats, and a per-colour table with length, turns, and endpoint coordinates |
| digitLen(int n) | int | String.valueOf(n).length() — used for alignment padding in the banner |

### 11.3 Output Format Reference

Puzzle board cell rendering:

| Cell state | Rendered as |
| --- | --- |
| Empty cell | `.` (dot, spaces around) |
| Endpoint A | `[A]` (letter in square brackets) |
| Internal cell | ` A ` (letter, space-padded, solution board only) |

---

## 12. ColorlinkGeneratorApp

> **Class — Entry Point**
>
> Main class. Parses CLI arguments and dispatches to the appropriate run mode. Has no instance state. Delegates all generation logic to SolvedBoardGenerator and all output to ConsolePrinter.
>
> **Depends on:** GeneratorConfig, SolvedBoardGenerator, GenerationResult, SolverValidator, ConsolePrinter

### 12.1 Methods — Public

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| main(String[] args) | void | Parses args[0] as a command keyword and dispatches. Falls through to demo7x7() when no args are supplied. |
| defaultColors(int gridSize) | int | Returns the spec-recommended default colour count for a given grid side length (5→4, 6→5, … 10→9). Public so other tooling can reuse the mapping. |

### 12.2 Run Modes (private)

| Method Signature | Returns | Responsibility |
| --- | --- | --- |
| runSingle(GeneratorConfig cfg) | void | Calls generate(), prints result via ConsolePrinter, optionally calls SolverValidator, and prints elapsed milliseconds |
| runBatch(int rows, int cols, int colors, int count) | void | Generates count independent puzzles (each with a different seed derived from system clock + offset). Prints each result and a final success-count summary. |
| runDemo() | void | Iterates all six spec-table sizes (5×5→10×10) and calls runSingle() for each with default colour counts |
| demo7x7() | void | Default no-args fallback: prints a usage hint then calls runSingle() for a 7×7 grid with 6 colours |

### 12.3 CLI Command Reference

| Command | Effect |
| --- | --- |
| `generate <r> <c> [colors]` | Single puzzle on r×c grid. Colors defaults to spec table value if omitted. |
| `generateRandom` | Picks a random size from the spec table and generates one puzzle. |
| `batch <r> <c> <colors> <n>` | Generates n independent puzzles. Each uses a unique seed. |
| `seed <s> <r> <c> [colors]` | Single puzzle with explicit seed s — guarantees reproducible output. |
| `demo` | Generates one puzzle for every supported grid size (5×5–10×10). |
| (no args) | Equivalent to: `generate 7 7 6` with a usage hint. |

---

## 13. End-to-End Call Flow

The following table traces a single `generate` call from CLI entry to console output.

| # | Caller | Callee | What happens |
| --- | --- | --- | --- |
| 1 | main() | runSingle(cfg) | CLI dispatches; config is ready |
| 2 | runSingle() | SolvedBoardGenerator.generate() | Outer retry loop starts (up to maxAttempts) |
| 3 | generate() | placeSeeds() | N seed cells placed with spacing |
| 4 | generate() | solve() | Recursive DFS begins |
| 5 | solve() | computeOpenEnds() | Find all extendable path ends |
| 6 | solve() | reachabilityOk() | Prune if any empty cell is sealed |
| 7 | solve() | mostConstrained() | MRV: pick end with fewest moves |
| 8 | solve() | extend() / retract() | Try each candidate; undo on failure |
| 9 | solve() | isolationOk() | Prune isolated empty neighbours |
| 10 | solve() | validateFinal() | Check degrees + min length when full |
| 11 | generate() | QualityFilter.passes() | Aesthetic quality gate |
| 12 | generate() | buildSolutionBoard() | Assemble Board from grid[][] |
| 13 | generate() | PuzzleExtractor.extract() | Derive puzzle board (hide internals) |
| 14 | runSingle() | SolverValidator.validate() | Optional structural re-check |
| 15 | runSingle() | ConsolePrinter.print() | Render to stdout |

---

## 14. Dependency Graph

Arrows point from dependent → dependency. All arrows are one-way; there are no circular dependencies.

| Class | Depends on |
| --- | --- |
| ColorlinkGeneratorApp | GeneratorConfig, SolvedBoardGenerator, GenerationResult, SolverValidator, ConsolePrinter |
| SolvedBoardGenerator | GeneratorConfig, ColorPath, Board, GenerationResult, PuzzleExtractor, QualityFilter |
| GenerationResult | Board, ColorPath |
| PuzzleExtractor | Board |
| SolverValidator | Board |
| QualityFilter | ColorPath, GeneratorConfig |
| ConsolePrinter | GenerationResult, Board, ColorPath |
| ColorPath | Cell |
| Board | Cell |
| GeneratorConfig | (none) |
| Cell | (none) |

---

## 15. Extension Points for Future Versions

| Feature | Recommended approach |
| --- | --- |
| Unique-solution check | Add an independent constraint-propagation solver. Inject it as a post-filter step in generate() between QualityFilter and result assembly. |
| Difficulty scoring | Add a DifficultyScorer class that accepts a GenerationResult and returns a numeric score based on path count, total turns, min path length, and solver backtrack depth. |
| Additional pruning | Add checkTwoByTwo() and checkParity() methods to SolvedBoardGenerator; call them inside solve() before the recursive call, in the same position as isolationOk(). |
| Difficulty presets | Add a DifficultyPreset enum consumed by GeneratorConfig.Builder to replace manual tuning of minPathLength, maxShortPathRatio, minEndpointDistance. |
| Board serialisation | Add BoardSerializer with toJson(Board) / fromJson(String) → Board methods. Enables file-based puzzle storage and batch validation. |
| GUI / web front-end | ConsolePrinter is already separated from the model. Add a BoardRenderer interface; implement ConsoleRenderer (current) and optionally an SVGRenderer or REST endpoint. |
| Parallel batch generation | SolvedBoardGenerator is stateless across attempts when each attempt resets. Wrap generate() in a Callable and submit to an ExecutorService in runBatch(). |

---

*— End of Document —*
