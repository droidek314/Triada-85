package org.triada.rom;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.triada.core.Machine;
import org.triada.core.MachineProfile;
import org.triada.core.MachineProfiles;
import org.triada.core.TriImage;
import org.triada.core.ternary.Word;

/**
 * The TRIADA-85 ROM, built in: the kernel, the monitor and the font,
 * assembled from {@code rom/*.tasm} during the build (see
 * {@code RomGenerator}).
 *
 * <pre>
 *   Machine m = Rom.newMachine();      // power on: the CPU starts in the ROM and boots
 *   Rom.autorun(m, program);           // optional: start this program after boot
 * </pre>
 */
public final class Rom {

    /** The 27-letter alphabet of the encoded words: 4 characters = 12 trits. */
    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQ";

    private static TriImage image;
    private static TriImage extension;
    private static Map<String, Integer> symbols;

    private Rom() {
    }

    /** The ROM image: it starts at the first ROM address, −19,683. */
    public static synchronized TriImage image() {
        if (image == null) {
            image = new TriImage(MachineProfiles.TRIADA_85.romStart(), decode(RomData.SIZE, RomData.WORDS));
        }
        return image;
    }

    /** The extension ROM (TRIBASIC, M14), starting at {@code EXT_ROM_START}. */
    public static synchronized TriImage extensionImage() {
        if (extension == null) {
            extension = new TriImage(MachineProfiles.TRIADA_85.extRomStart(),
                    decode(RomData.EXT_SIZE, RomData.EXT_WORDS));
        }
        return extension;
    }

    private static int[] decode(int size, String[] chunks) {
        int[] words = new int[size];
        int i = 0;
        for (String chunk : chunks) {
            for (int c = 0; c < chunk.length(); c += 4) {
                int u = 0;
                for (int k = 0; k < 4; k++) {
                    u = u * 27 + ALPHABET.indexOf(chunk.charAt(c + k));
                }
                words[i++] = u - Word.MAX;
            }
        }
        return words;
    }

    /** All labels and constants of the ROM source, e.g. "AUTORUN" or "putchar". */
    public static synchronized Map<String, Integer> symbols() {
        if (symbols == null) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (int i = 0; i < RomData.SYMBOL_NAMES.length; i++) {
                m.put(RomData.SYMBOL_NAMES[i], RomData.SYMBOL_VALUES[i]);
            }
            symbols = Collections.unmodifiableMap(m);
        }
        return symbols;
    }

    /** The value of a ROM symbol. @throws IllegalArgumentException if there is none */
    public static int address(String symbol) {
        Integer v = symbols().get(symbol);
        if (v == null) {
            throw new IllegalArgumentException("the ROM has no symbol '" + symbol + "'");
        }
        return v;
    }

    /** A TRIADA-85 with this ROM installed, in power-on state: its first frame starts the boot. */
    public static Machine newMachine() {
        return newMachine(MachineProfiles.TRIADA_85);
    }

    /**
     * A machine of the model {@code profile} with the ROM images its profile
     * names ("main", "tribasic"), in power-on state (M16).
     *
     * @throws IllegalArgumentException if the profile names an image this ROM doesn't have
     */
    public static Machine newMachine(MachineProfile profile) {
        Machine m = new Machine(profile);
        for (String name : profile.romImages()) {
            switch (name) {
                case "main" -> m.bus().loadRom(image().words());
                case "tribasic" -> m.bus().loadExtensionRom(extensionImage().words());
                default -> throw new IllegalArgumentException("no ROM image called '" + name + "'");
            }
        }
        m.powerOn();
        return m;
    }

    /**
     * Puts {@code program} into RAM and asks the ROM to start it after boot,
     * as if RUN had been typed (DECISIONS.md D-028). Call it right after
     * {@link #newMachine()}, before the first frame.
     *
     * @throws IllegalArgumentException if the program isn't entirely inside RAM
     */
    public static void autorun(Machine m, TriImage program) {
        long last = (long) program.loadAddress() + program.size() - 1;
        MachineProfile p = m.profile();
        if (program.loadAddress() < p.ramStart() || last > p.ramEnd()) {
            throw new IllegalArgumentException("the program occupies addresses " + program.loadAddress() + " .. "
                    + last + ", which is not inside RAM (" + p.ramStart() + " .. " + p.ramEnd() + ")");
        }
        m.bus().loadRam(program.loadAddress(), program.words());
        m.bus().write(address("AUTORUN"), program.loadAddress());
    }
}
