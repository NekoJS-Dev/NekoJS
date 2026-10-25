"""Verify the frozen artifact bindings, diagnostics delta, and matrix reports without launching games."""

from collections import Counter
from hashlib import sha256
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from zipfile import ZipFile


root = Path(__file__).resolve().parent
manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
archive = root / manifest["archive"]
assert sha256(archive.read_bytes()).hexdigest() == manifest["archiveSha256"]
for name, digest in manifest["dataFiles"].items():
    assert sha256((root / name).read_bytes()).hexdigest() == digest, name


def diagnostics(zipped, prefix):
    identities = Counter()
    entrypoints = Counter()
    for name in zipped.namelist():
        if not name.startswith(prefix) or not name.endswith("-tsc.txt"):
            continue
        entry = Path(name).name.removesuffix("-tsc.txt")
        for line in zipped.read(name).decode("utf-8").splitlines():
            match = re.match(r"(.*?)\(\d+,\d+\): error (TS\d+): (.*)", line)
            if match:
                path, code, message = match.groups()
                path = path.replace("\\", "/").split(".neko_probe/typescript/", 1)[-1]
                identities[(entry, path, code, message)] += 1
                entrypoints[entry] += 1
    assert len(entrypoints) == 10
    return identities, entrypoints


with ZipFile(archive) as zipped:
    for entry in manifest["entries"]:
        assert sha256(zipped.read(entry["path"])).hexdigest() == entry["sha256"], entry["path"]
    before, _ = diagnostics(zipped, "baseline/ide/")
    after, entrypoints = diagnostics(zipped, "current/ide/")
    assert sum(before.values()) == 1503
    assert sum(after.values()) == 1409
    assert sum((before - after).values()) == 94
    assert not after - before
    counts = Counter()
    suites = 0
    for name in zipped.namelist():
        if name.startswith("matrix/") and name.endswith(".xml"):
            suite = ET.fromstring(zipped.read(name))
            suites += 1
            counts.update({key: int(suite.get(key, "0"))
                           for key in ("tests", "failures", "errors", "skipped")})
    assert suites == 917
    assert counts == Counter(tests=5183, failures=0, errors=0, skipped=271)
    mapping = json.loads(zipped.read("current/root-cause-mapping.json"))
    assert mapping["total"] == 1409 == sum(f["total"] for f in mapping["families"].values())
    assert mapping["families"]["F4-fabric-generic-method"]["total"] == 0
    assert all(d["family"] != "UNMAPPED" for d in mapping["diagnostics"])

runs = json.loads((root / "run-summary.json").read_text(encoding="utf-8"))
assert len(runs) == 5
assert all(r["exitCode"] == 0 and not r["killed"] and r["stopSent"] and r["sessionLockReleased"] for r in runs)
binding = json.loads((root / "source-artifact-binding.json").read_text(encoding="utf-8"))
assert all(n["reflectorClassMatchesCompiled"] for n in binding["nodes"])
summary = json.loads((root / "ide-check-summary.json").read_text(encoding="utf-8"))
assert all(entry["exitCode"] == 2 and entry["diagnosticCount"] == entrypoints[node["node"] + "-" + entry["side"]]
           for node in summary["nodes"] for entry in node["typescript"])
print("PASS: archive, five installed runs, 5183 matrix tests, and 1503 -> 1409 (-94/+0) diagnostics")
