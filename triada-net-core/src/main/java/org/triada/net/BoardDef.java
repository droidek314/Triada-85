package org.triada.net;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One bulletin board of TRIADA-NET (NET-SPEC §4, M18): its phone number,
 * name, sysop, colours, welcome screen and areas. Boards are written as small
 * text files, so new ones need no code:
 *
 * <pre>
 *   # a comment
 *   NUMBER 1-23-45
 *   NAME NPO "TRIADA" SUPPORT
 *   SYSOP K. VOLKOV
 *   COLORS 4 -12            text and background colour (pixel values, SPEC §4.2)
 *   LINES 2                 callers at once; one more hears BUSY
 *   UNLISTED                not in the 0-00-00 directory
 *   MESSAGES GENERAL GENERAL TALK      an area: its id, then its title
 *   FILES PROGRAMS PROGRAMS
 *   WELCOME                 the welcome screen, up to 10 lines of 27 characters
 *    ___ ___
 *   END
 * </pre>
 *
 * @param messageAreas at least one; {@code files} may be empty
 */
public record BoardDef(String number, String name, String sysop, int foreground, int background, int lines,
        boolean unlisted, List<Area> messageAreas, List<Area> fileAreas, List<String> welcome) {

    /** A message or file area: a short id (stored with the posts) and a title. */
    public record Area(String id, String title) {
    }

    /** Phone numbers: D-DD-DD. */
    public static final Pattern NUMBER = Pattern.compile("[0-9]-[0-9]{2}-[0-9]{2}");
    private static final Pattern AREA_ID = Pattern.compile("[A-Z0-9]{1,12}");
    public static final int MAX_WELCOME_LINES = 10;

    public BoardDef {
        messageAreas = List.copyOf(messageAreas);
        fileAreas = List.copyOf(fileAreas);
        welcome = List.copyOf(welcome);
    }

    /**
     * Reads a board file.
     *
     * @throws IllegalArgumentException naming the line, if something is missing or wrong
     */
    public static BoardDef parse(String text) {
        String number = null;
        String name = null;
        String sysop = "SYSOP";
        int fg = 4;
        int bg = -12;
        int lines = 4;
        boolean unlisted = false;
        List<Area> messages = new ArrayList<>();
        List<Area> files = new ArrayList<>();
        List<String> welcome = new ArrayList<>();
        boolean inWelcome = false;
        String[] all = text.replace("\r\n", "\n").split("\n", -1);
        for (int n = 0; n < all.length; n++) {
            String line = all[n];
            if (inWelcome) {
                if (line.equals("END")) {
                    inWelcome = false;
                } else {
                    check(line.length() <= 27 && welcome.size() < MAX_WELCOME_LINES, n,
                            "the welcome screen has at most 10 lines of 27 characters");
                    welcome.add(Text.clean(line));
                }
                continue;
            }
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int space = t.indexOf(' ');
            String key = space < 0 ? t : t.substring(0, space);
            String rest = space < 0 ? "" : t.substring(space + 1).strip();
            switch (key) {
                case "NUMBER" -> {
                    check(NUMBER.matcher(rest).matches(), n, "a number looks like 1-23-45");
                    number = rest;
                }
                case "NAME" -> {
                    check(!rest.isEmpty() && rest.length() <= 27, n, "a name has 1 to 27 characters");
                    name = Text.clean(rest);
                }
                case "SYSOP" -> sysop = Text.fit(Text.clean(rest), 20);
                case "COLORS" -> {
                    String[] c = rest.split(" +");
                    check(c.length == 2, n, "COLORS needs two pixel values");
                    fg = colour(c[0], n);
                    bg = colour(c[1], n);
                }
                case "LINES" -> {
                    check(rest.matches("[0-9]{1,3}") && Integer.parseInt(rest) >= 1, n, "LINES needs a number");
                    lines = Integer.parseInt(rest);
                }
                case "UNLISTED" -> unlisted = true;
                case "MESSAGES", "FILES" -> {
                    int s = rest.indexOf(' ');
                    String id = s < 0 ? rest : rest.substring(0, s);
                    String title = s < 0 ? id : rest.substring(s + 1).strip();
                    check(AREA_ID.matcher(id).matches(), n, "an area id is 1 to 12 letters or digits");
                    (key.equals("MESSAGES") ? messages : files).add(new Area(id, Text.fit(Text.clean(title), 20)));
                }
                case "WELCOME" -> inWelcome = true;
                default -> check(false, n, "unknown line '" + key + "'");
            }
        }
        check(!inWelcome, all.length - 1, "WELCOME without END");
        check(number != null, 0, "no NUMBER");
        check(name != null, 0, "no NAME");
        check(!messages.isEmpty(), 0, "a board needs at least one MESSAGES area");
        return new BoardDef(number, name, sysop, fg, bg, lines, unlisted, messages, files, welcome);
    }

    private static int colour(String s, int line) {
        try {
            int v = Integer.parseInt(s);
            check(v >= -13 && v <= 13, line, "a colour is -13 ... 13");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("line " + (line + 1) + ": a colour is -13 ... 13");
        }
    }

    private static void check(boolean ok, int line, String message) {
        if (!ok) {
            throw new IllegalArgumentException("line " + (line + 1) + ": " + message);
        }
    }

    /** The message area with this id, or null. */
    public Area messageArea(String id) {
        return messageAreas.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }
}
