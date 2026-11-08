# Chapter 2 — Counting in threes

## 2.1 The trit

You count with ten digits, 0 to 9. Your TRIADA-85 counts with only three, and they are a little unusual:

| Digit | Value | Written |
|---|---|---|
| minus | −1 | `-` |
| zero | 0 | `0` |
| plus | +1 | `+` |

Such a digit is called a **trit** (from "ternary digit"). This way of counting is called **balanced ternary**, because the digits are balanced around zero.

## 2.2 Numbers made of trits

In our ordinary numbers each place is worth ten times the place to its right: ones, tens, hundreds. In balanced ternary each place is worth **three** times the place to its right: 1, 3, 9, 27, 81, 243, and so on. To read a number, add up the places, counting a `+` as plus and a `-` as minus.

```
   + - 0          means   1 x 9   -  1 x 3   +  0 x 1   =   9 - 3     =   6
   - +            means  -1 x 3   +  1 x 1              =  -3 + 1     =  -2
   + - - - 0      means  81 - 27 - 9 - 3 + 0                          =  42
```

Here are the first few numbers. Look how the negative numbers are simply the positive ones with every `+` and `-` swapped:

| Number | Trits | | Number | Trits |
|---|---|---|---|---|
| 1 | `+` | | −1 | `-` |
| 2 | `+-` | | −2 | `-+` |
| 3 | `+0` | | −3 | `-0` |
| 4 | `++` | | −4 | `--` |
| 5 | `+--` | | −5 | `-++` |
| 6 | `+-0` | | −6 | `-+0` |
| 13 | `+++` | | −13 | `---` |

**There is no minus sign.** A number is negative when its first trit is `-`. To make a number negative, swap every `+` and `-`. Binary computers have to work hard to do this; your TRIADA-85 does it in a moment.

## 2.3 Writing trits for the computer

When you give the computer a number in trits, start it with `0t` so that it knows. `0t+-0` is 6, and `0t-+` is −2. You can always use ordinary numbers as well: the computer understands both.

The monitor can show you any number in trits. Type `MON` to reach it (Chapter 3), then type `TERN 42` and press ENTER:

```monitor
> TERN 42
42 = 0t+---0
> TERN -42
-42 = 0t-+++0
```

## 2.4 Trits, trytes and words

| Unit | Trits | Smallest | Largest | Different values |
|---|---|---|---|---|
| trit | 1 | −1 | +1 | 3 |
| tryte | 6 | −364 | +364 | 729 |
| **word** | **12** | **−265,720** | **+265,720** | **531,441** |

Your computer works with **words** of 12 trits. Every place in its memory holds exactly one word. If a sum becomes too large for a word, it goes round to the other end, like the mileage counter of a car: 265,720 + 1 gives −265,720.

## 2.5 Rounding comes free

Here is a secret that makes ternary engineers smile. If you cut the last trits off a number in balanced ternary, the rest is the number divided and **rounded to the nearest whole number**, not simply chopped:

```
   +--  is 5.   Cut the last trit:  +-  is 2.     5 ÷ 3 = 1.67 ... rounded: 2
   ++   is 4.   Cut the last trit:  +   is 1.     4 ÷ 3 = 1.33 ... rounded: 1
```

That is why your TRIADA-85's division always rounds to the nearest whole number. It even means that the "remainder" of a division may be negative: 7 divided by 10 is 1, remainder −3, because 1 × 10 − 3 = 7. Programmers of other machines find this strange at first. You will find it natural.

## 2.6 Things to try

1. Use `TERN` to find out how 100 and −100 look in trits. Do you see the swap?
2. Which number is `0t+0+0+`? Check your answer with `TERN 0t+0+0+`.
3. What is the largest number you can write with three trits? (Answer: `+++` = 13.)

---
*TRIADA-85 Owner's Manual · Chapter 2*
