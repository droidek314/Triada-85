package org.triada.assembler;

import static org.triada.core.cpu.Instruction.ADD;
import static org.triada.core.cpu.Instruction.ADDI;
import static org.triada.core.cpu.Instruction.BR3;
import static org.triada.core.cpu.Instruction.CMP;
import static org.triada.core.cpu.Instruction.COND_GE;
import static org.triada.core.cpu.Instruction.COND_LE;
import static org.triada.core.cpu.Instruction.COND_N;
import static org.triada.core.cpu.Instruction.COND_NZ;
import static org.triada.core.cpu.Instruction.COND_P;
import static org.triada.core.cpu.Instruction.COND_Z;
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

import java.util.HashMap;
import java.util.Map;
import org.triada.core.cpu.Instruction;

/**
 * Every mnemonic the assembler accepts, real or pseudo (SPEC §3.4, §9.3), and
 * the <em>form</em> of its operands. The form decides both how the operands
 * are checked and how the instruction is encoded.
 */
final class Mnemonics {

    /** The operand shapes. */
    enum Form {
        /** no operands: {@code HLT} */
        NONE("no operands"),
        /** one register: {@code NEG R0}; also INC/DEC, whose imm5 is fixed */
        R("a register, e.g. R0"),
        /** two registers: {@code ADD R0, R1} */
        RR("two registers, e.g. R0, R1"),
        /** register and a full word: {@code LDI R0, 1000} */
        R_WORD("a register and a value, e.g. R0, 1000"),
        /** register and memory operand: {@code LD R0, [R1+2]} */
        R_MEM("a register and a memory operand, e.g. R0, [R1+2]"),
        /** register and imm5: {@code ADDI R0, 5} */
        R_IMM("a register and a small number (-121 .. 121), e.g. R0, 5"),
        /** register and optional imm5 (default 1): {@code ROT R0} */
        R_OPT_IMM("a register and an optional small number, e.g. R0 or R0, 2"),
        /** one address: {@code JMP loop}; the JIF aliases also use this form */
        ADDR("an address, e.g. loop"),
        /** condition and address: {@code JIF 2, loop} */
        COND_ADDR("a condition (-2 .. 3) and an address, e.g. 2, loop"),
        /** three addresses: {@code BR3 less, same, more} */
        ADDR3("three addresses, e.g. less, same, more");

        final String expected;

        Form(String expected) {
            this.expected = expected;
        }
    }

    /**
     * @param name    the mnemonic, upper case
     * @param opcode  the real opcode it assembles to
     * @param form    its operand shape
     * @param fixed   for pseudo-instructions: the fixed condition (JIF aliases)
     *                or imm5 (INC/DEC); otherwise 0
     */
    record Spec(String name, int opcode, Form form, int fixed) {

        /** Number of words this instruction occupies. */
        int length() {
            return Instruction.length(opcode);
        }
    }

    private static final Map<String, Spec> TABLE = new HashMap<>();

    static {
        add("HLT", HLT, Form.NONE, 0);
        add("NOP", NOP, Form.NONE, 0);
        add("MOV", MOV, Form.RR, 0);
        add("LDI", LDI, Form.R_WORD, 0);
        add("LD", LD, Form.R_MEM, 0);
        add("ST", ST, Form.R_MEM, 0);
        add("PUSH", PUSH, Form.R, 0);
        add("POP", POP, Form.R, 0);
        add("ADD", ADD, Form.RR, 0);
        add("SUB", SUB, Form.RR, 0);
        add("ADDI", ADDI, Form.R_IMM, 0);
        add("MUL", MUL, Form.RR, 0);
        add("DIV", DIV, Form.RR, 0);
        add("MOD", MOD, Form.RR, 0);
        add("NEG", NEG, Form.R, 0);
        add("MIN", Instruction.MIN, Form.RR, 0);
        add("MAX", Instruction.MAX, Form.RR, 0);
        add("TMUL", TMUL, Form.RR, 0);
        add("ROT", ROT, Form.R_OPT_IMM, 0);
        add("SHT", SHT, Form.R_IMM, 0);
        add("CMP", CMP, Form.RR, 0);
        add("JMP", JMP, Form.ADDR, 0);
        add("JSR", JSR, Form.ADDR, 0);
        add("RET", RET, Form.NONE, 0);
        add("JIF", JIF, Form.COND_ADDR, 0);
        add("BR3", BR3, Form.ADDR3, 0);
        add("WAIT", WAIT, Form.NONE, 0);
        // Pseudo-instructions (SPEC §9.3).
        add("JN", JIF, Form.ADDR, COND_N);
        add("JZ", JIF, Form.ADDR, COND_Z);
        add("JP", JIF, Form.ADDR, COND_P);
        add("JNZ", JIF, Form.ADDR, COND_NZ);
        add("JLE", JIF, Form.ADDR, COND_LE);
        add("JGE", JIF, Form.ADDR, COND_GE);
        add("INC", ADDI, Form.R, 1);
        add("DEC", ADDI, Form.R, -1);
    }

    private Mnemonics() {
    }

    private static void add(String name, int opcode, Form form, int fixed) {
        TABLE.put(name, new Spec(name, opcode, form, fixed));
    }

    /** The spec for an upper-case mnemonic, or null if there is none. */
    static Spec lookup(String upperName) {
        return TABLE.get(upperName);
    }

    /** The JIF alias for a valid condition, e.g. 2 → "JNZ". */
    static String conditionAlias(int cond) {
        switch (cond) {
            case COND_N:
                return "JN";
            case COND_Z:
                return "JZ";
            case COND_P:
                return "JP";
            case COND_NZ:
                return "JNZ";
            case COND_LE:
                return "JLE";
            case COND_GE:
                return "JGE";
            default:
                return null;
        }
    }
}
