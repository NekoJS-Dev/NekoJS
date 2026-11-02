# Recipes and tags

Recipe scripts normally belong to SERVER. Resolve `ServerEvents.recipes` and inspect its Event Object before using operations. Prefer the current instance's `event.recipes.<namespace>.<type>` signatures over examples from another modpack.

Recipe filters are data objects. Keys such as `id`, `type`, `input`, `output`, `and`, `or`, and `not` have recipe-specific meanings; they are unrelated to the authoring query language's `all`, `any`, and `some` predicates.

An item tag (`#namespace:path`) is not an item stack. Check which adapter accepts it. Tags, recipe schemas, resource formats, and reload support can differ by version and loader.

For complete recipe and item/block-tag files with run commands and expected effects, start with [scripting examples](scripting-examples.md).

Verify recipe changes in a running world after a successful server reload. Editor validation does not prove recipe codec acceptance or viewer refresh.
