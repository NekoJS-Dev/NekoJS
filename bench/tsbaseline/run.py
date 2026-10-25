# Strict TypeScript baseline driver (ticket 37 / research 2026-10-25-glm-stage2 handoff).
# Reproducible five-node isolated install -> default declaration generation ->
# ten-entrypoint strict tsc check (strict=true from the generated jsconfig,
# skipLibCheck=false, noEmit). Saves every command, exit code and diagnostic so
# later rounds can re-run the exact same flow after generator changes.
#
# Usage (from the repo root, sequential steps):
#   python bench/tsbaseline/run.py prepare --target build/tsbaseline-<tag> \
#       --revision <git-sha> [--platform-source build/closeout-selector-backend-installed-v3]
#   python bench/tsbaseline/run.py run     --target build/tsbaseline-<tag>
#   python bench/tsbaseline/run.py check   --target build/tsbaseline-<tag>
#
# prepare copies only the loader platform (libraries/, fabric-server-launch.jar)
# from --platform-source; the platform is pinned by the loader version, not by
# repository source. Everything else (config, nekojs/, .neko_probe/, world) is
# generated fresh by the current mod jars, so the baseline measures the current
# default declarations without acceptance-script noise.

import argparse
import glob
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RCON = os.path.join(REPO_ROOT, "bench", "perf", "rcon.py")
sys.path.insert(0, os.path.dirname(RCON))
import rcon as rcon_client  # noqa: E402  (bench/perf/rcon.py, fixed RCON channel)

NODES = [
    # loader, java (v3 recorded runtime JDKs: 1.21.1 needs 21, 26.x need 25),
    # server launch args, platform files to copy, external pinned mods, nekojs jar glob.
    {
        "node": "1.21.1", "loader": "neoforge",
        "java": "C:/Program Files/Java/jdk-21.0.10/bin/java.exe",
        "args": ["@libraries/net/neoforged/neoforge/21.1.227/win_args.txt", "nogui"],
        "platform": ["libraries"],
        "external_mods": ["graal-1504336-8762962.jar"],
        "jar_glob": "nekojs-neoforge-1.21.1-*-server.jar",
    },
    {
        "node": "26.1.2", "loader": "neoforge",
        "java": "C:/Program Files/Java/jdk-25.0.2/bin/java.exe",
        "args": ["@libraries/net/neoforged/neoforge/26.1.2.71/win_args.txt", "nogui"],
        "platform": ["libraries"],
        "external_mods": ["graal-1504336-8762962.jar"],
        "jar_glob": "nekojs-neoforge-26.1.2-*.jar",
    },
    {
        "node": "26.2.0", "loader": "neoforge",
        "java": "C:/Program Files/Java/jdk-25.0.2/bin/java.exe",
        "args": ["@libraries/net/neoforged/neoforge/26.2.0.75/win_args.txt", "nogui"],
        "platform": ["libraries"],
        "external_mods": ["graal-1504336-8762962.jar"],
        "jar_glob": "nekojs-neoforge-26.2.0-*.jar",
    },
    {
        "node": "26.1.2-fabric", "loader": "fabric",
        "java": "C:/Program Files/Java/jdk-25.0.2/bin/java.exe",
        "args": ["-jar", "fabric-server-launch.jar", "nogui"],
        "platform": ["fabric-server-launch.jar", "libraries"],
        "external_mods": ["graal-1504336-8762963.jar", "fabric-api-0.155.2+26.1.2.jar"],
        "jar_glob": "nekojs-fabric-26.1.2-*.jar",
    },
    {
        "node": "26.2.0-fabric", "loader": "fabric",
        "java": "C:/Program Files/Java/jdk-25.0.2/bin/java.exe",
        "args": ["-jar", "fabric-server-launch.jar", "nogui"],
        "platform": ["fabric-server-launch.jar", "libraries"],
        "external_mods": ["graal-1504336-8762963.jar", "fabric-api-0.159.0+26.2.jar"],
        "jar_glob": "nekojs-fabric-26.2-*.jar",
    },
]

SIDES = ["server", "startup"]
TS_CODE = re.compile(r"error (TS\d+):")


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def pick_nekojsjar(node_cfg):
    pattern = os.path.join(REPO_ROOT, "versions", node_cfg["node"], "build", "libs", node_cfg["jar_glob"])
    matches = [p for p in glob.glob(pattern)]
    if not matches:
        raise SystemExit(f"no nekojs jar matches {pattern}; build the node first")
    return max(matches, key=os.path.getmtime)


def cmd_prepare(args):
    target = os.path.abspath(args.target)
    platform_source = os.path.abspath(args.platform_source)
    if os.path.exists(target):
        raise SystemExit(f"target {target} already exists; remove it or pick a new tag")
    manifest = {
        "revision": args.revision,
        "platformSource": platform_source,
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "nodes": [],
    }
    for idx, cfg in enumerate(NODES):
        node = cfg["node"]
        node_dir = os.path.join(target, node)
        os.makedirs(node_dir)
        for rel in cfg["platform"]:
            src = os.path.join(platform_source, node, rel)
            dst = os.path.join(node_dir, rel)
            if not os.path.exists(src):
                raise SystemExit(f"platform file/dir missing: {src}")
            if os.path.isdir(src):
                shutil.copytree(src, dst)
            else:
                shutil.copy2(src, dst)
        mods_dir = os.path.join(node_dir, "mods")
        os.makedirs(mods_dir)
        node_entry = {"node": node, "loader": cfg["loader"], "java": cfg["java"],
                      "serverPort": args.base_port + idx * 10,
                      "rconPort": args.base_port + idx * 10 + 1,
                      "rconPassword": hashlib.sha1(f"{args.revision}:{node}".encode()).hexdigest()[:24],
                      "mods": []}
        for mod in cfg["external_mods"]:
            src = os.path.join(platform_source, node, "mods", mod)
            if not os.path.exists(src):
                raise SystemExit(f"external pinned mod missing: {src}")
            shutil.copy2(src, os.path.join(mods_dir, mod))
            node_entry["mods"].append({"file": mod, "sha256": sha256_file(src),
                                       "origin": "platform-source (pinned curse.maven artifact)"})
        jar = pick_nekojsjar(cfg)
        jar_name = os.path.basename(jar)
        shutil.copy2(jar, os.path.join(mods_dir, jar_name))
        node_entry["mods"].append({"file": jar_name, "sha256": sha256_file(jar),
                                   "origin": os.path.relpath(jar, REPO_ROOT)})
        with open(os.path.join(node_dir, "eula.txt"), "w", encoding="ascii", newline="\n") as f:
            f.write("eula=true\n")
        # Minimal fixed properties; the server fills the rest with its own defaults.
        props = {
            "server-ip": "127.0.0.1",
            "server-port": node_entry["serverPort"],
            "enable-rcon": "true",
            "rcon.port": node_entry["rconPort"],
            "rcon.password": node_entry["rconPassword"],
            "online-mode": "false",
            "view-distance": "2",
            "simulation-distance": "2",
            "pause-when-empty-seconds": "-1",
        }
        with open(os.path.join(node_dir, "server.properties"), "w", encoding="ascii", newline="\n") as f:
            for k, v in props.items():
                f.write(f"{k}={v}\n")
        manifest["nodes"].append(node_entry)
        print(f"prepared {node}: ports {node_entry['serverPort']}/{node_entry['rconPort']}, jar {jar_name}")
    write_json(os.path.join(target, "preparation-manifest.json"), manifest)
    print(f"prepare complete -> {target}")


def rcon_call(port, password, timeout, commands):
    # Opens one connection per call, mirroring bench/perf/rcon.py semantics.
    sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
    sock.settimeout(timeout)
    try:
        rcon_client.send_packet(sock, 1, 3, password)
        auth = rcon_client.recv_packet(sock)
        if auth is None or auth[0] == -1:
            raise RuntimeError("rcon auth failed")
        out = []
        for i, command in enumerate(commands):
            rcon_client.send_packet(sock, 10 + i, 2, command)
            resp = rcon_client.recv_packet(sock)
            if resp is None:
                raise RuntimeError(f"rcon no response for: {command}")
            out.append(resp[2])
        return out
    finally:
        sock.close()


def wait_rcon_ready(port, password, deadline):
    while time.time() < deadline:
        try:
            rcon_call(port, password, 10.0, ["list"])
            return True
        except (OSError, RuntimeError):
            time.sleep(3)
    return False


def wait_session_lock(node_dir, deadline):
    lock = os.path.join(node_dir, "world", "session.lock")
    while time.time() < deadline and os.path.exists(lock):
        try:
            os.rename(lock, lock + ".probe")
            os.rename(lock + ".probe", lock)
            return True
        except OSError:
            time.sleep(2)
    return not os.path.exists(lock)


def tail_text(path, lines=15):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return "".join(f.readlines()[-lines:])
    except OSError:
        return ""


def query_process_identity(pid):
    """Exact WMI identity (microsecond creation time, full command line) for cleanup verification."""
    try:
        out = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "$p = Get-CimInstance Win32_Process -Filter 'ProcessId=%d'; if ($p) {"
             " @{ creationDate = (Get-Date $p.CreationDate).ToString('yyyy-MM-ddTHH:mm:ss.fffffff');"
             "   commandLine = $p.CommandLine } | ConvertTo-Json -Compress }" % pid],
            capture_output=True, text=True, timeout=15)
        if out.returncode != 0 or not out.stdout.strip():
            return {}
        info = json.loads(out.stdout)
        return {"creationDate": info.get("creationDate"), "commandLine": info.get("commandLine")}
    except (OSError, ValueError):
        return {}


def cmd_run(args):
    target = os.path.abspath(args.target)
    manifest = json.load(open(os.path.join(target, "preparation-manifest.json"), encoding="utf-8"))
    results = []
    for entry in manifest["nodes"]:
        node = entry["node"]
        node_dir = os.path.join(target, node)
        cfg = next(c for c in NODES if c["node"] == node)
        stdout_path = os.path.join(node_dir, "server-stdout.log")
        stderr_path = os.path.join(node_dir, "server-stderr.log")
        out_f = open(stdout_path, "w", encoding="utf-8", errors="replace")
        err_f = open(stderr_path, "w", encoding="utf-8", errors="replace")
        proc = subprocess.Popen([entry["java"], "-Xms512M", "-Xmx2G"] + cfg["args"],
                                cwd=node_dir, stdout=out_f, stderr=err_f,
                                creationflags=subprocess.CREATE_NEW_PROCESS_GROUP)
        # Ownership record: kill-servers.ps1 only ever touches processes listed here, after
        # verifying PID liveness, exact creation time and exact command line (see README).
        record = {"node": node, "pid": proc.pid, "startedAtEpoch": time.time(),
                  "java": entry["java"], "rconPort": entry["rconPort"]}
        record.update(query_process_identity(proc.pid))
        write_json(os.path.join(node_dir, "server-process.json"), record)
        print(f"{node}: started pid {proc.pid}, waiting for rcon on {entry['rconPort']}")
        result = {"node": node, "pid": proc.pid}
        failure = None
        cause = None
        try:
            ready = wait_rcon_ready(entry["rconPort"], entry["rconPassword"], time.time() + args.boot_timeout)
            if not ready:
                failure = f"{node}: rcon never became ready; stdout tail:\n{tail_text(stdout_path)}"
            else:
                # RCON response bodies truncate at one packet (~4KB) because the mod emits
                # many NEKO-4029 warnings before the completion line, so completion is
                # confirmed from the stdout log instead; the RCON receipt is trigger-only.
                try:
                    receipt = rcon_call(entry["rconPort"], entry["rconPassword"], args.probe_timeout,
                                        ["nekojs probe typescript"])[0]
                    result["probeReceiptHead"] = receipt[:300]
                    deadline = time.time() + args.probe_timeout
                    marker = None
                    while time.time() < deadline:
                        marker = re.search(r"Probe \[typescript\] generated (\d+) files",
                                           tail_text(stdout_path, lines=400))
                        if marker:
                            break
                        if proc.poll() is not None:
                            raise RuntimeError(
                                f"server exited during probe; stdout tail:\n{tail_text(stdout_path)}")
                        time.sleep(2)
                    if not marker:
                        raise RuntimeError(
                            f"probe completion line never appeared; stdout tail:\n{tail_text(stdout_path)}")
                    result["probeFiles"] = int(marker.group(1))
                except (OSError, RuntimeError) as probe_failure:
                    cause = probe_failure
                    failure = f"{node}: probe failed: {probe_failure}"
        except Exception as unexpected:
            cause = unexpected
            failure = f"{node}: unexpected driver failure: {unexpected}"
        # Unified owned-server recovery for success, probe failure and driver error alike:
        # clean RCON stop first, bounded wait, then kill the child we own.
        result.update(stop_owned_server(proc, entry, args))
        out_f.close(); err_f.close()
        result["sessionLockReleased"] = wait_session_lock(node_dir, time.time() + 120)
        result["stdoutTail"] = tail_text(stdout_path, 5).strip().splitlines()[-1:] or [""]
        if failure is not None:
            result["failure"] = failure
        results.append(result)
        # Persist after every node so a later failure still leaves the completed nodes inspectable.
        write_json(os.path.join(target, "run-summary.json"), results)
        print(f"{node}: probeFiles={result.get('probeFiles')} exit={result['exitCode']} "
              f"killed={result['killed']} stopSent={result['stopSent']} "
              f"lockReleased={result['sessionLockReleased']}")
        if failure is not None:
            # Keep the original exception chained for the driver's own crash paths.
            raise SystemExit(failure) from cause
        if result["exitCode"] != 0:
            raise SystemExit(f"{node}: unexpected server exit {result['exitCode']}")
    print("run complete")


def stop_owned_server(proc, entry, args):
    """Graceful stop first, bounded wait, then kill; returns the recovery outcome."""
    outcome = {"stopSent": False, "exitCode": None, "killed": False}
    if proc.poll() is None:
        try:
            rcon_call(entry["rconPort"], entry["rconPassword"], 30.0, ["stop"])
            outcome["stopSent"] = True
        except (OSError, RuntimeError):
            pass
        grace = args.stop_timeout if outcome["stopSent"] else 15
        try:
            outcome["exitCode"] = proc.wait(timeout=grace)
            return outcome
        except subprocess.TimeoutExpired:
            proc.kill()
            outcome["exitCode"] = proc.wait()
            outcome["killed"] = True
    else:
        outcome["exitCode"] = proc.returncode
    return outcome


def tsc_version():
    try:
        out = subprocess.run(["node", os.path.join(REPO_ROOT, "node_modules", "typescript", "bin", "tsc"), "--version"],
                             capture_output=True, text=True)
    except OSError as failure:
        raise SystemExit(f"cannot launch node/tsc: {failure}")
    if out.returncode != 0:
        raise SystemExit(f"tsc --version failed (exit {out.returncode}): {(out.stderr or out.stdout)[:300]}")
    return out.stdout.strip()


def cmd_check(args):
    target = os.path.abspath(args.target)
    manifest = json.load(open(os.path.join(target, "preparation-manifest.json"), encoding="utf-8"))
    ide_dir = os.path.join(target, "ide")
    os.makedirs(ide_dir, exist_ok=True)
    summary = {"sourceRevision": manifest["revision"], "typescriptVersion": tsc_version(),
               "skipLibCheck": False, "noEmit": True, "nodes": []}
    total = 0
    for entry in manifest["nodes"]:
        node = entry["node"]
        node_summary = {"node": node, "typescript": []}
        summary["nodes"].append(node_summary)
        for side in SIDES:
            jsconfig = os.path.join(target, node, "nekojs", f"{side}_scripts", "jsconfig.json")
            if not os.path.exists(jsconfig):
                raise SystemExit(f"missing generated jsconfig: {jsconfig}")
            tsconfig_path = os.path.join(ide_dir, f"{node}-{side}-tsconfig.json")
            write_json(tsconfig_path, {"extends": jsconfig,
                                       "compilerOptions": {"skipLibCheck": False, "noEmit": True}})
            diag_path = os.path.join(ide_dir, f"{node}-{side}-tsc.txt")
            err_path = os.path.join(ide_dir, f"{node}-{side}-tsc-stderr.txt")
            cmd = ["node", os.path.join(REPO_ROOT, "node_modules", "typescript", "bin", "tsc"),
                   "-p", tsconfig_path]
            proc = subprocess.run(cmd, capture_output=True)
            text = proc.stdout.decode("utf-8", errors="replace")
            stderr_text = proc.stderr.decode("utf-8", errors="replace")
            with open(diag_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
            with open(err_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(stderr_text)
            codes = {}
            for line in text.splitlines():
                m = TS_CODE.search(line)
                if m:
                    codes[m.group(1)] = codes.get(m.group(1), 0) + 1
            count = sum(codes.values())
            # A run is valid only when the exit code and the parsed diagnostics agree: a broken
            # TypeScript install or missing config exits nonzero with an EMPTY diagnostic listing,
            # which must fail explicitly instead of counting as "zero diagnostics".
            if proc.returncode == 0:
                run_state = "clean" if count == 0 else "diagnostics"
            elif proc.returncode in (1, 2) and count > 0:
                run_state = "diagnostics"
            else:
                run_state = "invalid"
            if run_state != "invalid":
                total += count
            node_summary["typescript"].append({
                "side": side, "exitCode": proc.returncode, "runState": run_state,
                "diagnosticCount": count, "codes": dict(sorted(codes.items())),
                "diagnosticsComplete": run_state != "invalid",
                "command": " ".join(cmd),
            })
            print(f"{node}/{side}: exit={proc.returncode} state={run_state} diagnostics={count}")
            if run_state == "invalid":
                summary["totalDiagnostics"] = total
                write_json(os.path.join(target, "ide-check-summary.json"), summary)
                raise SystemExit(
                    f"{node}/{side}: invalid tsc run (exit {proc.returncode}, {count} parsed "
                    f"diagnostics); stdout head:\n{text[:300]}\nstderr head:\n{stderr_text[:300]}")
        summary["totalDiagnostics"] = total
        write_json(os.path.join(target, "ide-check-summary.json"), summary)
    summary["totalDiagnostics"] = total
    write_json(os.path.join(target, "ide-check-summary.json"), summary)
    print(f"total diagnostics across ten entrypoints: {total}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("--target", required=True)
    p.add_argument("--revision", required=True)
    p.add_argument("--platform-source", default=os.path.join("build", "closeout-selector-backend-installed-v3"))
    p.add_argument("--base-port", type=int, default=34500)
    p.set_defaults(func=cmd_prepare)
    p = sub.add_parser("run")
    p.add_argument("--target", required=True)
    p.add_argument("--boot-timeout", type=int, default=900)
    p.add_argument("--probe-timeout", type=int, default=600)
    p.add_argument("--stop-timeout", type=int, default=300)
    p.set_defaults(func=cmd_run)
    p = sub.add_parser("check")
    p.add_argument("--target", required=True)
    p.set_defaults(func=cmd_check)
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
