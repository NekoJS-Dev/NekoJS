---
name: nekojs-translate
description: Translate or update NekoJS wiki pages from Simplified Chinese to English. Use when asked to translate a NekoJS wiki page, update stale English translations, sync translations after upstream changes, or act on the translation drift report.
---

# NekoJS wiki translation

The procedure lives in **`docs/translation-workflow.md`**. Read it in full and
follow it. This file only exists so the workflow is discovered automatically.

Also read **`docs/translation-guide.md`** before translating anything. It holds
the conventions, the glossary, and the terms that carry more than one English
meaning. Read it rather than recalling it — `降级` means "lower" in compiler
text and "degrade" in platform text, and choosing wrong inverts the sentence.

Two rules are worth repeating here, because they are the ones an assistant gets
wrong by default:

- **Updating a stale page: apply only the upstream diff.** Do not re-translate
  the page. A full re-translation hides the real change and discards wording
  that has already been reviewed.
- **Never guess at behaviour.** If the Chinese is ambiguous, translate literally
  and leave a `TRANSLATION-NOTE` comment, then report it.

Do not commit or push unless asked.
