# 11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径

**What to build:** 脚本入口把 source/path/extension/requested mode/trust-approved source 交给 Preparation，得到带 language id、module mode、source map、诊断位置和稳定 cache key 的不可变 prepared module；Module Resolution/Cache 用它完成 CJS require、ESM import/link、依赖图、命中/失效和生命周期，最终 Graal 执行结果或错误能映射回原文件；模块 cache/session 的生命周期由 runtime owner 持有。legacy CJS bridge 被显式 characterization，而不是形成第二语义管线。

**Blocked by:** [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 把 NekoModulePipeline/NekoCompilationPipeline/NekoModulePipelineCache 的构造和 cache 注入显式化，移除 process-wide static cache 或 legacy instance 的必要调用；不新增公共 parser ModuleSPI。, 让 NekoPreparedModule 的调用者可观察语义包含 language id、mode、code/IR、source map、原始诊断位置和稳定 cache key，并保持不可变。, 收口 CJS/ESM identity、require/import/link、dependency graph、cache hit/invalidation、legacy bridge 和 module lifecycle 到 Resolution/Cache 路径，平台 callback 不进入 common 模块层。, 为本地 trusted 与远端 explicitly-authorized source 复用同一 prepare/resolve/execute 阶段，只让 trust 决策影响授权结果。, 建立 JS、.mjs/.cjs、循环依赖、未解析 import、identity 冲突、link 失败、内容变化和 ScriptType scoped clear 的 characterization/corpus。, 为 legacy CJS bridge 写明当前语义、删除条件和替代证据；在条件未满足前保留而不删除。, 收缩 gate：JS/CJS/ESM 旧 static cache、legacy instance 或并行装载旁路，只有替代 behavior、source-map/declaration、trace 与无调用者证据齐全后才能移除；清理随票完成，不推迟到 final release，也不删除公开语言。

## Acceptance criteria

- [ ] NekoModulePipeline.prepare 的最高调用者测试证明 JS/CJS/ESM 输入产生正确 language id、module mode、可执行 code/IR、可用 source map 和稳定 cache key。
- [ ] prepared module 不可变，Resolution/Cache 修改源码、身份或诊断上下文时测试变红。
- [ ] CJS require/module.exports 与 ESM import/export/link 的模块身份在重复加载、循环依赖和跨入口调用下保持既有语义。
- [ ] 内容、路径、mode 或 language identity 变化会失效对应 cache；同 stamp 同长度但内容不同的覆盖写入不会返回旧模块。
- [ ] 按 ScriptType 清理只影响目标范围，共享 node_modules/跨类型缓存行为有显式断言。
- [ ] 准备失败、resolve/link 失败、缓存失败和执行失败可区分 owner 与阶段，错误不延迟成无来源的 Graal 异常。
- [ ] 跨 import 的执行错误能经 source map 回到原始文件、行列和模块身份。
- [ ] 本地 trusted 与远端显式授权/拒绝用同一阶段模型观测，拒绝或降级不会隐藏语言边界。
- [ ] legacy CJS bridge 的 characterization、当前保留原因和收缩 gate 可追踪；只有替代 behavior、declaration、trace 通过且无调用者后才移除，不在 final release 统一清理，同一公开语义没有第二条长期 pipeline。
- [ ] Preparation 与 Resolution/Cache 不创建 Graal Context、不决定 HostAccess、不读取 Minecraft/loader；Context、HostAccess、bindings 与执行关闭继续由 Script Execution Environment 负责，且本约束不改变 common 允许 GraalJS 的既有规则。
- [ ] 随实现交付 JS、CJS、ESM 的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的模块能力，缓存/reload 行为与示例说明一致。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [语言模块管线规格](../specs/06-language-module-pipeline.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md): 删除 process-wide static cache/legacy instance 并把模块 session/cache 生命周期改为 runtime owner 持有时，必须以新 root 所有权为验收状态；不得用旧 static 旁路或旧 fixture 假通过。

## Scope and coordination

- **Rationale:** 以 JS 模块行为作为语言链基础，可以让 TS/Python 票接入同一个 prepared/resolved/diagnostic 事实，而不是按 lexer、parser、cache、测试分层开票。
- **Coordination:**
  - RUNTIME_ROOT/RELOAD_COMMIT: candidate/active generation、失败保留和模块 session 清理由 runtime 组定语义；旧 corpus 可先作 characterization；删除 static cache/验收 runtime-owned module session 必须等待 RUNTIME_ROOT，不得用旧 fixture 冒充新状态。
  - GLOBAL_STATE: 模块生命周期不得绕过候选代际中的类型内 global 语义，具体状态事务由 runtime/global owner 负责。
  - PERF_BASELINE: cache 命中率与编译成本只使用 PERF_BASELINE 的本域旧输入或随票补采对照，不自行设定发布阈值，也不用 all-type 空缺替代。
  - MANAGED_SURFACE: 语言 module 归属需要进入声明，但语言票不重定义 managed 规范源。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
