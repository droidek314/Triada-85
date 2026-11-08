package org.triada.core.triskette;

import java.util.List;
import org.triada.core.TriImage;
import org.triada.core.io.IoDevice;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.Bus;
import org.triada.core.memory.MemoryMap;

/**
 * The triskette drive (SPEC §5.1, M13), on I/O registers 23–27. A program
 * puts a file name, an address and a length into TS_NAME, TS_ADDR and
 * TS_LEN, then writes a command number into TS_CMD. The command finishes at
 * once, and TS_STATUS says how it went.
 *
 * <p>The files themselves live behind a {@link TriskettePort} that the host
 * plugs in with {@link #setPort}: a folder, browser storage, or memory. This
 * class never touches a file itself, so triada-core stays free of file I/O.
 */
public final class TrisketteDevice implements IoDevice {

    public static final int FIRST_REGISTER = IoRegisters.TS_CMD;
    public static final int REGISTER_COUNT = 5;

    /** TS_CMD values. */
    public static final int CMD_SAVE = 1;
    public static final int CMD_LOAD = 2;
    public static final int CMD_DIR = 3;
    public static final int CMD_DELETE = 4;
    public static final int CMD_LOAD_HOME = 5;

    /** TS_STATUS values. */
    public static final int OK = 0;
    public static final int NOT_FOUND = -1;
    public static final int NO_SPACE = -2;
    public static final int BAD_NAME = -3;
    public static final int IO_ERROR = -4;

    private final Bus bus;
    private TriskettePort port = new MemoryTriskette();
    private int name;
    private int addr;
    private int len;
    private int status;

    public TrisketteDevice(Bus bus) {
        this.bus = bus;
    }

    // ---------------------------------------------------------------- host side

    /** Puts a different triskette into the drive. */
    public void setPort(TriskettePort port) {
        this.port = port;
    }

    public TriskettePort port() {
        return port;
    }

    /** Power-on: the registers are cleared; the triskette keeps its files. */
    public void reset() {
        name = 0;
        addr = 0;
        len = 0;
        status = 0;
    }

    // ---------------------------------------------------------------- CPU side

    @Override
    public int read(int k) {
        switch (k) {
            case IoRegisters.TS_NAME:
                return name;
            case IoRegisters.TS_ADDR:
                return addr;
            case IoRegisters.TS_LEN:
                return len;
            case IoRegisters.TS_STATUS:
                return status;
            default:
                return 0;       // TS_CMD is write-only
        }
    }

    @Override
    public void write(int k, int value) {
        switch (k) {
            case IoRegisters.TS_CMD:
                status = run(value);
                break;
            case IoRegisters.TS_NAME:
                name = value;
                break;
            case IoRegisters.TS_ADDR:
                addr = value;
                break;
            case IoRegisters.TS_LEN:
                len = value;
                break;
            default:
                break;          // TS_STATUS is read-only
        }
    }

    /** Runs one command and returns its status. */
    private int run(int command) {
        try {
            switch (command) {
                case CMD_SAVE:
                    return save();
                case CMD_LOAD:
                    return load(false);
                case CMD_LOAD_HOME:
                    return load(true);
                case CMD_DIR:
                    return dir();
                case CMD_DELETE:
                    String file = fileName();
                    return file == null ? BAD_NAME : port.delete(file) ? OK : NOT_FOUND;
                default:
                    return IO_ERROR;    // no such command (D-045)
            }
        } catch (TrisketteException e) {
            return e.status();
        } catch (RuntimeException e) {
            return IO_ERROR;            // e.g. a broken file on the host
        }
    }

    private int save() {
        String file = fileName();
        if (file == null) {
            return BAD_NAME;
        }
        if (len < 0 || !inRam(addr, len)) {
            return IO_ERROR;            // only whole blocks of RAM are saved (D-045)
        }
        int[] words = new int[len];
        for (int i = 0; i < len; i++) {
            words[i] = bus.peek(addr + i);
        }
        port.write(file, new TriImage(addr, words));
        return OK;
    }

    private int load(boolean home) {
        String file = fileName();
        if (file == null) {
            return BAD_NAME;
        }
        TriImage image = port.read(file);
        if (image == null) {
            return NOT_FOUND;
        }
        if (home) {
            addr = image.loadAddress();
        }
        len = image.size();
        return copyIntoRam(addr, image.words()) ? OK : IO_ERROR;
    }

    private int dir() {
        List<String> names = port.names();
        int needed = 1;                             // the closing extra 0
        for (String n : names) {
            needed += n.length() + 1;
        }
        if (needed > len) {
            len = needed;                           // tells the program how much room it needs
            return NO_SPACE;
        }
        int[] words = new int[needed];
        int at = 0;
        for (String n : names) {
            for (int i = 0; i < n.length(); i++) {
                words[at++] = n.charAt(i);
            }
            at++;                                   // 0 after each name; the last 0 is already there
        }
        len = needed;
        return copyIntoRam(addr, words) ? OK : IO_ERROR;
    }

    /** Writes the words that land inside RAM; false if any had to be left out ("clipped"). */
    private boolean copyIntoRam(int start, int[] words) {
        boolean all = true;
        for (int i = 0; i < words.length; i++) {
            long a = (long) start + i;
            if (a >= MemoryMap.RAM_START && a <= MemoryMap.RAM_END) {
                bus.write((int) a, words[i]);
            } else {
                all = false;
            }
        }
        return all;
    }

    /** The 0-terminated name at TS_NAME, checked and in upper case; null if it isn't a valid name. */
    private String fileName() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i <= TrisketteNames.MAX_LENGTH; i++) {
            long a = (long) name + i;
            if (a > MemoryMap.RAM_END) {
                return null;
            }
            int c = bus.peek((int) a);
            if (c == 0) {
                return TrisketteNames.normalize(b.toString());
            }
            if (c < 32 || c > 126) {
                return null;
            }
            b.append((char) c);
        }
        return null;                                // no 0 within 13 words: too long
    }

    private static boolean inRam(int start, int count) {
        return start >= MemoryMap.RAM_START && (long) start + count - 1 <= MemoryMap.RAM_END;
    }
}
