# Chapter 7 — From the magazine: a type-in listing

*Reprinted with kind permission from* **TRIT & TRYTE** *— the magazine for ternary computer owners — No. 3/1985, page 27.*

```
 ==========================================================================
   TRIT & TRYTE  No. 3/1985                     TYPE-IN OF THE MONTH
 ==========================================================================
        T R I A N G L E   O F   T H R E E S
        for the TRIADA-85 · 143 words · machine code
 ==========================================================================
```

> **From the editors.** Every month we print a program sent in by a reader. This month's listing comes from a reader in Kharkov, who is fourteen years old and writes: *"In school we learned Pascal's triangle, where every number is the sum of the two above it. On paper it gets very big very quickly. But if you only keep the last trit of every number, a beautiful pattern appears. On a binary computer you would need a special trick for the last trit, but on the TRIADA it is just MOD 3, and the answer is already a trit!"*
>
> The triangle grows down the screen with a rising tone. Then the whole picture starts to turn: every trit of the picture is rotated up twice a second, so blue becomes black, black becomes red, and red becomes blue. Press any key to return to the monitor.

## Typing it in

No assembler? No problem. Switch on your TRIADA-85, type `MON` to reach the monitor, and type the 143 POKE lines below at the `>` sign, each followed by ENTER. Read them **down each column**, starting at the top left. Then type `RUN`.

Typing all of it takes about an hour. Take a break after each column! If the program does not work, check your typing with `PEEK 243 10`, `PEEK 253 10` and so on, and compare the numbers with the listing. The values are printed in trits, as our readers asked. Remember that the monitor shows trits without the zeros in front, just as they are printed here.

```
POKE 243 0t+0+-0000000    POKE 291 0t+----0-00000   POKE 339 0t+-+-0+0+-0-
POKE 244 0t-00-000000     POKE 292 0t+0+0-000000+   POKE 340 0t+0-+0000000
POKE 245 0t-00000000000   POKE 293 0t+0+0+000000-   POKE 341 0t+-0-000
POKE 246 0t-000000+0-     POKE 294 0t--++-0000000   POKE 342 0t++---000000
POKE 247 0t+0--0000000    POKE 295 0t+0++++         POKE 343 0t+0+--00000-+
POKE 248 0t--+--+--+--+   POKE 296 0t+0+0-00000+-   POKE 344 0t-++--000000+
POKE 249 0t+0-00000000    POKE 297 0t+0--0000000    POKE 345 0t+00-0000000
POKE 250 0t+-00+0-00+-    POKE 298 0t-+-+0          POKE 346 0t+0
POKE 251 0t-00000000000   POKE 299 0t++---0000000   POKE 347 0t+++--0-00000
POKE 252 0t++++0-         POKE 300 0t+0-00000000    POKE 348 0t+0+--00000+-
POKE 253 0t+0--0000000    POKE 301 0t+++--+0        POKE 349 0t+-----000000
POKE 254 0t------------   POKE 302 0t+0----000000   POKE 350 0t+0+-0000000+
POKE 255 0t-00000000000   POKE 303 0t+----+-00+0+   POKE 351 0t+0+-+000000-
POKE 256 0t++++0-         POKE 304 0t+0--0000000    POKE 352 0t--++-0000000
POKE 257 0t+0--0000000    POKE 305 0t+00            POKE 353 0t+++-00
POKE 258 0t+--+--+--+--   POKE 306 0t+----+-00++-   POKE 354 0t-0+000000000
POKE 259 0t-00000000000   POKE 307 0t+0--0000000    POKE 355 0t++0+--
POKE 260 0t++++0-         POKE 308 0t++             POKE 356 0t-00000000000
POKE 261 0t+0-00000000    POKE 309 0t---000000000   POKE 357 0t-000000+0-
POKE 262 0t+-+-0+0+-0-    POKE 310 0t+0+--000000-   POKE 358 0t-0-000000000
POKE 263 0t+0-+0000000    POKE 311 0t--++-0000000   POKE 359 0t+0-+0000000
POKE 264 0t+-0-000        POKE 312 0t++-++0         POKE 360 0t+--+
POKE 265 0t+0--0000000    POKE 313 0t+0+00000000-   POKE 361 0t+-----000000
POKE 266 0t+-             POKE 314 0t--++-0000000   POKE 362 0t+0+-0000000+
POKE 267 0t+-----000000   POKE 315 0t+0+++-         POKE 363 0t+0+-+000000-
POKE 268 0t+0+-0000000+   POKE 316 0t+0--0000000    POKE 364 0t--++-0000000
POKE 269 0t+0+-+000000-   POKE 317 0t0              POKE 365 0t++++0+
POKE 270 0t--++-0000000   POKE 318 0t+----+-00++-   POKE 366 0t-0-000000000
POKE 271 0t+0+0-0         POKE 319 0t+0--0000000    POKE 367 0t+00+0
POKE 272 0t+0--0000000    POKE 320 0t++             POKE 368 0t+000+
POKE 273 0t+0             POKE 321 0t+0-00000000    POKE 369 0t+0-0+
POKE 274 0t+0-00000000    POKE 322 0t+-0-           POKE 370 0t+-++-
POKE 275 0t+-+-0+0+0+0    POKE 323 0t-00000000000   POKE 371 0t+00-0
POKE 276 0t+-----000000   POKE 324 0t-000000++0     POKE 372 0t+0-0-
POKE 277 0t+00-0000000    POKE 325 0t+0--0000000    POKE 373 0t+0-++
POKE 278 0t+-+-0++--00    POKE 326 0t+-----+        POKE 374 0t+0--0
POKE 279 0t+0000000000    POKE 327 0t-00000000000   POKE 375 0t++--
POKE 280 0t+--+           POKE 328 0t-0000000++     POKE 376 0t+00-+
POKE 281 0t+00+0000000    POKE 329 0t+0-+0000000    POKE 377 0t+0--+
POKE 282 0t+0-+           POKE 330 0t+0+0           POKE 378 0t++--
POKE 283 0t++--0-0-00-    POKE 331 0t---000000000   POKE 379 0t+00+0
POKE 284 0t++-00-0-00+    POKE 332 0t++--+-00+-0    POKE 380 0t+0-00
POKE 285 0t+0----000000   POKE 333 0t--++-0000000   POKE 381 0t+000+
POKE 286 0t+0+--00000--   POKE 334 0t++++--         POKE 382 0t+0--0
POKE 287 0t+0-00000000    POKE 335 0t+0+-+000000-   POKE 383 0t+0--0
POKE 288 0t+0             POKE 336 0t--++-0000000   POKE 384 0t+00+-
POKE 289 0t+++---000000   POKE 337 0t++0+-+         POKE 385 0t0
POKE 290 0t+0+--00000+-   POKE 338 0t+0-00000000
```

## The source

Owners of the Developer triskette can type the source below into TASM instead. The semicolons start the comments, which you can leave out.

```tasm
        .org 243
        LDI R6, IO_BASE
        JSR CLS
        LDI R0, -224840                 ; tile 1: blue  (-11 x 20440)
        LDI R1, TILES+16
        JSR paint
        LDI R0, -265720                 ; tile 2: black (-13 x 20440)
        JSR paint
        LDI R0, 102200                  ; tile 3: red   (5 x 20440)
        JSR paint
        LDI R1, MAP                     ; rows 0 ... 16: trit 0 everywhere
        LDI R2, 459
        LDI R0, 2                       ; (a cell holds its trit + 2: tile 1, 2 or 3)
clear:  ST R0, [R1]
        INC R1
        DEC R2
        JNZ clear
        LDI R0, 3                       ; the top: a single +1
        LDI R1, MAP+13
        ST R0, [R1]
        LDI R3, MAP+28                  ; row 1, column 1
        LDI R4, 16                      ; rows still to grow
row:    LDI R5, 25                      ; columns 1 ... 25
cell:   LD R0, [R3-28]                  ; up and left
        LD R1, [R3-26]                  ; up and right
        ADD R0, R1
        ADDI R0, -4                     ; two tiles -> two trits
        LDI R1, 3
        MOD R0, R1                      ; MOD 3 rounds: 2 -> -1, -2 -> +1
        ADDI R0, 2
        ST R0, [R3]
        INC R3
        DEC R5
        JNZ cell
        ADDI R3, 2                      ; on to column 1 of the next row
        LDI R0, -60                     ; a rising note: 1020 - 60 x rows left
        MUL R0, R4
        LDI R1, 1020
        ADD R0, R1
        ST R0, [R6+V1_FREQ]
        LDI R0, 9
        ST R0, [R6+V1_VOL]
        LDI R0, 4                       ; 4 frames per row
wait:   WAIT
        DEC R0
        JNZ wait
        DEC R4
        JNZ row
        LDI R0, 0
        ST R0, [R6+V1_VOL]
        LDI R0, 4
        LDI R1, 17
        JSR LOCATE
        LDI R0, title
        JSR PRINT
turn:   LDI R2, 30                      ; half a second
pause:  WAIT
        LD R0, [R6+KEY]                 ; a key? back to the monitor
        JNZ done
        DEC R2
        JNZ pause
        LDI R1, MAP
        LDI R2, 459
rotate: LD R0, [R1]
        ADDI R0, -2                     ; tile -> trit
        ROT R0                          ; every trit up ...
        LDI R3, 3
        MOD R0, R3                      ; ... and keep only the lowest one
        ADDI R0, 2
        ST R0, [R1]
        INC R1
        DEC R2
        JNZ rotate
        JMP turn
done:   JSR CLS
        RET
paint:  LDI R2, 16                      ; the word R0 into the 16 words at R1
p_loop: ST R0, [R1]
        INC R1
        DEC R2
        JNZ p_loop
        RET
title:  .str "TRIANGLE OF THREES"
```

## How it works

```
   row 0:              +                    the top: a single +1
   row 1:            +   +                  every cell = (up-left + up-right) MOD 3
   row 2:          +   -   +                + plus + = 2, and 2 MOD 3 = -1  (blue)
   row 3:        +   0   0   +              + plus - = 0                    (black)
   row 4:      +   +   0   +   +
```

* **Cells hold trit + 2.** Tile 1 is blue (−1), tile 2 black (0) and tile 3 red (+1). So a cell's trit is its tile number minus 2, and two cells together are their tiles minus 4.
* **MOD 3 does the rest.** The TRIADA's division rounds to the nearest whole number, so its remainder after dividing by 3 is always −1, 0 or +1: already a trit! In ordinary numbers Pascal's triangle has the numbers 1, 2, 1 in row 2. Modulo 3 that is +1, −1, +1.
* **The pattern is a "fractal".** Big triangles are made of three smaller triangles, which are made of three smaller ones still. Count the black holes: they come in threes, too.
* **Turning the picture.** `ROT R0` rotates *every* trit of a register up at once. A cell holds only one trit, so the program keeps just the lowest one with `MOD 3`.
* **Colours by multiplying.** The tiles are painted with `paint`, which fills 16 words. Four dots of colour c are the word c × 20,440, so blue (−11) is −224,840.

## Things to try

1. Change the colours: red is 5 × 20,440 = 102,200. What colour is 11 × 20,440?
2. Start with a −1 at the top: change the `LDI R0, 3` before `MAP+13` into `LDI R0, 1`. What happens to the pattern?
3. Make the picture turn faster: change the 30 in `LDI R2, 30`.

*Programs published in TRIT & TRYTE may be copied for private use. Send your own listings to the editors. The best program of the year wins a TRIADA-85 Developer triskette!*

---
*TRIADA-85 Owner's Manual · Chapter 7*
