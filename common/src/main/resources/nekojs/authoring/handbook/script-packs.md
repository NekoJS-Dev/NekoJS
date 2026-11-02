# Script packs

A versioned script pack owns its manifest, entries, Script Types and module context. Inspect its actual manifest before editing; the flat `nekojs/*_scripts` directories are not a substitute for a pack-local entry layout.

Each pack has an independent context. `global` and `shared` follow the runtime's explicit contracts; imports and file paths must resolve from the pack's allowed sources. Do not assume one pack's top-level JavaScript variables are visible in another.

Signed and downloaded server-cache packs are immutable authoring inputs. Make changes in the maintained source pack, validate it, and follow its normal packaging/signing process. Editor metadata is part of signed content when included before signing.

Use the existing `/nekojs packs` commands to inspect the installed layout. Read the installed command help for exact subcommands before proposing an operation; API declaration queries do not describe the command tree.
