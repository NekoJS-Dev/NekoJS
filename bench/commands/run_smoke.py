"""Run loader-backed command assertions in an isolated NeoForge game directory."""

import argparse
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--node", choices=("26.1.2", "1.21.1"), default="26.1.2")
NODE = parser.parse_args().node
GAME = ROOT / f"versions/{NODE}/build/information-command-smoke/game"
REPORT = GAME / "information-command-smoke.json"
LOG = ROOT / "build/information-commands-runtime.txt"


def main():
    GAME.mkdir(parents=True, exist_ok=True)
    if not (GAME / "eula.txt").exists():
        (GAME / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    if not (GAME / "server.properties").exists():
        (GAME / "server.properties").write_text(
            "server-port=25943\nonline-mode=false\nlevel-type=minecraft:flat\n"
            "view-distance=2\nsimulation-distance=2\nspawn-protection=0\n", encoding="utf-8")
    REPORT.unlink(missing_ok=True)
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    with LOG.open("wb") as output:
        result = subprocess.run([str(wrapper), "-I", "bench/commands/smoke.init.gradle",
                                 f"-PinformationSmokeNode={NODE}", f":{NODE}:runInformationSmoke", "--offline", "--console=plain"],
                                cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"Smoke server exited with {result.returncode}; see {LOG}")
    if not REPORT.is_file():
        raise RuntimeError(f"Smoke server did not write a report; see {LOG}")
    report = json.loads(REPORT.read_text(encoding="utf-8"))
    if report.get("failure") is not None or len(report.get("checks", [])) < 44:
        raise RuntimeError(f"Command assertions failed: {report}; see {LOG}")
    print(f"PASS: {len(report['checks'])} loader-backed command assertions. Report: {REPORT}")


if __name__ == "__main__":
    main()
