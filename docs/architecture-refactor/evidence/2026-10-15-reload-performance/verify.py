import hashlib
import json
import sys
from pathlib import Path
from zipfile import ZipFile


root = Path(__file__).resolve().parent
summary = json.loads((root / "summary.json").read_text(encoding="utf-8"))
archive_path = root / summary["archive"]["file"]
assert summary["sourceRevision"] == "8ed398dd2a4877bf1382000e1b811eb2d667c381"
assert summary["samplerSha256"] == "1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4"
assert hashlib.sha256(archive_path.read_bytes()).hexdigest() == summary["archive"]["sha256"]
assert archive_path.stat().st_size == summary["archive"]["bytes"]
assert [group["name"] for group in summary["groups"]] == ["reload-1", "reload-2", "startup"]
with ZipFile(archive_path) as archive:
    assert sorted(archive.namelist()) == sorted(summary["entries"])
    for name, expected_hash in summary["entries"].items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == expected_hash, name
    for group in summary["groups"]:
        raw = archive.read(group["samplesEntry"]).decode("utf-8-sig")
        samples = [json.loads(line) for line in raw.splitlines() if line.strip()]
        assert samples == group["samples"]
        direct_exit = json.loads(archive.read(group["exitEntry"]).decode("utf-8-sig"))
        assert direct_exit["directExit"] == 0
        formal = [sample for sample in samples if sample["kind"] == "formal"]
        assert len(formal) == 5
        is_startup = group["mode"] == "startup"
        assert len(samples) == (7 if is_startup else 5)
        assert [sample["index"] for sample in formal] == ([3, 4, 5, 6, 7] if is_startup else [1, 2, 3, 4, 5])
        for sample in samples:
            assert sample["rev"] == "8ed398dd" and sample["node"] == "26.1.2"
            assert sample["stop_channel"] == "rcon"
            assert sample["killed"] is False and sample["forced_kill"] is False
            assert not sample.get("timeout", False)
            assert sample["wall_done_ms"] is not None and sample["done_s"] is not None
            if not is_startup:
                assert "PERF02-SERVER tick-bench load" in sample["marker_line"]
                assert "phase=COMMIT - no errors." in sample["rcon_response"]
        metric = "wall_done_ms" if is_startup else "marker_ms"
        assert group["formalValuesMs"] == [sample[metric] for sample in formal]
        assert group["meanMs"] == sum(group["formalValuesMs"]) / 5
        assert group["thresholdMs"] == (41173 if is_startup else 285.3)
        assert group["acceptanceVerdict"] == ("PASS" if group["meanMs"] <= group["thresholdMs"] else "FAIL")
    assert summary["overall"] == ("PASS" if all(group["acceptanceVerdict"] == "PASS" for group in summary["groups"]) else "FAIL")
print(f"PASS: immutable input/sample/exit evidence; measured performance {summary['overall']}")

if "--candidate" in sys.argv:
    candidate = json.loads((root / "candidate.json").read_text(encoding="utf-8"))
    assert candidate["sourceRevision"] == "e473479af6dcb0f9483b36a0d93207721904025f"
    assert candidate["baseRevision"] == summary["sourceRevision"]
    assert candidate["samplerSha256"] == summary["samplerSha256"]
    candidate_archive = root / candidate["archive"]["file"]
    assert hashlib.sha256(candidate_archive.read_bytes()).hexdigest() == candidate["archive"]["sha256"]
    assert candidate_archive.stat().st_size == candidate["archive"]["bytes"]
    with ZipFile(archive_path) as baseline_archive, ZipFile(candidate_archive) as archive:
        assert sorted(archive.namelist()) == sorted(candidate["entries"])
        for name, expected_hash in candidate["entries"].items():
            assert hashlib.sha256(archive.read(name)).hexdigest() == expected_hash, name
        binding = json.loads(archive.read("runner/binding.json").decode("utf-8-sig"))
        baseline_binding = json.loads(baseline_archive.read("runner/binding.json").decode("utf-8-sig"))
        assert binding["revision"] == candidate["sourceRevision"]
        assert binding["samplerSha256"].lower() == candidate["samplerSha256"]
        assert binding["fixtures"] == baseline_binding["fixtures"]
        assert binding["javaRoot"] == baseline_binding["javaRoot"]
        assert binding["reloadGroups"] == 2 and binding["samplesPerGroup"] == 5
        assert binding["reloadThresholdMs"] == 285.3
        assert binding["startupWarmup"] == 2 and binding["startupSamples"] == 5
        assert binding["startupThresholdMs"] == 41173
        before = json.loads(archive.read("runner/compiled-classes-before.json").decode("utf-8-sig"))
        after = json.loads(archive.read("runner/compiled-classes-after.json").decode("utf-8"))
        assert after and len(after) == candidate["compiledClassCount"]
        assert {name: value.lower() for name, value in before.items()} == after
        for name, expected_hash in candidate["sourceFiles"].items():
            assert hashlib.sha256(archive.read(f"source/{name}")).hexdigest() == expected_hash
        assert [group["name"] for group in candidate["groups"]] == ["reload-1", "reload-2", "startup"]
        for group in candidate["groups"]:
            samples = [json.loads(line) for line in archive.read(group["samplesEntry"]).decode("utf-8-sig").splitlines() if line.strip()]
            assert samples == group["samples"]
            assert json.loads(archive.read(group["exitEntry"]).decode("utf-8-sig"))["directExit"] == 0
            startup = group["mode"] == "startup"
            assert group["mode"] == ("startup" if group["name"] == "startup" else "reload")
            assert len(samples) == (7 if startup else 5)
            formal = [sample for sample in samples if sample["kind"] == "formal"]
            assert len(formal) == 5
            assert [sample["index"] for sample in formal] == ([3, 4, 5, 6, 7] if startup else [1, 2, 3, 4, 5])
            for sample in samples:
                assert sample["rev"] == candidate["sourceRevision"][:8] and sample["node"] == "26.1.2"
                assert sample["stop_channel"] == "rcon" and sample["killed"] is False and sample["forced_kill"] is False
                assert not sample.get("timeout", False)
                assert sample["wall_done_ms"] is not None and sample["done_s"] is not None
                if not startup:
                    assert "PERF02-SERVER tick-bench load" in sample["marker_line"]
                    assert "phase=COMMIT - no errors." in sample["rcon_response"]
            if startup:
                assert [sample["index"] for sample in samples if sample["kind"] == "warmup"] == [1, 2]
            metric = "wall_done_ms" if startup else "marker_ms"
            assert group["formalValuesMs"] == [sample[metric] for sample in formal]
            assert group["meanMs"] == sum(group["formalValuesMs"]) / 5
            assert group["thresholdMs"] == (41173 if startup else 285.3)
            assert group["acceptanceVerdict"] == ("PASS" if group["meanMs"] <= group["thresholdMs"] else "FAIL")
        tests = json.loads(archive.read("checks/tests.json").decode("utf-8"))
        assert tests == candidate["tests"]
        assert all(record["failures"] == 0 and record["errors"] == 0 for record in tests.values())
        assert tests["common/build/test-results/test"]["tests"] == 2314
        assert tests["common/build/test-results/test"]["skipped"] == 4
        assert "BUILD SUCCESSFUL" in archive.read("checks/full-checks.log").decode("utf-8-sig")
        assert "All 1 required tests passed" in archive.read("checks/gametest.log").decode("utf-8-sig")
    diagnostics = json.loads((root / "diagnostics.json").read_text(encoding="utf-8"))
    diagnostic_archive = root / diagnostics["archive"]["file"]
    assert hashlib.sha256(diagnostic_archive.read_bytes()).hexdigest() == diagnostics["archive"]["sha256"]
    assert diagnostic_archive.stat().st_size == diagnostics["archive"]["bytes"]
    with ZipFile(diagnostic_archive) as archive:
        assert sorted(archive.namelist()) == sorted(diagnostics["entries"])
        for name, expected_hash in diagnostics["entries"].items():
            assert hashlib.sha256(archive.read(name)).hexdigest() == expected_hash, name
    expected = "PASS" if all(group["acceptanceVerdict"] == "PASS" for group in candidate["groups"]) else "FAIL"
    assert candidate["overall"] == expected
    assert candidate["overall"] == "PASS", "Candidate performance failed the original thresholds"
    print("PASS: frozen candidate evidence, regression checks and original performance thresholds")
