# The Fourth State — story outline

> **DRAFT — needs the human's approval.** This is only an outline. None of the story's content (the clues, the hidden board, its posts) has been written into TRIADA-NET, and none will be until the outline is approved or changed. The starter boards carry mood only. (NET-SPEC §6, CLAUDE.md M19.)

## The premise

Everyone in the TRIADA world knows that a trit has three states: −1, 0 and +1. Late in 1985, rumours start on TRIADA-NET that engineers at NPO "Triada" found a **fourth state**: a value that a trit takes for a moment while it switches, which the test benches kept recording and the engineers kept being told to ignore.

The story is a quiet mystery for curious callers. It never claims the fourth state is real. It lets the players decide what they found.

## Tone

- Fictional, non-political, gentle. No villains and no secret police: the obstacles are bureaucracy, rounding errors and people who don't want to look foolish.
- 1985 Eastern-Bloc-inspired atmosphere, in the same voice as the starter boards: factory notices, magazine letters, students, and night owls.
- The puzzle is solved with the machine itself: TRIBASIC, the monitor, ternary arithmetic. Nothing needs knowledge from outside the TRIADA world.

## The trail (five steps)

1. **A letter that doesn't add up** (*Trit & Tryte*, LETTERS). A reader asks why her copy of the TERNCLOCK listing sometimes shows a digit the font doesn't have. The editors answer that it must be a typing error. A second reader says they saw it too.
2. **A patch notice** (NPO "Triada" Support, NEWS). An official notice about a "stability correction" for early machines. The patch, a downloadable `.TRI`, contains in its data a short table of numbers that is a phone number written in balanced ternary.
3. **The club's experiment** (Pioneer Palace, CLUB). Students time how long a trit takes to switch, using `FRAME` and `PEEK`, and post their results. One result is "impossible". The club leader tells them to repeat it. They can't, and they say so.
4. **The night board** (The Quiet Line). NIGHTWATCH posts, after midnight in-world time, a riddle in three lines, each a TRIBASIC IF with THEN, MAYBE and ELSE. Only the branch that can never be reached (the "fourth way out of an IF") makes sense of it.
5. **The unlisted number.** Decoding step 2 with the hint of step 4 gives an unlisted number, for example `4-04-04` (number to be chosen). It isn't in the 0-00-00 directory. Its board, **THE BENCH**, is the logbook of the original test bench: dry entries by a tired engineer, ending with a choice for the caller, and no single answer.

## What the ending is

At THE BENCH, the caller can leave a one-line entry in the logbook. That entry is their answer to the mystery. Every caller sees the last entries of others. The "solution" is the community of callers who found the board, each with their own reading. The story never confirms or denies the fourth state.

## What it needs technically (all with the M18/M19 machinery, nothing new)

- One more `.board` file (`UNLISTED`), seeded posts on the four starter boards, and one downloadable `.TRI` carrying the encoded number.
- Optional: posts that appear only after a date (the in-world date moves with real time), so the trail unfolds over a few weeks. That would need a small addition: a `FROM` date on seeded posts.
- Nothing in the story may use real people's names, real places of work, or real events.

## Questions for the human

1. Is the premise right, or would you prefer another (or none)?
2. Should the trail unfold over real weeks (dated posts), or be complete from the start?
3. Is the ending (a shared logbook, no single answer) what you want, or should there be a definite answer?
4. Any number you'd like for THE BENCH?
