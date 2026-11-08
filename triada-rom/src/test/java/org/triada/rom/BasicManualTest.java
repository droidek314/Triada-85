package org.triada.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The manual's TRIBASIC chapter (M15) can't lie: every {@code ```tribasic}
 * block is a screen transcript. Its numbered lines are typed in, the first
 * other line is typed as the command, and the screen must then show that
 * command and everything below it in the block. The {@code ```typein}
 * listing must be {@code programs/basic/TYPEIN.bas}.
 */
class BasicManualTest {

    private static final Path ROOT = Path.of(System.getProperty("triada.root", ".."));
    private static final Path CHAPTER = ROOT.resolve("docs/manual/tribasic.md");

    private static List<String> blocks(String fence) throws IOException {
        String text = Files.readString(CHAPTER, StandardCharsets.UTF_8).replace("\r\n", "\n");
        Matcher m = Pattern.compile("```" + fence + "\\n(.*?)```", Pattern.DOTALL).matcher(text);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @TestFactory
    Stream<DynamicTest> everyExampleIsWhatTheMachineShows() throws IOException {
        List<String> examples = blocks("tribasic");
        assertTrue(examples.size() >= 10, "found " + examples.size() + " examples");
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < examples.size(); i++) {
            String block = examples.get(i);
            tests.add(DynamicTest.dynamicTest("example " + (i + 1), () -> check(block)));
        }
        return tests.stream();
    }

    private static void check(String block) {
        List<String> lines = Arrays.asList(block.split("\n"));
        int command = 0;
        while (lines.get(command).matches("\\d+ .*")) {
            command++;
        }
        String setup = String.join("\n", lines.subList(0, command)) + (command > 0 ? "\n" : "");
        List<String> expected = lines.subList(command, lines.size());
        List<String> actual = BasicSuiteTest.run(setup + "---\n" + lines.get(command) + "\n");
        assertEquals(String.join("\n", expected), String.join("\n", actual));
    }

    @Test
    void theTypeInListingIsTheExampleProgram() throws IOException {
        List<String> listing = blocks("typein");
        assertEquals(1, listing.size());
        String file = Files.readString(ROOT.resolve("programs/basic/TYPEIN.bas"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(file, listing.get(0));
    }
}
