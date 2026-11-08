package org.triada.core.triskette;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.triada.core.io.IoRegisters.TS_ADDR;
import static org.triada.core.io.IoRegisters.TS_CMD;
import static org.triada.core.io.IoRegisters.TS_LEN;
import static org.triada.core.io.IoRegisters.TS_NAME;
import static org.triada.core.io.IoRegisters.TS_STATUS;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.TriImage;
import org.triada.core.memory.MemoryMap;

/** Every command and every status of the triskette drive (SPEC §5.1), driven through its I/O registers. */
class TrisketteDeviceTest {

    private static final int NAME_AT = 1000;
    private static final int DATA_AT = 2000;

    private final Machine m = new Machine();
    private final MemoryTriskette disk = new MemoryTriskette();

    TrisketteDeviceTest() {
        m.triskette().setPort(disk);
    }

    private int reg(int k) {
        return m.bus().read(MemoryMap.IO_BASE + k);
    }

    private void set(int k, int v) {
        m.bus().write(MemoryMap.IO_BASE + k, v);
    }

    private void putString(int at, String s) {
        for (int i = 0; i < s.length(); i++) {
            m.bus().write(at + i, s.charAt(i));
        }
        m.bus().write(at + s.length(), 0);
    }

    /** Runs a command on the file {@code name}; returns TS_STATUS. */
    private int command(int cmd, String name, int addr, int len) {
        putString(NAME_AT, name);
        set(TS_NAME, NAME_AT);
        set(TS_ADDR, addr);
        set(TS_LEN, len);
        set(TS_CMD, cmd);
        return reg(TS_STATUS);
    }

    @Test
    void saveWritesTheWordsAndTheirAddress() {
        for (int i = 0; i < 4; i++) {
            m.bus().write(DATA_AT + i, -i * 80_000);
        }
        assertEquals(0, command(TrisketteDevice.CMD_SAVE, "data.tri", DATA_AT, 4));
        TriImage f = disk.read("DATA.TRI");
        assertEquals(DATA_AT, f.loadAddress());
        assertArrayEquals(new int[] {0, -80_000, -160_000, -240_000}, f.words());
        assertEquals(List.of("DATA.TRI"), disk.names(), "lower case becomes upper case");
    }

    @Test
    void saveOverwritesAndAcceptsAnEmptyFile() {
        disk.write("A", new TriImage(5, new int[] {1, 2, 3}));
        assertEquals(0, command(TrisketteDevice.CMD_SAVE, "A", 7, 0));
        assertEquals(0, disk.read("A").size());
        assertEquals(7, disk.read("A").loadAddress());
    }

    @Test
    void loadCopiesToTsAddrAndReportsTheLength() {
        disk.write("GAME.TRI", new TriImage(243, new int[] {11, 22, 33}));
        assertEquals(0, command(TrisketteDevice.CMD_LOAD, "GAME.TRI", 5000, 99));
        assertEquals(3, reg(TS_LEN));
        assertEquals(5000, reg(TS_ADDR), "plain LOAD keeps TS_ADDR");
        assertEquals(22, m.bus().read(5001));
        assertEquals(0, m.bus().read(243), "not to its own address");
    }

    @Test
    void loadHomeUsesTheFilesOwnAddress() {
        disk.write("GAME.TRI", new TriImage(300, new int[] {7, 8}));
        assertEquals(0, command(TrisketteDevice.CMD_LOAD_HOME, "game.tri", 0, 0));
        assertEquals(300, reg(TS_ADDR));
        assertEquals(2, reg(TS_LEN));
        assertEquals(8, m.bus().read(301));
    }

    @Test
    void dirListsNamesThenAnExtraZero() {
        disk.write("B.TB", new TriImage(0, new int[0]));
        disk.write("A1", new TriImage(0, new int[0]));
        set(TS_ADDR, DATA_AT);
        set(TS_LEN, 100);
        set(TS_CMD, TrisketteDevice.CMD_DIR);
        assertEquals(0, reg(TS_STATUS));
        int[] expected = {'A', '1', 0, 'B', '.', 'T', 'B', 0, 0};
        assertEquals(expected.length, reg(TS_LEN), "words used");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], m.bus().read(DATA_AT + i), "word " + i);
        }
    }

    @Test
    void dirOfAnEmptyTrisketteIsOneZero() {
        m.bus().write(DATA_AT, 99);
        set(TS_ADDR, DATA_AT);
        set(TS_LEN, 1);
        set(TS_CMD, TrisketteDevice.CMD_DIR);
        assertEquals(0, reg(TS_STATUS));
        assertEquals(1, reg(TS_LEN));
        assertEquals(0, m.bus().read(DATA_AT));
    }

    @Test
    void dirWithTooLittleRoomWritesNothingAndSaysHowMuchIsNeeded() {
        disk.write("ABC", new TriImage(0, new int[0]));
        m.bus().write(DATA_AT, 99);
        set(TS_ADDR, DATA_AT);
        set(TS_LEN, 4);
        set(TS_CMD, TrisketteDevice.CMD_DIR);
        assertEquals(TrisketteDevice.NO_SPACE, reg(TS_STATUS));
        assertEquals(5, reg(TS_LEN), "\"ABC\", 0, 0");
        assertEquals(99, m.bus().read(DATA_AT));
    }

    @Test
    void deleteRemovesTheFile() {
        disk.write("OLD.TB", new TriImage(0, new int[] {1}));
        assertEquals(0, command(TrisketteDevice.CMD_DELETE, "OLD.TB", 0, 0));
        assertNull(disk.read("OLD.TB"));
        assertEquals(TrisketteDevice.NOT_FOUND, command(TrisketteDevice.CMD_DELETE, "OLD.TB", 0, 0));
    }

    @Test
    void notFound() {
        assertEquals(-1, command(TrisketteDevice.CMD_LOAD, "NOPE", DATA_AT, 0));
        assertEquals(-1, command(TrisketteDevice.CMD_LOAD_HOME, "NOPE", DATA_AT, 0));
        assertEquals(-1, command(TrisketteDevice.CMD_DELETE, "NOPE", 0, 0));
    }

    @Test
    void noSpace() {
        m.triskette().setPort(new MemoryTriskette(10));
        assertEquals(0, command(TrisketteDevice.CMD_SAVE, "A", DATA_AT, 10));
        assertEquals(-2, command(TrisketteDevice.CMD_SAVE, "B", DATA_AT, 1));
        assertEquals(0, command(TrisketteDevice.CMD_SAVE, "A", DATA_AT, 10), "replacing a file frees its space");
    }

    @Test
    void badNames() {
        for (String bad : new String[] {"", "A.B.C", "THIRTEEN.CHAR", "SPACE X", "MINUS-1", "Ä"}) {
            assertEquals(-3, command(TrisketteDevice.CMD_SAVE, bad, DATA_AT, 1), "\"" + bad + "\"");
        }
        assertEquals(0, command(TrisketteDevice.CMD_SAVE, "TWELVE.CHARS", DATA_AT, 1), "12 is allowed");
        // a name without its closing 0 within 13 words
        for (int i = 0; i < 20; i++) {
            m.bus().write(NAME_AT + i, 'A');
        }
        set(TS_NAME, NAME_AT);
        set(TS_CMD, TrisketteDevice.CMD_LOAD);
        assertEquals(-3, reg(TS_STATUS));
    }

    @Test
    void ioErrors() {
        assertEquals(-4, command(TrisketteDevice.CMD_SAVE, "X", 59_000, 100), "SAVE past the end of RAM");
        assertEquals(-4, command(TrisketteDevice.CMD_SAVE, "X", -5, 3), "SAVE from outside RAM");
        assertEquals(-4, command(TrisketteDevice.CMD_SAVE, "X", DATA_AT, -1), "negative length");
        assertEquals(-4, command(9, "X", DATA_AT, 1), "no such command");

        disk.write("BIG", new TriImage(0, new int[] {1, 2, 3}));
        assertEquals(-4, command(TrisketteDevice.CMD_LOAD, "BIG", 59_047, 0), "LOAD clipped at the end of RAM");
        assertEquals(1, m.bus().read(59_047), "what fits is still written");
        assertEquals(2, m.bus().read(59_048));
        assertEquals(3, reg(TS_LEN));

        m.triskette().setPort(new TriskettePort() {
            @Override
            public List<String> names() {
                return List.of();
            }

            @Override
            public TriImage read(String name) {
                throw new TrisketteException(TrisketteDevice.IO_ERROR, "the host's disk is gone");
            }

            @Override
            public void write(String name, TriImage file) {
            }

            @Override
            public boolean delete(String name) {
                return false;
            }
        });
        assertEquals(-4, command(TrisketteDevice.CMD_LOAD, "BIG", DATA_AT, 0), "a failing host");
    }

    @Test
    void registersReadBackAndPowerOnClearsThemButNotTheFiles() {
        set(TS_NAME, 1);
        set(TS_ADDR, 2);
        set(TS_LEN, 3);
        set(TS_STATUS, 4);
        assertEquals(1, reg(TS_NAME));
        assertEquals(2, reg(TS_ADDR));
        assertEquals(3, reg(TS_LEN));
        assertEquals(0, reg(TS_STATUS), "read-only");
        assertEquals(0, reg(TS_CMD), "write-only");
        disk.write("KEEP", new TriImage(0, new int[0]));
        m.powerOn();
        assertEquals(0, reg(TS_NAME) + reg(TS_ADDR) + reg(TS_LEN));
        assertEquals(List.of("KEEP"), m.triskette().port().names());
    }

    @Test
    void namesFollowTheRules() {
        assertEquals("GAME.TRI", TrisketteNames.normalize("game.tri"));
        assertEquals("NODOT", TrisketteNames.normalize("NoDot"));
        assertNull(TrisketteNames.normalize("A..B"));
        assertNull(TrisketteNames.normalize("ABCDEFGHIJKLM"));
        assertEquals("TRIZMEYK.TRI", TrisketteNames.forHostFile("programs/trizmeyka.tasm"));
        assertEquals("OBLAKA.TRI", TrisketteNames.forHostFile("C:\\games\\oblaka.tri"));
        assertEquals("MYPROG.TB", TrisketteNames.forHostFile("my-prog.tb"));
        assertEquals("README", TrisketteNames.forHostFile("readme"));
        assertEquals("FILE.TRI", TrisketteNames.forHostFile("___.tri"));
        assertEquals("LONGNAM.TEXT".length(), TrisketteNames.forHostFile("long name.textfile").length());
    }
}
