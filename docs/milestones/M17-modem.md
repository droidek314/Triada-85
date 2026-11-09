# Milestone 17 — The modem, the wire protocol and TRIMODEM

## In one paragraph

The TRIADA-85 can now make a phone call. It has a **modem**: a box that, in 1985, let a home computer talk to another computer over a telephone line. A program dials a number, and when the other side answers, every character it sends goes down the line and the other side's characters come back, slowly, about two per frame, as on a real 1200-baud line. The call really leaves the emulator. On the desktop and the command line it travels over the internet (TCP); in the web version over a WebSocket; in tests to a server inside the same program. On the other end sits a real **TRIADA-NET server**. For now it serves one test board that repeats everything you type; the bulletin boards come in M18. TRIBASIC gets five words to use the modem (`MDIAL`, `MSEND`, `MRECV$`, `MSTATUS`, `MHANGUP`). There is also **TRIMODEM**, the way programs will travel down the line: every word as exactly three characters, because 3^12 = 81^3.

This is the first milestone of Phase 3, TRIADA-NET. `TRIADA-NET.md` calls it M16; M16 was already the housekeeping, so it is M17 ([D-060](../DECISIONS.md#d-060--triada-net-is-milestones-m17m19-merged-into-claudemd-specmd-and-a-new-net-specmd---phase-3-2026-11-09)).

## What was built

What existed already and was reused, not duplicated:
- the device pattern of the triskette drive: a device on I/O registers, behind a port that the host plugs in (M13);
- the machine profile (M16): the modem is `Device.MODEM`, and its speed `modemBitsPerSecond`;
- the end of every frame, `Machine.finishFrame`, where the modem now moves its characters;
- TRIBASIC's keyword tables and helpers (`b_fname`, `b_tmpnew`, `b_poll`), and the TRIBASIC test suite (M14).

New:
- **The modem device** (`triada-core`, package `org.triada.core.modem`):
  - `ModemDevice`: I/O registers 28–35 (SPEC §5.2), two buffers of 729 characters, the dialing pause and the speed limit;
  - `ModemPort`: the line behind it, as the device sees it, with `ModemPort.OFFLINE` as the default;
  - `WireModemPort`: the client side of the wire protocol;
  - `ModemLink`: the byte pipe a transport provides;
  - `LoopbackLink`: an in-process pipe.
- **Transports:** `TcpModemLink` (command line `triada run --modem host:port`, desktop `--modem host:port`) and `WebModemLink` (web; the server address from the page, or `?net=` in the address).
- **A new module, `triada-net-core`** (pure Java):
  - `Trimodem`, `TrimodemSender` and `TrimodemReceiver`;
  - the server side of the wire protocol: `ServerConnection`, `Exchange`, `Answer`, `Call`, `Wire`, `Caller`;
  - `InWorld` (the date in 1985);
  - `EchoExchange`, the test board;
  - `LoopbackNetwork`, a server in the same program.
- **A new module, `triada-net-server`:** `NetServer`, the echo server on TCP port 2385 and WebSocket port 2386 (`/net`), with `/health`.
- **TRIBASIC:** tokens −69 … −74 (`DIAL` reserved for M19, `MDIAL`, `MHANGUP`, `MSEND`, `MSTATUS`, `MRECV$`), and the example `programs/basic/AUTOCALL.bas`.
- **The debugger** shows the modem's status, the characters waiting and the characters dropped.
- **Documents:**
  - SPEC §5.2 and §10.6.5;
  - [NET-SPEC.md](../NET-SPEC.md) §2.1, §2.2 and §3.1, with a worked TRIMODEM example;
  - decisions D-061 … D-065.

## Why it was built this way

**A port and a link, not one class per transport** ([D-061](../DECISIONS.md#d-061--the-modems-line-a-polled-port-a-byte-pipe-link-and-lf-ended-control-lines---milestone-m17-2026-11-09)). The machine's core may not wait, start threads or open sockets (it has to compile to JavaScript too). So the modem talks to a `ModemPort` whose methods all return at once. The device asks for the status once per frame. The protocol (HELLO, WELCOME, DIAL, CONNECT) is written once, in `WireModemPort`, on top of a `ModemLink`: a plain pipe of bytes. Each transport is then only that pipe, about twenty lines long. The protocol is tested once, against a scripted server on an in-process pipe.

**The network modules start now** ([D-062](../DECISIONS.md#d-062--the-triada-net-modules-start-in-m17-with-javalin-for-websocket---milestone-m17-2026-11-09)). M17 needs TRIMODEM "as a shared library" and an echo server on TCP and WebSocket. M18 plans `triada-net-core` and `triada-net-server`, so they were created now, and nothing will have to move later. WebSocket uses Javalin, as CLAUDE.md recommends. Only the server carries it; the emulator never does.

**TRIMODEM's gaps filled** ([D-063](../DECISIONS.md#d-063--trimodem-a-weighted-checksum-the-load-address-in-the-start-message-fixed-size-fields---milestone-m17-2026-11-09)):
- **The checksum** is the sum of the words, each weighted by its place, plus the block number. A plain sum would miss two swapped words; a CRC would be hard to write in TRIADA assembly for M19.
- **The start message also carries the load address.** Without it, a downloaded game would land at the wrong place and `BRUN` would fail.
- **Fields are read by position.** The separator `;` (character 59) is itself one of the 81 digit characters.

**Small rules written down** ([D-064](../DECISIONS.md#d-064--modem-details-the-dialing-pause-bad-numbers-hang-ups-and-the-debugger---milestone-m17-2026-11-09)). The spec left these open:
- how long dialing takes (longer for more digits, like a rotary dial);
- what a broken number does (−3 at once);
- what reading an empty receive buffer gives (0);
- when a hang-up from the other side shows (only after its last words have arrived).

**New keywords at the end** ([D-065](../DECISIONS.md#d-065--tribasics-modem-keywords-are-tokens-69--74---milestone-m17-2026-11-09)). TRIBASIC stores keywords as numbers, and programs on triskettes keep those numbers. The six new keywords take the next free ones, −69 … −74, so no saved program changes.

## How it works — the plain-language version

**A phone call in four steps.** Picture the TRIADA-85 next to a telephone:
1. The program writes the number, say `1-11-11`, somewhere in memory and tells the modem "dial" (MDM_CMD = 1). The modem's status becomes 1, *dialing*.
2. The modem waits a moment, as a rotary dial takes time to turn. One second, plus a fifth of a second per digit: two seconds for `1-11-11`. Meanwhile, far away, the call is placed.
3. The other side answers. The status becomes 2, *connected*, or one of the bad endings:
   - −2: busy;
   - −3: no such number;
   - −1: it rang but nobody answered ("no carrier");
   - −4: there is no network at all.
4. Now the two sides talk. What the program writes into MDM_TX goes into the modem's **send tray**, and what arrives lands in its **receive tray**. Each tray holds 729 characters.

**Why so slow?** A 1985 modem ran at 1200 bits per second. One character takes 10 bits (8 for the character, a start bit and a stop bit), so that is 120 characters a second. At 60 frames a second, that is exactly **2 characters per frame** in each direction. At the end of every frame the modem carries two characters from the send tray down the line and two from the line into the receive tray. A screen of 486 characters takes about four seconds to arrive, just as it did in 1985. If the receive tray is full, new characters fall on the floor, and the debugger counts them.

**The conversation between emulator and server.** Before the characters can flow, the emulator and the server greet each other in plain words. This greeting is the **handshake**:

```
  emulator:  HELLO TRIADA-NET/1 model=TRIADA-85 cols=27 rows=18 client=1.1
  server:    WELCOME TRIADA-NET/1 1985-11-09
  emulator:  DIAL 1-11-11
  server:    CONNECT 1200
  ... from here on, every byte is a character on the screen ...
```

The server tells the date inside the network's world: today's date minus 41 years. The emulator says how big its screen is, so a future model with a wider screen can get wider pages. Hanging up is simply closing the connection.

**Three characters per word.** Programs travel as words, and a word has 3^12 = 531,441 possible values. 81 × 81 × 81 is also 531,441! So every word fits exactly into three "digits" of base 81, and each digit is sent as one of 81 printable characters, `!` (33) up to `q` (113). The word 1000 becomes `IUe`:

```
  1000 + 265,720 = 266,720             (shift the word so it is never negative)
  266,720 = 40 × 6,561 + 52 × 81 + 68  (three base-81 digits: 40, 52, 68)
  33 + 40 = 73 "I",  33 + 52 = 85 "U",  33 + 68 = 101 "e"
```

**Checking for damage.** TRIMODEM sends a program in blocks of 81 words (243 characters). Each block carries a **checksum**: the block number plus 1 × the first word + 2 × the second + … + 81 × the last. The receiver works the same sum out again. If it matches, it answers ACK ("got it"); if not, NAK ("send it again"). After three failures in a row, both sides give up. Weighting each word by its place means two swapped words change the sum, which a plain sum would not notice.

## How it works — the programmer's version

Read in this order.

1. **The device.** [`ModemDevice.java`](../../triada-core/src/main/java/org/triada/core/modem/ModemDevice.java):
   - `ModemDevice.read` and `ModemDevice.write` are the registers. Writing MDM_CMD calls `dial()` or `hangUp()`. Writing MDM_TX puts the value MOD 256 into the send ring, only while connected.
   - `ModemDevice.endFrame` is the heart of it. While dialing, it counts down the pause (`ModemDevice.dialPause`), then takes the port's answer. While connected, it moves up to `charsPerFrame` characters each way, and notices a hang-up from the other side once the line has nothing left to read.
   - The buffers are plain `int[]` rings (`rx`, `tx` with a head and a count), so a frame allocates nothing.
   - `ModemDevice.peek` lets the debugger see the next character without taking it.

   ```java
   boolean lineEmpty = false;
   for (int i = 0; i < budget; i++) {
       int c = port.read();
       if (c < 0) { lineEmpty = true; break; }
       if (rxCount < BUFFER_SIZE) { rx[(rxHead + rxCount) % BUFFER_SIZE] = c; rxCount++; }
       else { dropped++; }
   }
   if (lineEmpty && port.status() != CONNECTED) { status = NO_CARRIER; txCount = 0; }
   ```
   *Take at most `budget` (2) characters from the line. A character that finds the ring full is counted and dropped. Only when the line has run dry and the port says the call is over does the status become −1, so the other side's last words are never lost.*
2. **Where the modem sits.** [`Machine.java`](../../triada-core/src/main/java/org/triada/core/Machine.java) builds it, plugs it into registers 28–35 if `profile.has(Device.MODEM)`, and calls `ModemDevice.endFrame()` in `finishFrame`, after video and sound. `Machine.modem()` gives the host access. The speed comes from `MachineProfile.modemCharsPerFrame()` ([`MachineProfile.java`](../../triada-core/src/main/java/org/triada/core/MachineProfile.java)).
3. **The line.** [`ModemPort.java`](../../triada-core/src/main/java/org/triada/core/modem/ModemPort.java) is what the device needs: `dial`, `status`, `read`, `write`, `hangUp`. `ModemPort.OFFLINE` keeps no state, so every machine can share it.
4. **The protocol.** [`WireModemPort.java`](../../triada-core/src/main/java/org/triada/core/modem/WireModemPort.java): `WireModemPort.dial` opens a new [`ModemLink`](../../triada-core/src/main/java/org/triada/core/modem/ModemLink.java) and sends HELLO. `WireModemPort.status` reads the server's control lines (`readControlLines`, `handle`) until CONNECT or a failure, and stops reading right after CONNECT's line feed, so the following bytes stay raw. `WireModemPort.welcomeDate` keeps the in-world date.
5. **The pipes.**
   - [`LoopbackLink.java`](../../triada-core/src/main/java/org/triada/core/modem/LoopbackLink.java) → `LoopbackLink.pair()`: two ends. An end with a `LoopbackLink.Listener` gets bytes handed over at once, so a server answers within the call.
   - [`TcpModemLink.java`](../../triada-cli/src/main/java/org/triada/cli/TcpModemLink.java) connects and reads on a virtual thread, queues what arrives, and keeps what is sent before the connection opens. `TcpModemLink.opener` parses `host:port`.
   - [`WebModemLink.java`](../../triada-web/src/main/java/org/triada/web/WebModemLink.java) calls the page's `triadaModem` object ([`index.html`](../../triada-web/src/main/webapp/index.html)) through [`WebPage.java`](../../triada-web/src/main/java/org/triada/web/WebPage.java) (`WebPage.modemOpen`, `WebPage.modemRead`, …). The address comes from `WebPage.netAddress`; [`WebApp.java`](../../triada-web/src/main/java/org/triada/web/WebApp.java) plugs it in only if one is set.
6. **The server side.** In `triada-net-core`:
   - [`ServerConnection.java`](../../triada-net-core/src/main/java/org/triada/net/ServerConnection.java) is the mirror of `WireModemPort`. `ServerConnection.receive` collects lines; `hello` reads the [`Caller`](../../triada-net-core/src/main/java/org/triada/net/Caller.java); `dial` asks the [`Exchange`](../../triada-net-core/src/main/java/org/triada/net/Exchange.java) for an [`Answer`](../../triada-net-core/src/main/java/org/triada/net/Answer.java). After CONNECT, every byte goes to the [`Call`](../../triada-net-core/src/main/java/org/triada/net/Call.java), which answers through a [`Wire`](../../triada-net-core/src/main/java/org/triada/net/Wire.java).
   - [`InWorld.java`](../../triada-net-core/src/main/java/org/triada/net/InWorld.java) → `InWorld.date` subtracts 41 years.
   - [`EchoExchange.java`](../../triada-net-core/src/main/java/org/triada/net/EchoExchange.java) is the test board.
   - [`LoopbackNetwork.java`](../../triada-net-core/src/main/java/org/triada/net/LoopbackNetwork.java) wires a `LoopbackLink` to a `ServerConnection`.
7. **The real server.** [`NetServer.java`](../../triada-net-server/src/main/java/org/triada/net/server/NetServer.java):
   - `NetServer.acceptLoop` gives every TCP caller a virtual thread (`serve`).
   - `NetServer.wsConnect` checks the WebSocket's Origin and keeps one `ServerConnection` per session.
   - `NetServer.main` starts the echo server.
8. **TRIMODEM.**
   - [`Trimodem.java`](../../triada-net-core/src/main/java/org/triada/net/Trimodem.java) has `Trimodem.encode`, `Trimodem.decode` and `Trimodem.checksum`.
   - [`TrimodemSender.java`](../../triada-net-core/src/main/java/org/triada/net/TrimodemSender.java) and [`TrimodemReceiver.java`](../../triada-net-core/src/main/java/org/triada/net/TrimodemReceiver.java) are state machines with no line of their own: you hand them bytes and they hand back what to send (`TrimodemSender.start`, `TrimodemSender.receive`, `TrimodemSender.timeout`, `TrimodemReceiver.receive`, `TrimodemReceiver.file`).
   - The receiver reads the start message by position (`headerChar`), because `;` can be a digit.
9. **TRIBASIC.** In [`rom/basic.tasm`](../../rom/basic.tasm): `st_mdial` (the number through `b_fname` into FNAME, then MDM_ADDR and MDM_CMD), `st_mhangup`, `st_msend` (waits with `WAIT` while MDM_TXFREE is 0), `fn_mstatus`, `fn_mrecv` (into a string temp). Every token from −51 down is looked up in `fntab`, so `fntab` gets four `err_syntax` entries for the statements −69 … −72.
10. **The debugger.** [`Debugger.java`](../../triada-desktop/src/main/java/org/triada/desktop/debug/Debugger.java) → `Debugger.registers` adds a line `MODEM status RX n DROPPED n`.

## Build it yourself from scratch

1. **The device first, with a fake line.** Write the registers and two ring buffers of 729. Give the device an interface to the line (dial, status, read, write, hang up) and test it with a scripted fake: every status, two characters per frame, overflow, both kinds of hang-up. Keep the transfer in an end-of-frame method, never in the register writes, or the speed limit is lost.
2. **Decide what "the other side hung up" means** before you write it. If you report it as soon as the connection closes, the last characters the other side sent are lost. Report it when the line is closed *and* empty.
3. **The protocol on a pipe.** Define a byte-pipe interface. Write the client protocol on top of it, then test it against a scripted server on an in-process pipe: normal call, every refusal, a server that says nothing, one that hangs up, one that never ends its line.
4. **The server side as a mirror**, again without sockets: it gets bytes and answers through an interface. Test the two against each other in one program.
5. **Only now the network.** TCP: one thread reads the socket into a thread-safe queue; writes made before the connection opens are kept and sent after. WebSocket: let the browser do the work and only queue bytes.
6. **TRIMODEM.** Encode by shifting the word to 0 … 531,440 and taking three base-81 digits. Test all 531,441 words. Then write the sender and receiver as state machines that take bytes and return bytes, and run them against each other in a loop, damaging messages on purpose.
7. **TRIBASIC.** Append the keywords to the token table (never renumber). Check how the interpreter tells functions from statements before choosing the numbers.
8. **What tends to go wrong:**
   - reading control lines past CONNECT (eating the first raw bytes);
   - splitting a TRIMODEM header at `;`;
   - a WebSocket that refuses to open and leaves the program waiting forever: mark the line closed on every error;
   - the debugger's memory view reading MDM_RX and so stealing a character.

## How we know it works

Run everything:

```bash
./gradlew build
```

All 763 tests pass: the 710 from before (one of them, the register-name test, extended by the eight new names) and 53 new ones.

The new ones:
- `ModemDeviceTest` (16 tests): every status, the dialing pause, broken numbers, 2 characters per frame, MOD 256, the full send buffer, overflow and the drop count, hang-up from either side, power-on, peeking.
- `WireModemPortTest` (9): the handshake, raw bytes after CONNECT, every answer, offline cases, a slow server, a remote hang-up, and a machine dialing through its registers.
- `TrimodemTest` (11): all 531,441 words, the worked examples, the checksum, whole transfers (empty file, an address containing `;`), damaged blocks sent again, the third failure, timeouts, lost ACKs, broken starts, noise.
- `ServerConnectionTest` (5): the server side of the protocol, refusals, screen sizes, the in-world date, and a TRIADA-85 calling the echo board in process.
- `NetServerTest` (4): real TCP and WebSocket calls on this computer (binary and text messages), the origin check, `/health`.
- `TcpModemTest` (3): a machine calling the server over real TCP, nobody listening (offline), bad addresses.
- The TRIBASIC suite gained `modem_echo`, `modem_status` and `modem_syntax` (its machine is connected to the echo exchange).
- `BasicExamplesTest` plays `AUTOCALL.bas` online and offline.
- `DebuggerTest` checks the modem line.

**By hand, over real TCP.** Start the echo server in one terminal (since M18 the server starts the boards by default, so the echo exchange needs `--echo`):

```bash
./gradlew :triada-net-server:run --args="--echo"
```

It prints `TRIADA-NET echo server: tcp 2385, websocket ws://localhost:2386/net (dial 1-11-11)`. Then, in another terminal, run AUTOCALL with a `RUN` line added at the end:

```bash
triada run --type autocall-run.bas --modem localhost:2385 --frames 900 --dump-screen autocall.png
```

The picture shows:

```
RUN
DIALING 1-11-11
CONNECT 1200
ECHO READY
HELLO FROM TRIADA
HUNG UP.
READY.
```

**The web page's WebSocket code** was driven from the browser against the same local server (`?net=ws://localhost:2386/net`). It received `WELCOME TRIADA-NET/1 1985-11-09`, `CONNECT 1200`, the echo and `BYE`, and then reported the line closed. An address where nobody listens was reported closed too. The web build compiles with the modem (`./gradlew :triada-web:gdx_teavm_web_js_build`). Without a server set, the deployed page behaves exactly as before: every call ends −4.

## Problems we hit and how we solved them

- **`;` is a digit.** The first TRIMODEM receiver split the start message at `;`. But character 59 is one of the 81 digit characters, so an address like `!;!` broke it. A test with exactly such an address found it. The fields are now read by position.
- **The test expected `PPP` for the word 0.** 0 + 265,720 has the digits 40, 40, 40, which is `III` (33 + 40 = 73). The code was right and the test's arithmetic wrong; the worked examples are now computed, not guessed.
- **A test raced its own server.** In one protocol test, the scripted server sent a character before the client had read CONNECT. The client then read it as part of a control line. In a real call the server never speaks before CONNECT, so the test now waits for CONNECT first.
- **Javalin 7 changed its API.** A binary message's data is a `ByteBuffer`, without the `offset()` and `length()` of older versions. The compiler said so; the code copies the buffer.
- **Shell quoting.** A few test files written through the shell lost their `\n` escapes; they were rewritten with the editor.
- **The browser pane was hidden**, so the web page drew no frames. The page's JavaScript was tested directly, but a call made from TRIBASIC inside the running web machine could not be watched. That path uses the same `WireModemPort` as the tested TCP one.

## Limitations and what comes next

- **Only the echo board.** The server answers `1-11-11` (echo), `2-22-22` (busy) and `3-33-33` (no carrier). The real boards, users, messages and files are M18.
- **No terminal yet.** `DIAL` has its token but says `?SYNTAX ERROR` until `TERM.TRI` exists (M19). Until then, programs use `MDIAL` and friends, like AUTOCALL.
- **TRIMODEM is Java only.** The server and tests have it; the machine's own version, in assembly inside TERM.TRI, is M19.
- **The web version has no server address set** (`TRIADA_NET_URL` is empty), so on the deployed page every call ends "offline". M19 makes the address a build setting and adds the "TRIADA-NET IS OFFLINE" message. The server must be served as `wss://` for the HTTPS page.
- **No turbo switch in the frontends yet**; `ModemDevice.setTurbo` exists for the terminal and the tests.
- **The headless runner doesn't wait for the network.** It runs frames as fast as it can, so a call over TCP may still be dialing after the pause; programs should wait on `MSTATUS`, as AUTOCALL does.
- **The server has no limits yet** (rate limits, sizes, bans); those are M18's security work. Don't run this echo server on a public address.

## New words

- **Modem**: the box that lets a computer talk over a telephone line; here the device on registers 28–35.
- **Bit rate**: bits per second on a line; 1200 bit/s is 2 characters per frame.
- **Protocol** and **wire protocol**: the agreed rules of a conversation between two programs, byte by byte.
- **Handshake**: the greeting before the conversation (HELLO … CONNECT).
- **Client and server**: the one who calls, and the one who answers many callers.
- **TCP**: the internet's reliable two-way byte stream.
- **WebSocket**: a two-way connection a web page keeps open to a server; `wss://` is the encrypted kind.
- **Port (network)**: the number that picks one program on a computer (2385, 2386).
- **Origin (web)**: the address of the site a page comes from.
- **Loopback**: a connection that stays inside the same program.
- **Exchange (telephone)**: what decides who answers a number.
- **Virtual thread**: a very light Java thread, one per caller.
- **Bulletin board system (BBS)**: a computer you call to read and leave messages.
- **TRIMODEM**: TRIADA-NET's way of sending programs, three characters per word.
- **Checksum**: a number that reveals damage to a block.
- **ACK and NAK**: "got it" and "send it again".
