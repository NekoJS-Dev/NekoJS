"""Failure-path checks for the isolated active-node matrix driver."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("active_sources", Path(__file__).with_name("verify-active-node-sources.py"))
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


class ActiveNodeSourceDriverTest(unittest.TestCase):
    def run_driver(self, root, fail_check=None, fail_restore=False):
        def gradle(command, **kwargs):
            if command[1] == "switchVersion":
                active = command[2].split("=", 1)[1]
                if active == "original" and fail_restore:
                    return SimpleNamespace(returncode=1)
                (root / "stonecutter.gradle.kts").write_text(f'stonecutter active "{active}"\n', encoding="utf-8")
                (root / "src/original.txt").write_bytes(b"normalized by switching\n")
            elif command[1] == "-I":
                if fail_check is not None:
                    raise fail_check
                report = root / "versions/node/build/node-source-selection.json"
                report.parent.mkdir(parents=True, exist_ok=True)
                report.write_text(json.dumps({"node": "node", "checks": [], "failures": []}), encoding="utf-8")
            return SimpleNamespace(returncode=0)

        with patch.object(driver, "ROOT", root), patch.object(driver, "NODES", ("node",)), \
                patch.object(driver.subprocess, "run", side_effect=gradle), \
                patch.object(sys, "argv", ["matrix", "--target", str(root / "reports")]):
            driver.main()

    def checkout(self, root):
        (root / "src").mkdir()
        (root / "src/original.txt").write_bytes(b"original bytes\r\n")
        (root / "stonecutter.gradle.kts").write_text('stonecutter active "original"\n', encoding="utf-8")

    def test_success_restores_shared_bytes_and_removes_owned_probes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.checkout(root)
            self.run_driver(root)
            self.assertEqual(b"original bytes\r\n", (root / "src/original.txt").read_bytes())
            self.assertIn('active "original"', (root / "stonecutter.gradle.kts").read_text())
            self.assertFalse(list(root.rglob("NodeSourceOverride*Probe.java")))
            self.assertFalse(list(root.rglob("*-override.txt")))
            self.assertFalse((root / "src/main/resources/node-source-selection").exists())
            self.assertEqual(1, len(json.loads((root / "reports/summary.json").read_text())))

    def test_operation_error_survives_failed_restore_and_stale_summary_is_cleared(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.checkout(root)
            (root / "reports").mkdir()
            (root / "reports/summary.json").write_text('[{"active":"stale"}]')
            failure = RuntimeError("original probe failure")
            with self.assertRaises(RuntimeError) as raised:
                self.run_driver(root, fail_check=failure, fail_restore=True)
            self.assertIs(failure, raised.exception)
            self.assertTrue(any("Restoration failed" in note for note in failure.__notes__))
            self.assertEqual(b"original bytes\r\n", (root / "src/original.txt").read_bytes())
            self.assertFalse(list(root.rglob("NodeSourceOverride*Probe.java")))
            self.assertEqual([], json.loads((root / "reports/summary.json").read_text()))

    def test_existing_probe_path_is_never_overwritten_or_removed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.checkout(root)
            existing = root / "src/main/java/com/tkisor/nekojs/buildfixture/NodeSourceOverrideMainProbe.java"
            existing.parent.mkdir(parents=True)
            existing.write_bytes(b"user-owned fixture")
            with self.assertRaises(FileExistsError):
                self.run_driver(root)
            self.assertEqual(b"user-owned fixture", existing.read_bytes())
            self.assertEqual(b"original bytes\r\n", (root / "src/original.txt").read_bytes())


if __name__ == "__main__":
    unittest.main()
