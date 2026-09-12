# Managed Surface golden / manifest / Probe / declaration 显式 regenerate 流程（ticket 09 / AC9）

> 本文件是 golden 与派生产物基线的**唯一**更新流程说明。普通测试只读基线，任何基线变化
> 必须走本流程并留下旧新 diff、原因、影响与维护者审阅记录（spec 07）。不存在也不允许
> 引入手写第二规范 JSON：manifest 与 declaration 均由 `NormativeApiContract` 反射派生。

## 1. 基线清单与守护测试

| 基线 | 路径 | 守护测试 | 派生输入 |
|---|---|---|---|
| core API manifest | `common/src/test/resources/nekojs/golden/api-manifest-core.json` | `ApiManifestGoldenTest` | `CoreManagedApiBootstrap.buildContract` 反射 → `ApiSurfaceSnapshot` → `ApiManifestGenerator` |
| managed TS declaration fixture | `common/src/test/probe-ts/generated/index.d.ts` | `ProbeTypeScriptFixtureWriterTest`（字节对比；实际输出写 `common/build/probe-ts-actual/`） | 固定 surface fixture → `ManagedApiDeclarationGenerator`；该 golden 同时被 `npm run test:probe-types`（tsc 契约校验）消费 |
| legacy probe bindings/events | `common/src/test/resources/nekojs/probe/legacy-bindings.expected.d.ts`、`legacy-events.expected.d.ts` | `LegacyProbeCompatibilityTest`（经 `ProbeGoldenSupport`） | catalog fixture → TS probe backend |
| legacy probe 树 | `common/src/test/resources/nekojs/probe/legacy-tree/**` | `LegacyProbeTreeTest`（经 `ProbeGoldenSupport`，整树镜像语义） | `LegacyProbeFixture` → TS probe backend |

## 2. 显式 regenerate 命令

前置条件：工作树干净（`git status` 无未提交改动），避免把无关改动混入 diff。

```bash
# 全部 probe golden（legacy-tree / legacy-bindings / legacy-events / probe-ts fixture）：
./gradlew :common:regenerateGoldens --console=plain

# 仅 manifest golden（单测 + 开关；开关必须显式为 true，普通测试恒为只读）：
./gradlew :common:test --tests "com.tkisor.nekojs.core.api.ApiManifestGoldenTest" \
    -Dnekojs.golden.regenerate=true --console=plain

# regenerate 后必须重跑 TS 契约校验（probe-ts golden 被 tsc 消费）：
npm run test:probe-types
```

机制说明：
- `-Dnekojs.golden.regenerate=true` 经 `common/build.gradle` 透传为 test system property；
  golden 测试在 regen 模式下把实际产物**写回 src/test/resources 源树**（`ProbeGoldenSupport`
  会把 Gradle 的 `build/resources/test` 副本映射回源树），随后 assumption 跳过断言。
- 普通测试（不带开关）只读 golden；`ApiManifestGoldenTest.regenerateSwitchIsOffForNonTrueValues`
  守护开关不会被普通运行误开（build.gradle 无条件透传字符串 "false" 也不会触发写回）。
- 本票新增的派生测试（`ManagedSurfaceDerivationDeterminismTest`、
  `PythonDeclarationDeterminismParityTest`、`ManagedSurfaceEndToEndChainTest`）不写任何
  golden：确定性验证在内存/临时目录完成。

## 3. 旧新 diff、原因、影响与审阅记录（必填）

regenerate 之后、提交之前：

```bash
git diff -- common/src/test/resources/nekojs common/src/test/probe-ts
```

1. **逐文件审阅 diff**，确认每一处变化都能对应到一个已审阅的契约变化
   （facade/数据类型/事件注册类的增删改、能力条件变化、版本号 bump）。
   出现无法解释的 diff 即回滚重跑（先 `git checkout -- <路径>`）。
2. 在提交说明（或本目录 REPORT 的「golden 差异」小节）记录：
   - **原因**：哪个契约/输入变化触发（引用 commit/facade 符号清单）；
   - **影响**：受影响符号、module、capability 与迁移面（breaking 与否，是否进迁移表）；
   - **旧新 diff**：粘贴或引用 `git diff` 输出要点；
   - **审阅记录**：维护者确认记录（谁、何时、结论）。
3. 只有带上述记录的基线变化才允许提交；普通测试不允许带 regen 开关跑 CI。

## 4. 纪律红线

- 普通测试永远不写 golden / manifest / Probe 基线 / declaration 产物（spec 07 Testing Decisions）。
- regenerate 产物只来自反射派生；**手工编辑基线文件**（而不是修输入再 regenerate）视为无效变更。
- 不新增手写第二规范 JSON；契约唯一规范源是 `CoreManagedApiBootstrap.buildContract` 的反射输入
  （见 `NormativeApiContractOwnerTest`）。
- 收缩 gate（工单 Work item 7）：只有替代 contract/manifest/Probe/declaration 的 behavior、
  golden 与 trace 均通过且旧路径无调用者后，才移除对应旧生成或观察旁路。
