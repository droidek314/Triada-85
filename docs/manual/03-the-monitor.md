# Chapter 3 — The monitor

## 3.1 What the monitor is

The **monitor** is the program built into your computer's permanent memory (the **ROM**). It starts when you switch on, shows `READY.` and waits for your commands at the `>` sign. With it you can look into the memory, change it, and start programs. Think of it as the computer's front desk.

Type a command, then press **ENTER**. Numbers may be ordinary (`123`, `-5`) or written in trits (`0t+-0`, see Chapter 2).

## 3.2 HELP

`HELP` lists every command:

```monitor
> HELP
COMMANDS:
 PEEK ADDR [COUNT]
 POKE ADDR VALUE
 RUN [ADDR]
 CLS
 TERN N
 HELP
NUMBERS: 123  -5  0T+-0
```

Square brackets mean "you may leave this out".

## 3.3 TERN — show a number in trits

`TERN` shows any number in balanced ternary. It also works the other way round: give it trits and it tells you the ordinary number.

```monitor
> TERN 6
6 = 0t+-0
> TERN 0T+++
13 = 0t+++
> TERN -265720
-265720 = 0t------------
```

The last one is the smallest number a word can hold: twelve minus trits.

## 3.4 PEEK — look into the memory

The memory of your computer is like a very long street of houses, each with a number (its **address**), and in each house lives one word. `PEEK` shows you who lives at an address. Add a count to see several houses in a row.

```monitor
> PEEK 0 3
0: 0 0t0
1: 3 0t+0
2: 3 0t+0
```

Each line shows the address, the word as an ordinary number, and the same word in trits. The first houses of the street belong to the monitor itself. Houses 1 and 2 hold the column and the line of the cursor, and they change while PEEK is printing. When house 1 was read, the cursor stood in column 3, just after `1: `. When house 2 was read, it was on line 3 of the screen (counting from 0, like the computer).

**A strange street.** On your TRIADA-85, house number 0 is in the middle of the street, because addresses are words and words can be negative. The houses with negative numbers hold the ROM and the connections to the joystick, the sound and the picture. The houses from 0 to 59,048 are the computer's working memory (**RAM**). Your programs live from house **243** onwards. The Appendix has a map of the whole street.

## 3.5 POKE — change the memory

`POKE` puts a new word into a house. It does not answer: no news is good news. Check the result with `PEEK`.

```monitor
> POKE 243 0T+-0
> POKE 244 -42
> PEEK 243 2
243: 6 0t+-0
244: -42 0t-+++0
```

You cannot POKE into the ROM. It is read-only, so the word there stays as it was. Don't worry: you cannot break anything with POKE. At worst, a program you have POKEd in will not work until you correct it.

## 3.6 RUN — start a program

`RUN` starts the program that begins at house 243. `RUN 1000` would start a program at house 1000 instead. When the program has finished, the monitor says `READY.` again. Chapter 4 shows how to write programs, and Chapter 7 has a complete program for you to type in.

## 3.7 CLS — clear the screen

`CLS` clears the screen and puts the `>` sign at the top.

## 3.8 Mistakes

If the monitor does not understand you, it says so:

```monitor
> HELLO
?SYNTAX ERROR
> PEEK
?SYNTAX ERROR
> PEEK 243 0
?SYNTAX ERROR
```

A missing number, a count below 1, a number too large for a word or an unknown word all give `?SYNTAX ERROR`. Just type the line again.

---
*TRIADA-85 Owner's Manual · Chapter 3*
