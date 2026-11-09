package org.triada.net;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.triada.core.triskette.TrisketteNames;

/**
 * One bulletin board of TRIADA-NET (NET-SPEC §4, M18, M19): its phone number,
 * name, sysop, colours, welcome screen and areas, and the posts and files it
 * starts with. Boards are written as small text files, so new ones need no code:
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
 *   POST GENERAL 1985-03-02 14:10 KVOLKOV      a post the board starts with (M19):
 *   SUBJECT WELCOME                            area, in-world date and time, author
 *   The text, line by line,
 *   .                                          ... up to a line with only a dot
 *   FILE PROGRAMS SNAKE.TRI 1985-05-12 EDITOR  a file the board offers (M19)
 *   A description.
 *   .
 * </pre>
 *
 * @param messageAreas at least one; {@code files} may be empty
 */
public record BoardDef(String number, String name, String sysop, int foreground, int background, int lines,
        boolean unlisted, List<Area> messageAreas, List<Area> fileAreas, List<String> welcome, List<Post> posts,
        List<FileDef> files) {

    /** A message or file area: a short id (stored with the posts) and a title. */
    public record Area(String id, String title) {
    }

    /** A post the board starts with (M19): written by a fictional caller, dated in 1985. */
    public record Post(String area, LocalDateTime date, String author, String subject, String body) {
    }

    /** A file the board offers from the start (M19); its words come from {@link Boards#file}. */
    public record FileDef(String area, String name, LocalDate date, String uploader, String description) {
    }

    /** Phone numbers: D-DD-DD. */
    public static final Pattern NUMBER = Pattern.compile("[0-9]-[0-9]{2}-[0-9]{2}");
    private static final Pattern AREA_ID = Pattern.compile("[A-Z0-9]{1,12}");
    private static final Pattern HANDLE = Pattern.compile("[A-Z0-9]{2,12}");
    public static final int MAX_WELCOME_LINES = 10;

    public BoardDef {
        messageAreas = List.copyOf(messageAreas);
        fileAreas = List.copyOf(fileAreas);
        welcome = List.copyOf(welcome);
        posts = List.copyOf(posts);
        files = List.copyOf(files);
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
        List<Post> posts = new ArrayList<>();
        List<FileDef> fileDefs = new ArrayList<>();
        String[] all = text.replace("\r\n", "\n").split("\n", -1);
        for (int n = 0; n < all.length; n++) {
            String t = all[n].strip();
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
                case "WELCOME" -> {
                    for (n++; n < all.length && !all[n].equals("END"); n++) {
                        check(all[n].length() <= 27 && welcome.size() < MAX_WELCOME_LINES, n,
                                "the welcome screen has at most 10 lines of 27 characters");
                        welcome.add(Text.clean(all[n]));
                    }
                    check(n < all.length, all.length - 1, "WELCOME without END");
                }
                case "POST" -> {
                    String[] p = rest.split(" +", 4);
                    check(p.length == 4, n, "POST needs: area, date, time, author");
                    LocalDateTime when = dateTime(p[1] + "T" + p[2], n);
                    check(HANDLE.matcher(p[3]).matches(), n, "an author is a handle: 2 to 12 letters or digits");
                    n++;
                    check(n < all.length && all[n].startsWith("SUBJECT "), n, "a POST's next line is SUBJECT");
                    String subject = Text.fit(Text.clean(all[n].substring(8).strip()), 24);
                    List<String> body = new ArrayList<>();
                    n = block(all, n + 1, body);
                    posts.add(new Post(p[0], when, p[3], subject, String.join("\n", body)));
                }
                case "FILE" -> {
                    String[] f = rest.split(" +", 4);
                    check(f.length == 4, n, "FILE needs: area, name, date, uploader");
                    String fileName = TrisketteNames.normalize(f[1]);
                    check(fileName != null && (fileName.endsWith(".TB") || fileName.endsWith(".TRI")), n,
                            "a file name is a triskette name ending in .TB or .TRI");
                    LocalDate day = dateTime(f[2] + "T00:00", n).toLocalDate();
                    check(HANDLE.matcher(f[3]).matches(), n, "an uploader is a handle");
                    List<String> description = new ArrayList<>();
                    n = block(all, n + 1, description);
                    fileDefs.add(new FileDef(f[0], fileName, day, f[3], String.join(" ", description)));
                }
                default -> check(false, n, "unknown line '" + key + "'");
            }
        }
        check(number != null, 0, "no NUMBER");
        check(name != null, 0, "no NAME");
        check(!messages.isEmpty(), 0, "a board needs at least one MESSAGES area");
        for (Post p : posts) {
            check(messages.stream().anyMatch(a -> a.id().equals(p.area())), 0, "a POST in no MESSAGES area: " + p.area());
        }
        for (FileDef f : fileDefs) {
            check(files.stream().anyMatch(a -> a.id().equals(f.area())), 0, "a FILE in no FILES area: " + f.area());
        }
        return new BoardDef(number, name, sysop, fg, bg, lines, unlisted, messages, files, welcome, posts, fileDefs);
    }

    /** Reads lines from {@code n} up to a line with only "."; returns the index of that line. */
    private static int block(String[] all, int n, List<String> into) {
        for (; n < all.length && !all[n].strip().equals("."); n++) {
            into.add(Text.clean(all[n]).stripTrailing());
        }
        check(n < all.length, all.length - 1, "a block without its closing '.'");
        return n;
    }

    private static LocalDateTime dateTime(String s, int line) {
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("line " + (line + 1) + ": a date is 1985-03-02, a time 14:10");
        }
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
