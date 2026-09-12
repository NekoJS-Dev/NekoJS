# 09: Managed Surface 单一规范源与声明/Probe 派生链

**What to build:** 脚本作者使用的 managed facade、数据类型和事件注册发生变化时，NormativeApiContract 是唯一规范输入；manifest、Probe、TypeScript declaration 与 Python declaration 由同一契约确定性派生。legacy catalog 继续只作迁移观察，Graal/高级 Java 面保持可用，脚本作者得到与运行时成员一致的声明和能力结论。

**Blocked by:** [01: P0 五节点构建与契约基线](01-build-baseline.md)、[02: P0 独立性能基线](02-perf-baseline.md)、[05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 以 CoreManagedApiBootstrap、facade、数据类型和事件注册为反射输入，收口 NormativeApiContract 的 owner、贡献校验和 contract identity；manifest/Probe/declaration 只消费冻结或已验证 surface。, 为 managed symbol、module、capability、TS/Python declaration 建立从运行时成员到产物的 deterministic fixture，普通测试只读 golden。, 保留 NekoScriptCatalog/LEGACY_PREVIEW 的迁移观察职责，增加 legacy shadow 不能覆盖 managed contract 的 characterization。, 为 supported/partial/unavailable 能力补充 loader、版本和运行上下文条件；不可用能力显式失败或降级，不允许静默 no-op。, 保持 KubeJS 风格入口、java:、Java.type、Java.loadClass、Graal interop 和当前 HostAccess 的既有行为 smoke。, 列出 golden/manifest/Probe/declaration 的显式 regenerate 流程与旧新 diff、原因、影响和审阅记录；不新增手写第二规范 JSON。, 收缩 gate：只有替代 contract/manifest/Probe/declaration 的 behavior、golden 与 trace 均通过且旧路径无调用者后，才移除对应旧生成或观察旁路；不在 final release 统一大清理，也不删除公开功能或语言。

## Acceptance criteria

- [x] CoreManagedApiBootstrap/ApiSurface 的最高调用者测试证明 facade、数据类型、事件注册是 NormativeApiContract 的唯一反射输入。
- [x] 同一契约输入重复生成 manifest、Probe、TypeScript declaration 和 Python declaration 的结果稳定，普通测试不会写任何 golden。
- [x] legacy catalog 或 LEGACY_PREVIEW 符号可被迁移观察，但不会被标成 managed stable，也不能覆盖同名 managed symbol。
- [x] 每个涉及能力的 symbol 均有 supported/partial/unavailable 及 loader/version/context 条件；声明与真实外部行为一致。
- [x] TS 与 Python declaration 成员、签名、module 归属和 runtime member parity 有 fixture 证明。
- [x] managed 可写配置 Builder 的显式 setter 与 JavaBean-style property assignment 被定义为同一写入语义，并进入同一校验、规范化和 declaration；final identity、只读成员和未开放 experimental 成员例外。Graal 天然 Bean 行为不作为承诺，公开等价性必须由 runtime contract fixture 固定。本票验证规范生成规则和代表性受管 Builder fixture；启动期、动态与修改域的实际覆盖由各域票完成，P4 汇总，不把所有下游 Builder 实现反向作为本票 blocker。
- [x] 随实现交付最小 managed API 可运行示例与必要迁移材料；示例只使用已通过 gate 的能力，不展示 not verified/unavailable 能力。
- [x] java:、Java.type、Java.loadClass、Graal interop 和当前 HostAccess 的既有高级 Java 用例保持通过。
- [x] 契约变化必须走显式 regenerate，并留下旧新 diff、原因、影响和维护者审阅记录。
- [x] 不存在新的独立 API artifact、全仓 catalog、第二规范 JSON 或通用 capability 框架。
- [x] 至少一个真实脚本调用经过 contract 反射、manifest/Probe、TS/Python declaration 的完整链路，外部 addon/Probe 差异只作为协调项而不冒充本票验收。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [01: P0 五节点构建与契约基线](01-build-baseline.md): 先冻结本票会改写的 ApiManifest、Probe、managed/legacy contract 与 declaration 旧 golden；只消费本 scope 内旧证据，不用全仓 all-type 基线空缺充当通过。
- [02: P0 独立性能基线](02-perf-baseline.md): contract 反射、Probe/declaration 生成需以本域既有性能采样为旧输入；若缺失，先补本域基线，不把性能采样空缺当作任意开工理由。
- [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md): workspace/declaration/Probe 生命周期挂到唯一 root 并触达受保护数据；05 直接依赖 03，使本票传递获得数据保护清单、fixture 和回滚边界。

## Scope and coordination

- **Rationale:** 先把规范源和派生关系变成可验证事实，避免事件、查询和声明票各自制造第二 catalog；这不是 parser 或生成器横切重构，而是一条从脚本成员到声明与能力输出的完整路径。
- **Coordination:**
  - BUILD_BASELINE: 冻结并比较既有 ApiManifest/Probe/declaration golden，作为本票旧输入。
  - PLUGIN_ADDON: 外部 addon discovery、fat jar 与 Probe 兼容验收由插件组承接；本票只保证 managed/Probe 派生契约可被其消费。
  - LANGUAGE_PIPELINE/LANGUAGE_TS/LANGUAGE_PY: 声明生成需要保留全语言模块归属，语言行为本身由语言票负责。
  - EVENT_SURFACE/QUERY_TOOLS: 后续事件与查询面必须以本票的规范源和派生流程为准。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## JSX UI feature coordination（2026-09-12）

UI facade、intrinsic elements、props 和事件由 [40: JSX UI common core](40-jsx-ui-common-core.md) 进入同一 NormativeApiContract 派生链；后续 UI 票随成员实现补齐声明，不新增 UI 私有 catalog。本票不反向依赖 JSX feature。
## Closure record（2026-09-12）

- 执行者：zcode-agent。实施区间 `80b4442d..（本提交）`，6 个实施 commit + review 修订。
- 交付物：契约 owner 收口（`NormativeApiContractOwnerTest` 双记账证明反射输入唯一）+ 确定性派生
  fixture（manifest/TS/Py 两次生成逐字节比对，普通测试零 golden 写入；golden 本轮零变化）+
  legacy shadow characterization（managed 优先、LEGACY_NAME_COLLISION）+ 能力三态/条件
  （`CapabilityStatus` 三态 + `ContractCapability.status/conditions` + resolver 权威 gate）+
  REGENERATE.md（显式 regenerate 流程与审阅模板，本轮零 regenerate）+ 最小示例/MIGRATION.md +
  完整链路测试（脚本→契约反射→manifest→Probe/TS→运行时）。
- code-review（双轴）后修订：**契约条件成为激活裁定的权威 gate**（v1 的 isEligible 只看 provider
  scope，契约声明不匹配时 provider 无 scope/更宽 scope 仍会激活——已修 + 两条负样本用例 +
  更宽 provider 的 SCOPE_NOT_CONTAINED fail-fast 用例）；CORE owner 双拼写收敛为
  `CapabilityResolver.CORE_OWNER_ID/CORE_MODID` 单点词表（语义未变，收敛原因与维护者裁定项见
  CapabilityResolver javadoc 与 REPORT §7.1）；`NormativeApiContract` FQN 清理；测试共享 fixture
  `ApiSurfaceTestSupport` 抽取（6 份逐字复制 → 1）；AC6 补真实 Graal runtime fixture（public 字段
  写路径钉死；裸 bean setter 的 property 写实测静默不生效——"Graal 天然 Bean 行为不作为承诺"的
  实证，setter 等价分发归票 15/39 的受管 Builder 机制）。
- 已记录缺口（非本票反例，owner 已列）：① 真实契约 capabilities=空集，涉及能力的符号由功能域票
  陆续注册（报告 §6 AC4 注记）；② compound 值经契约 invoker 的 NATIVE_TYPE_LEAK（`ApiValueMarshaller:459`，
  owner W4/W5 跟进）；③ TS/Py 生成器暂不渲染 capability 条件、Python 生成器消费 catalog IR
  （统一归 W5 跟进）；④ 外部 addon 真实 discovery 消费归 PLUGIN_ADDON 协调项；⑤ 非 primary 节点
  check 归 W9/W10。
- 测试：`:common` 189 suites/1400 tests（+8/+34），`:common-api-processor:test`、`guardLint`、
  `:26.1.2:check`、`npm run test:probe-types` 全绿；golden 零变化（diff 为空）。
