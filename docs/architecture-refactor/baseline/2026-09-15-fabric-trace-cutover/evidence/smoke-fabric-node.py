# 工单 32：fabric 节点本地 Windows 等价 runtime smoke（照票 31 evidence 做法：
# fixture 铺设 → runServer 分离启动 → 轮询三标记 → RCON stop → 断言 BUILD SUCCESSFUL）。
# 与 CI 的对应：同 fixture（src/fabric/test/resources/fabric-runtime-smoke）、同 gradle
# 任务（:<node>:runServer）、同三标记断言；CI 另有的 180s timeout 包装与 packaged-artifact
# 校验分别由本脚本的轮询上限与 verifyFabricRuntimeArtifact/jar facts 覆盖。
#
# 用法: python smoke-fabric-node.py <repo根> <节点名> <输出前缀>
import os
import re
import shutil
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

root = Path(sys.argv[1]).resolve()
node = sys.argv[2]
prefix = sys.argv[3] if len(sys.argv) > 3 else node
run_dir = root / "versions" / node / "run-server"
fixture = root / "src" / "fabric" / "test" / "resources" / "fabric-runtime-smoke"
SERVER_PORT = 25881
RCON_PORT = 25882
RCON_PASSWORD = "nekojs-smoke"

MARKERS = [
    "FABRIC-CI-SMOKE: startup bindings ok",
    "FABRIC-CI-SMOKE: server started",
    "FABRIC-CI-SMOKE: spawnLightning ok",
]
FAIL_PATTERNS = re.compile(
    r"spawnLightning rejected|Critical injection|FAILED during|"
    r"Mixin[^\n]*(failed|error)|Could not find required mod|ExceptionInInitializerError",
    re.IGNORECASE,
)


def rcon(command: str):
    s = socket.create_connection(("127.0.0.1", RCON_PORT), timeout=15)

    def pkt(rid, ptype, body):
        data = struct.pack("<ii", rid, ptype) + body.encode("utf-8") + b"\x00\x00"
        return struct.pack("<i", len(data)) + data

    s.sendall(pkt(1, 3, RCON_PASSWORD))
    resp = s.recv(64)
    if len(resp) < 12 or struct.unpack("<i", resp[4:8])[0] == -1:
        s.close()
        raise RuntimeError(f"RCON login failed for {node}")
    s.sendall(pkt(2, 2, command))
    try:
        s.recv(4096)
    except socket.timeout:
        pass
    s.close()


def scan(files):
    """返回 (已命中的标记列表, 失败模式命中行)。文件只读尾部 256KB。

    debug.log（loom dev run 的 Mixin DEBUG 流）排除：CI 的失败扫描实际只覆盖
    gradle stdout（"$run_dir/logs" 目录参数被 2>/dev/null 吞掉），DEBUG 行的
    exitOnError/回调描述会误报 "Mixin.*error"。真失败出现在 INFO 以上级别。
    """
    hits, fails = set(), []
    for f in files:
        if f.name == "debug.log":
            continue
        try:
            text = f.read_bytes().decode("utf-8", errors="replace")
        except OSError:
            continue
        for m in MARKERS:
            if m in text:
                hits.add(m)
        for line in text.splitlines():
            if "/DEBUG]" in line:
                continue
            if FAIL_PATTERNS.search(line):
                fails.append(f"{f.name}: {line.strip()[:160]}")
    return hits, fails


def main():
    # 1) 铺设 fixture + eula + rcon 配置（run 目录全新）
    if run_dir.exists():
        shutil.rmtree(run_dir)
    smoke_dir = run_dir / "nekojs"
    (smoke_dir / "startup_scripts").mkdir(parents=True)
    (smoke_dir / "server_scripts").mkdir(parents=True)
    shutil.copy(fixture / "startup_scripts" / "fabric_ci_smoke.js", smoke_dir / "startup_scripts")
    shutil.copy(fixture / "server_scripts" / "fabric_ci_smoke.js", smoke_dir / "server_scripts")
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    # 与 CI 同口径：默认世界生成（flat preset 会引发一条无关的 "No key layers"
    # datapack ERROR，污染零 ERROR 断言）
    (run_dir / "server.properties").write_text(
        f"server-port={SERVER_PORT}\nenable-rcon=true\nrcon.port={RCON_PORT}\n"
        f"rcon.password={RCON_PASSWORD}\nspawn-protection=0\nonline-mode=false\n",
        encoding="utf-8",
    )

    gradle_log = root / "build" / f"smoke-{prefix}-gradle.log"
    markers_txt = root / "build" / f"smoke-{prefix}-markers.txt"
    proc = subprocess.Popen(
        ["cmd", "/c", "gradlew.bat", f":{node}:runServer", "--console=plain"],
        cwd=str(root),
        stdout=open(gradle_log, "wb"),
        stderr=subprocess.STDOUT,
    )

    deadline = time.time() + 420
    hits, fails = set(), []
    while time.time() < deadline:
        logs = [gradle_log]
        for base, dirs, files in os.walk(run_dir):
            dirs[:] = [d for d in dirs if d not in ("world", "libraries", "downloads")]
            logs += [Path(base) / f for f in files if f.endswith((".log", ".txt"))]
        hits, fails = scan(logs)
        if fails:
            print(f"FAIL-PATTERN {node}:")
            for line in sorted(set(fails))[:10]:
                print("  " + line)
            break
        if len(hits) == len(MARKERS):
            print(f"markers complete for {node}, sending RCON stop")
            try:
                rcon("stop")
            except Exception as e:  # noqa: BLE001
                print(f"RCON stop failed ({e}); falling back to SIGTERM")
                proc.terminate()
            break
        if proc.poll() is not None:
            print(f"gradle exited early (code={proc.returncode}) with markers={sorted(hits)}")
            break
        time.sleep(2)

    try:
        rc = proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"], check=False)
        rc = proc.wait()

    markers_txt.write_text("\n".join(sorted(hits)) + "\n", encoding="utf-8")
    print(f"gradle exit={rc}, markers={len(hits)}/{len(MARKERS)}")
    for m in sorted(hits):
        print("  " + m)
    ok = rc == 0 and len(hits) == len(MARKERS) and not fails
    print("SMOKE " + ("PASS" if ok else "FAIL") + f": {node}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
