# TRIADA-NET — Server and Protocol Specification

Version 1.2 (in progress, milestones M17–M19). This document is the **source of truth** for the TRIADA-NET server, the wire protocol between an emulated TRIADA and a server, and the TRIMODEM transfer protocol. The machine side (the modem device, the TRIBASIC statements, the terminal program) is in [SPEC.md](SPEC.md) §5.2, §10.6.5 and §11. If code and this document disagree, the code is wrong, or this document is changed on purpose and the change is logged in [DECISIONS.md](DECISIONS.md).

The protocol is documented completely, so that anyone can write their own client or their own boards.

---

## 1. What TRIADA-NET is
A fictional 1985 network of bulletin board systems (BBSes): computers you dial with a modem to read messages, write mail and download programs. One real server hosts many fictional boards, each with its own phone number. A TRIADA-85, emulated on the desktop, on the command line or in the browser, really dials in and talks to it.

---

## 2. Wire protocol (emulator ↔ server)
Over TCP or WebSocket, using UTF-8 for control lines.
1. Client → `HELLO TRIADA-NET/1 model=TRIADA-85 cols=27 rows=18 client=<version>`
2. Server → `WELCOME TRIADA-NET/1 <in-world date>`, or `ERROR <reason>` and close.
3. Client → `DIAL <number>`
4. Server → `CONNECT 1200`, `BUSY`, `NO NUMBER` or `NO CARRIER`.
5. After `CONNECT`, **raw mode**: bytes in both directions are terminal characters. Over WebSocket, one message carries one or more bytes.
6. **Hang-up** = closing the connection, from either side.

The `cols`/`rows` values let future models (a TRIADA-88) get wider screens from the same boards.

The web build must use `wss://` (TLS WebSocket), because GitHub Pages is served over HTTPS.

---

## 3. TRIMODEM (program transfer)
- **Encoding:** 3^12 = 81^3, so **every word is exactly 3 characters** in base 81, using the printable characters 33…113.
- **Transfer:** blocks of 81 words. Each block is `ESC 'B'`, the block number (3 chars), 243 data chars and a checksum (3 chars). The receiver answers `ACK` (character 6) or `NAK` (character 21). After 3 failures, abort.
- **Start:** `ESC 'F'`, the file name, `;`, the length in words (3 chars), `;`, the type (`TB` or `TRI`). **End:** `ESC 'E'`.
- **Uploads** use the same protocol in the other direction.
- The exact checksum is defined in M17, with a worked example here and in the manual.

---

## 4. Server features
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

---

## 5. Security and moderation (it's a public server)
- Passwords use PBKDF2 with salt. Use constant-time comparison. Lock out after repeated failures.
- **Rate limits** per connection and per IP: connections, logins, posts, uploads.
- Limits on input line length, message size, file size and total storage per user. File names are sanitized; caller input is never used as a file path.
- **Sysop tools:** an admin page protected by a secret token, plus CLI commands to review the upload queue, delete posts, ban handles or IPs, and read reports. Callers can flag a message with `R` (report).
- An in-world **"SYSOP RULES"** screen on registration: be kind, no real personal data, no illegal content.
- Logs record connections and moderation actions, never passwords.
- WebSocket origin check: allow only the configured site origin(s).

---

## 6. Content
- **Starter boards** (5–7). For example:
  - **NPO "TRIADA" SUPPORT**: official, formal, with manuals and patches.
  - ***Trit & Tryte* MAGAZINE BBS**: type-in listings and reader letters.
  - **PIONEER PALACE COMPUTER CLUB**: students, games, beginners' questions.
  - **THE QUIET LINE**: a strange late-night board.
  - **TRIADA-NET INFO** (`0-00-00`).

  Names and personas may be refined, keeping the 1985 Eastern-Bloc-inspired, fictional, non-political tone.
- **Seeded fictional posts** on each board, dated in 1985. Fictional callers are part of the world. Real callers' posts appear alongside them.
- **The hidden story:** only an **outline** in `docs/net/STORY.md`, marked **DRAFT, needs the human's approval**. Suggested premise: *"The Fourth State"*, rumours that NPO Triada engineers found a fourth trit value, with clues spread across boards that lead to an unlisted number. The full story content is not written until the human approves the outline.
- **Downloadable files:** the existing games and TRIBASIC example programs, placed in fitting file areas with in-world descriptions.

---

## 7. Deployment
The project never creates hosting accounts or deploys to a public server by itself. It ships a deployment kit (Docker image, `docker-compose.yml` with a Caddy reverse proxy for TLS and `wss://`, configuration through environment variables, a `/health` endpoint, structured logs) and `docs/net/DEPLOY.md`, so the human can deploy. Local servers for development and tests are fine.
