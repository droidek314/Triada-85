package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;

/**
 * {@code programs/troika.tasm}, the ternary puzzle of M10. The rules are
 * modelled here in plain Java (a 5 x 5 array of trits), and the game, which
 * does the same with ROT and TMUL on whole row words, must agree with the
 * model after every move. A small search then solves real levels through the
 * real controls.
 */
class TroikaTest {

    private GameHarness g;

    @BeforeEach
    void boot() {
        g = new GameHarness("troika");
    }

    // --- the model ----------------------------------------------------------

    /** Applies a move (+1 up, -1 down, 0 flip) to the cross at (cx, cy) of a trit board [row][col]. */
    static void model(int[][] b, int op, int cx, int cy) {
        int[][] cross = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : cross) {
            int x = cx + d[0];
            int y = cy + d[1];
            if (x < 0 || x > 4 || y < 0 || y > 4) {
                continue;
            }
            int t = b[y][x];
            b[y][x] = op == 0 ? -t : (t + op + 4) % 3 - 1;
        }
    }

    /** A row word: the column-c cell is trit 4 - c. */
    static int word(int[] row) {
        int w = 0;
        for (int c = 0; c < 5; c++) {
            w = w * 3 + row[c];
        }
        return w;
    }

    private int[][] boardFromRam() {
        int[][] b = new int[5][5];
        for (int r = 0; r < 5; r++) {
            int w = g.ram(g.sym("board") + r);
            for (int c = 4; c >= 0; c--) {
                int t = Math.floorMod(w + 1, 3) - 1;     // the lowest trit
                b[r][c] = t;
                w = (w - t) / 3;
            }
            assertEquals(0, w, "row " + r + " has trits beyond the five cells");
        }
        return b;
    }

    private int[] words(int[][] b) {
        int[] w = new int[5];
        for (int r = 0; r < 5; r++) {
            w[r] = word(b[r]);
        }
        return w;
    }

    private int[] ramWords() {
        int[] w = new int[5];
        for (int r = 0; r < 5; r++) {
            w[r] = g.ram(g.sym("board") + r);
        }
        return w;
    }

    /** Every cell's centre tile shows its trit: tile 6v + 10 (the sign on the cell colour). */
    private void assertDrawn(int[][] b) {
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                int v = b[r][c];
                int col = 6 + 3 * c;
                int row = 2 + 3 * r;
                assertEquals(6 * v + 7, g.cell(col, row), "top-left of " + c + "," + r);
                assertEquals(6 * v + 10, g.cell(col + 1, row + 1), "centre of " + c + "," + r);
                assertEquals(-(6 * v + 9), g.cell(col + 2, row + 1), "right edge of " + c + "," + r);
            }
        }
    }

    // --- playing ---------------------------------------------------------------

    private void push(int dx, int dy) {
        g.joy(dx, dy);
        g.frames(1);
        g.joy(0, 0);
        g.frames(1);
    }

    private void cursorTo(int x, int y) {
        while (g.var("V_CX") < x) {
            push(1, 0);
        }
        while (g.var("V_CX") > x) {
            push(-1, 0);
        }
        while (g.var("V_CY") < y) {
            push(0, 1);
        }
        while (g.var("V_CY") > y) {
            push(0, -1);
        }
    }

    private void move(int op) {
        g.tap(op == 1 ? 'A' : op == -1 ? 'B' : 'C');
    }

    // --- tests -----------------------------------------------------------------

    @Test
    void levelOneIsScrambledWithParThree() {
        assertEquals(1, g.var("V_LEVEL"));
        assertEquals(3, g.var("V_PAR"));
        assertEquals(0, g.var("V_MOVES"));
        assertEquals(0, g.var("V_SOLVED"));
        int[][] b = boardFromRam();
        assertFalse(java.util.Arrays.stream(ramWords()).allMatch(w -> w == 0), "not already solved");
        assertDrawn(b);
        assertTrue(g.row(0).startsWith("TROIKA"));
        assertTrue(g.row(0).endsWith("LEVEL 1 "));
        assertEquals(" MOVES 0   PAR 3    R AGAIN", g.row(17));
        assertArrayEquals(ramWords(), startBoard(), "the start is kept for R");
    }

    private int[] startBoard() {
        int[] w = new int[5];
        for (int r = 0; r < 5; r++) {
            w[r] = g.ram(g.sym("startboard") + r);
        }
        return w;
    }

    @Test
    void theCursorMovesOneCellPerPushAndStaysOnTheBoard() {
        assertEquals(2, g.var("V_CX"));
        assertEquals(2, g.var("V_CY"));
        g.joy(1, 0);
        g.frames(10);                               // held: one step only
        assertEquals(3, g.var("V_CX"));
        g.joy(0, 0);
        g.frames(1);
        push(1, 0);
        push(1, 0);
        push(1, 0);
        assertEquals(4, g.var("V_CX"), "stops at the right edge");
        push(0, -1);
        push(0, -1);
        push(0, -1);
        assertEquals(0, g.var("V_CY"), "stops at the top");
        cursorTo(0, 4);
        assertEquals(0, g.var("V_CX"));
        assertEquals(4, g.var("V_CY"));
        // The four corner sprites frame cell (0, 4): x = 48, y = 16 + 4 * 24.
        int s = MemoryMap.SPRITES;
        assertEquals(48, g.ram(s));
        assertEquals(112, g.ram(s + 1));
        assertEquals(64, g.ram(s + 4));
        assertEquals(128, g.ram(s + 13));
        assertEquals(-19, g.ram(s + 14), "bottom-right: mirrored ...");
        assertEquals(-1, g.ram(s + 15), "... and flipped");
    }

    @Test
    void movesFollowTheRulesEverywhere() {
        // 300 random moves at random places, including all edges and corners;
        // after each, the game's row words and the screen must match the model.
        int[][] b = boardFromRam();
        Random rnd = new Random(85);
        for (int i = 0; i < 300; i++) {
            int x = rnd.nextInt(5);
            int y = rnd.nextInt(5);
            int op = rnd.nextInt(3) - 1;
            cursorTo(x, y);
            move(op);
            model(b, op, x, y);
            assertEquals(0, g.var("V_SOLVED"), "solved by chance at move " + i + " (pick another seed)");
            assertArrayEquals(words(b), ramWords(), "after move " + i + ": op " + op + " at " + x + "," + y);
            assertEquals(i + 1, g.var("V_MOVES"));
        }
        assertDrawn(b);
    }

    @Test
    void eachMoveWorksOnItsCross() {
        // From a known board: centre up, corner down, edge flip.
        int[][] b = new int[5][5];
        b[0][0] = 1;
        b[2][2] = -1;
        b[1][2] = 1;
        load(b);
        cursorTo(2, 2);
        move(1);
        model(b, 1, 2, 2);
        assertArrayEquals(words(b), ramWords());
        assertEquals(0, b[2][2], "-1 rotated up is 0");
        assertEquals(-1, b[1][2], "+1 rotated up is -1");
        assertEquals(1, b[2][1], "0 rotated up is +1");
        cursorTo(0, 0);
        move(-1);
        model(b, -1, 0, 0);
        assertArrayEquals(words(b), ramWords());
        assertEquals(0, b[0][0]);
        cursorTo(2, 0);
        move(0);
        model(b, 0, 2, 0);
        assertArrayEquals(words(b), ramWords());
        assertEquals(1, b[1][2], "flipped");
        assertDrawn(b);
        assertEquals(330, g.io(IoRegisters.V1_FREQ), "the flip clicks at 330 Hz");
    }

    /** Puts a board into the game's RAM (as if it were the level) and redraws through a no-op. */
    private void load(int[][] b) {
        int[] w = words(b);
        for (int r = 0; r < 5; r++) {
            g.poke(g.sym("board") + r, w[r]);
        }
    }

    @Test
    void solvingShowsTheMessageAndZGoesToTheNextLevel() {
        int[][] b = new int[5][5];
        model(b, 1, 3, 1);                          // one move away from solved
        load(b);
        cursorTo(3, 1);
        move(-1);
        assertEquals(1, g.var("V_SOLVED"));
        assertTrue(g.row(1).contains("SOLVED!  Z: NEXT LEVEL"));
        assertEquals(262, g.io(IoRegisters.V1_FREQ), "a C major chord");
        assertEquals(330, g.io(IoRegisters.V2_FREQ));
        assertEquals(392, g.io(IoRegisters.V3_FREQ));
        g.frames(1);
        assertEquals(12, g.io(IoRegisters.V1_VOL), "loud ... (50 / 4 = 12.5, a tie, rounds toward zero)");
        g.frames(60);
        assertEquals(0, g.io(IoRegisters.V1_VOL), "... and faded");
        push(1, 0);
        g.tap('B');
        assertEquals(1, g.var("V_LEVEL"), "nothing but Z works now");
        assertEquals(3, g.var("V_CX"));
        g.tap('A');
        assertEquals(2, g.var("V_LEVEL"));
        assertEquals(4, g.var("V_PAR"));
        assertEquals(0, g.var("V_MOVES"));
        assertEquals(0, g.var("V_SOLVED"));
        assertFalse(g.row(1).contains("SOLVED"));
        assertTrue(g.row(0).endsWith("LEVEL 2 "));
        assertDrawn(boardFromRam());
    }

    @Test
    void rStartsTheLevelAgain() {
        int[] start = ramWords();
        cursorTo(1, 1);
        move(1);
        move(0);
        assertEquals(2, g.var("V_MOVES"));
        g.m.input().pushKey('r');
        g.frames(1);
        assertArrayEquals(start, ramWords());
        assertEquals(0, g.var("V_MOVES"));
        assertEquals(2, g.var("V_CX"), "cursor back in the centre");
        assertDrawn(boardFromRam());
        g.m.input().pushKey('q');                   // other keys do nothing
        g.frames(1);
        assertArrayEquals(start, ramWords());
    }

    /**
     * The real thing: a search finds a solution within par for levels 1 and 2,
     * and it is played through the joystick and buttons. This also shows that
     * PAR (the number of scramble moves) is always reachable.
     */
    @Test
    void levelsOneAndTwoAreSolvedWithinPar() {
        for (int level = 1; level <= 2; level++) {
            int par = g.var("V_PAR");
            List<int[]> solution = solve(boardFromRam(), par);
            assertNotNull(solution, "level " + level + " has a solution within par " + par);
            for (int[] mv : solution) {
                cursorTo(mv[1], mv[2]);
                move(mv[0]);
            }
            assertEquals(1, g.var("V_SOLVED"), "level " + level + " solved");
            assertTrue(g.var("V_MOVES") <= par);
            assertArrayEquals(new int[5], ramWords());
            g.frames(60);
            g.tap('A');
        }
        assertEquals(3, g.var("V_LEVEL"));
    }

    /** Depth-first search for a sequence of at most {@code depth} moves that zeroes the board. */
    private static List<int[]> solve(int[][] b, int depth) {
        for (int d = 1; d <= depth; d++) {
            List<int[]> path = new ArrayList<>();
            if (dfs(b, d, path)) {
                return path;
            }
        }
        return null;
    }

    private static boolean dfs(int[][] b, int left, List<int[]> path) {
        if (isZero(b)) {
            return true;
        }
        if (left == 0) {
            return false;
        }
        for (int op = -1; op <= 1; op++) {
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    int[][] c = copy(b);
                    model(c, op, x, y);
                    path.add(new int[] {op, x, y});
                    if (dfs(c, left - 1, path)) {
                        return true;
                    }
                    path.remove(path.size() - 1);
                }
            }
        }
        return false;
    }

    private static boolean isZero(int[][] b) {
        for (int[] row : b) {
            for (int t : row) {
                if (t != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int[][] copy(int[][] b) {
        int[][] c = new int[5][];
        for (int r = 0; r < 5; r++) {
            c[r] = b[r].clone();
        }
        return c;
    }

    /** The smoke test: a minute of random button mashing never faults. */
    @Test
    void aMinuteOfButtonMashing() {
        Random rnd = new Random(3);
        for (int i = 0; i < 1200; i++) {
            g.joy(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1);
            g.m.input().setButtonA(rnd.nextInt(4) == 0);
            g.m.input().setButtonB(rnd.nextInt(4) == 0);
            g.m.input().setButtonC(rnd.nextInt(4) == 0);
            if (rnd.nextInt(50) == 0) {
                g.m.input().pushKey('R');
            }
            g.frames(3);
        }
        assertTrue(g.var("V_MOVES") >= 0);
        assertDrawn(boardFromRam());
    }

    @Test
    void goldenScreenOfLevelOne() throws Exception {
        g.assertGolden("troika-level1.png");
    }
}
