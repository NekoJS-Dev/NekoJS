# check-translation-drift

Translation drift detection (dashboard mode). Run from the repository root:

```bash
scripts/check-translation-drift
# or
bash scripts/check-translation-drift
```

## Purpose

The English pages under `wiki/en_us/` are translations of the Chinese pages in
`wiki/`. When a Chinese page is edited, its English translation silently
becomes out of date. This script finds those cases.

Each English page records the exact commit of the source page it was
translated from, in a metadata table near the top of the file:

```markdown
> | Source page    | [`wiki/Home.md`](../Home)     |
> | Source version | commit `101fd07a`, 2026-08-20 |
```

The script reads that table and asks git whether the source page has changed
since that commit.

## Output

| Status | Meaning |
|---|---|
| `UP TO DATE` | The Chinese source has not changed since the translation was made. |
| `STALE` | The Chinese source has changed. The commits are listed so you can see what changed. The translation needs review. |
| `NO METADATA` | An English page has no metadata table, or the table names a source page or commit that does not exist. |
| `MISSING` | A Chinese page that has no English translation yet. |
| `ORPHANED` | An English page whose Chinese source was deleted or renamed. The commit where the source was last seen is printed, so its content can be recovered if needed. |

Example:

```text
Translation drift report
========================

UP TO DATE   en_us/Home.md                    (source wiki/Home.md unchanged since 101fd07a)
STALE        en_us/Commands.md                (source wiki/命令.md changed in 2 commit(s) since 98953755)
                 101fd07a docs(wiki): fix probe doc drift
                 b76875e4 docs: 全面同步 wiki 与代码实现
MISSING      脚本基础.md

------------------------------------------------------------
up to date: 1   stale: 1   no metadata: 0   not translated: 21
```

## Options

| Option | Effect |
|---|---|
| *(none)* | Dashboard mode. Prints everything and always exits 0. |
| `--strict` | Exits 1 if any page is stale or has bad metadata. Intended for a future CI gate. |
| `--quiet` | Prints only problems. Hides `UP TO DATE` lines. |
| `--help` | Prints the header comment from the script. |

Following the convention of `check-platform-drift`, the default mode never
fails. Whether this becomes a CI gate is a separate decision.

## Updating a stale translation

1. Read what changed in the Chinese source:

   ```bash
   git diff <recorded-commit>..HEAD -- wiki/<source-page>
   ```

2. Apply the same change to the English page.

3. Update the `Source version` row to the current commit of the source page:

   ```bash
   git log -1 --format='%h %ad' --date=short -- wiki/<source-page>
   ```

## Related

- `docs/translation-guide.md` — translation conventions, the term glossary,
  and the list of terms that have more than one English meaning.

## Note

This README is written in English because the script supports the English
translation effort.
