package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The top level of the command line: usage, help, unknown commands. */
class MainTest {

    @Test
    void usageNamesTheMachineAndBothCommands() {
        String text = Main.usage();
        assertTrue(text.startsWith("TRIADA-85"), text);
        assertTrue(text.contains("triada tasm"), text);
        assertTrue(text.contains("triada run"), text);
        assertTrue(text.contains("exit codes"), text);
    }

    @Test
    void helpPrintsUsageToStdoutWithExitCode0() {
        for (String help : new String[] {"help", "--help", "-h"}) {
            Cli.Result r = Cli.run(help);
            assertEquals(0, r.code());
            assertTrue(r.out().startsWith("TRIADA-85 command-line tools"), r.out());
        }
    }

    @Test
    void noArgumentsPrintsUsageToStderrWithExitCode2() {
        Cli.Result r = Cli.run();
        assertEquals(Main.EXIT_USAGE, r.code());
        assertEquals("", r.out());
        assertTrue(r.err().contains("usage:"), r.err());
    }

    @Test
    void unknownCommandsAreNamed() {
        Cli.Result r = Cli.run("compile", "x.tasm");
        assertEquals(2, r.code());
        assertTrue(r.err().startsWith("triada: unknown command 'compile'"), r.err());
    }
}
