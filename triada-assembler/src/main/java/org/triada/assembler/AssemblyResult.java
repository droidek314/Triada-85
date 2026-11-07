package org.triada.assembler;

import java.util.Map;
import org.triada.core.TriImage;

/**
 * What a successful assembly produces.
 *
 * @param image    the words and their load address, ready to be written as a {@code .tri} file
 * @param listing  the {@code .lst} listing text (SPEC §9.5)
 * @param symbols  every label and constant the program defined, with its value
 *                 (the debugger can use these to show names instead of numbers)
 */
public record AssemblyResult(TriImage image, String listing, Map<String, Integer> symbols) {
}
