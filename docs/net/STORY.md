# The Fourth State — the hidden story of TRIADA-NET

> **Spoilers.** This page gives away the whole trail. If you want to find it yourself, stop here and start dialling.

The outline was approved by the human on 2026-11-09, with two choices: **the trail is complete from the start** (no posts appear later), and **there is no definite answer**. The number of the hidden board was left to us: **4-04-04**, "the fourth". The story is now written into the starter boards (D-076).

## The premise

Everyone in the TRIADA world knows that a trit has three states: −1, 0 and +1. In autumn 1985 a few callers notice something that shouldn't exist: a **fourth state**, a value a trit takes for a moment while it switches, seen between 0 and +. Engineers at NPO "Triada" recorded it on their test bench and were told to ignore it.

The story never says whether the fourth state is real. It lets the players decide what they found.

## Tone

- Fictional, non-political, gentle. No villains and no secret police: the obstacles are bureaucracy, rounding errors and people who don't want to look foolish.
- The same voice as the starter boards: factory notices, magazine letters, students, night owls.
- Solved with the machine itself: a download, `BRUN`, and one line of TRIBASIC. A recurring time, **23:13**, ties the sightings together.

## The trail

| Step | Where | What the caller finds |
|---|---|---|
| 1 | *Trit & Tryte* (2-27-81), READERS' LETTERS | Irina sees a small ring among the -, 0 and + of her TERNCLOCK. The editors call it a typing error. Matvei saw it too, once, at 23:13. |
| 2 | NPO "Triada" Support (1-85-01), NEWS and PROGRAMS | A "stability correction" for machines from **test bench 4**, and the program `PATCH85.TRI`. Run with `BRUN`, it changes nothing and prints a checksum: `0T+-00+++-++0`. |
| 3 | Pioneer Palace Club (3-43-21), CLUB TALK | Lena and Kostya time a trit switching, and once get a reading between 0 and +, at 23:13. Forty repeats show nothing. |
| 4 | The Quiet Line (9-13-13), NIGHT THOUGHTS | NIGHTWATCH's riddle, "THREE DOORS": an IF with THEN, MAYBE and ELSE, and a fourth door whose number is hidden "in a sum. A sum is sometimes a number you can call." |
| 5 | **THE BENCH (4-04-04)**, unlisted | `PRINT 0T+-00+++-++0` gives 40404: dial 4-04-04. The logbook of test bench 4: dry entries, always at 23:13, by ENGINEER4. The last one asks each caller to leave their answer on the wall. |

## The ending

THE BENCH's one-liner wall (`O` in its menu) is the real logbook. Every caller who finds the board leaves one line: their answer to what the fourth state is. A fault, a dream, a door, or something else. Every caller sees the answers of those before. The "solution" is the community of people who found the board, each with their own reading. The story never confirms or denies anything.

## Where it lives

- The posts: `POST` blocks in [`npo-support.board`](../../triada-net-core/src/main/resources/net/boards/npo-support.board), [`trit-and-tryte.board`](../../triada-net-core/src/main/resources/net/boards/trit-and-tryte.board), [`pioneer-palace.board`](../../triada-net-core/src/main/resources/net/boards/pioneer-palace.board) and [`quiet-line.board`](../../triada-net-core/src/main/resources/net/boards/quiet-line.board), each marked with a "The Fourth State, step N" comment.
- The hidden board: [`the-bench.board`](../../triada-net-core/src/main/resources/net/boards/the-bench.board) (`UNLISTED`).
- The correction: [`programs/patch85.tasm`](../../programs/patch85.tasm), offered on 1-85-01 as `PATCH85.TRI`.
- The test that follows the whole trail on a headless TRIADA: `TermTest.theFourthStateCanBeFound` in `triada-rom`.

Nothing in the story uses real people's names, real places of work or real events.
