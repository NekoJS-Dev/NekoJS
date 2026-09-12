# 工单 01 fabric 修复验证——五节点产物事实采集（2026-09-12）
# 用法: python jar-facts.py <worktree 项目根>
# 输出: 每个 jar 的大小/SHA-256/entry 数；fabric jar 的 metadata、mixin、禁入条目、Graal/night-config/icu4j 通道证据。
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
jars = {
    "1.21.1": root / "versions/1.21.1/build/libs/nekojs-neoforge-1.21.1-1.1.0-preview3.jar",
    "26.1.2": root / "versions/26.1.2/build/libs/nekojs-neoforge-26.1.2-1.1.0-preview3.jar",
    "26.2.0": root / "versions/26.2.0/build/libs/nekojs-neoforge-26.2.0-1.1.0-preview3.jar",
    "26.1.2-fabric": root / "versions/26.1.2-fabric/build/libs/nekojs-fabric-26.1.2-1.1.0-preview3.jar",
    "26.2.0-fabric": root / "versions/26.2.0-fabric/build/libs/nekojs-fabric-26.2-1.1.0-preview3.jar",
}

FORBIDDEN_RES = {
    "META-INF/accesstransformer.cfg", "META-INF/neoforge.mods.toml",
    "neoforge.mods.toml", "nekojs.mixins.json", "nekojs-dynamic.mixins.json",
    "nekojs.interface_injection.json",
}

def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

for node, path in jars.items():
    if not path.exists():
        print(f"== {node}: MISSING {path}")
        continue
    data_size = path.stat().st_size
    digest = sha256(path)
    with zipfile.ZipFile(path) as zf:
        names = zf.namelist()
        print(f"== {node}")
        print(f"file      : {path.name}")
        print(f"size      : {data_size} B")
        print(f"sha256    : {digest}")
        print(f"entries   : {len(names)}")
        # fabric-specific facts
        if node.endswith("-fabric"):
            fmj = json.loads(zf.read("fabric.mod.json"))
            print("fabric.mod.json:")
            print(f"  id={fmj.get('id')} version={fmj.get('version')}")
            print(f"  depends={json.dumps(fmj.get('depends'), sort_keys=True)}")
            print(f"  suggests={json.dumps(fmj.get('suggests'), sort_keys=True)}")
            print(f"  breaksed? breaks={json.dumps(fmj.get('breaks'), sort_keys=True)}")
            print(f"  entrypoints keys={sorted(fmj.get('entrypoints', {}).keys())}")
            print(f"  mixins field={json.dumps(fmj.get('mixins'))}")
            print(f"  accessWidener={fmj.get('accessWidener')}")
            mixin_cfgs = [n for n in names if n.endswith("mixins.json")]
            for m in sorted(mixin_cfgs):
                cfg = json.loads(zf.read(m))
                total = sum(len(cfg.get(k, [])) for k in ("mixins", "client", "server"))
                print(f"  mixin cfg {m}: mixins={len(cfg.get('mixins', []))} "
                      f"client={len(cfg.get('client', []))} server={len(cfg.get('server', []))} total={total}")
            forbidden = [n for n in names if n in FORBIDDEN_RES]
            neo_pkgs = [n for n in names if n.startswith(("com/tkisor/nekojs/neoforge/", "net/neoforged/"))]
            neo_named = [n for n in names if n.endswith(".class") and "neoforge" in n.lower()]
            print(f"  forbidden-res hits: {sorted(forbidden) or 'NONE'}")
            print(f"  neoforge pkg hits : {sorted(neo_pkgs) or 'NONE'}")
            print(f"  neoforge class hits: {sorted(neo_named) or 'NONE'}")
            # Graal / night-config / icu4j 通道证据
            def count(prefix):
                return sum(1 for n in names if n.startswith(prefix))
            print(f"  org/graalvm/** entries      : {count('org/graalvm/')}")
            print(f"  com/oracle/truffle/** entries: {count('com/oracle/truffle/')}")
            print(f"  com/electronwill/night-config/** entries: {count('com/electronwill/night-config/')}")
            print(f"  com/ibm/icu/** entries      : {count('com/ibm/icu/')}")
            print(f"  com/tkisor/nekojs/** entries: {count('com/tkisor/nekojs/')}")
            aw = [n for n in names if n.endswith(".accesswidener")]
            print(f"  accesswidener entries: {aw}")
            svc = [n for n in names if n.startswith("META-INF/services/")]
            print(f"  META-INF/services count: {len(svc)}")
        else:
            at = [n for n in names if n.endswith("accesstransformer.cfg")]
            mods_toml = [n for n in names if n.endswith(("neoforge.mods.toml", "mods.toml"))]
            print(f"  AT entries: {at}; mods.toml entries: {mods_toml}")
            mx = [n for n in names if n.endswith("mixins.json")]
            print(f"  mixin cfgs: {sorted(mx)}")
    print()
