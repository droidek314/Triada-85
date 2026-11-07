package org.triada.core.cpu;

import org.triada.core.ternary.Ternary;
import org.triada.core.ternary.Word;

/**
 * Everything about the <em>shape</em> of an instruction (SPEC §3.3, §3.4, §3.8):
 * the 27 opcodes, the JIF conditions, how the fields are packed into the first
 * word, and how many words each instruction has. The CPU, the assembler (M4),
 * the disassembler and the debugger all share these definitions.
 *
 * <pre>
 *  t11 t10 t9 | t8 t7 | t6 t5 | t4 t3 t2 t1 t0
 *    opcode   |  rd   |  rs   |     imm5
 *   −13…+13   | −4…+4 | −4…+4 |  −121…+121
 *
 *  word = opcode·3^9 + rd·3^7 + rs·3^5 + imm5
 * </pre>
 *
 * <p>The register fields hold {@code n − 4} for register Rn (SPEC §3.1), so
 * R0 is −4 and R8 is +4. Use {@link #reg(int)} to convert.
 */
public final class Instruction {

    // ------------------------------------------------------------ opcodes (§3.4)

    public static final int HLT = 0;
    public static final int NOP = 1;
    public static final int MOV = 2;
    public static final int LDI = 3;
    public static final int LD = 4;
    public static final int ST = 5;
    public static final int PUSH = 6;
    public static final int POP = 7;
    public static final int ADD = 8;
    public static final int SUB = 9;
    public static final int ADDI = 10;
    public static final int MUL = 11;
    public static final int DIV = 12;
    public static final int MOD = 13;
    public static final int NEG = -1;
    public static final int MIN = -2;
    public static final int MAX = -3;
    public static final int TMUL = -4;
    public static final int ROT = -5;
    public static final int SHT = -6;
    public static final int CMP = -7;
    public static final int JMP = -8;
    public static final int JSR = -9;
    public static final int RET = -10;
    public static final int JIF = -11;
    public static final int BR3 = -12;
    public static final int WAIT = -13;

    // ------------------------------------------------------------ JIF conditions (§3.8)

    /** Jump if S = −1 ({@code JN}). */
    public static final int COND_N = -1;
    /** Jump if S = 0 ({@code JZ}). */
    public static final int COND_Z = 0;
    /** Jump if S = +1 ({@code JP}). */
    public static final int COND_P = 1;
    /** Jump if S ≠ 0 ({@code JNZ}). */
    public static final int COND_NZ = 2;
    /** Jump if S ≠ +1 ({@code JLE}). */
    public static final int COND_LE = -2;
    /** Jump if S ≠ −1 ({@code JGE}). */
    public static final int COND_GE = 3;

    // ------------------------------------------------------------ field ranges

    /** Largest register field value (R8). */
    public static final int REG_FIELD_MAX = 4;
    /** Largest imm5 value: (3^5 − 1) / 2. */
    public static final int IMM5_MAX = 121;
    /** Largest opcode value. */
    public static final int OPCODE_MAX = 13;
    /** Number of registers R0 … R8. */
    public static final int REGISTER_COUNT = 9;
    /** Register number of the stack pointer. */
    public static final int SP = 8;

    // Mnemonics indexed by opcode + 13.
    private static final String[] MNEMONICS = {
        "WAIT", "BR3", "JIF", "RET", "JSR", "JMP", "CMP", "SHT", "ROT", "TMUL", "MAX", "MIN", "NEG",
        "HLT", "NOP", "MOV", "LDI", "LD", "ST", "PUSH", "POP", "ADD", "SUB", "ADDI", "MUL", "DIV", "MOD",
    };

    private Instruction() {
    }

    // ------------------------------------------------------------ encoding

    /** The register field value for register Rn: {@code n − 4}. */
    public static int reg(int n) {
        if (n < 0 || n >= REGISTER_COUNT) {
            throw new IllegalArgumentException("no register R" + n + " (R0 … R8)");
        }
        return n - 4;
    }

    /**
     * Packs the four fields into an instruction word. The register arguments
     * are <em>field values</em> (−4 … +4, see {@link #reg(int)}); for JIF the
     * rd field holds the condition instead.
     *
     * @throws IllegalArgumentException if a field is out of range
     */
    public static int encode(int opcode, int rdField, int rsField, int imm5) {
        check("opcode", opcode, OPCODE_MAX);
        check("rd", rdField, REG_FIELD_MAX);
        check("rs", rsField, REG_FIELD_MAX);
        check("imm5", imm5, IMM5_MAX);
        return opcode * Ternary.pow3(9) + rdField * Ternary.pow3(7) + rsField * Ternary.pow3(5) + imm5;
    }

    private static void check(String name, int value, int max) {
        if (value < -max || value > max) {
            throw new IllegalArgumentException(name + " " + value + " is outside " + -max + " … " + max);
        }
    }

    // ------------------------------------------------------------ decoding
    //
    // The offset trick (see WordMath): u = word + Word.MAX adds 1 to every trit,
    // turning the word into an ordinary base-3 number with digits 0, 1, 2. Each
    // field then becomes "field + its maximum", read with plain / and %:
    //   u % 243         = imm5 + 121      (low 5 trits)
    //   (u / 243) % 9   = rs + 4          (next 2 trits)  = register number!
    //   (u / 2187) % 9  = rd + 4          (next 2 trits)
    //   u / 19683       = opcode + 13     (top 3 trits)

    /** The opcode (−13 … +13) of an instruction word. */
    public static int opcode(int word) {
        return (word + Word.MAX) / 19_683 - 13;
    }

    /** The rd field (−4 … +4). For a register, add 4 to get its number. */
    public static int rdField(int word) {
        return (word + Word.MAX) / 2_187 % 9 - 4;
    }

    /** The rs field (−4 … +4). */
    public static int rsField(int word) {
        return (word + Word.MAX) / 243 % 9 - 4;
    }

    /** The imm5 field (−121 … +121). */
    public static int imm5(int word) {
        return (word + Word.MAX) % 243 - 121;
    }

    // ------------------------------------------------------------ tables

    /** The mnemonic of an opcode, e.g. {@code mnemonic(8)} is {@code "ADD"}. */
    public static String mnemonic(int opcode) {
        check("opcode", opcode, OPCODE_MAX);
        return MNEMONICS[opcode + 13];
    }

    /** How many words an instruction with this opcode occupies (1, 2 or 4). */
    public static int length(int opcode) {
        switch (opcode) {
            case LDI:
            case JMP:
            case JSR:
            case JIF:
                return 2;
            case BR3:
                return 4;
            default:
                return 1;
        }
    }

    /** True if {@code cond} is one of the six JIF conditions; anything else faults. */
    public static boolean isValidCondition(int cond) {
        return cond >= COND_LE && cond <= COND_GE;
    }
}
