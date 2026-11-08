package org.triada.rom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.TriImage;
import org.triada.core.triskette.MemoryTriskette;

/**
 * DISKSAVE, DISKLOAD, DISKDIR and DISKDEL (SPEC §8.5, M13), called from a
 * TRIADA program the way any program would call them. The program writes
 * every result into RAM at 30000, and checks after every call that R3 … R7
 * still hold what it put there (a 1 in the result list means "kept").
 */
class DiskRoutinesTest {

    private static final int OUT = 30_000;

    private static Triada run(MemoryTriskette disk, String body) {
        Triada t = new Triada();
        t.m.triskette().setPort(disk);
        Rom.autorun(t.m, Triada.assemble("""
                        LDI R3, 333
                        LDI R4, 444
                        LDI R5, 555
                        LDI R6, 666
                        LDI R7, OUT
                """.replace("OUT", Integer.toString(OUT)) + body + """
                        RET
                kept:   PUSH R0                 ; stores 1 at [R7] if R3 ... R6 survived, then R7++
                        LDI R0, 0
                        LDI R1, 333
                        CMP R3, R1
                        JNZ k_no
                        LDI R1, 444
                        CMP R4, R1
                        JNZ k_no
                        LDI R1, 555
                        CMP R5, R1
                        JNZ k_no
                        LDI R1, 666
                        CMP R6, R1
                        JNZ k_no
                        LDI R0, 1
                k_no:   ST R0, [R7]
                        INC R7
                        POP R0
                        RET
                name:   .str "notes.tb"
                bad:    .str "no good"
                data:   .word 10, -20, 30
                """));
        t.waitForPrompt();
        return t;
    }

    private static int[] out(Triada t, int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            r[i] = t.m.bus().read(OUT + i);
        }
        return r;
    }

    @Test
    void saveThenLoadToAnAddressThenLoadHome() {
        MemoryTriskette disk = new MemoryTriskette();
        Triada t = run(disk, """
                        LDI R0, name
                        LDI R1, data
                        LDI R2, 3
                        JSR DISKSAVE
                        ST R0, [R7]
                        INC R7
                        JSR kept
                        LDI R0, name
                        LDI R1, 31000
                        JSR DISKLOAD
                        ST R0, [R7+0]
                        ST R1, [R7+1]
                        ST R2, [R7+2]
                        ADDI R7, 3
                        JSR kept
                        LDI R0, name
                        LDI R1, -1
                        JSR DISKLOAD
                        ST R1, [R7+0]
                        ST R2, [R7+1]
                        ADDI R7, 2
                """);
        TriImage saved = disk.read("NOTES.TB");
        assertArrayEquals(new int[] {10, -20, 30}, saved.words());
        int data = saved.loadAddress();
        assertArrayEquals(new int[] {0, 1, 0, 31_000, 3, 1, data, 3}, out(t, 8));
        assertEquals(-20, t.m.bus().read(31_001), "loaded to 31000");
    }

    @Test
    void dirAndDelete() {
        MemoryTriskette disk = new MemoryTriskette();
        disk.write("A", new TriImage(0, new int[0]));
        disk.write("NOTES.TB", new TriImage(0, new int[0]));
        Triada t = run(disk, """
                        LDI R0, 32000
                        LDI R1, 100
                        JSR DISKDIR
                        ST R0, [R7+0]
                        ST R1, [R7+1]
                        ADDI R7, 2
                        JSR kept
                        LDI R0, 32000
                        LDI R1, 3
                        JSR DISKDIR
                        ST R0, [R7+0]
                        ST R1, [R7+1]
                        ADDI R7, 2
                        LDI R0, name
                        JSR DISKDEL
                        ST R0, [R7]
                        INC R7
                        JSR kept
                        LDI R0, name
                        JSR DISKDEL
                        ST R0, [R7]
                        INC R7
                """);
        assertArrayEquals(new int[] {0, 12, 1, -2, 12, 0, 1, -1}, out(t, 8));
        assertEquals('N', t.m.bus().read(32_002), "\"A\", 0, then NOTES.TB");
        assertNull(disk.read("NOTES.TB"));
        assertEquals(List.of("A"), disk.names());
    }

    @Test
    void theStatusIsAlsoInTheSignFlagForAQuickTest() {
        Triada t = run(new MemoryTriskette(), """
                        LDI R0, bad
                        LDI R1, data
                        LDI R2, 3
                        JSR DISKSAVE
                        JZ s_ok                 ; S = sign of the status
                        ST R0, [R7]
                        JMP s_end
                s_ok:   LDI R0, 99
                        ST R0, [R7]
                s_end:  INC R7
                        JSR kept
                """);
        assertArrayEquals(new int[] {-3, 1}, out(t, 2), "bad name, and S said so");
    }
}
