# 工单 32：Fabric 五层源唯一性 trace 采集（2026-09-15）
#
# 分层记录两个 fabric 节点的源→制品链路（交接单 §4.3 要求五层分开、不得混淆）：
#   L1 raw source origin   —— src/fabric/**（唯一 raw loader 事实源）+ 共享树孪生源守卫核验
#   L2 processed source    —— stonecutter 生成副本（生成证据，**不是第二源码事实源**）
#   L3 编译 class           —— 节点 classes + common classes（common 不得携带孪生 FQCN）
#   L4 Jar 去重前打包输入    —— 按 jar 任务 from 序枚举全部来源，聚合 entry→来源映射
#   L5 最终 ZIP entries     —— 实际 jar 内容 + 孪生 class 字节级比对（SHA-256 对 L3）
#
# 用法: python fabric-trace-t32.py <repo根> <输出文件>
# 前置: 双 fabric build 已完成（classes/jar 均已生成）；
#       L4 的依赖清单用 trace-probe.init.gradle.kts 采集的 deps 文件（可选，缺失则降级）。
import hashlib
import json
import sys
import zipfile
from collections import Counter
from pathlib import Path

root = Path(sys.argv[1]).resolve()
out = []

# 交接单 §4.3 的 7 个同 FQCN 孪生（共享树 neoforge 面 + src/fabric fabric 面）
TWIN_FQCN = [
    "com/tkisor/nekojs/bindings/event/CommandEvents",
    "com/tkisor/nekojs/bindings/event/EntityEvents",
    "com/tkisor/nekojs/bindings/event/ItemEvents",
    "com/tkisor/nekojs/bindings/event/LevelEvents",
    "com/tkisor/nekojs/bindings/event/PlayerEvents",
    "com/tkisor/nekojs/bindings/event/ServerEvents",
    "com/tkisor/nekojs/bindings/event/client/KeyBindEvents",
]
FABRIC_NODES = ["26.1.2-fabric", "26.2.0-fabric"]
NEOFORGE_NODES = ["1.21.1", "26.1.2", "26.2.0"]
# convention fabricForbiddenResourceEntries（processResources 层排除，build/resources 不含）
FORBIDDEN_RES = {
    "META-INF/accesstransformer.cfg", "META-INF/neoforge.mods.toml",
    "neoforge.mods.toml", "nekojs.mixins.json", "nekojs-dynamic.mixins.json",
    "nekojs.interface_injection.json",
}
# jar 任务级 exclude（L4 模拟用，与 convention 同步）
JAR_EXCLUDES = {"module-info.class"}  # + META-INF/versions/**/module-info.class


def emit(line=""):
    out.append(line)
    print(line)


def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def tree_files(base: Path, pattern: str = "**/*"):
    return sorted(p for p in base.glob(pattern) if p.is_file()) if base.exists() else []


def rel(p: Path) -> str:
    return p.relative_to(root).as_posix()


import re

def strip_comments(text: str) -> str:
    """剥离块注释与行注释后的有效代码（stonecutter 把失活守卫块整体注释化）。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def is_hollow(p: Path) -> bool:
    """生成树守卫副本是否为注释空壳（无有效类型声明 → javac 不产 class）。"""
    return not strip_comments(p.read_text(encoding="utf-8", errors="replace")).strip()


def load_deps_probe(node: str):
    """trace-probe init script 采集的依赖清单（common runtime + fabric bundled）。"""
    probe = root / "build" / f"trace-deps-{node}.txt"
    if not probe.exists():
        return None
    lines = [l.rstrip("\r\n") for l in probe.read_text(encoding="utf-8").splitlines()]
    common_rt = [l.split("::", 1)[1] for l in lines if l.startswith("COMMON_RT::")]
    bundled = [l.split("::", 1)[1] for l in lines if l.startswith("BUNDLED::")]
    return {"common_rt": common_rt, "bundled": bundled}


# ---------------- L1 raw source origin ----------------
emit("#" * 72)
emit("# L1 raw source origin —— src/fabric 是两个 fabric 节点唯一的 raw loader 事实源")
emit("#" * 72)
fabric_root = root / "src" / "fabric"
raw_java = tree_files(fabric_root / "java", "**/*.java")
raw_res = tree_files(fabric_root / "resources")
raw_tpl = tree_files(fabric_root / "templates")
raw_fixture = tree_files(fabric_root / "test")
emit(f"src/fabric/java   : {len(raw_java)} 个 .java")
emit(f"src/fabric/resources: {len(raw_res)} 个文件")
emit(f"src/fabric/templates : {len(raw_tpl)} 个文件")
emit(f"src/fabric/test      : {len(raw_fixture)} 个文件（runtime smoke fixture，仅 CI 拷贝消费）")
emit("全部 java 清单（路径 = <sha256[:12]>）：")
for p in raw_java:
    emit(f"  {sha256(p)[:12]}  {rel(p)}")

shared_root = root / "src" / "main" / "java"
emit("")
emit("共享树孪生源守卫核验（7 个 neoforge 面孪生必须整文件 `//? if neoforge` 守卫）：")
for fqcn in TWIN_FQCN:
    p = shared_root / (fqcn + ".java")
    if not p.exists():
        emit(f"  MISSING  src/main/java/{fqcn}.java")
        continue
    head = p.read_text(encoding="utf-8", errors="replace").splitlines()[:2]
    guarded = any("if neoforge" in h for h in head)
    emit(f"  {'GUARDED' if guarded else 'UNGUARDED!!'}  src/main/java/{fqcn}.java  首行: {head[0][:60]!r}")

for node in FABRIC_NODES:
    node_src = root / "versions" / node / "src" / "main" / "java"
    nj = tree_files(node_src, "**/*.java")
    emit(f"versions/{node}/src/main/java（节点 override）: {len(nj)} 个 .java")
    for p in nj:
        emit(f"  {sha256(p)[:12]}  {rel(p)}")

# ---------------- L2 processed source ----------------
emit("")
emit("#" * 72)
emit("# L2 processed source —— stonecutter 生成副本（生成证据，不是第二源码事实源）")
emit("#" * 72)
shared_java = tree_files(shared_root, "**/*.java")
whole_file_neoforge = [
    p for p in shared_java
    if any("if neoforge" in h for h in p.read_text(encoding="utf-8", errors="replace").splitlines()[:2])
]
emit(f"共享树 src/main/java 总数: {len(shared_java)}；整文件 `//? if neoforge` 守卫: {len(whole_file_neoforge)}")
for node in FABRIC_NODES:
    gen = root / "versions" / node / "build" / "generated" / "stonecutter" / "main" / "java"
    gen_java = tree_files(gen, "**/*.java")
    gen_set = {p.relative_to(gen).as_posix() for p in gen_java}
    emit(f"versions/{node} 生成树: {len(gen_java)} 个 .java（共享树 {len(shared_java)} 的守卫剥除/替换副本）")
    # a) 7 个孪生 neoforge 面在生成树必须是"注释空壳"（stonecutter 把失活整文件守卫
    #    注释化：文件名保留但无有效类型声明，javac 不产出 class——文件名存在不等于第二源）
    for fqcn in TWIN_FQCN:
        gp = gen / (fqcn + ".java")
        if not gp.exists():
            emit(f"  ABSENT           生成树/{fqcn}.java")
        else:
            emit(f"  {'HOLLOW(注释空壳)' if is_hollow(gp) else 'LIVE!!(有效代码)'}  生成树/{fqcn}.java（{gp.stat().st_size} B）")
    # b) src/fabric 的文件不得以有效代码形式出现在生成树（stonecutter 不处理 raw root；
    #    同路径的注释空壳不算泄漏——空壳不产 class）
    leaked, hollow_only = [], []
    for p in raw_java:
        relp = p.relative_to(fabric_root / "java").as_posix()
        gp = gen / relp
        if gp.exists():
            (hollow_only if is_hollow(gp) else leaked).append(relp)
    emit(f"  src/fabric 路径在生成树有有效代码: {len(leaked)}{(' !! ' + str(leaked[:5])) if leaked else '（零——raw root 不被预处理）'}")
    emit(f"  src/fabric 路径在生成树仅注释空壳: {len(hollow_only)}（= 上方 7 个孪生的 neoforge 守卫壳，非 fabric 内容）")
    # c) 共享树全部整文件守卫在生成树必须空壳化
    live_guards = [
        p.relative_to(shared_root).as_posix() for p in whole_file_neoforge
        if (gen / p.relative_to(shared_root)).exists() and not is_hollow(gen / p.relative_to(shared_root))
    ]
    emit(f"  整文件 neoforge 守卫在生成树仍含有效代码: {len(live_guards)}{(' !! ' + str(live_guards[:5])) if live_guards else '（全部注释空壳化）'}")

# ---------------- L3 编译 class ----------------
emit("")
emit("#" * 72)
emit("# L3 编译 class —— 节点 classes 唯一携带 fabric 孪生；common classes 必须零孪生")
emit("#" * 72)
common_classes = root / "common" / "build" / "classes" / "java" / "main"
for node in FABRIC_NODES:
    cls = root / "versions" / node / "build" / "classes" / "java" / "main"
    node_twins = {}
    for fqcn in TWIN_FQCN:
        p = cls / (fqcn + ".class")
        node_twins[fqcn] = sha256(p) if p.exists() else None
    emit(f"versions/{node}/build/classes/java/main：7 孪生存在性/哈希")
    for fqcn, h in node_twins.items():
        emit(f"  {h[:12] if h else 'MISSING!!'}  {fqcn}.class")
    common_twins = [fqcn for fqcn in TWIN_FQCN if (common_classes / (fqcn + ".class")).exists()]
    emit(f"  common classes 携带孪生 FQCN: {len(common_twins)}{(' !! ' + str(common_twins)) if common_twins else '（零——common 不是孪生第二来源）'}")
    # 节点 override 类
    emit(f"  节点 override 编译产物（versions/{node}/src 的类）:")
    for p in tree_files(root / "versions" / node / "src" / "main" / "java", "**/*.java"):
        cn = p.relative_to(root / 'versions' / node / 'src' / 'main' / 'java').as_posix()[:-5] + ".class"
        cp = cls / cn
        emit(f"    {'BUILT ' + sha256(cp)[:12] if cp.exists() else 'MISSING!!'}  {cn}")

# ---------------- L4 Jar 去重前打包输入 ----------------
emit("")
emit("#" * 72)
emit("# L4 Jar 去重前打包输入 —— 按 jar 任务 from 注册序枚举全部来源")
emit("#（序即 DuplicatesStrategy.EXCLUDE 的胜者序：节点 main output → common output →")
emit("#  common runtime deps → bundled；jar 级 exclude=module-info 规则在去重之外先过滤）")
emit("#" * 72)

l4_sets = {}  # node -> 去重后（序首胜出）entry 集合，供 L5 闭合对照
for node in FABRIC_NODES:
    nd = root / "versions" / node / "build"
    sources = []  # (来源标签, {entry: file})
    # 1) 节点 main output：classes + resources（processResources 已排除 forbidden 资源）
    node_out = {}
    for base, label in [(nd / "classes" / "java" / "main", "node-classes"),
                        (nd / "resources" / "main", "node-resources"),
                        (nd / "generated" / "icuClasses", "icu-extract")]:
        for p in tree_files(base):
            node_out.setdefault(p.relative_to(base).as_posix(), p)
    sources.append((f"{node}:sourceSets.main.output", node_out))
    # 2) common main output
    common_out = {}
    for base in [common_classes, root / "common" / "build" / "resources" / "main"]:
        for p in tree_files(base):
            common_out.setdefault(p.relative_to(base).as_posix(), p)
    sources.append((":common:sourceSets.main.output", common_out))
    # 3+4) 依赖（探针清单）
    deps = load_deps_probe(node)
    if deps:
        for jar_path in deps["common_rt"]:
            zp = Path(jar_path)
            if not zp.exists():
                continue
            entries = {}
            with zipfile.ZipFile(zp) as zf:
                for n in zf.namelist():
                    if not n.endswith("/"):
                        entries.setdefault(n, zp)
            sources.append((f"common-rt:{zp.name}", entries))
        for jar_path in deps["bundled"]:
            zp = Path(jar_path)
            entries = {}
            if zp.exists():
                with zipfile.ZipFile(zp) as zf:
                    for n in zf.namelist():
                        if not n.endswith("/"):
                            entries.setdefault(n, zp)
            sources.append((f"bundled:{zp.name}", entries))
    else:
        emit("  （探针清单缺失——L4 只覆盖 sourceSet output，依赖来源降级未采集）")

    # 聚合
    agg = {}
    for label, entries in sources:
        for name in entries:
            agg.setdefault(name, []).append(label)
    # jar 级 exclude 先过滤（module-info）
    def jar_excluded(name):
        return name in JAR_EXCLUDES or (name.startswith("META-INF/versions/") and name.endswith("module-info.class"))
    pre_dedup = {n: ls for n, ls in agg.items() if not jar_excluded(n)}
    dups = {n: ls for n, ls in pre_dedup.items() if len(ls) > 1}
    emit(f"versions/{node}：去重前打包输入 {len(pre_dedup)} 个 entry，多来源 entry {len(dups)} 个")
    # 模拟 EXCLUDE 序首胜出 + zip 层目录项：存集合供 L5 全等对照
    l4_sets[node] = set(pre_dedup)
    for n in sorted(dups)[:20]:
        emit(f"  x{len(dups[n])}  {n}  <- {sorted(set(dups[n]))[:3]}")
    emit(f"  （多来源 entry 全部由 DuplicatesStrategy.EXCLUDE 取序首；上方至多列 20 条）")
    for fqcn in TWIN_FQCN:
        ls = pre_dedup.get(fqcn + ".class", [])
        emit(f"  孪生来源数 x{len(ls)}  {fqcn}.class  <- {ls if ls else 'MISSING!!'}")

# ---------------- L5 最终 ZIP entries ----------------
emit("")
emit("#" * 72)
emit("# L5 最终 ZIP entries —— 实际 jar 内容 + 孪生 class 与 L3 节点产物字节级比对")
emit("#" * 72)

for node in FABRIC_NODES:
    libs_dir = root / "versions" / node / "build" / "libs"
    final = [p for p in sorted(libs_dir.glob("*.jar"))
             if not p.stem.endswith(("-sources", "-javadoc", "-plain", "-dev"))] if libs_dir.exists() else []
    if not final:
        emit(f"== {node}: MISSING jar")
        continue
    path = final[-1]
    emit(f"== {node}: {path.name}")
    emit(f"  size  : {path.stat().st_size} B")
    emit(f"  sha256: {sha256(path)}")
    cls = root / "versions" / node / "build" / "classes" / "java" / "main"
    with zipfile.ZipFile(path) as zf:
        names = zf.namelist()
        cnt = Counter(names)
        dup = {k: v for k, v in cnt.items() if v > 1}
        emit(f"  entries: {len(names)}；ZIP 层重复 entry: {len(dup)}")
        # L4↔L5 闭合：去重前输入（序首胜出）集合与最终 jar 文件 entry 全等
        jar_files = {n for n in names if not n.endswith("/")}
        l4 = l4_sets.get(node, set())
        only_jar, only_l4 = sorted(jar_files - l4), sorted(l4 - jar_files)
        emit(f"  L4↔L5 闭合: 仅在 jar {len(only_jar)} / 仅在 L4 {len(only_l4)}"
             + (f" !! jar-only={only_jar[:5]} l4-only={only_l4[:5]}" if (only_jar or only_l4) else "（全等）"))
        for fqcn in TWIN_FQCN:
            n = fqcn + ".class"
            in_jar = cnt[n]
            disk = cls / n
            match = ""
            if in_jar == 1 and disk.exists():
                inner = hashlib.sha256(zf.read(n)).hexdigest()
                match = " 字节==节点class" if inner == sha256(disk) else " !!字节!=节点class"
            emit(f"  x{in_jar}  {n}{match}")
        # forbidden 资源核验
        emit(f"  forbidden 资源: {sorted(FORBIDDEN_RES & set(names)) or 'none'}")

# NeoForge 侧孪生终态（跨 loader 对照：fabric 孪生从未进入 NeoForge jar）
emit("")
emit("NeoForge 三节点孪生终态对照（fabric 孪生不应出现；出现即 cross-loader 泄漏）：")
for node in NEOFORGE_NODES:
    libs_dir = root / "versions" / node / "build" / "libs"
    final = [p for p in sorted(libs_dir.glob("*.jar"))
             if not p.stem.endswith(("-sources", "-javadoc", "-plain", "-dev"))] if libs_dir.exists() else []
    if not final:
        emit(f"  {node}: 无 jar（仅 check 时正常缺省）")
        continue
    with zipfile.ZipFile(final[-1]) as zf:
        names = Counter(zf.namelist())
        line = "  " + node + ": "
        for fqcn in TWIN_FQCN:
            line += f"x{names[fqcn + '.class']} "
        emit(line + "（neoforge 面孪生，源自共享树守卫分支）")
        # 验证 NeoForge jar 的孪生不是 src/fabric 编译物：与各 fabric 节点 class 哈希比对
        for fqcn in TWIN_FQCN:
            n = fqcn + ".class"
            if names[n] == 0:
                continue
            inner = hashlib.sha256(zf.read(n)).hexdigest()
            same_as_fabric = [
                fn for fn in FABRIC_NODES
                if (root / "versions" / fn / "build" / "classes" / "java" / "main" / n).exists()
                and sha256(root / "versions" / fn / "build" / "classes" / "java" / "main" / n) == inner
            ]
            if same_as_fabric:
                emit(f"    !! {n} 与 fabric 节点编译产物字节一致（cross-loader 泄漏）: {same_as_fabric}")
        emit(f"    （无 !! 行 = NeoForge jar 孪生与 src/fabric 编译物零重合）")

if len(sys.argv) > 2:
    Path(sys.argv[2]).write_text("\n".join(out) + "\n", encoding="utf-8")
