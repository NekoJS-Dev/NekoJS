"""Owned-server failure checks through the baseline driver's command boundary."""

import json
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import run as baseline


class RunCleanupTest(unittest.TestCase):
    def test_identity_timeout_reaps_owned_server_and_records_original_failure(self):
        self.assert_owned_cleanup(baseline.subprocess.TimeoutExpired("process identity", 15), SystemExit)

    def test_interruption_reaps_owned_server_without_replacing_the_interrupt(self):
        self.assert_owned_cleanup(KeyboardInterrupt("identity interrupted"), KeyboardInterrupt)

    def assert_owned_cleanup(self, failure, expected_exception):
        process = Mock(pid=987654)
        process.poll.return_value = None
        process.wait.side_effect = [baseline.subprocess.TimeoutExpired("server", 15), -9]
        with tempfile.TemporaryDirectory(prefix="neko-baseline-cleanup-") as folder:
            target = Path(folder)
            (target / "1.21.1").mkdir()
            manifest = {"nodes": [{"node": "1.21.1", "java": "unused",
                                   "rconPort": 1, "rconPassword": "fixture-only"}]}
            (target / "preparation-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            args = types.SimpleNamespace(target=folder, boot_timeout=1, probe_timeout=1, stop_timeout=1)
            with patch.object(baseline.subprocess, "Popen", return_value=process) as launch, \
                    patch.object(baseline.subprocess, "CREATE_NEW_PROCESS_GROUP", 0, create=True), \
                    patch.object(baseline, "query_process_identity", side_effect=failure), \
                    patch.object(baseline, "rcon_call", side_effect=OSError("stop unavailable")), \
                    patch.object(baseline, "wait_session_lock", return_value=True):
                try:
                    with self.assertRaises(expected_exception) as raised:
                        baseline.cmd_run(args)
                    self.assertTrue(launch.call_args.kwargs["stdout"].closed)
                    self.assertTrue(launch.call_args.kwargs["stderr"].closed)
                finally:
                    for name in ("stdout", "stderr"):
                        launch.call_args.kwargs[name].close()
            if expected_exception is KeyboardInterrupt:
                self.assertIs(raised.exception, failure)
            else:
                self.assertIs(raised.exception.__cause__, failure)
            process.kill.assert_called_once_with()
            self.assertEqual(process.wait.call_count, 2)
            results = json.loads((target / "run-summary.json").read_text(encoding="utf-8"))
            self.assertTrue(results[0]["killed"])
            self.assertIn(str(failure), results[0]["failure"])


if __name__ == "__main__":
    unittest.main()
