# Chapter 5 — Pictures and sound

## 5.1 The screen

The picture is 216 dots wide and 144 dots high, framed by a **border**. It is built like a mosaic: 27 columns and 18 lines of little squares, each 8 × 8 dots. Each square shows one of 243 small pictures called **tiles**. The letters you see are tiles too: the ROM puts the alphabet into tiles 32 to 126, so the tile for "A" is number 65, its letter code.

| Houses | What lives there |
|---|---|
| 40,000 … 43,887 | The 243 tiles, 16 words each. |
| 44,000 … 44,485 | The **tile map**: which tile to show in each of the 27 × 18 squares, line by line. |
| 44,600 … 44,707 | The 27 **sprites**, 4 words each. |

Try it in the monitor: square 0 of the tile map is house 44,000. Type `POKE 44000 65` and an "A" appears in the top left corner.

## 5.2 Twenty-seven colours

A colour is three trits: one for **red**, one for **green** and one for **blue**. A `-` means "none of this colour", `0` means "half" and `+` means "full". So `+--` is pure red, `---` is black, `+++` is white and `000` is grey. Three trits give 27 colours, numbered −13 to 13:

| | | | | | |
|---|---|---|---|---|---|
| −13 black | −12 dark blue | −11 blue | −10 dark green | −9 teal | −8 azure |
| −7 green | −6 spring green | −5 cyan | −4 dark red | −3 purple | −2 violet |
| −1 olive | 0 grey | 1 lavender | 2 chartreuse | 3 light green | 4 light cyan |
| 5 red | 6 rose | 7 magenta | 8 orange | 9 salmon | 10 pink |
| 11 yellow | 12 light yellow | 13 white | | | |

Change the border: `POKE -20403 5` makes it red.

## 5.3 Drawing tiles

Each line of a tile is two words of four dots each. The dots are packed into a word by multiplying: first dot × 19,683, second × 729, third × 27, fourth × 1, all added up. A handy trick: **four dots of colour c make the word c × 20,440**. So a tile filled with red (5) is 16 words of 102,200.

## 5.4 Sprites

A **sprite** is a tile that floats freely above the mosaic, at any position. Each of the 27 sprites has four words: its position across, its position down, its tile, and a switch: 1 shows it, −1 shows it upside down, 0 hides it. In a sprite, grey (0) is see-through. Sprite 26 is drawn on top of all the others.

A negative tile number shows the tile mirrored, left to right. This works in the tile map too.

## 5.5 Scrolling

The houses SCROLL_X (−20,405) and SCROLL_Y (−20,404) slide the whole mosaic, dot by dot. What leaves the screen on one side comes back on the other. Sprites stay where they are. The game OBLAKA (Chapter 6) flies over a sky that scrolls one dot down in every picture.

## 5.6 The joystick and the keyboard in your programs

| House | Name | What you read there |
|---|---|---|
| −20,411 | JOY_X | −1 left, 0 middle, +1 right |
| −20,410 | JOY_Y | −1 up, 0 middle, +1 down |
| −20,409 … −20,407 | BTN_A, BTN_B, BTN_C | 1 while the button is pressed |
| −20,406 | KEY | the next key typed, or 0; reading takes it away |
| −20,393 | RAND | a new random number every time |
| −20,412 | FRAME | counts the pictures, 60 per second |

## 5.7 Three voices

Your TRIADA-85 can play three notes at once. Each **voice** has three houses: its **frequency** (how high, in vibrations per second: 440 is the note A), its **volume** (0 to 13) and its **wave** (the kind of sound):

| Wave | Sound |
|---|---|
| −1 | **noise**: hissing, crashes, explosions |
| 0 | **triangle**: soft, like a flute |
| +1 | **ternary square**: the TRIADA's own bright sound, which steps up, middle, down and middle again |

| Voice | Frequency | Volume | Wave |
|---|---|---|---|
| 1 | −20,402 | −20,401 | −20,400 |
| 2 | −20,399 | −20,398 | −20,397 |
| 3 | −20,396 | −20,395 | −20,394 |

Try it: `POKE -20402 440`, then `POKE -20401 10`. You hear an A. `POKE -20400 0` makes it soft, and `POKE -20401 0` makes it stop.

---
*TRIADA-85 Owner's Manual · Chapter 5*
