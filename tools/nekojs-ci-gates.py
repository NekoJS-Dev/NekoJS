#!/usr/bin/env python3
"""NekoJS build/CI 子集与 processor 延期替代 gate（工单 33 / W9）。

只读 gate：解析节点事实源（settings/stonecutter/versions/*/gradle.properties/CI workflow/
buildSrc convention）、规范 spec 声明、编译产物与声明基线，输出逐项结论与失败诊断。
不新增 Gradle project、不写任何 golden、不修改任何基线。

用法：
  python tools/nekojs-ci-gates.py all [--json]
  python tools/nekojs-ci-gates.py subsets|processor|declaration|nodes
  python tools/nekojs-ci-gates.py source-roots --node <节点>
  python tools/nekojs-ci-gates.py selftest        # 植入故障自检（证明 gate 会红）

失败输出格式（每条都能定位节点、输入、期望与 owner）：
  <check> <subject> node=<node> input=<file> expected=<...> owner=<owner> :: <detail>
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

WORKFLOW = ".github/workflows/ci-build.yml"
PLATFORM_GATE_TASK = "platformGateTest"
PROCESSOR_OPTION = "-Anekojs.platform"


# ---------------------------------------------------------------- 公共形状

class Gate:
    """一个 check：owner / 输入 / 逐项输出 / 失败诊断（票 33 AC2/AC8）。"""

    def __init__(self, check, owner, inputs):
        self.check = check
        self.owner = owner
        self.inputs = list(inputs)
        self.rows = []
        self.failures = []

    def row(self, subject, state, evidence):
        self.rows.append({"subject": subject, "state": state, "evidence": evidence})
        return state

    def fail(self, subject, node, input_file, expected, detail):
        self.failures.append({
            "check": self.check, "subject": subject, "node": node,
            "input": input_file, "expected": expected, "owner": self.owner, "detail": detail,
        })

    @property
    def state(self):
        return "fail" if self.failures else "pass"

    def report(self):
        return {"check": self.check, "owner": self.owner, "inputs": self.inputs,
                "rows": self.rows, "failures": self.failures, "state": self.state}


# ---------------------------------------------------------------- 节点事实源

NODE_VERSION_RE = re.compile(r'^\s*versions\((?P<plain>[^)]*)\)', re.M)
NODE_FABRIC_RE = re.compile(
    r'^\s*version\("(?P<id>[^"]+)",\s*"(?P<mc>[^"]+)"\)\.buildscript\s*=\s*"(?P<script>[^"]+)"', re.M)


def parse_nodes(repo=REPO):
    """节点图唯一事实源 = settings.gradle.kts 的 stonecutter DSL（票 33 AC4/AC5）。"""
    text = (repo / "settings.gradle.kts").read_text(encoding="utf-8")
    nodes = []
    for m in NODE_VERSION_RE.finditer(text):
        for raw in m.group("plain").split(","):
            node = raw.strip().strip('"')
            if node:
                nodes.append({"id": node, "script": "build.gradle.kts"})
    for m in NODE_FABRIC_RE.finditer(text):
        nodes.append({"id": m.group("id"), "script": m.group("script")})
    for node in nodes:
        props = {}
        prop_file = repo / "versions" / node["id"] / "gradle.properties"
        if prop_file.is_file():
            for line in prop_file.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    props[k.strip()] = v.strip()
        node["props"] = props
        node["platform"] = props.get("deps.platform", "")
        node["project"] = ":" + node["id"]
    return nodes


# ---------------------------------------------------------------- gate: CI 子集

# 每个子集登记：用途、owner、覆盖节点、intentional skip、必须出现的任务/步骤/制品路径。
# 一致性检查按用途逐类对照 settings.gradle.kts 节点图，不用单一等值检查冒充所有用途（AC4）。
SUBSET_SPEC = {
    "neoforge-nbt": {
        "purpose": "NeoForge 专属 portable binary NBT smoke（fabric 分支脚本不注册 nbtSmokeTest）",
        "owner": "neoforge-nbt/CI-build owner",
        "expect": ["1.21.1", "26.1.2", "26.2.0"],
        "expectPlatform": "neoforge",
        "skip": ["26.1.2-fabric", "26.2.0-fabric"],
        "commands": [":1.21.1:nbtSmokeTest", ":26.1.2:nbtSmokeTest", ":26.2.0:nbtSmokeTest"],
        "steps": ["Run NeoForge NBT smoke tests"],
        "workflow": [],
        "intentionalSkip": [
            "Fabric 节点不在此子集：fabric convention 不注册 nbtSmokeTest（NBT 编解码由 "
            "NeoForge platform adapter 提供，Fabric 走 IPlatform 默认 unsupported 语义）"],
    },
    "fabric-artifact-smoke": {
        "purpose": "Fabric 制品 entrypoint/NeoForge 泄漏校验 + development runtime smoke"
                   "（基于真实五层 raw root 的制品）",
        "owner": "fabric-artifact/CI-build owner",
        "expect": ["26.1.2-fabric", "26.2.0-fabric"],
        "expectPlatform": "fabric",
        "skip": ["1.21.1", "26.1.2", "26.2.0"],
        "commands": [],
        "steps": ["Verify packaged Fabric artifact", "Run Fabric development runtime smoke"],
        "workflow": ["versions/26.1.2-fabric/build/libs/nekojs-fabric-",
                     "versions/26.2.0-fabric/build/libs/nekojs-fabric-"],
        "intentionalSkip": [
            "此 job 校验的是 download-artifact 取回的已上传 jar，不是本机构建产物，因此不复用"
            " Gradle :<node>:verifyFabricRuntimeArtifact；两处断言各自保留：本地 Gradle 门禁保证"
            "构建不产出坏 jar，CI bash 保证上传/下载链路没有偷换对象",
            "NeoForge 节点不在此子集：artifact 面由 release-publish 子集覆盖",
        ],
    },
    "all-node-build": {
        "purpose": "五节点 build（compileJava → test → check，含隔离门禁/"
                   "verifyDevModSourceSets/制品校验）",
        "owner": "build-convention/CI-build owner",
        "expect": ["1.21.1", "26.1.2", "26.2.0", "26.1.2-fabric", "26.2.0-fabric"],
        "expectPlatform": None,
        "skip": [],
        "commands": [":1.21.1:build", ":26.1.2:build", ":26.2.0:build",
                     ":26.1.2-fabric:build", ":26.2.0-fabric:build"],
        "steps": ["Build version nodes"],
        "workflow": [],
        "intentionalSkip": [],
    },
    "release-publish": {
        "purpose": "release 通道发布 NeoForge 三节点制品；Fabric 制品仅作 CI artifact"
                   "（未随公开发布）",
        "owner": "release/CI-publish owner",
        "expect": ["1.21.1", "26.1.2", "26.2.0"],
        "expectPlatform": "neoforge",
        "skip": ["26.1.2-fabric", "26.2.0-fabric"],
        "commands": [],
        "steps": ["Create GitHub release", "Publish to CurseForge"],
        "workflow": ["artifacts/nekojs-neoforge-26.1.2-", "artifacts/nekojs-neoforge-26.2.0-",
                     "artifacts/nekojs-neoforge-1.21.1-"],
        "intentionalSkip": [
            "Fabric 制品不进入公开 release：workflow 内按注释保留 CurseForge Fabric 发布块"
            "（按计划关闭，不是漏配）；Prepare artifacts 步骤在 release 通道显式跳过 "
            "nekojs-fabric-*"],
    },
}


def strip_comments(text):
    """剥掉 // 行注释与 /* */ 块注释（字符串字面量内的 // 保留原样）。

    票 33 review F4：processor gate 的 option 判定必须基于**实际代码**，否则注解里
    写一行 "-Anekojs.platform=nf26" 就能让"已删掉真实传参"的节点假阳通过。
    Kotlin/Java 的字符串字面量里出现 // 是可能的（如 URL），所以按状态机处理。
    """
    out = []
    i = 0
    in_line = in_block = False
    quote = None
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""
        if in_line:
            if ch == "\n":
                in_line = False
                out.append(ch)
            i += 1
            continue
        if in_block:
            if ch == "*" and nxt == "/":
                in_block = False
                i += 2
                continue
            if ch == "\n":
                out.append(ch)
            i += 1
            continue
        if quote is not None:
            out.append(ch)
            if ch == "\\" and nxt:
                out.append(nxt)
                i += 2
                continue
            if ch == quote:
                quote = None
            i += 1
            continue
        if ch == "/" and nxt == "/":
            in_line = True
            i += 2
            continue
        if ch == "/" and nxt == "*":
            in_block = True
            i += 2
            continue
        if ch in ("\"", "'"):
            quote = ch
            out.append(ch)
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def iter_run_commands(text):
    """粗粒度提取 workflow 的 run: 命令块；不引入 PyYAML 依赖。"""
    lines = text.splitlines()
    out, i = [], 0
    while i < len(lines):
        m = re.match(r"^\s*-?\s*run:\s*(\||>)?\s*(.*)$", lines[i])
        if m:
            if m.group(1):
                indent = len(lines[i]) - len(lines[i].lstrip())
                i += 1
                while i < len(lines) and (not lines[i].strip()
                                          or (len(lines[i]) - len(lines[i].lstrip())) > indent):
                    out.append(lines[i])
                    i += 1
                continue
            out.append(m.group(2))
        i += 1
    return "\n".join(out)


def gate_subsets(repo=REPO):
    gate = Gate("ci-subset-consistency", "build-convention/CI-build owner",
                [WORKFLOW, "settings.gradle.kts", "versions/*/gradle.properties"])
    nodes = parse_nodes(repo)
    ids = [n["id"] for n in nodes]
    workflow = (repo / WORKFLOW).read_text(encoding="utf-8")
    commands = iter_run_commands(workflow)

    for name, spec in SUBSET_SPEC.items():
        expected = spec["expect"]
        gate.row(name, "purpose", spec["purpose"])
        for node in expected:
            if node not in ids:
                gate.fail(name, node, "settings.gradle.kts", "node 存在于 stonecutter 节点图",
                          "CI 子集声明覆盖了节点图里不存在的节点")
        for token in spec["commands"]:
            if token not in commands:
                owner_node = next((n for n in expected if n in token), None)
                gate.fail(name, token, WORKFLOW, "run 命令块含 " + token,
                          "子集缺少期望的节点任务（节点 " + str(owner_node) + "）")
        for token in spec["steps"]:
            if token not in workflow:
                gate.fail(name, token, WORKFLOW, "workflow 含步骤 " + token,
                          "子集缺少期望的步骤")
        for token in spec["workflow"]:
            if token not in workflow:
                owner_node = next((n for n in expected if n in token), None)
                gate.fail(name, token, WORKFLOW, "workflow 含 " + token,
                          "子集缺少期望的制品路径（节点 " + str(owner_node) + "）")
        for note in spec["intentionalSkip"]:
            gate.row(name, "intentional-skip", note)
        for node in spec["skip"]:
            if node not in ids:
                gate.fail(name, node, "settings.gradle.kts", "skip 节点存在于节点图",
                          "intentional skip 指向不存在的节点")
                continue
            platform = next(n["platform"] for n in nodes if n["id"] == node)
            if spec["expectPlatform"] and platform == spec["expectPlatform"]:
                gate.fail(name, node, "versions/" + node + "/gradle.properties",
                          "skip 节点平台 != " + spec["expectPlatform"],
                          "skip 与节点平台不符：该节点平台是 " + platform)

    all_covered, all_skipped = set(), set()
    for spec in SUBSET_SPEC.values():
        all_covered |= set(spec["expect"])
        all_skipped |= set(spec["skip"])
    for node in ids:
        if node not in all_covered:
            gate.fail("all-node-coverage", node, "settings.gradle.kts",
                      "节点被至少一个 CI 子集覆盖",
                      "节点既不在任何子集 expect 也未列入 intentional skip")
        else:
            purpose = next(s["purpose"] for s in SUBSET_SPEC.values() if node in s["expect"])
            gate.row(node, "covered", purpose)

    # 全节点子集必须与节点图等值（其余子集按用途可以是有意子集）
    build_cmd = next((line for line in commands.splitlines()
                      if ":26.2.0-fabric:build" in line or ":1.21.1:build" in line), "")
    for node in ids:
        if ":" + node + ":build" not in build_cmd:
            gate.fail("all-node-build", node, WORKFLOW, "五节点 build 命令含 :" + node + ":build",
                      "手写全节点列表漏节点（节点图事实源：" + ",".join(ids) + "）")
    return gate


# ---------------------------------------------------------------- gate: processor 延期

def gate_processor(repo=REPO):
    """Fabric common-api-processor 延期（票 33 AC1）。

    事实源 = 各节点 convention 的 annotationProcessor 接线 + -Anekojs.platform 选项 +
    节点 properties 的 deps.platform_tag + 处理器源码接受的 option 词表。四者一致才证明
    "未接入"，而不是靠文档声明。
    """
    processor_rel = ("common-api-processor/src/main/java/com/tkisor/nekojs/api/spec/processor/"
                     "SpecCoverageProcessor.java")
    gate = Gate("fabric-processor-deferral", "build-convention owner",
                ["buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts",
                 "buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts",
                 "versions/*/gradle.properties", processor_rel])
    nodes = parse_nodes(repo)
    processor_text = (repo / processor_rel).read_text(encoding="utf-8")
    accepted = [v for v in ("nf26", "nf121", "cr") if '"%s"' % v in processor_text]
    gate.row("processor-options", "declared", "accepts=" + ",".join(accepted) + " (source of truth: "
             + processor_rel + ")")
    if not accepted:
        gate.fail("processor-options", None, processor_rel, "至少一个平台 option",
                  "无法从处理器源码读出接受的平台 option 词表（gate 输入失效）")

    for node in nodes:
        convention = repo / ("buildSrc/src/main/kotlin/nekojs.%s-node.gradle.kts"
                             % node["platform"])
        raw = convention.read_text(encoding="utf-8") if convention.is_file() else ""
        # 票 33 review F4：必须剥注释后再匹配。注释里留一行
        # // "-Anekojs.platform=nf26" 而删掉真实传参时，纯文本包含匹配会假阳。
        text = strip_comments(raw)
        wired = 'annotationProcessor(project(":common-api-processor"))' in text
        passes_option = PROCESSOR_OPTION + "=" in text
        tag = node["props"].get("deps.platform_tag")

        gate.row(node["id"], "platform=" + node["platform"],
                 "wired=" + str(wired).lower() + " option_arg=" + str(passes_option).lower()
                 + " platform_tag=" + str(tag))

        if node["platform"] == "fabric":
            if wired:
                gate.fail(node["id"], node["id"], str(convention.relative_to(repo)),
                          "Fabric 节点不挂 :common-api-processor（1.2.0 延期）",
                          "Fabric convention 挂上了处理器 —— 延期被偷换成临时接线")
            if passes_option:
                gate.fail(node["id"], node["id"], str(convention.relative_to(repo)),
                          "Fabric 节点不传 " + PROCESSOR_OPTION,
                          "Fabric convention 传了平台 option")
            if tag in accepted:
                gate.fail(node["id"], node["id"], "versions/" + node["id"] + "/gradle.properties",
                          "Fabric 节点不宣称处理器 option",
                          "Fabric 平台标签 %s 已在处理器接受词表 %s 内 —— 延期结论必须重审"
                          % (tag, accepted))
            continue

        if not wired:
            gate.fail(node["id"], node["id"], str(convention.relative_to(repo)),
                      "NeoForge 既有接线保持（processor 在 annotationProcessor）",
                      "NeoForge convention 不再挂 :common-api-processor")
        if not passes_option:
            gate.fail(node["id"], node["id"], str(convention.relative_to(repo)),
                      "NeoForge 既有接线保持（传 " + PROCESSOR_OPTION + "）",
                      "NeoForge convention 不再传平台 option")
        if tag not in accepted:
            gate.fail(node["id"], node["id"], "versions/" + node["id"] + "/gradle.properties",
                      "平台 option 值在处理器接受词表 %s 内" % accepted,
                      "节点 deps.platform_tag=%s 不在处理器接受词表内（选项表与节点图漂移）" % tag)
    return gate


# ---------------------------------------------------------------- gate: declaration

def gate_declaration(repo=REPO):
    """declaration 覆盖 gate 的输入/输出登记（票 33 AC2）。

    真正的断言在 Java 侧（ManagedDeclarationCoverageGateTest，输出
    common/build/nekojs-gates/declaration-parity.json）；本 check 把输入、输出与失败
    诊断项登记进同一报告，并核验产物存在（缺失 = not verified）。
    """
    fixture_rel = "common/src/test/resources/nekojs/platform-gates/declaration-parity.txt"
    report_rel = "common/build/nekojs-gates/declaration-parity.json"
    gate = Gate("declaration-parity", "Managed Surface/Probe owner",
                ["common/src/main/java/com/tkisor/nekojs/core/api/CoreManagedApiBootstrap.java",
                 "common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/"
                 "ManagedApiDeclarationGenerator.java",
                 fixture_rel])
    if not (repo / fixture_rel).is_file():
        gate.fail("declaration", None, fixture_rel, "只读基线存在",
                  "缺少 declaration 基线 fixture：declaration gate not verified")
        return gate
    gate.row("fixture", "present", fixture_rel)
    report = repo / report_rel
    if not report.is_file():
        gate.fail("declaration", None, report_rel,
                  ":common:test 已运行 ManagedDeclarationCoverageGateTest",
                  "缺少 declaration gate 报告：not verified（跑 :common:test --tests "
                  "com.tkisor.nekojs.core.api.ManagedDeclarationCoverageGateTest）")
        return gate
    data = json.loads(report.read_text(encoding="utf-8"))
    for row in data.get("rows", []):
        gate.row("declaration", "derived", row)
    for failure in data.get("failures", []):
        gate.fail("declaration", None, report_rel, "0 declaration gaps", failure)
    return gate


# ---------------------------------------------------------------- gate: 五节点报告

def sha256_prefix(path, length=16):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()[:length]


def gate_nodes(repo=REPO):
    """五节点 check/artifact/source trace 进同一报告（票 33 AC6）。

    输入 = 各节点 check 结果（Test XML）、制品（名字 + 大小 + sha256）、source trace
    （build/nekojs-gates/source-roots.json）与两个节点内 gate 报告。任何一项缺失 =
    not verified 并逐条列出，绝不静默省略（尤其 Fabric artifact 与已声明能力 smoke）。
    """
    gate = Gate("node-report", "build-convention owner",
                ["versions/<node>/build/test-results/", "versions/<node>/build/libs/",
                 "versions/<node>/build/nekojs-gates/"])
    artifact_prefix = {"neoforge": "nekojs-neoforge-", "fabric": "nekojs-fabric-"}

    for node in parse_nodes(repo):
        node_id = node["id"]
        build = repo / "versions" / node_id / "build"
        if not build.is_dir():
            gate.fail(node_id, node_id, "versions/" + node_id + "/build",
                      "节点已构建（check + artifact + source trace 可观测）",
                      "build 目录不存在：本报告对该节点是 not verified，不做任何通过推断")
            continue

        # 只统计普通 test 任务的结果：platformGateTest 有自己的目录，避免把 gate 自身的
        # 结果混进 check 覆盖率（两者是不同的观察对象）。
        results = sorted((build / "test-results" / "test").glob("TEST-*.xml")) \
            if (build / "test-results" / "test").is_dir() else []
        tests = failures = 0
        for xml in results:
            head = xml.read_text(encoding="utf-8", errors="replace")[:400]
            m = re.search(r'tests="(\d+)".*?failures="(\d+)"', head)
            if m:
                tests += int(m.group(1))
                failures += int(m.group(2))
        if not results:
            gate.fail(node_id, node_id, "versions/" + node_id + "/build/test-results",
                      "节点 check 结果可观测",
                      "没有 test-results XML：该节点 check 结果 not verified")
        elif failures:
            gate.fail(node_id, node_id, "versions/" + node_id + "/build/test-results",
                      "0 failing tests", "%d failing of %d executed" % (failures, tests))
        else:
            gate.row(node_id, "check", "%d suites / %d tests / 0 failed" % (len(results), tests))

        jars = sorted((build / "libs").glob("*.jar")) if (build / "libs").is_dir() else []
        jars = [j for j in jars if not re.search(r"-(sources|javadoc|dev|plain)\.jar$", j.name)]
        prefix = artifact_prefix.get(node["platform"], "?")
        matched = [j for j in jars if j.name.startswith(prefix)]
        if not matched:
            gate.fail(node_id, node_id, "versions/" + node_id + "/build/libs",
                      prefix + "*.jar",
                      "节点制品缺失：artifact 验证对该节点 not verified"
                      + ("（Fabric artifact 验证不得静默省略）" if node["platform"] == "fabric"
                         else ""))
        else:
            jar = matched[0]
            gate.row(node_id, "artifact", "%s size=%d sha256=%s"
                     % (jar.name, jar.stat().st_size, sha256_prefix(jar)))

        trace = build / "nekojs-gates" / ("source-roots-%s.json" % node_id)
        if trace.is_file():
            data = json.loads(trace.read_text(encoding="utf-8"))
            gate.row(node_id, "source-trace", "java=%d resources=%d"
                     % (len(data.get("java", [])), len(data.get("resources", []))))
        else:
            gate.fail(node_id, node_id,
                      "versions/" + node_id + "/build/nekojs-gates/source-roots-%s.json" % node_id,
                      "source trace 已记录",
                      "缺少 source-roots 探针输出：source trace not verified（跑 "
                      "tools/nekojs-ci-gates.py source-roots --node " + node_id + "）")

        for name in ("spec-coverage", "event-surface"):
            report = build / "nekojs-gates" / ("%s-%s.json" % (name, node_id))
            if not report.is_file():
                gate.fail(node_id, node_id, str(report.relative_to(repo)),
                          name + " gate 已运行", "缺少 " + name + " gate 报告：not verified")
                continue
            data = json.loads(report.read_text(encoding="utf-8"))
            failures = data.get("failures") or []
            if failures:
                gate.row(node_id, name, "%d rows / %d failures" % (len(data.get("rows", [])),
                                                                   len(failures)))
                # 票 33 review F2：逐条打印该节点的**全部** failure，不只取第 [0] 条截断 160 字。
                # 真实故障（某节点 platformGateTest 红）会让多个节点同时带 failures，
                # 只留第一条会让其余条目不可定位。每条都带 node/input/expected/owner。
                for index, failure in enumerate(failures, start=1):
                    gate.fail(node_id, node_id, str(report.relative_to(repo)),
                              "%s gate 0 failure" % name,
                              "failure[%d/%d] %s" % (index, len(failures), failure))
            else:
                gate.row(node_id, name, "%d rows / 0 failures" % len(data.get("rows", [])))
    return gate


# ---------------------------------------------------------------- source-root 探针

def gradle_wrapper(repo):
    """按平台选 Gradle wrapper。

    票 33 review F5：CI 跑在 ubuntu-latest，硬编码 gradlew.bat 会让新步骤在 Linux 上必红，
    且 build/nekojs-gates-report.json 永远不产出。Windows 用 .bat，其余用 shell 脚本。
    """
    name = "gradlew.bat" if sys.platform.startswith("win") else "./gradlew"
    return str(repo / name) if name.endswith(".bat") else name


def source_roots(repo, node):
    """source-roots <node>：用 Gradle init-script 读出该节点实际挂载的 source roots。

    事实源 = 构建配置（不是手写清单）：探针把 main sourceSet 的 srcDirs 直接写盘为
    versions/<node>/build/nekojs-gates/source-roots-<node>.json，供 gate_nodes 消费。
    stdout 只作人读摘要，不作为解析输入（避免 Windows 管道截断被误当成"没有输入"）。

    票 33 review F2/F5：失败时**不 raise**（否则 set -euo pipefail 会在跑到 all 之前退出），
    而是按统一格式打一条带 node/input/expected/owner 的诊断并返回非零。
    """
    command = [gradle_wrapper(repo), "--quiet", "-I",
               str(repo / "tools/nekojs-source-roots.init.gradle"), ":%s:help" % node]
    out = subprocess.run(command, cwd=str(repo), capture_output=True, text=True, shell=True)
    target = repo / "versions" / node / "build" / "nekojs-gates" / ("source-roots-%s.json" % node)
    if not target.is_file():
        print("FAIL source-roots subject=%s node=%s input=%s expected=探针产出 source-roots-%s.json "
              "owner=build-convention owner :: gradle 退出 %s；stdout 尾部 %r；stderr 尾部 %r"
              % (node, node, "tools/nekojs-source-roots.init.gradle", node, out.returncode,
                 out.stdout[-400:], out.stderr[-400:]))
        return 1
    data = json.loads(target.read_text(encoding="utf-8"))
    print("source-roots: %s java=%d resources=%d" % (node, len(data["java"]), len(data["resources"])))
    for path in data["java"]:
        print("  java      " + path)
    for path in data["resources"]:
        print("  resources " + path)
    return 0


# ---------------------------------------------------------------- selftest

def selftest(repo=REPO):
    """在仓库副本上植入故障，逐条证明对应 gate 会变红（票 33 AC8 的可运行检查）。

    每个 case 都对应一条 review finding；副本复制忽略 build/，因此涉及"节点已构建"的
    case 会显式铺设所需的最小产物。
    """
    import contextlib
    import io
    import shutil
    import tempfile

    problems = []

    def fresh_copy():
        tmp = tempfile.mkdtemp()
        copy = Path(tmp) / "repo"
        shutil.copytree(repo, copy, symlinks=True, ignore=shutil.ignore_patterns(
            ".git", "build", ".gradle", "node_modules"))
        return copy

    def expect_fail(label, mutate, gate_fn, expect_check, expected_details=()):
        copy = fresh_copy()
        mutate(copy)
        try:
            gate = gate_fn(copy)
        except SystemExit as error:
            problems.append("%s: gate 直接退出（应输出可定位诊断）%s" % (label, error))
            return
        hits = [f for f in gate.failures if f["check"] == expect_check]
        if not hits:
            problems.append("%s: gate 未变红（期望 %s；实际 failures=%d）"
                            % (label, expect_check, len(gate.failures)))
            return
        # F2：断言该 check 的**全部** failure 都进了报告（不能只留 failures[0]）
        details = [f["detail"] for f in hits]
        missing = [d for d in expected_details if not any(d in detail for detail in details)]
        if missing:
            problems.append("%s: 报告吞掉了 failure 条目 %s（实际 %d 条）"
                            % (label, missing, len(hits)))
            return
        shown = hits[0]
        for detail_key in expected_details:
            match = [h for h in hits if detail_key in h["detail"]]
            if match:
                shown = match[0]
                break
        print("  OK   %-30s -> %s" % (label, shown["detail"][:68].strip()))

    def break_subset(copy):
        """AC4：从五节点 build 命令里划掉一个节点。"""
        target = copy / WORKFLOW
        target.write_text(target.read_text(encoding="utf-8")
                          .replace(":26.2.0-fabric:build ", ""), encoding="utf-8")

    def break_processor_wired(copy):
        """AC1 反向：Fabric 节点偷接 processor。"""
        target = copy / "buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts"
        text = target.read_text(encoding="utf-8")
        target.write_text(text + '\nannotationProcessor(project(":common-api-processor"))\n'
                                 '// "-Anekojs.platform=fabric"\n', encoding="utf-8")

    def break_option_comment(copy):
        """F4 假阳：真传参被注释掉、注释里仍留形如 -Anekojs.platform=nf26 的文本。"""
        target = copy / "buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts"
        text = target.read_text(encoding="utf-8")
        target.write_text(text.replace('"-Anekojs.platform=$platformTag",',
                                       '// "-Anekojs.platform=$platformTag",'), encoding="utf-8")

    def lay_node_evidence(copy, node, prefix, failures=None, omit=None):
        """给某节点铺设最小 gate + check + artifact 证据。"""
        build = copy / "versions" / node / "build"
        (build / "test-results/test").mkdir(parents=True, exist_ok=True)
        (build / "test-results/test/TEST-Stub.xml").write_text(
            '<testsuite name="Stub" tests="1" failures="0" errors="0"/>', encoding="utf-8")
        (build / "libs").mkdir(parents=True, exist_ok=True)
        (build / "libs" / ("%s0.jar" % prefix)).write_bytes(b"x")
        gates = build / "nekojs-gates"
        gates.mkdir(parents=True, exist_ok=True)
        if omit != "source-roots":
            (gates / ("source-roots-%s.json" % node)).write_text(
                json.dumps({"node": node, "java": ["src/main/java"], "resources": []}),
                encoding="utf-8")
        if omit != "event-surface":
            (gates / ("event-surface-%s.json" % node)).write_text(
                json.dumps({"node": node, "rows": [], "failures": failures or []},
                           ensure_ascii=False), encoding="utf-8")
        if omit != "spec-coverage":
            (gates / ("spec-coverage-%s.json" % node)).write_text(
                json.dumps({"node": node, "rows": [], "failures": []}, ensure_ascii=False),
                encoding="utf-8")

    def break_multi_failure(copy):
        """F2：单节点报告带多条 failure，必须逐条出现在同一报告里。"""
        lay_node_evidence(copy, "26.2.0-fabric", "nekojs-fabric-26.2-", failures=[
            "missing-binding domain=A node=26.2.0-fabric",
            "missing-binding domain=B node=26.2.0-fabric",
            "member-drift domain=C node=26.2.0-fabric",
        ])

    def break_nodes_missing(copy):
        """AC3/AC6：节点看起来已构建，但没有 source trace -> 必须记 not verified。"""
        lay_node_evidence(copy, "26.2.0-fabric", "nekojs-fabric-26.2-", omit="source-roots")

    def break_declaration_gap(copy):
        target = copy / "common/build/nekojs-gates"
        target.mkdir(parents=True, exist_ok=True)
        (target / "declaration-parity.json").write_text(json.dumps(
            {"rows": ["members=141"],
             "failures": ["missing-member owner=Text member=of owner_ref=Managed Surface/Probe owner"]}),
            encoding="utf-8")

    def break_declaration_missing(copy):
        target = copy / "common/build/nekojs-gates"
        target.mkdir(parents=True, exist_ok=True)

    print("selftest（每个 case 在临时副本上植入故障）:")
    expect_fail("ci-subset 漏节点", break_subset, gate_subsets, "ci-subset-consistency")
    expect_fail("fabric 偷接 processor", break_processor_wired, gate_processor,
                "fabric-processor-deferral")
    expect_fail("option 被注释掉（F4）", break_option_comment, gate_processor,
                "fabric-processor-deferral")
    expect_fail("节点报告多条 failure（F2）", break_multi_failure, gate_nodes, "node-report",
                expected_details=("domain=A", "domain=B", "domain=C"))
    # 断言具体是"该节点的 source trace 缺失"，而不是靠别的节点没构建也报 node-report 蒙对
    expect_fail("节点无 source trace（AC3/AC6）", break_nodes_missing, gate_nodes, "node-report",
                expected_details=("source trace not verified",))
    expect_fail("declaration 报告带缺口（AC2）", break_declaration_gap, gate_declaration,
                "declaration-parity")
    expect_fail("declaration 报告缺失（AC2）", break_declaration_missing, gate_declaration,
                "declaration-parity")

    def check_source_roots_diagnostic():
        """F5：wrapper/探针不可用时必须打可定位诊断 + 非零返回，而不是 raise。"""
        copy = fresh_copy()
        # 真正制造"跑不起来"：删掉 wrapper（Linux 上硬编码 gradlew.bat 就是这类失败）
        for name in ("gradlew.bat", "gradlew"):
            wrapper = copy / name
            if wrapper.exists():
                wrapper.unlink()
        captured = io.StringIO()
        with contextlib.redirect_stdout(captured):
            code = source_roots(copy, "26.2.0-fabric")
        text = captured.getvalue().strip()
        line = text.splitlines()[-1] if text else ""
        if code == 0:
            problems.append("source-roots 未变红（wrapper/探针失败被当成成功）")
        elif not (line.startswith("FAIL source-roots") and "node=26.2.0-fabric" in line
                  and "input=" in line and "expected=" in line and "owner=" in line):
            problems.append("source-roots 失败诊断格式不含 node/input/expected/owner: %r" % line)
        else:
            print("  OK   %-30s -> %s" % ("source-roots 失败诊断（F5）", line[:68].strip()))

    check_source_roots_diagnostic()

    if problems:
        for line in problems:
            print("  FAIL " + line)
        return 1
    print("selftest 全部通过：每个 gate 都会因对应输入变化而变红")
    return 0


# ---------------------------------------------------------------- 主入口

def main():
    parser = argparse.ArgumentParser(description="NekoJS 工单 33 build/CI gate（只读）")
    parser.add_argument("command", choices=["all", "subsets", "processor", "declaration",
                                            "nodes", "source-roots", "selftest"])
    parser.add_argument("--json", action="store_true", help="输出机器可读 JSON（供 CI 汇总）")
    parser.add_argument("--node", help="source-roots：节点名")
    parser.add_argument("--out", help="all：把 JSON 报告写到该路径")
    args = parser.parse_args()

    if args.command == "selftest":
        return selftest()
    if args.command == "source-roots":
        if not args.node:
            print("FAIL source-roots subject=<none> node=<none> input=命令行 expected=--node <节点名> "
                  "owner=build-convention owner :: 缺少 --node")
            return 1
        return source_roots(REPO, args.node)

    registry = {"subsets": gate_subsets, "processor": gate_processor,
                "declaration": gate_declaration, "nodes": gate_nodes}
    gates = ([gate_subsets(), gate_processor(), gate_declaration(), gate_nodes()]
             if args.command == "all" else [registry[args.command]()])

    payload = {"gates": [g.report() for g in gates]}
    if args.out:
        target = REPO / args.out
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                          encoding="utf-8")

    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        for gate in gates:
            print("== %s (owner=%s) -> %s" % (gate.check, gate.owner, gate.state))
            for row in gate.rows:
                print("   %-16s %-16s %s" % (row["subject"], row["state"], row["evidence"]))
            for failure in gate.failures:
                print("   FAIL %s subject=%s node=%s input=%s expected=%s owner=%s :: %s" % (
                    failure["check"], failure["subject"], failure.get("node"),
                    failure["input"], failure["expected"], failure["owner"], failure["detail"]))
        failed = sum(len(g.failures) for g in gates)
        print("\n结果：%d 个 check，%d 个失败条目" % (len(gates), failed))
    return 1 if any(g.failures for g in gates) else 0


if __name__ == "__main__":
    sys.exit(main())