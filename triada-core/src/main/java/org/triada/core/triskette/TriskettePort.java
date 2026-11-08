package org.triada.core.triskette;

import java.util.List;
import org.triada.core.TriImage;

/**
 * Where the files of a triskette really live (SPEC §5.1, M13): a folder on the
 * desktop, browser storage on the web, a map in tests. The machine only sees
 * the {@link TrisketteDevice}; the host plugs in one of these behind it.
 *
 * <p>Names given to a port are always valid, upper-case triskette names
 * ({@link TrisketteNames}). Every file is a {@link TriImage}: the words plus
 * the address they were saved from.
 *
 * <p>A port that can't do its job throws {@link TrisketteException} with the
 * status the program should see: {@link TrisketteDevice#NO_SPACE} or
 * {@link TrisketteDevice#IO_ERROR}.
 */
public interface TriskettePort {

    /** All file names, sorted alphabetically. */
    List<String> names();

    /** The file called {@code name}, or null if there is none. */
    TriImage read(String name);

    /** Stores {@code file} as {@code name}, replacing a file of that name. */
    void write(String name, TriImage file);

    /** Removes the file; false if there was none. */
    boolean delete(String name);
}
