# Chapter 6 — The Games triskette

Your TRIADA-85 comes with a triskette holding three games. Each one starts by itself when you insert the triskette (in the emulator: `--load programs/trizmeyka.tasm`, `troika.tasm` or `oblaka.tasm`). In all three, the joystick moves and button **A** starts the game.

## TRIZMEYKA — the ternary snake

```
  TRIZMEYKA  SCORE 4   HI 0
  ###########################
  #                         #
  #          o              #
  #       S                 #
  #       SSSSS>            #
  ###########################
```

Guide the hungry snake around the garden with the joystick. It eats fruit of three kinds, which are, of course, the three trits:

| Fruit | Trit | Points | The snake grows by |
|---|---|---|---|
| blue | −1 | 1 | 1 |
| white | 0 | 2 | 2 |
| red | +1 | 3 | 3 |

The snake can't turn straight back on itself. If it runs into the wall or into its own tail, the game is over. Press A to play again. Your best score is kept as **HI** until you switch off.

**Hint:** the red fruit is tempting, but a long snake is a slow-witted snake.

## TROIKA — the puzzle of threes

The board has 25 cells, each showing a trit: blue **−**, grey **0** or red **+**. Make every cell **0**!

Move the yellow frame with the joystick. Each button changes the cell in the frame **and its four neighbours** above, below, left and right:

```
         .                  button A:  rotate up      -  ->  0  ->  +  ->  -
       . X .                button B:  rotate down    +  ->  0  ->  -  ->  +
         .                  button C:  flip           -  <->  +   (0 stays 0)
```

Every puzzle was made by shuffling a finished board with a few random moves. The number of moves is shown as **PAR**, so you know it can be done in that many. Level 1 is shuffled with 3 moves, level 2 with 4, and so on. Press R on the keyboard to start the level again. When the board is all grey, a chord sounds and button A takes you to the next level.

**Hint:** rotating up and flipping is not the same as flipping and rotating up. Try both on a single 0 cell and see!

**For programmers:** TROIKA keeps each row of the board in a single word, one trit per cell, and works out every move with `ROT` and `TMUL` on whole rows at once (Chapter 4). It could only have been invented on a ternary computer.

## OBLAKA — over the clouds

Fly your ship through the night sky above the clouds ("oblaka" means "clouds"). The joystick moves the ship, and holding button A fires. Strange ships come down from the top, each of one of three kinds:

| Enemy | Trit | Points | Note |
|---|---|---|---|
| azure | −1 | 10 | |
| white | 0 | 20 | |
| red | +1 | 30 | twice as fast |

Every few moments each enemy decides, by throwing a three-sided die, whether to drift left, straight on or right: the **ternary wobble**. If an enemy touches your ship you lose one of your three ships (shown at the top right), and your ship blinks for two seconds while nothing can hurt it. The more points you have, the more enemies come. After the last ship, press A to fly again.

**Hint:** stay low, and keep firing.

---
*TRIADA-85 Owner's Manual · Chapter 6*
