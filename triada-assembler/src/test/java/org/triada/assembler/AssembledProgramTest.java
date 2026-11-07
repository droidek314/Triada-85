package org.triada.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.TriImage;
import org.triada.core.ternary.Word;

/**
 * CPU tests written in TRIADA assembly (CLAUDE.md M4: "From now on, CPU tests
 * should also be written in assembly where that's clearer"). Each program is
 * assembled, loaded at 243, run until it halts, and its results are checked
 * in registers or memory.
 */
class AssembledProgramTest {

    private Machine run(String source) {
        TriImage img = AssemblerTest.asm(source).image();
        Machine m = new Machine();
        m.bus().loadRam(img.loadAddress(), img.words());
        m.cpu().setPc(img.loadAddress());
        m.cpu().setRegister(8, 59_049);              // what the ROM will do (SPEC §8.2)
        for (int frame = 0; frame < 600 && !m.cpu().isHalted(); frame++) {
            m.runFrame();
        }
        assertTrue(m.cpu().isHalted(), "program did not halt within 10 seconds of machine time");
        assertNull(m.cpu().fault(), "program faulted");
        return m;
    }

    @Test
    void sumOfOneToHundred() {
        Machine m = run("""
                        LDI R0, 0           ; sum
                        LDI R1, 100         ; counter
                loop:   ADD R0, R1
                        DEC R1
                        JNZ loop
                        HLT
                """);
        assertEquals(5050, m.cpu().register(0));
    }

    @Test
    void recursiveFactorialUsesTheStack() {
        Machine m = run("""
                        LDI R0, 7
                        JSR fact
                        HLT
                ; fact: R0 = R0!  (recursive; keeps R1 safe on the stack)
                fact:   PUSH R1
                        MOV R1, R0
                        ADDI R1, -1
                        JLE base            ; n - 1 <= 0  ->  n! = n (for n = 1)
                        PUSH R0
                        MOV R0, R1
                        JSR fact            ; R0 = (n-1)!
                        POP R1              ; R1 = n
                        MUL R0, R1
                base:   POP R1
                        RET
                """);
        assertEquals(5040, m.cpu().register(0));
        assertEquals(59_049, m.cpu().register(8), "stack is balanced again");
    }

    @Test
    void threeWayClassificationWithCmpAndBr3() {
        // Count negative, zero and positive numbers of a table into three cells.
        Machine m = run("""
                        LDI R4, 1000        ; the three counters: 1000, 1001, 1002
                        LDI R1, table
                        LDI R2, 0           ; the value 0, for CMP
                next:   LD R0, [R1]
                        CMP R0, R2
                        BR3 neg, zero, pos
                neg:    LD R3, [R4+0]
                        INC R3
                        ST R3, [R4+0]
                        JMP step
                zero:   LD R3, [R4+1]
                        INC R3
                        ST R3, [R4+1]
                        JMP step
                pos:    LD R3, [R4+2]
                        INC R3
                        ST R3, [R4+2]
                step:   INC R1
                        LDI R5, end
                        CMP R1, R5
                        JN next
                        HLT
                table:  .word -5, 0, 7, -265720, 3, 0, 1, 265720, -1
                end:
                """);
        assertEquals(3, m.bus().read(1000), "negatives");
        assertEquals(2, m.bus().read(1001), "zeros");
        assertEquals(4, m.bus().read(1002), "positives");
    }

    @Test
    void stringLengthWalksAZeroTerminatedString() {
        Machine m = run("""
                        LDI R1, msg
                        LDI R0, 0
                loop:   LD R2, [R1]
                        JZ done             ; LD sets S: zero means the terminator
                        INC R0
                        INC R1
                        JMP loop
                done:   HLT
                msg:    .str "TRIT & TRYTE"
                """);
        assertEquals(12, m.cpu().register(0));
    }

    @Test
    void modThreeGivesTheLowestTritDirectly() {
        // Balanced ternary magic: n MOD 3 is already −1, 0 or +1, the low trit,
        // and n DIV 3 (rounded) removes it. Write all 12 trits of n to memory.
        int n = Word.parse("+-0+--0+0-+-");
        Machine m = run("""
                        LDI R0, %d
                        LDI R1, 2000        ; where trit 0 goes
                        LDI R2, 3
                        LDI R4, 12          ; trits to do
                loop:   MOV R3, R0
                        MOD R3, R2          ; R3 = low trit
                        ST R3, [R1]
                        DIV R0, R2          ; drop it
                        INC R1
                        DEC R4
                        JNZ loop
                        HLT
                """.formatted(n));
        int[] trits = Word.toTrits(n);
        for (int i = 0; i < 12; i++) {
            assertEquals(trits[i], m.bus().read(2000 + i), "trit " + i);
        }
        assertEquals(0, m.cpu().register(0));
    }

    @Test
    void readsTheKeyboardThroughIoRegisters() {
        String src = """
                        LDI R1, IO_BASE
                        LDI R2, 3000
                loop:   LD R0, [R1+KEY]     ; reading KEY removes the key
                        JZ done
                        ST R0, [R2]
                        INC R2
                        JMP loop
                done:   ST R2, [R1+DEBUG_NUM] ; nothing attached yet: ignored
                        HLT
                """;
        TriImage img = AssemblerTest.asm(src).image();
        Machine m = new Machine();
        m.bus().loadRam(img.loadAddress(), img.words());
        m.cpu().setPc(img.loadAddress());
        for (char c : "TRIADA".toCharArray()) {
            m.input().pushKey(c);
        }
        m.runFrame();
        assertTrue(m.cpu().isHalted());
        StringBuilder typed = new StringBuilder();
        for (int a = 3000; m.bus().read(a) != 0; a++) {
            typed.append((char) m.bus().read(a));
        }
        assertEquals("TRIADA", typed.toString());
        assertEquals(0, m.input().queuedKeys());
    }

    @Test
    void waitLetsAProgramCountFrames() {
        Machine m = run("""
                        LDI R1, IO_BASE
                        LDI R2, 10
                loop:   WAIT
                        DEC R2
                        JNZ loop
                        LD R0, [R1+FRAME]
                        HLT
                """);
        assertEquals(10, m.cpu().register(0), "ten WAITs = ten frames");
    }
}
