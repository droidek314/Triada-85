package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;

/**
 * {@code programs/trizmeyka.tasm}, the snake game of M10 (CLAUDE.md: "tiles,
 * joystick, score, sound"). Played through the joystick and button A; checked
 * in the tile map, the game's variables, the sound registers and the screen.
 */
class TrizmeykaTest {

    private static final int FLOOR = 1;
    private static final int WALL = 2;
    private static final int BODY = 3;
    private static final int HEAD = 4;

    private GameHarness g;

    @BeforeEach
    void boot() {
        g = new GameHarness("trizmeyka");
    }

    /** Starts a game: after this the snake makes its first step in 5 frames. */
    private void start() {
        g.tap('A');
    }

    /** The map cell (col + 27 * row) of the snake's head. */
    private int head() {
        return g.ram(g.sym("BODY") + g.var("V_HEAD")) - MemoryMap.MAP;
    }

    private int length() {
        int n = g.var("V_HEAD") - g.var("V_TAIL") + 1;
        return n > 0 ? n : n + g.sym("RING");
    }

    /** Runs exactly enough frames for {@code n} snake steps. */
    private void steps(int n) {
        g.frames(n * g.sym("STEP"));
    }

    private int countTiles(int lo, int hi) {
        int n = 0;
        for (int i = 27; i < 486; i++) {
            int t = g.ram(MemoryMap.MAP + i);
            if (t >= lo && t <= hi) {
                n++;
            }
        }
        return n;
    }

    @Test
    void titleScreenWaitsForButtonA() {
        assertEquals("TRIZMEYKA  SCORE 0   HI 0  ", g.row(0));
        assertTrue(g.row(9).contains("PRESS Z TO START"));
        assertEquals(WALL, g.cell(0, 1));
        assertEquals(WALL, g.cell(26, 17));
        assertEquals(WALL, g.cell(0, 10));
        assertEquals(FLOOR, g.cell(1, 2));
        g.joy(1, 0);
        g.frames(120);                              // the joystick alone starts nothing
        assertEquals(0, g.var("V_ALIVE"));
        assertTrue(g.row(9).contains("PRESS Z TO START"));
    }

    @Test
    void holdingButtonAStartsOnlyOneGame() {
        g.setButton('A', true);
        g.frames(3);
        assertEquals(1, g.var("V_ALIVE"));
        assertEquals(3, length());
    }

    @Test
    void aNewGameHasAThreeSegmentSnakeAndOneFood() {
        start();
        assertEquals(1, g.var("V_ALIVE"));
        assertEquals(BODY, g.cell(5, 9));
        assertEquals(BODY, g.cell(6, 9));
        assertEquals(HEAD, g.cell(7, 9));
        assertEquals(1, countTiles(HEAD, HEAD));
        assertEquals(1, countTiles(5, 7), "exactly one food");
        int food = g.var("V_FOOD");
        assertTrue(food >= -1 && food <= 1, "the food is a trit: " + food);
        assertEquals(6 + food, countFoodTile());
        assertEquals("TRIZMEYKA  SCORE 0   HI 0  ", g.row(0));
        assertTrue(g.row(9).indexOf("PRESS") < 0, "the start text is gone");
    }

    private int countFoodTile() {
        for (int i = 27; i < 486; i++) {
            int t = g.ram(MemoryMap.MAP + i);
            if (t >= 5 && t <= 7) {
                return t;
            }
        }
        return 0;
    }

    @Test
    void theSnakeStepsOnceEverySixFrames() {
        start();
        g.frames(4);
        assertEquals(7 + 9 * 27, head(), "not yet");
        g.frames(1);
        assertEquals(8 + 9 * 27, head(), "one step right");
        assertEquals(FLOOR, g.cell(5, 9), "the tail moved on");
        assertEquals(BODY, g.cell(7, 9));
        assertEquals(HEAD, g.cell(8, 9));
        steps(2);
        assertEquals(10 + 9 * 27, head());
        assertEquals(3, length());
    }

    @Test
    void theJoystickSteersButCannotReverse() {
        start();
        g.frames(5);                                // head at (8, 9), heading right
        g.joy(-1, 0);                               // straight back: ignored
        steps(1);
        assertEquals(9 + 9 * 27, head());
        g.joy(0, 1);                                // down
        steps(1);
        assertEquals(9 + 10 * 27, head());
        g.joy(0, 0);                                // released: keeps going down
        steps(1);
        assertEquals(9 + 11 * 27, head());
        g.joy(0, -1);                               // up = reverse: ignored
        steps(1);
        assertEquals(9 + 12 * 27, head());
        g.joy(-1, 0);                               // left
        steps(1);
        assertEquals(8 + 12 * 27, head());
    }

    /** Puts food of trit {@code trit} two cells in front of the head (and removes the old food). */
    private void foodAhead(int trit) {
        for (int i = 27; i < 486; i++) {
            int t = g.ram(MemoryMap.MAP + i);
            if (t >= 5 && t <= 7) {
                g.poke(MemoryMap.MAP + i, FLOOR);
            }
        }
        g.poke(MemoryMap.MAP + head() + 2, 6 + trit);
        g.setVar("V_FOOD", trit);
    }

    @Test
    void foodIsWorthItsTritPlusTwoPointsAndSegments() {
        start();
        int[] gains = {1, 2, 3};
        int score = 0;
        int length = 3;
        for (int trit = -1; trit <= 1; trit++) {
            foodAhead(trit);
            g.frames(g.var("V_TIMER"));             // to the next step: one cell before the food
            steps(1);                               // eat it
            int gain = gains[trit + 1];
            score += gain;
            assertEquals(score, g.var("V_SCORE"));
            assertEquals(440 + 220 * gain, g.io(IoRegisters.V1_FREQ), "eat sound pitch");
            assertEquals(1, g.io(IoRegisters.V1_WAVE));
            assertTrue(g.io(IoRegisters.V1_VOL) > 0);
            assertEquals(1, countTiles(5, 7), "new food placed");
            steps(gain);                            // the snake grows one segment per step
            length += gain;
            assertEquals(length, length());
            steps(1);
            assertEquals(length, length(), "and then stops growing");
        }
        assertEquals("TRIZMEYKA  SCORE 6   HI 0  ", g.row(0));
    }

    @Test
    void theEatSoundFadesOut() {
        start();
        foodAhead(0);
        g.frames(g.var("V_TIMER"));
        steps(1);
        int v = g.io(IoRegisters.V1_VOL);
        g.frames(3);
        assertEquals(v - 3, g.io(IoRegisters.V1_VOL));
        g.frames(20);
        assertEquals(0, g.io(IoRegisters.V1_VOL));
    }

    @Test
    void hittingTheWallEndsTheGame() {
        start();
        g.frames(5 + 17 * 6);                       // head from column 8 ... to 25 (beside the wall)
        assertEquals(25 + 9 * 27, head());
        assertEquals(1, g.var("V_ALIVE"));
        steps(1);
        assertEquals(0, g.var("V_ALIVE"), "crashed into column 26");
        assertEquals(25 + 9 * 27, head(), "the head stays where it was");
        assertEquals(WALL, g.cell(26, 9), "the wall is unharmed");
        assertTrue(g.row(8).contains("GAME OVER"));
        assertTrue(g.row(10).contains("PRESS Z"));
        assertEquals(-1, g.io(IoRegisters.V2_WAVE), "crash sound: noise");
        assertTrue(g.io(IoRegisters.V2_VOL) > 0);
        g.frames(200);
        assertEquals(0, g.var("V_ALIVE"), "stays over until Z");
        assertEquals(0, g.io(IoRegisters.V2_VOL), "the crash faded");
    }

    @Test
    void hittingItsOwnBodyEndsTheGame() {
        start();
        g.frames(5);
        g.poke(MemoryMap.MAP + head() + 2, BODY);   // body two cells ahead
        steps(1);
        assertEquals(1, g.var("V_ALIVE"));
        steps(1);
        assertEquals(0, g.var("V_ALIVE"));
    }

    @Test
    void theHiScoreIsKeptAndZStartsAgain() {
        start();
        foodAhead(1);
        g.frames(g.var("V_TIMER"));
        steps(1);
        g.frames(18 * 6);                           // into the wall
        assertEquals(0, g.var("V_ALIVE"));
        assertEquals(3, g.var("V_HI"));
        assertEquals("TRIZMEYKA  SCORE 3   HI 3  ", g.row(0));
        start();
        assertEquals(1, g.var("V_ALIVE"));
        assertEquals("TRIZMEYKA  SCORE 0   HI 3  ", g.row(0));
        assertTrue(g.row(8).indexOf("GAME") < 0, "the field is clean again");
        assertEquals(3, length());
        // A lower score doesn't replace the hi-score.
        g.frames(5 + 18 * 6);
        assertEquals(0, g.var("V_ALIVE"));
        assertEquals(3, g.var("V_HI"));
    }

    /**
     * The smoke test: a simple autopilot plays for two minutes of game time
     * (restarting with Z if it crashes): food everywhere, a long snake, the ring
     * buffer wrapping around, and the game never faults.
     */
    @Test
    void anAutopilotPlaysForTwoMinutes() {
        int games = 0;
        int best = 0;
        boolean wrapped = false;
        for (int frame = 0; frame < 7200; frame++) {
            if (g.var("V_ALIVE") == 0) {
                g.tap('A');
                games++;
            }
            autopilot();
            int before = g.var("V_TAIL");
            g.frames(1);
            wrapped |= g.var("V_TAIL") == 0 && before == g.sym("RING") - 1;
            if (g.var("V_ALIVE") == 1) {
                assertEquals(length(), countTiles(BODY, HEAD), "every ring entry is on the map, nothing else");
            }
            best = Math.max(best, g.var("V_SCORE"));
        }
        assertTrue(best >= 30, "the autopilot should eat a fair amount, best " + best);
        assertTrue(wrapped, "the tail (and so the head) wrapped around the ring buffer");
        assertTrue(g.var("V_HI") >= best || g.var("V_ALIVE") == 1, "the best finished game is the hi-score");
        System.out.println("TRIZMEYKA autopilot: " + games + " games, best score " + best);
    }

    private void autopilot() {
        autopilot(g);
    }

    /**
     * Heads for the food, never into something solid, never straight back.
     * Static so that DocImagesTest can use it for the README's picture.
     */
    static void autopilot(GameHarness g) {
        int head = g.ram(g.sym("BODY") + g.var("V_HEAD")) - MemoryMap.MAP;
        int food = -1;
        for (int i = 27; i < 486; i++) {
            int t = g.ram(MemoryMap.MAP + i);
            if (t >= 5 && t <= 7) {
                food = i;
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int bestScore = Integer.MAX_VALUE;
        int[] best = {0, 0};
        for (int[] d : dirs) {
            int next = head + d[0] + 27 * d[1];
            if (d[0] + 27 * d[1] + g.var("V_DELTA") == 0) {
                continue;
            }
            int t = g.ram(MemoryMap.MAP + next);
            if (t != FLOOR && (t < 5 || t > 7)) {
                continue;
            }
            int dist = Math.abs(next % 27 - food % 27) + Math.abs(next / 27 - food / 27);
            int free = 0;                            // prefer cells with room around them
            for (int[] e : dirs) {
                int u = g.ram(MemoryMap.MAP + next + e[0] + 27 * e[1]);
                if (u == FLOOR || (u >= 5 && u <= 7)) {
                    free++;
                }
            }
            int score = dist + (4 - free) * 3;
            if (score < bestScore) {
                bestScore = score;
                best = d;
            }
        }
        g.joy(best[0], best[1]);
    }

    /** A golden screenshot: a game in progress after the snake has eaten twice. */
    @Test
    void goldenScreenInPlay() throws Exception {
        start();
        foodAhead(1);
        g.frames(g.var("V_TIMER"));
        steps(1);
        g.joy(0, 1);
        steps(3);
        g.joy(1, 0);
        foodAhead(-1);
        steps(4);
        g.joy(0, -1);
        steps(5);
        g.assertGolden("trizmeyka-play.png");
    }
}
