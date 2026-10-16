# 领域切换复核修复与 Python 完整声明验收

起点 `20809213`，分支 `codex/architecture-closeout`。本批主代理独自修改；Luna xhigh 仅只读调查/复核。票 28、37 继续开放，不变更发布版本或执行远程发布。

## 改动与契约

- 修复领域 review 的动态物品重复声明问题：已存在的物品保留 holder 组件和 tag，首次注册仍初始化组件；资源重载仍由已有 initializer/replay 路径负责重建。旧官方 JAR 实机复现组件被清除，新 JAR 同一流程保留组件。两次均等到重载后 40 个 owner tick 再观察，正常停止。
- 修复同名插件 Builder 的嵌套属性声明：TS/Python 属性与回调类型引用唯一导出别名。新增两个同名 FoodBuilder 互相引用的回归。
- 修复实际生成的 Python stub 中同一类实例字段/方法重名导致的重复定义。原 IR 和运行时名称保留；单一 property getter 返回字段类型与私有 callable Protocol 的联合，setter 继续使用原字段/Bean writer 类型，final 字段只读。普通与 Bean 冲突均有回归。
- 修正交易查询 Javadoc，使 committed generation 的计数与保留的物理省略项区分清楚；将中文基线报告的新增说明恢复为中文。

Python 联合类型是明确近似：Graal 的 `target.x` 与 `target.x()` 可分别解析到字段和方法，但 Python 类型系统不能按该语境选择分支，因此直接调用联合类型仍可能报告不可调用。这里没有声明取出的标量可调用，没有添加 Any/suppress/exclude，没有扩展扫描范围；static 和继承冲突不属于本次实机 7/13 个诊断修复范围。

## 验证结果

```text
.\gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
python tools/nekojs-ci-gates.py all --out build/domain-closeout-reviewed-ci-gates.json
python tools/nekojs-ci-gates.py source-roots --node <each supported node>
python docs/architecture-refactor/evidence/2026-10-16-followup-acceptance/verify.py
```

完整构建 PASS，3m28s，109 tasks（37 executed / 72 up-to-date）。七个模块的现存完整测试 XML 合计 4906 tests，271 skipped，0 failures/errors；skip 未算作通过。Probe 类型回归、四项 CI gate、五节点 source roots 均 PASS。新增 Python focused suite 和 Builder 碰撞回归先独立通过，再进入完整构建；先前编译/断言失败记录保留在归档。

每个官方 JAR 都逐项核对 common 与节点编译 class 的字节，准备清单绑定 `20809213` 加生产源码 patch SHA256。本批所有生产代码在完整构建前冻结，后续只补验证工具和文档。五个全新专属服务器均完成注册、嵌套配置、方块、重载、Python AST；NeoForge 另完成流体及真实交易物理池生命周期，现代 NeoForge 完成空动态代际保留与 reclaim。全部 runtime verdict PASS，五次 RCON stop 后 exitCode=0。

完整默认 Pyright 1.1.414 保留已有 basic 模式，逐份包含实际生成的全部 stub；严格 TSC 使用实际默认编辑器入口、`skipLibCheck=false`。没有用定向 fixture 代替完整检查。

| 节点 | stub 数 / AST | Pyright 错误：上轮 → 本轮 | TSC server / startup |
|---|---:|---:|---:|
| NeoForge 1.21.1 | 320 / PASS | 7 → 0 | 586 / 587，FAIL |
| NeoForge 26.1.2 | 378 / PASS | 7 → 0 | 744 / 745，FAIL |
| NeoForge 26.2.0 | 346 / PASS | 7 → 0 | 638 / 639，FAIL |
| Fabric 26.1.2 | 296 / PASS | 13 → 0 | 1162 / 1163，FAIL |
| Fabric 26.2.0 | 263 / PASS | 13 → 0 | 917 / 918，FAIL |

五节点 Pyright exitCode=0，全部 warning 也为 0。TSC 仍失败，数量与上轮相同，缺模块/类型、泛型及继承冲突尚未收口，完整 IDE gate 仍未通过。

## 证据与剩余项

[reviewed-evidence.zip](reviewed-evidence.zip) 包含实际安装日志、命令、完整生成声明、完整诊断、class/source 绑定、测试 XML、旧/新 JAR 组件对照及验证工具；[清单](reviewed-manifest.json) 和[摘要](reviewed-summary.json)可独立核对。归档不含 JAR、存档、口令或用户 profile。

组件试验保留两次无效的早期验收：第一次 fixture 别名碰撞产生脚本错误，第二次在物理激活前过早观察。两者均未算 PASS；修正验收器并使用新目录、相同延迟重跑旧/新 JAR，得到实际 red/green。

[四阶段回退重启报告](ROLLBACK.md)另行记录完整世界备份与实际 pdata reader 验收，绑定领域切换时的 JAR；不能替代最终候选的发布/回滚决策。完整 TSC、票 28 多节点客户端首帧、确切最终候选性能及最终发布/回滚策略继续开放。本批尚未执行新的客户端首帧或性能测量；没有代签维护者验收。

后续独立验证：[26.2 首帧像素报告](FRAME.md)已用相同生产代码/JAR 补齐指定节点证据；[双轴增量复核](REVIEW.md)最终 Standards 0 / Spec 0。它们不回写以上构建/IDE 结果为整票发布通过。

[枚举继承修复与五节点声明复测](ENUM.md)使用后续 `489b4a6b` 新制品：完整构建、五节点实机和 Pyright 通过，十个严格 TSC 入口减少 160 条诊断、无新增诊断，完整 TSC 仍失败。Python Java 类 stub 逐字节一致；入口/事件模块的顺序与导入别名差异另有原始记录和类型身份比较，不声称全部文件字节一致。

[泛型适配器修复与完整复测](GENERIC.md)使用 `f3f32a69` 新制品：类型参数、bound、嵌套 Self 和编辑后 IR 得以保留，完整构建、五节点实机、Pyright 和定向严格 TSC 通过。完整默认 TSC 移除 40 条裸泛型错误，同时新增 930 条约束错误，净增 890；本批不能按完整声明或无新增诊断验收。真实错误链与原始失败证据保留，完整 gate 仍开放。
