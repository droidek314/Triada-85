package org.triada.net.server;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import org.triada.net.Moderation;
import org.triada.net.NetStore;

/**
 * The sysop's web page and its API (NET-SPEC §5, M18). The page itself
 * ({@code /admin}) holds no secrets; every API call must carry the admin token
 * as {@code Authorization: Bearer <token>}, which the page asks for and keeps
 * only in the browser tab.
 *
 * <pre>
 *   GET  /admin/api/queue                     uploads waiting
 *   POST /admin/api/files/{id}/approve|reject
 *   GET  /admin/api/reports                   reported messages
 *   POST /admin/api/messages/{id}/delete
 *   POST /admin/api/handles/{handle}/ban|unban
 *   POST /admin/api/ips/{ip}/ban|unban
 * </pre>
 */
final class AdminApi {

    private final Moderation mod;
    private final Predicate<Context> authorized;

    AdminApi(Moderation mod, Predicate<Context> authorized) {
        this.mod = mod;
        this.authorized = authorized;
    }

    void routes(JavalinConfig config) {
        config.routes.get("/admin", ctx -> ctx.contentType("text/html; charset=utf-8").result(page()));
        config.routes.get("/admin/api/queue", guard(ctx -> json(ctx, queue())));
        config.routes.post("/admin/api/files/{id}/approve", guard(ctx -> done(ctx, mod.approve(id(ctx)))));
        config.routes.post("/admin/api/files/{id}/reject", guard(ctx -> done(ctx, mod.reject(id(ctx)))));
        config.routes.get("/admin/api/reports", guard(ctx -> json(ctx, reports())));
        config.routes.post("/admin/api/messages/{id}/delete", guard(ctx -> done(ctx, mod.deleteMessage(id(ctx)))));
        config.routes.post("/admin/api/handles/{handle}/ban",
                guard(ctx -> done(ctx, mod.banHandle(ctx.pathParam("handle"), true))));
        config.routes.post("/admin/api/handles/{handle}/unban",
                guard(ctx -> done(ctx, mod.banHandle(ctx.pathParam("handle"), false))));
        config.routes.post("/admin/api/ips/{ip}/ban", guard(ctx -> {
            mod.banIp(ctx.pathParam("ip"), true);
            done(ctx, true);
        }));
        config.routes.post("/admin/api/ips/{ip}/unban", guard(ctx -> {
            mod.banIp(ctx.pathParam("ip"), false);
            done(ctx, true);
        }));
    }

    private Handler guard(Handler h) {
        return ctx -> {
            if (!authorized.test(ctx)) {
                ctx.status(401).result("unauthorized");
                return;
            }
            h.handle(ctx);
        };
    }

    private static long id(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void done(Context ctx, boolean ok) {
        ctx.status(ok ? 200 : 404);
        json(ctx, "{\"ok\":" + ok + "}");
    }

    private static void json(Context ctx, String body) {
        ctx.contentType("application/json").result(body);
    }

    private String queue() {
        List<String> items = mod.queue().stream().map(f -> "{\"id\":" + f.id() + ",\"board\":" + q(f.board())
                + ",\"area\":" + q(f.area()) + ",\"name\":" + q(f.name()) + ",\"type\":" + q(f.type().name())
                + ",\"words\":" + f.file().size() + ",\"uploader\":" + q(f.uploader()) + ",\"description\":"
                + q(f.description()) + ",\"time\":" + q(String.valueOf(f.time())) + "}").toList();
        return "[" + String.join(",", items) + "]";
    }

    private String reports() {
        List<String> items = mod.reports().stream().map(r -> {
            NetStore.Message m = r.message();
            String message = m == null ? "null" : "{\"board\":" + q(m.board()) + ",\"area\":" + q(m.area())
                    + ",\"author\":" + q(m.author()) + ",\"subject\":" + q(m.subject()) + ",\"body\":"
                    + q(m.body()) + "}";
            return "{\"id\":" + r.report().id() + ",\"messageId\":" + r.report().messageId() + ",\"reporter\":"
                    + q(r.report().reporter()) + ",\"time\":" + q(String.valueOf(r.report().time()))
                    + ",\"message\":" + message + "}";
        }).toList();
        return "[" + String.join(",", items) + "]";
    }

    private static String q(String s) {
        return JsonLog.string(s);
    }

    private static String page() {
        try (InputStream in = AdminApi.class.getResourceAsStream("/admin.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            return "admin page missing";
        }
    }
}
