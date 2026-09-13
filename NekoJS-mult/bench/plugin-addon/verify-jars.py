# Ticket 08 (AC1) — verify the five production jars contain no trace of the test-only external
# addon fixture: no class, no resource, no loader metadata. Run after `gradlew <node>:jar`.
#
# Usage: python bench/plugin-addon/verify-jars.py [--json <out.json>]
import argparse
import json
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]

# Every pattern that must NOT appear anywhere in a production jar entry list or its contents.
FORBIDDEN_ENTRY_PATTERNS = [
    re.compile(r"^com/example/nekojs08/"),
    re.compile(r"^Addon", re.IGNORECASE),
    re.compile(r"nekojs08addon", re.IGNORECASE),
    re.compile(r"nekojs08"),
]

# Text markers that must not appear in any DECLARED metadata file (the platform's own
# neoforge.mods.toml / fabric.mod.json are expected, but they must not mention the addon).
FORBIDDEN_TEXT = ["nekojs08addon", "com.example.nekojs08", "NekoJS Ticket 08 Test Addon"]


def production_jars():
    jars = []
    for path in sorted(ROOT.glob("versions/*/build/libs/*.jar")):
        name = path.name
        if "-sources" in name or "-javadoc" in name or "-dev" in name or "-plain" in name:
            continue
        jars.append(path)
    return jars


def inspect(jar):
    result = {"jar": str(jar.relative_to(ROOT)).replace("\\", "/"), "entries": 0,
              "violations": [], "metadata": []}
    with zipfile.ZipFile(jar) as zf:
        names = zf.namelist()
        result["entries"] = len(names)
        for name in names:
            for pattern in FORBIDDEN_ENTRY_PATTERNS:
                if pattern.search(name):
                    result["violations"].append(f"entry matches {pattern.pattern}: {name}")
        for meta in ("META-INF/neoforge.mods.toml", "fabric.mod.json"):
            if meta not in names:
                continue
            text = zf.read(meta).decode("utf-8", errors="replace")
            result["metadata"].append(meta)
            for marker in FORBIDDEN_TEXT:
                if marker in text:
                    result["violations"].append(f"{meta} mentions {marker!r}")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", default=None)
    args = parser.parse_args()

    jars = production_jars()
    if len(jars) != 5:
        print(f"expected 5 production jars, found {len(jars)}:", file=sys.stderr)
        for jar in jars:
            print(f"  {jar}", file=sys.stderr)
        return 1

    report = {"jars": [], "ok": True}
    for jar in jars:
        info = inspect(jar)
        report["jars"].append(info)
        status = "OK" if not info["violations"] else "VIOLATION"
        print(f"[{status}] {info['jar']} entries={info['entries']} metadata={info['metadata']}")
        for violation in info["violations"]:
            print(f"    {violation}")
            report["ok"] = False

    if args.json:
        pathlib.Path(args.json).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("PASS: no addon class, resource or metadata in any production jar" if report["ok"]
          else "FAIL: addon artifacts leaked into a production jar")
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
