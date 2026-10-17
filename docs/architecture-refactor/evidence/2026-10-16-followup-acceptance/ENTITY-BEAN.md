# Entity Bean 五处精确迁移与实际声明验收

最终源码/测试冻结 `e551e559`，固定增量 `d2655f5e...e551e559`。用户于 2026-10-17 明确回复「批准这五处精确迁移」，范围见[决策记录](ENTITY-BEAN-DECISION.md)。主代理独自实现与验证，Luna xhigh 只读调查和复核。

## 改动与契约

既有 TypeDocs 渠道新增 `BeanAliasExclusionCatalogEntry`，按目标 Class、getter 名和派生属性名精确匹配。common 不引用 Minecraft 类型；共享版本树插件贡献本节点的 `Entity.class / isAttackable / attackable`。NeoForge 走注解发现，Fabric 的内建发现清单显式包含该插件。

TS backend 每轮用当前不可变 catalog 快照替换排除规则；渲染只略去匹配的派生属性，并保留真实 getter。共享 IR 和 Python 后端不消费此规则，旧快照构造器形状保留兼容重载。收集冻结、common/node 合并、重新 bootstrap 和同一 backend 的有规则/空规则复用均有回归。

脚本作者应把 Entity 类型的 `entity.attackable` 改为 `entity.isAttackable()`。LivingEntity/ArmorStand 的真实 `attackable()` 方法，以及其他类自己声明的属性保留。Java/Graal 的原有运行时属性和方法仍存在；没有改变数据、wire 或运行时访问。

## 构建、回归与复核

```text
.\gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
python tools/nekojs-ci-gates.py all --out build/domain-entity-bean-ci-gates-final.json
python build/domain-closeout-tooling/check-entity-bean-final-strict.py
python build/domain-closeout-tooling/accept-entity-bean-final.py
node build/domain-closeout-tooling/audit-entity-bean.cjs build/domain-raw-input-installed build/domain-entity-bean-final-installed build/domain-entity-bean-final-alias-audit.json
python build/domain-closeout-tooling/compare-entity-bean-final-diagnostics.py
python docs/architecture-refactor/evidence/2026-10-16-followup-acceptance/verify.py
```

最终完整矩阵 PASS，1m17s，106 tasks（35 executed / 71 up-to-date）。七模块 XML 合计 4933 tests、271 skipped、0 failures/errors。常规 Probe 类型回归及四项 CI gate PASS。新增五个 common 回归和每节点一个接线回归均进入完整矩阵。

真实生成的严格 TSC 对照保留两份项目：未应用规则时出现 TS2416、TS2426、TS2344、TS2322 各一条；应用精确规则后 exitCode=0。正例验证子类作为父类 bound 的实参和真实方法；负例继续拒绝错误 bound 及已移除的父类属性。实际 Graal Context 使用生产 host access，验证原属性、父 getter 和子方法的运行时行为保留。

初次源码静态复核发现错误码与既有物品配置错误冲突，已改用未占用的 `NEKO-4047` 并更新中英文索引。首轮实际 JAR 验证又发现 Fabric 未接入显式插件清单，修正后新增节点测试。节点测试初次把未预处理的条件注释当成分支，导致 NeoForge 失败；已改为检查实际发现类，再重跑完整矩阵。初步运行、失败日志和 XML 均保留；它们不算最终五节点精确迁移通过。最终固定增量的静态 Standards 0 / Spec 0。

Wiki 结构检查 PASS。严格 `npm run check:wiki` 仍 FAIL：plugin-development、type-adapters、event-extensions 三个既有主题各有 source/translation 摘要过期，共六项；在 `d2655f5e` 的独立文档快照复现相同六项。本次修改的迁移页与错误索引使用现有工具更新摘要，保留 `humanReview: pending`，没有代签翻译或发布验收。

## 五节点新 JAR 与完整 IDE

每个官方 JAR 都逐项比较 common 与节点编译 class 字节，绑定冻结源码及生产 patch。五个全新独立服务器全部 runtime PASS，正常 RCON stop 后 exitCode=0。完整默认 Pyright 1.1.414 保留 basic 模式，包含全部实际 stub：五节点均 0 error / 0 warning。

完整 TSC 保留原 server/startup 编辑器入口和 `skipLibCheck=false`：

| 节点 | server：前 → 后 | startup：前 → 后 | 每入口新增 / 移除 | attackable 属性：前 → 后 |
|---|---:|---:|---:|---:|
| NeoForge 1.21.1 | 685 → 463 | 686 → 464 | 0 / 222 | 7 → 6 |
| NeoForge 26.1.2 | 859 → 607 | 860 → 608 | 0 / 252 | 7 → 6 |
| NeoForge 26.2.0 | 631 → 599 | 632 → 600 | 0 / 32 | 4 → 3 |
| Fabric 26.1.2 | 1240 → 993 | 1241 → 994 | 0 / 247 | 7 → 6 |
| Fabric 26.2.0 | 858 → 831 | 859 → 832 | 0 / 27 | 4 → 3 |

十个入口由 8551 降到 6991 条，减少 1560 条诊断，多重集逐条比较无新增。完整 TSC 仍 FAIL，不能将定向严格项目通过写成全部声明通过。

实际生成声明经 TypeScript AST 审计：恰好五处 Entity 的 getter alias 被删除，其他 24 处完整保留。每个 Entity 所在包模块只删除一行；其他 Java 包声明逐字节一致，因而父 getter、子类真实方法和其他成员均保留。事件和绑定模块中的顺序差异按 AST 比较；独立名称排序时保留同名 overload 的原顺序，声明事实一致。

五节点全部 Java 类 Python stub 与前一批逐字节一致；入口/事件模块若有顺序或本地导入别名差异，完整类型身份比较相等，不声称整个目录字节一致。独立校验器重新核对实际归档的五处精确删除、24 处保留、其他包字节、Python 类型身份、测试 XML 和诊断多重集，强制五节点及各自两个入口齐全、零新增。它还将两批归档内的全部 TS 声明通过 stdin 交给归档的 AST 工具重新比较，包括 reference/TS 指令，不依赖原工作目录的生成文件或 audit 自报结果。此重放需工作区已有 Node.js 和 TypeScript；不会写文件。 另以内存中的篡改材料验证：删除一个 startup 比较入口、改动 reference 指令，两项均被校验器拒绝；实际归档完整重放 PASS。

## 证据与剩余项

[归档](entity-bean-evidence.zip)、[清单](entity-bean-manifest.json)、[摘要](entity-bean-summary.json)保存最终源/class/制品绑定、完整 XML、严格 TSC 对照、实际运行和 IDE 日志、别名清单及验证工具。首轮 Fabric 接线遗漏的实际 Entity 模块另存 preliminary，不能代替最终证据。归档 9,059,529 bytes、4380 entries，SHA256 `9837c25684b5cbde8739488bc635314db78e5ff83d008778d6079c17345d8615`；完整证据校验 PASS。校验器曾因原始 Windows/npm 日志与 Node 基线日志的 CRLF/LF 差异拒绝文档对照，现只在对照时规范化换行，原始文件和摘要保留。

本次批准的五处迁移完成。MinecartFurnace.push 冲突、其他完整 TSC 错误、最近正式重载性能 FAIL、多节点客户端首帧与最终候选的数据回退/发布决定继续开放。本批没有重跑正式性能、客户端或最终候选回退，没有新增 PR、关闭票 28/37 或执行发布。
