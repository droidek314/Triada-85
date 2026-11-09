# TRIADA-NET — Phase 3 Task for the Agent

This file adds **TRIADA-NET**: a fictional 1985 network of bulletin board systems (BBSes) that a TRIADA-85 can **really dial into**. The machine gets a modem. A real Java server hosts the boards, and the machine runs a terminal program to use them.

- **Part A** is the work plan: milestones M16–M18. Merge it into `CLAUDE.md` §3.
- **Part B** is the specification. Merge it into `docs/SPEC.md` (machine side) and a new `docs/NET-SPEC.md` (server and protocol side).

All rules in `CLAUDE.md` apply in full, including §5 (documentation). Two rules from earlier phases still hold: **reuse what exists, never duplicate it**, and **the released behaviour wins** where it differs from this file. Log every adaptation in `DECISIONS.md`.

**Why this matters:** it's the feature no other ternary or fantasy-computer project has. Games like *Digital: A Love Story* simulate BBSes inside a closed game. TRIADA-NET is a real server and protocol that a working emulated machine connects to, and other people can write their own clients and boards for it.

---

# PART A — Work plan

## A.0 Before writing any code
1. Read the current `SPEC.md`, `DECISIONS.md`, `ARCHITECTURE.md`, the machine profile code (M15), the ROM and TRIBASIC sources.
2. Check I/O registers 28–35 are free (Part B §B.1). If they aren't, pick free ones and log it.
3. The modem is a device listed in the **TRIADA-85 machine profile**.
4. **Never create accounts on hosting services and never deploy to a public server yourself.** Prepare everything (Docker image, config, docs) so the human can deploy. Local servers for development and tests are fine.

## A.1 Milestones

### M16 — Modem device, protocol and TRIMODEM
- The modem device in `triada-core` (§B.1), behind a `ModemPort` interface. Backends:
  - **TCP** (desktop, CLI);
  - **WebSocket** (web build; it must use `wss://` because GitHub Pages is HTTPS);
  - **in-process loopback** (tests).
- Client side of the wire protocol (§B.2).
- **TRIMODEM** encoding and transfer protocol (§B.3) as a shared pure-Java library. It's used by both server and tests. The machine-side version is written in TRIADA assembly in M18.
- A minimal **echo test server**, enough to test the modem end to end.
- TRIBASIC modem statements (§B.6).
- Tests: every modem status, the character rate limit, buffer overflow, and hang-up from either side. Include a TRIBASIC test program that dials the echo server and checks what comes back.

### M17 — The TRIADA-NET server
- New modules:
  - **`triada-net-core`:** pure Java, no sockets. Boards, users, sessions, menus, messages, mail, file areas, rendering for a 27×18 screen.
  - **`triada-net-server`:** TCP and WebSocket transports, persistence, config, admin.
- **Recommended stack** (change it only with a DECISIONS entry):
  - Java 21+ **virtual threads** (one per caller);
  - **Javalin** for WebSocket, the health endpoint and the admin page;
  - plain `ServerSocket` for TCP;
  - **SQLite** through JDBC, with versioned SQL migration scripts run by a small in-house runner;
  - passwords hashed with **PBKDF2** from the JDK (no extra library).
- All features in §B.4, security and moderation in §B.5.
- **Deployment kit:** Dockerfile, `docker-compose.yml` (server plus a Caddy reverse proxy for TLS and `wss://`), all config through environment variables, a `/health` endpoint, structured logs. Write `docs/net/DEPLOY.md` explaining deployment to a small VPS or a container host, step by step, for someone who has never deployed a server.
- Tests:
  - unit tests for every menu action;
  - migration tests;
  - a protocol test suite;
  - a **load test**: 50 simulated callers at once on a local server, every session completes, no errors;
  - a **security test list**: wrong passwords, oversized input, flooding, path tricks in file names.

### M18 — Terminal, content and release v1.2
- **`TERM.TRI`**, the terminal program, written in TRIADA assembly and shipped on the default triskette (§B.7). Includes dial sounds, the control codes, TRIMODEM download and upload to the triskette, and a phonebook.
- TRIBASIC `DIAL "number"` starts TERM with that number (§B.6).
- **Content** (§B.8): the starter boards, seeded fictional posts, the hidden story **outline**, and the files available for download.
- **End-to-end test:** a headless TRIADA boots, dials an in-process server, logs in as GUEST, reads a message, downloads a program with TRIMODEM, saves it to the triskette, hangs up, then `BRUN`s or `LOAD`s and `RUN`s it successfully.
- **Web build:**
  - the server URL is a build-time setting;
  - if no server is configured or it's unreachable, dialing gives `NO CARRIER` and the terminal prints "TRIADA-NET IS OFFLINE";
  - the deployed site must never break because the server is down.
- **Desktop:** a "Start local node" option (or `triada net-server --local`), so anyone can try TRIADA-NET offline.
- **Optional, only if cheap:** compile `triada-net-core` into the web build as an in-browser offline node. Spike it first and log the result.
- Manual chapter `docs/manual/triada-net.md`, *"Calling the World: Your TRIADA-85 Modem"*, written in-universe.
- **Release v1.2:** README section with screenshots or a GIF of a call, changelog, Pages redeploy. Add TRIADA-NET to `docs/PRIOR-ART.md`'s "what's new" paragraph.

---

# PART B — Specification

## B.1 Modem device (machine side)

| k | Name | R/W | Meaning |
|---|---|---|---|
| 28 | MDM_CMD | W | 1 = DIAL (number string at MDM_ADDR), 2 = HANG UP |
| 29 | MDM_ADDR | R/W | RAM address of the number string (one char per word, 0-terminated) |
| 30 | MDM_STATUS | R | 0 = idle, 1 = dialing, 2 = connected, −1 = no carrier / lost, −2 = busy, −3 = no such number, −4 = network offline |
| 31 | MDM_TX | W | Send one character (0–255) |
| 32 | MDM_RX | R | Next received character. **Reading removes it.** Undefined if MDM_RXCOUNT = 0 |
| 33 | MDM_RXCOUNT | R | Number of characters waiting |
| 34 | MDM_TXFREE | R | Free space in the send buffer |
| 35 | MDM_SPEED | R | Line speed in bits per second (1200) |

Rules:
- **Speed is simulated.** At 1200 bit/s, at most **2 characters per frame** move in each direction. Text appears at a real 1985 pace. The rate comes from the machine profile. Frontends may offer a "turbo" setting that's off by default.
- Buffers hold 729 characters in each direction. Received characters beyond that are dropped, and the drop is counted in the debugger.
- Dialing takes a realistic moment of 1–3 seconds of frames before the result. The terminal fills this with dial sounds.
- On a hang-up from either side or a lost connection, status becomes −1.

## B.2 Wire protocol (emulator ↔ server)
Over TCP or WebSocket, using UTF-8 for control lines.
1. Client → `HELLO TRIADA-NET/1 model=TRIADA-85 cols=27 rows=18 client=<version>`
2. Server → `WELCOME TRIADA-NET/1 <in-world date>`, or `ERROR <reason>` and close.
3. Client → `DIAL <number>`
4. Server → `CONNECT 1200`, `BUSY`, `NO NUMBER` or `NO CARRIER`.
5. After `CONNECT`, **raw mode**: bytes in both directions are terminal characters. Over WebSocket, one message carries one or more bytes.
6. **Hang-up** = closing the connection, from either side.

The `cols`/`rows` values let future models (TRIADA-88) get wider screens from the same boards. Document the protocol fully in `NET-SPEC.md`, so others can write their own clients.

## B.3 TRIMODEM (program transfer)
- **Encoding:** 3^12 = 81^3, so **every word is exactly 3 characters** in base 81, using printable characters 33…113. Document this neat fact in the manual.
- **Transfer:** blocks of 81 words. Each block is `ESC 'B'`, the block number (3 chars), 243 data chars and a checksum (3 chars). The receiver answers `ACK` (character 6) or `NAK` (character 21). After 3 failures, abort.
- **Start:** `ESC 'F'`, the file name, `;`, the length in words (3 chars), `;`, the type (`TB` or `TRI`). **End:** `ESC 'E'`.
- **Uploads** use the same protocol in the other direction.
- The exact checksum is the agent's design. Put a worked example in the docs.

## B.4 Server features
- **Network of boards.** One server hosts many fictional BBSes, each with a phone number (format `D-DD-DD`), name, sysop persona, welcome screen made of TRIADA characters, menus and theme. `0-00-00` is "TRIADA-NET INFO", a directory of public numbers. Some numbers are unlisted and found through clues.
- **In-world date** = the real date minus 41 years (2026 → 1985), so time passes in step.
- **Callers:**
  - `GUEST` login (read-only);
  - `NEW` registration with a handle and password only, no email or personal data;
  - one account works across all boards.
- **Message areas:** list, read, post, reply. Newest first, paged for 27 columns.
- **Private mail** between callers.
- **File areas:** list, describe, download, upload (TRIMODEM). Size limits apply. Uploads go to a moderation queue before they become public.
- **Authentic touches:** last-callers list, who's online, a one-liner wall, a "time left this call" limit (e.g. 30 min per day per board).
- **Render everything for 27×18.** Word-wrap, paging with `MORE? (Y/N)`, and short menus with single-key commands.
- **Optional door game:** one small multiplayer text game themed on ternary logic. Only after everything else is done.

## B.5 Security and moderation (it's a public server)
- Passwords use PBKDF2 with salt. Use constant-time comparison. Lock out after repeated failures.
- **Rate limits** per connection and per IP: connections, logins, posts, uploads.
- Limits on input line length, message size, file size and total storage per user. File names are sanitized; never use caller input as a file path.
- **Sysop tools:** an admin page protected by a secret token, plus CLI commands to review the upload queue, delete posts, ban handles or IPs, and read reports. Callers can flag a message with `R` (report).
- An in-world **"SYSOP RULES"** screen on registration: be kind, no real personal data, no illegal content.
- Logs record connections and moderation actions, never passwords.
- WebSocket origin check: allow only the configured site origin(s).

## B.6 TRIBASIC additions

| Statement / function | Meaning |
|---|---|
| `DIAL "number"` | Runs `TERM.TRI` with that number (load it from the triskette) |
| `MDIAL "number"` | Low-level dial without the terminal, for programs and bots |
| `MHANGUP` | Hang up |
| `MSTATUS` | The MDM_STATUS value |
| `MSEND s$` | Send a string |
| `MRECV$` | All received characters, as a string of up to 80 |

With these, users can write their own terminal or bot in TRIBASIC. Include one example, `programs/basic/AUTOCALL.bas`.

## B.7 TERM.TRI (terminal program)
- Shows a phonebook (saved on the triskette as `PHONE.DAT`). You can dial a number from the list or type one.
- **Dial sounds:** a dial tone, then tones for each digit (two voices at once, like real phones), then a short "handshake" screech on the third voice. It's skipped in turbo mode.
- Prints `CONNECT 1200` and then shows the board.
- **Control codes:**
  - 12 clears the screen;
  - 13 starts a new line;
  - 8 is backspace;
  - 7 rings the bell;
  - `ESC 'C' fg bg` sets the text color;
  - `ESC 'L' col row` positions the cursor (values offset by 32);
  - TRIMODEM sequences start transfers.
- **Downloads** show a progress bar, save to the triskette, and report the result.
- **Esc + H** hangs up, and the program returns to TRIBASIC.

## B.8 Content
- **Starter boards** (5–7). For example:
  - **NPO "TRIADA" SUPPORT**: official, formal, with manuals and patches.
  - ***Trit & Tryte* MAGAZINE BBS**: type-in listings and reader letters.
  - **PIONEER PALACE COMPUTER CLUB**: students, games, beginners' questions.
  - **THE QUIET LINE**: a strange late-night board.
  - **TRIADA-NET INFO** (`0-00-00`).

  The agent may refine the names and personas, keeping the 1985 Eastern-Bloc-inspired, fictional, non-political tone.
- **Seeded fictional posts** on each board, dated in 1985. Fictional callers are part of the world. Real callers' posts appear alongside them.
- **The hidden story:** write **only an outline** in `docs/net/STORY.md`, marked **DRAFT, needs the human's approval**. A suggested premise is *"The Fourth State"*: rumours that NPO Triada engineers found a fourth trit value, with clues spread across boards that lead to an unlisted number. **Don't write the full story content until the human approves the outline.** Carry on with the technical work in the meantime.
- **Downloadable files:** the existing games and TRIBASIC example programs, placed in fitting file areas with in-world descriptions.
