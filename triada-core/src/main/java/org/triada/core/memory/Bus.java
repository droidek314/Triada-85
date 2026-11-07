package org.triada.core.memory;

import static org.triada.core.memory.MemoryMap.IO_BASE;
import static org.triada.core.memory.MemoryMap.IO_END;
import static org.triada.core.memory.MemoryMap.RAM_END;
import static org.triada.core.memory.MemoryMap.RAM_SIZE;
import static org.triada.core.memory.MemoryMap.RAM_START;
import static org.triada.core.memory.MemoryMap.ROM_END;
import static org.triada.core.memory.MemoryMap.ROM_SIZE;
import static org.triada.core.memory.MemoryMap.ROM_START;

import java.util.Arrays;
import org.triada.core.io.IoDevice;
import org.triada.core.io.IoRegisters;
import org.triada.core.ternary.Word;

/**
 * The bus: the single path between the CPU and everything it can address.
 * The CPU calls {@link #read(int)} and {@link #write(int, int)} with an
 * address. The bus looks at which region the address falls into
 * ({@link MemoryMap}) and forwards the request to RAM, ROM, an I/O device,
 * or nowhere.
 *
 * <p>RAM and ROM are plain {@code int[]} arrays of words (DECISIONS.md D-004).
 * The I/O region is a table of 729 slots, one per register, each holding the
 * {@link IoDevice} attached there, or {@code null}.
 *
 * <p>The CPU's methods ({@code read}, {@code write}) allocate nothing. The host's
 * methods ({@code loadRom}, {@code loadRam}, {@code attach}) check their
 * arguments carefully, because they are called rarely and mistakes there
 * are much easier to find early.
 */
public final class Bus {

    private final int[] ram = new int[RAM_SIZE];
    private final int[] rom = new int[ROM_SIZE];
    private final IoDevice[] devices = new IoDevice[IoRegisters.COUNT];

    /** A new bus in power-on state: RAM and ROM all 0, no devices (SPEC §2.4). */
    public Bus() {
    }

    // ---------------------------------------------------------------- CPU side

    /**
     * The CPU reads the word at {@code address}. Reading an I/O register may
     * have a side effect (KEY removes a key). Unmapped addresses and I/O
     * registers without a device read 0.
     */
    public int read(int address) {
        // RAM first: it is by far the most common case.
        if (address >= RAM_START && address <= RAM_END) {
            return ram[address];
        }
        if (address >= ROM_START && address <= ROM_END) {
            return rom[address - ROM_START];
        }
        if (address >= IO_BASE && address <= IO_END) {
            int k = address - IO_BASE;
            IoDevice device = devices[k];
            return device == null ? 0 : device.read(k);
        }
        MemoryMap.checkAddress(address);
        return 0;
    }

    /**
     * The CPU writes {@code value} to {@code address}. Writes to ROM, to
     * unmapped addresses and to I/O registers without a device are ignored.
     *
     * @throws IllegalArgumentException if {@code value} is not a word, which
     *     would mean a bug in the CPU, never in a TRIADA program
     */
    public void write(int address, int value) {
        if (!Word.isValid(value)) {
            throw new IllegalArgumentException("value " + value + " is not a word");
        }
        if (address >= RAM_START && address <= RAM_END) {
            ram[address] = value;
            return;
        }
        if (address >= IO_BASE && address <= IO_END) {
            int k = address - IO_BASE;
            IoDevice device = devices[k];
            if (device != null) {
                device.write(k, value);
            }
            return;
        }
        if (address < ROM_START || address > ROM_END) {
            MemoryMap.checkAddress(address);
        }
        // ROM or unmapped: ignored, as on the real (imaginary) machine.
    }

    // ---------------------------------------------------------------- host side

    /**
     * Like {@link #read(int)} but without side effects: reading KEY this way
     * shows the next key and leaves it in the queue. For the debugger and tests.
     */
    public int peek(int address) {
        if (MemoryMap.regionOf(address) == MemoryMap.Region.IO) {
            int k = address - IO_BASE;
            IoDevice device = devices[k];
            return device == null ? 0 : device.peek(k);
        }
        return read(address);
    }

    /**
     * Plugs {@code device} into registers {@code firstK … firstK + count − 1}.
     *
     * @throws IllegalArgumentException if the range leaves 0 … 728 or overlaps
     *     a register that already has a device
     */
    public void attach(int firstK, int count, IoDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (count < 1 || firstK < 0 || firstK + count > IoRegisters.COUNT) {
            throw new IllegalArgumentException("registers " + firstK + " .. " + (firstK + count - 1)
                    + " are outside 0 .. " + (IoRegisters.COUNT - 1));
        }
        for (int k = firstK; k < firstK + count; k++) {
            if (devices[k] != null) {
                throw new IllegalArgumentException("I/O register " + k + " (" + IoRegisters.nameOf(k)
                        + ") already has a device attached");
            }
        }
        for (int k = firstK; k < firstK + count; k++) {
            devices[k] = device;
        }
    }

    /** The device attached to register {@code k}, or {@code null}. */
    public IoDevice deviceAt(int k) {
        return devices[k];
    }

    /**
     * Replaces the whole ROM with {@code image}: word {@code i} goes to address
     * {@code ROM_START + i}, and any remaining ROM words become 0. This is how
     * the emulator "burns" the assembled ROM (M8); the CPU can never write ROM.
     */
    public void loadRom(int[] image) {
        if (image.length > ROM_SIZE) {
            throw new IllegalArgumentException(
                    "ROM image has " + image.length + " words, ROM holds " + ROM_SIZE);
        }
        checkWords(image);
        Arrays.fill(rom, 0);
        System.arraycopy(image, 0, rom, 0, image.length);
    }

    /**
     * Copies {@code words} into RAM starting at {@code address}, e.g. a program
     * loaded at 243. The whole block must fit in RAM.
     */
    public void loadRam(int address, int[] words) {
        if (address < RAM_START || (long) address + words.length - 1 > RAM_END) {
            throw new IllegalArgumentException("block of " + words.length + " words at " + address
                    + " does not fit in RAM (" + RAM_START + " .. " + RAM_END + ")");
        }
        checkWords(words);
        System.arraycopy(words, 0, ram, address, words.length);
    }

    /** Sets every RAM word to 0, as at power-on. ROM and devices are kept. */
    public void clearRam() {
        Arrays.fill(ram, 0);
    }

    private static void checkWords(int[] words) {
        for (int i = 0; i < words.length; i++) {
            if (!Word.isValid(words[i])) {
                throw new IllegalArgumentException("word " + i + " = " + words[i] + " is not a word");
            }
        }
    }
}
