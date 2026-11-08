package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;

/**
 * {@code programs/oblaka.tasm}, the scrolling shooter of M10 (CLAUDE.md: "a
 * platformer or a shooter using scrolling and many sprites"). Played through
 * the joystick and button A; checked in the sprite table, the game's
 * variables, SCROLL_Y, the sound registers and the screen.
 */
class OblakaTest {

    private static final int PLAYER = 0;
    private static final int FIRST_SHOT = 1;
    private static final int FIRST_ENEMY = 5;
    private static final int FIRST_DIGIT = 17;
    private static final int FIRST_LIFE = 22;

    private GameHarness g;

    @BeforeEach
    void boot() {
        g = new GameHarness("oblaka");
    }

    private int sprite(int i, int field) {
        return g.ram(MemoryMap.SPRITES + 4 * i + field);
    }

    private void setSprite(int i, int x, int y, int tile, int flags) {
        int a = MemoryMap.SPRITES + 4 * i;
        g.poke(a, x);
        g.poke(a + 1, y);
        g.poke(a + 2, tile);
        g.poke(a + 3, flags);
    }

    private int enemyVar(String array, int i) {
        return g.ram(g.sym(array) + i);
    }

    /** Puts enemy {@code i} of {@code kind} at (x, y), alive and not drifting. */
    private void placeEnemy(int i, int x, int y, int kind) {
        setSprite(FIRST_ENEMY + i, x, y, g.sym("T_ENEMY") + kind, 1);
        g.poke(g.sym("e_kind") + i, kind);
        g.poke(g.sym("e_dx") + i, 0);
        g.poke(g.sym("e_boom") + i, 0);
    }

    private int activeShots() {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            n += sprite(FIRST_SHOT + i, 3) != 0 ? 1 : 0;
        }
        return n;
    }

    /** Starts a game; afterwards the game has run one frame. */
    private void start() {
        g.tap('A');
        assertEquals(1, g.var("V_PLAYING"));
    }

    /** A game with no enemies coming and the ship no longer invulnerable. */
    private void quietGame() {
        start();
        g.setVar("V_SPAWN", 100_000);
        g.setVar("V_INV", 0);
    }

    @Test
    void titleScreen() {
        assertEquals(0, g.var("V_PLAYING"));
        assertTrue(g.row(5).contains("O B L A K A"));
        assertTrue(g.row(8).contains("ARROWS: FLY   Z: FIRE"));
        assertTrue(g.row(11).contains("PRESS Z TO START"));
        assertEquals(0, g.io(IoRegisters.SCROLL_Y), "the sky stands still");
        for (int d = 0; d < 5; d++) {
            assertEquals(100, sprite(FIRST_DIGIT + d, 2), "score 00000");
            assertEquals(1, sprite(FIRST_DIGIT + d, 3));
        }
        assertEquals(0, sprite(PLAYER, 3), "no ship yet");
        g.frames(100);
        assertEquals(0, g.var("V_PLAYING"));
    }

    @Test
    void aGameStartsWithThreeShipsAndAScrollingSky() {
        start();
        assertEquals(104, sprite(PLAYER, 0));
        assertEquals(120, sprite(PLAYER, 1));
        assertEquals(g.sym("T_SHIP"), sprite(PLAYER, 2));
        for (int k = 0; k < 3; k++) {
            assertEquals(1, sprite(FIRST_LIFE + k, 3), "ship " + k + " left");
        }
        assertTrue(g.row(5).indexOf('O') < 0, "the title is gone");
        assertEquals(143, g.io(IoRegisters.SCROLL_Y), "0 - 1, wrapped into 0 ... 143");
        g.frames(10);
        assertEquals(133, g.io(IoRegisters.SCROLL_Y), "one pixel per frame");
        g.frames(133);
        assertEquals(0, g.io(IoRegisters.SCROLL_Y));
        g.frames(1);
        assertEquals(143, g.io(IoRegisters.SCROLL_Y));
    }

    @Test
    void theShipBlinksWhileInvulnerable() {
        start();
        Set<Integer> flags = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            g.frames(1);
            flags.add(sprite(PLAYER, 3));
        }
        assertEquals(Set.of(0, 1), flags);
        g.frames(20);
        for (int i = 0; i < 10; i++) {
            g.frames(1);
            assertEquals(1, sprite(PLAYER, 3), "steady after 60 frames");
        }
    }

    @Test
    void theJoystickFliesTwoPixelsPerFrameInsideTheLowerHalf() {
        quietGame();
        g.joy(1, -1);
        g.frames(10);
        assertEquals(124, sprite(PLAYER, 0));
        assertEquals(100, sprite(PLAYER, 1));
        g.frames(100);
        assertEquals(208, sprite(PLAYER, 0), "right edge");
        assertEquals(64, sprite(PLAYER, 1), "no higher than the middle");
        g.joy(-1, 1);
        g.frames(200);
        assertEquals(0, sprite(PLAYER, 0));
        assertEquals(132, sprite(PLAYER, 1));
    }

    @Test
    void holdingZFiresEveryEighthFrame() {
        quietGame();
        g.setButton('A', true);
        g.frames(1);
        assertEquals(1, activeShots());
        assertEquals(104, sprite(FIRST_SHOT, 0));
        assertEquals(110, sprite(FIRST_SHOT, 1), "made at y - 6, then moved 4 up");
        assertEquals(1, g.io(IoRegisters.V1_WAVE));
        assertEquals(1200, g.io(IoRegisters.V1_FREQ));
        assertEquals(8, g.io(IoRegisters.V1_VOL));
        g.frames(7);
        assertEquals(1, activeShots());
        assertEquals(82, sprite(FIRST_SHOT, 1));
        g.frames(1);
        assertEquals(2, activeShots(), "the second shot on frame 9");
        g.frames(16);
        assertEquals(4, activeShots());
        g.setButton('A', false);
        g.frames(40);
        assertEquals(0, activeShots(), "all gone above the screen");
        assertEquals(0, g.io(IoRegisters.V1_VOL), "and the sound faded");
    }

    @Test
    void enemiesAppearAtTheTopAndWobbleWithTrits() {
        start();
        Set<Integer> dxSeen = new HashSet<>();
        Set<Integer> kinds = new HashSet<>();
        boolean spawnedAtTop = false;
        for (int f = 0; f < 900; f++) {
            g.setVar("V_INV", 100);                 // keep the ship safe
            g.frames(1);
            for (int i = 0; i < 12; i++) {
                if (sprite(FIRST_ENEMY + i, 3) == 0 || enemyVar("e_boom", i) > 0) {
                    continue;
                }
                int kind = enemyVar("e_kind", i);
                kinds.add(kind);
                assertEquals(g.sym("T_ENEMY") + kind, sprite(FIRST_ENEMY + i, 2));
                dxSeen.add(enemyVar("e_dx", i));
                int x = sprite(FIRST_ENEMY + i, 0);
                assertTrue(x >= 0 && x <= 208, "x " + x);
                spawnedAtTop |= sprite(FIRST_ENEMY + i, 1) == -8;
            }
        }
        assertEquals(Set.of(-1, 0, 1), kinds, "all three kinds");
        assertEquals(Set.of(-1, 0, 1), dxSeen, "the wobble is a trit");
        assertTrue(spawnedAtTop);
    }

    @Test
    void enemiesMoveDownOneOrTwoPixels() {
        quietGame();
        placeEnemy(3, 20, 10, -1);
        placeEnemy(4, 100, 10, 1);
        g.frames(5);
        assertEquals(15, sprite(FIRST_ENEMY + 3, 1));
        assertEquals(20, sprite(FIRST_ENEMY + 4, 1), "the red kind is faster");
        placeEnemy(5, 50, 143, 0);
        g.frames(1);
        assertEquals(0, sprite(FIRST_ENEMY + 5, 3), "gone below the screen");
    }

    @Test
    void aShotDestroysAnEnemyForTenTimesKindPlusTwoPoints() {
        quietGame();
        placeEnemy(0, 104, 96, 1);
        g.setButton('A', true);
        g.frames(3);                                // shot 110, 106, 102; enemy 98, 100, 102
        g.setButton('A', false);
        assertEquals(g.sym("BOOM_TIME"), enemyVar("e_boom", 0));
        assertEquals(g.sym("T_BOOM"), sprite(FIRST_ENEMY, 2));
        assertEquals(30, g.var("V_SCORE"));
        assertEquals(0, sprite(FIRST_SHOT, 3), "the shot is used up");
        assertEquals(-1, g.io(IoRegisters.V2_WAVE), "noise");
        assertEquals(13, g.io(IoRegisters.V2_VOL));
        g.frames(1);
        assertEquals(103, sprite(FIRST_DIGIT + 3, 2), "score digits 00030");
        assertEquals(100, sprite(FIRST_DIGIT + 4, 2));
        g.frames(6);
        assertEquals(g.sym("T_BOOM") + 1, sprite(FIRST_ENEMY, 2), "second explosion frame");
        g.frames(5);
        assertEquals(0, sprite(FIRST_ENEMY, 3), "the explosion is over");
        placeEnemy(1, 104, 96, -1);
        g.setButton('A', true);
        g.frames(3);
        assertEquals(40, g.var("V_SCORE"), "+10 for the azure one");
        g.frames(20);
        placeEnemy(2, 104, 90, 0);
        g.frames(30);
        assertEquals(60, g.var("V_SCORE"), "+20 for the white one");
    }

    @Test
    void rammingAnEnemyCostsAShipAndGivesTwoSecondsOfSafety() {
        quietGame();
        placeEnemy(0, 104, 118, 0);
        g.frames(1);
        assertEquals(2, g.var("V_LIVES"));
        assertEquals(1, sprite(FIRST_LIFE + 1, 3));
        assertEquals(0, sprite(FIRST_LIFE + 2, 3), "one ship icon gone");
        assertEquals(120, g.var("V_INV"));
        assertEquals(-1, g.io(IoRegisters.V3_WAVE));
        assertEquals(90, g.io(IoRegisters.V3_FREQ));
        assertEquals(13, g.io(IoRegisters.V3_VOL), "loud at once");
        assertEquals(0, g.var("V_SCORE"), "ramming scores nothing");
        placeEnemy(1, 104, 118, 0);                 // invulnerable now
        g.frames(1);
        assertEquals(2, g.var("V_LIVES"));
    }

    @Test
    void losingTheLastShipEndsTheGameAndZRestartsIt() {
        quietGame();
        g.setVar("V_SCORE", 250);
        g.setVar("V_LIVES", 1);
        placeEnemy(0, 104, 118, 0);
        g.frames(1);
        assertEquals(0, g.var("V_PLAYING"));
        assertEquals(250, g.var("V_HI"));
        assertTrue(g.row(6).contains("GAME OVER"));
        assertTrue(g.row(8).contains("HI 250"));
        assertTrue(g.row(11).contains("PRESS Z"));
        assertEquals(0, g.io(IoRegisters.SCROLL_Y));
        assertEquals(0, sprite(PLAYER, 3));
        for (int i = FIRST_SHOT; i < FIRST_DIGIT; i++) {
            assertEquals(0, sprite(i, 3), "sprite " + i + " hidden");
        }
        g.tap('A');
        assertEquals(0, g.var("V_PLAYING"), "not at once");
        g.frames(60);
        g.tap('A');
        assertEquals(1, g.var("V_PLAYING"));
        assertEquals(0, g.var("V_SCORE"));
        assertEquals(3, g.var("V_LIVES"));
        assertEquals(250, g.var("V_HI"), "the hi-score stays");
    }

    @Test
    void theScoreShowsFiveDigits() {
        quietGame();
        g.setVar("V_SCORE", 12345);
        g.frames(1);
        for (int d = 0; d < 5; d++) {
            assertEquals(101 + d, sprite(FIRST_DIGIT + d, 2));
        }
        g.setVar("V_SCORE", 90817);                 // digits that round up in DIV
        g.frames(1);
        int[] want = {9, 0, 8, 1, 7};
        for (int d = 0; d < 5; d++) {
            assertEquals(100 + want[d], sprite(FIRST_DIGIT + d, 2));
        }
    }

    /**
     * The smoke test: an autopilot plays for two minutes (restarting after
     * GAME OVER): it shoots, gets hit, and the game never faults.
     */
    @Test
    void anAutopilotPlaysForTwoMinutes() {
        int games = 0;
        int best = 0;
        int mostSprites = 0;
        for (int f = 0; f < 7200; f++) {
            if (g.var("V_PLAYING") == 0) {
                g.setButton('A', false);
                g.frames(61);
                g.tap('A');
                games++;
            }
            autopilot();
            g.frames(1);
            best = Math.max(best, g.var("V_SCORE"));
            int visible = 0;
            for (int i = 0; i < 27; i++) {
                visible += sprite(i, 3) != 0 ? 1 : 0;
            }
            mostSprites = Math.max(mostSprites, visible);
        }
        assertTrue(best >= 300, "the autopilot should shoot a fair amount, best " + best);
        assertTrue(mostSprites >= 16, "many sprites at once: " + mostSprites);
        System.out.println("OBLAKA autopilot: " + games + " games, best score " + best
                + ", up to " + mostSprites + " sprites at once");
    }

    /** Fires all the time, steers under the lowest enemy, and keeps away from close ones. */
    private void autopilot() {
        g.setButton('A', true);
        int px = sprite(PLAYER, 0);
        int py = sprite(PLAYER, 1);
        int target = -1;
        int targetY = -100;
        int dodge = 0;
        for (int i = 0; i < 12; i++) {
            if (sprite(FIRST_ENEMY + i, 3) == 0 || enemyVar("e_boom", i) > 0) {
                continue;
            }
            int ex = sprite(FIRST_ENEMY + i, 0);
            int ey = sprite(FIRST_ENEMY + i, 1);
            if (ey > py - 24 && ey < py + 8 && Math.abs(ex - px) < 12) {
                dodge = ex >= px ? -1 : 1;
            }
            if (ey < py - 24 && ey > targetY) {
                targetY = ey;
                target = ex;
            }
        }
        int dx = dodge != 0 ? dodge : target < 0 ? 0 : Integer.signum(target - px);
        g.joy(dx, 1);
    }

    @Test
    void goldenScreenInFlight() throws Exception {
        start();
        for (int f = 0; f < 150; f++) {
            autopilot();
            g.frames(1);
        }
        g.assertGolden("oblaka-flight.png");
    }
}
