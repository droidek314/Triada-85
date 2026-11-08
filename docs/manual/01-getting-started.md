# Chapter 1 — Getting started

## 1.1 What is in the box

Please check that your package contains:

1. The **TRIADA-85 computer** with its built-in keyboard.
2. The **power supply** (220 V, 50 Hz).
3. The **TV lead** for the aerial socket of your television.
4. The **joystick** with three fire buttons, marked A, B and C.
5. The **Games triskette**, with three games (see Chapter 6).
6. This manual and the guarantee card.

If anything is missing, please contact the shop where you bought your computer.

## 1.2 Connecting

```
   TELEVISION                    TRIADA-85                    POWER
  +-----------+   TV lead    +----------------------+    +-----------+
  |           |<-------------| TV      JOY  TAPE    |<---| 220 V     |
  |           |              |                      |    +-----------+
  +-----------+              |  [triskette drive]   |
                             +----------------------+
                                 ^
                       joystick -+
```

1. Connect the TV lead to the socket marked **TV** and to the aerial socket of your television.
2. Connect the joystick to the socket marked **JOY**.
3. Connect the power supply last.
4. Tune your television to channel 36 until the picture is steady.

**CAUTION:** Never insert or remove the joystick while the computer is switched on. Keep the triskettes away from magnets, radiators and younger brothers.

## 1.3 Switching on

Switch on the computer. You will hear a short chime of three rising notes, and after about one second the screen shows:

```
TRIADA-85 NPO "TRIADA" 1985
TRIBASIC V1.0
39757 WORDS FREE
READY.
_
```

The blinking line is the **cursor**. It shows where the next letter you type will appear. Your computer is now waiting for your commands. The program that greets you is **TRIBASIC**, the computer's own programming language, built into it. Try `PRINT 6 * 7` and press ENTER.

For working with the machine itself there is a second built-in program, the **monitor**. Type `MON` and press ENTER to reach it, and `BASIC` to come back; your TRIBASIC program waits for you meanwhile. Chapter 3 tells you everything about the monitor.

"39757 WORDS FREE" means that 39,757 places in the computer's memory are free for your programs. Each place (a **word**) holds one number. Chapter 2 explains what kind of numbers.

## 1.4 The keyboard

Type letters as on a typewriter. The computer works in capital letters, so you need not press the shift key (only text "in quotes" in TRIBASIC keeps small letters). Press **ENTER** to finish a line, and **BACKSPACE** to rub out the last letter you typed. A TRIBASIC line may be 80 letters long and simply continues on the next screen row; a monitor line at most 24. The **ESC** key stops a running TRIBASIC program.

## 1.5 The joystick

The joystick has three positions from left to right (left, middle, right) and three from top to bottom (up, middle, down). Of course! A binary joystick would have only two. The three fire buttons are called **A**, **B** and **C**. Programs read the joystick and buttons whenever they like (see Chapter 5). TRIBASIC and the monitor do not use them.

## 1.6 Triskettes

A **triskette** holds programs. Insert it in the drive with the label upwards, and the computer starts again: the chime plays, and then the program on the triskette starts by itself. When it ends, TRIBASIC says `READY.` again.

## 1.7 If something goes wrong

| What you see | What to do |
|---|---|
| No picture | Check the TV lead and the channel. Is the power supply connected? |
| `?SYNTAX ERROR` | TRIBASIC or the monitor did not understand your line. Check the spelling (Chapter 3 for the monitor). |
| The picture stands still and nothing reacts | A program has stopped the processor. Switch off and on again. |
| A program does strange things | Check your typing against the listing, word by word (Chapter 7). |

---
*TRIADA-85 Owner's Manual · Chapter 1*
