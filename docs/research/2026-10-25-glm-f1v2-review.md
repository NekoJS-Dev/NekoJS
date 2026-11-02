# GLM F1v2 与驱动修正复核

日期：2026-10-25。固定范围 `70daade0...24c1660d`，对应 `d075db12`、`fab58a7d`、`24c1660d`。三名 GPT-6-luna xhigh 子代理只读审查产品、需求与驱动/证据；主代理独立执行定向 TS 检查、分类器及临时故障控制。未修改产品代码、执行真实进程终止、启动游戏、提交或推送。

结论：上轮点名的四项调用、常见探针异常的进程回收及空诊断编译器启动失败已有实质修正，但“全部复核项已修复”仍不成立。命名视图继续省略合法公开方法，分类器也没有真正拒绝未知诊断。

## Standards

### [P1] 重载过滤会连同安全的无参签名整组删除

Standards 子代理确认 `TypeScriptClassRenderer.java:806–817,843` 因某个 SAM 重载引用 B 而将整组排除。`sorted(Comparator)` 因而连带删掉 `sorted()`；无参签名不接受捕获元素，返回 stream view，是确定的合法操作。该代码机制与下文 Spec 的公开合同回退是同一问题的两个审查视角，不累计成两个独立缺陷。不能因一个重载难以表示就默认删除整个公开组。

### [P2] 进程身份检查仍允许时间窗口内的 PID 复用

`bench/tsbaseline/kill-servers.ps1` 已改为只处理 target 下的进程记录，消除了旧版全局枚举强杀的问题。但它仍仅检查通用 launcher 标记和记录时间 ±30 秒，不验证记录的 Java 路径或确切安装路径，记录的时间也是 Popen 后的墙钟时间而非实际进程创建身份。

主代理对实际脚本做完全模拟的控制：进程记录 PID 为 999999、时间 T，CIM 返回相同 PID、创建时间 T+5 秒、命令行为另一个安装的 `D:/unrelated-server/fabric-server-launch.jar`，脚本仍调用了模拟 Stop-Process。所有进程查询与停止均由 PowerShell 函数替换，没有查询或终止真实进程。

应记录并精确校验实际进程出生身份与启动路径/参数，身份不一致时 SKIP。新增“同一 PID、30 秒窗口内新进程、不同安装”控制，不应仅验证 100 秒漂移。

### [P2] 失败原因未落摘要，原异常仅保留消息

`bench/tsbaseline/run.py` 的异常处理将原异常转换成字符串，回收后 `raise SystemExit(failure)`；run-summary 没有 failure/error 字段，原异常类型、链和 traceback 均丢失。

主代理用假进程和假 RCON 调用真实 cmd_run：探针和 stop 同时失败时，确实执行 stop 尝试、有界 wait(15)、kill、wait/reap，摘要也写出。这说明上轮的主要孤儿进程问题已修复。随后抛出的对象却是 SystemExit，`__cause__` 与 `__context__` 均为空；摘要只有 node/pid/退出与清理状态，没有 probe 失败原因。

按“记录失败、摘要落盘、保留原始异常”的原要求，应保存失败阶段、原异常类型/消息及清理结果，并在清理后保留原异常或明确异常链。不能将“原消息出现在 SystemExit 文案”表述成原始异常完整保留。

## Spec

### [P1] 命名视图仍删减合法 Optional/Stream 调用

`TypeScriptClassRenderer.transformCaptureViewMember` 的 R1/R2/R3 仍将 `or`、`reduce/sorted/collect`、`toList/spliterator` 从视图中删除。它们在具体实现上存在，并不能补回接口/通配返回值的调用能力。尤其无参 `sorted()` 和无参 `toList()`/`spliterator()` 不需要向捕获元素传入一个上界对象，是明确的合法公开调用。

主代理在当前生成 fixture 的临时副本追加正例：

```ts
captureResult.or(() => otherOpt);
captureResult.stream().sorted();
captureResult.stream().toList();
captureResult.stream().spliterator();
```

tsc 实际分别报 TS2339、TS2339、TS2339、TS2551。另使用 JDK 21 编译真实 `Optional<? extends Number>` 的 `or(Optional::empty)`、`stream().sorted()`、`stream().toList()` 和 `stream().spliterator()`，javac 退出 0。

新测试仍用 `@ts-expect-error` 要求这些成员缺失；这只是把原来的任意截断改成按规则截断。重载配对/容器不变性证明当前表示存在限制，不能证明公开成员删除已经获准。不得以缺失方法来保持 1503 的诊断数；需保留完整来源合同并增加这些正例，不能只补本次指出的少数名字。

上轮四个例子已恢复、递归也已改成命名类型，这些是有效进展；问题是其余合法调用仍被缩减，而不是命名视图本身没有价值。若完整表示暂时无法证明，应撤下生产投影并保留研究及驱动工作，不继续将缺失成员作为绿态目标。

### [P2] 未知诊断仍被无条件归入尾项

`bench/tsbaseline/classify_diagnostics.py` 的最后一条规则为 `("tail-isolated-instances", lambda m, c: True)`，所以 UNMAPPED 检查不可达。主代理直接调用 classify，输入全新的 `TS9999: Unexpected entirely new failure`，结果是 tail-isolated-instances，不会报错。

现有 1609 条能互斥闭合，是因为每条输入必定被某条规则接收；它不等于已证明每条都匹配了显式的已知根因。尾项应限定为明确列出的已知诊断模式，其余返回 UNMAPPED 并非零退出。新增未知诊断的控制，修正脚本和证据页“未知项显式拒绝”的陈述。

## 已核实的进展与范围

- `npm.cmd run test:probe-wildcard-optional` 实际退出 0；本次未重跑全链 npm、common/check 或五节点安装。
- 分类器对真实本地基线目录重新计算 1609，对 f1v2 重新计算 1503；新 F1 分类为 156→50，其余分类计数保持。互斥计算修正了旧表的重复累计。
- 子代理核对映射 JSON：归一化诊断身份后移除 106、新增 0；TS2416 −90、TS2430 −16。十入口仍全部是有诊断的 exit 2，完整严格 TS 没有通过。
- 上轮四项正例已进入新的接口 capture-view 消费；具体实现窄返回保持。本次未发现针对返回槽位 `overridden` guard 的新反例，但这不等于已验证所有底层类型编辑与隐藏场景。
- cleanup T3 的 wait/kill/reap/摘要路径经独立模拟通过；无 TypeScript/零诊断的编译器启动失败现已能拒绝。三态逻辑仍只依据退出码和诊断数，不能仅据此证明“输出部分诊断后崩溃”的运行完整；当前十入口没有这种崩溃证据。
- 清理脚本的操作范围已大幅收紧；本次残留问题是身份匹配精度，不再描述成旧版全局强杀。

## 下一步交给 GLM

1. 保留已完成的四项正例及驱动核心回收修复；先消除其余合法调用被排除的合同回退，不能直接进入 F4。
2. 分类尾项改为显式已知模式，未知项真正失败；补未知错误码/消息控制。
3. 将异常原因写入摘要并保留异常链；收紧实际进程出生身份与安装归属验证，补时间窗内 PID 复用控制。
4. 检查修改后生成结果及合法/非法消费，按仓库规则运行 common/check 和定向链。实现收敛后再用确切产物重采十入口并重算分类；诊断减少必须与公开合同保持同时成立。

性能结果仍绑定 d06cd961，后续最终候选需独立绑定；没有推送、PR、发布或人工验收代签。

审查汇总：Standards 3 项（最重 P1，重载组过滤）；Spec 2 项（最重 P1，合法公开方法缺失）。两轴的 P1 指向同一项公开合同回退。
