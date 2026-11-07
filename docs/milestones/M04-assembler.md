# Milestone 04 — Assembler and disassembler

## In one paragraph
Until now, a TRIADA program had to be typed in as raw numbers: `ADD R1, R2` was the number 150,417, and every jump target had to be counted out by hand. An *assembler* is a translator that lets a person write `ADD R1, R2` and `JMP loop` and produces the numbers for them. It works out where every label ends up, checks every value, and explains mistakes politely ("line 3, column 13: unknown name 'Start' — did you mean 'start'?"). This milestone builds that translator for TRIADA's own assembly language, plus the opposite tool, a *disassembler*, which turns numbers back into readable instructions. The debugger will need it later. It also defines the `.tri` "triskette" file in which finished programs are stored.

## What was built
- **`TriImage`** (in `triada-core`): a program image, meaning a load address plus words, with conversion to and from the bytes of a `.tri` file (SPEC §9.5).
- **The assembler** (package `org.triada.assembler`):
  - `Lexer` cuts a line into tokens.
  - `Parser` turns tokens into a `Line`: label, constant, instruction or directive.
  - `Assembler` runs the two passes: layout, then encoding.
  - `Mnemonics` lists all 27 instructions and 8 pseudo-instructions with their operand forms.
  - `BuiltinSymbols` holds `PUTCHAR`, `IO_BASE`, `KEY`, `TILES` and the rest.
  - `SourceLoader` reads `.include` files without the library touching the disk.
  - `Listing` renders the `.lst` listing.
  - `AssemblyError` and `AssemblyException` report errors with file, line and column.
- **The disassembler**: `Disassembler`, with one instruction at any address (for the debugger), a whole block, or a complete re-assemblable source file.
- **104 new tests**, including:
  - every instruction form and directive;
  - 53 error cases, each checked for line, column and wording;
  - an *exhaustive* round trip over all 531,441 possible first words;
  - seven CPU tests written in TRIADA assembly.
- Five decisions (D-012 to D-016), and a new SPEC §9.6 listing the syntax details they settled.

## Why it was built this way

**A hand-written lexer and parser.** TRIADA's syntax is one statement per line, so a small hand-written *recursive-descent parser* (a set of functions, one per grammar rule, that call each other) is short, fast, and gives precise error messages. Parser-generator tools exist, but they would add a dependency (CLAUDE.md §2 says no) and usually produce worse messages.

**Two passes.** `JMP end` may come before `end:` is defined. Pass 1 only works out *sizes and addresses*: every instruction's length depends only on its mnemonic, never on its operands' values, so all labels can be placed without knowing any value. Pass 2 then has every label and encodes everything. The one exception is `.org` and the count of `.fill`, which decide addresses themselves, so their values must be known in pass 1 ([D-013](../DECISIONS.md#d-013--assembler-syntax-details-the-spec-leaves-open---milestone-m04-2026-11-07)).

**No file access in the library.** `.include` goes through the `SourceLoader` interface. The CLI (M5) will read files from disk; the tests use a `Map` of strings. This keeps `triada-assembler` portable to a web build, as CLAUDE.md asks.

**Many errors at once, but no misleading ones** ([D-016](../DECISIONS.md#d-016--report-many-errors-but-dont-encode-after-a-layout-error---milestone-m04-2026-11-07)). Every bad line is reported, up to 100. After a layout error, encoding is skipped, because every later address could be wrong and would trigger confusing follow-on errors.

**One `.tri` block, default origin 243** ([D-012](../DECISIONS.md#d-012--program-images-default-origin-243-one-block-gaps-filled-with-0---milestone-m04-2026-11-07)).

**Exact round trips** ([D-014](../DECISIONS.md#d-014--the-disassembler-prints-word-for-words-without-a-canonical-spelling---milestone-m04-2026-11-07)). Any word whose instruction text wouldn't re-assemble to exactly that word is shown as `.word N`.

**ASCII-only messages** ([D-015](../DECISIONS.md#d-015--everything-the-programs-print-is-plain-ascii---milestone-m04-2026-11-07)), because the Windows console showed `−` as `?`.

**`TriImage` lives in `triada-core`**, not in the assembler, because the headless runner and the desktop frontend load `.tri` files without needing the assembler.

## How it works — the plain-language version

### What an assembler does
The CPU understands only numbers. People understand words. *Assembly language* is the thinnest possible layer between the two: one line of text is one instruction, written with its *mnemonic* (a short name like `ADD`) instead of its opcode number.

```
  you write                    the assembler produces
  ---------                    ----------------------
  start:  LDI R0, msg          50301, 250
          JSR PRINT            -177147, -19679
  loop:   WAIT                 -255879
          JMP loop             -157464, 247
  msg:    .str "HELLO, WORLD"  72, 69, 76, 76, 79, 44, 32, 87, ... 0
```

Besides instructions, a source file can contain:
- **labels** (`loop:`), which are names for addresses;
- **constants** (`SPEED = 3`), which are names for numbers;
- **comments** (after `;`);
- **directives**, which are instructions to the assembler itself rather than to the CPU:
  - `.org 243` means "continue at address 243";
  - `.str "HELLO"` stores a string, one character per word, ending with a 0;
  - `.word` stores raw words;
  - `.fill 10, 0` stores ten copies of a word;
  - `.pix` packs four pixels into one word (SPEC §4.3);
  - `.include "file"` pastes in another file.

Numbers can be written in decimal (`-7`), in balanced ternary (`0t+-0` = 6) or as a character (`'A'` = 65). They can be combined with `+` and `-`: `IO_BASE + KEY`, `end - start`.

### Two passes, like numbering the pages of a book
Imagine writing a book whose text says "see page 42" before you know what will be on page 42. The sensible way: first lay out the whole book and number every page (pass 1), then go back and fill in all the page references (pass 2).

The assembler does the same:
1. **Pass 1, layout.** Read every line. Count how many words each one will take: `LDI` is 2, `ADD` is 1, `.str "HI"` is 3. Keep a running address, starting at 243. When a line has a label, note "loop = 247".
2. **Pass 2, encoding.** All labels are now known. Turn every line into its words, look up every name, and check every value.

### Checking everything
A mistake in assembly language can make a program behave in strange ways that are very hard to track down. So the assembler refuses anything doubtful, and every message says exactly where and what. Here is the real output for a three-line file with three mistakes:

```
3 errors
oops.tasm:1:16: value 300000 is out of range for a word (-265720 .. 265720)
oops.tasm:2:18: value 200 doesn't fit in the 5-trit immediate field (-121 .. 121); use LDI and a register for larger values
oops.tasm:3:13: unknown name 'Start' (did you mean 'start'? names are case-sensitive)
```

Each line reads as file : line : column : what was expected and what was found. The "did you mean" hint appears because labels are case-sensitive (SPEC §9.1). That is a classic trap.

### The listing
The assembler can also print a *listing*: the source with the address and words of every line next to it, in ternary and decimal. Here is the real listing of the example from SPEC §9.1:

```
; TRIADA-85 assembler listing
;ADDRESS  TERNARY        DECIMAL   LINE  SOURCE
; ---- hello.tasm ----
                                      1  ; comment
                                      2  NAME = 59049              ; constant
                                      3          .org 243
     243  0+0--0000000     50301      4  start:  LDI R0, msg       ; label as immediate
     244  000000+00+-+       250
     245  -00000000000   -177147      5          JSR PRINT
     246  00-0000000++    -19679
     247  ---000000000   -255879      6  loop:   WAIT
     248  -0+000000000   -157464      7          JMP loop
     249  000000+000++       247
     250  0000000+0-00        72      8  msg:    .str "HELLO, WORLD"
     ...
```

Look at the ternary column of line 4: `0+0` `--` `00` `00000` is opcode 3 (LDI), rd = −4 (R0), rs = 0, imm5 = 0. That is exactly the instruction layout of SPEC §3.3.

### The disassembler: back again
The disassembler reads words and writes the text: −177147, −19679 becomes `JSR -19679`. The tricky part is that not every word is a sensible instruction. The letter `'H'` (72) decodes as `HLT` with a junk number in a field the CPU ignores. Printing it as `HLT` would lie: assembling `HLT` gives 0, not 72. So whenever the text wouldn't reproduce the word exactly, the disassembler prints `.word 72` instead. That's how it keeps its promise: **assemble → disassemble → assemble always gives the same words.**

### The `.tri` file
A finished program is saved as a `.tri` file: the eight letters `TRIADA85`, the load address, the number of words, then the words. Each number takes four bytes, least significant byte first:

```
 54 52 49 41 44 41 38 35 | F3 00 00 00 | 02 00 00 00 | 01 00 00 00 | FE FF FF FF
 T  R  I  A  D  A  8  5  | load = 243  | count = 2   | word 1      | word -2
```

## How it works — the programmer's version

Read the classes in the order the text flows through them.

### 1. `Lexer`: characters → tokens
[`Lexer.java`](../../triada-assembler/src/main/java/org/triada/assembler/Lexer.java). `Lexer.tokenize(line)` scans left to right and produces `Token`s. Each `Token` ([`Token.java`](../../triada-assembler/src/main/java/org/triada/assembler/Token.java)) has a type, text, numeric value and **column**.
- Names: `[A-Za-z_][A-Za-z0-9_]*`.
- Numbers: decimal, or `0t` followed by trits (decoded by `Ternary.parse` from M1).
- Characters and strings, with escapes; only printable ASCII is allowed.
- Single-character punctuation, `.directive`, and `;` to end of line.

A minus sign is always its own token. That keeps the lexer simple and makes `5-3` and `-3` work the same way.

Errors are thrown as `AsmFailure` ([`AsmFailure.java`](../../triada-assembler/src/main/java/org/triada/assembler/AsmFailure.java)), an internal exception with a column. It is created without a stack trace, because it is ordinary control flow, not a crash.

### 2. `Parser`: tokens → `Line`
[`Parser.java`](../../triada-assembler/src/main/java/org/triada/assembler/Parser.java). `Parser.parse(text)` implements the grammar in its class comment, one method per rule (`line()`, `operand()`, `expr()`, `unary()`). The result is a `Line` record ([`Line.java`](../../triada-assembler/src/main/java/org/triada/assembler/Line.java)): label, kind (EMPTY, CONSTANT, INSTRUCTION, DIRECTIVE), name, and operands.

- Operands are `Operand.Reg`, `Operand.Mem`, `Operand.Value` or `Operand.Str` ([`Operand.java`](../../triada-assembler/src/main/java/org/triada/assembler/Operand.java)).
- Expressions are a small tree ([`Expr.java`](../../triada-assembler/src/main/java/org/triada/assembler/Expr.java)): `Num`, `Sym`, `Neg` and `Bin`. They are trees, not numbers, because a label's value may not be known yet.
- `Registers.number()` ([`Registers.java`](../../triada-assembler/src/main/java/org/triada/assembler/Registers.java)) recognises `R0`–`R8` and `SP` in any case.

The parser checks only the *shape* of a line. Whether `ADD` really got two registers is the assembler's job, because only it knows each mnemonic.

### 3. `Mnemonics`: what each instruction expects
[`Mnemonics.java`](../../triada-assembler/src/main/java/org/triada/assembler/Mnemonics.java). `Mnemonics.lookup()` maps every mnemonic to a `Spec(name, opcode, form, fixed)`.

The `Mnemonics.Form` (NONE, R, RR, R_WORD, R_MEM, R_IMM, R_OPT_IMM, ADDR, COND_ADDR, ADDR3) decides both the operand check and the encoding, and carries the text used in "expects …" messages.

Pseudo-instructions are just rows with a `fixed` value:
- `JNZ` is `JIF` in the ADDR form with condition 2;
- `INC` is `ADDI` in the R form with imm5 = 1;
- `ROT R0` works because ROT's form has an optional immediate that defaults to 1.

`Mnemonics.conditionAlias()` goes the other way, for the disassembler.

### 4. `Assembler`: the two passes
[`Assembler.java`](../../triada-assembler/src/main/java/org/triada/assembler/Assembler.java). The public entry points are `Assembler.assemble(path)` (main file via the loader) and `Assembler.assembleSource(name, text)`. Each call creates a fresh `Assembler.Run`, which holds all the state of one assembly. That's why one `Assembler` object can be reused.

Each source line becomes an `Assembler.SourceLine` that collects what the passes learn: its parsed `Line`, `address`, `size` and `words`.

**Pass 1** is `Run.layoutFile()` → `Run.layout()` for every line:
- a label is defined at the current address (`pc`);
- an instruction reserves `spec.length()` words, after its operand count is checked;
- `.org` moves `pc`;
- `.word`, `.str`, `.fill` and `.pix` reserve their sizes;
- `.include` resolves the path with `Assembler.resolve()`, checks for cycles with a stack of open files, and calls `layoutFile()` recursively.

`Run.define()` rejects duplicates and built-in names.

**Pass 2** runs only without pass-1 errors:
- `Run.checkConstants()` evaluates every constant at its own line.
- Then `Run.encode()` handles every line. For instructions, `Run.encodeInstruction()` has one `case` per form. Here is R_MEM, for `LD`/`ST`:
```java
int rd = reg(ops.get(0), "first operand");
Operand.Mem m = mem(ops.get(1));
int imm = m.offset() == null ? 0 : imm5(evaluate(m.offset(), false), m.offset().column());
return new int[] {enc(op, rd, Instruction.reg(m.base()), imm)};
```
- `reg()` checks that the first operand is a register and gives its field value (n − 4).
- `mem()` checks that the second operand is a bracketed memory operand.
- `[R1]` has no offset, so imm5 = 0. Otherwise the offset is evaluated and range-checked by `imm5()`, whose error names the column of the offset.
- Finally `Instruction.encode()` from M3 packs the fields.

**Expressions.** `Run.evaluate()` walks the `Expr` tree using `long` arithmetic, so huge values can't silently wrap before the range check. `Run.symbol()` looks a name up as a label, then a constant, then a built-in. Unknown names get a "did you mean" hint from `Run.suggestion()`. Constants are evaluated on first use by `Run.constantValue()`, which:
- caches the value;
- detects cycles with an `evaluating` flag (`A = B`, `B = A`);
- reports an error inside a definition once, at the definition's own line, and then aborts the using line with the silent `AsmFailure.silent()`.

**Output.**
- `Run.buildImage()` finds the lowest and highest address used, copies all words into one array (gaps stay 0), and reports any address produced twice.
- `Listing.render()` ([`Listing.java`](../../triada-assembler/src/main/java/org/triada/assembler/Listing.java)) writes the listing.
- `Run.symbols()` collects labels and constants for the result.

Everything is returned as an `AssemblyResult(image, listing, symbols)` ([`AssemblyResult.java`](../../triada-assembler/src/main/java/org/triada/assembler/AssemblyResult.java)). Errors become an `AssemblyException` whose `AssemblyException.errors()` lists `AssemblyError(file, line, column, message)` records ([`AssemblyError.java`](../../triada-assembler/src/main/java/org/triada/assembler/AssemblyError.java)).

### 5. Supporting cast
- [`BuiltinSymbols.java`](../../triada-assembler/src/main/java/org/triada/assembler/BuiltinSymbols.java): `BuiltinSymbols.all()` holds the ROM jump-table addresses (−19,683 + 2·i), `IO_BASE`, the 23 register offsets from `IoRegisters`, and `TILES`, `MAP` and `SPRITES` from `MemoryMap`. All are computed from the core's constants, never typed twice.
- [`SourceLoader.java`](../../triada-assembler/src/main/java/org/triada/assembler/SourceLoader.java): one method, `SourceLoader.load(path)`, returns the text or `null`. `SourceLoader.NONE` knows no files.
- [`TriImage.java`](../../triada-core/src/main/java/org/triada/core/TriImage.java): `TriImage.toBytes()` and `TriImage.fromBytes()` write and read the little-endian format byte by byte (no `ByteBuffer`, to stay web-friendly). `fromBytes` explains *why* a file is broken: wrong magic, wrong length, or a value that isn't a word.

### 6. `Disassembler`
[`Disassembler.java`](../../triada-assembler/src/main/java/org/triada/assembler/Disassembler.java). Everything goes through `Disassembler.decode(address, buf, available)`:
1. Decode the fields with M3's `Instruction` helpers.
2. If the instruction needs more words than are available, return `.word`.
3. Build the text with one `case` per group of opcodes, and at the same time compute the **canonical** word: the same opcode and used fields, with unused fields 0.
4. If the canonical word differs from the real one, return `.word` (D-014).

The three entry points:
- `Disassembler.disassemble(address, memory)` reads through a function such as the debugger's `bus::peek`, wrapping addresses like PC does.
- `Disassembler.disassembleAll()` walks a block.
- `Disassembler.toSource()` adds an `.org` line, so the output is a complete source file.

## Build it yourself from scratch
1. **Tokens first.** Write a lexer that records the column of every token. Test that each kind of literal decodes correctly, and that `;` inside a string isn't a comment. **Pitfall:** don't make `-7` a single token, or `5-7` breaks.
2. **One-line parser.** Each line is label? + statement?. Keep expressions as trees.
3. **A table of mnemonics with operand forms.** Pseudo-instructions are just more rows in it.
4. **Pass 1:** sizes and labels only. **Pitfall:** a label on an `.org` line, and `.fill` counts, need special care because they change addresses.
5. **Pass 2:** evaluate, range-check (never truncate silently), encode with the CPU's own encoder.
6. **Error reporting:** carry the column all the way through, and test every message with its exact line and column. A table-driven test (one row per error) keeps this manageable.
7. **Disassembler:** for each word build text *and* re-encode it. If the two don't agree, print `.word`.
8. **Round trip:** for every possible first word, disassemble, assemble, compare. Assemble in batches of tens of thousands of lines to keep it fast.
9. **The `.tri` format:** write and read it byte by byte, and test a hand-checked byte layout.

## How we know it works

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. `triada-core` now has 185 tests and `triada-assembler` 98 (plus 1 in the CLI). 104 are new in M4.

| Test class | Tests | What it proves |
|---|---|---|
| [`TriImageTest`](../../triada-core/src/test/java/org/triada/core/TriImageTest.java) | 6 | The exact byte layout of SPEC §9.5, round trip through bytes, and readable rejection of broken files. |
| [`LexerTest`](../../triada-assembler/src/test/java/org/triada/assembler/LexerTest.java) | 7 | All three number notations, escapes, comments vs. quotes, and error columns. |
| [`AssemblerTest`](../../triada-assembler/src/test/java/org/triada/assembler/AssemblerTest.java) | 24 | **`AssemblerTest.theExampleFromSpecSection9`** assembles the spec's own example word for word. Every real instruction against `Instruction.encode`, every pseudo-instruction, memory operands, case rules, expressions and forward references, built-in symbols, every directive, `.org` gaps, `.include` with relative paths, the listing layout, and line endings. |
| [`AssemblerErrorTest`](../../triada-assembler/src/test/java/org/triada/assembler/AssemblerErrorTest.java) | 53 | 45 table rows, each checking file, line, column and message. Plus several errors at once, the error limit, include cycles, and constants that fail. |
| [`DisassemblerTest`](../../triada-assembler/src/test/java/org/triada/assembler/DisassemblerTest.java) | 7 | Text for every form, `.word` for non-canonical words, and the round trip of a real program. **`DisassemblerTest.roundTripOfEveryPossibleFirstWord`**: all 531,441 words, about 1.7 s. |
| [`AssembledProgramTest`](../../triada-assembler/src/test/java/org/triada/assembler/AssembledProgramTest.java) | 7 | CPU tests in assembly: sum 1..100, recursive factorial, CMP+BR3 classification, string length, trits via MOD/DIV 3, reading the KEY queue, counting frames with WAIT. |

**Mutation check.** We broke two things on purpose: `JLE` mapped to condition +3 instead of −2, and the `\n` escape produced 13 instead of 10. 5 tests failed (among them `pseudoInstructions`, `recursiveFactorialUsesTheStack`, `escapesInCharactersAndStrings` and the exhaustive round trip). With the code restored, all pass.

## Problems we hit and how we solved them
- **Errors in constants pointed at the wrong line.** The first version reported an error deep inside a constant's definition at the line that *used* the constant, but with a column belonging to the definition's line. It also never checked constants nobody used. The fix: `checkConstants()` evaluates every constant at its own line, an error is reported there once, and users of a broken constant fail silently. A test with a chain of constants (`X = Y`, `Y = nowhere`) caught a double report on the way.
- **Unreadable messages on Windows.** `−`, `…` and `→` came out as `?` and `�` in the console. All printed text is now ASCII ([D-015](../DECISIONS.md#d-015--everything-the-programs-print-is-plain-ascii---milestone-m04-2026-11-07)).
- **Hidden errors.** `ADD R1` (a missing operand) was only detected in pass 2, so it wasn't reported next to a pass-1 error elsewhere. Operand counts are now checked in pass 1.
- **Our own test drafts had mistakes.** Some expectations were wrong (for example, an error we expected from the parser actually comes from the assembler's operand check), and one test built its program with a confusing string replace. We fixed all of them before trusting the results.
- **Shell trouble while scripting edits.** Some edit scripts failed in the shell because of characters like `…` inside heredocs. They were rewritten as files. This is a tooling note, not a project bug.

## Limitations and what comes next
- **No command-line tool yet.** M5 adds `triada tasm source.tasm -o out.tri --listing out.lst`, which reads files through a `SourceLoader` and writes `TriImage.toBytes()` and the listing.
- **Expressions only add and subtract**, exactly as the spec says. There are no `*`, `/` or parentheses; constants can work around that.
- **The disassembler doesn't know symbols.** It prints `JMP 247`, not `JMP loop`. The debugger (M9) could pass in `AssemblyResult.symbols()`.
- **No macros or local labels.** The ROM (M8) may want them; if so, they'll be added with a decision entry.
- **Next, M5:** the headless runner and the first real program, `hello.tasm`, printing through the debug port.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Assembler / Assembly language:** a program that turns text instructions into machine words / that text language.
- **Disassembler:** the reverse: words back into assembly text.
- **Label / Constant / Symbol:** a name for an address / a name for a number / either one, or a built-in name.
- **Directive:** a line that tells the assembler what to do (`.org`, `.str` …) rather than producing an instruction.
- **Pseudo-instruction:** a convenient spelling the assembler translates into a real instruction (`INC R0` → `ADDI R0, 1`).
- **Expression:** a calculation in the source, like `IO_BASE + KEY`.
- **Lexer / Token:** the part that cuts text into pieces / one piece, like a name or a number.
- **Parser / Grammar / Recursive descent:** the part that recognises the structure of a line / the rules of that structure / a parser built from one function per rule.
- **Two-pass assembler / Forward reference:** first lay out addresses, then encode / using a label before the line that defines it.
- **Listing:** source printed next to its addresses and words.
- **Round trip:** converting there and back and getting exactly what you started with.
- **Canonical form:** the one standard way of writing something.
- **Load address / Image:** where a program goes in memory / the block of words that goes there.
- **Little-endian:** storing a number's least significant byte first.
- **Magic number:** fixed bytes at the start of a file that identify its format (`TRIADA85`).
- **Escape sequence:** a backslash code for a special character inside quotes, like `\n`.
