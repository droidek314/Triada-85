package org.triada.assembler;

import static org.triada.core.cpu.Instruction.imm5;
import static org.triada.core.cpu.Instruction.opcode;
import static org.triada.core.cpu.Instruction.rdField;
import static org.triada.core.cpu.Instruction.rsField;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.triada.core.cpu.Instruction;
import org.triada.core.ternary.Word;

/**
 * Turns machine words back into assembly text: the opposite of the assembler.
 * The debugger (M9) uses it to show the code around PC.
 *
 * <p><b>The round-trip promise:</b> assembling the text this class produces
 * gives back exactly the same words. That matters because some words have no
 * "proper" spelling: a {@code NOP} whose unused fields aren't 0, a JIF with an
 * invalid condition, or simply data (like the characters of a string) that
 * happens to look like an instruction. Such words come out as
 * {@code .word N}, which always round-trips.
 *
 * <p>Numbers are printed in decimal. Memory operands print as {@code [R1]},
 * {@code [R1+5]} or {@code [R1-3]}; JIF prints as its alias ({@code JNZ 300}).
 */
public final class Disassembler {

    /**
     * One disassembled instruction (or data word).
     *
     * @param address  where it starts
     * @param words    the words it occupies (1 to 4)
     * @param text     the assembly text, e.g. {@code LD R0, [R1+5]}
     */
    public record Result(int address, int[] words, String text) {

        /** Number of words. */
        public int length() {
            return words.length;
        }
    }

    private Disassembler() {
    }

    /**
     * Disassembles the instruction at {@code address}, reading words through
     * {@code memory} (for the debugger: {@code bus::peek}, which has no side
     * effects). Addresses wrap at the ends of the address range, as PC does.
     */
    public static Result disassemble(int address, IntUnaryOperator memory) {
        int[] buf = new int[4];
        int a = address;
        for (int i = 0; i < 4; i++) {
            buf[i] = memory.applyAsInt(a);
            a = a == Word.MAX ? Word.MIN : a + 1;
        }
        return decode(address, buf, 4);
    }

    /**
     * Disassembles a whole block, e.g. a {@link org.triada.core.TriImage}'s
     * words, from start to end. An instruction whose extra words would run past
     * the end of the block is shown as {@code .word}.
     */
    public static List<Result> disassembleAll(int loadAddress, int[] words) {
        List<Result> out = new ArrayList<>();
        int i = 0;
        int[] buf = new int[4];
        while (i < words.length) {
            int available = Math.min(4, words.length - i);
            System.arraycopy(words, i, buf, 0, available);
            Result r = decode(loadAddress + i, buf, available);
            out.add(r);
            i += r.length();
        }
        return out;
    }

    /**
     * Renders a block as a complete source file that re-assembles to the same
     * words: an {@code .org} line followed by one line per instruction.
     */
    public static String toSource(int loadAddress, int[] words) {
        StringBuilder sb = new StringBuilder();
        sb.append("        .org ").append(loadAddress).append('\n');
        for (Result r : disassembleAll(loadAddress, words)) {
            sb.append("        ").append(r.text()).append('\n');
        }
        return sb.toString();
    }

    /** Decodes from {@code buf}, of which {@code available} words are real. */
    static Result decode(int address, int[] buf, int available) {
        int w = buf[0];
        int op = opcode(w);
        int rdF = rdField(w);
        int rsF = rsField(w);
        int imm = imm5(w);
        int length = Instruction.length(op);
        if (available < length) {
            return data(address, w);
        }
        String mn = Instruction.mnemonic(op);
        String rd = reg(rdF);
        String rs = reg(rsF);
        String text;
        int canonical;
        switch (op) {
            case Instruction.HLT:
            case Instruction.NOP:
            case Instruction.RET:
            case Instruction.WAIT:
                text = mn;
                canonical = Instruction.encode(op, 0, 0, 0);
                break;
            case Instruction.MOV:
            case Instruction.ADD:
            case Instruction.SUB:
            case Instruction.MUL:
            case Instruction.DIV:
            case Instruction.MOD:
            case Instruction.MIN:
            case Instruction.MAX:
            case Instruction.TMUL:
            case Instruction.CMP:
                text = mn + " " + rd + ", " + rs;
                canonical = Instruction.encode(op, rdF, rsF, 0);
                break;
            case Instruction.NEG:
            case Instruction.PUSH:
            case Instruction.POP:
                text = mn + " " + rd;
                canonical = Instruction.encode(op, rdF, 0, 0);
                break;
            case Instruction.LDI:
                text = mn + " " + rd + ", " + buf[1];
                canonical = Instruction.encode(op, rdF, 0, 0);
                break;
            case Instruction.LD:
            case Instruction.ST:
                text = mn + " " + rd + ", [" + rs + (imm > 0 ? "+" + imm : imm < 0 ? Integer.toString(imm) : "") + "]";
                canonical = Instruction.encode(op, rdF, rsF, imm);
                break;
            case Instruction.ADDI:
            case Instruction.ROT:
            case Instruction.SHT:
                text = mn + " " + rd + ", " + imm;
                canonical = Instruction.encode(op, rdF, 0, imm);
                break;
            case Instruction.JMP:
            case Instruction.JSR:
                text = mn + " " + buf[1];
                canonical = Instruction.encode(op, 0, 0, 0);
                break;
            case Instruction.JIF: {
                String alias = Mnemonics.conditionAlias(rdF);
                if (alias == null) {
                    return data(address, w);       // invalid condition: this would fault
                }
                text = alias + " " + buf[1];
                canonical = Instruction.encode(op, rdF, 0, 0);
                break;
            }
            case Instruction.BR3:
                text = mn + " " + buf[1] + ", " + buf[2] + ", " + buf[3];
                canonical = Instruction.encode(op, 0, 0, 0);
                break;
            default:
                throw new IllegalStateException("opcode " + op);
        }
        if (canonical != w) {
            return data(address, w);               // unused fields aren't 0: not a "real" instruction
        }
        int[] words = new int[length];
        System.arraycopy(buf, 0, words, 0, length);
        return new Result(address, words, text);
    }

    private static Result data(int address, int w) {
        return new Result(address, new int[] {w}, ".word " + w);
    }

    private static String reg(int field) {
        return Registers.name(field + 4);
    }
}
