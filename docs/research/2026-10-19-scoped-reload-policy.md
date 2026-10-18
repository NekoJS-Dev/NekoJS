# 单次重载线程策略原型

目标仍是默认条件下两组重载均值 ≤285.3 ms、启动 ≤41173 ms。`6e5bb8f0` 默认 466.4/469.4 ms FAIL 有效；进程级显式 HighQoS 的诊断通过不能替代产品修复。

## 假设与可否证结果

1. Windows 自动调度是主要影响：仅默认 SERVER reload 的 owner thread 在完整 root+recipe/pack 同步调用期间请求关闭 execution-speed throttling，应降低实际 RCON 和原 marker；其他线程与命令间隙保持原状态。
2. scope 外日志/回执等待占主导：即使 RCON 降低，原 marker 仍可能超过门槛，此时不能宣称修复。
3. JIT 等工作线程占主导：进程级有效、owner scope 无效；不据此扩大到永久进程策略。

先在已有 owned 性能工作树创建 `codex/reload-thread-policy-prototype`，基线 `3c94d8c8`。主代理实现、测试及运行；Luna 已完成调用链只读调查，补充依赖调查遇到额度限制，未知项由主代理精确核实。不创建 PR。

## 原型范围

沿用 Minecraft 自带 JNA，不增加 common 或项目依赖；版本树内一个同步 scope 封装 Windows API，三个 loader 命令实现接线。非 Windows/非 SERVER 直接执行原行为；不延长到异步 CLIENT 调度。成功读取并保存 version/control/state 后，仅修改 execution-speed 位；正常返回、提前返回、候选/配方失败均 finally 恢复精确快照；嵌套 scope 分别恢复上一层。查询/设置失败继续原命令，恢复失败必须明确报告，不掩盖已发生的 COMMIT 或原脚本异常。没有全局电源、affinity、优先级或进程策略修改。

Microsoft 的 [SetThreadInformation](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setthreadinformation) 给出 HighQoS/系统管理示例；[结构定义](https://learn.microsoft.com/windows/win32/api/processthreadsapi/ns-processthreadsapi-thread_power_throttling_state)约束三个 ULONG 字段。实际 native 结构固定为三个 32-bit 字段，不能使用 Windows x64 下大小不同的 Java long。

`GetThreadInformation` 官方页面目前未列出 power-throttling 查询，但当前 Windows SDK enum 包含该项，本机实际查询成功。这里不据此保证所有旧 Windows 版本支持查询；失败时不设置未知策略，继续原流程，并报告一次不可用。恢复失败时报告策略可能未恢复，因此不可用警告也不声称“没有 hint”或“已恢复”。

## 验证与去留

先验证真实 native 读取/设置/精确恢复及嵌套、回调异常、原生 API 故障路径，再检查命令 scope 覆盖 root、配方、数据包和结果报告；common 保持隔离。冷首次调用的 JNA 加载成本保留。

原型提交后，在 clean owned checkout 上完成五节点构建与相关回归，再使用原 `bench/perf/sample.ps1`（SHA-256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`）、原 fixture/参数/端口/缓存，预先指定 reload-1、reload-2、startup 三组；两次预热加五次正式启动，每组五次重载。保留首值、失败及正常停服要求；期间不并行本任务构建或其他游戏，不运行外部 QoS controller。

原门禁通过且恢复约束成立才将实现带回 stonecutter；否则保持独立原型，记录失败，不合入产品。维护者接受、长期内存、Fabric 资源桥等目标分别记录，不由本轮性能得分代替。

## 结果：否决原型，不合入

原型 `8ca66bf4` 五节点构建、guardLint、七十项最终定向检查及四项 CI gate 均通过，本机 native 独立观测确认其他线程不受影响、owner 精确恢复。但原标准正式两组重载为 **396.0 / 391.6 ms，均 FAIL**，启动 **61690.6 ms，FAIL**。全部九次会话正常停服，无策略故障日志；没有因分数不佳重跑或删值。因此只保留在独立原型分支，stonecutter 不加入该策略。

[冻结原始证据与独立复核器](../architecture-refactor/evidence/2026-10-19-scoped-reload-policy/README.md)可重算全部判定。两组 RCON 自身已达 312.4 / 319.2 ms，继续在同会话拆分实际 owner CPU、阶段墙钟与返回前等待；不将不同日期历史值与本轮相减声称源码收益。
