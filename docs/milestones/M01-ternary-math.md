# Milestone 01 — Ternary math

## In one paragraph
A computer is, at its heart, a very fast calculator. Before we can build TRIADA's processor, we have to teach Java (which thinks in binary, like every real computer) to count and calculate the TRIADA way: with *trits* that can be −1, 0 or +1 instead of bits that are 0 or 1. This milestone builds that calculator. It can write any TRIADA number as a row of `-`, `0` and `+` and read it back, and it can add, subtract, multiply and divide. It knows what happens when a number grows too big for the machine. It can also do the strange "trit-by-trit" operations that only a ternary machine has. Every one of the 531,441 numbers the machine can hold has been checked.

## What was built
- **`Trit`**: the three trit values, their characters `-` `0` `+`, and turning one trit "up" or "down".
- **`Ternary`**: powers of three, and conversion between ordinary numbers and `-0+` strings of any width from 1 to 12 trits. Pixels (3 trits) and instruction fields (5 trits) will use it too.
- **`Word`**: the 12-trit *word*, the machine's basic unit. It has range constants, the *wrap* and *carry* formulas, single-trit access, trit arrays and strings.
- **`WordMath`**: every calculation the processor will need:
  - arithmetic: add, subtract (each with carry), multiply, the special rounding division and remainder, negate, sign, compare;
  - trit-wise: MIN, MAX, TMUL, ROT (rotate) and SHT (shift).
- **50 tests**, among them the exhaustive round trip of all 531,441 words, and every operation checked against a slow trit-by-trit reference.
- A clarification in the spec: MOD by zero gives 0 ([D-006](../DECISIONS.md#d-006--mod-by-zero-also-gives-0---milestone-m01-2026-11-07)).

All of it lives in the package `org.triada.core.ternary` inside `triada-core`.

## Why it was built this way

**A word is a plain number, not an object** ([D-004](../DECISIONS.md#d-004--words-are-plain-ints-operations-are-static-methods---milestone-m01-2026-11-07)). It would be natural to model a word as twelve little trit objects. But the processor will run about 3.5 million instructions per second. Every new object is something Java's *garbage collector* (the part of Java that cleans up objects nobody uses any more) must later find and throw away. So a word is just a Java `int` holding its value, for example 6. The surprising fact that makes this work: ordinary addition, subtraction and multiplication give *exactly* the ternary answer. Only when the answer is too big do we need one extra step, the wrap. We only take a word apart into trits when an operation really works trit by trit.

**Trit arrays count from the right** ([D-005](../DECISIONS.md#d-005--trit-arrays-are-indexed-by-position-least-significant-first---milestone-m01-2026-11-07)). In an array, slot `i` holds trit `ti`, the one worth 3^i. The spec numbers trits that way, so the code reads like the spec. The written form (`+-0`) still puts the most important trit first, like ordinary numbers.

**Spec gap: MOD by zero.** The spec's formula and its sentence about division by zero disagreed for MOD. We chose "result 0" for both DIV and MOD ([D-006](../DECISIONS.md#d-006--mod-by-zero-also-gives-0---milestone-m01-2026-11-07)) and fixed the sentence in SPEC §3.5.

## How it works — the plain-language version

### Counting with −1, 0 and +1
Our normal numbers use ten digits, and each place is worth ten times the one to its right: 1, 10, 100… Balanced ternary uses three digits, `-` (−1), `0` and `+` (+1), and each place is worth **three** times the one to its right: 1, 3, 9, 27, 81…

To read `+-0`, multiply each trit by its place value and add up:

```
   +      -      0
 1×9  + (−1)×3 + 0×1  =  9 − 3 + 0  =  6
```

Here are the numbers −4 to 4 written with two trits:

| −4 | −3 | −2 | −1 | 0 | 1 | 2 | 3 | 4 |
|---|---|---|---|---|---|---|---|---|
| `--` | `-0` | `-+` | `0-` | `00` | `0+` | `+-` | `+0` | `++` |

Notice that there's **no minus sign**. Negative numbers are simply the ones whose first non-zero trit is `-`. And to make a number negative, you just flip every trit: `+-0` (6) becomes `-+0` (−6). Binary computers need a trick called *two's complement* (a way of storing negative numbers in binary that turns the top bit into a sign) to do this.

### Units
A **trit** is one digit. Six trits make a **tryte** (−364 … +364). Twelve trits make a **word** (−265,720 … +265,720). That's 3^12 = 531,441 different values. Every memory cell and every *register* (a small storage slot inside the processor) of the TRIADA holds one word.

### Turning a number into trits
Repeatedly ask "what is the last trit?", then remove it. The last trit is the remainder after dividing by 3, but taken as −1, 0 or +1 instead of 0, 1 or 2. A remainder of 2 counts as −1, because 2 = 3 − 1.

Example, 6:

| step | number | remainder → last trit | remove it: (number − trit) ÷ 3 |
|---|---|---|---|
| 1 | 6 | 0 → `0` | (6 − 0) ÷ 3 = 2 |
| 2 | 2 | 2 → `-` | (2 + 1) ÷ 3 = 1 |
| 3 | 1 | 1 → `+` | (1 − 1) ÷ 3 = 0 |

Read from the bottom up: `+-0`. Padded to a word, that's `000000000+-0`.

### When numbers get too big: wrapping and carry
A word has room for only twelve trits. If a sum needs a thirteenth, that trit falls off the end. Think of a car's odometer rolling over from 999999 to 000000. TRIADA's odometer is centred on zero, so the largest number plus one rolls over to the smallest:

```
    ++++++++++++   (265,720, the largest word)
  +            +   (1)
  = +------------  (13 trits!) → drop the leftmost → ------------ = −265,720
```

The trit that fell off is the **carry**. It is +1 when we rolled over the top and −1 when we rolled under the bottom. The processor will keep it in a *flag* (a one-trit note about the last calculation), so a program can notice the overflow.

### Division that rounds to the nearest number
Ordinary computer division throws away the fraction: 8 ÷ 3 = 2.67 becomes 2. TRIADA **rounds to the nearest** whole number instead: 8 ÷ 3 → 3. When the answer is exactly halfway, as in 7 ÷ 2 = 3.5, it rounds toward zero, giving 3 (and −7 ÷ 2 → −3).

The matching **remainder** (MOD) is whatever is left over: 8 − 3×3 = −1. Because of the rounding, the remainder is never more than half the divisor. Here it is between −1.5 and +1.5.

Dividing by zero doesn't crash the machine. The answer is simply 0, and the processor raises the overflow flag (in M3).

### Trit-by-trit operations
Some operations ignore the number's value and work on each trit column separately, like a stencil applied to each position:

| a | b | MIN (smaller) | MAX (larger) | TMUL (a × b) |
|---|---|---|---|---|
| `-` | `-` | `-` | `-` | `+` |
| `-` | `0` | `-` | `0` | `0` |
| `-` | `+` | `-` | `+` | `-` |
| `0` | `0` | `0` | `0` | `0` |
| `0` | `+` | `0` | `+` | `0` |
| `+` | `+` | `+` | `+` | `+` |

(The missing rows are the same with a and b swapped.) If you think of `-` as "false" and `+` as "true", MIN acts as AND and MAX acts as OR, with `0` meaning "unknown". This is the logic of a ternary machine.

**ROT** (rotate) turns every trit one step around a three-hour clock: `-` → `0` → `+` → `-`. Zeros rotate too, so rotating the word 0 once gives `++++++++++++`. Three rotations bring every word back to itself.

### Shifting: the nicest trick of balanced ternary
**SHT** slides all trits left or right.
- **Sliding left** by one place multiplies by 3, just like adding a zero to a decimal number multiplies by 10. Trits pushed past the left edge fall off (wrapping).
- **Sliding right** drops the last trits. In binary that *truncates* (always rounds down). In balanced ternary it **rounds to the nearest number**, for free:

```
  8 = +0-    shift right 1 →  +0  = 3     (8 ÷ 3 = 2.67, nearest is 3)
  7 = +-+    shift right 1 →  +-  = 2     (7 ÷ 3 = 2.33, nearest is 2)
 −8 = -0+    shift right 1 →  -0  = −3    (works the same for negatives)
```

Why does this work? The trits you drop are worth at most "half a step" either way. For example, a dropped `+` in the last place is worth +1, which is less than half of 3. So what's left is always the nearest multiple. And because 3, 9, 27… are odd, there can never be an exact tie.

## How it works — the programmer's version

Study the classes in this order. All four are in [`triada-core/src/main/java/org/triada/core/ternary/`](../../triada-core/src/main/java/org/triada/core/ternary/).

### 1. `Trit`: one digit
[`Trit.java`](../../triada-core/src/main/java/org/triada/core/ternary/Trit.java). A trit is an `int` of −1, 0 or +1. The class has the constants `NEG`, `ZERO`, `POS` and the conversions `Trit.toChar()` and `Trit.fromChar()`. `Trit.rotate(t, n)` is a one-liner:

```java
return Math.floorMod(t + 1 + n, 3) - 1;
```
- `t + 1` moves −1…+1 to 0…2.
- `+ n` and `floorMod(…, 3)` turn the hand of a three-hour clock. `floorMod` instead of `%` is used because Java's `%` returns negative results for negative inputs (−1 % 3 is −1), and we need 2.
- `- 1` moves back to −1…+1.

### 2. `Ternary`: numbers of any width
[`Ternary.java`](../../triada-core/src/main/java/org/triada/core/ternary/Ternary.java).
- `Ternary.pow3(n)` reads from a 13-entry table.
- `Ternary.maxValue(width)` is (3^width − 1)/2: all trits `+`.
- `Ternary.lowestTrit(v)` = `floorMod(v + 1, 3) - 1` is the "remainder as −1/0/+1" step from the plain-language table. It also works for negative numbers, for the same reason as above.
- `Ternary.toString(value, width)` repeats "take the lowest trit, then `v = (v - t) / 3`" (exact, because `v − t` is a multiple of 3) and fills the characters from the right.
- `Ternary.parse(text)` uses *Horner's rule* (build the number one digit at a time: multiply what you have by the base, then add the next digit): `value = value * 3 + trit`. Every error names the offending character and its position, because the assembler (M4) and the monitor (M8) will show these messages to people.

### 3. `Word`: the 12-trit word
[`Word.java`](../../triada-core/src/main/java/org/triada/core/ternary/Word.java). Constants `Word.TRITS` = 12, `Word.COUNT` = 531,441, `Word.MAX` = 265,720 and `Word.MIN` = −265,720.

`Word.wrap(long r)` is SPEC §1.3 word for word:

```java
return (int) (Math.floorMod(r + MAX, (long) COUNT) - MAX);
```
- Adding `MAX` shifts the word range −265,720…265,720 to 0…531,440.
- `floorMod` with 531,441 folds any number into that range.
- Subtracting `MAX` shifts back.

The parameter is a `long` because a product of two words (up to about 7·10^10) doesn't fit in an `int`.

`Word.carry(r)` = `(r − wrap(r)) / COUNT`: how many whole rounds were thrown away.

The other methods:
- `Word.toTrits(word, int[] out)` fills a caller's array and allocates nothing, so the CPU and debugger can reuse one array. `Word.toTrits(word)` is the convenient allocating version for tests and tools.
- `Word.fromTrits()` is Horner's rule again, walking from t11 down to t0.
- `Word.trit(word, position)` shifts right by `position` (see SHT below) and takes the lowest trit.
- `Word.withTrit()` changes one trit by adding `(new − old) · 3^position`.
- `Word.toString()` and `Word.parse()` delegate to `Ternary` with width 12.

### 4. `WordMath`: all operations
[`WordMath.java`](../../triada-core/src/main/java/org/triada/core/ternary/WordMath.java). Every method is `static`, takes and returns `int` words, and never allocates.

**Arithmetic.**
- `WordMath.add()`, `WordMath.sub()` and `WordMath.mul()` do the normal operation in `long` and call `Word.wrap()`.
- `WordMath.addCarry()` and `WordMath.subCarry()` return the carry the CPU will store in flag O.
- `WordMath.neg()` is just `-a`. Unlike in binary, the range is symmetric, so this never overflows.
- `WordMath.sign()` returns the sign. `WordMath.compare()` returns `signum(a − b)` with no wrapping, so MAX compared with MIN is +1 even though `MAX − MIN` would wrap.

**Rounding division**, `WordMath.div()`:
```java
int q = a / b;          // Java rounds toward zero
int r = a - q * b;      // remainder, same sign as a, |r| < |b|
if (2 * Math.abs(r) > Math.abs(b)) {
    q += Integer.signum(a) * Integer.signum(b);
}
```
- Line 1: start from Java's truncated quotient. For 8 ÷ 3 that is 2.
- Line 2: the leftover. For 8 ÷ 3 that is 2.
- Lines 3–4: if the leftover is *more* than half the divisor (2·2 = 4 > 3), the true answer is nearer the next whole number away from zero, so step one further (to 3). Exactly half (7 ÷ 2: 2·1 = 2, not > 2) stays put, which is "ties toward zero".

`WordMath.mod()` is `a − div(a, b) · b`. Both return 0 for b = 0, and `WordMath.isDivideByZero()` tells the CPU when to set O = +1.

**Trit-wise operations use the offset trick.** `Word.MAX` = 1 + 3 + 9 + … + 3^11, so adding it to a word adds 1 to *every* trit. `a + MAX` is then an ordinary base-3 number whose digits 0/1/2 mean −1/0/+1, and plain `% 3` and `/ 3` take it apart with no sign trouble. `WordMath.min()`:

```java
int x = a + Word.MAX, y = b + Word.MAX, result = 0, place = 1;
for (int i = 0; i < Word.TRITS; i++) {
    result += Math.min(x % 3, y % 3) * place;   // one column
    x /= 3; y /= 3; place *= 3;                 // next column
}
return result - Word.MAX;                       // undo the offset
```
- `WordMath.max()` is the same loop with `Math.max`.
- `WordMath.tmul()` converts each digit back to −1/0/+1, multiplies, and converts back.
- `WordMath.rot()` replaces each digit `d` with `(d + steps) % 3`, where `steps = floorMod(n, 3)`.

**Shift**, `WordMath.sht(a, n)`:
- |n| ≥ 12 shifts every trit out and gives 0. That also covers the largest imm5 values, ±121.
- For n > 0: `wrap(a · 3^n)`.
- For n < 0 with p = 3^|n|: `low = floorMod(a + half, p) − half`, where `half = (p − 1)/2`. This is the balanced value of the low |n| trits, by the same fold-into-range idea as `wrap`. Then `(a − low) / p` divides exactly.

## Build it yourself from scratch
1. Write `lowestTrit(v) = floorMod(v + 1, 3) − 1` and convince yourself with v = −2, −1, 0, 1, 2. Everything else builds on it.
2. Write `toString(value, width)` (take the lowest trit, `v = (v − t)/3`, repeat) and `parse` (Horner's rule). Test them with the spec's examples `+-0` = 6 and `-+` = −2.
3. Write the exhaustive test **before** going on: for every v from −265,720 to 265,720, check `parse(toString(v)) == v`. It runs in well under a second.
4. Write `wrap` and `carry` exactly as SPEC §1.3 states them. **Pitfall:** use `Math.floorMod`, never `%`, and do the arithmetic in `long`.
5. Add, sub and mul are one line each on top of `wrap`.
6. Division: start from truncation and correct by one step when the remainder is more than half the divisor. Write a *reference implementation* (a slow but obviously correct version, used only to check the fast one) that compares the two nearest candidates, and test thousands of pairs against it. **Pitfall:** "round half away from zero" (what `Math.round` does for positives) gives 7 ÷ 2 = 4, which is wrong here.
7. Trit-wise operations: either loop over trits after converting with `lowestTrit`, or use the offset trick (`a + MAX` has digits 0/1/2). Check them against a version that converts to trit arrays.
8. SHT right: drop the low trits and check against "round to nearest". **Pitfall:** `Math.floorDiv(a, 3^k)` looks plausible but rounds down. Our mutation check below caught exactly that.

## How we know it works

Run the core tests:

```bash
./gradlew :triada-core:test
```

Expected: `BUILD SUCCESSFUL`. The per-class report (`triada-core/build/reports/tests/test/index.html`) shows 50 tests, 0 failures:

| Test class | Tests | What it proves |
|---|---|---|
| [`TritTest`](../../triada-core/src/test/java/org/triada/core/ternary/TritTest.java) | 4 | Characters follow the spec. Rotation goes `-`→`0`→`+`→`-` and uses mathematical mod (including imm5 = ±121). |
| [`TernaryTest`](../../triada-core/src/test/java/org/triada/core/ternary/TernaryTest.java) | 9 | The spec examples, all pixel colors from SPEC §4.2, every value of every width 1–9 round-tripping, and readable error messages. |
| [`WordTest`](../../triada-core/src/test/java/org/triada/core/ternary/WordTest.java) | 11 | **`WordTest.exhaustiveRoundTripOfEveryWord`**: all 531,441 words to trits and back, and to strings and back. The trits are checked against the definition (Σ trit·3^i, each trit valid), which is independent of the code under test. Also wrap and carry in both directions. |
| [`WordMathTest`](../../triada-core/src/test/java/org/triada/core/ternary/WordMathTest.java) | 24 | Add and sub wrap both ways with carry ±1. MUL wraps (3^6 × 3^6 = 0). Every division example from SPEC §3.5, plus ties, signs, extremes and ÷0. DIV and MOD against a reference for about 465,000 pairs. NEG flips every trit of every word. MIN, MAX and TMUL against a trit-by-trit reference for every word × 8 partners plus 300,000 random pairs. ROT for every word. SHT against its definition for every word and every shift 1–11. |

The whole run takes about 3 seconds, almost all of it in `WordMathTest`.

**Mutation check.** A test suite that never fails proves nothing. So we deliberately broke `WordMath` twice: ties rounded *away* from zero (`>=` instead of `>`), and right shift using `floorDiv`. With those bugs, 10 tests failed:

```
WordMathTest > divRoundingExamplesFromSpec() FAILED
WordMathTest > shtRightRoundsToNearest() FAILED
WordTest > singleTritAccess() FAILED
...
50 tests completed, 10 failed
```

With the code restored, all 50 pass again.

## Problems we hit and how we solved them
- **The spec disagreed with itself about MOD by zero.** Settled as "result 0" ([D-006](../DECISIONS.md#d-006--mod-by-zero-also-gives-0---milestone-m01-2026-11-07)), and SPEC §3.5 updated.
- **Wrong expectations in our own first draft of the tests.** We first expected `MAX + MAX` to wrap to −2 and `MIN + MIN` to 2. The correct answers are −1 and +1 (531,440 − 531,441 = −1). The draft also called `.intValue()` on a plain `int`, which doesn't compile. We caught all of this by re-reading the tests before running them, and checked the corrected values by hand with the wrap formula. Lesson: work out expected values in a test by hand, from the definition, never by "what feels right".
- **Making sure the tests have teeth.** Everything passed on the first run, which is suspicious. The mutation check above shows the tests do catch real mistakes.

## Limitations and what comes next
- **Speed isn't measured yet.** The trit-wise operations loop over 12 trits with a division each time. That should be plenty fast, but M3 measures the CPU as a whole. If needed, they can switch to *lookup tables* (precomputed answers for every pair of trytes, so the work becomes one array read).
- **No `Tryte` type.** Nothing in the spec computes with trytes yet. `Ternary.toString(value, 6)` covers display.
- **`int` isn't type-safe.** Java can't stop someone from passing 999,999 as a "word". Methods document that inputs must be valid words. The conversion methods check it; the hot-path math doesn't, for speed.
- **Next, M2:** memory and the bus, which give these words somewhere to live: RAM, ROM, I/O registers and unmapped space, with address 0 in the middle.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Balanced ternary:** base 3 with the digits −1, 0, +1. Negative numbers need no sign.
- **Tryte / Word:** 6 trits / 12 trits, the machine's basic unit.
- **Most / least significant trit:** the leftmost (biggest place value) / rightmost (place value 1) trit.
- **Wrap (overflow):** a result too big for a word rolls around to the other end, like an odometer.
- **Carry:** the trit that fell off the end when a result wrapped.
- **Flag:** a one-trit note the processor keeps about its last calculation.
- **Register:** a small storage slot inside the processor holding one word.
- **Two's complement:** the binary way of storing negative numbers. TRIADA doesn't need it.
- **Trit-wise operation:** an operation that treats each trit column separately (MIN, MAX, TMUL, ROT).
- **Truth table:** a table listing an operation's result for every combination of inputs.
- **Rotate / Shift:** turn every trit one step around the clock / slide all trits left or right.
- **Horner's rule:** build a number digit by digit: multiply by the base, add the next digit.
- **Garbage collector:** the part of Java that frees unused objects. Creating fewer objects keeps it quiet.
- **Exhaustive test:** a test that tries every possible input, not just a sample.
- **Reference implementation:** a slow but obviously correct version, used only to check the fast one.
- **Mutation check:** breaking the code on purpose to confirm the tests notice.
- **Lookup table:** a precomputed list of answers, so a calculation becomes one array read.
