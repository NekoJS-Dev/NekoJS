# Probe 共享原因修复与当前制品复测

本阶段源码候选 `dd15186c`，相对 `495d9cb8` 实现三个修复：多父接口的实际继承签名合并、显式手写 host/input 类型依赖，以及动态物品 wire 布尔值拒绝。全部由主代理实施和验证，Luna xhigh 只读调查与分轴审查；不创建 PR、不推送或正式发布。

## 改动与公开边界

- 接口合并按原 Java 方法名和实际上下文参数 Type 分组，保留不同重载与不同泛型约束，仅折叠相同或可证明协变的返回；生产 HostAccess 回归保留实际调用行为。
- `ManualDeclarationCatalogEntry.references` 显式映射全局别名到实际 Java Class。host 保留宿主成员，input 使用既有适配器、枚举和函数输入规则；数组补齐元素类。依赖仅进入 TS 闭包，保留主扫描、exclude/hide、编辑后 IR、运行时权限与 Python。缺失或受限引用明确拒绝，不填空类型或全图 `any`。
- 原六参数构造与无引用输出保持；record 组件数量增加，依赖六组件 record pattern 的插件源码需要迁移至访问器或新组件列表。保留构造不等于全部 record 元数据兼容。中英文插件指南已有新引用示例。
- NeoForge 26.x 动态物品只接受规范 `true` / `false`，缺省 false 保持；非法值在 PREPARE / STATE_SYNC 实际 Adapter 校验处拒绝，未触达 registry surgery。wire schema、编码和默认关闭 gate 不变。组件证据不充当多人 TCP 验收。
- canonical legacy tree 仅由既有任务再生成，ByteChannel / SequencedSet 增加实际继承方法；两份起点已有修改声明的哈希未变。错误码 `NEKO-4050` / `NEKO-4051` 已登记于中英文参考。

## 验证结果

完整 `:common:check :common-api-processor:test guardLint` 与五节点 `build` 通过，含现有 isolation、platform 与 runtime artifact 门禁。5056 tests，271 skipped，0 failures/errors；没有把旧 nbtSmoke / regenerate suite 计入本次。全部 `npm run test:probe-types` 严格消费链通过。

三个最小缺陷均有先失败后通过记录；接口泛型重载和泛型数组进一步边界回归的原始失败也保留。首次集中构建误用不存在任务而失败，没有进入测试或改变门禁；随后使用实际任务完整通过。

五节点新 JAR 安装至独立新世界，逐个核对全部 common/node class 字节与打包内容，冻结实际 JAR、源文件和 class SHA256。全部完成实际注册、适用的物品/方块/流体、重载及 Probe；NeoForge 验证交易池生命周期，26.x NeoForge 还验证空 generation 保留物理动态效果。五服务端均 RCON 正常停止，exitCode=0。所有 1609 个 Python stub 的默认 Pyright 均 0 error / warning。

| 节点 | 严格 TSC server | 严格 TSC startup | 移除合计 |
|---|---:|---:|---:|
| 1.21.1 | 583 | 584 | 84 |
| 26.1.2 | 644 | 645 | 86 |
| 26.2.0 | 633 | 634 | 86 |
| 26.1.2-fabric | 712 | 713 | 44 |
| 26.2.0-fabric | 687 | 688 | 44 |

十个实际默认入口均 `skipLibCheck=false`，TSC exitCode=2，无编译器崩溃。相比旧归档 6867 条，现为 **6523 条**；按文件、错误码和首行消息的诊断多重集比较移除 **344**、新增 **0**。其中所有 218 条 TS2320 消失，NeoForge 手写引用相关移除另外 126 条；这不代表剩余继承、重名、泛型约束和不可用依赖已经修复。旧基线是 `09fcf574` 加归档 Character 生产补丁，对应后续已提交的字符修复；不是声称旧 HEAD 本身包含该补丁。

Standards：独立只读审查原报告 0 findings，主代理补查发现一处错误码登记遗漏并修复；没有以最初报告掩盖此遗漏。Spec：独立只读审查 0 findings，确认本阶段源码范围并明确其他整票门禁仍开放。两轴分别保留，没有合并排序。

## 未完成范围与证据

完整严格 TSC 仍失败，票 37 继续开放。客户端 PostEffects 剩余节点、真实多人动态注册表同步、最终候选性能/制品与数据回退，以及维护者发布/回滚结论不在本阶段通过范围。历史性能结果不外推至新候选；未做正式版本包装。wiki 结构检查 0 问题，整库严格检查仍有其他主题摘要/翻译审阅问题，未代签人工审阅。

[执行记录](../../../research/2026-10-21-ticket28-37-closeout.md)、[机器摘要](summary.json)、[SHA256 清单](manifest.json)、[原始证据包](evidence.zip)。包内不包含 JAR、世界、RCON 配置或口令。完整生成声明、前后诊断、XML、安装日志、绑定和验证工具均保留；检查归档的命令如下，它不会执行游戏或给出发布通过：

```powershell
python docs/architecture-refactor/evidence/2026-10-21-probe-closeout/verify.py
```
