package org.triada.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * SPEC §9.4: every error states the file, the line, the column, what was
 * expected and what was found. Each case checks all of that.
 */
class AssemblerErrorTest {

    private static List<AssemblyError> errors(String source) {
        AssemblyException e = assertThrows(AssemblyException.class,
                () -> new Assembler(SourceLoader.NONE).assembleSource("err.tasm", source));
        return e.errors();
    }

    private static AssemblyError single(String source) {
        List<AssemblyError> errs = errors(source);
        assertEquals(1, errs.size(), errs.toString());
        return errs.get(0);
    }

    /** source (with | for newlines), line, column, and a piece of the message. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '#', value = {
        // unknown things
        "FOO R1# 1# 1# unknown instruction 'FOO'",
        ".bar 5# 1# 1# unknown directive '.bar'",
        "LDI R0, nowhere# 1# 9# unknown name 'nowhere'",
        "loop: NOP|JMP Loop# 2# 5# did you mean 'loop'? names are case-sensitive",
        // wrong operands
        "ADD R1# 1# 1# ADD expects two registers, e.g. R0, R1, found 1 operand",
        "ADD R1, R2, R3# 1# 13# found 3 operands",
        "HLT R1# 1# 5# HLT expects no operands, found 1 operand",
        "ADD R1, 5# 1# 9# expected a register (R0 .. R8) as second operand, found a value",
        "LD R0, R1# 1# 8# expected a memory operand such as [R1] or [R1+2], found register R1",
        "LDI R0, [R1]# 1# 9# expected a value, found a memory operand",
        "JMP R3# 1# 5# expected a value, found register R3",
        "NEG 5# 1# 5# expected a register (R0 .. R8) as operand, found a value",
        "MOV R9, R1# 1# 5# expected a register (R0 .. R8) as first operand, found a value",
        "LD R0, [5]# 1# 9# expected a register (R0 .. R8) after '[', found '5'",
        "LD R0, [R1*2]# 1# 11# unexpected character '*'",
        "LD R0, [R1+2# 1# 13# expected '+', '-' or ']' in memory operand, found end of line",
        "LDI R0, R1+1# 1# 11# register arithmetic needs brackets",
        "ADD R1 R2# 1# 8# expected ',' or end of line, found 'R2'",
        // ranges
        "ADDI R0, 122# 1# 10# value 122 doesn't fit in the 5-trit immediate field (-121 .. 121)",
        "LD R0, [R1-122]# 1# 11# value -122 doesn't fit",
        "LDI R0, 265721# 1# 9# value 265721 is out of range for a word",
        ".word -265721# 1# 7# out of range for a word",
        "JMP 300000# 1# 5# address 300000 is out of range",
        "JIF 4, 0# 1# 5# JIF condition 4 is not valid; expected -2 .. 3",
        ".pix 1, 2, 3, 14# 1# 15# pixel value 14 is out of range (-13 .. 13)",
        ".fill -1, 0# 1# 7# .fill count -1 is out of range",
        // names
        "R1: NOP# 1# 1# 'R1' is a register name and can't be a label",
        "sp = 5# 1# 1# 'sp' is a register name and can't be a constant",
        "MAP = 5# 1# 1# 'MAP' is a built-in symbol and can't be redefined",
        "x: NOP|x: NOP# 2# 1# 'x' is already defined at err.tasm:1",
        "A = B|B = A|.word A# 2# 5# constant 'A' is defined in terms of itself",
        "X = nowhere + 1# 1# 5# unknown name 'nowhere'",
        "X = Y|Y = nowhere|LDI R0, X# 2# 5# unknown name 'nowhere'",
        "x: A = 5# 1# 4# a constant definition can't have a label",
        // directives
        ".org later|later: NOP# 1# 6# 'later' must be defined above this line",
        ".str 5# 1# 6# expected a string in double quotes, found a value",
        ".str \"a\", \"b\"# 1# 11# .str expects one string",
        ".word# 1# 1# .word expects one or more values, found none",
        ".pix 1, 2# 1# 1# .pix expects four pixel values",
        ".include \"missing.tasm\"# 1# 10# can't find included file 'missing.tasm'",
        // layout
        ".org 265720|.word 1, 2# 2# 1# the program runs past the last address 265720",
        ".org 300|.word 1|.org 300|.word 2# 4# 1# address 300 is already used by err.tasm:2",
        "5# 1# 1# expected a label, instruction, directive or constant, found '5'",
        "NOP: , # 1# 6# expected a label, instruction, directive or constant, found ','",
        "LDI R0, ADD# 1# 9# it is an instruction name; is a line break missing?",
    })
    void errorCase(String source, int line, int column, String message) {
        AssemblyError e = single(source.replace('|', '\n'));
        assertEquals("err.tasm", e.file());
        assertEquals(line, e.line(), e.toString());
        assertEquals(column, e.column(), e.toString());
        assertTrue(e.message().contains(message), "expected message containing \"" + message + "\", got: " + e);
    }

    @Test
    void errorsAreFormattedLikeACompiler() {
        AssemblyError e = single("NOP\n  FOO");
        assertEquals("err.tasm:2:3: unknown instruction 'FOO'; expected a mnemonic such as LDI, ADD or JMP",
                e.toString());
    }

    @Test
    void severalErrorsAreReportedAtOnce() {
        List<AssemblyError> errs = errors("FOO\nNOP\nBAR\nLDI R0, 9999999999");
        assertEquals(3, errs.size(), errs.toString());
        assertEquals(List.of(1, 3, 4), errs.stream().map(AssemblyError::line).toList());
        AssemblyException e = assertThrows(AssemblyException.class,
                () -> new Assembler(SourceLoader.NONE).assembleSource("err.tasm", "FOO\nBAR"));
        assertTrue(e.getMessage().startsWith("2 errors\nerr.tasm:1:1:"), e.getMessage());
    }

    @Test
    void operandCountsAreCheckedInPass1SoTheyAreReportedWithOtherPass1Errors() {
        List<AssemblyError> errs = errors("ADD R1\nFOO");
        assertEquals(List.of(1, 2), errs.stream().map(AssemblyError::line).toList(), errs.toString());
    }

    @Test
    void errorsStopAtTheLimit() {
        String many = "FOO\n".repeat(Assembler.MAX_ERRORS + 50);
        assertEquals(Assembler.MAX_ERRORS, errors(many).size());
    }

    @Test
    void pass2DoesNotRunWhenPass1Failed() {
        // Line 2 would also fail in pass 2 (unknown name), but addresses after a bad
        // line can't be trusted, so only the pass-1 error is reported.
        List<AssemblyError> errs = errors("FOO\nJMP nowhere");
        assertEquals(1, errs.size(), errs.toString());
    }

    @Test
    void errorsInIncludedFilesNameThatFile() {
        Map<String, String> files = Map.of("main.tasm", "NOP\n.include \"lib.tasm\"", "lib.tasm", "NOP\n  BAD");
        AssemblyException e = assertThrows(AssemblyException.class,
                () -> new Assembler(files::get).assemble("main.tasm"));
        AssemblyError err = e.errors().get(0);
        assertEquals("lib.tasm", err.file());
        assertEquals(2, err.line());
        assertEquals(3, err.column());
    }

    @Test
    void includeCyclesAreDetected() {
        Map<String, String> files = Map.of("a.tasm", ".include \"b.tasm\"", "b.tasm", ".include \"a.tasm\"");
        AssemblyException e = assertThrows(AssemblyException.class, () -> new Assembler(files::get).assemble("a.tasm"));
        assertTrue(e.errors().get(0).message().contains("includes itself (a.tasm -> b.tasm -> a.tasm)"),
                e.getMessage());
        assertEquals("b.tasm", e.errors().get(0).file());
    }

    @Test
    void missingMainFile() {
        AssemblyException e = assertThrows(AssemblyException.class,
                () -> new Assembler(SourceLoader.NONE).assemble("nope.tasm"));
        assertEquals("nope.tasm:0:0: can't read source file", e.errors().get(0).toString());
    }
}
