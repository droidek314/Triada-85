package org.triada.net.server;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.List;
import org.triada.net.Moderation;
import org.triada.net.NetStore;

/**
 * The sysop's command line (NET-SPEC §5, M18): the same actions as the admin
 * page, straight on the database file. It can run while the server runs
 * (SQLite lets two programs share a file).
 *
 * <pre>
 *   triada-net-server admin [--db FILE] queue
 *   triada-net-server admin approve 12          reject 12
 *   triada-net-server admin reports
 *   triada-net-server admin show 40             delete 40
 *   triada-net-server admin ban PETYA           unban PETYA
 *   triada-net-server admin ban-ip 10.0.0.9     unban-ip 10.0.0.9
 * </pre>
 * Exit code 0: done; 1: no such thing; 2: wrong usage.
 */
final class AdminCommand {

    private AdminCommand() {
    }

    static int run(String[] args, ServerConfig config, PrintStream out, PrintStream err) throws SQLException {
        String db = config.database();
        int i = 0;
        if (args.length >= 2 && args[0].equals("--db")) {
            db = args[1];
            i = 2;
        }
        if (i >= args.length) {
            err.println(usage());
            return 2;
        }
        String command = args[i];
        String arg = i + 1 < args.length ? args[i + 1] : null;
        try (SqliteNetStore store = new SqliteNetStore(db)) {
            Moderation mod = new Moderation(store, new JsonLog(err), "command line");
            switch (command) {
                case "queue" -> {
                    List<NetStore.FileEntry> q = mod.queue();
                    if (q.isEmpty()) {
                        out.println("nothing waiting");
                    }
                    for (NetStore.FileEntry f : q) {
                        out.println(f.id() + "  " + f.name() + "  " + f.file().size() + " words  from " + f.uploader()
                                + "  on " + f.board() + "/" + f.area() + ": " + f.description());
                    }
                    return 0;
                }
                case "reports" -> {
                    List<Moderation.ReportedMessage> r = mod.reports();
                    if (r.isEmpty()) {
                        out.println("no reports");
                    }
                    for (Moderation.ReportedMessage x : r) {
                        NetStore.Message m = x.message();
                        out.println("message " + x.report().messageId() + " reported by " + x.report().reporter()
                                + (m == null ? " (deleted)" : ": " + m.author() + ": " + m.subject()));
                    }
                    return 0;
                }
                case "approve", "reject", "show", "delete" -> {
                    long id = number(arg);
                    if (id < 0) {
                        err.println(command + " needs a number");
                        return 2;
                    }
                    boolean ok;
                    if (command.equals("show")) {
                        NetStore.Message m = mod.message(id);
                        ok = m != null;
                        if (ok) {
                            out.println("#" + m.id() + " " + m.board() + "/" + m.area() + " from " + m.author()
                                    + " at " + m.time());
                            out.println(m.subject());
                            out.println(m.body());
                        }
                    } else {
                        ok = command.equals("approve") ? mod.approve(id)
                                : command.equals("reject") ? mod.reject(id) : mod.deleteMessage(id);
                        if (ok) {
                            out.println(command + " " + id + ": done");
                        }
                    }
                    if (!ok) {
                        err.println(command.equals("approve") || command.equals("reject")
                                ? "no upload " + id + " waiting" : "no message " + id);
                    }
                    return ok ? 0 : 1;
                }
                case "ban", "unban" -> {
                    if (arg == null) {
                        err.println(command + " needs a handle");
                        return 2;
                    }
                    boolean ok = mod.banHandle(arg, command.equals("ban"));
                    (ok ? out : err).println(ok ? command + " " + arg.toUpperCase(java.util.Locale.ROOT) + ": done"
                            : "no caller " + arg);
                    return ok ? 0 : 1;
                }
                case "ban-ip", "unban-ip" -> {
                    if (arg == null) {
                        err.println(command + " needs an address");
                        return 2;
                    }
                    mod.banIp(arg, command.equals("ban-ip"));
                    out.println(command + " " + arg + ": done");
                    return 0;
                }
                default -> {
                    err.println(usage());
                    return 2;
                }
            }
        }
    }

    private static long number(String s) {
        try {
            return s == null ? -1 : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String usage() {
        return "usage: triada-net-server admin [--db FILE] queue | approve ID | reject ID | reports | show ID"
                + " | delete ID | ban HANDLE | unban HANDLE | ban-ip IP | unban-ip IP";
    }
}
