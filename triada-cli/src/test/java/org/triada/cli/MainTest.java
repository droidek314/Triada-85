package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** M0 smoke test: the CLI module builds and can see triada-core. */
class MainTest {

    @Test
    void usageNamesTheMachineAndBothCommands() {
        String text = Main.usage();
        assertTrue(text.startsWith("TRIADA-85"), text);
        assertTrue(text.contains("triada tasm"), text);
        assertTrue(text.contains("triada run"), text);
    }
}
