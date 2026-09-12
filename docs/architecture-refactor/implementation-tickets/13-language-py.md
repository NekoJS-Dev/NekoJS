# 13: Python 转译、模块行为与诊断路径

**What to build:** 脚本作者写入 .py 后，既有 Python 语法、缩进结构、定义/调用、模块模式和 import 行为经 Preparation 生成可执行 JS/IR，再由 Resolution/Cache 与 Graal 执行；Python 源错误和生成代码运行错误均保留原始位置，缓存与声明不把 Python 误标为 JS。

**Blocked by:** [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 扩展 Python golden/corpus，覆盖缩进、定义/类/调用、注释、模块导入和既有 Python-to-JS 行为的固定输出。, 确保 .py 的 language id、requested mode、cache key、source map 和 prepended-line 语义进入统一 prepared module。, 区分 Python 源解析/转换错误与生成 JS 的 link/执行错误，均能映射回原始 .py 文件。, 让 Python import/require 与 CJS/ESM 依赖图使用同一模块身份与失效规则，不复制第二条 resolver。, 保持 Python 支持和纯 Java自研实现，不引入 Python 运行时或非纯 Java 依赖。, 收缩 gate：Python 旧并行转换或装载旁路只有在替代 behavior、source-map/declaration、trace 通过且无调用者后移除；随本票完成收缩，不推迟 final release，也不删除 Python 支持或公开功能。

## Acceptance criteria

- [ ] .py representative corpus 得到 language id=Python 语义、正确 module mode、可执行 code/IR 和可用 source map。
- [ ] 缩进、定义、调用、注释和导入的既有行为由 golden/corpus 固定，不冻结私有 parser 对象身份。
- [ ] Python 源语法/转换错误在准备阶段携带原始行列；生成代码执行错误也能回映射到 .py。
- [ ] Python 模块身份、依赖图和 cache invalidation 与统一 Resolution/Cache 行为一致。
- [ ] 源码、mode、identity 或依赖变化不会命中旧 Python 产物。
- [ ] Python 与 JS/CJS/ESM/TS 混合加载的错误阶段和模块归属可观察。
- [ ] Python declaration/probe 中的语言与 module 归属不被 JS declaration 覆盖。
- [ ] 不删除 Python 支持，不新增 Python runtime、公共 parser SPI或第二套模块管线。
- [ ] 随实现交付 .py 最小可运行示例与必要迁移材料；示例只使用已通过 gate 的 Python 语法、import 和诊断能力。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [语言模块管线规格](../specs/06-language-module-pipeline.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md): Python 必须复用 prepared module、模块身份、cache key 和 source location 模型；独立 transpiler/load 路径会破坏全语言语义一致性。

## Scope and coordination

- **Rationale:** Python 的完整路径独立验收，因为其源形式、source map 和错误映射与 TS/JSX 不同；但仍阻塞于同一模块基础，避免私有 resolver。
- **Coordination:**
  - MANAGED_SURFACE: Python declaration 与 Probe 输出必须由 managed 契约派生。
  - DIAGNOSTICS: Python 源位置和阶段信息进入统一错误上下文与 workspace 报告。
  - PERF_BASELINE: Python corpus 成本只记录对照，不设定未确认阈值。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
