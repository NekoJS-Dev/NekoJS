# Translation workflow

Use this workflow when adding a Wiki topic or synchronizing a language pair. Read [translation-guide.md](translation-guide.md) first. The page mapping and reading order come from [wiki-pages.json](wiki-pages.json).

## 1. Find the canonical pair

Run:

```bash
node scripts/wiki-docs.mjs check
```

`MISSING`, `ORPHANED`, language-link, structure or anchor problems require correction. `STALE OR UNSTAMPED` means the Chinese body differs from its reviewed digest or has no digest; a translation-digest failure means the English file changed after stamping.

Read `wiki/<topic>_cn.md` and `wiki/<topic>_us.md`, not a legacy migration page. Read the full affected source and its changes before writing. Keep a routine update limited to the changed sections and necessary cross-links.

## 2. Translate

Preserve heading hierarchy, table and example order. Translate prose naturally and keep identifiers and executable behavior unchanged. Use current-language topic links, with the language switch linking to the same topic in the other language. Keep shared `#wiki-section-N` references.

For a new topic, add its bilingual titles and section to the manifest once, then create both canonical files. The manifest array sets the reading order. New entries use lowercase kebab-case IDs; `_cn` and `_us` are the selected filename suffixes.

When behavior is uncertain, check implementation and tests and report the uncertainty. Resolve a confirmed source error in both languages together. A complete translation is not a substitute for verifying the product behavior it describes.

## 3. Generate navigation and anchors

```bash
node scripts/wiki-docs.mjs sync
node scripts/wiki-docs.mjs check --structure-only --strict
```

Sync derives the shared Home/sidebar, per-page language header, matched positional anchors and previous/next links. It does not translate text or certify a translation. After inserting or removing headings, review cross-page section references as well as running sync.

Completion criterion: both files exist, structure matches, language links and local targets resolve, and generated navigation matches the manifest.

## 4. Review and stamp

Compare meaning, platform limitations, commands, configuration and example behavior. In Python examples verify that the special `from nekojs import *` or unaliased named import and `/nekojs probe python` steps remain explicit.

After reviewing the selected pair:

```bash
node scripts/wiki-docs.mjs stamp python-scripts
node scripts/wiki-docs.mjs check --strict
```

Stamp records source and translation body digests. It must follow translation review; using it solely to clear a drift report defeats the check. Use `stamp --all` only after reviewing the entire bilingual set. Report maintainer acceptance separately; draft review by an assistant is not a human sign-off.

## 5. Deliver and publish

```bash
git diff --check
node --test scripts/wiki-docs.test.mjs
node scripts/wiki-docs.mjs check --strict
```

Review the final diff for language, scope and identifiers. Preserve unrelated working changes. Avoid creating branches or commits unless the maintainer asks for them.

Publishing to GitHub Wiki is a separate action. Verify current-topic language switching, shared sidebar, heading anchors and migration links at the actual published URL. Local file/anchor checks do not prove remote rendering or publishing success.

## Legacy pages

Existing Chinese filenames and the `en_us` entries contain migration links. Update the canonical pair, not these entry stubs. Remove a compatibility entry only when the maintainer explicitly approves its removal. The fixed GitHub Wiki `Home.md` and `_Sidebar.md` remain shared infrastructure, not extra content translations.
