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

### 2.1 Details settled in M17 (D-061)
- **Lines** end with a line feed (10). A carriage return (13) before it is ignored, so CR LF works too. A control line may be at most 256 characters; a longer one ends the call (the server answers `ERROR line too long`).
- **HELLO** fields are `key=value` words after `HELLO TRIADA-NET/1`, in any order. Unknown keys are ignored, so later clients can add fields. `cols` and `rows` must be 1 … 243; missing or other values mean 27 and 18.
- **WELCOME** carries the in-world date as `YYYY-MM-DD` (§4): `WELCOME TRIADA-NET/1 1985-11-09`.
- After `BUSY`, `NO NUMBER`, `NO CARRIER` or `ERROR …` the server closes the connection.
- **What the client makes of it** (the modem's status, SPEC §5.2): `CONNECT …` is connected (2); `BUSY` is −2; `NO NUMBER` is −3; `NO CARRIER` or any other answer to DIAL is −1; `ERROR …`, any other answer to HELLO, a connection that can't be opened, or one closed before WELCOME is "network offline" (−4); one closed after WELCOME is −1.
- **Bytes in the same packet as `CONNECT`**, after its line feed, are already raw characters.
- **Where:** TCP port 2385; WebSocket `ws://host:2386/net` (behind TLS: `wss://`). Both carry exactly the same byte stream; a WebSocket message may be binary or text. `GET /health` on the WebSocket port answers `OK`.
- **WebSocket origin check:** a browser sends the address of the page it runs on (its *origin*). The server can be given the allowed origins; a WebSocket from any other is closed at once with code 1008. With none configured, all are allowed (for development).

### 2.2 The echo exchange (M17)
The server of M17 serves a test exchange; M18 replaces it with the boards.

| Number | What happens |
|---|---|
| `1-11-11` | Connects. Sends `ECHO READY` and a carriage return, then echoes every character. Character 4 (end of transmission) makes it say `BYE` and a carriage return, and hang up |
| `2-22-22` | `BUSY` |
| `3-33-33` | `NO CARRIER` |
| any other | `NO NUMBER` |

---

## 3. TRIMODEM (program transfer)
- **Encoding:** 3^12 = 81^3, so **every word is exactly 3 characters** in base 81, using the printable characters 33…113.
- **Transfer:** blocks of 81 words. Each block is `ESC 'B'`, the block number (3 chars), 243 data chars and a checksum (3 chars). The receiver answers `ACK` (character 6) or `NAK` (character 21). After 3 failures, abort.
- **Start:** `ESC 'F'`, the file name, `;`, the length in words (3 chars), `;`, the type (`TB` or `TRI`), `;`, the load address (3 chars). **End:** `ESC 'E'`.
- **Uploads** use the same protocol in the other direction.

### 3.1 Details settled in M17 (D-063)
**Three characters per word.** Add 265,720 to the word, so it lies in 0 … 531,440. Write that in base 81: three digits, most significant first. Send digit *d* as the character 33 + *d*.

```
word 1000:  1000 + 265,720 = 266,720 = 40·6,561 + 52·81 + 68
            digits 40, 52, 68  ->  characters 73, 85, 101  =  "IUe"
word 0:     265,720 = 40·6,561 + 40·81 + 40  ->  "III"
word -265,720 -> "!!!"        word 265,720 -> "qqq"
```

Numbers in messages (block numbers, lengths, addresses, checksums) are words, encoded the same way. Every field has a fixed size, so messages are read by position. The character `;` (59) is itself a digit and may appear inside a number.

**Messages** (each starts with ESC, 27):

| Message | Bytes | The receiver answers |
|---|---|---|
| Start | `ESC 'F'` name `;` length(3) `;` type `;` address(3) | ACK, or NAK if the name, type or numbers are bad |
| Block | `ESC 'B'` number(3) data(243) checksum(3) | ACK, or NAK if a character isn't a digit, the checksum is wrong, or the number is out of order |
| End | `ESC 'E'` | ACK if every block arrived, otherwise NAK |
| Abort | `ESC 'X'` | nothing: the transfer is over |

- **The load address** is part of the start message, so a `.TRI` program arrives where it must run (`BRUN`). `TRIADA-NET.md` didn't include it; D-063 adds it. A `.TB` program carries 243.
- The **name** follows the triskette's rules (SPEC §5.1): 1 – 12 characters, A–Z, 0–9, at most one `.`. The **length** is 0 … 59,049 words, and the file must fit below the top address.
- Blocks are numbered from 0. The **last block is padded** with 0 words to 81; the length says how many words are real. A file of 0 words has no blocks: start, then end.
- **Checksum** of block *n* with the words w₀ … w₈₀: *n* + 1·w₀ + 2·w₁ + … + 81·w₈₀, wrapped to a word like any TRIADA sum (SPEC §1.3). Weighting by position catches two swapped words, which a plain sum misses, and the machine can compute it with MUL and ADD.
- **Repeats:** if a block or the start arrives a second time (because an ACK got lost), the receiver answers ACK again and keeps only one copy.
- **Failures:** a NAK, or no answer in time (the sender decides how long it waits), counts as one failure. The sender sends the same message again; at its third failure in a row it sends `ESC 'X'` and gives up. The receiver gives up after its third NAK in a row.
- Anything received while waiting for an ESC is ignored (line noise).

**Worked example.** The two-word TRIBASIC file `A.TB` = [5, 7], loaded at 243:

```
start:  ESC F A . T B ; I I K ; T B ; I L I        length 2 = "IIK", address 243 = "ILI"
block:  ESC B  I I I  I I N  I I P  I I I … (79 × "III")  I I \
               block 0  5      7      the padding            checksum
end:    ESC E
```

The checksum is 0 + 1·5 + 2·7 = 19 = `II\` (19 + 265,720 = 40·6,561 + 40·81 + 59, and 33 + 59 = 92 is `\`). The receiver answers ACK three times, and the file is [5, 7] at 243.

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

### 4.1 Details settled in M18 (D-068)
- **Boards are text files** (`.board`): `NUMBER`, `NAME` (≤ 27), `SYSOP`, `COLORS fg bg`, `LINES n` (callers at once; one more hears `BUSY`), `UNLISTED`, `MESSAGES id title` and `FILES id title` (areas; ids are 1–12 of A–Z, 0–9), and `WELCOME` … `END` (≤ 10 lines of ≤ 27 characters). The server has built-in boards, or reads a folder (`TRIADA_NET_BOARDS`).
- **What a caller sees:** the welcome screen, then `HANDLE (GUEST, NEW):`. `GUEST` may read and download; `NEW` shows the SYSOP RULES, then asks for a handle (2–12 letters and digits; GUEST, NEW, SYSOP and ALL are reserved) and a password (6–24 characters, typed twice, shown as `*`). The main menu:

  ```
  M MESSAGES   E MAIL
  F FILES      O ONE-LINERS
  L LAST CALLS W WHO IS ON
  T TIME LEFT  G GOODBYE
  ```

  Messages: `L` list (newest first), `R` read by number, `P` post. Reading one: `N` next older, `A` answer, `R` report, `Q`. A text ends with a line holding only `.`, then `S` saves or `A` aborts. Mail: `R` read, `S` send. Files: `D` download and `I` info by name, `U` upload. Lists longer than a screen ask `MORE? (Y/N)`.
- **Typing:** each key is echoed (a password as `*`); 8 (or 127) deletes; Return is 13 or 10, and a 10 right after a 13 is ignored. A line can't grow past its limit: the bell rings instead.
- **0-00-00** needs no login: it lists the boards that aren't unlisted, waits for a key and hangs up.
- **Downloads and uploads** use TRIMODEM (§3). After `D` and a name, the server starts sending. After `U`, it waits for the caller's start message, then asks for a description; the upload waits in the queue until the sysop approves it.
- **Dates** on boards are in-world: 2026-11-09 shows as 1985-11-09; times are UTC.

### 4.2 Security in M18 (D-067)
The numbers are in `Limits.DEFAULT` and D-067: PBKDF2 with 120,000 rounds, lockouts after 3 tries per call, 5 per account, 10 per address; 3 new accounts per address per day; 5 posts per 10 minutes; 3 uploads per hour; 30 minutes per board per day; 5 minutes idle; 1,000 characters a second; 4 calls at once and 20 a minute per address. Uploads are stored in the database, never under a caller's file name. The sysop's tools are the admin page (`/admin`, with the admin token) and `triada-net-server admin …` (see `docs/net/DEPLOY.md`).

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
- **The hidden story:** first only an **outline** in `docs/net/STORY.md`, for the human's approval. Premise: *"The Fourth State"*, rumours that NPO Triada engineers found a fourth trit value, with clues spread across boards that lead to an unlisted number. (Approved and written after M19, see §6.1.)
- **Downloadable files:** the existing games and TRIBASIC example programs, placed in fitting file areas with in-world descriptions.

### 6.1 Details settled in M19 (D-072, D-073)
- **The starter boards** are built in (`triada-net-core`, resources `net/boards/`):

  | Number | Board | Lines | Areas |
  |---|---|---|---|
  | 0-00-00 | TRIADA-NET INFO (the directory) | any | — |
  | 1-85-01 | NPO "TRIADA" SUPPORT | 4 | news, questions; programs |
  | 2-27-81 | TRIT & TRYTE MAGAZINE | 3 | readers' letters, listings; listings on disk |
  | 3-43-21 | PIONEER PALACE CLUB | 2 | club talk, first steps; games |
  | 9-13-13 | THE QUIET LINE | 1 | night thoughts |
  | 4-04-04 | THE BENCH (unlisted: the end of the hidden story) | 2 | test bench log |

- **Seeded posts and files** are written into the board files: `POST area date time author`, `SUBJECT …`, the text, a line with `.`; and `FILE area NAME date uploader`, a description, `.`. Dates are in-world (1985); they are stored 41 years later, so they show as written. Each post and file is added to the database once only (the table `seeds`). A fictional author gets an account that no password opens, so nobody can take the handle.
- **The files' words** are built during the build by `ContentGenerator`, from `triada-net-core/content/files.txt`: a `.tasm` program is assembled, and a `.bas` program is typed into a real TRIBASIC and SAVEd. `TERM.TRI` is not offered: TERM can't receive a file over itself.
- **The hidden story**, *The Fourth State*, is written into the boards (approved on 2026-11-09; complete from the start, no single answer): [`docs/net/STORY.md`](net/STORY.md), with spoilers.
- **A local node:** `LocalNode` runs the starter boards inside the emulator (desktop `--local-node`, command line `triada run --local-node`), with nothing kept after the program ends.

---

## 7. Deployment
**How to deploy:** [`docs/net/DEPLOY.md`](net/DEPLOY.md), with the kit in `deploy/` (M18, D-069).

**The web version's server** is set when it is built (M19, D-074): `-Ptriada.netUrl=wss://…` fills in `TRIADA_NET_URL` in its page. The GitHub Pages workflow passes the repository variable `TRIADA_NET_URL`. Unset, the page has no network: every call ends "TRIADA-NET IS OFFLINE", and nothing else about the page changes.

The project never creates hosting accounts or deploys to a public server by itself. It ships a deployment kit (Docker image, `docker-compose.yml` with a Caddy reverse proxy for TLS and `wss://`, configuration through environment variables, a `/health` endpoint, structured logs) and `docs/net/DEPLOY.md`, so the human can deploy. Local servers for development and tests are fine.
