package org.triada.core;

import java.util.List;
import java.util.Set;
import org.triada.core.ternary.Word;

/**
 * Everything that makes one TRIADA model what it is (M16): its memory map,
 * where the video hardware looks in RAM, its timing, its screen, its sound
 * chip, which devices are plugged in and which ROM images it carries. A
 * {@link Machine} is built from a profile, so a later model (a TRIADA-88, say)
 * is a new profile, not a new emulator. The values of the TRIADA-85 are
 * {@link MachineProfiles#TRIADA_85}, exactly those of {@code docs/SPEC.md}.
 *
 * <p>What stays the same for every model is not here: the 12-trit word, the
 * instruction set, the pixel packing and the I/O register numbers of each
 * device. Those define the TRIADA family itself.
 *
 * @param id              the name used on the command line, e.g. "triada-85"
 * @param name            the name shown to people, e.g. "TRIADA-85"
 * @param specVersion     the version of the specification it follows
 * @param ramStart        first RAM address (SPEC §2.2)
 * @param ramSize         RAM words
 * @param romStart        first main ROM address; also where the CPU starts
 * @param romSize         main ROM words
 * @param extRomStart     first extension ROM address (SPEC §2.5)
 * @param extRomSize      extension ROM words; 0 = none
 * @param ioBase          address of I/O register 0 (SPEC §5)
 * @param ioSize          number of I/O register slots
 * @param programStart    where programs load (SPEC §2.3)
 * @param tiles           tile patterns in RAM (SPEC §4.4)
 * @param map             tile map in RAM (SPEC §4.5)
 * @param sprites         sprite table in RAM (SPEC §4.7)
 * @param freeStart       start of the free / stack area (SPEC §2.3)
 * @param cyclesPerFrame  CPU cycles per frame (SPEC §6)
 * @param framesPerSecond frames per second (SPEC §6)
 * @param screenWidth     visible screen in pixels (SPEC §4.1)
 * @param screenHeight    visible screen in pixels
 * @param border          border width in pixels on each side (D-020)
 * @param tileSize        tile edge in pixels; 8 (two words of four pixels per row)
 * @param tileCount       number of tiles (SPEC §4.4)
 * @param spriteCount     number of sprites (SPEC §4.7)
 * @param voices          sound voices, 1 … 3 (SPEC §7)
 * @param devices         the devices plugged into the I/O registers
 * @param romImages       the ROM images to load, by name, e.g. "main", "tribasic"
 */
public record MachineProfile(
        String id,
        String name,
        String specVersion,
        int ramStart,
        int ramSize,
        int romStart,
        int romSize,
        int extRomStart,
        int extRomSize,
        int ioBase,
        int ioSize,
        int programStart,
        int tiles,
        int map,
        int sprites,
        int freeStart,
        int cyclesPerFrame,
        int framesPerSecond,
        int screenWidth,
        int screenHeight,
        int border,
        int tileSize,
        int tileCount,
        int spriteCount,
        int voices,
        Set<Device> devices,
        List<String> romImages) {

    /** The devices a model may have. */
    public enum Device {
        /** FRAME and RAND (SPEC §5). */
        SYSTEM,
        /** Joystick, buttons and the KEY queue. */
        INPUT,
        /** SCROLL_X, SCROLL_Y, BORDER and the picture. */
        VIDEO,
        /** The voices. */
        SOUND,
        /** DEBUG_CHAR, DEBUG_NUM, HOST_EXIT: the link to the host. */
        DEBUG,
        /** The triskette drive (SPEC §5.1). */
        TRISKETTE
    }

    /** Checks that the regions fit in the word range and don't overlap; copies the collections. */
    public MachineProfile {
        devices = Set.copyOf(devices);
        romImages = List.copyOf(romImages);
        checkRange("RAM", ramStart, ramSize);
        checkRange("ROM", romStart, romSize);
        checkRange("I/O", ioBase, ioSize);
        if (extRomSize > 0) {
            checkRange("extension ROM", extRomStart, extRomSize);
        }
        int[][] regions = {{ramStart, ramSize}, {romStart, romSize}, {ioBase, ioSize}, {extRomStart, extRomSize}};
        for (int i = 0; i < regions.length; i++) {
            for (int j = i + 1; j < regions.length; j++) {
                if (regions[i][1] > 0 && regions[j][1] > 0
                        && regions[i][0] <= regions[j][0] + regions[j][1] - 1
                        && regions[j][0] <= regions[i][0] + regions[i][1] - 1) {
                    throw new IllegalArgumentException(id + ": memory regions " + i + " and " + j + " overlap");
                }
            }
        }
        if (tileSize != 8) {
            throw new IllegalArgumentException(id + ": tiles are 8 x 8 (two words of four pixels per row)");
        }
        if (screenWidth % tileSize != 0 || screenHeight % tileSize != 0) {
            throw new IllegalArgumentException(id + ": the screen must be whole tiles");
        }
        if (voices < 1 || voices > 3) {
            throw new IllegalArgumentException(id + ": 1 to 3 voices (their registers are 10 ... 18)");
        }
        if (cyclesPerFrame < 1 || framesPerSecond < 1) {
            throw new IllegalArgumentException(id + ": timing must be positive");
        }
    }

    private static void checkRange(String what, int start, int size) {
        if (size < 1 || !Word.isValid(start) || (long) start + size - 1 > Word.MAX) {
            throw new IllegalArgumentException(what + " " + start + " (+" + size + ") is outside the word range");
        }
    }

    // ---------------------------------------------------------------- derived values

    public int ramEnd() {
        return ramStart + ramSize - 1;
    }

    public int romEnd() {
        return romStart + romSize - 1;
    }

    public int ioEnd() {
        return ioBase + ioSize - 1;
    }

    public int extRomEnd() {
        return extRomStart + extRomSize - 1;
    }

    /** SP after boot: one past the top of RAM; the stack grows down (SPEC §2.3). */
    public int stackTop() {
        return ramEnd() + 1;
    }

    /** The whole picture, border included. */
    public int frameWidth() {
        return screenWidth + 2 * border;
    }

    public int frameHeight() {
        return screenHeight + 2 * border;
    }

    /** Tile map columns and rows: the screen in tiles. */
    public int mapColumns() {
        return screenWidth / tileSize;
    }

    public int mapRows() {
        return screenHeight / tileSize;
    }

    /** True if the model has that device. */
    public boolean has(Device device) {
        return devices.contains(device);
    }

    /** True if {@code address} is RAM. */
    public boolean isRam(int address) {
        return address >= ramStart && address <= ramEnd();
    }
}
