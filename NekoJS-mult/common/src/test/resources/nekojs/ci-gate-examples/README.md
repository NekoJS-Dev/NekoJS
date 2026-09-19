# 工单 33 最小可运行示例：build/CI gate

这三条命令就是本票交付的 gate 全部入口；全部只读，不写任何 golden、不新增 Gradle project。
示例只使用已通过 gate 的能力（本文件不展示 not verified 的路径）。

## 1. 全量（CI 用）

```bash
for node in 1.21.1 26.1.2 26.2.0 26.1.2-fabric 26.2.0-fabric; do
  python3 tools/nekojs-ci-gates.py source-roots --node "$node"
done
python3 tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json
```

退出码：0 = 四个 check 全绿；1 = 有失败条目（每条都带 node/input/expected/owner）。

## 2. 单类（定位用）

```bash
python3 tools/nekojs-ci-gates.py subsets       # CI 子集用途一致性
python3 tools/nekojs-ci-gates.py processor     # Fabric processor 延期
python3 tools/nekojs-ci-gates.py declaration   # declaration parity
python3 tools/nekojs-ci-gates.py nodes         # 五节点 check/artifact/source trace
```

## 3. 自检（证明 gate 会红）

```bash
python3 tools/nekojs-ci-gates.py selftest
```

在仓库副本上植入故障（漏节点 / 偷接 processor / declaration 缺口 / 节点无证据），
逐条断言对应 gate 变红。

## 失败输出形态

```text
FAIL <check> subject=<subject> node=<node> input=<file> expected=<...> owner=<owner> :: <detail>
```

例（真实运行输出，见 baseline evidence）：

```text
FAIL ci-subset-consistency subject=:26.2.0-fabric:build node=26.2.0-fabric input=.github/workflows/ci-build.yml \
  expected=run 命令块含 :26.2.0-fabric:build owner=build-convention/CI-build owner \
  :: 子集缺少期望的节点任务（节点 26.2.0-fabric）
FAIL fabric-processor-deferral subject=26.2.0-fabric node=26.2.0-fabric \
  input=buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts \
  expected=Fabric 节点不挂 :common-api-processor（1.2.0 延期） owner=build-convention owner \
  :: Fabric convention 挂上了处理器 —— 延期被偷换成临时接线
```

## 节点内 gate（Gradle）

```bash
./gradlew.bat :<node>:platformGateTest       # contract/spec + event/surface（已挂进 :check）
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.api.ManagedDeclarationCoverageGateTest
```

逐项输出落在 `build/nekojs-gates/*.json`：
`spec-coverage-<node>.json`、`event-surface-<node>.json`、`source-roots-<node>.json`、
`common/build/nekojs-gates/declaration-parity.json`。
