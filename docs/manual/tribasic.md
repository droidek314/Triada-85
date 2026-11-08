# Chapter 8 — TRIBASIC: Programming with Three Answers

When you switch on your TRIADA-85, it greets you with **TRIBASIC**, its own programming language. TRIBASIC understands plain English words like PRINT, IF and GOTO, and it does something no other home computer's language does: besides *yes* and *no*, it knows *maybe*. This chapter shows you every word of TRIBASIC, each with a small example to type in.

The examples look exactly like your screen. Type the lines that start with a number, then the command below them (usually `RUN`), and press ENTER after each line. What follows is what your TRIADA-85 answers.

## 8.1 Your first command

Type this and press ENTER:

```tribasic
PRINT 6 * 7
42
READY.
```

A line without a number in front is done at once. TRIBASIC answers `READY.` when it is ready for the next one.

TRIBASIC counts in whole numbers from −265,720 to +265,720. Division rounds to the **nearest** whole number, and halves go toward zero, so `7 / 2` is 3. `MOD` gives what is left over, and may be negative: `8 MOD 3` is −1, because 8 = 3 × 3 − 1. This is how a ternary computer divides, and it is fair: a remainder is never more than half the divisor, in either direction. A comma in PRINT moves to the next column of nine.

```tribasic
PRINT 7 / 2, 8 / 3, 8 MOD 3
3        3        -1
READY.
```

Numbers may also be written in trits, starting with `0T`. `TERN$` turns a number into trits:

```tribasic
PRINT 0T+-0, TERN$(42)
6        +---0
READY.
```

## 8.2 Programs

A line **with** a number is kept as part of your program. The numbers decide the order, so number your lines 10, 20, 30, which leaves room to add lines in between later. `RUN` starts the program.

```tribasic
10 FOR I = 1 TO 5
20 PRINT I; " SQUARED IS "; I * I
30 NEXT
RUN
1 SQUARED IS 1
2 SQUARED IS 4
3 SQUARED IS 9
4 SQUARED IS 16
5 SQUARED IS 25
READY.
```

`LIST` shows your program. You may type in small letters; TRIBASIC keeps keywords and names in capitals, and text in "quotes" exactly as you typed it.

```tribasic
10 print "hello"
20 goto 10
LIST
10 PRINT "hello"
20 GOTO 10
READY.
```

This program would print "hello" for ever. Press **ESC** to stop it: TRIBASIC says `BREAK IN 10`.

- To change a line, type it again with the same number.
- To remove a line, type its number alone.
- `LIST 20-30`, `LIST -30` and `LIST 30-` show part of the program.
- `NEW` erases the whole program.
- `RUN 20` starts at line 20.

Put several statements on one line with a colon: `10 A = 1: PRINT A`. Keywords must stand apart from names and numbers: write `IF X = 0 THEN`, not `IFX=0THEN`. A line may be 80 letters long.

## 8.3 Variables and strings

A **variable** is a named box for a number: `LET A = 5`, or simply `A = 5`. A name is a letter and up to 7 more letters or digits. A name ending in `$` holds **text** (a "string") of up to 80 letters.

```tribasic
10 A$ = "TRI"
20 B$ = A$ + "ADA"
30 PRINT B$, LEN(B$), LEFT$(B$, 3)
RUN
TRIADA   6        TRI
READY.
```

The string functions are:

| Function | Gives |
|---|---|
| `LEN(s$)` | the number of letters |
| `LEFT$(s$, n)`, `RIGHT$(s$, n)` | the first or last n letters |
| `MID$(s$, start, n)` | n letters from position start (the first letter is 1) |
| `CHR$(n)`, `ASC(s$)` | the letter with code n; the code of the first letter |
| `STR$(n)`, `VAL(s$)` | a number as text; text as a number |
| `TERN$(n)` | a number in trits |

```tribasic
10 S$ = "BALANCED"
20 PRINT MID$(S$, 2, 4); " "; RIGHT$(S$, 3)
30 PRINT ASC("A"); " "; CHR$(66); " "; VAL("0T++")
RUN
ALAN CED
65 B 4
READY.
```

## 8.4 Three answers

Every question TRIBASIC asks itself has one of **three** answers:

| Answer | Value |
|---|---|
| `TRUE` | +1 |
| `UNKNOWN` | 0 |
| `FALSE` | −1 |

Comparisons (`=`, `<>`, `<`, `>`, `<=`, `>=`) answer TRUE or FALSE. `AND`, `OR` and `NOT` work with all three answers. "Yes AND maybe" is maybe; "yes OR maybe" is yes; "NOT maybe" is still maybe:

```tribasic
10 PRINT FALSE AND UNKNOWN,
20 PRINT TRUE OR UNKNOWN, NOT FALSE
RUN
-1       1        1
READY.
```

| AND | FALSE | UNKNOWN | TRUE |
|---|---|---|---|
| **FALSE** | FALSE | FALSE | FALSE |
| **UNKNOWN** | FALSE | UNKNOWN | UNKNOWN |
| **TRUE** | FALSE | UNKNOWN | TRUE |

| OR | FALSE | UNKNOWN | TRUE |
|---|---|---|---|
| **FALSE** | FALSE | UNKNOWN | TRUE |
| **UNKNOWN** | UNKNOWN | UNKNOWN | TRUE |
| **TRUE** | TRUE | TRUE | TRUE |

The special comparison `<=>` answers −1, 0 or +1: "smaller", "the same" or "bigger". It works for text too, in alphabetical order.

```tribasic
10 PRINT 3 <=> 5, 5 <=> 5, "B" <=> "A"
RUN
-1       0        1
READY.
```

**Remember:** a variable you have never set is 0, so it is UNKNOWN. Your TRIADA-85 never pretends to know what it doesn't.

## 8.5 IF … THEN … MAYBE … ELSE

`IF` looks at a number. If it is positive, the statements after `THEN` run; if it is zero, those after `MAYBE`; if it is negative, those after `ELSE`. `MAYBE` and `ELSE` may be left out, and may come in either order.

```tribasic
10 FOR T = -1 TO 1
20 IF T THEN PRINT "YES" MAYBE PRINT "MAYBE" ELSE PRINT "NO"
30 NEXT
RUN
NO
MAYBE
YES
READY.
```

A line number alone means "go there": `IF C THEN 100 MAYBE 200 ELSE 300`.

`ASK` puts a question to you and waits for **Y**, **N** or **?**, which give TRUE, FALSE and UNKNOWN:

```
10 ASK "IS IT RAINING"; R
20 IF R THEN PRINT "TAKE AN UMBRELLA" MAYBE PRINT "TAKE IT, JUST IN CASE"
30 IF R < 0 THEN PRINT "ENJOY THE SUN"
RUN
IS IT RAINING (Y/N/?) ?
TAKE IT, JUST IN CASE
READY.
```

`INPUT` waits for a number or a line of text, and ENTER: `INPUT "YOUR AGE"; A` or `INPUT N$`. If you type something that isn't a number where one is needed, it says `?REDO` and asks again.

## 8.6 Loops and jumps

| Statement | What it does |
|---|---|
| `FOR v = a TO b STEP s` … `NEXT v` | counts v from a to b in steps of s (STEP 1 if left out); the lines in between run each time, and always at least once |
| `GOTO n` | goes on at line n |
| `GOSUB n` … `RETURN` | goes to line n and comes back when it meets RETURN |
| `END` | ends the program |
| `STOP` | stops it with `BREAK IN line` |
| `REM` | a remark: the rest of the line is for you, not the computer |

```tribasic
10 GOSUB 100: GOSUB 100
20 END
100 PRINT "HELLO FROM 100"
110 RETURN
RUN
HELLO FROM 100
HELLO FROM 100
READY.
```

## 8.7 Arrays

An **array** is a row of numbered boxes with one name. `DIM A(10)` makes boxes 0 to 10; `DIM A$(5)` makes six strings. Ternary-minded programmers like `DIM T(-5 TO 5)`, a row with box 0 in the middle. An array you use without DIM gets the boxes 0 to 10.

```tribasic
10 DIM T(-1 TO 1)
20 T(-1) = 7: T(1) = 9
30 PRINT T(-1); T(0); T(1)
RUN
709
READY.
```

## 8.8 Pictures, sound and the machine

These statements reach the TRIADA-85's picture and sound chips directly (Chapter 5 explains tiles, sprites and colours):

| Statement | What it does |
|---|---|
| `CLS` | clears the screen |
| `LOCATE col, row` | moves the cursor (columns 0–26, rows 0–17) |
| `TILE col, row, t` | puts tile t in one square of the screen (−t: mirrored) |
| `DEFTILE t, row, p0, …, p7` | sets one row of tile t to 8 colours (−13 … 13) |
| `SPRITE i, x, y, t [, flags]` | shows sprite i (0–26) with tile t at x, y |
| `COLOR fg, bg` | the colours of the letters and their background |
| `BORDER c` | the colour of the border |
| `SCROLL x, y` | moves the whole background |
| `SOUND v, freq, vol, wave` | voice v (1–3): frequency in Hz, volume 0–13, wave −1 noise, 0 triangle, +1 ternary square |
| `WAIT n` | waits n frames (60 in a second) |
| `POKE addr, value`, `PEEK(addr)` | writes or reads one word of memory |
| `SYS addr` | calls a machine-code program (Chapter 4) |

And these functions read the machine:

| Function | Gives |
|---|---|
| `JOYX`, `JOYY` | the joystick: −1, 0 or +1 |
| `BTN(n)` | fire button 1, 2 or 3 (A, B, C): 1 while pressed |
| `INKEY` | the code of the key pressed last, or 0 |
| `FRAME` | the frame counter |
| `RND(n)` | a random number from 1 to n |
| `ABS(n)`, `SGN(n)` | n without its sign; the sign of n (−1, 0, +1) |

Try this on a fresh screen: a red square that walks across, with a beep.

```
10 CLS: FOR R = 0 TO 7: DEFTILE 1, R, 5, 5, 5, 5, 5, 5, 5, 5: NEXT
20 FOR X = 0 TO 26: TILE X, 8, 1: SOUND 1, 220 + 20 * X, 9, 1: WAIT 4
30 TILE X, 8, 32: NEXT: SOUND 1, 0, 0, 0
```

## 8.9 The triskette

Programs and data are kept on the **triskette**:

| Command | What it does |
|---|---|
| `SAVE "NAME"` | saves your program as NAME.TB |
| `LOAD "NAME"` | loads NAME.TB in place of your program |
| `FILES` | lists everything on the triskette |
| `ERASE "NAME.TB"` | removes a file |
| `BSAVE "NAME", addr, len` | saves len words of memory as NAME.TRI |
| `BLOAD "NAME" [, addr]` | loads NAME.TRI to where it came from (or to addr) |
| `BRUN "NAME"` | loads NAME.TRI and starts it: this is how machine-code games are run |

```
SAVE "SQUARES"
READY.
FILES
SQUARES.TB
READY.
```

A name has up to 12 letters and digits with one dot: `SQUARES.TB`, `TROIKA.TRI`. A game that loads where your program lives replaces it; when it ends, TRIBASIC starts afresh with `READY.`.

## 8.10 When something goes wrong

TRIBASIC tells you what it didn't like, and in which line:

```tribasic
10 PRINT 1 / 0
RUN
?DIVISION BY ZERO ERROR IN
10
READY.
```

| Message | Meaning |
|---|---|
| `?SYNTAX ERROR` | a word or sign TRIBASIC doesn't understand there |
| `?UNDEFINED LINE ERROR` | GOTO or GOSUB to a line that doesn't exist |
| `?RETURN WITHOUT GOSUB ERROR` | RETURN, but no GOSUB to come back to |
| `?NEXT WITHOUT FOR ERROR` | NEXT, but no FOR loop |
| `?OUT OF MEMORY ERROR` | too many variables, or GOSUBs inside GOSUBs |
| `?DIVISION BY ZERO ERROR` | `/ 0` or `MOD 0` |
| `?OVERFLOW ERROR` | a number beyond ±265,720 |
| `?TYPE MISMATCH ERROR` | text where a number belongs, or the other way round |
| `?BAD SUBSCRIPT ERROR` | an array box that doesn't exist |
| `?STRING TOO LONG ERROR` | text longer than 80 letters |
| `?FILE NOT FOUND ERROR` | no such file on the triskette |
| `?DISK ERROR` | the triskette refused: a bad name, or it is full |

One thing TRIBASIC does *not* report: multiplication beyond ±265,720 wraps around, the way the processor's own MUL does.

## 8.11 From the magazine: Trit Tapestry

```
  +--------------------------------------------------------------+
  |  T R I T  &  T R Y T E      No. 4/1985      TYPE-IN CORNER   |
  |                                                              |
  |  TRIT TAPESTRY  ·  for the TRIADA-85 with TRIBASIC           |
  +--------------------------------------------------------------+
```

> Every square of the screen takes one of three colours, worked out from its place: (X·X + Y·Y) MOD 3 is always −1, 0 or +1. Then the whole carpet shifts its pattern, slowly, until you press a key. Twenty lines, ten minutes of typing.

```typein
10 REM TRIT TAPESTRY - TRIT & TRYTE 4/1985
20 REM EVERY CELL IS (X*X + Y*Y + K) MOD 3: -1, 0 OR +1
30 FOR R = 0 TO 7
40 DEFTILE 1, R, -11, -11, -11, -11, -11, -11, -11, -11
50 DEFTILE 2, R, -13, -13, -13, -13, -13, -13, -13, -13
60 DEFTILE 3, R, 5, 5, 5, 5, 5, 5, 5, 5
70 NEXT
80 CLS: K = 0
90 FOR Y = 0 TO 16
100 FOR X = 0 TO 26
110 TILE X, Y, (X * X + Y * Y + K) MOD 3 + 2
120 NEXT X
130 NEXT Y
140 LOCATE 0, 17: PRINT "TRIT TAPESTRY - ANY KEY";
150 WAIT 30: K = K + 1: IF INKEY THEN 200
160 GOTO 90
200 CLS: PRINT "THANK YOU FOR TYPING!"
```

Type `SAVE "TAPESTRY"` before you `RUN` it, and you will never have to type it again.

---
*TRIADA-85 Owner's Manual · Chapter 8*
