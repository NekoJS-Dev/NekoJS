"""Switch each supported active node and verify every node's compiler/resource inputs."""

import argparse
import errno
import json
import os
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[1]
NODES = tuple(sorted(path.parent.name for path in (ROOT / "versions").glob("*/gradle.properties")))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compile", action="store_true", help="Also compile main and test sources for all nodes.")
    parser.add_argument("--target", type=Path, default=ROOT / "build/active-source-matrix")
    args = parser.parse_args()
    if not NODES:
        raise RuntimeError("No version-node configuration files were found")
    args.target = args.target.resolve()
    args.target.mkdir(parents=True, exist_ok=True)
    controller = ROOT / "stonecutter.gradle.kts"
    original = re.search(r'^stonecutter active "([^"]+)"', controller.read_text(encoding="utf-8"), re.M).group(1)
    original_sources = {}
    for path in (ROOT / "src").rglob("*"):
        if path.is_file():
            if not path.resolve().is_relative_to(ROOT):
                raise ValueError(f"Shared source is outside the checkout: {path}")
            original_sources[path] = path.read_bytes()
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    results = []
    (args.target / "summary.json").write_text("[]\n", encoding="utf-8")
    fixtures = []
    created_directories = set()
    failure = None

    def fixture(path, text):
        path = path.resolve()
        if not path.is_relative_to(ROOT):
            raise ValueError(f"Fixture is outside the checkout: {path}")
        directory = path.parent
        while not directory.exists():
            created_directories.add(directory)
            directory = directory.parent
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("x", encoding="utf-8") as output:
            output.write(text)
        fixtures.append(path)

    def gradle(arguments, log_name):
        with (args.target / log_name).open("wb") as log:
            result = subprocess.run([str(wrapper), *arguments, "--offline", "--console=plain"], cwd=ROOT,
                                    stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError(f"Gradle failed with exit {result.returncode}; see {args.target / log_name}")

    try:
        # Real source roots exercise Java and resource precedence for main and test.
        # Fabric also has a raw loader root outside Stonecutter's generated tree.
        for source_set in ("main", "test"):
            name = "NodeSourceOverride" + source_set.title() + "Probe"
            java_path = Path("com/tkisor/nekojs/buildfixture") / f"{name}.java"
            resource_path = Path("node-source-selection") / f"{source_set}-override.txt"
            roots = [ROOT / "src" / source_set, *(ROOT / "versions" / node / "src" / source_set for node in NODES)]
            if source_set == "main":
                roots.append(ROOT / "src/fabric")
            for root in roots:
                fixture(root / "java" / java_path,
                        f"package com.tkisor.nekojs.buildfixture; public final class {name} {{}}\n")
                fixture(root / "resources" / resource_path, "node source selection fixture\n")
        for active in NODES:
            print(f"Switching active node to {active}", flush=True)
            gradle(["switchVersion", f"-Pnode={active}"], f"{active}-switch.txt")
            tasks = ["verifySwitchTaskModel", *[f":{node}:verifyNodeSourceSelection" for node in NODES]]
            if args.compile:
                tasks.extend(task for node in NODES for task in (f":{node}:classes", f":{node}:testClasses"))
            gradle(["-I", "tools/verify-node-source-selection.init.gradle", *tasks], f"{active}-check.txt")
            reports = [json.loads((ROOT / "versions" / node / "build/node-source-selection.json").read_text(encoding="utf-8"))
                       for node in NODES]
            results.append({"active": active, "compiled": args.compile, "nodes": reports})
            (args.target / "summary.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
            print(f"PASS {active}: all {len(NODES)} node selections" + (" and compilations" if args.compile else ""), flush=True)
    except BaseException as error:
        failure = error
        raise
    finally:
        cleanup_errors = []
        for path in reversed(fixtures):
            try:
                path.unlink(missing_ok=True)
            except OSError as error:
                cleanup_errors.append(error)
        try:
            gradle(["switchVersion", f"-Pnode={original}"], "restore.txt")
        except BaseException as error:
            cleanup_errors.append(error)
        # Switching back can normalize inactive guard comments. Restore the initial
        # bytes as well; this driver is intended for an otherwise idle test checkout.
        for path, contents in original_sources.items():
            try:
                if not path.resolve().is_relative_to(ROOT):
                    raise ValueError(f"Shared source moved outside the checkout: {path}")
                if not path.is_file() or path.read_bytes() != contents:
                    path.write_bytes(contents)
            except (OSError, ValueError) as error:
                cleanup_errors.append(error)
        for path in fixtures:
            try:
                path.unlink(missing_ok=True)
            except OSError as error:
                cleanup_errors.append(error)
        for directory in sorted(created_directories, key=lambda path: len(path.parts), reverse=True):
            try:
                if directory.resolve() == ROOT or not directory.resolve().is_relative_to(ROOT):
                    raise ValueError(f"Fixture directory moved outside the checkout: {directory}")
                directory.rmdir()
            except FileNotFoundError:
                pass
            except OSError as error:
                if error.errno not in (errno.ENOTEMPTY, errno.EEXIST):
                    cleanup_errors.append(error)
            except ValueError as error:
                cleanup_errors.append(error)
        try:
            gradle(["help"], "restore-model.txt")
        except BaseException as error:
            cleanup_errors.append(error)
        if cleanup_errors:
            detail = "; ".join(str(error) for error in cleanup_errors)
            if failure is not None:
                failure.add_note("Restoration failed: " + detail)
            else:
                raise RuntimeError("Restoration failed: " + detail) from cleanup_errors[0]


if __name__ == "__main__":
    main()
