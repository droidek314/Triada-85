package org.triada.net;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.triada.core.TriImage;

/**
 * Puts the boards' starting content into a store (M19): the posts of fictional
 * 1985 callers and the files each board offers. Each post and file is added
 * once only, whenever the server starts: the store remembers what it has seen
 * ({@link NetStore#markSeeded}), so a restart adds nothing twice, and a post
 * added to a board file later still appears.
 *
 * <p>A fictional author gets an account that no password opens, so nobody
 * can register their handle and speak for them.
 */
public final class Content {

    /** Not a password hash: {@link Passwords#verify} never accepts it. */
    static final String NO_LOGIN = "fictional";

    private Content() {
    }

    /**
     * Seeds {@code store} with the posts and files of {@code boards}; the files'
     * words come from {@code files} (usually {@link Boards#file}).
     *
     * @throws IllegalStateException if a board offers a file that {@code files} doesn't have
     */
    public static void seed(NetStore store, List<BoardDef> boards, Function<String, TriImage> files) {
        for (BoardDef b : boards) {
            List<BoardDef.Post> posts = b.posts().stream().sorted(Comparator.comparing(BoardDef.Post::date)).toList();
            for (BoardDef.Post p : posts) {
                if (store.markSeeded("post " + b.number() + " " + p.area() + " " + p.date() + " " + p.author())) {
                    author(store, p.author());
                    store.post(new NetStore.Message(0, b.number(), p.area(), p.author(), p.subject(), p.body(),
                            real(p.date().toInstant(ZoneOffset.UTC)), 0));
                }
            }
            for (BoardDef.FileDef f : b.files()) {
                if (store.markSeeded("file " + b.number() + " " + f.area() + " " + f.name())) {
                    TriImage image = files.apply(f.name());
                    if (image == null) {
                        throw new IllegalStateException(b.number() + " offers " + f.name() + ", which isn't built");
                    }
                    author(store, f.uploader());
                    store.addFile(new NetStore.FileEntry(0, b.number(), f.area(), f.name(),
                            f.name().endsWith(".TB") ? Trimodem.Type.TB : Trimodem.Type.TRI, f.description(),
                            image, f.uploader(), NetStore.FileStatus.PUBLIC,
                            real(f.date().atStartOfDay().toInstant(ZoneOffset.UTC))));
                }
            }
        }
    }

    /** An in-world moment (1985) as the real one it is shown for: 41 years later. */
    private static Instant real(Instant inWorld) {
        return inWorld.atOffset(ZoneOffset.UTC).plusYears(InWorld.YEARS_BACK).toInstant();
    }

    private static void author(NetStore store, String handle) {
        if (store.user(handle) == null) {
            store.addUser(new NetStore.User(handle, NO_LOGIN, 0, null, false, Instant.EPOCH));
        }
    }
}
