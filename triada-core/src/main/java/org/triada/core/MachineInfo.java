package org.triada.core;

/**
 * Basic facts about the TRIADA-85 that every part of the project shares:
 * the window title, the boot screen, the CLI banner.
 *
 * <p>Keeping them in one place means the name is spelled the same way
 * everywhere, and the frontends do not need to invent their own text.
 */
public final class MachineInfo {

    /** The machine's name, exactly as printed on the (fictional) case. */
    public static final String NAME = MachineProfiles.TRIADA_85.name();

    /** The version of {@code docs/SPEC.md} this code implements. */
    public static final String SPEC_VERSION = MachineProfiles.TRIADA_85.specVersion();

    private MachineInfo() {
        // Constants only; never instantiated.
    }
}
