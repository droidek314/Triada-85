# Milestone 19 — The terminal, the boards' content, and release 1.2

## In one paragraph

This milestone makes TRIADA-NET something a person can use. The TRIADA-85 gets a **terminal program**, TERM, written in the machine's own assembly language, as a 1985 programmer would have. Type `DIAL "3-43-21"`:
1. you hear the dial tone and the digits;
2. the Pioneer Palace computer club's welcome screen appears, two characters at a time;
3. you read the students' messages;
4. you take a game home onto your triskette with TRIMODEM;
5. you hang up and play it.

The network has **four starter boards** with their own sysops, colours and 1985 posts, and programs to download. The desktop and the command line can run the whole network inside the emulator, with no internet at all. The web version is ready to dial a real server as soon as one is deployed. Until then it says, honestly, "TRIADA-NET IS OFFLINE". The hidden story exists only as an outline, waiting for your approval. The machine's manual gains a chapter on the modem, and the whole of TRIADA-NET is released as **version 1.2**.

## What was built

What existed and was reused:
- the modem, TRIBASIC's modem words, the wire protocol, TRIMODEM and the server (M17, M18);
- the ROM build (`RomGenerator`), now also building TERM;
- the triskette ports of every host (M13), BRUN's way of calling a program, and TRIBASIC's warm start;
- the board files and the seeded store of M18, and the picture test (`DocImagesTest`, M11).

New:
- **TERM.TRI** ([`programs/term.tasm`](../../programs/term.tasm), about 1,400 lines of TRIADA assembly):
  - the phonebook (`PHONE.DAT`);
  - the dial sounds;
  - the terminal with its control codes and colour changes;
  - TRIMODEM download and upload, in assembly;
  - the hang-up.
- **`DIAL "number"`** in TRIBASIC ([`rom/basic.tasm`](../../rom/basic.tasm), `st_dial`).
- **Built in like the ROM:** `RomGenerator` also assembles TERM into `TermData`. `Rom.term()` and `Rom.prepareTriskette()` put TERM.TRI on the desktop's folder, the web version's storage and the command line's in-memory triskette.
- **The starter boards:** NPO "TRIADA" Support (1-85-01), Trit & Tryte Magazine (2-27-81), Pioneer Palace Club (3-43-21) and The Quiet Line (9-13-13), each with posts dated 1985 and, for three of them, programs to download.
- **Board files with content:**
  - `BoardDef` reads `POST` and `FILE` blocks;
  - `Content.seed` adds them to a store once each, remembered in the new `seeds` table (migration V002);
  - the board loader `Boards` moved into `triada-net-core`.
- **The downloadable programs**, built during the build by `ContentGenerator`. Games are assembled; TRIBASIC programs are typed into a real TRIBASIC and SAVEd.
- **`LocalNode`:** the whole network inside the emulator. Desktop `--local-node`, command line `triada run --local-node`.
- **The web version's server address** as a build setting (`-Ptriada.netUrl=wss://…`, repository variable `TRIADA_NET_URL` in the Pages workflow). Unset, it is offline.
- **Documents:**
  - [the manual's chapter 9](../manual/triada-net.md), "Calling the World: Your TRIADA-85 Modem";
  - [the story outline](../net/STORY.md), a DRAFT for your approval;
  - SPEC §10.6.5 and §11.1, NET-SPEC §6.1 and §7, and decisions D-070 … D-075;
  - two pictures in `docs/images`, the README's TRIADA-NET section, and the changelog;
  - release 1.2.

## Why it was built this way

**TERM high in memory, files at their own address** ([D-070](../DECISIONS.md#d-070--term-lives-at-34000-files-arrive-at-their-own-address-dial-fills-a-number-slot---milestone-m19-2026-11-09)). A downloaded game must land where it will run, or `BRUN` fails, and the triskette remembers the address a file was saved *from*. So TERM writes each arriving block straight to its final address and saves the file from there: no buffer, no new triskette command. TERM itself sits at 34,000, above where programs go, so a TRIBASIC program survives a call. When a transfer has used the program area, TERM leaves through TRIBASIC's warm start. That start keeps the memory if it holds a valid TRIBASIC program (a downloaded `.TB` does) and clears it otherwise.

**TERM.TRI on the triskettes people use, not on every machine** ([D-071](../DECISIONS.md#d-071--termtri-is-built-in-like-the-rom-and-put-on-the-default-triskettes---milestone-m19-2026-11-09)). A fresh machine's empty triskette is part of the released behaviour (tests and the manual list it), and a folder you name with `--disk` is yours. So each host puts TERM.TRI on its own default triskette, if it isn't there yet.

**Content as text, seeded once** ([D-072](../DECISIONS.md#d-072--the-boards-content-lives-in-their-board-files-seeded-once---milestone-m19-2026-11-09)). Posts and files are written into the board files, where a writer can change them without code. The store remembers which ones it has added, so restarting the server adds nothing twice. Fictional callers get accounts no password opens, so nobody can register "SASHA" and speak for him.

**TRIBASIC programs typed in for real** ([D-073](../DECISIONS.md#d-073--the-boards-programs-are-built-during-the-build-tribasic-ones-by-typing-them-in---milestone-m19-2026-11-09)). A `.TB` file holds TRIBASIC's tokens, which only the ROM's own tokenizer makes. Rather than commit binary files or write a second tokenizer in Java, the build types each example into a headless TRIADA and saves it. The file on the board is exactly what a person's own SAVE would make.

**Offline unless told otherwise** ([D-074](../DECISIONS.md#d-074--the-web-versions-server-is-a-build-setting-offline-is-the-default---milestone-m19-2026-11-09)). Deploying a server is your step. When you have one, a single repository variable points the web version at it. Until then the page works exactly as before and every call ends "TRIADA-NET IS OFFLINE".

**A local node now, an in-browser node not yet** ([D-075](../DECISIONS.md#d-075--a-local-node-for-trying-triada-net-offline-no-in-browser-node-yet---milestone-m19-2026-11-09)). Running the boards inside the desktop emulator costs a few lines. Running them inside the browser would need `triada-net-core` to compile to JavaScript, but it uses the JDK's cryptography, file access and resources. That wasn't cheap, so per CLAUDE.md it wasn't attempted beyond that analysis.

## How it works — the plain-language version

**A terminal is a window onto another computer.** On the screen it shows what the other computer sends; it sends the keys you press. Everything clever happens far away, on the board. TERM's own work is small but exacting:
- **Dialling:** it plays the sounds of a call while the modem dials. Each digit is a pair of tones, as on a real telephone: digit 5 is 770 Hz and 1336 Hz together.
- **Showing:** it draws what arrives, character by character, obeying the board's instructions. Character 12 means "clear the screen"; ESC C means "change the text colour".
- **Sending:** it sends what you type.
- **Files:** it moves files with TRIMODEM, checking every block of 81 words.

**Taking a game home, step by step.**
1. On the board you press D and type `TRIZMEYK.TRI`.
2. The board sends a start message: the file's name, length and address, each number as three characters. TERM checks that the file fits below 34,000 and answers ACK.
3. Then come blocks: 3 characters for the block number, 243 for 81 words, 3 for a checksum. TERM turns every three characters back into a word, adds up the checksum (the block number, plus 1 × the first word, 2 × the second, and so on), and compares it with the one sent. If they match, it writes the words straight to their final place in memory and answers ACK; if not, NAK, and the board sends the block again. A `#` appears for every twentieth of the file.
4. The end message arrives. TERM saves the file onto the triskette, from exactly where it now lies, and prints `SAVED TRIZMEYK.TRI`.
5. You press Esc, then H. TERM hangs up and returns to TRIBASIC, and `BRUN "TRIZMEYK"` starts the snake.

**The boards have a life before you arrive.** When the server (or the local node) starts, it reads the board files and adds their posts once:
- the club leader's welcome from March 1985;
- Sasha's 61-trit snake record, and Vova's reply (64, "with the television on the kitchen table");
- the Quiet Line's night thoughts.

Your posts appear alongside theirs.

**No network? Bring your own.** With `--local-node` the boards run inside the emulator itself. The call never leaves your computer, but it is the same server code, so it sounds, looks and behaves exactly like the real thing.

## How it works — the programmer's version

Read in this order.

1. **DIAL.** [`rom/basic.tasm`](../../rom/basic.tasm), `st_dial`:
   - copies `"TERM.TRI"` into FNAME (the triskette reads names from RAM);
   - LOAD-HOMEs it with `diskload` (R1 = −1); `?FILE NOT FOUND ERROR` comes from `b_diskstatus`;
   - copies up to 16 characters of the number to TERM's address + 2;
   - calls it with `b_call` and ends with `b_afterload`, exactly as `st_brun` does.
2. **TERM's skeleton.** [`programs/term.tasm`](../../programs/term.tasm):
   - `term` (a `JMP`) and `t_number`;
   - `t_start` saves SP in `t_sp`, so `t_quit` can return from any depth;
   - `t_quit` silences the voices, hangs up, puts the colours back and returns. If `t_clob` says the program area was used, it jumps to `BASIC_WARM` instead.
3. **The phonebook and dialling.**
   - `t_book` (PHONE.DAT or `t_deflist`) and `t_newnumber` (append and DISKSAVE).
   - `t_dial` sets MDM_ADDR and MDM_CMD, then plays the dial tone, the digit pairs from `t_low` and `t_high`, and the screech.
   - Each frame `t_dialframe` checks Esc and the status. In turbo the modem answers at once, so the sounds stop at once.
   - The result is printed (`t_m_busy`, `t_m_nonumber`, `t_m_offline` …).
4. **Online.**
   - `t_online` loops: take every waiting character to `t_incoming`, check the status, take a key to `t_key`, then `WAIT`.
   - `t_key` turns Return (10) into 13 and handles Esc H and Esc U; `t_send` waits while MDM_TXFREE is 0.
   - `t_incoming` is a small state machine (`S_TEXT`, `S_ESC`, `S_P1`, `S_P2`, `S_HDR`, `S_BLK`). Text and control codes go to the ROM's `PUTCHAR`, `CLS` and `LOCATE`. `ESC C` goes to `t_color`, the same pixel-by-pixel recolouring as TRIBASIC's `COLOR`, which keeps TRIBASIC's record of the colours (system variables 41 and 42) up to date.
5. **Receiving.**
   - `t_hdrstart`/`t_hdrchar` read the start message field by field (`t_hphase`), never by splitting at `;`, because `;` is itself a digit character.
   - `t_hdrcheck` checks 243 ≤ address and address + length ≤ 34,000.
   - `t_blkchar` turns digits into words with `t_digit`. It accumulates `acc × 81 + (c − 73)`, the balanced base-81 value, which never overflows: the largest is exactly 265,720.
   - `t_blkdone` computes the checksum with `MUL` and `ADD` (their wrapping is exactly NET-SPEC §3's), handles repeats, and copies the block to its place.
   - `t_dlend` saves with `DISKSAVE`; `t_fail` sends NAK and counts to three; `t_progress` draws the `#` marks.
6. **Sending.**
   - `t_upload`: a triskette file is LOAD-HOMEd and its type found from the name.
   - `tu_send`/`tu_wait`/`tu_ack`/`tu_nak`: each message is sent, then up to 600 frames wait for ACK or NAK; three failures send `ESC X`.
   - `t_sendmsg` builds the messages. `t_sendword` encodes a word with TRIADA's own rounding `MOD`, which gives balanced digits −40 … 40 directly, so nothing overflows.
7. **Built in.**
   - [`RomGenerator.java`](../../triada-rom/src/generator/java/org/triada/rom/tools/RomGenerator.java) → `RomGenerator.term` writes `TermData`.
   - [`Rom.java`](../../triada-rom/src/main/java/org/triada/rom/Rom.java) → `Rom.term`, `Rom.prepareTriskette`.
   - The hosts: [`DesktopLauncher.java`](../../triada-desktop/src/main/java/org/triada/desktop/DesktopLauncher.java), [`WebApp.java`](../../triada-web/src/main/java/org/triada/web/WebApp.java), [`RunCommand.java`](../../triada-cli/src/main/java/org/triada/cli/RunCommand.java).
8. **Content.**
   - [`BoardDef.java`](../../triada-net-core/src/main/java/org/triada/net/BoardDef.java) → `BoardDef.Post`, `BoardDef.FileDef`, `BoardDef.parse` (blocks ended by a line with `.`).
   - [`Content.java`](../../triada-net-core/src/main/java/org/triada/net/Content.java) → `Content.seed`.
   - [`Boards.java`](../../triada-net-core/src/main/java/org/triada/net/Boards.java) → `Boards.builtIn`, `Boards.load`, `Boards.file`.
   - `NetStore.markSeeded`, with [`V002__seeds.sql`](../../triada-net-server/src/main/resources/db/V002__seeds.sql).
   - The boards: [`npo-support.board`](../../triada-net-core/src/main/resources/net/boards/npo-support.board), [`trit-and-tryte.board`](../../triada-net-core/src/main/resources/net/boards/trit-and-tryte.board), [`pioneer-palace.board`](../../triada-net-core/src/main/resources/net/boards/pioneer-palace.board) and [`quiet-line.board`](../../triada-net-core/src/main/resources/net/boards/quiet-line.board).
9. **The programs the boards offer.** [`ContentGenerator.java`](../../triada-net-core/src/contentgen/java/org/triada/net/tools/ContentGenerator.java) → `ContentGenerator.typeIn` boots a TRIADA, types `NEW`, the program and `SAVE "NAME"` with a `KeyFeeder`, and takes the file from the in-memory triskette. The list is [`content/files.txt`](../../triada-net-core/content/files.txt).
10. **The local node.** [`LocalNode.java`](../../triada-net-core/src/main/java/org/triada/net/LocalNode.java) → `LocalNode.network`, `LocalNode.line`.
11. **The web address.** [`triada-web/build.gradle.kts`](../../triada-web/build.gradle.kts) fills in `TRIADA_NET_URL` from `triada.netUrl` and refuses anything that isn't `wss://`. [`pages.yml`](../../.github/workflows/pages.yml) passes `vars.TRIADA_NET_URL`.

## Build it yourself from scratch

1. **Decide where the terminal lives** before writing it. Downloads must land at their own address, so the terminal must sit where no program will be. Decide also how it leaves when it has overwritten the program area.
2. **Write the terminal in layers:**
   - first a loop that prints what arrives and sends what is typed;
   - then the control codes;
   - then dialling and its sounds;
   - then the phonebook.

   Test each layer against a server in the same program, through the machine's registers, frame by frame.
3. **TRIMODEM in assembly:**
   - encode and decode with balanced base-81 digits (−40 … 40). Shifting by 265,720 as the Java version does would overflow a TRIADA word;
   - compute the checksum with the CPU's own wrapping `MUL` and `ADD`;
   - read the start message by position.

   Test against the Java sender and receiver, which are already known to be right.
4. **Watch the flags.** On this CPU `LDI` sets S too, so a compare must be the last thing before its jump.
5. **Build the program into the emulator** like the ROM, and decide which triskettes get it.
6. **Put content in data files**, seeded once by key. Make fictional authors' accounts unusable.
7. **Generate binary content during the build** by running the real machine, rather than committing it or writing a second implementation.
8. **Test the whole story end to end:** boot, dial, log in, read, download, hang up, run what was downloaded.
9. **What tends to go wrong:**
   - lines one character too long for 27 columns;
   - a welcome screen of 11 lines;
   - receiving a file over the terminal itself;
   - a test that expects the old behaviour of a word that was only reserved (DIAL said `?SYNTAX ERROR` in M17).

## How we know it works

Run everything:

```bash
./gradlew build
```

All 843 tests pass. New in M19:
- **`TermTest`** (8), on a headless TRIADA with the network in process:
  - **the end-to-end test**: boot, `DIAL "3-43-21"`, GUEST, read Sasha's message, download `TRIZMEYK.TRI` (checked word for word against the board's file), Esc H, then `BRUN "TRIZMEYK"` shows "PRESS Z TO START";
  - a `.TB` downloaded, then `LOAD`ed and `RUN`;
  - the phonebook, and an offline call ("NO CARRIER", "TRIADA-NET IS OFFLINE");
  - a new number dialled and kept in `PHONE.DAT`;
  - BUSY and NO SUCH NUMBER;
  - an upload with Esc U, arriving in the queue word for word;
  - the board's colour while online, and the old one back after;
  - the manual's example program.
- **`DocImagesTest`** (2 more): the Trit & Tryte welcome and a message on the Pioneer Palace board, drawn by the real machine on a fixed date.
- The board and store tests now also cover `POST` and `FILE` blocks and `markSeeded` (both stores), and the migrations include V002.
- `modem_syntax` now expects `DIAL` on an empty triskette to say `?FILE NOT FOUND ERROR` (it said `?SYNTAX ERROR` while DIAL was only reserved).

**The content build:** `./gradlew :triada-net-core:processResources` prints `TRIADA-NET content: 12 files`.

**By hand:**

```bash
./gradlew :triada-desktop:run --args="--local-node"
```

Then type `DIAL "0-00-00"`, or `BRUN "TERM"` for the phonebook.

**The web build with a server address:**

```bash
./gradlew :triada-web:gdx_teavm_web_js_build -Ptriada.netUrl=wss://net.example.org/net
```

That build puts the address into the page. An `http://` address is refused with "triada.netUrl must be a wss:// address". Without the property the page has an empty address, as deployed today.

**Not checked:**
- a call in the desktop window, by eye and ear. The desktop was started with `--local-node` in its capture mode: it ran, saved its picture, and put `TERM.TRI` into the `triskettes/` folder. But no call was watched or heard in an open window. The same machine, TERM and network run in the tests and made the pictures;
- the deployed web page making a call. No server is deployed, so on GitHub Pages every call ends "TRIADA-NET IS OFFLINE"; the offline path is what the tests check.

## Problems we hit and how we solved them

- **27 columns are few.** The first phonebook entry "2 1-85-01 NPO TRIADA SUPPORT" was 28 characters and wrapped, and the club's welcome screen had a 28-character line. The tests caught both; the texts were shortened.
- **A subject of 25 characters** was cut by the board's 24-character limit ("RE: HOW DO I START A GAM"). It showed in the picture, and the posts were reworded.
- **TERM can't receive itself.** TERM.TRI was first on the support board's file list, but its address range (34,000 …) is exactly where TERM refuses files. It is not offered; TERM comes on every triskette instead.
- **A reserved word started working.** The M17 suite pinned `DIAL` to `?SYNTAX ERROR`. Now that it works, an empty triskette gives `?FILE NOT FOUND ERROR`, and the expected screen was updated, since it's the behaviour the spec asks for.
- **A user's folder must stay theirs.** Putting TERM.TRI into every triskette would have changed a `--disk` folder's contents (a test noticed). Only the hosts' default triskettes get it.
- **A broken test migration** had to become number 3, now that number 2 is real.
- **Shell quoting** mangled escapes in generated Java again; those files were fixed with the editor.

## Limitations and what comes next

- **The story is only an outline** ([STORY.md](../net/STORY.md)), waiting for your approval and answers to its questions. No clues are in the boards yet.
- **No server is deployed**, so the web version is offline. Deploy one with [DEPLOY.md](../net/DEPLOY.md), then set the repository variable `TRIADA_NET_URL` to its `wss://…/net` address.
- **No in-browser node** (D-075).
- **TERM's limits:** files up to 33,756 words; no scroll-back; ESC L and colours as the boards use them; one upload at a time, chosen by name.
- **No door game** (optional, and still optional).
- **No turbo switch** in the frontends; TERM follows the modem's speed.

## New words

- **Terminal (program)**: a program that shows what another computer sends and sends what you type.
- **Phonebook**: TERM's list of numbers, kept on the triskette as `PHONE.DAT`.
- **Dual-tone dialling**: telephone digits as pairs of tones, one low and one high, played together.
- **Seeding**: putting a system's starting content into its database, once.
- **Local node**: TRIADA-NET running inside the emulator, offline.
- **Build setting**: a value given when the program is built, not written in its code (`-Ptriada.netUrl`).
