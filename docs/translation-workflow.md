# Translation workflow

How to add a new English page under `wiki/en_us/`, and how to bring an existing
one back in sync after the Chinese page changes.

**This document is written to be given to an AI assistant**, which is how the
translations are produced in practice. Give it this file and
[`translation-guide.md`](translation-guide.md), then check its output against
the rules in section 5. It is not tied to any particular assistant or editor:
ChatGPT, GLM, or anything else can follow it. See section 8.

It is also plain enough to follow by hand, if you would rather translate a page
yourself.

Either way, read [`translation-guide.md`](translation-guide.md) first: it holds
the conventions, the glossary, and the terms that carry more than one English
meaning.

| | |
|---|---|
| Last updated | 2026-09-13 |

---

## 1. Find out what needs work

```bash
bash scripts/check-translation-drift
```

The report gives one of five states per page. What to do with each:

| Status | Action |
|---|---|
| `UP TO DATE` | Nothing. Skip it. |
| `STALE` | Update the existing translation. See section 3. |
| `MISSING` | A Chinese page with no translation yet. Translate it only when it is the page you set out to do. Do not work through the whole backlog in one pass; small batches are easier to review. |
| `ORPHANED` | The Chinese source was deleted or renamed. See section 4. |
| `NO METADATA` | The metadata block is absent or malformed. Repair it using section 2. |

Work on a branch, not on `master`. Translations reach this repository as pull
requests from a fork.

---

## 2. Translating a new page

**Read the whole Chinese page before writing anything.** Later sections often
redefine terms used earlier, so translating paragraph by paragraph produces
inconsistencies.

Take the English filename from the page table in `translation-guide.md`
section 9. Do not invent a new name; the sidebar and cross-links depend on it.

Get the source commit:

```bash
git log -1 --format='%h %ad' --date=short -- wiki/<source-page>.md
```

Start the file with the metadata block:

```markdown
<!--
  Translated page. Keep the metadata block below up to date.
  See docs/translation-guide.md for conventions and the term glossary.
-->

> **English** · [中文](../<source-page>)
>
> | | |
> |---|---|
> | Source page | [`wiki/<source-page>.md`](../<source-page>) |
> | Source version | commit `<hash>`, <YYYY-MM-DD> |
> | Translation updated | <today> |
>
> If the Chinese page has changed since that commit, the Chinese page is correct and this one may be out of date.
```

For `_Sidebar.md` only, put the same three fields in an HTML comment instead.
A visible table there would render beside every page in the wiki.

**Links.** Point at the English page when it exists. Otherwise point at the
Chinese page and mark it, so the reader knows before clicking:

```markdown
[Recipe system](Recipe-System)          <!-- English page exists -->
[Recipe system 中文](../配方系统)         <!-- not translated yet -->
```

Then:

1. Update the progress table in `translation-guide.md` section 9.
2. Update `wiki/en_us/_Sidebar.md` to point at the new English page.
3. Re-run the drift script and confirm the page reads `UP TO DATE`.

---

## 3. Updating a stale page

Read exactly what changed upstream:

```bash
git diff <recorded-commit>..HEAD -- wiki/<source-page>.md
```

**Apply only those changes.** Do not re-translate the page from scratch. A full
re-translation produces a large diff that hides the real change, and it discards
wording that has already been reviewed.

Then update both metadata rows: `Source version` to the current commit of the
source page, and `Translation updated` to today.

---

## 4. Orphaned pages

The Chinese source no longer exists. The script cannot tell which of two things
happened, so check before acting:

- **Renamed.** Point the `Source page` row at the new filename. `git log --follow`
  helps find it.
- **Deleted.** The English page should normally be deleted too.

The report prints the commit where the source was last seen, so the content can
be recovered if the deletion was a mistake. **Confirm with the maintainer before
deleting a page.**

---

## 5. Rules that are broken most often

The full set is in `translation-guide.md`. These are the ones that slip:

- **No idioms, metaphors, or phrasal verbs.** Many readers are not native English
  speakers. Write "cancel", not "call off".
- **Split long sentences.** Chinese chains clauses with commas where English
  wants separate sentences.
- **Keep requirement strength.** 必须 = must, 应该 = should, 可以 = can or may,
  不要 = do not.
- **Never translate identifiers.** Anything in backticks or a fenced code block
  stays exactly as written: file paths, configuration keys, command names,
  registry IDs, class names.
- **Do translate Chinese comments inside documentation code samples.** Those are
  prose written for the reader.
- **Preserve structure exactly.** Heading levels, table columns and row order,
  list order, blockquote callouts. The English page should be comparable against
  the Chinese page side by side.
- **Use the recurring table headers verbatim.** `| 方法 | 说明 |` appears 37
  times and must always become `| Method | Description |`.

---

## 6. When the source is ambiguous

Do not guess at behaviour. Translate as literally as possible and leave a marker
on the line above:

```markdown
<!-- TRANSLATION-NOTE: unclear whether this also applies to 1.12.2 -->
```

Collect these and raise them in the pull request as questions. Never leave a
`TRANSLATION-NOTE` in a page presented as finished without saying it is there.

---

## 7. Before opening a pull request

1. Re-run `bash scripts/check-translation-drift` and confirm the pages you
   touched read `UP TO DATE`.
2. Check that every link resolves. Links are bare and relative, so a typo fails
   silently.
3. List any glossary terms you added, and any `TRANSLATION-NOTE` markers you
   left, in the pull request description.

## 8. Using an AI assistant

This is the main way the document is used. Give the assistant this file and
`translation-guide.md`, name the page to work on, and review what comes back
against section 5.

Two rules need checking every time, because assistants get them wrong by
default:

- **Updating a stale page: only the upstream diff should change.** An assistant
  will tend to re-translate the whole page, which produces a large diff that
  hides the real change.
- **Ambiguity must be marked, not resolved.** An assistant will tend to invent a
  plausible meaning. The output should contain a `TRANSLATION-NOTE` marker
  instead, as described in section 6.

The translator is responsible for the result regardless of what produced it.
