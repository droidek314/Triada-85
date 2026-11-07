package org.triada.assembler;

/**
 * One problem found in the source (SPEC §9.4): where it is and what is wrong.
 * Lines and columns count from 1, like in any text editor.
 *
 * @param file    the source file's name as given to the assembler
 * @param line    line number, from 1
 * @param column  column of the offending text, from 1
 * @param message what was expected and what was found
 */
public record AssemblyError(String file, int line, int column, String message) {

    /** The usual compiler format: {@code hello.tasm:3:9: expected …, found …}. */
    @Override
    public String toString() {
        return file + ":" + line + ":" + column + ": " + message;
    }
}
