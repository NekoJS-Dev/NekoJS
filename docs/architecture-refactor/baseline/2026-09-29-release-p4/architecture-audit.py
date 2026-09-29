#!/usr/bin/env python3
"""Ticket 34 AC7 physical-architecture audit (read-only evidence generator).

Automates the production source/resource inventory required by
docs/architecture-refactor/implementation-handoff.md section 2:

  R1  common/src/main carries no Minecraft/loader imports (Graal allowed).
  R2  common-api-processor/src/main is MC/loader-free and independent.
  R3  root src/main and versions/<node>/src/main hold no duplicated business
      logic: identical content between a root file and a node file, or the
      same content shipped by two different nodes, is flagged.
  R4  src/fabric holds no NeoForge-named sources.

Output is a per-file inventory plus rule verdicts; anything nonconforming is
listed as an exception candidate for the per-file exception table (owner and
retention reason are assigned by the report, not by this script). Whole-file
overrides in versions/<node> are allowed by the handoff rules and are counted
separately, never silently merged.

Usage: python architecture-audit.py [--repo <path>] (writes inventory to stdout)
"""

import argparse
import hashlib
import re
import sys
from pathlib import Path

MC_LOADER_IMPORT_RE = re.compile(
    r'^\s*import\s+(?:static\s+)?(net\.minecraft\.|com\.mojang\.|net\.neoforged\.'
    r'|net\.fabricmc\.|net\.forge\.\S*minecraft|org\.spongepowered\.|net\.minecraftforge\.)',
    re.M)
GRAAL_IMPORT_RE = re.compile(r'^\s*import\s+(?:static\s+)?(org\.graalvm\.|com\.oracle\.truffle\.)', re.M)
# Only actual NeoForge loader imports count as "NeoForge source"; textual
# mentions in comments or Stonecutter guards are legitimate references.
NEOFORGE_NAME_RE = re.compile(
    r'^\s*import\s+(?:static\s+)?(net\.neoforged\.|net\.minecraftforge\.)', re.M)

PRODUCTION_ROOTS = [
    ("common/src/main", "common engine/api (MC/loader-free rule R1)"),
    ("common/src/main/resources", None),  # covered by the tree walk below
    ("common-api-processor/src/main", "processor (independent, R2)"),
    ("src/main", "shared MC-facing tree"),
    ("src/fabric", "Fabric raw loader root (R4)"),
]

FABRIC_NODES = {"26.1.2-fabric", "26.2.0-fabric"}
NODES = ["1.21.1", "26.1.2", "26.2.0", "26.1.2-fabric", "26.2.0-fabric"]


def is_source_or_resource(path: Path) -> bool:
    return path.suffix in {".java", ".json", ".cfg", ".toml", ".properties",
                           ".accesswidener", ".js", ".ts", ".d.ts", ".txt", ".md"} or path.suffix == ""


def walk_files(root: Path):
    if not root.is_dir():
        return
    for p in sorted(root.rglob("*")):
        if p.is_file() and is_source_or_resource(p):
            yield p


def file_sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:16]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=str(Path(__file__).resolve().parents[4]))
    args = ap.parse_args()
    repo = Path(args.repo)

    areas = {}
    # Shared roots.
    for rel, label in [("common/src/main", "common/src/main"),
                       ("common-api-processor/src/main", "common-api-processor/src/main"),
                       ("src/main", "src/main (shared MC-facing)"),
                       ("src/fabric", "src/fabric (raw loader root)")]:
        files = list(walk_files(repo / rel))
        areas[rel] = {"label": label, "files": files}

    # Node roots (26.2.0-fabric intentionally has no own src; record the fact).
    for node in NODES:
        rel = "versions/%s/src/main" % node
        areas[rel] = {"label": rel, "files": list(walk_files(repo / rel))}

    print("# production source/resource inventory (ticket 34 AC7)")
    print("# repo: %s" % repo)
    total = 0
    for rel in sorted(areas):
        n = len(areas[rel]["files"])
        total += n
        print("AREA %-38s files=%d" % (rel, n))
    print("AREA TOTAL files=%d" % total)
    print()
    # Full per-area listing: the inventory must cover every production file.
    print("## FULL FILE LISTING")
    for rel in sorted(areas):
        print("# area %s" % rel)
        for f in areas[rel]["files"]:
            print("FILE %s %s" % (rel, f.relative_to(repo / rel).as_posix()))
    print()

    # R1/R2: MC/loader imports in common and processor.
    for rel in ["common/src/main", "common-api-processor/src/main"]:
        print("## RULE %s MC/loader imports" % rel)
        hits = 0
        for f in areas[rel]["files"]:
            if f.suffix != ".java":
                continue
            text = f.read_text(encoding="utf-8", errors="replace")
            m = MC_LOADER_IMPORT_RE.search(text)
            if m:
                hits += 1
                print("VIOLATION file=%s import=%s" % (f.relative_to(repo).as_posix(), m.group(0).strip()))
            if GRAAL_IMPORT_RE.search(text):
                print("graal-allowed file=%s" % f.relative_to(repo).as_posix())
        print("verdict=%s violations=%d" % ("PASS" if hits == 0 else "FAIL", hits))
        print()

    # R4: src/fabric must not carry NeoForge-named sources.
    print("## RULE src/fabric NeoForge-named sources")
    hits = 0
    for f in areas["src/fabric"]["files"]:
        if f.suffix != ".java":
            continue
        text = f.read_text(encoding="utf-8", errors="replace")
        if NEOFORGE_NAME_RE.search(text):
            hits += 1
            print("VIOLATION file=%s" % f.relative_to(repo).as_posix())
    print("verdict=%s violations=%d" % ("PASS" if hits == 0 else "FAIL", hits))
    print()

    # R3: duplicate business logic between root src/main and nodes, and across nodes.
    print("## RULE duplicate content root src/main <-> versions/<node>/src/main")
    root_hash = {}
    for f in areas["src/main"]["files"]:
        if f.suffix in {".java", ".json", ".cfg", ".toml", ".properties"}:
            root_hash.setdefault(file_sha(f), []).append(f.relative_to(repo).as_posix())
    dup_root = 0
    node_files = {}
    for node in NODES:
        rel = "versions/%s/src/main" % node
        for f in areas[rel]["files"]:
            if f.suffix in {".java", ".json", ".cfg", ".toml", ".properties"}:
                node_files.setdefault(file_sha(f), []).append((node, f.relative_to(repo).as_posix()))
    for h, nf in sorted(node_files.items()):
        if h in root_hash:
            dup_root += len(nf)
            for node, path in nf:
                print("VIOLATION node=%s file=%s identical to root %s"
                      % (node, path, ",".join(root_hash[h])))
    print("verdict=%s identical-root-copies=%d" % ("PASS" if dup_root == 0 else "FAIL", dup_root))
    print()

    print("## RULE identical content shared by two different nodes")
    cross = 0
    for h, nf in sorted(node_files.items()):
        nodes = {n for n, _ in nf}
        if len(nodes) > 1:
            cross += 1
            print("FLAG nodes=%s files=%s" % (",".join(sorted(nodes)),
                                              ";".join(p for _, p in nf)))
    print("cross-node-identical-groups=%d (26.1.2-fabric <-> 26.2.0-fabric expected: shared raw root policy)"
          % cross)
    print()

    # Whole-file overrides: same relative path below the source root in both
    # src/main and versions/<node>/src/main. Allowed by handoff; inventoried.
    print("## INVENTORY whole-file overrides (allowed, inventoried)")
    root_rel = {f.relative_to(repo / "src/main").as_posix() for f in areas["src/main"]["files"]}
    overrides = 0
    for node in NODES:
        rel = "versions/%s/src/main" % node
        for f in areas[rel]["files"]:
            r = f.relative_to(repo / rel).as_posix()
            if r in root_rel:
                overrides += 1
                print("OVERRIDE node=%s file=%s" % (node, r))
    print("overrides=%d" % overrides)
    return 0


if __name__ == "__main__":
    sys.exit(main())
