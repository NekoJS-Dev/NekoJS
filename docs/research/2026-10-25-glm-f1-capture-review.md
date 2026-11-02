# GLM §3 首批交付复核

日期：2026-10-25。仓库 `D:/mcmodDemo/NekoJS-mult`，分支 `stonecutter`。固定范围 `c3ee4097...70daade0`，包括 `dd7ec3db`、`f84e3e47`、`914df3c7`、`70daade0`。三名 GPT-6-luna xhigh 子代理只读审查代码合同、证据/驱动及 Spec；主代理独立检查源码、重算诊断并做临时控制复现。没有修改产品代码、启动真实服务端、提交或执行远端操作。

结论：基线重采与诊断减少有真实原文支持，但 F1 修复不能按现状接受；新返回视图缩减了公开声明能力。安装工具也存在需要修正的问题。先修下面的问题，再继续 F4。

## Standards

### [P1] 清理脚本会强杀不属于本次安装的服务端

`bench/tsbaseline/kill-servers.ps1:1–5` 枚举全部 `java.exe`，只按 `win_args.txt|fabric-server-launch` 匹配命令行就 `Stop-Process -Force`。这些是普通 NeoForge/Fabric 服务端的通用启动标记，不含本次 target、进程身份或所有权限制；执行脚本可能强杀用户其他服务端，丢失未保存数据。

应改为仅清理本驱动记录并验证身份的子进程，优先正常停服；强制终止前核对 PID、创建时间和确切安装归属。不能靠追加另一个通用进程名或启动参数过滤解决。此次仅静态审查，没有执行该脚本。

### [P2] probe 抛错时跳过等待退出与后续资源回收

`bench/tsbaseline/run.py:266–280` 的 finally 只尝试发送 stop。probe 异常会在 finally 后继续传播，下面的 `proc.wait`、超时处理、文件关闭与结果保存不会执行；若 stop 也失败，已启动的 owned 服务端可能留下。

主代理在系统临时目录用假进程/假 RCON 调用真实 `cmd_run`：probe 和 stop 均抛 RuntimeError 时，stop 尝试 1 次，wait 0 次、kill 0 次。没有启动或终止真实进程。

应让正常与异常路径都进入同一个 owned 进程回收 finally，保存失败和清理结果，并保留原始异常；发生启动/readiness/probe/stop 失败均需覆盖。

### [P2] 编译器启动失败被汇总成“诊断完整、零错误”

`bench/tsbaseline/run.py:333–343` 不校验 tsc 返回码，`diagnosticsComplete` 仅依赖 stderr 是否含字面量 `Compilation failed`。若 TypeScript 安装损坏或 Node 找不到 tsc，Node 退出 1、stderr 写 Cannot find module、stdout 为空，驱动仍正常结束，写入 diagnosticCount=0、diagnosticsComplete=true。

主代理用临时目录及模拟编译器结果调用真实 `cmd_check`，得到上述结果。应区分正常完成的零诊断、正常完成但存在 TS 诊断、配置/工具/崩溃等无效运行；无效运行必须显式失败，不能进入诊断改善统计。当前保存的十入口都是真实 exit 2，不能据这个驱动缺陷反推本轮实测是假的。

## Spec

### [P1] 捕获视图通过缩减 Optional/Stream 声明能力消除了部分不兼容

`common/src/main/java/com/tkisor/nekojs/probe/ir/TypeScriptClassRenderer.java:699–701` 将所有符合条件的未编辑 `Optional<? extends Bound>` 输出替换成手写的少数成员。原来通过 `$Optional<Bound>` 可用的公开调用因此消失，包括没有捕获入参的 `orElseThrow()`、Bean `present` 属性，以及可安全接收上界谓词的 `filter(...)`。stream 也只剩 count/findAny/findFirst，下一层 Optional 又不能 stream。

主代理在新 fixture 的临时副本加入下面的正例：

```ts
declare const previousShape: $Optional<ConstDesc>;
previousShape.orElseThrow();
previousShape.present;
previousShape.filter(() => true);
previousShape.stream().findAny().stream().count();

const captureResult = asInterface.describeConstable();
captureResult.orElseThrow();
captureResult.present;
captureResult.filter(() => true);
captureResult.stream().findAny().stream().count();
```

旧形状四项全部可编译；新视图四项分别出现 TS2339/TS2551/TS2339/TS2339。另以 JDK 21 实际编译 `Optional<? extends Number>` 的 orElseThrow/filter/ifPresent/stream.findAny.stream.count 合法调用，javac 退出 0。因此“捕获入参成员都没有健全参数类型、只能留在具体实现上”的解释不能成立。

`ProbeWildcardOptionalCaptureTest.java:98–99` 更将合法的递归 stream 调用写成 `@ts-expect-error`，把这项合同缩减固化为通过条件。只在具体实现上保留 Optional 方法，无法补回经接口/通配返回值访问的能力。既有授权没有允许这种公开成员缩减；一行 golden 变化及全图诊断减少不能证明 API 得到保留。

应将这些调用改为正向合同回归，用来源/成员驱动的可递归捕获表示保留公开能力、Bean/隐藏/编辑约束及合法 SAM 消费，同时保留错误元素的负例。方案未获证明前，不应继续向其他容器推广该缩减视图；若暂时无法完成，应撤下这项生产投影而保留已完成的驱动/基线工作，不能继续用成员缺失作为绿态目标。

### [P2] 根因分类表的计数不闭合

`docs/research/2026-10-25-strict-ts-root-causes.md` 总表逐项相加为 1613，不是声称分类的 1609。F6 的 `432+16` 包含 TS2430，F9 又以 50 条 TS2430 成族，需明确是否重叠并调整其它项目，使分类能与每条原始诊断对上。表头称节点列为 server 入口，但 F1 的 `28×5` 对应的是两入口合计，也需统一口径。

请保存可重算的诊断到主根因族映射，主分类总数闭合为 1609；连带机制可以另列标签，不能重复累加成互斥分类。此问题不否定主代理已重算出的原始诊断总数或 −106。

## 已独立核实的进展

- 本轮新定向命令 `npm.cmd run test:probe-wildcard-optional` 退出 0；它只能证明现有用例通过，不能覆盖上述新增正例。
- 主代理直接重算两个本地安装目录各十份 `*-tsc.txt`：基线 1609，修复后 1503。TS2416 为 804→714（−90），TS2430 为 50→34（−16），其余错误码总量未变。
- 节点逐入口基线为 111/112、165/166、159/160、186/187、181/182；修复后为 102/103、154/155、148/149、175/176、170/171。全部是仍失败的完整严格 TS 结果。
- 没有重新执行 common/check、全链 npm、五节点构建或游戏安装；GLM 所述这些结果需保持原始证据的提交与运行范围。本次合法调用和驱动异常控制只使用系统临时目录，已自动清理。

额外绑定边界：驱动 `prepare` 接受调用者给定的 revision，按 mtime 选 JAR 并保存摘要，但不自行证明两者的构建关系。不能把填入 revision 当作自动完成源码绑定；后续应保留实际构建、class/制品对应及加载来源证据。本次没有发现某个当前 JAR 已实际错绑，不另报为已发生的产品缺陷。

## 给 GLM 的下一步

1. 先限制/替换通用强杀脚本，修正驱动异常回收及无效编译处理，保留失败过程并加对应故障控制。
2. 修复 F1 的公开合同回归，移除把合法递归调用当负例的断言；不要只补这四个例子后仍任意截断其他合法方法。
3. 修正分类表并保存可重算映射；重新走生成和定向严格正负消费、common/check 与完整定向链。修复收敛后再以确切产物重跑十入口，重新报告诊断增减。
4. 之后再进入 F4；最终候选、客户端/TCP/分离回退、版本包装及维护者结论仍开放。原性能 PASS 绑定 d06cd961，不自动代表新的最终候选。

审查汇总：Standards 3 项（最重 P1，进程归属）；Spec 2 项（最重 P1，公开声明能力缩减）。
