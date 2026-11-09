package org.triada.assembler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.triada.core.TriImage;
import org.triada.core.cpu.Instruction;
import org.triada.core.MachineProfile;
import org.triada.core.MachineProfiles;
import org.triada.core.ternary.Word;

/**
 * The TRIADA assembler, {@code tasm} (SPEC §9): turns assembly source into
 * machine words.
 *
 * <p>It works in two passes over the source, the classic way:
 * <ol>
 * <li><b>Layout.</b> Read every line (following {@code .include}s), work out
 *     how many words each line produces and therefore the address of every
 *     line and every label. Nothing is encoded yet, because a line may use a
 *     label that is only defined further down.</li>
 * <li><b>Encode.</b> Now that every label is known, evaluate all operands,
 *     check their ranges and produce the words.</li>
 * </ol>
 * Errors don't stop at the first one: each bad line is reported and skipped,
 * up to {@link #MAX_ERRORS}. If any line fails in pass 1, pass 2 doesn't run,
 * because the addresses after a broken line can't be trusted.
 *
 * <p>An {@code Assembler} can be reused; each call works on fresh state.
 */
public final class Assembler {

    /**
     * Where code goes on the TRIADA-85 if the source has no {@code .org}: the
     * program area (DECISIONS.md D-012). Another model's is its profile's programStart.
     */
    public static final int DEFAULT_ORIGIN = MachineProfiles.TRIADA_85.programStart();

    /** Stop collecting after this many errors. */
    public static final int MAX_ERRORS = 100;

    private static final int MAX_INCLUDE_DEPTH = 16;

    private final SourceLoader loader;
    private final Map<String, Integer> builtins;
    private final int origin;

    /** An assembler for the TRIADA-85 that reads {@code .include}d files through {@code loader}. */
    public Assembler(SourceLoader loader) {
        this(loader, MachineProfiles.TRIADA_85);
    }

    /**
     * An assembler for the model {@code profile} (M16): its built-in symbols
     * ({@link BuiltinSymbols#of(MachineProfile)}) and its default origin come
     * from the profile.
     */
    public Assembler(SourceLoader loader, MachineProfile profile) {
        this.loader = loader;
        this.builtins = BuiltinSymbols.of(profile);
        this.origin = profile.programStart();
    }

    /** Assembles the source file {@code path}, read through the loader. */
    public AssemblyResult assemble(String path) throws AssemblyException {
        String text = loader.load(path);
        if (text == null) {
            throw new AssemblyException(List.of(new AssemblyError(path, 0, 0, "can't read source file")));
        }
        return assembleSource(path, text);
    }

    /** Assembles {@code text}, reporting errors under the name {@code name}. */
    public AssemblyResult assembleSource(String name, String text) throws AssemblyException {
        return new Run().assemble(name, text);
    }

    /** One source line and everything the passes learn about it. */
    static final class SourceLine {
        final String file;
        final int number;
        final String text;
        Line parsed;            // null if the line didn't parse
        long address;           // address of its first word (or of its label)
        int size;               // number of words it produces
        int[] words = new int[0];

        SourceLine(String file, int number, String text) {
            this.file = file;
            this.number = number;
            this.text = text;
        }
    }

    /** A constant definition, evaluated on first use. */
    private static final class Constant {
        final Expr expr;
        final SourceLine line;  // where it is defined: its errors are reported there
        boolean evaluating;     // for cycle detection: A = B, B = A
        boolean failed;         // its definition has an error (already reported)
        Long value;

        Constant(Expr expr, SourceLine line) {
            this.expr = expr;
            this.line = line;
        }
    }

    /** The state of one assembly. */
    private final class Run {
        private final List<SourceLine> lines = new ArrayList<>();
        private final List<AssemblyError> errors = new ArrayList<>();
        private final Map<String, Long> labels = new HashMap<>();
        private final Map<String, Constant> constants = new HashMap<>();
        private final Map<String, SourceLine> definedAt = new HashMap<>();
        private final Deque<String> includeStack = new ArrayDeque<>();
        private long pc = origin;
        private SourceLine current;

        AssemblyResult assemble(String name, String text) throws AssemblyException {
            includeStack.push(name);
            layoutFile(name, text);
            includeStack.pop();
            if (errors.isEmpty()) {
                checkConstants();
                for (SourceLine sl : lines) {
                    if (sl.parsed != null) {
                        current = sl;
                        try {
                            sl.words = encode(sl);
                        } catch (AsmFailure f) {
                            error(sl, f.column, f.getMessage(), f.silent);
                        }
                    }
                }
            }
            TriImage image = errors.isEmpty() ? buildImage() : null;
            if (!errors.isEmpty()) {
                throw new AssemblyException(errors);
            }
            return new AssemblyResult(image, Listing.render(lines), symbols());
        }

        // ============================================================ pass 1: layout

        private void layoutFile(String file, String text) {
            int number = 0;
            for (String raw : splitLines(text)) {
                number++;
                SourceLine sl = new SourceLine(file, number, raw);
                lines.add(sl);
                current = sl;
                try {
                    Line line = Parser.parse(raw);
                    sl.parsed = line;
                    layout(sl, line);
                } catch (AsmFailure f) {
                    sl.parsed = null;
                    error(sl, f.column, f.getMessage(), f.silent);
                }
            }
        }

        private void layout(SourceLine sl, Line line) {
            sl.address = pc;
            boolean isOrg = line.kind() == Line.Kind.DIRECTIVE && line.name().equals("org");
            if (isOrg) {
                // "start: .org 300" means start = 300, so move pc before defining the label.
                layoutDirective(sl, line);
            }
            if (line.label() != null) {
                if (pc > Word.MAX) {
                    throw new AsmFailure(line.labelColumn(), "label '" + line.label()
                            + "' would be past the last address " + Word.MAX);
                }
                define(line.label(), line.labelColumn(), sl);
                labels.put(line.label(), pc);
            }
            switch (line.kind()) {
                case EMPTY:
                    return;
                case CONSTANT:
                    define(line.name(), line.nameColumn(), sl);
                    constants.put(line.name(), new Constant(line.value(), sl));
                    return;
                case INSTRUCTION: {
                    Mnemonics.Spec spec = Mnemonics.lookup(line.name());
                    if (spec == null) {
                        throw new AsmFailure(line.nameColumn(), "unknown instruction '" + line.name()
                                + "'; expected a mnemonic such as LDI, ADD or JMP");
                    }
                    checkOperandCount(line, spec);   // needs no symbol values, so check it early
                    reserve(sl, spec.length(), line.nameColumn());
                    return;
                }
                case DIRECTIVE:
                    if (!isOrg) {
                        layoutDirective(sl, line);
                    }
                    return;
                default:
                    throw new IllegalStateException();
            }
        }

        private void layoutDirective(SourceLine sl, Line line) {
            List<Operand> ops = line.operands();
            switch (line.name()) {
                case "org": {
                    expectOperands(line, 1, "an address, e.g. .org 243");
                    long address = word(evaluate(value(ops.get(0)), true), ops.get(0).column(), "address");
                    pc = address;
                    sl.address = address;
                    return;
                }
                case "word":
                    if (ops.isEmpty()) {
                        throw new AsmFailure(line.nameColumn(), ".word expects one or more values, found none");
                    }
                    reserve(sl, ops.size(), line.nameColumn());
                    return;
                case "str": {
                    expectOperands(line, 1, "one string, e.g. .str \"HELLO\"");
                    reserve(sl, string(ops.get(0)).length() + 1, line.nameColumn());
                    return;
                }
                case "fill": {
                    expectOperands(line, 2, "a count and a value, e.g. .fill 10, 0");
                    long count = evaluate(value(ops.get(0)), true);
                    if (count < 0 || count > Word.COUNT) {
                        throw new AsmFailure(ops.get(0).column(), ".fill count " + count
                                + " is out of range (0 .. " + Word.COUNT + ")");
                    }
                    reserve(sl, (int) count, line.nameColumn());
                    return;
                }
                case "pix":
                    expectOperands(line, 4, "four pixel values (-13 .. 13), e.g. .pix 13, 0, 0, -13");
                    reserve(sl, 1, line.nameColumn());
                    return;
                case "include": {
                    expectOperands(line, 1, "a file name in quotes, e.g. .include \"font.tasm\"");
                    include(string(ops.get(0)), ops.get(0).column());
                    return;
                }
                default:
                    throw new AsmFailure(line.nameColumn(), "unknown directive '." + line.name()
                            + "'; expected .org, .word, .str, .fill, .pix or .include");
            }
        }

        private void include(String requested, int column) {
            String path = resolve(includeStack.peek(), requested);
            if (includeStack.contains(path)) {
                List<String> chain = new ArrayList<>(includeStack);
                java.util.Collections.reverse(chain);
                throw new AsmFailure(column, "'" + path + "' includes itself (" + String.join(" -> ", chain)
                        + " -> " + path + ")");
            }
            if (includeStack.size() >= MAX_INCLUDE_DEPTH) {
                throw new AsmFailure(column, "includes are nested more than " + MAX_INCLUDE_DEPTH + " deep");
            }
            String text = loader.load(path);
            if (text == null) {
                throw new AsmFailure(column, "can't find included file '" + path + "'");
            }
            SourceLine includer = current;
            includeStack.push(path);
            layoutFile(path, text);
            includeStack.pop();
            current = includer;
        }

        /** Gives the current line {@code size} words at pc and moves pc past them. */
        private void reserve(SourceLine sl, int size, int column) {
            if (size > 0 && pc + size - 1 > Word.MAX) {
                throw new AsmFailure(column, "the program runs past the last address " + Word.MAX);
            }
            sl.size = size;
            pc += size;
        }

        private void define(String name, int column, SourceLine sl) {
            if (builtins.get(name) != null) {
                throw new AsmFailure(column, "'" + name + "' is a built-in symbol and can't be redefined");
            }
            SourceLine earlier = definedAt.get(name);
            if (earlier != null) {
                throw new AsmFailure(column, "'" + name + "' is already defined at " + earlier.file + ":"
                        + earlier.number);
            }
            definedAt.put(name, sl);
        }

        // ============================================================ pass 2: encode

        /**
         * Evaluates every constant at its own definition line, so an error in a
         * constant is reported there (once), even if nothing uses the constant.
         */
        private void checkConstants() {
            for (SourceLine sl : lines) {
                Line line = sl.parsed;
                if (line == null || line.kind() != Line.Kind.CONSTANT) {
                    continue;
                }
                Constant c = constants.get(line.name());
                if (c.value != null || c.failed) {
                    continue;
                }
                current = sl;
                c.evaluating = true;
                try {
                    c.value = evaluate(c.expr, false);
                } catch (AsmFailure f) {
                    c.failed = true;
                    error(sl, f.column, f.getMessage(), f.silent);
                } finally {
                    c.evaluating = false;
                }
            }
        }

        private int[] encode(SourceLine sl) {
            Line line = sl.parsed;
            if (line.kind() == Line.Kind.INSTRUCTION) {
                return encodeInstruction(line, Mnemonics.lookup(line.name()));
            }
            if (line.kind() == Line.Kind.DIRECTIVE) {
                return encodeDirective(sl, line);
            }
            return new int[0];
        }

        private int[] encodeInstruction(Line line, Mnemonics.Spec spec) {
            List<Operand> ops = line.operands();
            int op = spec.opcode();
            Mnemonics.Form form = spec.form();
            switch (form) {
                case NONE:
                    expectForm(line, spec, 0, 0);
                    return new int[] {enc(op, 0, 0, 0)};
                case R:
                    expectForm(line, spec, 1, 1);
                    return new int[] {enc(op, reg(ops.get(0), "operand"), 0, spec.fixed())};
                case RR:
                    expectForm(line, spec, 2, 2);
                    return new int[] {enc(op, reg(ops.get(0), "first operand"), reg(ops.get(1), "second operand"), 0)};
                case R_WORD:
                    expectForm(line, spec, 2, 2);
                    return new int[] {enc(op, reg(ops.get(0), "first operand"), 0, 0), wordOperand(ops.get(1), "value")};
                case R_MEM: {
                    expectForm(line, spec, 2, 2);
                    int rd = reg(ops.get(0), "first operand");
                    Operand.Mem m = mem(ops.get(1));
                    int imm = m.offset() == null ? 0 : imm5(evaluate(m.offset(), false), m.offset().column());
                    return new int[] {enc(op, rd, Instruction.reg(m.base()), imm)};
                }
                case R_IMM:
                    expectForm(line, spec, 2, 2);
                    return new int[] {enc(op, reg(ops.get(0), "first operand"), 0, imm5Operand(ops.get(1)))};
                case R_OPT_IMM: {
                    expectForm(line, spec, 1, 2);
                    int imm = ops.size() == 2 ? imm5Operand(ops.get(1)) : 1;
                    return new int[] {enc(op, reg(ops.get(0), "first operand"), 0, imm)};
                }
                case ADDR:
                    expectForm(line, spec, 1, 1);
                    return new int[] {enc(op, spec.fixed(), 0, 0), wordOperand(ops.get(0), "address")};
                case COND_ADDR: {
                    expectForm(line, spec, 2, 2);
                    Operand c = ops.get(0);
                    long cond = evaluate(value(c), false);
                    if (!Instruction.isValidCondition((int) Math.max(-9, Math.min(9, cond)))) {
                        throw new AsmFailure(c.column(), "JIF condition " + cond
                                + " is not valid; expected -2 .. 3 (or use JN, JZ, JP, JNZ, JLE, JGE)");
                    }
                    return new int[] {enc(op, (int) cond, 0, 0), wordOperand(ops.get(1), "address")};
                }
                case ADDR3:
                    expectForm(line, spec, 3, 3);
                    return new int[] {enc(op, 0, 0, 0), wordOperand(ops.get(0), "address"),
                        wordOperand(ops.get(1), "address"), wordOperand(ops.get(2), "address")};
                default:
                    throw new IllegalStateException();
            }
        }

        private int[] encodeDirective(SourceLine sl, Line line) {
            List<Operand> ops = line.operands();
            switch (line.name()) {
                case "word": {
                    int[] out = new int[ops.size()];
                    for (int i = 0; i < out.length; i++) {
                        out[i] = wordOperand(ops.get(i), "value");
                    }
                    return out;
                }
                case "str": {
                    String s = string(ops.get(0));
                    int[] out = new int[s.length() + 1];   // last word stays 0: the terminator
                    for (int i = 0; i < s.length(); i++) {
                        out[i] = s.charAt(i);
                    }
                    return out;
                }
                case "fill": {
                    int value = wordOperand(ops.get(1), "value");
                    int[] out = new int[sl.size];
                    java.util.Arrays.fill(out, value);
                    return out;
                }
                case "pix": {
                    int word = 0;
                    for (Operand o : ops) {
                        long p = evaluate(value(o), false);
                        if (p < -13 || p > 13) {
                            throw new AsmFailure(o.column(), "pixel value " + p + " is out of range (-13 .. 13)");
                        }
                        word = word * 27 + (int) p;   // p0·19683 + p1·729 + p2·27 + p3 (SPEC §4.3)
                    }
                    return new int[] {word};
                }
                default:                               // org, include: no words
                    return new int[0];
            }
        }

        private int enc(int opcode, int rdField, int rsField, int imm5) {
            return Instruction.encode(opcode, rdField, rsField, imm5);
        }

        // ============================================================ operand helpers

        /** The operand count each form allows, checked already in pass 1. */
        private void checkOperandCount(Line line, Mnemonics.Spec spec) {
            switch (spec.form()) {
                case NONE -> expectForm(line, spec, 0, 0);
                case R, ADDR -> expectForm(line, spec, 1, 1);
                case R_OPT_IMM -> expectForm(line, spec, 1, 2);
                case ADDR3 -> expectForm(line, spec, 3, 3);
                default -> expectForm(line, spec, 2, 2);
            }
        }

        private void expectForm(Line line, Mnemonics.Spec spec, int min, int max) {
            int n = line.operands().size();
            if (n < min || n > max) {
                int column = n > max ? line.operands().get(max).column() : line.nameColumn();
                throw new AsmFailure(column, spec.name() + " expects " + spec.form().expected + ", found "
                        + (n == 0 ? "no operands" : n == 1 ? "1 operand" : n + " operands"));
            }
        }

        private void expectOperands(Line line, int n, String expected) {
            int found = line.operands().size();
            if (found != n) {
                int column = found > n ? line.operands().get(n).column() : line.nameColumn();
                throw new AsmFailure(column, "." + line.name() + " expects " + expected + ", found "
                        + (found == 0 ? "nothing" : found + " operands"));
            }
        }

        /** The register FIELD value of a register operand. */
        private int reg(Operand o, String which) {
            if (o instanceof Operand.Reg r) {
                return Instruction.reg(r.number());
            }
            throw new AsmFailure(o.column(), "expected a register (R0 .. R8) as " + which + ", found " + describe(o));
        }

        private Operand.Mem mem(Operand o) {
            if (o instanceof Operand.Mem m) {
                return m;
            }
            throw new AsmFailure(o.column(), "expected a memory operand such as [R1] or [R1+2], found " + describe(o));
        }

        private Expr value(Operand o) {
            if (o instanceof Operand.Value v) {
                return v.expr();
            }
            throw new AsmFailure(o.column(), "expected a value, found " + describe(o));
        }

        private String string(Operand o) {
            if (o instanceof Operand.Str s) {
                return s.text();
            }
            throw new AsmFailure(o.column(), "expected a string in double quotes, found " + describe(o));
        }

        private int wordOperand(Operand o, String what) {
            return word(evaluate(value(o), false), o.column(), what);
        }

        private int imm5Operand(Operand o) {
            return imm5(evaluate(value(o), false), o.column());
        }

        private int word(long v, int column, String what) {
            if (!Word.isValid(v)) {
                throw new AsmFailure(column, what + " " + v + " is out of range for a word (-265720 .. 265720)");
            }
            return (int) v;
        }

        private int imm5(long v, int column) {
            if (v < -Instruction.IMM5_MAX || v > Instruction.IMM5_MAX) {
                throw new AsmFailure(column, "value " + v + " doesn't fit in the 5-trit immediate field "
                        + "(-121 .. 121); use LDI and a register for larger values");
            }
            return (int) v;
        }

        private String describe(Operand o) {
            if (o instanceof Operand.Reg r) {
                return "register " + Registers.name(r.number());
            }
            if (o instanceof Operand.Mem) {
                return "a memory operand";
            }
            if (o instanceof Operand.Str) {
                return "a string";
            }
            return "a value";
        }

        // ============================================================ expressions

        /**
         * The value of an expression. In pass 1 ({@code firstPass}), only
         * labels defined above this line are known.
         */
        private long evaluate(Expr e, boolean firstPass) {
            if (e instanceof Expr.Num n) {
                return n.value();
            }
            if (e instanceof Expr.Neg n) {
                return -evaluate(n.inner(), firstPass);
            }
            if (e instanceof Expr.Bin b) {
                long l = evaluate(b.left(), firstPass);
                long r = evaluate(b.right(), firstPass);
                return b.plus() ? l + r : l - r;
            }
            Expr.Sym s = (Expr.Sym) e;
            return symbol(s, firstPass);
        }

        private long symbol(Expr.Sym s, boolean firstPass) {
            Long label = labels.get(s.name());
            if (label != null) {
                return label;
            }
            Constant c = constants.get(s.name());
            if (c != null) {
                return constantValue(s, c, firstPass);
            }
            Integer builtin = builtins.get(s.name());
            if (builtin != null) {
                return builtin;
            }
            if (firstPass) {
                throw new AsmFailure(s.column(), "'" + s.name() + "' must be defined above this line "
                        + "(.org and .fill need their value in the first pass)");
            }
            throw new AsmFailure(s.column(), "unknown name '" + s.name() + "'" + suggestion(s.name()));
        }

        /**
         * The value of constant {@code c}, referenced by {@code ref}. If its
         * definition has an error, that error is reported once, at the
         * definition's own line (the columns in its expression belong to that
         * line), and the line that used it fails silently.
         */
        private long constantValue(Expr.Sym ref, Constant c, boolean firstPass) {
            if (c.value != null) {
                return c.value;
            }
            if (c.failed) {
                throw AsmFailure.silent();
            }
            if (c.evaluating) {
                throw new AsmFailure(ref.column(), "constant '" + ref.name() + "' is defined in terms of itself");
            }
            c.evaluating = true;
            try {
                long v = evaluate(c.expr, firstPass);
                c.value = v;
                return v;
            } catch (AsmFailure f) {
                c.failed = true;
                error(c.line, f.column, f.getMessage(), f.silent);
                throw AsmFailure.silent();
            } finally {
                c.evaluating = false;
            }
        }

        /** A "did you mean" hint when only the upper/lower case differs. */
        private String suggestion(String name) {
            List<String> all = new ArrayList<>(definedAt.keySet());
            all.addAll(builtins.keySet());
            for (String known : all) {
                if (known.equalsIgnoreCase(name)) {
                    return " (did you mean '" + known + "'? names are case-sensitive)";
                }
            }
            if (Mnemonics.lookup(name.toUpperCase(Locale.ROOT)) != null) {
                return " (it is an instruction name; is a line break missing?)";
            }
            return "";
        }

        // ============================================================ output

        private TriImage buildImage() {
            // Find the range of addresses used, and check that no two lines share an address.
            Map<Long, SourceLine> owner = new HashMap<>();
            long lo = Long.MAX_VALUE;
            long hi = Long.MIN_VALUE;
            for (SourceLine sl : lines) {
                if (sl.words.length == 0) {
                    continue;
                }
                lo = Math.min(lo, sl.address);
                hi = Math.max(hi, sl.address + sl.words.length - 1);
            }
            if (lo > hi) {
                return new TriImage(origin, new int[0]);
            }
            if (hi - lo + 1 > Word.COUNT) {
                throw new IllegalStateException("impossible: image larger than the address space");
            }
            int[] image = new int[(int) (hi - lo + 1)];
            boolean[] used = new boolean[image.length];
            for (SourceLine sl : lines) {
                for (int i = 0; i < sl.words.length; i++) {
                    long a = sl.address + i;
                    int idx = (int) (a - lo);
                    if (used[idx]) {
                        SourceLine first = owner.get(a);
                        error(sl, 1, "address " + a + " is already used by " + first.file + ":" + first.number
                                + " (check your .org directives)", false);
                        break;
                    }
                    used[idx] = true;
                    owner.putIfAbsent(a, sl);
                    image[idx] = sl.words[i];
                }
            }
            return new TriImage((int) lo, image);
        }

        private Map<String, Integer> symbols() {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : labels.entrySet()) {
                out.put(e.getKey(), e.getValue().intValue());
            }
            for (Map.Entry<String, Constant> e : constants.entrySet()) {
                Long v = e.getValue().value;
                if (v == null) {
                    try {
                        v = evaluate(e.getValue().expr, false);
                    } catch (AsmFailure ignored) {
                        continue;
                    }
                }
                if (Word.isValid(v)) {
                    out.put(e.getKey(), v.intValue());
                }
            }
            return out;
        }

        private void error(SourceLine sl, int column, String message, boolean silent) {
            if (!silent && errors.size() < MAX_ERRORS) {
                errors.add(new AssemblyError(sl.file, sl.number, column, message));
            }
        }
    }

    // ================================================================ helpers

    /** Splits text into lines on \n, \r\n or \r. A final newline doesn't add an empty line. */
    static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                out.add(text.substring(start, i));
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        if (start < text.length()) {
            out.add(text.substring(start));
        }
        return out;
    }

    /** Resolves an include path relative to the folder of the including file. */
    static String resolve(String including, String requested) {
        String req = requested.replace('\\', '/');
        if (req.startsWith("/") || (req.length() > 1 && req.charAt(1) == ':')) {
            return req;
        }
        String inc = including.replace('\\', '/');
        int slash = inc.lastIndexOf('/');
        return slash < 0 ? req : inc.substring(0, slash + 1) + req;
    }
}
