package org.triada.core.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.triada.core.cpu.Instruction.ADD;
import static org.triada.core.cpu.Instruction.ADDI;
import static org.triada.core.cpu.Instruction.BR3;
import static org.triada.core.cpu.Instruction.CMP;
import static org.triada.core.cpu.Instruction.DIV;
import static org.triada.core.cpu.Instruction.HLT;
import static org.triada.core.cpu.Instruction.JIF;
import static org.triada.core.cpu.Instruction.JMP;
import static org.triada.core.cpu.Instruction.JSR;
import static org.triada.core.cpu.Instruction.LD;
import static org.triada.core.cpu.Instruction.LDI;
import static org.triada.core.cpu.Instruction.MOD;
import static org.triada.core.cpu.Instruction.MOV;
import static org.triada.core.cpu.Instruction.MUL;
import static org.triada.core.cpu.Instruction.NEG;
import static org.triada.core.cpu.Instruction.NOP;
import static org.triada.core.cpu.Instruction.POP;
import static org.triada.core.cpu.Instruction.PUSH;
import static org.triada.core.cpu.Instruction.RET;
import static org.triada.core.cpu.Instruction.ROT;
import static org.triada.core.cpu.Instruction.SHT;
import static org.triada.core.cpu.Instruction.ST;
import static org.triada.core.cpu.Instruction.SUB;
import static org.triada.core.cpu.Instruction.TMUL;
import static org.triada.core.cpu.Instruction.WAIT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.memory.Bus;
import org.triada.core.ternary.Word;

/**
 * One or more tests per opcode, written with hand-built instruction words
 * (the assembler arrives in M4). Programs are loaded at 243 with SP = 59,049,
 * as the ROM will do for real programs.
 */
class CpuTest {

    private static final int START = 243;
    private static final int DATA = 1000;

    private Machine machine;
    private Cpu cpu;
    private Bus bus;

    @BeforeEach
    void setUp() {
        machine = new Machine();
        cpu = machine.cpu();
        bus = machine.bus();
    }

    /** Instruction word; rd and rs are register NUMBERS 0 … 8. */
    private static int ins(int op, int rd, int rs, int imm5) {
        return Instruction.encode(op, Instruction.reg(rd), Instruction.reg(rs), imm5);
    }

    private static int ins(int op, int rd, int rs) {
        return ins(op, rd, rs, 0);
    }

    private static int ins(int op, int rd) {
        return ins(op, rd, 0, 0);
    }

    private static int ins(int op) {
        return ins(op, 4, 4, 0);   // register fields 0, which is what "unused" assembles to
    }

    /** JIF with a raw condition field. */
    private static int jif(int cond) {
        return Instruction.encode(JIF, cond, 0, 0);
    }

    private void load(int... words) {
        bus.loadRam(START, words);
        cpu.setPc(START);
        cpu.setRegister(8, 59_049);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            cpu.step();
        }
    }

    // ================================================================ power-on

    @Test
    void powerOnState() {
        assertEquals(-19_683, cpu.pc());
        for (int n = 0; n <= 8; n++) {
            assertEquals(0, cpu.register(n));
        }
        assertEquals(0, cpu.flagS());
        assertEquals(0, cpu.flagO());
        assertFalse(cpu.isHalted());
        assertNull(cpu.fault());
    }

    @Test
    void emptyRomIsAllHltSoThePowerOnMachineHaltsImmediately() {
        assertEquals(1, machine.runFrame());
        assertTrue(cpu.isHalted());
        assertEquals(-19_682, cpu.pc());
    }

    @Test
    void cpuStartsInRom() {
        // The first ROM words: LDI R8, 59049 ; LDI R0, 7 ; HLT
        bus.loadRom(new int[] {ins(LDI, 8), 59_049, ins(LDI, 0), 7, ins(HLT)});
        machine.runFrame();
        assertEquals(59_049, cpu.register(8));
        assertEquals(7, cpu.register(0));
        assertTrue(cpu.isHalted());
    }

    // ================================================================ 0 HLT, 1 NOP

    @Test
    void hltStopsTheCpuAndFurtherStepsDoNothing() {
        load(ins(HLT), ins(ADDI, 0, 0, 5));
        cpu.step();
        assertTrue(cpu.isHalted());
        assertNull(cpu.fault(), "HLT is not a fault");
        steps(3);
        assertEquals(0, cpu.register(0));
        assertEquals(START + 1, cpu.pc());
        assertEquals(0, cpu.runFrame(1000));
    }

    @Test
    void nopOnlyAdvancesPc() {
        load(ins(NOP));
        cpu.setFlags(1, -1);
        cpu.step();
        assertEquals(START + 1, cpu.pc());
        assertEquals(1, cpu.flagS());
        assertEquals(-1, cpu.flagO());
    }

    // ================================================================ 2 MOV, 3 LDI

    @Test
    void movCopiesAndSetsS() {
        load(ins(MOV, 1, 2), ins(MOV, 3, 4));
        cpu.setRegister(2, -42);
        cpu.step();
        assertEquals(-42, cpu.register(1));
        assertEquals(-1, cpu.flagS());
        cpu.step();                                  // R4 = 0
        assertEquals(0, cpu.flagS());
    }

    @Test
    void movOfARegisterToItselfTestsIt() {
        // SPEC §3.10 idiom: MOV R0, R0 sets S from R0.
        load(ins(MOV, 0, 0));
        cpu.setRegister(0, 17);
        cpu.step();
        assertEquals(17, cpu.register(0));
        assertEquals(1, cpu.flagS());
    }

    @Test
    void ldiLoadsTheNextWordAndSkipsIt() {
        load(ins(LDI, 5), Word.MIN, ins(LDI, 6), 0);
        cpu.step();
        assertEquals(Word.MIN, cpu.register(5));
        assertEquals(-1, cpu.flagS());
        assertEquals(START + 2, cpu.pc());
        cpu.step();
        assertEquals(0, cpu.flagS());
    }

    // ================================================================ 4 LD, 5 ST

    @Test
    void ldReadsBasePlusOffsetInBothDirections() {
        bus.write(DATA + 121, 7);
        bus.write(DATA - 121, -8);
        load(ins(LD, 0, 1, 121), ins(LD, 2, 1, -121));
        cpu.setRegister(1, DATA);
        cpu.step();
        assertEquals(7, cpu.register(0));
        assertEquals(1, cpu.flagS());
        cpu.step();
        assertEquals(-8, cpu.register(2));
        assertEquals(-1, cpu.flagS());
    }

    @Test
    void ldReachesIoRegisters() {
        // SPEC §5: LDI R1, -20412 then LD R0, [R1+k] reaches register k.
        // Frame 1: WAIT. Frame 2: FRAME is now 1; read it through R1, then HLT.
        load(ins(WAIT), ins(LDI, 1), -20_412, ins(LD, 0, 1, 0), ins(HLT));
        machine.runFrame();
        machine.runFrame();
        assertEquals(1, cpu.register(0));
    }

    @Test
    void stWritesAndLeavesFlagsAlone() {
        load(ins(ST, 3, 1, -2));
        cpu.setRegister(1, DATA);
        cpu.setRegister(3, -99);
        cpu.setFlags(1, 1);
        cpu.step();
        assertEquals(-99, bus.read(DATA - 2));
        assertEquals(1, cpu.flagS());
    }

    @Test
    void stToRomIsIgnored() {
        load(ins(ST, 0, 1));
        cpu.setRegister(0, 5);
        cpu.setRegister(1, -1);
        cpu.step();
        assertEquals(0, bus.read(-1));
    }

    @Test
    void effectiveAddressWraps() {
        // MIN + (−1) wraps to MAX (unmapped); without wrapping the bus would reject it.
        load(ins(LD, 0, 1, -1), ins(ST, 0, 2, 1));
        cpu.setRegister(1, Word.MIN);
        cpu.setRegister(2, Word.MAX);
        cpu.setRegister(0, 9);
        cpu.step();
        assertEquals(0, cpu.register(0));
        cpu.step();                                   // writes MIN: unmapped, ignored
        assertFalse(cpu.isHalted());
    }

    // ================================================================ 6 PUSH, 7 POP

    @Test
    void pushDecrementsSpThenStores() {
        load(ins(PUSH, 2));
        cpu.setRegister(2, 55);
        cpu.step();
        assertEquals(59_048, cpu.register(8));
        assertEquals(55, bus.read(59_048));
    }

    @Test
    void popLoadsThenIncrementsSpAndSetsS() {
        load(ins(PUSH, 2), ins(POP, 3));
        cpu.setRegister(2, -5);
        steps(2);
        assertEquals(-5, cpu.register(3));
        assertEquals(-1, cpu.flagS());
        assertEquals(59_049, cpu.register(8));
    }

    @Test
    void stackIsLastInFirstOut() {
        load(ins(PUSH, 0), ins(PUSH, 1), ins(POP, 2), ins(POP, 3));
        cpu.setRegister(0, 10);
        cpu.setRegister(1, 20);
        steps(4);
        assertEquals(20, cpu.register(2));
        assertEquals(10, cpu.register(3));
    }

    @Test
    void pushR8PushesItsOldValueAndPopR8LoadsThePoppedValue() {
        // DECISIONS.md D-009.
        load(ins(PUSH, 8), ins(LDI, 8), 50_000, ins(PUSH, 0), ins(POP, 8));
        steps(1);
        assertEquals(59_049, bus.read(59_048), "PUSH R8 stores R8 as it was before the push");
        steps(1);                                     // R8 = 50,000
        cpu.setRegister(0, 1234);
        steps(2);                                     // PUSH R0 to 49,999, POP R8
        assertEquals(1234, cpu.register(8), "POP R8: R8 is the popped value, not value + 1");
    }

    @Test
    void stackPointerWrapsAtTheBottomOfTheAddressRange() {
        load(ins(PUSH, 0));
        cpu.setRegister(8, Word.MIN);
        cpu.step();
        assertEquals(Word.MAX, cpu.register(8));
    }

    // ================================================================ 8 ADD, 9 SUB, 10 ADDI

    @Test
    void addSetsSAndCarry() {
        load(ins(ADD, 0, 1), ins(ADD, 2, 3), ins(ADD, 4, 5));
        cpu.setRegister(0, 2);
        cpu.setRegister(1, 3);
        cpu.setRegister(2, Word.MAX);
        cpu.setRegister(3, 1);
        cpu.setRegister(4, Word.MIN);
        cpu.setRegister(5, -1);
        cpu.step();
        assertEquals(5, cpu.register(0));
        assertEquals(1, cpu.flagS());
        assertEquals(0, cpu.flagO());
        cpu.step();
        assertEquals(Word.MIN, cpu.register(2));
        assertEquals(-1, cpu.flagS());
        assertEquals(1, cpu.flagO(), "wrapped over the top");
        cpu.step();
        assertEquals(Word.MAX, cpu.register(4));
        assertEquals(1, cpu.flagS());
        assertEquals(-1, cpu.flagO(), "wrapped under the bottom");
    }

    @Test
    void addARegisterToItself() {
        load(ins(ADD, 0, 0));
        cpu.setRegister(0, 21);
        cpu.step();
        assertEquals(42, cpu.register(0));
    }

    @Test
    void subSetsSAndCarry() {
        load(ins(SUB, 0, 1), ins(SUB, 2, 2));
        cpu.setRegister(0, Word.MIN);
        cpu.setRegister(1, 1);
        cpu.setRegister(2, 77);
        cpu.step();
        assertEquals(Word.MAX, cpu.register(0));
        assertEquals(-1, cpu.flagO());
        cpu.step();                                   // idiom: SUB R2, R2 clears R2
        assertEquals(0, cpu.register(2));
        assertEquals(0, cpu.flagS());
        assertEquals(0, cpu.flagO());
    }

    @Test
    void addiAddsTheImmediate() {
        load(ins(ADDI, 0, 4, -121), ins(ADDI, 1, 4, 1));
        cpu.setRegister(1, Word.MAX);
        cpu.step();
        assertEquals(-121, cpu.register(0));
        assertEquals(-1, cpu.flagS());
        assertEquals(0, cpu.flagO());
        cpu.step();
        assertEquals(Word.MIN, cpu.register(1));
        assertEquals(1, cpu.flagO());
    }

    // ================================================================ 11 MUL, 12 DIV, 13 MOD

    @Test
    void mulWrapsAndLeavesOAlone() {
        load(ins(MUL, 0, 1), ins(MUL, 2, 3));
        cpu.setRegister(0, -6);
        cpu.setRegister(1, 7);
        cpu.setRegister(2, 729);
        cpu.setRegister(3, 729);
        cpu.setFlags(0, -1);
        cpu.step();
        assertEquals(-42, cpu.register(0));
        assertEquals(-1, cpu.flagS());
        assertEquals(-1, cpu.flagO(), "MUL doesn't touch O");
        cpu.step();
        assertEquals(0, cpu.register(2), "3^12 wraps to 0");
        assertEquals(0, cpu.flagS());
    }

    @Test
    void divRoundsAndClearsO() {
        load(ins(DIV, 0, 1), ins(DIV, 2, 3));
        cpu.setRegister(0, 7);
        cpu.setRegister(1, 2);
        cpu.setRegister(2, -8);
        cpu.setRegister(3, 3);
        cpu.setFlags(0, 1);
        cpu.step();
        assertEquals(3, cpu.register(0));
        assertEquals(1, cpu.flagS());
        assertEquals(0, cpu.flagO());
        cpu.step();
        assertEquals(-3, cpu.register(2));
        assertEquals(-1, cpu.flagS());
    }

    @Test
    void modGivesTheRoundingRemainder() {
        load(ins(MOD, 0, 1));
        cpu.setRegister(0, 8);
        cpu.setRegister(1, 3);
        cpu.step();
        assertEquals(-1, cpu.register(0));
        assertEquals(-1, cpu.flagS());
        assertEquals(0, cpu.flagO());
    }

    @Test
    void divisionByZeroGivesZeroWithSZeroAndOPlusOne() {
        load(ins(DIV, 0, 1), ins(MOD, 2, 1));
        cpu.setRegister(0, 100);
        cpu.setRegister(2, -100);
        cpu.step();
        assertEquals(0, cpu.register(0));
        assertEquals(0, cpu.flagS());
        assertEquals(1, cpu.flagO());
        cpu.setFlags(1, 0);
        cpu.step();
        assertEquals(0, cpu.register(2), "D-006");
        assertEquals(0, cpu.flagS());
        assertEquals(1, cpu.flagO());
    }

    // ================================================================ −1 NEG … −4 TMUL

    @Test
    void negFlipsTheSign() {
        load(ins(NEG, 0), ins(NEG, 1));
        cpu.setRegister(0, Word.MIN);
        cpu.step();
        assertEquals(Word.MAX, cpu.register(0));
        assertEquals(1, cpu.flagS());
        cpu.step();
        assertEquals(0, cpu.flagS());
    }

    @Test
    void minMaxTmulAreTritWise() {
        int a = Word.parse("+-0+-0+-0+-0");
        int b = Word.parse("0+-0+-0+-0+-");
        load(ins(Instruction.MIN, 0, 3), ins(Instruction.MAX, 1, 3), ins(TMUL, 2, 3));
        cpu.setRegister(0, a);
        cpu.setRegister(1, a);
        cpu.setRegister(2, a);
        cpu.setRegister(3, b);
        cpu.step();
        assertEquals(Word.parse("0--0--0--0--"), cpu.register(0));
        assertEquals(-1, cpu.flagS());
        cpu.step();
        assertEquals(Word.parse("++0++0++0++0"), cpu.register(1));
        assertEquals(1, cpu.flagS());
        cpu.step();
        assertEquals(Word.parse("0-00-00-00-0"), cpu.register(2));
        assertEquals(-1, cpu.flagS());
    }

    // ================================================================ −5 ROT, −6 SHT

    @Test
    void rotRotatesEveryTritUsingImm5ModThree() {
        load(ins(ROT, 0, 4, 1), ins(ROT, 1, 4, -1), ins(ROT, 2, 4, 3));
        cpu.setRegister(2, 77);
        cpu.step();
        assertEquals(Word.MAX, cpu.register(0), "all zeros rotate up to all +");
        assertEquals(1, cpu.flagS());
        cpu.step();
        assertEquals(Word.MIN, cpu.register(1), "-1 = down once: all zeros become -");
        cpu.step();
        assertEquals(77, cpu.register(2), "3 mod 3 = 0: unchanged");
    }

    @Test
    void shtShiftsBothWays() {
        load(ins(SHT, 0, 4, 2), ins(SHT, 1, 4, -1), ins(SHT, 2, 4, 121));
        cpu.setRegister(0, 6);
        cpu.setRegister(1, 8);
        cpu.setRegister(2, 5);
        cpu.step();
        assertEquals(54, cpu.register(0));
        cpu.step();
        assertEquals(3, cpu.register(1), "8 ÷ 3 rounds to 3");
        cpu.step();
        assertEquals(0, cpu.register(2));
        assertEquals(0, cpu.flagS());
    }

    // ================================================================ −7 CMP

    @Test
    void cmpSetsSWithoutWrappingOrStoring() {
        load(ins(CMP, 0, 1), ins(CMP, 1, 0), ins(CMP, 2, 2));
        cpu.setRegister(0, Word.MAX);
        cpu.setRegister(1, Word.MIN);
        cpu.setFlags(0, -1);
        cpu.step();
        assertEquals(1, cpu.flagS(), "MAX > MIN, although MAX - MIN would wrap");
        assertEquals(Word.MAX, cpu.register(0), "no store");
        assertEquals(-1, cpu.flagO(), "O unchanged");
        cpu.step();
        assertEquals(-1, cpu.flagS());
        cpu.step();
        assertEquals(0, cpu.flagS());
    }

    // ================================================================ −8 JMP, −9 JSR, −10 RET

    @Test
    void jmpJumps() {
        load(ins(JMP), 300);
        cpu.step();
        assertEquals(300, cpu.pc());
    }

    @Test
    void jsrPushesTheReturnAddressAndRetComesBack() {
        // 243: JSR 300 ; 245: HLT       300: LDI R0, 9 ; 302: RET
        load(ins(JSR), 300, ins(HLT));
        bus.loadRam(300, new int[] {ins(LDI, 0), 9, ins(RET)});
        cpu.step();
        assertEquals(300, cpu.pc());
        assertEquals(245, bus.read(59_048), "return address = the word after the 2-word JSR");
        assertEquals(59_048, cpu.register(8));
        steps(2);
        assertEquals(245, cpu.pc());
        assertEquals(59_049, cpu.register(8));
        cpu.step();
        assertTrue(cpu.isHalted());
        assertEquals(9, cpu.register(0));
    }

    @Test
    void retDoesNotChangeTheFlags() {
        load(ins(RET));
        bus.write(59_048, 0);
        cpu.setRegister(8, 59_048);
        cpu.setFlags(1, 1);
        cpu.step();
        assertEquals(0, cpu.pc());
        assertEquals(1, cpu.flagS());
    }

    @Test
    void pushThenRetIsAnIndirectJump() {
        // SPEC §3.10 idiom.
        load(ins(PUSH, 1), ins(RET));
        cpu.setRegister(1, 4321);
        steps(2);
        assertEquals(4321, cpu.pc());
        assertEquals(59_049, cpu.register(8));
    }

    // ================================================================ −11 JIF

    @Test
    void everyConditionAgainstEverySign() {
        // cond, then expected jump for S = −1, 0, +1 (SPEC §3.8).
        int[][] table = {
            {-1, 1, 0, 0},   // JN
            {0, 0, 1, 0},    // JZ
            {1, 0, 0, 1},    // JP
            {2, 1, 0, 1},    // JNZ
            {-2, 1, 1, 0},   // JLE
            {3, 0, 1, 1},    // JGE
        };
        for (int[] row : table) {
            for (int sign = -1; sign <= 1; sign++) {
                setUp();
                load(jif(row[0]), 500);
                cpu.setFlags(sign, 0);
                cpu.step();
                boolean expectJump = row[sign + 2] == 1;
                assertEquals(expectJump ? 500 : START + 2, cpu.pc(), "cond " + row[0] + ", S = " + sign);
                assertFalse(cpu.isHalted());
            }
        }
    }

    @Test
    void invalidConditionsFaultAndHalt() {
        for (int cond : new int[] {-4, -3, 4}) {
            setUp();
            load(ins(NOP), jif(cond), 500);
            cpu.step();
            cpu.step();
            assertTrue(cpu.isHalted(), "cond " + cond);
            assertEquals(Fault.INVALID_CONDITION, cpu.fault());
            assertEquals(1, cpu.fault().code());
            assertEquals(START + 1, cpu.faultPc(), "address of the JIF");
            assertEquals(START + 1, cpu.pc(), "PC left on the faulting instruction (D-010)");
            assertEquals(0, cpu.runFrame(100));
        }
    }

    @Test
    void resetClearsAFault() {
        load(jif(4), 0);
        cpu.step();
        cpu.reset();
        assertFalse(cpu.isHalted());
        assertNull(cpu.fault());
    }

    // ================================================================ −12 BR3

    @Test
    void br3PicksOneOfThreeTargetsBySign() {
        for (int sign = -1; sign <= 1; sign++) {
            setUp();
            load(ins(BR3), 1000, 2000, 3000);
            cpu.setFlags(sign, 0);
            cpu.step();
            assertEquals(2000 + 1000 * sign, cpu.pc(), "S = " + sign);
        }
    }

    @Test
    void br3AfterCmpIsAThreeWayCompare() {
        // CMP R0, R1 then BR3 less, equal, greater: the natural ternary "if".
        load(ins(CMP, 0, 1), ins(BR3), 1000, 2000, 3000);
        cpu.setRegister(0, 5);
        cpu.setRegister(1, 9);
        steps(2);
        assertEquals(1000, cpu.pc());
    }

    // ================================================================ −13 WAIT and the frame budget

    @Test
    void waitEndsTheFrameAndTheNextFrameContinuesAfterIt() {
        // 243: ADDI R0,1 ; WAIT ; ADDI R0,1 ; WAIT ; HLT
        load(ins(ADDI, 0, 4, 1), ins(WAIT), ins(ADDI, 0, 4, 1), ins(WAIT), ins(HLT));
        assertEquals(2, cpu.runFrame(Machine.CYCLES_PER_FRAME), "ADDI + WAIT, 1 cycle each");
        assertEquals(1, cpu.register(0));
        assertEquals(START + 2, cpu.pc());
        assertEquals(2, cpu.runFrame(Machine.CYCLES_PER_FRAME));
        assertEquals(2, cpu.register(0));
        assertEquals(1, cpu.runFrame(Machine.CYCLES_PER_FRAME));
        assertTrue(cpu.isHalted());
    }

    @Test
    void aBusyLoopUsesExactlyTheWholeBudget() {
        // loop: ADDI R0, 1 ; JMP loop   (two instructions, three words, one cycle each)
        load(ins(ADDI, 0, 4, 1), ins(JMP), START);
        assertEquals(59_049, cpu.runFrame(Machine.CYCLES_PER_FRAME));
        assertEquals(29_525, cpu.register(0), "ADDI runs on cycles 1, 3, 5 .. 59,049");
        assertEquals(59_049, cpu.cycles());
    }

    @Test
    void eachInstructionIsOneCycleWhateverItsLength() {
        // 243: LDI R0, 1 (2 words) ; 245: BR3 249, 249, 249 (4 words) ; 249: HLT
        load(ins(LDI, 0), 1, ins(BR3), 249, 249, 249, ins(HLT));
        assertEquals(3, cpu.runFrame(100));       // LDI (2 words), BR3 (4 words), HLT
        assertEquals(3, cpu.cycles());
    }

    @Test
    void budgetCountsAcrossFramesForTheMachine() {
        load(ins(ADDI, 0, 4, 1), ins(JMP), START);
        machine.runFrame();
        machine.runFrame();
        assertEquals(2 * 59_049, cpu.cycles());
        assertEquals(59_049, cpu.register(0));
    }

    // ================================================================ PC wrap

    @Test
    void pcWrapsFromTheTopOfTheAddressRangeToTheBottom() {
        // Address MAX is unmapped, so it reads 0, which is HLT. Fetching it still
        // moves PC past it, and "past MAX" wraps to MIN.
        cpu.setPc(Word.MAX);
        cpu.step();
        assertEquals(Word.MIN, cpu.pc());
        assertTrue(cpu.isHalted());
    }
}
