# Wiki pair and translation checks

The existing `check-translation-drift` command delegates to the shared manifest-driven tool. Node.js is required; no npm dependencies are needed.

```bash
node scripts/wiki-docs.mjs check
bash scripts/check-translation-drift --strict
```

## Checks

The canonical topics are declared once in [wiki-pages.json](../docs/wiki-pages.json). Each topic has `wiki/<id>_cn.md` and `wiki/<id>_us.md`.

The checker reports missing or orphaned pairs, duplicate IDs, invalid groups, wrong language links, missing files/anchors, unmatched heading/table/code structure, generated-navigation drift and changed body digests. Body digests inspect the current working files, including uncommitted edits. They exclude generated language headers, anchors and previous/next navigation.

Default dashboard mode reports problems without a failing exit code. `--strict` returns 1 for any reported problem, including a missing translation. `--quiet` suppresses a successful summary. `--structure-only` checks structure without digest comparison and is useful before stamping a reviewed pair.

## Maintenance

```bash
node scripts/wiki-docs.mjs sync
node scripts/wiki-docs.mjs check --structure-only --strict
node scripts/wiki-docs.mjs stamp python-scripts
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
```

Sync regenerates anchors and navigation. Stamp records the current reviewed source/translation bodies; use it after semantic review, not as an automatic repair for drift. `stamp --all` is reserved for a full-set review.

[Translation guide](../docs/translation-guide.md) defines language and terminology; [translation workflow](../docs/translation-workflow.md) defines the update steps. Automated checks do not establish semantic correctness, human acceptance or successful GitHub Wiki publication.
