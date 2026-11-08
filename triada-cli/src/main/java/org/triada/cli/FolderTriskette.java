package org.triada.cli;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.triada.core.TriImage;
import org.triada.core.triskette.TrisketteDevice;
import org.triada.core.triskette.TrisketteException;
import org.triada.core.triskette.TrisketteNames;
import org.triada.core.triskette.TriskettePort;

/**
 * A triskette that is a folder on the PC (M13): every file in it is a
 * {@code .tri} file named like the triskette file, e.g.
 * {@code triskettes/GAME.TRI}. Used by {@code triada run --disk DIR} and by
 * the desktop ({@code --disk DIR}, default {@code triskettes/}).
 *
 * <p>Files whose names aren't valid triskette names are ignored. Names are
 * matched without regard to case, so {@code game.tri} copied in by hand is
 * found as {@code GAME.TRI}. The folder is created on the first save.
 */
public final class FolderTriskette implements TriskettePort {

    private final Path dir;

    public FolderTriskette(Path dir) {
        this.dir = dir;
    }

    public Path folder() {
        return dir;
    }

    @Override
    public List<String> names() {
        return new ArrayList<>(files().keySet());
    }

    @Override
    public TriImage read(String name) {
        Path file = files().get(name);
        if (file == null) {
            return null;
        }
        try {
            return TriImage.fromBytes(Files.readAllBytes(file));
        } catch (IOException | IllegalArgumentException e) {
            throw new TrisketteException(TrisketteDevice.IO_ERROR, "can't read " + file + ": " + e.getMessage());
        }
    }

    @Override
    public void write(String name, TriImage file) {
        byte[] bytes = file.toBytes();
        Path target = files().getOrDefault(name, dir.resolve(name));    // keep the case of an existing file
        try {
            Files.createDirectories(dir);
            if (Files.getFileStore(dir).getUsableSpace() < bytes.length) {
                throw new TrisketteException(TrisketteDevice.NO_SPACE, "the disk of " + dir + " is full");
            }
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new TrisketteException(TrisketteDevice.IO_ERROR, "can't write " + target + ": " + e.getMessage());
        }
    }

    @Override
    public boolean delete(String name) {
        Path file = files().get(name);
        if (file == null) {
            return false;
        }
        try {
            Files.delete(file);
            return true;
        } catch (IOException e) {
            throw new TrisketteException(TrisketteDevice.IO_ERROR, "can't delete " + file + ": " + e.getMessage());
        }
    }

    /** The folder's files by triskette name; empty if the folder doesn't exist yet. */
    private TreeMap<String, Path> files() {
        TreeMap<String, Path> found = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return found;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = TrisketteNames.normalize(p.getFileName().toString());
                if (name != null && Files.isRegularFile(p)) {
                    found.putIfAbsent(name, p);
                }
            }
        } catch (IOException e) {
            throw new TrisketteException(TrisketteDevice.IO_ERROR, "can't list " + dir + ": " + e.getMessage());
        }
        return found;
    }
}
