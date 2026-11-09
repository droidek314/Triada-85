# Milestone 18 — The TRIADA-NET server

## In one paragraph

In M17 the TRIADA-85 learned to make a phone call, but the only thing on the other end was a test line that repeated everything. Now there is something worth calling: **bulletin boards**. A bulletin board (a BBS) was a computer you dialled in the 1980s to read and leave messages, send private mail, download programs and see who else had called. The TRIADA-NET server hosts many of them, each with its own phone number, name, sysop (the person who runs it) and welcome screen. Number `0-00-00` is the directory.

You can call as a GUEST and read, or register with a handle and password and write. The server remembers everything in a small database, keeps passwords only as scrambled fingerprints, and limits how fast and how much anyone can do. The real sysop gets a web page and a command line to approve uploads, delete messages and ban troublemakers. Finally, there is a kit for putting the server on the internet, with a step-by-step guide.

## What was built

What existed and was reused:
- the server side of the wire protocol (`ServerConnection`, `Exchange`, `Call`, `Wire`), `LoopbackNetwork`, TRIMODEM and `NetServer` from M17;
- `TrisketteNames` for file names (M13), `InWorld` for the 1985 date, and the terminal's control codes (SPEC §11.1).

New in `triada-net-core` (pure Java, no sockets, no database):
- **Screens:**
  - `TermOut` writes text and control codes;
  - `Text` word-wraps for 27 columns;
  - `TerminalScreen` is a pretend TRIADA terminal that turns the bytes back into a 27 × 18 screen, for tests and for M19.
- **Boards:** `BoardDef` reads `.board` text files. `Network` is the exchange: it knows the boards, busy lines, banned addresses and who is online.
- **Sessions:**
  - `TextCall` is the event-driven base (questions, menus, paging, idle, flooding);
  - `BoardSession` is a whole board: login, registration, messages, mail, files with TRIMODEM, one-liners, last callers, who is on, time left;
  - `InfoCall` is the 0-00-00 directory.
- **Storage:** `NetStore` (the interface) and `MemoryNetStore`.
- **Rules:** `Passwords` (PBKDF2), `RateLimiter`, `Limits`, `NetLog`, `Moderation`.
- **Test fixtures:** `TestClock`, `TestCaller` and `NetStoreContract`, shared with the server's tests.

New in `triada-net-server`:
- **Storage:** `SqliteNetStore` and `Migrations`, with `V001__schema.sql`.
- **Configuration:** `ServerConfig` (environment variables), `Boards` (built-in or from a folder) and `JsonLog`.
- **The server:** `NetServer` now hosts the boards by default (`--echo` for the M17 exchange), with per-address limits, a once-a-second tick and the admin API.
- **Sysop tools:** `AdminApi` with `admin.html`, and `AdminCommand` (`triada-net-server admin …`).

The deployment kit:
- `deploy/Dockerfile`, `deploy/docker-compose.yml`, `deploy/Caddyfile`, `deploy/.env.example` and `.dockerignore`;
- [`docs/net/DEPLOY.md`](../net/DEPLOY.md), the step-by-step guide.

Documents:
- [NET-SPEC](../NET-SPEC.md) §4.1 and §4.2, and SPEC §11.1 (the colour code's offset);
- decisions D-066 … D-069;
- the M17 doc's echo-server command (now `--echo`).

Not in M18:
- the starter boards' content, the seeded posts, the story outline and the downloadable programs (M19). The server ships one plain test board, `1-00-00`;
- the optional door game.

## Why it was built this way

**Sessions that never wait** ([D-068](../DECISIONS.md#d-068--boards-as-text-files-sessions-as-chains-of-screens---milestone-m18-2026-11-09)). A board is a conversation: show a menu, wait for a key, ask a question, wait for a line. The obvious way is a thread per caller that sits and waits. But the same board code must also run inside tests, and later inside the same program as an emulated machine, with no threads at all. So a session never waits. Asking a question just leaves a note saying "when the line is complete, do this", and returns. Each arriving character goes to the current note. The cost is that menus read as chains of small methods; the gain is that one design works everywhere, and the tests run the real board, byte for byte, in a fraction of a second.

**Boards as files.** A board is a `.board` file of a dozen lines: its number, name, sysop, colours, lines, areas and welcome screen. M19's starter boards and anyone's own boards are content, not code.

**SQLite and our own migrations** ([D-066](../DECISIONS.md#d-066--sqlite-with-our-own-migration-runner-and-one-store-interface---milestone-m18-2026-11-09)). SQLite is a whole database in one file, with nothing else to install. The tables will change in later versions, so changes are numbered scripts that run once each (*migrations*). A fifty-line runner does this; a migration library would be a large dependency for so little. The store is an interface with two versions: SQLite for the server, memory for tests. One shared test (the *contract*) keeps them identical.

**A public server's limits** ([D-067](../DECISIONS.md#d-067--the-limits-of-a-public-server---milestone-m18-2026-11-09)). The server will be on the internet, so it assumes some callers are hostile:
- **Passwords** are hashed slowly (PBKDF2, 120,000 rounds, with a salt). They are compared in constant time, and wrong guesses lock the account and then the address.
- **Everything is limited:** how often people write and upload, how much they write, how long they stay, how fast they type, and how many calls one address makes.
- **Nothing a caller types becomes a file path or part of an SQL command.** Uploads live inside the database.
- **The log** records calls and moderation, never passwords.

The numbers follow 1985 custom (30 minutes per board per day) where that is also safe.

**Docker and Caddy, deployed by the human** ([D-069](../DECISIONS.md#d-069--deployment-docker-with-caddy-for-tls-never-deployed-by-us---milestone-m18-2026-11-09)). The web version's page is served over HTTPS, so it may only open encrypted WebSockets (`wss://`), which need a TLS certificate. Caddy gets and renews one by itself, and Docker turns the whole thing into one command on any small server. CLAUDE.md forbids us to create hosting accounts or deploy, so the kit is prepared and explained, and you run it.

## How it works — the plain-language version

**Calling a board.** Your TRIADA dials `1-00-00`. The server's exchange looks the number up. If the board's lines are all taken, you hear BUSY, as on a real board with only a few telephone lines. Otherwise the board answers and shows its welcome screen:

```
 +----------------------+
 |  T R I A D A  N E T  |
 +----------------------+
TRIADA-NET TEST BOARD
SYSOP: THE ENGINEER
1985-11-09
HANDLE (GUEST, NEW):
```

The date is today's, 41 years ago. Type `GUEST` to look around, or `NEW` to join. Joining shows the SYSOP RULES (be kind, no real names or addresses, nothing illegal), then asks for a handle and a password, twice. From then on that handle works on every board of the network.

**The main menu** is one key per choice, as menus were then:

```
TRIADA-NET TEST BOARD
-------------------------
M MESSAGES   E MAIL
F FILES      O ONE-LINERS
L LAST CALLS W WHO IS ON
T TIME LEFT  G GOODBYE
-------------------------
30 MIN LEFT. COMMAND?
```

- **Messages** are the board's public discussion, newest first. You read one by its number. From there `N` is the next older one, `A` answers it, and `R` reports it to the sysop.
- **Mail** is private, between two handles.
- **Files** are programs to download, sent with TRIMODEM (M17). Your own uploads wait until the sysop has checked them.
- **One-liners** are a wall where each caller leaves one short line for the next.

Everything fits 27 columns. Long lists stop after each screen and ask `MORE? (Y/N)`.

**How a board "waits" without waiting.** Think of a receptionist with many phones. They don't hold one phone in silence until its caller has finished thinking. They write on a card, "this caller is choosing from the menu; when they press a key, do X", and pick up the next phone. When a key arrives on line 3, they read line 3's card and act on it. Every board session works like that: each key goes to the current card, and the card says what comes next. A clock tick once a second hangs up callers who have gone quiet for five minutes or used up their 30 minutes for the day.

**Keeping passwords safe.** The server never stores a password. It stores a **salt** (16 random bytes) and the result of mixing the password with the salt and stirring it 120,000 times (**PBKDF2**). To check a login, it stirs the typed password the same way and compares the results. A stolen database shows only the stirred results, and testing a single guess takes noticeable time. That's why five wrong guesses lock an account for fifteen minutes, and ten from one address shut that address out.

**The sysop's desk.** The real sysop opens `/admin` in a browser and types the secret admin token. They see the uploads waiting (Approve or Reject) and the reported messages (Delete, or Ban the author), and can ban a handle or an address. The same actions exist on the server's command line, for example:

```
triada-net-server admin queue
```

Each action is written to the log, with who did it.

**Putting it online.** The guide [`docs/net/DEPLOY.md`](../net/DEPLOY.md) walks through it, with every command:
1. Rent a small server and point a domain name at it.
2. Install Docker.
3. Fill in three settings: the domain, the web version's address, and a long random admin token.
4. Type `docker compose up -d --build`.

## How it works — the programmer's version

Read in this order.

1. **Text out and the pretend terminal.**
   - [`TermOut.java`](../../triada-net-core/src/main/java/org/triada/net/TermOut.java) builds bytes: `TermOut.clear`, `TermOut.line`, `TermOut.color`, `TermOut.at`, `TermOut.raw`, and `TermOut.freshLine`, which remembers the last character across `take()` so a goodbye never runs into a prompt.
   - [`TerminalScreen.java`](../../triada-net-core/src/main/java/org/triada/net/TerminalScreen.java) does the reverse, the way TERM.TRI will: it wraps at the right edge, scrolls at the bottom, and treats ESC sequences as two parameters each offset by 32.
   - [`Text.java`](../../triada-net-core/src/main/java/org/triada/net/Text.java) → `Text.wrap`, `Text.fit`, `Text.clean`.
2. **Boards and the exchange.**
   - [`BoardDef.java`](../../triada-net-core/src/main/java/org/triada/net/BoardDef.java) → `BoardDef.parse` reads a `.board` file, checking every line, and names the line in its error.
   - [`Network.java`](../../triada-net-core/src/main/java/org/triada/net/Network.java) → `Network.dial`: a banned address hears NO CARRIER; `0-00-00` answers with an `InfoCall`; a full board answers BUSY. Otherwise it creates a `BoardSession` and takes a line at once (the `online` set), so two callers can't both get the last line. `Network.online` and `Network.directory` serve "who is on" and the directory.
3. **The session engine.** [`TextCall.java`](../../triada-net-core/src/main/java/org/triada/net/TextCall.java):
   - `TextCall.receive` checks for flooding, ignores a 10 right after a 13, and hands the key to the current `Input`;
   - `TextCall.ask` installs a line editor (echo, `*` for passwords, backspace, the bell at the length limit);
   - `TextCall.choose` installs a one-key menu, and `TextCall.page` pages with MORE?;
   - `TextCall.tick` handles idleness and the subclass's `timeTick`;
   - `TextCall.bye` says the last words and hangs up;
   - a running TRIMODEM transfer (`Transfer`) gets every byte and every tick instead.

   ```java
   protected void ask(String prompt, int max, boolean masked, Consumer<String> then) {
       out.text(prompt);
       StringBuilder typed = new StringBuilder();
       input = c -> {
           if (c == 13 || c == 10) { out.newline(); input = k -> { }; then.accept(typed.toString()); }
           ...
   ```
   *Show the prompt, then replace the session's current input handler. Every key now goes into `typed` until Return; then the handler steps aside and runs `then` with the line. Nothing waits: `ask` returns at once.*
4. **A board.** [`BoardSession.java`](../../triada-net-core/src/main/java/org/triada/net/BoardSession.java), in screen order:
   - the welcome and login: `askHandle`, `askPassword`, `wrongPassword` (lockouts), `rules`, `newHandle` and `newPassword` (registration limits);
   - `loggedIn` (the daily time and the callers' log) and `mainMenu`;
   - messages: `messageArea`, `read`, `older`, `compose`, `bodyLine` (the size limits);
   - `mail`;
   - files: `fileArea`, `find`, `info`, `download` (a `TrimodemSender` with resends on timeout), `upload` and `received` (a `TrimodemReceiver`, then size and space checks, then the queue);
   - `oneLiners`, `lastCallers`, `whoIsOn`;
   - `ended`, which writes down the time used.

   [`InfoCall.java`](../../triada-net-core/src/main/java/org/triada/net/InfoCall.java) is the directory.
5. **Storage and rules.**
   - [`NetStore.java`](../../triada-net-core/src/main/java/org/triada/net/NetStore.java) lists what is stored (its records `NetStore.User`, `NetStore.Message`, `NetStore.Mail`, `NetStore.FileEntry` and others).
   - [`MemoryNetStore.java`](../../triada-net-core/src/main/java/org/triada/net/MemoryNetStore.java) is the in-memory version.
   - [`Passwords.java`](../../triada-net-core/src/main/java/org/triada/net/Passwords.java) → `Passwords.hash`, `Passwords.verify` (`MessageDigest.isEqual`) and `Passwords.verifyNobody` (the same cost for unknown handles).
   - [`RateLimiter.java`](../../triada-net-core/src/main/java/org/triada/net/RateLimiter.java) → `RateLimiter.allow`, `RateLimiter.record`, `RateLimiter.count`: sliding windows per key, pruned now and then.
   - [`Limits.java`](../../triada-net-core/src/main/java/org/triada/net/Limits.java) → `Limits.DEFAULT`.
   - [`Moderation.java`](../../triada-net-core/src/main/java/org/triada/net/Moderation.java) is shared by both sysop tools.
6. **The database.**
   - [`SqliteNetStore.java`](../../triada-net-server/src/main/java/org/triada/net/server/SqliteNetStore.java): one connection in WAL mode, prepared statements only, files as `.tri` bytes (`TriImage.toBytes`).
   - [`Migrations.java`](../../triada-net-server/src/main/java/org/triada/net/server/Migrations.java) → `Migrations.run`: `schema_version`, then each new script in its own transaction; `Migrations.statements` splits a script.
   - [`V001__schema.sql`](../../triada-net-server/src/main/resources/db/V001__schema.sql) is the first schema.
7. **The server.** [`NetServer.java`](../../triada-net-server/src/main/java/org/triada/net/server/NetServer.java):
   - `NetServer.admit` and `NetServer.release` count calls per address;
   - `NetServer.tickAll` runs once a second;
   - `NetServer.address` takes `X-Forwarded-For` only if told to trust the proxy;
   - `NetServer.authorized` checks the admin token in constant time and counts wrong guesses;
   - `NetServer.main` reads [`ServerConfig.java`](../../triada-net-server/src/main/java/org/triada/net/server/ServerConfig.java) (`ServerConfig.fromEnv`, `ServerConfig.withArgs`) and loads [`Boards.java`](../../triada-net-server/src/main/java/org/triada/net/server/Boards.java).

   [`JsonLog.java`](../../triada-net-server/src/main/java/org/triada/net/server/JsonLog.java) writes the log.
8. **The sysop's tools.**
   - [`AdminApi.java`](../../triada-net-server/src/main/java/org/triada/net/server/AdminApi.java): routes behind `guard`, JSON built by hand.
   - [`admin.html`](../../triada-net-server/src/main/resources/admin.html): shows callers' text with `textContent`, never as HTML, and keeps the token only in the tab.
   - [`AdminCommand.java`](../../triada-net-server/src/main/java/org/triada/net/server/AdminCommand.java): the command line.
9. **Deployment.** [`deploy/Dockerfile`](../../deploy/Dockerfile), [`deploy/docker-compose.yml`](../../deploy/docker-compose.yml), [`deploy/Caddyfile`](../../deploy/Caddyfile) (`handle` blocks: Caddy would otherwise run `respond` before `reverse_proxy`), and [`deploy/.env.example`](../../deploy/.env.example).

## Build it yourself from scratch

1. **Start with the pretend terminal.** Before any board exists, write the thing that turns bytes into a 27 × 18 screen. Every later test reads screens with it.
2. **Write the session engine** with nothing that waits: the current input handler, a line editor, a one-key menu, paging. Test it with a fake line that records bytes.
3. **Design the storage as an interface** and write one test class that any implementation must pass. Write the in-memory version first; the board logic only ever sees the interface.
4. **Build the board screen by screen**, testing each with a scripted caller and a clock you move by hand: login, registration, menus, messages, mail, files, the wall. Test the limits from both sides, just under and just over.
5. **Transfers:** reuse the TRIMODEM state machines; while one runs, it takes every byte. Its timeout comes from the tick.
6. **Security as you go, not afterwards:**
   - hash passwords slowly with a salt and compare in constant time;
   - give unknown handles the same answer and the same cost as wrong passwords;
   - lock out per account and per address;
   - never use input as a path or paste it into SQL.
7. **Then the database:** the same contract test, a schema script, and a migration runner (version table, one transaction per script). Test a fresh database, a re-run, an upgrade with data, and a broken script.
8. **Then the server around it:** per-address limits, a ticker, structured logs, an admin API guarded by a token, and a command line on the same actions.
9. **Test it under load** with real sockets: many callers at once, each following a full script, and check the totals afterwards.
10. **What tends to go wrong:**
    - a test that types before the previous screen has fully arrived (wait for each screen's last words);
    - a prompt and a goodbye on the same line;
    - a two-line board that is busy in a test;
    - Windows refusing to delete an open database file;
    - Caddy running its catch-all `respond` before `reverse_proxy`.

## How we know it works

Run everything:

```bash
./gradlew build
```

All 833 tests pass: the 763 from before and 70 new ones.

The new ones:
- **`triada-net-core`:**
  - `BoardSessionTest` (29): every menu action; welcome, guest rights, registration rules, logins, lockouts, per-address limits, bans; posting, listing, reading, answering, reporting, paging, size limits, writing too fast; mail; file list, info, download, upload, the queue and upload limits; one-liners, last callers, who is on, time left, goodbye, the daily limit, idling, flooding, long input; busy lines, unknown numbers, the directory, unlisted boards.
  - `NetBasicsTest` (5): wrapping, the terminal, board files, passwords, rate limits.
  - `MemoryNetStoreTest` (6, the contract).
- **`triada-net-server`:**
  - `SqliteNetStoreTest` (8): the contract, plus reopening a file and quotes in text.
  - `MigrationsTest` (5): new, re-run, upgrade with data, broken script, script parsing.
  - `ServerSecurityTest` (6): calls at once and per minute from one address, wrong passwords over TCP, oversized input, flooding, path tricks in file names.
  - `LoadTest` (1): 50 callers at once over TCP on SQLite; each registers, posts, lists, reads the wall and says goodbye. All complete, with no errors, in about 3.5 seconds.
  - `ProtocolTest` (3): every answer to DIAL, the directory over TCP, a whole call over WebSocket.
  - `AdminTest` (7): the admin API with and without the token, the lockout after wrong tokens, no admin page without a token, the command line, configuration, the JSON log, loading boards.

The M17 server tests still pass, with `ServerConnectionTest` updated for the caller's address.

**By hand.** The server was started from its start script with an admin token, as the deployment does:

```bash
TRIADA_NET_ADMIN_TOKEN=local-test-token-0123456789 triada-net-server/build/install/triada-net-server/bin/triada-net-server
```

It printed:

```
TRIADA-NET: tcp 2385, websocket ws://localhost:2386/net, admin page http://localhost:2386/admin
  0-00-00 TRIADA-NET INFO
  1-00-00 TRIADA-NET TEST BOARD
```

A TRIBASIC program on a headless TRIADA (`triada run --type … --modem localhost:2385`) dialled `1-00-00`, logged in as GUEST, received the main menu, pressed G and read the goodbye. The server's log showed:

```
{"time":"…","event":"connect","ip":"127.0.0.1","via":"tcp"}
{"time":"…","event":"call","board":"1-00-00","ip":"127.0.0.1"}
{"time":"…","event":"login","board":"1-00-00","handle":"GUEST","ip":"127.0.0.1"}
{"time":"…","event":"hangup","board":"1-00-00","handle":"GUEST"}
```

`curl` showed the rest:
- `/admin/api/queue` without the token: 401;
- with the token: `[]`;
- `/health`: `OK`.

**Not checked:** the Docker image and the compose file. This computer has no Docker, so they have never been built or run. Everything inside them (the server, its configuration through environment variables, the start script) was run, but the kit as a whole is untested until someone follows `DEPLOY.md`.

## Problems we hit and how we solved them

- **A goodbye ran into the prompt.** "IDLE TOO LONG" appeared right after "COMMAND? " on the same line, wrapped mid-word. `TermOut.freshLine` now starts a new line when needed, remembering the last character even across sends.
- **The tests found the board's own rules.** A test of upload limits failed because the board had only two lines and two earlier callers were still connected: the third heard BUSY. The test now hangs its callers up.
- **The test caller saw old text.** In the load test, a caller waited for "PRESS ANY KEY." and found the one from the previous screen still showing. It typed too early, and the server, rightly, read the key as an answer to the wrong question. The TCP test caller now only looks at text that arrived since it last typed. Each step waits for the last words of its screen (for example "MIN LEFT. COMMAND? "), not the first, because a screen arrives in pieces.
- **Windows keeps database files locked** while they are open, so the temporary folders of the SQLite tests couldn't be deleted. The tests now close every store they open.
- **The JDK warns about native code.** `sqlite-jdbc` loads a native library, and Java 25 asks for permission. The server and its tests run with `--enable-native-access=ALL-UNNAMED`.
- **An old server was still listening.** The M17 echo server's Java process had outlived the shell that started it and still held port 2385. It was found by its port and stopped.
- **Caddy's order.** A first `Caddyfile` would have answered every request with its greeting, because Caddy runs `respond` before `reverse_proxy` whatever order they are written in. It now uses `handle` blocks. (Found by reading Caddy's directive order, not by running it.)
- **Shell quoting** mangled backslashes in a few files written through the shell; they were rewritten with the editor.

## Limitations and what comes next

- **One test board, no content.** M19 brings the starter boards, seeded 1985 posts, the story outline (for your approval first) and the games in the file areas.
- **No terminal program yet.** Calling from TRIBASIC with `MDIAL`/`MRECV$` works, but shows control codes raw. TERM.TRI (M19) will draw the screens, play the dial sounds and do TRIMODEM on the machine.
- **The deployment kit is untested** (no Docker here). Nothing has been deployed: that is yours to do, with `docs/net/DEPLOY.md`.
- **Limits are fixed at their defaults** for now; `Limits` is ready for settings.
- **One SQLite connection** serves everyone. That's plenty for a hobby network (50 callers at once in the load test), not for thousands.
- **The web version** still has no server address set, so on GitHub Pages every call ends offline. M19 makes it a build setting and adds "TRIADA-NET IS OFFLINE".
- **No door game.** It is optional, and comes only after everything else.

## New words

- **Sysop**: the person who runs a bulletin board.
- **Handle**: a caller's name on a board.
- **One-liner**: a short line left on a board's wall.
- **Event-driven**: reacting to each key or tick and returning, instead of waiting.
- **Database**, **SQLite**, **transaction**, **migration**: an organised store of data; a small one in a single file; a group of changes that happen all together or not at all; a numbered script that upgrades the tables.
- **Hash**, **salt**, **PBKDF2**, **constant-time comparison**: how passwords are stored and checked.
- **Lockout** and **rate limit**: refusing for a while after too many failures, and capping how often something may happen.
- **Moderation queue**: uploads waiting for the sysop.
- **JSON**: the text format of the log and the admin API.
- **Test fixture**: helper code shared by tests.
- **Container (Docker)**, **reverse proxy**, **TLS**, **VPS**, **domain name**: the pieces of a deployment.
