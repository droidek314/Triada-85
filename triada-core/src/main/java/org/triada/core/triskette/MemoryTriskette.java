package org.triada.core.triskette;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.triada.core.TriImage;

/**
 * A triskette kept in memory (M13): the machine's default until the host
 * plugs in a real one, and the tests' triskette. An optional capacity, in
 * words, makes "no space" testable.
 */
public final class MemoryTriskette implements TriskettePort {

    private final TreeMap<String, TriImage> files = new TreeMap<>();
    private final int capacityWords;

    /** A triskette without a size limit. */
    public MemoryTriskette() {
        this(Integer.MAX_VALUE);
    }

    /** A triskette that holds at most {@code capacityWords} words in all its files together. */
    public MemoryTriskette(int capacityWords) {
        this.capacityWords = capacityWords;
    }

    @Override
    public List<String> names() {
        return new ArrayList<>(files.keySet());
    }

    @Override
    public TriImage read(String name) {
        return files.get(name);
    }

    @Override
    public void write(String name, TriImage file) {
        long used = 0;
        for (var e : files.entrySet()) {
            used += e.getKey().equals(name) ? 0 : e.getValue().size();
        }
        if (used + file.size() > capacityWords) {
            throw new TrisketteException(TrisketteDevice.NO_SPACE, "the triskette is full");
        }
        files.put(name, file);
    }

    @Override
    public boolean delete(String name) {
        return files.remove(name) != null;
    }
}
