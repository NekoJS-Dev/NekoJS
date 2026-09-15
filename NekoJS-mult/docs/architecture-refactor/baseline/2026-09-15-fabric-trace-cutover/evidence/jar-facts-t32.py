# 工单 31 fabric raw root 迁移——两 fabric 节点制品事实采集（2026-09-15）
# 方法沿用票 01 的 2026-09-12-fabric-build-fix/logs/jar-facts.py（SHA-256/entries/metadata/
# mixin/禁入条目），按本票验收面扩展：compat provider services 条目、compat/LevelExtension/
# inject.MixinLevel 类存在性、7 个同 FQCN 的重复计数、去重前后 entry 计数。
# 用法: python jar-facts-t31.py <repo 根> <输出文件>
import hashlib
import json
import sys
import zipfile
from collections import Counter
from pathlib import Path

root = Path(sys.argv[1])
jars = {
    "26.1.2-fabric": root / "versions/26.1.2-fabric/build/libs",
    "26.2.0-fabric": root / "versions/26.2.0-fabric/build/libs",
    "1.21.1": root / "versions/1.21.1/build/libs",
    "26.1.2": root / "versions/26.1.2/build/libs",
    "26.2.0": root / "versions/26.2.0/build/libs",
}

FORBIDDEN_RES = {
    "META-INF/accesstransformer.cfg", "META-INF/neoforge.mods.toml",
    "neoforge.mods.toml", "nekojs.mixins.json", "nekojs-dynamic.mixins.json",
    "nekojs.interface_injection.json",
}

# 交接单 §4.3：7 个双 FQCN（共享树 neoforge 面 + raw root fabric 孪生）
TWIN_FQCN = [
    "com/tkisor/nekojs/bindings/event/CommandEvents.class",
    "com/tkisor/nekojs/bindings/event/EntityEvents.class",
    "com/tkisor/nekojs/bindings/event/ItemEvents.class",
    "com/tkisor/nekojs/bindings/event/LevelEvents.class",
    "com/tkisor/nekojs/bindings/event/PlayerEvents.class",
    "com/tkisor/nekojs/bindings/event/ServerEvents.class",
    "com/tkisor/nekojs/bindings/event/client/KeyBindEvents.class",
]

# AC4 诊断面：compat 门面与调用链上的关键类
COMPAT_CLASSES = [
    "com/tkisor/nekojs/platform/compat/McVersionCompat.class",
    "com/tkisor/nekojs/platform/compat/McPlatformCompat.class",
    "com/tkisor/nekojs/platform/compat/McClientCompat.class",
    "com/tkisor/nekojs/api/inject/LevelExtension.class",
    "com/tkisor/nekojs/mixin/inject/MixinLevel.class",
]

out = []


def emit(line=""):
    out.append(line)
    print(line)


def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


for node, libs_dir in jars.items():
    found = sorted(libs_dir.glob("*.jar")) if libs_dir.exists() else []
    final = [p for p in found if not p.stem.endswith(("-sources", "-javadoc", "-plain", "-dev"))]
    if not final:
        emit(f"== {node}: MISSING jar in {libs_dir}")
        continue
    # jar 任务只产一个最终制品；多于一个就全部列出并取最后一个（构建序）
    if len(final) > 1:
        emit(f"== {node}: NOTE multiple final jars: {[p.name for p in final]}")
    path = final[-1]
    emit(f"== {node}")
    emit(f"file      : {path.name}")
    emit(f"size      : {path.stat().st_size} B")
    emit(f"sha256    : {sha256(path)}")
    with zipfile.ZipFile(path) as zf:
        names = zf.namelist()
        emit(f"entries   : {len(names)}")
        dup = {k: v for k, v in Counter(names).items() if v > 1}
        emit(f"dup entries: {len(dup)}" + (f" {dict(sorted(dup.items())[:10])}" if dup else ""))
        services = sorted(n for n in set(names) if n.startswith("META-INF/services/"))
        emit("services  :")
        for s in services:
            body = zf.read(s).decode().strip().splitlines()
            emit(f"  {s} => {body}")
        emit("compat classes:")
        for c in COMPAT_CLASSES:
            emit(f"  {'PRESENT' if c in set(names) else 'absent '}  {c}")
        emit("twin FQCN dup counts:")
        for t in TWIN_FQCN:
            emit(f"  x{Counter(names)[t]}  {t}")
        forbidden = sorted(FORBIDDEN_RES & set(names))
        emit(f"forbidden : {forbidden if forbidden else 'none'}")
        if node.endswith("-fabric"):
            fmj = json.loads(zf.read("fabric.mod.json"))
            emit("fabric.mod.json:")
            emit(f"  id={fmj.get('id')} version={fmj.get('version')}")
            emit(f"  depends={json.dumps(fmj.get('depends'), sort_keys=True)}")
            emit(f"  entrypoints keys={sorted(fmj.get('entrypoints', {}).keys())}")
            emit(f"  mixins field={json.dumps(fmj.get('mixins'))}")
            emit(f"  accessWidener={fmj.get('accessWidener')}")
            mixin_cfgs = sorted(n for n in set(names) if n.endswith("mixins.json"))
            for m in mixin_cfgs:
                cfg = json.loads(zf.read(m))
                total = sum(len(cfg.get(k, [])) for k in ("mixins", "client", "server"))
                emit(f"  mixin cfg {m}: refs={total}")
    emit()

if len(sys.argv) > 2:
    Path(sys.argv[2]).write_text("\n".join(out), encoding="utf-8")
