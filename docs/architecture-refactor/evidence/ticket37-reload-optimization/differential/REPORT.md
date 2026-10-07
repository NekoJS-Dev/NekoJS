# Independent SourceMap identity binary differential

Verdict: **PASS, 1026 exact JSON comparisons, zero counterexamples**. No tracked production, test or documentation files were edited. All writes are confined to this ignored `build/ticket37-source-map-differential/` directory. No Gradle tests/build, Minecraft benchmark, network access or dependency installation ran.

## Frozen inputs

- Old source revision: `2bf28fa77113a38acaecaa34537010a783b73dfc`.
- Old packaged artifact was copied before the parent rebuilt artifacts. Source and snapshot independently matched SHA256 `10730BAE0B5E57765906483B71A64FC439CE174D61DD8A82E3E754856EA0D220`. Snapshot: `old-2bf28fa7.jar`.
- New compiled classes were frozen from `common/build/classes/java/main/com/tkisor/nekojs/core/compiler/NekoSourceMapBuilder*.class` into `new-classes/`. Main class SHA256: `9564DF811CA9D27F1650FF5AC7C0302A7AAF83BCB4AFAD03F5151E6B9AC152B6`; companion class hashes are in `new-class-sha256.txt`.
- Same existing Gson2.11.0jar in both isolated loaders; SHA256 `57928D6E5A6EDEB2ABD3770A8F95BA44DCE45F3B23B7A9DC2B309C581552A78B`.
- Scratch harness SHA256: `82D944C42CD0295942F8614CDF70742C9DC9F31058947712DEA9FECDC6B6F796`.

The harness loads old/new classes in two distinct `URLClassLoader`s whose parent is the platform loader only. It asserts each class really belongs to its respective loader. It invokes only the public static `identity(Path,String,String)` via `getMethod`; no private reflection, privileged access or production instrumentation. Both outputs must have equal String content **and** identical UTF8JSON bytes for every case; first mismatch would write two JSON files and fail.

## Corpus and measured result

Fixed Random seed: `6003110640112454977` (`0x534F555243454D41`). The corpus includes a256case Cartesian edge matrix,768pinned random cases, and2larger line cases: **1026total**,276normalized-exact and750transformed.

Coverage: null/empty authored/generated, null/default/normalized/Unicode paths, CRLF and standalone CR, trailing newline, multiple final empty lines, astral characters measured as UTF16code units, lone surrogate units, tabs/NUL/backslashes/quotes and Unicode line separators, random identical native source, unrelated transformed source, expanded generated lines with original-line clamping,200empty lines and1000native statement lines. Exact map output preserves source content, source/file name and mapping bytes.

Identical label-length/output-length-framed JSON corpus SHA256:
`70CBA66A87FFCC94EF3A976AF3A908E99A37BCFB8FBB8D764FFA79125EB743DE`.

Original/new summed invocation observations:78.7666ms/43.6105ms. These are sequential scratch-harness diagnostics with JVM/loading/warmup effects; **not** a controlled benchmark, a precise source-map speedup, a Minecraft reload result or proof of gate closure.

## Why the removed column clamp is redundant

Normalize null authored/generated to empty strings. In the changed branch the strings are equal, so every ordinary generated line has exactly the corresponding authored line's UTF16length. The loop emits columns0through that length inclusive, hence `min(column, authoredLength)` equals column.

When the exact text ends inLF, `lineCount` includes a final empty generated line while `authoredLineCount` excludes that last empty line and clamps `originalLine` to the preceding line. This is the only clamped exact-source case: the final generated line has length0and emits only column0. The previous authored line's length is nonnegative, including consecutive final newlines, so `min(0, authoredLength)=0` remains identical. Empty text and null-normalized text have one zero-length line and column0. CR is retained in both equal strings' line lengths; astral code points count as the same two UTF16units on both sides. Transformed-source behavior still emits conservative column0and does not use the altered exact branch.

Public `javap -c` disassemblies independently confirm old calls `lineOffset`/`lineLength`/`Math.min` inside each exact-column iteration, while the new snapshot sends that column directly into `add`. Neither bytecode nor math suggests a boundary counterexample.

## Reproduction commands

```powershell
$scope='D:\mcmodDemo\NekoJS-mult\build\ticket37-source-map-differential'
$jdk='C:\Program Files\Java\jdk-25.0.2\bin'
$gson='C:\Users\11515\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson\2.11.0\527175ca6d81050b53bdd4c457a6d6e017626b0e\gson-2.11.0.jar'
& "$jdk\javac.exe" -d "$scope\harness-classes" "$scope\SourceMapDifferential.java"
& "$jdk\java.exe" -cp "$scope\harness-classes" SourceMapDifferential $scope $gson
```

Both javac and java exited0. `console.txt` and `result.txt` retain the actual output; `old-public-bytecode.txt`/`new-public-bytecode.txt` retain public disassembly. Result file SHA256: `6650FFC8C1B9068FC889AF139C086E92AAC39C3F79421467448ABFA8C3CDF9C8`. These are frozen-input differential results; they do not certify future compiled changes or whole-project acceptance.
