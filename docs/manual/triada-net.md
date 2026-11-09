# 9. Calling the World: Your TRIADA-85 Modem

```
   TRIADA-85  ──  MODEM  ──  telephone line  ──  TRIADA-NET
     (you)       1200 bit/s                       boards, mail, programs
```

## Your computer has a voice

Inside your TRIADA-85 is a **modem**, a small device that lets the computer speak through the telephone line. With it, your computer can call other computers. Some of them are **boards**: computers that answer the telephone day and night, so that anyone may leave a message, read the news, write to a friend or take home a program.

All the boards together are called **TRIADA-NET**. Every board has a telephone number of the form `D-DD-DD`, for example `3-43-21`. The directory of public numbers is always at **0-00-00**.

Calling a board costs nothing but your telephone bill. Please agree on it with the people you live with.

## Your first call

Make sure the modem cable is in the telephone socket, switch on, and type:

```
DIAL "0-00-00"
```

The computer loads the terminal program **TERM** from your triskette (every triskette from the works carries `TERM.TRI`) and dials. Listen: first the **dial tone**, then a pair of tones for **each digit**, then the **handshake**, a short screech while the two modems agree on their speed. Then:

```
CONNECT 1200
```

You are connected. Everything the board sends appears on your screen, and everything you type goes to the board.

> **Patience, please.** The line carries 1200 bits each second: two characters every fiftieth of a second, about 120 a second. A full screen takes four seconds to arrive. In 1985 this is fast.

If the call does not succeed, TERM tells you why:

| You see | It means |
|---|---|
| `BUSY` | Someone else is using the board's last line. Try again later. |
| `NO SUCH NUMBER` | There is no board at that number. Check the directory at 0-00-00. |
| `NO CARRIER` | The line was answered by nobody, or was lost. |
| `NO CARRIER` and `TRIADA-NET IS OFFLINE` | Your computer could not reach the network at all. |

## Logging in

Each board first shows its welcome screen and asks for your **handle**, the name you use on TRIADA-NET:

```
HANDLE (GUEST, NEW):
```

- Type **GUEST** to look around. Guests may read messages and take programs home.
- Type **NEW** to join. The board shows its **SYSOP RULES** (be kind; no real names, addresses or telephone numbers; nothing illegal). Then choose a handle (2 to 12 letters and digits) and a password, which you type twice. Your handle then works on every board of TRIADA-NET.

The **sysop** is the person who runs a board. Every board has one, and the sysop may delete messages and close the door to those who misbehave.

## The menu

```
M MESSAGES   E MAIL
F FILES      O ONE-LINERS
L LAST CALLS W WHO IS ON
T TIME LEFT  G GOODBYE
```

Press one key; there is no need for Return.

- **M**: the board's messages, newest first. `L` lists them, `R` reads one by its number, `P` posts a new one. While reading: `N` the next older message, `A` answer it, `R` report it to the sysop. You end your text with a line holding only a full stop (`.`), then `S` saves.
- **E**: your private mail.
- **F**: programs to take home (see below).
- **O**: the one-liner wall. Leave a line for the next caller.
- **L** and **W**: who called before you, and who is on TRIADA-NET now.
- **T**: your time. Each board allows each caller 30 minutes a day.
- **G**: goodbye. The board hangs up politely.

## Taking a program home

In the files menu, press **D** and type the program's name, for example `TROIKA.TRI`. The board sends it, and TERM receives it with **TRIMODEM**:

```
RECEIVING TROIKA.TRI
##########
SAVED TROIKA.TRI
```

The program is now on your triskette. Hang up (**Esc**, then **H**). TERM returns you to TRIBASIC, and:

- a **.TRI** file is a machine-code program: `BRUN "TROIKA"`;
- a **.TB** file is a TRIBASIC program: `LOAD "ORACLE"`, then `RUN`.

### Why three characters?

TRIMODEM sends each word of a program as exactly three characters. This is not a coincidence. A word has 3¹² = 531,441 possible values, and 81 × 81 × 81 is also 531,441. So three "digits" of 81 kinds hold one word exactly, and each digit becomes one of 81 printable characters, from `!` to `q`. The word 1000 travels as `IUe`. With every 81 words comes a checksum. If a line crackles and a character arrives wrong, the receiver asks for that block again.

## Sending a program

To share your own program, choose **U** in a board's files menu. When the board says `SEND YOUR FILE BY TRIMODEM NOW.`, press **Esc**, then **U**, and type the name of a file on your triskette, for example `MINE.TB`. After the transfer the board asks for a short description. Your program waits for the sysop's approval before others can see it.

## The phonebook

`BRUN "TERM"` without a number shows TERM's **phonebook**. Press **1** to **9** to dial an entry, **N** to type a new number (it is added to the phonebook, which TERM keeps on your triskette as `PHONE.DAT`), or **Esc** to return to TRIBASIC.

```
TRIADA TERM  PHONEBOOK

1 0-00-00 TRIADA-NET INFO
2 1-85-01 TRIADA SUPPORT
3 2-27-81 TRIT AND TRYTE
4 3-43-21 PIONEER PALACE

N NEW NUMBER   ESC QUIT
```

## While connected

| Keys | |
|---|---|
| **Esc**, then **H** | Hang up and return to TRIBASIC. |
| **Esc**, then **U** | Send a file from your triskette. |
| Return | Ends a line, as always. |

Boards may change the colour of your text while you are connected. TERM puts your own colours back when you hang up.

## Writing your own terminal

For those who like to know how things work, TRIBASIC has five words for the modem:

| | |
|---|---|
| `MDIAL "number"` | Dial, without TERM. Your program continues at once. |
| `MSTATUS` | 1 while dialing, 2 when connected, 0 before any call; −1 no carrier, −2 busy, −3 no such number, −4 no network. |
| `MSEND text$` | Send text. |
| `MRECV$` | Everything received so far, up to 80 characters. |
| `MHANGUP` | Hang up. |

```
10 MDIAL "0-00-00"
20 WAIT 1: IF MSTATUS = 1 THEN GOTO 20
30 IF MSTATUS <> 2 THEN PRINT "NO LINE": END
40 WAIT 120
50 PRINT MRECV$
60 MHANGUP
```

The program `AUTOCALL` from the magazine is a complete example.

## Good manners on TRIADA-NET

- Be kind. There is a person behind every handle.
- Never give your real name, address or telephone number.
- One board, one line: if you hear BUSY, someone else is reading. Wait your turn.
- Do not stay longer than you need. Others are waiting for the line.

---

### About this chapter (a note from the present day)

TRIADA-NET is real. The modem in the emulator really calls a TRIADA-NET server, over the internet from the desktop and the command line, and over a WebSocket from the web version. The server is in this repository (`triada-net-server`), and anyone can run one ([docs/net/DEPLOY.md](../net/DEPLOY.md)). To try it without any network, start the desktop with `--local-node`: the boards then run inside the emulator itself. The machine's side is specified in [SPEC.md](../SPEC.md) §5.2, §10.6.5 and §11, and the network's in [NET-SPEC.md](../NET-SPEC.md).
