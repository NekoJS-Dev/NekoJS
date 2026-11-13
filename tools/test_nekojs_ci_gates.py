"""Checks source-root probe arguments and failure reporting without launching Gradle."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("ci_gates", Path(__file__).with_name("nekojs-ci-gates.py"))
gates = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gates)


class SourceRootsInvocationTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="nekojs repo ")
        self.addCleanup(temporary.cleanup)
        self.repo = Path(temporary.name)
        self.node = "26.2.0-fabric"

    def write_report(self):
        target = self.repo / "versions" / self.node / "build/nekojs-gates" / (
            "source-roots-%s.json" % self.node)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps({"node": self.node, "java": ["src/main/java"],
                                      "resources": ["src/main/resources"]}), encoding="utf-8")

    def run_probe(self, platform, returncode=0, error=None):
        output = io.StringIO()
        completed = subprocess.CompletedProcess([], returncode, stdout="Gradle output", stderr="Gradle error")
        with patch.object(gates.sys, "platform", platform), \
                patch.object(gates.subprocess, "run", return_value=completed, side_effect=error) as run, \
                contextlib.redirect_stdout(output):
            code = gates.source_roots(self.repo, self.node)
        return code, output.getvalue(), run

    def test_posix_passes_the_full_argument_list_without_a_shell(self):
        self.write_report()
        for platform in ("linux", "darwin"):
            with self.subTest(platform=platform):
                code, _, run = self.run_probe(platform)
                self.assertEqual(0, code)
                run.assert_called_once_with(
                    ["./gradlew", "--quiet", "-I", str(self.repo / "tools/nekojs-source-roots.init.gradle"),
                     ":26.2.0-fabric:help"],
                    cwd=str(self.repo), capture_output=True, text=True, shell=False)

    def test_windows_retains_batch_wrapper_shell_dispatch(self):
        self.write_report()
        code, _, run = self.run_probe("win32")
        self.assertEqual(0, code)
        run.assert_called_once_with(
            [str(self.repo / "gradlew.bat"), "--quiet", "-I",
             str(self.repo / "tools/nekojs-source-roots.init.gradle"), ":26.2.0-fabric:help"],
            cwd=str(self.repo), capture_output=True, text=True, shell=True)

    def test_missing_wrapper_returns_a_locatable_failure(self):
        code, output, _ = self.run_probe("linux", error=FileNotFoundError("wrapper missing"))
        self.assertEqual(1, code)
        for field in ("FAIL source-roots", "node=26.2.0-fabric", "input=", "expected=", "owner=",
                      "could not start Gradle"):
            self.assertIn(field, output)

    def test_failed_gradle_does_not_accept_an_existing_report(self):
        self.write_report()
        code, output, _ = self.run_probe("linux", returncode=2)
        self.assertEqual(1, code)
        self.assertIn("FAIL source-roots", output)
        self.assertIn("Gradle error", output)

    def test_successful_exit_without_a_report_still_fails(self):
        code, output, _ = self.run_probe("linux")
        self.assertEqual(1, code)
        self.assertIn("FAIL source-roots", output)


if __name__ == "__main__":
    unittest.main()
