# Reproducible root-cause family mapping for strict-TS baseline diagnostics (ticket 37).
#
# Parses the ten *-tsc.txt files produced by `run.py check` and assigns diagnostics to
# families from docs/research/2026-10-25-strict-ts-root-causes.md. The rule chain is
# ordered and exclusive: the first matching rule wins, so per-family totals always sum
# to the parsed diagnostic total with no double counting. Linked mechanisms that share
# a leaf cause are folded into that leaf's family (for example the HolderLookup$Provider
# TS2430 rows are Optional-wildcard sites per the RegistryOps.lookup javap verification,
# so they count under F1, not F9). There is NO catch-all rule: the tail family lists the
# known isolated signatures explicitly, and any diagnostic outside every rule lands in
# UNMAPPED and fails the run with a non-zero exit code, so new or shifted error shapes
# are never silently absorbed into a bucket.
#
# Usage:
#   python bench/tsbaseline/classify_diagnostics.py --ide-dir build/tsbaseline-<tag>/ide \
#       [--json out.json] [--markdown out.md]

import argparse
import collections
import glob
import json
import os
import re
import sys

TAIL_SIGNATURES = re.compile(r"Property '(getOrCreate|getOrThrow|leashHolder|fabric_enumCycle|get)'")

RULES = [
    # (family, predicate(message_block, code))
    ("F1-constable-optional", lambda m, c: "describeConstable" in m
        or (c == "TS2430" and ("$HolderLookup$Provider" in m or "'$RegistryAccess'" in m))),
    ("F2-javatime-chrono-cycle", lambda m, c: "Chrono" in m),
    ("F4-fabric-generic-method", lambda m, c: "getAttached" in m or "setAttached" in m),
    ("F6-recipe-constraint", lambda m, c: "Recipe" in m and "Menu" not in m),
    ("F7-consumeeffect-constraint", lambda m, c: "ConsumeEffect" in m),
    ("F5-codec-gettype-invariant", lambda m, c: "codec" in m or "getType" in m
        or "PositionSource" in m or "PoolAlias" in m),
    ("F3-entity-container-overload", lambda m, c: "players" in m or "teleport" in m
        or ("'level'" in m and "ServerPlayer" in m)),
    ("F8-entity-stream-covariance", lambda m, c: "getSelfAndPassengers" in m
        or "getPassengersAndSelf" in m or "$EntityAccess" in m),
    ("F13-getter-method-name-conflict", lambda m, c: c == "TS2426"
        or ("SectionPos" in m and "Vec3i" in m)
        or "ShelfBlockEntity" in m or "'offset'" in m
        or re.search(r"Property '(x|y|z)'", m)),
    ("F10-client-omission-residue", lambda m, c: c in ("TS2724", "TS2552", "TS2304")),
    ("F11-nekojs-core-missing", lambda m, c: c == "TS2307"
        and ("nekojs/core" in m or "@side-only/server/events/recipes" in m)),
    ("F12-neoforge-client-missing", lambda m, c: c == "TS2307"
        and "neoforge/client/extensions" in m),
    ("F14-fabric-pdata-member", lambda m, c: c == "TS2420"),
    # Known isolated signatures enumerated explicitly (menus + scattered single properties);
    # extend this list only after inspecting the new diagnostic text.
    ("tail-isolated-instances", lambda m, c: "$AbstractContainerMenu" in m
        or TAIL_SIGNATURES.search(m)),
]


def parse_blocks(ide_dir):
    entries = []
    for path in sorted(glob.glob(os.path.join(ide_dir, "*-tsc.txt"))):
        name = os.path.basename(path).replace("-tsc.txt", "")
        text = open(path, encoding="utf-8").read()
        for block in re.split(r"(?=^.*error TS\d+: )", text, flags=re.M):
            lines = block.splitlines()
            if not lines:
                continue
            head = re.match(r".*?error (TS\d+): (.*)", lines[0])
            if not head:
                continue
            loc = re.match(r"(.*?)\((\d+),(\d+)\)", lines[0])
            entries.append({
                "entrypoint": name,
                "file": loc.group(1) if loc else lines[0],
                "line": int(loc.group(2)) if loc else 0,
                "column": int(loc.group(3)) if loc else 0,
                "code": head.group(1),
                "message": head.group(2),
                "family": classify(block),
            })
    return entries


def classify(block):
    for family, predicate in RULES:
        if predicate(block, re.search(r"error (TS\d+): ", block).group(1)):
            return family
    return "UNMAPPED"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ide-dir", required=True)
    parser.add_argument("--json")
    parser.add_argument("--markdown")
    args = parser.parse_args()

    entries = parse_blocks(args.ide_dir)
    per_family = collections.Counter(e["family"] for e in entries)
    per_family_node = collections.defaultdict(collections.Counter)
    for e in entries:
        per_family_node[e["family"]][e["entrypoint"]] += 1
    unmapped = [e for e in entries if e["family"] == "UNMAPPED"]

    lines = [f"# Root-cause family mapping for {os.path.normpath(args.ide_dir)}", "",
             f"total diagnostics: {len(entries)}", ""]
    for family, _ in RULES:
        nodes = per_family_node[family]
        server = {k.rsplit("-", 1)[0]: v for k, v in nodes.items() if k.endswith("-server")}
        lines.append(f"- {family}: {per_family[family]}  "
                     f"(server per node: {dict(sorted(server.items()))})")
    print("\n".join(lines))

    if unmapped:
        print(f"\nUNMAPPED diagnostics: {len(unmapped)}", file=sys.stderr)
        for e in unmapped[:10]:
            print(f"  {e['entrypoint']} {e['file']}({e['line']}): {e['message']}", file=sys.stderr)
        sys.exit(1)

    if args.json:
        payload = {
            "ideDir": os.path.normpath(args.ide_dir),
            "total": len(entries),
            "families": {family: {"total": per_family[family],
                                  "perEntrypoint": dict(sorted(per_family_node[family].items()))}
                         for family, _ in RULES},
            "diagnostics": entries,
        }
        with open(args.json, "w", encoding="utf-8", newline="\n") as f:
            json.dump(payload, f, indent=2, ensure_ascii=False)
            f.write("\n")
    if args.markdown:
        with open(args.markdown, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
