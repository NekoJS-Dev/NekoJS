# NekoJS 实现票据拆分草案

Status: draft
Type: ticket-breakdown

本文件供维护者确认拆分粒度和阻塞关系，不是已发布实现票据，也不构成源码实施授权。生成依据为全部已确认 spec、实施交接单和功能覆盖账本，不修改或重新打开任何源决策。

## 拆分约束

- 每张票交付一个能独立演示或验证的行为路径，包含必要实现、调用者 Interface、声明/平台适配、成功失败测试和删除前提；不按接口层、实现层、测试层分别拆票。
- 依赖只表示缺失输入会阻塞验收路径；共享文件、Minecraft运行实例、构建锁和同一进程的采样互斥属于协调事项，不据此硬串整个依赖图。
- 必要的行为基线和窄范围预整理先于相关修改；性能基线独立于构建清单。旧态采样必须在相关行为被改动前完成，允许使用隔离的旧提交工作树采集，不把无关文件改动当成语义先决条件。
- 对确实无法垂直落地的机械广域变更，采用有删除条件的内部 expand–migrate–contract；每个迁移批次大小由实际影响面决定，不预先发明无证据的过渡层。无法保持独立绿色的批次共享整合分支，只有整合验收票可宣称全绿。
- 临时过渡不增加第二 runtime owner、第二语义管线或第二事实源；最终一次 1.2.0 clean cutover 必须只剩单套标准实现，不保留长期兼容 shim。
- 每张票从现有最高调用者 Seam 测试外部结果，普通测试不更新 golden；缺少验证不等于 capability 不可用。失败时保留已定的 runtime/data 安全边界，源码/版本/持久格式变化遵循各自验收和授权条件。
- 性能发布策略须由维护者在取得 P0 数据后、进入 P4 前确认；最终复测按该策略判定。维护者四类试做也需要真实人工参与，代理不能替维护者填写认可。
- 默认先生成供审核的拆分。通过本草案后，按本仓库 tracker 约定在 docs 下的一票一文件目录发布，而不是生成 GitHub issue 或重复 .scratch 镜像；原决策/spec 保留原状。

## 待确认的票据清单

共 **37 张必选票 + 1 张可选票**。编号按一种合法拓扑顺序排列，不等于强制执行顺序；例如 Fabric raw 源迁移只需构建基线，不必等待编号更小的所有功能票。

01. **[01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)** — 依赖：无（获准实施后可从这里开始）。交付：采集五节点旧态源、制品、契约和测试基线。

02. **[02 P0 独立性能基线](#02-p0-独立性能基线)** — 依赖：无（获准实施后可从这里开始）。交付：独立采集 startup、Probe、reload、tick 与内存性能旧态。

03. **[03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)** — 依赖：[01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)。交付：建立必需的旧数据回读与迁移回滚保护。

04. **[04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)** — 依赖：[02 P0 独立性能基线](#02-p0-独立性能基线)。交付：维护者基于基线，在 P4 前确定性能发布政策。 **需维护者参与。**

05. **[05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)** — 依赖：[01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[02 P0 独立性能基线](#02-p0-独立性能基线)。交付：两 loader 共用唯一运行时 owner，移除 static root 旁路。

06. **[06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)** — 依赖：[05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)。交付：候选成功才交接，失败保留旧 generation。

07. **[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)** — 依赖：[06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)。交付：同类型串行、close 优先、watchdog 后显式恢复。

08. **[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)** — 依赖：[06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)。交付：真实 addon 从发现、贡献到产物消费及 reload 存活。

09. **[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)** — 依赖：[01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[02 P0 独立性能基线](#02-p0-独立性能基线)。交付：统一 managed 规范源与声明、manifest、Probe 派生。

10. **[10 按类型 global、显式 shared 与候选顶层写集联合提交](#10-按类型-global显式-shared-与候选顶层写集联合提交)** — 依赖：[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)。交付：分类型 global 与显式 shared 的顶层写集联合交接。

11. **[11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)** — 依赖：[05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)。交付：JS/CJS/ESM 模块身份、缓存失效与执行闭环。

12. **[12 TS/JSX/TSX 编译、source map 与执行行为路径](#12-tsjsxtsx-编译source-map-与执行行为路径)** — 依赖：[11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)。交付：TS/JSX/TSX 转译到执行及 source map 回映射。

13. **[13 Python 转译、模块行为与诊断路径](#13-python-转译模块行为与诊断路径)** — 依赖：[11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)。交付：Python 转译、模块运行及错误定位闭环。

14. **[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)** — 依赖：[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)。交付：闭合既有事件派发、声明、取消与清理。

15. **[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：启动期 Builder、类型转换与连带注册闭环。

16. **[16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：形成可测的惰性定义计划，不提前修改 live registry。

17. **[17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)** — 依赖：[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)。交付：网络只注册一次，wire 保持兼容，回调进入正确代际。

18. **[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)** — 依赖：[17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)。交付：PData/ClientData 同步、旧数据和失效边界保持。

19. **[19 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](#19-脚本包分发-trust-决策远端包激活与-fabric-world-现状)** — 依赖：[17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)。交付：脚本包分发、信任、激活和断线卸载闭环。

20. **[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)** — 依赖：[19 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](#19-脚本包分发-trust-决策远端包激活与-fabric-world-现状)。交付：管理权限、packs/trust 与 reload 结果通过唯一入口消费。

21. **[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)** — 依赖：[16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)、[17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[10 按类型 global、显式 shared 与候选顶层写集联合提交](#10-按类型-global显式-shared-与候选顶层写集联合提交)。交付：验证多人准备、确认、激活和失败门禁，不假称分布式原子性。

22. **[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：交易声明事件与第一版 add、稳定只读查询。

23. **[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：迁移既有 recipe、loot、tags 与 viewer 事件，不重复造事件。

24. **[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：验证 capability、goal 与实体行为事件完整调用路径。

25. **[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)** — 依赖：[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)。交付：保留 DataMap/EntitySelectors 查询工具及现有契约层级。

26. **[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：客户端输入、keybind 与 HUD callback 的代际生命周期。

27. **[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：GUI/render 资源从注册到呈现、关闭与清理。

28. **[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：声明事件与运行期 binding 分离，资源失败保留旧态。

29. **[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)** — 依赖：[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)。交付：复用唯一资源生成事件，验证生成、reload 与回读。

30. **[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)** — 依赖：[12 TS/JSX/TSX 编译、source map 与执行行为路径](#12-tsjsxtsx-编译source-map-与执行行为路径)、[13 Python 转译、模块行为与诊断路径](#13-python-转译模块行为与诊断路径)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)。交付：错误位置、代际、日志、telemetry 与用户报告一致。

31. **[31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)** — 依赖：[01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)。交付：迁移 Fabric raw 源与资源，显式挂载并保持 smoke。

32. **[32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)** — 依赖：[31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)。交付：证明五层 source/artifact 唯一性后删除 bridge。

33. **[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)** — 依赖：[32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)。交付：CI 子集一致，Fabric processor 延期有真实替代 gate。

34. **[34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)** — 依赖：[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)。交付：汇总五节点、契约、能力、迁移和 runtime 的整体验收。

35. **[35 P4 性能复测与政策对照](#35-p4-性能复测与政策对照)** — 依赖：[04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)。交付：按已确认政策复测并对照 P0，不临时虚构阈值。

36. **[36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)** — 依赖：[04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)。交付：维护者完成新增事件、Adapter、扩展点、版本四类试做。 **需维护者参与。**

37. **[37 1.2.0 clean cutover 与发布交接](#37-120-clean-cutover-与发布交接)** — 依赖：[34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)、[35 P4 性能复测与政策对照](#35-p4-性能复测与政策对照)、[36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)。交付：单次 1.2.0 cutover、确切候选制品复验与本地交接。

38. **[38 离线 validator / migration report（可选）](#38-离线-validator--migration-report可选)** — 依赖：[03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)。交付：可选只读报告，不阻塞必选票或发布。

## 逐票验收草案

### 01 P0 五节点构建与契约基线

**What to build：** 在旧行为改变前，生成五节点实际源/产物、测试发现、契约与声明能力的可复现基线，保留原始结果与差异；性能另行采样。

**Blocked by：** 无（获准实施后可从这里开始）

**状态：** 草案，尚未发布。

**来源 spec：** [PR 37 维护体验回归约束规格](specs/00-pr37-maintainer-research.md)、[维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[平台构建与 Stonecutter 策略规格](specs/03-platform-build-strategy.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** W0；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 基线在记录的环境和输入下可重复生成，原始输入、生成方式、结果和失败日志均留档，不用缓存目录或历史日志冒充本轮证据。
- [ ] 五个既有节点及其支持等级完整出现在基线中；本基线不删除、降级或新增节点，也不改变 26.2 节点身份、Minecraft 坐标语义或 Fabric 制品命名。
- [ ] 每个节点分别记录实际参与构建的源、资源、模板、metadata、mixin、processor、能力声明、测试发现/跳过/数量和生成物 trace，并能与节点事实源对齐。
- [ ] manifest、能力矩阵和测试清单是节点图与实际产物的派生快照，不成为第二事实源；手工记录与实测差异逐项列出。
- [ ] 最终 class、jar entries、metadata 与声明能力可互相追溯；重复、缺失、跳过和有意子集都有原因、owner 和失败诊断。
- [ ] 契约、golden、Probe 与语言基线只被读取；普通验证不得更新基线，任何必要基线变化都保留旧新差异与审阅记录。
- [ ] 每个 not-verified 或 deferred 项都有独立证据状态和 owner，不被改写为 supported、partial 或 unavailable。
- [ ] 基线报告明确性能采样不属于 W0，并指向独立的 P0 性能基线，而不是用性能不阻塞作为省略结论。
- [ ] 对后续迁移有影响的差异按 Fabric 源所有权、CI 子集、契约覆盖和数据保护归类，形成可执行处置项。

**切片边界：** 它独立交付一份可复核的重构前事实快照，是所有构建迁移和 P4 对照的共同输入。

**协调事项（不等于额外阻塞）：**

- 与运行时、插件、语言、registry、功能域 owner 确认契约与能力输入的取舍；共享资产或同时采集只作协调，不作为阻塞。

### 02 P0 独立性能基线

**What to build：** 在任何性能相关行为改动前，按固定负载、环境、预热和重复次数采集 startup、Probe、reload、tick、Adapter 与内存基线，并留下原始样本、统计口径和复现说明。

**Blocked by：** 无（获准实施后可从这里开始）

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** P0-performance；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 性能基线与 W0 source/artifact manifest 分离，不把性能指标塞入构建基线或用 manifest 代替采样。
- [ ] 每个采样维度明确节点范围、支持等级、负载、数据集、预热、重复次数、采集点和统计方法；未采样节点或维度显式记录而不是外推。
- [ ] startup、Probe、reload、tick、Adapter 与 heap/memory 至少按已声明范围留下原始样本、汇总值和离群值处置规则。
- [ ] 运行环境、Java/Graal/加载器版本、初始化状态和数据规模留档；任何环境差异或中断都进入诊断而不是被静默丢弃。
- [ ] 采样方法可由另一名维护者按记录复现，样本与生成结果不可被普通检查改写。
- [ ] 报告只陈述观测值、方差和风险，不在取得数据前设定发布阻断阈值或虚构预算。
- [ ] 明确该基线必须先于性能相关行为改动完成，并列出会触发重新采样或对比的行为类别。
- [ ] 每个异常或不可信样本有 owner、原因假设和后续验证动作。

**切片边界：** 独立性能事实可以在重构前单独完成并复核，为后续行为变更和 P4 维护者阈值决策提供同一口径的数据。

**协调事项（不等于额外阻塞）：**

- 与执行、Probe 和平台 owner 确认负载与节点范围；若与其他组共享运行环境，只安排调度，不把资源争用当作依赖。

### 03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移

**What to build：** 建立必备数据保护与旧读回回归：对 config、world、实体/玩家 pdata、脚本与 pack、trust-store、用户编辑 workspace/declaration、历史日志和可再生 cache 固定旧 fixture 为事实输入，验证路径、格式、key、wire、默认启用与读写语义不被运行时重构改变；不可再生数据不删除，cache 只有来源可重建才重建。数据保护、旧数据读回和必要迁移的备份/原子替换、版本、幂等与回滚都是必备验收；只有离线 validator/report 是可选、只读且非发布 gate。

**Blocked by：** [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)

**状态：** 草案，尚未发布。

**来源 spec：** [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)

**工作单：** 建立 config、world、pdata、pack、trust-store、workspace/declaration、logs、probe/module cache 的数据清单与可再生性分类。、用旧 fixture 在普通 reload、失败 reload、切世界、server stop 和 root close 前后比较路径、格式、key、wire 和默认启用状态。、验证用户编辑文件不被生成物覆盖，probe/module cache 只有来源可重建且留有证据时才清理。、把旧 fixture 回读、不可再生数据保留和必要格式迁移的备份/原子替换、schema/version、幂等、失败回滚列为必备验收，不以可选报告替代。、提供可选离线只读 migration/validator 报告路径；普通执行和 release gate 不依赖它。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 基线列出的每类数据都有 owner、路径、格式/key/wire、可再生性、备份策略和旧 fixture 结论；未知项不能默认当作 cache 删除。
- [ ] 普通成功 reload、失败 reload、server stop、切换世界和 root close 后，config、world、pdata、pack、trust-store、workspace/declaration 和 logs 原样可读。
- [ ] 脚本 pack 的 GLOBAL/WORLD/SERVER_CACHE 路径、启用状态文件、manifest key 和默认启用规则不变；Fabric WORLD 当前行为被记录为现状差异而非被迫 parity。
- [ ] probe 输出和 module cache 只有在源输入存在、可重建且报告证据成立时重建；无法证明可再生的文件保留。
- [ ] 若必须迁移，迁移前生成备份或使用原子替换，写入版本标记，旧 fixture 可回读，重复运行幂等，注入失败后原始数据可回滚且旧数据保留到验证完成；这些是本票必备 gate。
- [ ] 仅离线 validator/report 可选、显式只读、不进入普通 runtime 错误路径、不更新规范源，也不作为硬发布 gate；必备数据保护与迁移回滚验收不得被它替代。
- [ ] 数据专用迁移与回滚 fixture 通过前，不删除旧格式读取路径；无数据收益时不得引入通用 migration framework。
- [ ] 所有断言通过公开文件内容、脚本读写、pack/trust 输出和 reload 结果观察，不以私有文件句柄或内部字段为契约。

**阻塞理由：**

- [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)：数据保护必须以旧路径、旧格式、旧 key、旧 wire 和旧 fixture 输入为比较基线。

**切片边界：** 数据保护可以用旧 fixture 和公开数据结果先行回归，不需要等待新 root；后续 DATA_SYNC、PACK_TRUST 等改数据或传输的票消费并重跑这些保护验收，最终新 root 生命周期下的数据验收由主整合另行闭合。

**协调事项（不等于额外阻塞）：**

- PACK_TRUST、DATA_SYNC 与 MANAGED_SURFACE/workspace 组共享数据清单和用户编辑文件判断；以协调避免重复写入，不把本票变成发布 gate。

### 04 P4 前性能发布政策确认

**What to build：** 维护者在取得 P0 独立性能基线后、进入 P4 前，基于实际数据确认每个关键维度是否设置发布阻断阈值、适用节点范围、判定口径和失败处置；不预设任何数字。

**Blocked by：** [02 P0 独立性能基线](#02-p0-独立性能基线)

**状态：** 草案，尚未发布。 **需维护者参与。**

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** P4-preparation；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 维护者在 P0 基线完成后、任何票进入 P4 活动前完成政策确认，并留下人工确认记录；本票不能被默认当作可无人代办的技术验证。
- [ ] 每个关键性能维度都获得维护者结论：设置发布阻断阈值、仅记录观测、或暂不设阈值；不预设数字或预算。
- [ ] 若设置阈值，逐项记录数值来源、适用节点、支持等级、测量口径、样本规则、判定方式和失败处置；数值必须能追溯到基线数据与维护者理由。
- [ ] 若不设置阈值，也明确记录依据和后续触发重新评估的条件，不得写成性能不阻塞或省略采样。
- [ ] 政策区分发布阻断与观测指标，并说明 primary、secondary、experimental 或未采样范围的适用差异。
- [ ] 环境漂移、负载变化、样本剔除和不可复现情况的处理规则事先明确，不能在结果不利时临时调整。
- [ ] 政策确认后，后续性能相关行为改动、P4 复测或重新裁决的触发条件明确；不得沿用过期政策或旧样本。
- [ ] 确认记录包含 owner、输入基线、维护者结论、理由、适用范围和失败规则，可供 release handoff 直接引用。

**阻塞理由：**

- [02 P0 独立性能基线](#02-p0-独立性能基线)：政策只能基于已取得的 P0 独立性能基线和其环境、负载、预热、重复次数与统计口径确认，不能在采样前预设结论。

**切片边界：** 这是必须在 P4 前闭合的人工政策门，能独立于实现进度完成，并防止无数据阈值或临时裁量。

**协调事项（不等于额外阻塞）：**

- 维护者亲自确认政策；执行、Probe 与平台 owner 只解释基线口径，不代替裁决。

### 05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口

**What to build：** 以现有 startup、SERVER、CLIENT、afterInit、reload 和 close 路径验证单一 NekoRuntimeRoot 装配与调用者注入，迁移并删除 static root 旁路；保留现有行为，不同时引入第二 owner 或半套新旧 runtime。

**Blocked by：** [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[02 P0 独立性能基线](#02-p0-独立性能基线)

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** 以 BUILD_BASELINE 记录两个 loader 的当前创建顺序、STARTUP/SERVER/CLIENT/afterInit 时机和 close 行为。、抽出共享装配函数或工厂，只作为构造实现，不新增 RuntimeKernel、gateway、service locator 或第二 owner。、让 loader entry 私有持有 root，并向 client、server、command、pack sync 和错误边界注入窄生命周期 handle。、迁移所有生产代码中的公开 RUNTIME_ROOT 直接读取，保留 loader 特有时机与平台接线。、补当前行为烟测：startup、server started/reload、client load/reload、afterInit 和 close；至少覆盖两个 loader 的现行节点。、在删除旧旁路前确认 Plugin Runtime 仍只在 loader bootstrap 创建一次。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] NeoForge 与 Fabric 各自只创建一个 NekoRuntimeRoot，loader entry 外没有可替换或可读取的公开 static root。
- [ ] STARTUP 首次加载、SERVER started 后加载或 reload、CLIENT 在各自 loader 既有安全点加载、afterInit 触发顺序与旧烟测一致。
- [ ] SERVER reload 与 CLIENT reload 都只经 root 生命周期入口发生，命令、F3+T、pack sync 和平台 listener 不再直接触碰 ScriptManager。
- [ ] close 按当前契约冲刷并关闭 script managers、清理 listener 和 root 资源；关闭中的异常不阻止后续清理，重复 close 不产生二次回调。
- [ ] 两 loader 现行 startup/server/client/close 烟测可复现，输出脚本 marker、错误计数、资源释放日志和最终退出状态。
- [ ] Plugin Runtime bootstrap、平台事件注册、network 注册次数在 reload 前后保持一次，不因共同装配函数产生第二套状态。
- [ ] config、world、pdata、pack、trust-store、workspace/declaration 的路径、格式、key、wire 和默认启用规则与基线一致。
- [ ] 公开 static root 旁路、重复 manager 容器和旧直接装配路线在全部调用者迁移并通过上述烟测后同票删除，不保留长期并行路径。

**阻塞理由：**

- [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)：两 loader 共同装配重排前需要五节点旧行为、旧契约和数据输入基线，避免用当前实现猜测现行时机。
- [02 P0 独立性能基线](#02-p0-独立性能基线)：共同装配与启动/加载时机预整理会改变相关源码行为；旧启动、reload 和资源采样必须在改动前完成，不能改后再补基线。

**切片边界：** 这是所有运行时路径的共同 owner 预整理，范围只含生命周期接线和旁路删除；两 loader 烟测让它独立可验，且不把 registry、语言或 feature 域拉进前置依赖。

**协调事项（不等于额外阻塞）：**

- 与 NETWORK_SYNC、PACK_TRUST、DATA_SYNC、RUNTIME_COMMANDS 共享 loader entry、client reload hook 和 lifecycle handle 文件；按冲突协调，不把 feature 域变成硬依赖。
- REGISTRY_STARTUP 只消费 root 的启动时机，不阻塞本预整理。

### 06 候选环境、阶段结果与 owner-thread commit 点

**What to build：** 在现有部分候选事务基础上补齐 generation-owned 交接的不完整隔离与清理风险：candidate 的 Context、session、binding、listener、timer 收集和计划在 preparation、execution、binding、事件计划及现有域 preflight 全部通过后，才经明确 commit 点切换；失败关闭 candidate 全部 generation 资源并保留 active。本票只负责 generation-owned reload 交接与阶段结果，不承担 GLOBAL_STATE 的 root map 联合事务，也不承担 RUNTIME_THREADS 的完整队列、重入和 watchdog 调度；完整 reload 契约须三票结合验收。

**Blocked by：** [05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)

**状态：** 草案，尚未发布。

**来源 spec：** [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** 把 candidate 的 Context、module session、binding、listener、timer 和计划放入独立 generation，不再提前写入 active 共享 bus。、保持 generation-owned 资源与阶段结果边界：不提前发布生产 callback、对外 binding 或 live mutation，也不把 root 级 global/shared 事务或完整线程调度并入本票。、在 owner thread 建立单一 commit 点：先切换生产路由，再让旧 generation 停止接收新 callback，最后按所有权释放旧 session。、candidate 的 timer 注册只进入候选资源收集，commit 前不作为生产 timer 分发；显式测试 callback 可在测试 harness 中执行，但不承诺 pending timer 自动完成。、保留 Plugin Runtime、Point、Contributor、frozen result 和 Extension Handle 的进程级身份；只让 session object 按 generation 校验。、为 STARTUP 的不可逆平台注册保留显式 restart/unsupported 边界，不让本票偷偷重做 registry bootstrap。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] SERVER、CLIENT、TEST 的成功 reload 都先构建 candidate generation；candidate 执行期间生产 callback、timer、对外 binding 和 live mutation 仍由 active 执行。
- [ ] preparation、execution、binding 或现有域 preflight 失败时，candidate 的 Context、timer、listener、binding 和临时计划全部关闭，active 仍能接收事件并读旧 state。
- [ ] 失败结果外部可见地包含 generation、phase、source location 和 owner/domain，不带修复指引，也不把内部锁或私有对象当契约。
- [ ] commit 后新 generation 是唯一新 callback 接收者，同一事件不出现旧新双重执行；旧 generation 按 timer、listener、Context 所有权顺序释放。
- [ ] candidate 自己的测试 callback 和 pending timer 在提交前可完成；被 watchdog 或语句上限终止时 candidate 被丢弃，active 不变。
- [ ] candidate 的 pending timer 只被收集并随 generation 提交或关闭，commit 前不作为生产 timer 执行；显式测试 callback 可执行，被 watchdog 或语句上限终止时 candidate 丢弃、active 不变。
- [ ] STARTUP 不可逆平台注册未被域 Adapter 证明可回滚时，入口显式要求 loader restart 或返回不支持阶段，不执行 reset 后宣称事务成功。
- [ ] 本票通过只代表 generation-owned 交接绿；GLOBAL_STATE 的联合写集与 RUNTIME_THREADS 的完整调度仍需各自通过，三者结合后才构成完整 reload 契约。

**阻塞理由：**

- [05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)：candidate 提交点必须挂在唯一 root 生命周期入口上；若先改旧 static/root 旁路会形成第二实现。

**切片边界：** 这是与全局状态、插件有效性和网络 stale packet 直接共享的核心窄路径；不包含线程调度、global 写集或任何 feature 域，但足以用现有脚本和资源外部观察独立闭合。

**协调事项（不等于额外阻塞）：**

- registry/events/client feature 域只能作为 candidate plan 消费者接入，不反向阻塞本票。
- DynamicRegistry 多人协议若需要 candidate 状态输入，只协调真实协议字段，不让本票依赖整个 feature。
- 生命周期诊断只输出阶段与错误结果；独立 telemetry、workspace 和 dashboard 归 language-surface 组。

### 07 同类型串行、close 优先与 watchdog 隔离恢复

**What to build：** 在 candidate/active 交接之上实现同一 ScriptType 的 owner-thread 生命周期调度：evaluate、reload、close 串行，reload 不重入，close 优先，非 owner 请求排队，回调内 reload 明确排队或拒绝；watchdog 终止 candidate 时保留 active，终止 active 时进入隔离失败并等待显式 reload，不自动创建第二个 active。

**Blocked by：** [06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)

**状态：** 草案，尚未发布。

**来源 spec：** [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** 为 SERVER、CLIENT、STARTUP/TEST 复用既有 owner 入口并建立窄调度面，不把 Graal Context 暴露给任意外部线程。、定义同类型请求队列、回调内 reload 的排队/拒绝结果、close 抢占规则和取消点。、隔离 candidate watchdog 与 active watchdog 的状态转移：candidate failure retain、active failed awaiting explicit reload。、确保 guest-created thread 只能通过显式调度入口访问 managed runtime，同时不收紧既有高级 Java/thread 能力。、用并发 fixture 与 watchdog fixture 验证锁顺序、无自等待死锁、无双重 callback 和清理幂等。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 同一 ScriptType 的 evaluate、reload、close 请求按单一序列执行；并发 reload 只有一个 candidate，另一个排队或得到明确拒绝结果。
- [ ] 回调内部请求 reload 不会递归进入自身队列或同步等待自身完成，外部可观察结果是排队成功或明确拒绝。
- [ ] close 对尚未开始的 reload 优先，对在途 candidate 先取消并关闭候选，再关闭 active session 与 root 资源。
- [ ] 非 owner 线程请求 managed lifecycle 时进入对应 owner 队列，不直接触碰 Context、binding、listener 或 timer。
- [ ] watchdog 终止 candidate 时 active 事件、timer、binding 和 state 不变；再次显式 reload 可创建新 candidate。
- [ ] watchdog 终止 active 后进入隔离失败状态，停止向被杀 Context 分发，不自动创建第二 active；显式 reload 是唯一恢复入口。
- [ ] guest-created thread 的高级 Java 能力不被收紧，但访问 managed lifecycle 必须走显式调度入口。
- [ ] 并发/重入/watchdog fixture 覆盖 SERVER 与 CLIENT owner，至少一个非 owner 线程和 close 抢占路径；旧 Context 私有锁路线在通过后删除。

**阻塞理由：**

- [06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)：串行化和 close/watchdog 状态机围绕 generation commit 点定义；先做调度会保护错误的先清 active 行为。

**切片边界：** 线程与 watchdog 是 reload commit 的独立可验状态机，单独成票避免把 W1/W4 合成巨块，也不需要语言管线或 feature 域输入。

**协调事项（不等于额外阻塞）：**

- PERF_BASELINE 可并行采样调度耗时；本票只承诺正确性，不设置性能阈值。
- 网络 receiver 与 DynamicRegistry 平台 Adapter 复用调度入口，不改出第二队列。

### 08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活

**What to build：** 用一个真实外部 addon fixture 证明 Java 插件能通过 loader discovery 和 fat jar 依赖进入 Plugin Runtime，沿既有 Point/Contributor/Hook 贡献，bootstrap 后经 Extension Handle 或脚本 binding 消费产物；普通 reload 不重新 bootstrap/freeze 插件，而 generation session object 失效。该票只补真实消费链和边界修正，不新增 Point 或插件框架。

**Blocked by：** [06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)

**状态：** 草案，尚未发布。

**来源 spec：** [PR 37 维护体验回归约束规格](specs/00-pr37-maintainer-research.md)、[维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** 构造带 @RegisterNekoJSPlugin 和 owner identity 的外部 addon 测试制品，模拟真实 fat jar/classpath discovery 输入。、让 addon 通过既有 Contributor/Hook 与一个既有扩展点贡献，bootstrap 后由 Handle/result 和脚本可见 binding 消费同一冻结产物。、验证 discovery、contribution、dependsOn 拓扑、freeze、initialization/collection、finish/result 的外部顺序与错误定位。、在普通脚本 reload 前后断言 Plugin Runtime bootstrap/freeze 只发生一次，Handle 产物仍可读，generation token 不能操作新 session。、补依赖错误、重复 id、freeze 后注册和未知依赖的 addon 级失败输出。、删除无生产调用者的 legacy manager facade/bootstrap 入口；仅当外部 fixture 继续需要嵌入入口时保留并记录原因。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 真实 loader discovery 能发现外部 addon，owner identity、priority、requiredMods/clientOnly 规则与旧契约一致，内部实例化不能冒充通过。
- [ ] addon 贡献按 Point 事实源收集，Hook/Contributor 投影效果等价，脚本侧可观察到同一冻结产物。
- [ ] Extension Handle 在 finish 前拒绝读取，finish 后可读产物；依赖、环、重复 id、freeze 后注册在对应阶段带 addon 定位失败。
- [ ] 普通 reload 不重新 discovery、bootstrap 或 freeze Plugin Runtime；Point result 与 Handle 身份保持有效。
- [ ] reload 后旧 generation 的 session object、事件 token 或临时计划不能静默操作新 session，错误明确指向失效 generation。
- [ ] session 清理不关闭仍由进程级 Plugin Runtime 持有的共享 Java 对象；Binding.value 的共享对象不被误当作 generation 快照。
- [ ] 外部 addon 在至少一个 NeoForge 节点与 Fabric 当前节点的 loader 启动/烟测路径中被发现并执行。
- [ ] 无调用者 manager facade、旧 legacy bootstrap 和 loader 私有 Point registry 旁路仅在外部 addon 与 reload fixture 通过后删除。

**阻塞理由：**

- [06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)：进程级 Handle 与 generation session 的分离只有在完整 candidate reload 后才可观察。

**切片边界：** 插件链路的主要缺口不是重造 Point 框架，而是真实 discovery 到消费的端到端证据；单 context 可用 addon marker、Handle result、reload 次数和失效 token 独立验证。

**协调事项（不等于额外阻塞）：**

- MANAGED_SURFACE 组拥有公开签名和 declaration 观测；本票只消费既有 Point/binding，不新增规范源。
- BUILD_BASELINE 提供旧 addon/Probe 输出输入；若外部 fixture 制品接线需要构建支持，只协调不重开 build/release 票。

### 09 Managed Surface 单一规范源与声明/Probe 派生链

**What to build：** 脚本作者使用的 managed facade、数据类型和事件注册发生变化时，NormativeApiContract 是唯一规范输入；manifest、Probe、TypeScript declaration 与 Python declaration 由同一契约确定性派生。legacy catalog 继续只作迁移观察，Graal/高级 Java 面保持可用，脚本作者得到与运行时成员一致的声明和能力结论。

**Blocked by：** [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[02 P0 独立性能基线](#02-p0-独立性能基线)

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** 以 CoreManagedApiBootstrap、facade、数据类型和事件注册为反射输入，收口 NormativeApiContract 的 owner、贡献校验和 contract identity；manifest/Probe/declaration 只消费冻结或已验证 surface。、为 managed symbol、module、capability、TS/Python declaration 建立从运行时成员到产物的 deterministic fixture，普通测试只读 golden。、保留 NekoScriptCatalog/LEGACY_PREVIEW 的迁移观察职责，增加 legacy shadow 不能覆盖 managed contract 的 characterization。、为 supported/partial/unavailable 能力补充 loader、版本和运行上下文条件；不可用能力显式失败或降级，不允许静默 no-op。、保持 KubeJS 风格入口、java:、Java.type、Java.loadClass、Graal interop 和当前 HostAccess 的既有行为 smoke。、列出 golden/manifest/Probe/declaration 的显式 regenerate 流程与旧新 diff、原因、影响和审阅记录；不新增手写第二规范 JSON。、收缩 gate：只有替代 contract/manifest/Probe/declaration 的 behavior、golden 与 trace 均通过且旧路径无调用者后，才移除对应旧生成或观察旁路；不在 final release 统一大清理，也不删除公开功能或语言。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] CoreManagedApiBootstrap/ApiSurface 的最高调用者测试证明 facade、数据类型、事件注册是 NormativeApiContract 的唯一反射输入。
- [ ] 同一契约输入重复生成 manifest、Probe、TypeScript declaration 和 Python declaration 的结果稳定，普通测试不会写任何 golden。
- [ ] legacy catalog 或 LEGACY_PREVIEW 符号可被迁移观察，但不会被标成 managed stable，也不能覆盖同名 managed symbol。
- [ ] 每个涉及能力的 symbol 均有 supported/partial/unavailable 及 loader/version/context 条件；声明与真实外部行为一致。
- [ ] TS 与 Python declaration 成员、签名、module 归属和 runtime member parity 有 fixture 证明。
- [ ] java:、Java.type、Java.loadClass、Graal interop 和当前 HostAccess 的既有高级 Java 用例保持通过。
- [ ] 契约变化必须走显式 regenerate，并留下旧新 diff、原因、影响和维护者审阅记录。
- [ ] 不存在新的独立 API artifact、全仓 catalog、第二规范 JSON 或通用 capability 框架。
- [ ] 至少一个真实脚本调用经过 contract 反射、manifest/Probe、TS/Python declaration 的完整链路，外部 addon/Probe 差异只作为协调项而不冒充本票验收。

**阻塞理由：**

- [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)：先冻结本票会改写的 ApiManifest、Probe、managed/legacy contract 与 declaration 旧 golden；只消费本 scope 内旧证据，不用全仓 all-type 基线空缺充当通过。
- [02 P0 独立性能基线](#02-p0-独立性能基线)：contract 反射、Probe/declaration 生成需以本域既有性能采样为旧输入；若缺失，先补本域基线，不把性能采样空缺当作任意开工理由。

**切片边界：** 先把规范源和派生关系变成可验证事实，避免事件、查询和声明票各自制造第二 catalog；这不是 parser 或生成器横切重构，而是一条从脚本成员到声明与能力输出的完整路径。

**协调事项（不等于额外阻塞）：**

- BUILD_BASELINE: 冻结并比较既有 ApiManifest/Probe/declaration golden，作为本票旧输入。
- PLUGIN_ADDON: 外部 addon discovery、fat jar 与 Probe 兼容验收由插件组承接；本票只保证 managed/Probe 派生契约可被其消费。
- LANGUAGE_PIPELINE/LANGUAGE_TS/LANGUAGE_PY: 声明生成需要保留全语言模块归属，语言行为本身由语言票负责。
- EVENT_SURFACE/QUERY_TOOLS: 后续事件与查询面必须以本票的规范源和派生流程为准。

### 10 按类型 global、显式 shared 与候选顶层写集联合提交

**What to build：** 在同一个 NekoRuntimeRoot 内为 STARTUP、SERVER、CLIENT、TEST 提供独立 global backing store，同类型多文件和普通 reload 共享；提供显式进程内 shared 工作名入口；global 与 shared 的顶层 set/delete/clear 进入 candidate 写集，支持 read-your-writes、失败丢弃、并发冲突检测和联合提交，不承诺深回滚或网络同步。

**Blocked by：** [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)

**状态：** 草案，尚未发布。

**来源 spec：** [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)、[global 共享状态与候选写入规格](specs/10-shared-global-candidate-writes.md)

**工作单：** 用 root 拥有的按 ScriptType backing store 替换进程级 NekoGlobal 静态 Map，并加入显式 shared 窄域 Map 入口。、为每个 generation 创建 global/shared 视图，捕获顶层 set/delete/clear 写集并实现 read-your-writes。、commit 前联合预检私有与共享写集；其他 writer 修改同管 key 时让 candidate 失败，成功时一次性发布。、保留 root 级 Map 跨 reload、server stop 和切换世界，root close 释放，generation close 不清空。、对保存的 guest 函数或 Value 施加 generation 生命周期，不因存入 Map 获得永久保活。、区分 NekoJS global 状态容器与当前 Context 的 globalThis，并保留现有 Node shim 回归；不新增语言管线。、为 1.2.0 clean cutover 补旧跨类型 global 到显式 shared 的迁移表，同类型用法保持不变。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 同类型多个脚本文件共享同一 global，同名 key 在 STARTUP、SERVER、CLIENT、TEST 读回彼此独立的值。
- [ ] candidate 内顶层写入可读回；成功 commit 后 active 可见，失败时 set、delete、clear 均不污染旧 active。
- [ ] 其他 writer 在候选期间修改同一受管顶层 key 时冲突被检测且不丢写，candidate 失败；跨类型同名私有 key 不冲突，shared 竞争写入失败。
- [ ] 一次 candidate 同时写 global 与 shared 时，两边联合成功或全部不发布，不存在半提交。
- [ ] global/shared 在同一 root 内跨普通 reload、server stop 和切换世界保留；root close 释放；generation close 不误清；独立 root 和测试 runner 从空状态开始。
- [ ] 保存的 guest 函数或 Value 不延长已销毁 Context 的生命周期；嵌套对象、列表、已共享 Java 对象内部修改明确不承诺深回滚。
- [ ] 用户脚本中的 global 与 globalThis 分工可外部观察，Node shim 仍使用正确语言全局对象；不把状态容器当作模块全局对象。
- [ ] 迁移表逐项列出旧跨类型 global 写法到显式 shared 的替代；同类型用法不变，fixture 通过后删除旧隐式跨类型回退和双写。
- [ ] 不新增权限系统、第二 runtime owner、网络同步协议或通用事务框架，也不收紧高级 Java 访问。

**阻塞理由：**

- [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)：shared 最终公开符号、binding 定义、declaration 与迁移表输入需要 managed surface 冻结，避免先发布第二规范源。
- [07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)：并发 writer、同类型串行和 close/watchdog 边界决定联合写集的冲突检测与提交时机。

**切片边界：** global/shared 是一个窄的 Map 状态域，但包含隔离、事务、生命周期和迁移的完整外部路径；独立 root 和 reload fixture 可直接验证，不需要 registry 或客户端显示域。

**协调事项（不等于额外阻塞）：**

- shared 仍为工作名；最终公开符号、declaration 和完整 managed surface 由 MANAGED_SURFACE 组冻结，重名只需同步 binding 定义与迁移表，不阻塞运行时语义。
- LANGUAGE_PIPELINE 只需保留现有 Node shim/globalThis 回归，不得让本票依赖新转译或全语言管线。

### 11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径

**What to build：** 脚本入口把 source/path/extension/requested mode/trust-approved source 交给 Preparation，得到带 language id、module mode、source map、诊断位置和稳定 cache key 的不可变 prepared module；Module Resolution/Cache 用它完成 CJS require、ESM import/link、依赖图、命中/失效和生命周期，最终 Graal 执行结果或错误能映射回原文件；模块 cache/session 的生命周期由 runtime owner 持有。legacy CJS bridge 被显式 characterization，而不是形成第二语义管线。

**Blocked by：** [05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[语言模块管线规格](specs/06-language-module-pipeline.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** 把 NekoModulePipeline/NekoCompilationPipeline/NekoModulePipelineCache 的构造和 cache 注入显式化，移除 process-wide static cache 或 legacy instance 的必要调用；不新增公共 parser ModuleSPI。、让 NekoPreparedModule 的调用者可观察语义包含 language id、mode、code/IR、source map、原始诊断位置和稳定 cache key，并保持不可变。、收口 CJS/ESM identity、require/import/link、dependency graph、cache hit/invalidation、legacy bridge 和 module lifecycle 到 Resolution/Cache 路径，平台 callback 不进入 common 模块层。、为本地 trusted 与远端 explicitly-authorized source 复用同一 prepare/resolve/execute 阶段，只让 trust 决策影响授权结果。、建立 JS、.mjs/.cjs、循环依赖、未解析 import、identity 冲突、link 失败、内容变化和 ScriptType scoped clear 的 characterization/corpus。、为 legacy CJS bridge 写明当前语义、删除条件和替代证据；在条件未满足前保留而不删除。、收缩 gate：JS/CJS/ESM 旧 static cache、legacy instance 或并行装载旁路，只有替代 behavior、source-map/declaration、trace 与无调用者证据齐全后才能移除；清理随票完成，不推迟到 final release，也不删除公开语言。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

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

**阻塞理由：**

- [05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)：删除 process-wide static cache/legacy instance 并把模块 session/cache 生命周期改为 runtime owner 持有时，必须以新 root 所有权为验收状态；不得用旧 static 旁路或旧 fixture 假通过。

**切片边界：** 以 JS 模块行为作为语言链基础，可以让 TS/Python 票接入同一个 prepared/resolved/diagnostic 事实，而不是按 lexer、parser、cache、测试分层开票。

**协调事项（不等于额外阻塞）：**

- RUNTIME_ROOT/RELOAD_COMMIT: candidate/active generation、失败保留和模块 session 清理由 runtime 组定语义；旧 corpus 可先作 characterization；删除 static cache/验收 runtime-owned module session 必须等待 RUNTIME_ROOT，不得用旧 fixture 冒充新状态。
- GLOBAL_STATE: 模块生命周期不得绕过候选代际中的类型内 global 语义，具体状态事务由 runtime/global owner 负责。
- PERF_BASELINE: cache 命中率与编译成本只使用 PERF_BASELINE 的本域旧输入或随票补采对照，不自行设定发布阈值，也不用 all-type 空缺替代。
- MANAGED_SURFACE: 语言 module 归属需要进入声明，但语言票不重定义 managed 规范源。

### 12 TS/JSX/TSX 编译、source map 与执行行为路径

**What to build：** 脚本作者写入 .ts/.jsx/.tsx 后，类型擦除、JSX 结构、runtime 注入、模块身份、缓存失效和 Graal 执行结果保持既有语义；语法或转换错误在准备阶段带原始 TS/JSX/TSX source location，运行时错误也能回映射。声明与语言能力归属不因转译丢失。

**Blocked by：** [11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)

**状态：** 草案，尚未发布。

**来源 spec：** [语言模块管线规格](specs/06-language-module-pipeline.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** 扩展 TypeScript erasure、JSX element/fragment、automatic runtime 和 TSX corpus，覆盖固定源码到可执行输出的行为与 source-map 映射。、确保 requested mode、扩展名和 language id 共同决定 TS/JSX/TSX prepare 结果，Resolution/Cache 不复制类型擦除或 JSX 转换语义。、为类型语法、 JSX 结构和 TSX 组合错误保留原始行列、文件和准备阶段，生成 JS 后的运行时异常也能回映射。、将 language id、mode、内容、依赖图和 source map 纳入 cache invalidation，避免 TS/TSX 改动命中旧 JS 产物。、保持纯 Java 自研实现；不引入非纯 Java 转译器，不因候选库评估阻塞行为票。、收缩 gate：TS/JSX/TSX 旧并行转换或装载旁路只有在替代 behavior、source-map/declaration、trace 通过且无调用者后移除；随本票完成收缩，不推迟 final release，也不删除公开语言。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] .ts、.jsx、.tsx representative corpus 均产生正确 language id、module mode、可执行 code/IR 和非空可用 source map。
- [ ] TypeScript 类型擦除不改变运行时值、控制流和导出形状；corpus 固定有意承诺的行为而非私有 AST 布局。
- [ ] JSX/TSX 的 element、fragment 和 automatic runtime 行为与既有 golden/执行测试一致。
- [ ] 准备期语法/转换错误携带原始 TS/JSX/TSX 文件、行列和阶段；不会被后续 JS 位置覆盖。
- [ ] 执行期异常经 source map 回到原始 TS/JSX/TSX 位置，并保留模块身份。
- [ ] 源码、mode、language id 或依赖变化使 cache key/revision 失效，未变化输入可观察命中。
- [ ] TS/JSX/TSX 与 JS/CJS/ESM 混合 import 的身份和错误归属可追踪。
- [ ] 不新增外部转译依赖、公共 parser SPI、Gradle project 或第二套 TS 管线。

**阻塞理由：**

- [11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)：TS/JSX/TSX 必须消费同一个不可变 prepared module、模块身份、cache key 和错误阶段模型；否则会重新形成转译语言私有管线。

**切片边界：** TS/JSX/TSX 的风险在行为、source map 和缓存，而不是 parser 接口；该票从作者源文件到执行结果和诊断完整闭合。

**协调事项（不等于额外阻塞）：**

- MANAGED_SURFACE: TS declaration 的规范源与显式 golden 更新由 managed surface 票约束。
- DIAGNOSTICS: 错误字段、workspace 展示和用户报告消费本票的 source-map/阶段结果。
- PERF_BASELINE: TS/TSX corpus 执行时间只作对照记录，不擅自设定阈值。

### 13 Python 转译、模块行为与诊断路径

**What to build：** 脚本作者写入 .py 后，既有 Python 语法、缩进结构、定义/调用、模块模式和 import 行为经 Preparation 生成可执行 JS/IR，再由 Resolution/Cache 与 Graal 执行；Python 源错误和生成代码运行错误均保留原始位置，缓存与声明不把 Python 误标为 JS。

**Blocked by：** [11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)

**状态：** 草案，尚未发布。

**来源 spec：** [语言模块管线规格](specs/06-language-module-pipeline.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** 扩展 Python golden/corpus，覆盖缩进、定义/类/调用、注释、模块导入和既有 Python-to-JS 行为的固定输出。、确保 .py 的 language id、requested mode、cache key、source map 和 prepended-line 语义进入统一 prepared module。、区分 Python 源解析/转换错误与生成 JS 的 link/执行错误，均能映射回原始 .py 文件。、让 Python import/require 与 CJS/ESM 依赖图使用同一模块身份与失效规则，不复制第二条 resolver。、保持 Python 支持和纯 Java自研实现，不引入 Python 运行时或非纯 Java 依赖。、收缩 gate：Python 旧并行转换或装载旁路只有在替代 behavior、source-map/declaration、trace 通过且无调用者后移除；随本票完成收缩，不推迟 final release，也不删除 Python 支持或公开功能。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] .py representative corpus 得到 language id=Python 语义、正确 module mode、可执行 code/IR 和可用 source map。
- [ ] 缩进、定义、调用、注释和导入的既有行为由 golden/corpus 固定，不冻结私有 parser 对象身份。
- [ ] Python 源语法/转换错误在准备阶段携带原始行列；生成代码执行错误也能回映射到 .py。
- [ ] Python 模块身份、依赖图和 cache invalidation 与统一 Resolution/Cache 行为一致。
- [ ] 源码、mode、identity 或依赖变化不会命中旧 Python 产物。
- [ ] Python 与 JS/CJS/ESM/TS 混合加载的错误阶段和模块归属可观察。
- [ ] Python declaration/probe 中的语言与 module 归属不被 JS declaration 覆盖。
- [ ] 不删除 Python 支持，不新增 Python runtime、公共 parser SPI或第二套模块管线。

**阻塞理由：**

- [11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)：Python 必须复用 prepared module、模块身份、cache key 和 source location 模型；独立 transpiler/load 路径会破坏全语言语义一致性。

**切片边界：** Python 的完整路径独立验收，因为其源形式、source map 和错误映射与 TS/JSX 不同；但仍阻塞于同一模块基础，避免私有 resolver。

**协调事项（不等于额外阻塞）：**

- MANAGED_SURFACE: Python declaration 与 Probe 输出必须由 managed 契约派生。
- DIAGNOSTICS: Python 源位置和阶段信息进入统一错误上下文与 workspace 报告。
- PERF_BASELINE: Python corpus 成本只记录对照，不设定未确认阈值。

### 14 事件总线与 Script/Native/Probe 事件声明基础

**What to build：** 脚本作者通过既有 bus 注册、取消、排序和清理事件；ScriptEvents 的动态声明、NativeEvents 的 raw adapter 面和 ProbeEvents 的 Probe 扩展面进入各自正确 tier，并由同一 managed contract/catalog 派生 TS/Python declaration。平台 callback/mixin 仍在 Adapter，不创建万能 Event Module或重复事件。

**Blocked by：** [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)

**工作单：** 冻结事件 bus 的注册、remove/dispatch、priority、cancel、side filter、线程/重入和 reload 后清理外部行为，复用 EventBusConcurrentStressTest 形状。、把共同事件名、payload 成员、side、dispatch、cancel 和 tier 纳入 managed contract/catalog；原生回调、mixin和平台时机留在 src/或 node Adapter。、为 ScriptEvents 建立动态事件组、注册冲突、side、reload 清理、runtime member 与 TS/Python declaration parity 的同一 catalog 派生路径。、将 NativeEvents 明确保留为 legacy/raw Adapter 观察面，记录 capability 与声明来源，不静默升级 managed。、将 ProbeEvents 限定为 Probe 扩展面，验证事件到真实 catalog/declaration golden，不变成通用运行时事件。、建立 EventApiSurfaceGoldenTest/NekoScriptCatalogEventsTest 级别的无重复 bus、独立 side entry 和能力矩阵 fixture。、收缩 gate：旧事件注册/声明旁路只有在替代 behavior、declaration、trace 与无调用者证据通过后移除；NativeEvents/ProbeEvents 的合法旧 tier 不因收缩被删除，公开功能不删除，清理不推迟 final release。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 脚本注册、触发、取消、优先级和 remove 的外部行为在并发 mutation/post 后最终回到空状态且无重复 dispatch。
- [ ] server-only、client-only 和 mixed side 事件在 catalog/declaration 中保持独立条目，side filter 不产生重复声明。
- [ ] ScriptEvents 动态事件注册冲突、未知依赖/组、side错误和 reload 清理有可诊断失败与 fixture。
- [ ] NativeEvents 保留 raw/legacy tier 与 capability 说明，不被 catalog 收录动作升级为 managed stable。
- [ ] ProbeEvents 输出真实 Probe/catalog/declaration golden，不创建通用运行时事件或第二事件 bus。
- [ ] 每条 bus 只出现一次；已有事件域不被复制为 ScriptEvents/NativeEvents/ProbeEvents 新事件。
- [ ] TS/Python declaration 与 runtime member、payload 形状、side 和 capability 一致。
- [ ] 平台 callback/mixin/transport 时机留在 loader/version Adapter，common 不引入 Minecraft/loader 类型。
- [ ] reload 失败或取消后 listener 不双注册、不泄漏到旧 generation，active callback 可见性符合 runtime 语义。
- [ ] 不新增万能 Event Module、无生命周期 Point或第二注册路径；旧事件旁路仅在替代 behavior、declaration、trace 通过且无调用者后移除，合法 legacy/raw tier 与公开功能不删除，清理不推迟 final release。

**阻塞理由：**

- [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)：事件公开名、payload、side、tier 和 declaration 必须以 NormativeApiContract 为规范源并从 catalog 派生；先改 bus 会制造第二事件规范。
- [07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)：公开事件的跨线程派发、重入与关闭验收需要最终 owner 队列和 watchdog 隔离结果。

**切片边界：** 事件基础必须覆盖 bus 与三类容易混淆的事件声明 owner，但只建立公共路径；recipe、capability、goal、实体行为等已有域另有窄票补齐，避免“所有事件迁移”巨票。

**协调事项（不等于额外阻塞）：**

- RUNTIME_ROOT/RELOAD_COMMIT: generation 切换、失败保留和 listener 清理的最终事务语义由 runtime 组提供；现有 runtime smoke 只能先作 characterization；candidate 隔离与 cleanup 的新状态验收等待 RELOAD_COMMIT。
- PLUGIN_ADDON: Probe/插件贡献发现和外部 addon fixture 由插件组承接，本票只定义事件/Probe 声明投影。
- RECIPE_DATA_SURFACE/GAMEPLAY_EVENT_SURFACE: 既有事件域在本基础 上补域内语义，不以文件冲突为由改成串行阻塞。
- BUILD_BASELINE: 事件 catalog/golden 以当前快照为 characterization 旧输入。

### 15 启动期注册、typed Builder 与连带注册垂直收口

**What to build：** STARTUP 脚本通过唯一 RegistryEvents.register 调用者 Interface 声明对象，经 Registry Runtime 收集、校验、规范化与类型工厂，由各节点 Adapter 在正确注册 pass 创建对象和已裁定连带对象；runtime member、TS/Python declaration、contract/golden、capability 与节点 smoke 从同一条路径可见。该票只处理游戏启动前声明注册，不把服务器运行期 Dynamic Registry 并入同一路径。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)

**工作单：** W6；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] STARTUP 脚本只从现有 RegistryEvents.register 调用者 Interface 进入；事件在首个注册表 pass 前恰好收集一次，default 类型糖方法、命名类型和 custom/register 输入都能到达对应 Registry Runtime 请求。
- [ ] Registry Runtime 对 duplicate、additional、default、类型冲突和 drain 顺序保留现有已验证语义：同批重复 fail-fast，additional 不与来源对象外的同 id 冲突，每个 registry pass 只 drain 一次且无未交付残留；失败不留下可污染下一轮启动的进程级暂存。
- [ ] typed Builder 的公开成员、校验和错误结果由契约反射生成；Block/Item/Fluid 等已验证启动期类型只按裁定完成约定连带注册，不在本票扩大类型或复制第二套类型 catalog。
- [ ] MC/loader 对象创建、注册 pass 接线、版本差异和 capability 只放在共享 MC-facing Adapter 或节点 Adapter；common 作者契约不引入 Minecraft/loader 类型，26.1.2、26.2.0、1.21.1 与两个 Fabric 节点的实际差异逐项记录。
- [ ] runtime member、TS/Python declaration、Probe/manifest 与 contract/golden 均由同一契约输入派生；legacy preview 只作迁移观察，不静默升级为 stable；普通测试不得改写 golden 或声明产物。
- [ ] 成功、失败、重复、类型冲突、连带注册缺失和 drain 失败测试都从 RegistryEvents 调用者 Interface 贯穿到节点 Adapter 的可观察注册结果，测试输出包含定义、注册表、节点和错误来源，不依赖私有仓库字段。
- [ ] 五节点 source trace、artifact 检查和按各节点既定支持等级与声明能力的最小 runtime smoke 证明声明、实现与能力一致；差异显式记录，不自动补 Fabric parity，也不把 experimental 节点当作 primary 回归。
- [ ] 本票不把 Dynamic Registry 指向启动期 drain 路径，也不在服务器运行期修改 live registry；两个生命周期在契约、测试和迁移表中保持分离。
- [ ] 旧类型化启动入口、手写 declaration、重复 type catalog 或不受测兼容 wrapper 只能在替代路径 parity、旧 route 无消费者、迁移表覆盖所有公开写法并获得维护者删除确认后删除；本票不保留长期双路径。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：RegistryEvents 调用者 wrapper、事件成员、dispatch 时机和 catalog/golden 消费由事件面基础提供，不能并行发明第二总线语义。

**切片边界：** 启动期注册是完整的静态声明→drain→节点注册路径，能独立交付并验收；Dynamic Registry 的事务、同步和 stale 语义会超出单一上下文，因此必须分开。

**协调事项（不等于额外阻塞）：**

- 与 EVENT_SURFACE owner 协调现有 RegistryEvents 总线的注册时机和 catalog 唯一性；事件基础是 blocker，这里只协调具体接入，不因共享事件文件另加串行。
- 与 MANAGED_SURFACE owner 确认 registry Builder 与 declaration 是契约反射输入，避免手写声明成为第二事实源。

### 16 Dynamic Registry inert 定义计划与 typed Builder

**What to build：** SERVER 脚本通过独立的服务器运行期动态注册事件 facade 在 candidate 阶段提交 typed callback Builder 定义；本地路径完成事件收集、全规范化 fingerprint、preflight、同 key 冲突、stale/retired 记录和 inert Adapter 请求，并用现有 event Builder 与 Adapter 本地行为测试证明候选不修改 live registry。候选类型只在既有 Item、SoundEvent、MobEffect 范围内选取；完整公开激活由事务/同步 gate 决定，本票不宣称动态热更新已完成。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** W6；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] server registry ready 触发初次候选；script/data reload 在候选阶段重新收集并完成本批 preflight，成功 commit 才发布计划；失败或取消不另起写入，也不在任意脚本线程即时执行 registry mutation。
- [ ] 事件回调只接收 typed callback Builder；不提供通用 type 对象 catalog，也不把未知 type 字符串转换成注册能力。候选范围只从既有 Item、SoundEvent、MobEffect 中选取，且只有通过目标 Adapter、事务与同步 gate 的类型才可公开；未验证类型记录 not verified 并阻塞开放，不因缺测改写为 unavailable。
- [ ] 显式 setter 与 JavaBean-style property 写入调用同一个 setter、校验、规范化和 definition fingerprint 路径；GraalMC 临时 property 实验只作为 characterization，本票验收必须由运行时 contract test 固定，且不得把该实验称为集成通过。
- [ ] 全规范化 fingerprint 覆盖 Builder 输入和约定连带声明，不依赖对象身份或部分字段；相同定义在重复 reload 中得到相同 fingerprint，字段或连带声明变化能被识别。
- [ ] 同一 key 的定义变化在第一版导致整批冲突失败，旧 active 定义继续服务；remove、replace、modify 和未来覆盖机制不出现在公开 Interface、golden、declaration 或迁移承诺中。
- [ ] preflight、fingerprint 冲突和 Adapter 请求都是 generation-scoped inert candidate plan；脚本线程或候选失败不得修改 live registry、挂载生产 callback 或提前发布对外 binding，失败时临时计划和资源全部清理。
- [ ] 脚本不再声明的已暴露项标记 stale/retired，普通 reload 不物理删除；claim、stale、mode 与后续显式清理语义由 Registry Runtime/Adapter Interface 可观察并测试。
- [ ] 调用者 Interface、Registry Runtime/Adapter 契约、TS/Python declaration、contract/golden 和本地行为测试形成同一条证据链；测试优先穿过事件 facade 与 Adapter Interface，不断言私有 Manager 字段。
- [ ] 本票只证明本地 inert 计划行为，不激活多人同步、不宣称动态热更新或任何节点能力已完成；公开激活和能力结论由事务/同步 gate 决定。
- [ ] 旧 DynamicRegistry 静态全局入口和直接 registry surgery 路径只能在事件 facade、候选计划、Adapter 请求、声明和迁移路径全部覆盖且无消费者后删除；删除需维护者确认，不保留双写 shim。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：动态注册事件 wrapper、成员目录、dispatch 语义和 candidate 事件收集必须消费既有事件面基础，不新增第二 bus。

**切片边界：** 把可本地闭合的 inert 定义、规范化、冲突与 stale 语义先独立交付，可以让 Builder 和事件契约在单上下文内验证；公开类型激活、多人 prepare/ack/commit 与网络/生命周期 gate 另行处理，避免一张过大的 Dynamic Registry 票。

**协调事项（不等于额外阻塞）：**

- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 candidate generation token、owner thread 和失败清理接口；RELOAD_COMMIT 是必要 blocker，接口细节仍需协调。
- 与 REGISTRY_STARTUP owner 共享类型事实与 Builder 表达方式，但禁止复用启动期全局 drain 路径；差异写入两张票的测试。
- 与 GLOBAL_STATE owner 只协调同一 candidate 中写集与注册计划的联合失败边界，不把 global 实现作为 Dynamic Registry blocker。
- 与 REGISTRY_STARTUP owner 共享类型事实、setter/property 语义和规范化输入，但 Dynamic Registry 使用自己的 Builder 路径且不得复用启动期全局 drain，因此不是阻塞；若实施时抽取共同 setter/fingerprint 契约，再升级为真实依赖并说明边界。

### 17 网络注册一次、wire 不变与脚本自定义通道 owner 调度

**What to build：** 把 NekoJS payload 注册保留在 loader 原生初始化时机，通过既有 PlayPacketDispatcher/Adapter 装配发送与接收面；普通 reload 不重复注册 network，以旧 wire fixture 为事实输入，不因文字描述暗增策略；脚本自定义 Network 通道经 runtime owner 调度进入当前 generation，旧 generation 或关闭后的 stale packet 不触碰已关闭 Context。

**Blocked by：** [07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)

**状态：** 草案，尚未发布。

**来源 spec：** [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** 固定现有 NekoScriptPayload 与基础 play/configuration payload 的 id、方向、codec 和旧 wire bytes fixture。、让 NeoForge 与 Fabric 各自只在 loader 初始化注册一次，reload/root close 不重复注册或注销平台 network。、统一接收侧经平台 enqueue/main-thread 入口进入对应 ScriptType owner 队列，再路由当前 active generation。、定义 reload commit 前后、active watchdog 隔离和 root close 后的 stale packet 行为：排队到新 generation、丢弃或明确错误，不触碰旧 Context。、保留两 loader 当前已支持的网络面子集，不为了 parity 补脚本编辑器、dashboard 或客户端显示域。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] NeoForge 与 Fabric 的 network payload 注册在启动/客户端初始化各发生一次，多次 SERVER/CLIENT reload 后注册计数和平台连接协商不变。
- [ ] NekoScriptPayload 及本票触达的基础 payload 保持旧 id、方向、codec、字段顺序和线格式；新旧 fixture 字节或等价解码对照一致。
- [ ] 脚本 Network.sendToServer/sendToPlayer/sendToAll 的现有语义不变，接收事件在对应平台主线程/owner 队列执行。
- [ ] reload commit 前到达的 packet 不提前进入 candidate；commit 后新事件只由新 generation 处理一次，旧 generation 不再接收。
- [ ] active watchdog 隔离或 root close 后，在途 packet 被丢弃或返回明确失效结果，不调用已关闭 Context、timer 或 binding。
- [ ] 网络线程异常、非法 channel、超大 payload 和坏 NBT 的行为与旧防线一致，不炸平台网络线程或静默 no-op。
- [ ] Fabric 当前缺失的编辑器/显示网络面保持显式子集，不在本票伪造 parity 或接客户端 UI。
- [ ] 重复 dispatcher、绕过 owner 队列的 receiver 或旧直接 Context 路由，仅在全 loader fixture 通过后删除。

**阻塞理由：**

- [07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)：接收侧 owner 队列、close 优先、reload 重入和 watchdog 隔离是 stale packet 与 generation 边界的真实输入。

**切片边界：** 本票只覆盖网络所有权、wire 兼容和脚本自定义通道，不包含 PData/ClientData payload 域或 pack trust；两 loader 旧 wire 与 stale generation fixture 使其独立可验。

**协调事项（不等于额外阻塞）：**

- DynamicRegistry 多人侧只有真实协议输入才依赖本票结果；candidate plan 本身不阻塞本票。
- PACK_TRUST 复用配置期 payload 桥，但信任语义在 PACK_TRUST 内验证。

### 18 PData 与 ClientData 数据同步路径保护和 generation 边界

**What to build：** 在不改变存档格式、key、wire 和跨 loader 语义的前提下闭合实体 PData 同步与 ClientData 键值同步：PData 持久化容器、dirty/revision/tick 限流、entity id 复用清理和客户端 mirror 保持旧 fixture 证明的现行为；ClientData JSON 校验、大小上限、覆盖写入、断线/切世界清空保持旧 fixture 证明的现行为；接收在 owner 调度下进入当前 generation。

**Blocked by：** [17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)

**状态：** 草案，尚未发布。

**来源 spec：** [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** 固定 NeoForge Entity persistent data 与 Fabric NeoForgeData/NekoJSPersistentData 兼容格式的旧存档回读写入 fixture。、保持 PDataSyncPacket id/codec/entity id/revision/NBT wire 不变，验证 dirty 队列、每 tick 上限、空数据清理和 stale revision 拒绝。、保持 ClientDataSyncPacket JSON 字符串 wire 与 key/value 语义，验证 JSON 类型限制、32768 字符上限、覆盖、坏包丢弃和断线/切世界清空。、把接收与回调消费接到对应 owner 线程/当前 generation，避免 reload 或 close 后写入旧 Context。、对比 SERVER reload 前后玩家/实体持久化数据与脚本读取结果，确保 NekoJS 自有 reload 不回滚世界副作用。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 旧 NeoForge 与 Fabric 存档 fixture 中的 NeoForgeData/NekoJSPersistentData 均可回读，PData key、NBT 形状和跨 loader 语义不变。
- [ ] PData 写入触发 dirty 标记，读操作不触发；flush 保持每 tick 上限、tracking player 目标和 revision 递增语义。
- [ ] 空数据包清除对应 entity mirror，stale revision 被拒绝，entity id 复用后不会读到旧实体数据。
- [ ] ClientData 仅接受旧契约允许的 JSON 类型，超限值显式失败，同 key 覆盖，坏包丢弃并记录警告。
- [ ] ClientData 在断线、离开旧世界/切维度时按现约清空；首次进服收到的数据不被进入世界钩子误删。
- [ ] PDataSyncPacket 与 ClientDataSyncPacket 的 id、方向、codec、字段顺序和 JSON/NBT wire 与旧 fixture 一致。
- [ ] SERVER/CLIENT reload、candidate 失败和 root close 后，同步回调不进入旧 Context，持久化数据与客户端 mirror 的保留/清空规则符合各域契约。
- [ ] 若旧 fixture 发现格式必须修复，本票不得直接改默认路径；先补备份、原子替换、版本、旧数据回读、幂等与回滚迁移并纳入验收后再删除旧读路。

**阻塞理由：**

- [17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)：PData/ClientData payload 的注册、wire fixture、owner 队列和 stale generation 处理由网络同步票提供。
- [03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)：PData 存档路径、key、跨 loader 格式和旧 fixture 保护回归是修改前后的必备输入。

**切片边界：** PData 与 ClientData 共享“服务端状态到客户端 mirror”的数据同步风险和同一条 owner-thread/generation 边界，合成一个窄数据域可独立验证，且不承担 pack trust 或网络注册重构。

**协调事项（不等于额外阻塞）：**

- 与 NETWORK_SYNC 共享 payload 注册和主线程 enqueue 文件，但本票只验证 PData/ClientData 数据语义。
- 与 DATA_PROTECTION 共享数据清单；与 language-surface 组的客户端显示消费只做接口协调，不实现 UI。

### 19 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状

**What to build：** 以旧 fixture 为事实输入，闭合 GLOBAL/WORLD 脚本包在服务器 gather、配置期哈希/Bundle 传输、客户端验证或信任决策、SERVER_CACHE 激活和断线卸载的路径：未信任服务器不执行远端脚本，hashOnly 只观测不执行，显式信任后按既有原子持久化语义保存；trust-store 跨 reload 保留，损坏降级可诊断；客户端包变更通过 root 触发 CLIENT reload。Fabric WORLD 保留当前行为并显式记录差异，不新增签名政策或权限，也不强迫 parity。

**Blocked by：** [17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)

**状态：** 草案，尚未发布。

**来源 spec：** [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)

**工作单：** 复用 PackSyncServer/Client 共享管线，固定 enabled+clientSync 的 GLOBAL/WORLD gather 顺序和 hash-list/bundle 次序。、验证客户端信任或拒绝结果、hashOnly 不执行、bundle 大小/损坏/验签失败、信任提示和断线卸载的外部输出。、保持 trusted-servers.json 路径、JSON key、原子写入、reload 保留和损坏降级语义；签名公钥 pinning 现状不被加强或放松。、让接受的 SERVER_CACHE pack 只在 bundle 完整落盘并验证后激活，再经 root reload CLIENT；断线卸载 cache 集合但不删除可再生文件。、记录 Fabric WORLD 当前激活/分发差异并给出能力证据；不新增本地 GLOBAL/WORLD 签名政策，不要求 Fabric parity。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 服务器按旧顺序只收集启用且 clientSync 允许的 GLOBAL/WORLD 包，配置期先发送 hash list，非 hashOnly 且有包时再发送 bundle。
- [ ] 客户端对未信任服务器明确断开或拒绝执行并给出 trust hint；hashOnly 模式不落盘执行远端脚本；all 模式仅在验证与信任通过后激活。
- [ ] 显式 trustServer/trustPublicKey 的路径、JSON key、bucket 计算、原子替换和跨 reload 保留行为不变；损坏文件降级为空 store 并产生可观察警告。
- [ ] bundle 损坏、hash 不匹配、超限或非法 manifest 的远端包被拒绝，不执行脚本，不覆盖既有本地包，失败原因进入 pack trust 结果。
- [ ] 接受的远端包写入现有 SERVER_CACHE bucket，激活后经 root 触发 CLIENT candidate reload；断线或服务器清空时卸载 cache 集合，可再生文件按现约保留。
- [ ] Fabric WORLD 的当前激活、列表和分发现象被 fixture 固定并公开为 partial/unavailable 证据；NeoForge 与 Fabric 不伪造 parity，也不改变本地 pack 默认启用或路径。
- [ ] trust 决策、拒绝、降级和审计输出在执行或 pack sync 结果中可见，不宣称强恶意隔离。
- [ ] 共享核心管线 fixture 覆盖 NeoForge 与 Fabric 当前配置期桥；loader 重复信任解析/写文件路线在两侧行为等价验证后才删除。

**阻塞理由：**

- [17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)：配置期 payload 传输、wire 兼容、owner 队列和断线处理是远端包验证与激活的真实输入。
- [03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)：trust-store、pack 状态、缓存与用户数据的路径/格式旧 fixture 及保护回滚验收是持久化决策的必备输入。

**切片边界：** pack trust 是独立于 PData/ClientData 的远端脚本执行安全路径；用一次客户端连接夹具可从服务器包集合观察到信任、激活、reload 与断线卸载全链路。

**协调事项（不等于额外阻塞）：**

- 与 DATA_PROTECTION 共享 trust-store 与 cache 分类；冲突协调而非硬串。
- 能力表最终呈现由 language-surface/MANAGED_SURFACE 组承接，本票提供行为证据。
- RUNTIME_COMMANDS 只复用 trust 命令结果，不改 trust 语义。

### 20 管理命令权限、生命周期入口与阶段诊断结果

**What to build：** 闭合 /nekojs 管理入口的权限与生命周期消费：reload/test/error/packs/trust 在两个 loader 上保持旧 fixture 证明的现行 gamemaster 权限与各自能力子集，统一经 NekoRuntimeRoot 调度与结果对象执行；命令输出成功、失败、候选保留、active 隔离等待显式 reload、TEST 未配置和 wrong distribution 的稳定状态。诊断只覆盖生命周期阶段与错误结果，不新增 dashboard、telemetry 或 workspace。

**Blocked by：** [19 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](#19-脚本包分发-trust-决策远端包激活与-fabric-world-现状)

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** 保持 /nekojs 现行 gamemaster 权限口径，在两个 loader 上统一 reload/test/error/packs/trust 的入口检查和拒绝输出。、把命令执行改为调用 root 生命周期结果，而不是读取 static root 或 ScriptManager；命令线程进入对应 owner 队列。、输出成功、失败、隔离等待显式 reload、TEST 未配置、wrong distribution 等稳定结果；诊断包含 generation/phase/source/owner 摘要。、保留 Fabric 文本错误与 NeoForge既有错误面差异，不实现 dashboard、workspace、telemetry 或客户端显示域。、与 pack trust、错误面板和 workspace 组只共享结果 DTO/文本构造，不建立第二诊断框架。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] NeoForge 与 Fabric 的 /nekojs 生命周期、packs、trust、test 和 error 子命令保持 gamemaster 以上可执行，无权限者得到明确拒绝且不触发 reload。
- [ ] reload/test 命令只经唯一 root 入口执行，不再读取公开 static root；命令线程按 ScriptType 进入 owner 队列。
- [ ] 成功 reload 输出类型与提交结果；失败输出 phase/source 摘要并明确 active 已保留或需显式 reload，不把 Throwable 栈直接当用户契约。
- [ ] active watchdog 隔离后的 reload 命令尝试显式创建 candidate；candidate 失败时仍保持隔离/旧 active 状态，不自动二次恢复。
- [ ] TEST 未配置、SERVER 命令在客户端侧、CLIENT 命令在专用服务器等边界有稳定错误，不发生半初始化 manager。
- [ ] 错误命令只展示 root ErrorSnapshot/阶段结果；Fabric 文本降级与 NeoForge 现有面板差异保持显式，不新增 dashboard。
- [ ] packs/trust 命令分别呈现 PACK_TRUST 结果，不改变 pack 启用状态文件或信任决策语义。
- [ ] 直接 static root 命令助手和重复 reload 结果包装在两 loader fixture 通过后删除；Fabric 独立命令子集在缺失 feature 组闭合前不被强行合并。

**阻塞理由：**

- [19 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](#19-脚本包分发-trust-决策远端包激活与-fabric-world-现状)：packs/trust 子命令实际消费 pack 列表、启用状态、trust 决策和失败结果。

**切片边界：** 命令是 runtime 生命周期最外部的可观察入口；单独闭合权限、owner 调用和阶段结果可以避免把错误 UI、workspace 或 feature 重放混入 runtime 票。

**协调事项（不等于额外阻塞）：**

- 独立 diagnostic/telemetry/workspace/dashboard 由 language-surface 组负责；本票只暴露生命周期错误结果。
- PACK_TRUST 提供 trust/packs 行为结果；共享命令树文件按冲突协调。

### 21 Dynamic Registry 多人 prepare/ack/commit 门禁

**What to build：** 在本地 inert 定义计划之上完成服务器与客户端的 Dynamic Registry 批事务：preflight、同 key 指纹冲突、服务端 prepare、客户端 prepare/ack、受控 commit、失败/取消保留旧 active、ID/sync/registry surgery 的 Adapter 边界与按节点声明能力的证据。prepare/ack 只是协议阶段，不宣称分布式原子提交；未通过同步与 reload gate 时不公开不安全热更新。

**Blocked by：** [16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)、[17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[10 按类型 global、显式 shared 与候选顶层写集联合提交](#10-按类型-global显式-shared-与候选顶层写集联合提交)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)、[global 共享状态与候选写入规格](specs/10-shared-global-candidate-writes.md)

**工作单：** W6、W7；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 一批动态注册按 preflight、同 key fingerprint/冲突检测、服务端 prepare、客户端 prepare/ack、commit 的顺序执行；每个阶段的外部结果、generation、owner/domain 和错误来源可观察。
- [ ] 任一阶段失败、候选脚本失败、watchdog 终止、close 抢占或同步无法完成时整批不提交；候选计划丢弃并清理，旧 active state 继续服务，无部分注册、半成功 ID 或混合代际。
- [ ] prepare/ack 消息在契约、测试和文档中只表示协议方向与事务阶段，不证明跨进程分布式原子性；成功断言必须观察所有被激活节点的最终一致可见性或明确的拒绝/降级结果。
- [ ] 同步未完成的客户端不激活新代际，不保留 server-only 多人暴露路径；断线、重连、迟到 ack、重复 ack 和客户端不可用都有确定结果，不出现静默 no-op。
- [ ] 数值 ID、网络 payload、registry surgery、claim、cleanup 和平台差异只由 Registry Runtime 与平台/版本 Adapter 执行；事件 facade 不做反射或直接修改 registry 内部结构。
- [ ] commit 前 candidate 对生产 callback、对外 binding、live registry 和其他节点不可见；commit 后旧 generation 不再接收新计划，新 generation 只执行一次，旧资源按所有权顺序释放。
- [ ] 同 key 冲突、缺失声明 stale/retired、普通 reload 不物理删除的行为与本地票集成后仍成立；未来 replace/update 未实现时不得静默覆盖旧 active。
- [ ] contract/golden、TS/Python declaration、transaction/reload/delete-cleanup fixture、capability/source-trace 和跨节点 runtime smoke 只对通过目标 Adapter、事务与同步 gate 的既有候选类型作出结论；未验证类型记录 not verified 并阻塞公开开放，不因缺测改写为 unavailable。
- [ ] 旧 unsafe live mutation、静态 DynamicRegistry 全局入口和不安全 server-only 路径只有在批事务、失败回滚、迁移表和旧 route 无消费者全部闭合并获维护者确认后才能删除。

**阻塞理由：**

- [16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)：多人事务只能提交已经由 typed Builder、fingerprint、preflight、冲突和 stale 语义稳定生成的 generation-scoped Adapter 请求。
- [17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)：客户端 prepare/ack、连接生命周期、重试/断线和 payload 传输由 network owner 提供；该基础不得反向依赖 Dynamic Registry，避免形成依赖环。
- [10 按类型 global、显式 shared 与候选顶层写集联合提交](#10-按类型-global显式-shared-与候选顶层写集联合提交)：一次脚本候选的动态注册失败必须与受管 global/shared 写集交接一致，验收依赖已闭合的联合写集行为。

**切片边界：** 多人可见性、网络确认和 commit gate 是独立于 Builder 本地语义的风险最高的垂直切片；拆开后可分别用本地 Adapter 测试和跨节点事务 smoke 验收，避免把 prepare/ack 误当原子性证明。

**协调事项（不等于额外阻塞）：**

- 与 NETWORK_SYNC owner 固定 payload 语义、连接生命周期与重试边界；network 基础只依赖 runtime root，不依赖 Dynamic Registry。
- 与 GLOBAL_STATE owner 协调同一 candidate 中注册计划与受管 global/shared 写集的联合成败；共享 commit 门禁是协调，不是让 global 实现 blocker。
- 与 EVENT_SURFACE owner 确认动态事件在 catalog/golden 中只有一条 bus，且 side filter 不把 SERVER 事件泄漏给 CLIENT。

### 22 Villager Trades 声明事件与稳定查询

**What to build：** 服务器脚本通过现有 ServerEvents 的数据/reload 子事件声明村民交易；第一版只提供 add 贡献和绑定 generation/stale 的稳定只读 query。事件面负责收集与预验证，实际 registry mutation 只由 26.x/1.21.1 平台/版本 Adapter 在合法 commit 点执行；失败保留旧交易，Fabric unavailable 显式可见。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** W7；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 调用者只通过现有 ServerEvents 数据/reload 子事件贡献 trade；事件、payload、成员和 side 过滤在 catalog/golden 中恰好出现一次，不新增第二事件 bus。
- [ ] 第一版公开面只有 add 和稳定 query；remove、replace、modify 不出现在调用者 Interface、golden、TS/Python declaration、示例或能力承诺中。
- [ ] query 返回只读快照并绑定 generation/stale 校验，不暴露 live registry view、可变 Manager 状态或旧 generation 可写对象；成功 reload 后新 generation 可读，缺失声明给出确定 stale/retired 结果。
- [ ] 事件收集阶段只形成候选 overlay 和 Adapter 请求；未知 trade set、无效配置、事件失败或 Adapter 拒绝时不发生部分 mutation，不留下 pending 脏数据，旧 active 交易仍可用。
- [ ] 合法 commit 点由平台/版本 Adapter 执行 registry epoch 与 mutation；26.x 与 1.21.1 的注册时机、trade set 映射和错误结果由真实节点测试证明，共享事件面不包含 loader surgery。
- [ ] reload 中断、close、candidate watchdog 或同步失败会清理候选 listener/计划并保留旧 active；旧 generation token 的后续查询与写入有明确失败结果，不产生双重提交。
- [ ] 脚本不再声明的既有 trade 不在普通 reload 中物理删除，只进入 stale/retired 记录；后续查询、诊断和迁移说明能看到该状态。
- [ ] Fabric Villager Trades 的 unavailable 通过 capability/source-trace/smoke 显式验证为明确拒绝或不可用，不用无错误 no-op 冒充；NeoForge 节点 supported/partial 只按实际测试证据记录。
- [ ] 旧 VillagerTradesJS 静态 add/pendingCount、全局 Manager 暂存和直连 registry surgery 只能在事件+Adapter+query parity、迁移表、旧 route 无消费者和维护者确认后删除；删除后不保留长期兼容 shim。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：ServerEvents 数据/reload 子事件 wrapper、成员目录、dispatch 语义和 catalog/golden 必须消费既有事件面基础，不能新增第二 bus。

**切片边界：** Villager Trades 可以独立收敛为一个很窄的 add+query 垂直路径；把 remove/replace/modify 和通用 registry 事务塞入同一票会暴露尚无回滚语义的公开写操作。

**协调事项（不等于额外阻塞）：**

- 与 EVENT_SURFACE owner 复用 ServerEvents 现有数据/reload 子事件与 bus/golden 规则；共享事件文件不构成本票阻塞。
- 与 RELOAD_COMMIT owner 对齐 server candidate preflight、commit 点和失败保留；与 GLOBAL_STATE owner 无直接依赖，脚本状态写集只作联合失败协调。

### 23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径

**What to build：** 整合包作者在既有 recipe/data 事件中编写 recipe schema、JSON builder、数据生成（不含 Assets）、loot 和 tags，并配置 recipe viewer 信息；调用经过 managed surface、平台/version Adapter、资源生成或回读，得到可验证产物、afterRecipes 时序、reload 清理、TS/Python declaration 和节点 capability。所有事件均复用原 bus，不新增平行事件。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)

**工作单：** 以现有 RecipeLifecycle、RecipeEventJS、MinecraftRecipeHandler、RecipeManagerMixin 和 catalog snapshot 为 characterization，建立 recipe schema/type/namespace contract fixture。、把 recipe JSON builder、值转换、filter、generated id 和错误归属接入同一 managed surface 与平台 Adapter，MC 类型/mixin 留在 src/或节点。、验证 DataGeneratorJS 的非 Assets 数据输出、路径、JSON 结构、失败保留和资源回读；Assets 继续复用既有 ClientEvents.generateAssets，不由本票新增事件。、为 loot table/pool/entry 与 tags 的既有事件时机、修改、冲突、reload 清理和生成产物建立 fixture。、为 recipe viewer/JEI 条件能力建立 adapter/source trace 与 declaration，不在未安装 JEI 的环境伪装能力。、输出 afterRecipes 生命周期、transaction/reload/delete-cleanup、两 loader artifact/runtime smoke 和 capability matrix 证据。、收缩 gate：recipe/data/loot/tags/viewer 旧绑定或生成旁路只有在替代 behavior、declaration、artifact/source trace 与无调用者证据通过后移除；公开事件与 helper 功能不删除，清理随域内票完成而不是 final release 统一处理。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 既有 recipe 事件名和 bus 没有第二份声明；catalog/golden 能从真实 runtime member 推导 TS/Python 声明。
- [ ] representative recipe schema/type/namespace 输入生成或修改预期 JSON，builder 值转换、filter 和 generated id 行为有测试。
- [ ] afterRecipes 只在 recipe 数据完整提交后的既有生命周期触发；失败、取消或 reload 中断不产生半更新。
- [ ] 非 Assets 数据生成的路径、JSON、覆盖策略、失败保留和回读结果可验证，且不新增 Assets 事件。
- [ ] loot 与 tags 的修改、冲突、删除/清理和 reload 后 stale 状态有外部行为 fixture。
- [ ] recipe viewer 信息仅在相应 JEI/平台条件成立时声明 supported，否则显式 partial/unavailable，不静默 no-op。
- [ ] MC 类型、mixin和平台 registry/loader 操作留在 共享 MC-facing 或节点 Adapter，common 不复制平台业务逻辑。
- [ ] NeoForge/Fabric 的 artifact、source trace 和 runtime smoke 按节点等级记录，不能用局部单测冒充。
- [ ] 脚本错误能定位到事件域、owner、源文件和生成/修改阶段，普通错误不嵌 validator 提示。
- [ ] 不把每个 recipe helper升格为新 Extension Point，不造第二 recipe registry或万能数据 catalog；旧旁路仅在替代 behavior、declaration、trace 通过且无调用者后移除，公开事件/helper不删除，清理不推迟 final release。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：本票需要事件名、payload、side、catalog 和 declaration 的单一规范链；事件基础未收口前会为 recipe 域另建第二 surface。

**切片边界：** 这是既有事件域的窄垂直路径，按 recipe/data/loot/tags/viewer 的共同生命周期和资源结果验收，避免按 wrapper 类、mixin或测试层拆散，也避免与其他事件域合成巨票。

**协调事项（不等于额外阻塞）：**

- RUNTIME_ROOT/RELOAD_COMMIT: recipe reload 事务、generation 可见性和失败保留由 runtime 语义承接；本票可在现有 runtime 上先交付域 fixture。
- BUILD_BASELINE: 现有 recipe/datagen catalog 与生成资源是 characterization 输入。
- REGISTRY_STARTUP: recipe schema/type 与启动 registry 元数据若有交集，注册事实源由 registry 组保留，本票只消费。
- client/JEI 集成拥有者: recipe viewer 的原生 JEI 接线如需客户端改动，本票提供 contract/source trace 并协调，不重写 client 域。

### 24 Capability/goal/实体行为既有事件面覆盖路径

**What to build：** 脚本作者继续使用既有 capability、goal 和实体行为事件（含实体加入/离开、伤害、死亡、掉落、生成_finalize 等 wrapper 面）完成订阅、修改、取消和清理；事件公开名、payload、side、priority/cancel、平台差异和 TS/Python declaration 由 managed catalog/contract 固定，不新增事件或万能 Event Module。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)

**工作单：** 盘点现有 CapabilityEvents、GoalEvents、EntityEvents 及 entity/living wrapper 的公开成员，形成真实 catalog snapshot 差异，不逐 symbol 开票。、为 capability、goal、实体生命周期/伤害/死亡/掉落/生成行为各选 representative caller path，验证注册、payload、修改、取消、优先级和 side。、把 wrapper 公开名与 payload 纳入 managed contract/golden，原生 NeoForge/Fabric callback和 mixin留在平台 Adapter 并记录 source trace。、验证并发/多次 reload 后 listener 清理、无重复 dispatch、取消结果和线程/时机语义。、补 TS/Python declaration parity、错误阶段与逐节点 capability matrix/source trace/smoke。、收缩 gate：capability/goal/entity 旧 wrapper、binding 或声明旁路只有在替代 behavior、declaration、trace 与无调用者证据通过后移除；公开事件功能不删除，清理随本票完成而不是 final release 统一处理。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] capability、goal、entity 行为事件在真实 catalog snapshot 中没有遗漏家族；新增/删除公开成员会产生 contract diff 而不是静默 drift。
- [ ] 每个 representative path 从脚本 listener 注册到平台 callback、wrapper payload、执行或取消结果可追踪。
- [ ] priority、cancel、返回值修改和多次订阅行为符合既有语义，并发 stress 后无重复 dispatch或半清理状态。
- [ ] server/client side 过滤、mixed side 与 loader/version capability 显式记录；不可用能力不静默 no-op。
- [ ] 实体加入/离开、伤害、死亡、掉落、finalize spawn 等行为至少各有一条 caller-to-result fixture，覆盖当前公开家族代表。
- [ ] capability/goal 事件不与启动 registry、交易或客户端实现 owner混淆；本票只冻结事件面和 Adapter 交界。
- [ ] TS/Python declaration 与 runtime member/payload一致，普通测试不更新 golden。
- [ ] NeoForge/Fabric source trace 和 runtime smoke 按支持等级记录，不能用反射清单替代。
- [ ] 不新增平行事件、Extension Point或第二注册路径。
- [ ] 旧 wrapper/binding/声明旁路只有在替代 behavior、declaration、trace 通过且无调用者后移除；公开 capability/goal/entity 事件功能不删除，清理不推迟 final release。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：capability/goal/entity wrapper 需要复用同一事件 contract、bus 清理和 declaration 链；直接逐 symbol 补测试会留下第二套事件规范。

**切片边界：** 按用户补充，把 capability/goal/entity 行为放入一个足够窄的既有事件域票，用家族 representative path 加 catalog diff 防漏，不为每个 symbol开票，也不造新事件。

**协调事项（不等于额外阻塞）：**

- RUNTIME_ROOT/RELOAD_COMMIT: listener generation、失败保留和清理语义与 runtime 组共同验证。
- REGISTRY_STARTUP: capability registry/type 事实源如涉及启动注册，由 registry 组负责；本票不迁移 registry mutation。
- client 域拥有者: render/client-only 实体或 UI 相关平台实现不由本票重写，只保留事件公开面边界。
- BUILD_BASELINE: 当前 catalog 与事件 wrapper 行为先作 characterization，再冻结有意承诺。

### 25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径

**What to build：** 脚本作者调用 DataMap 的 furnaceFuel/compostable 等只读查询，或用 EntitySelectors factory/builder/query 生成选择器并执行查询；请求经既有 tier的 binding 与平台/version Adapter返回快照或结果，缺失/非法输入得到普通可读错误，TS/Python declaration 和 capability 与真实行为一致。tier 按 source contract 归类，规范化不默认 stable；二者都不事件化。

**Blocked by：** [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)

**工作单：** 将 DataMapJS 与 EntitySelectorsJS/Builder/factory 按 source contract 归类为 query binding，不复用事件 owner，也不创建 Point。、为 DataMap furnaceFuel/compostable 等既有查询建立命中、缺失、空值、类型转换和只读结果 fixture；NeoForge data map 与 MC-facing类型由平台/版本 Adapter持有。、为 EntitySelectors builder/query 建立 server/test side、selector 语法、level/entity 输入、命中与非法 selector/level 错误 fixture。、把两个查询域的 runtime member、TS/Python declaration、Probe parity 和 loader/version capability 纳入 contract/golden。、记录 current path、target owner、Adapter source trace、替代覆盖和旧路径删除条件；当前入口存在不等于 managed stable。、收缩 gate：DataMap/EntitySelectors 旧查询旁路只有在替代 behavior、declaration、trace 与无调用者证据通过后移除；公开查询功能不删除，清理随本票完成而不是 final release 统一处理。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] DataMap representative 查询命中返回既有平台数据快照，未命中/缺失按公开语义返回空或明确错误，不暴露可变 registry view。
- [ ] EntitySelectors factory/builder/query 在 server/test side 生成并执行预期 selector，返回可验证实体结果。
- [ ] 非法 selector、非法 level 或缺失输入得到普通错误，包含域、调用入口和源位置，不嵌修复提示。
- [ ] DataMap 和 EntitySelectors 均无新增事件、无事件包装器、无无生命周期 Point。
- [ ] source contract 明确二者既有 tier 与能力，不因现有 binding/LEGACY_PREVIEW 收录而自动升级 managed stable。
- [ ] TS/Python declaration、Probe 输出与 runtime member/signature 一致。
- [ ] NeoForge/Fabric/1.21.1 capability 按 source trace 和真实 smoke/fixture 记录 supported/partial/unavailable。
- [ ] MC-facing data map/selector 类型与平台差异由平台/版本 Adapter持有；共享契约与查询 binding 不引入 Minecraft/loader 依赖。
- [ ] 每个域的替代查询面、旧路径消费者和删除条件可追踪；公开删除仍需维护者确认。
- [ ] 旧查询旁路只有在替代 behavior、declaration、trace 通过且无调用者后移除；公开 DataMap/EntitySelectors 查询功能不删除，清理不推迟 final release。

**阻塞理由：**

- [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)：查询工具的既有 tier、签名、capability 和 TS/Python declaration 必须由 source contract/catalog 归类并派生；现有 binding 的存在不把 legacy/raw 规范化为 managed stable。

**切片边界：** 每个查询域都走 Query+Adapter+declaration fixture 的一条 path；两者可在同一窄票中闭合非事件查询工具的共同验收，但 coverageDomains 明确分开，不互相冒充。

**协调事项（不等于额外阻塞）：**

- REGISTRY_STARTUP: DataMap 的 Registry Runtime 查询面归属与启动 registry/类型元数据由 registry 组协调，本票负责查询 binding 到 Adapter 的完整路径。
- MANAGED_SURFACE: declaration/golden 再生成必须走显式审阅流程。
- DIAGNOSTICS: 查询错误复用统一错误上下文，不创建第二错误面。

### 26 CLIENT 输入与 HUD callback 生命周期

**What to build：** CLIENT 脚本的 keybind 注册、pressed/released/tick 输入 callback 和 HUD/overlay callback 从 ClientEvents 调用者 Interface 到平台 client Adapter 的注册、owner-thread dispatch、reload 清理、client-only 过滤、声明与按节点能力验证；不包含 GUI 屏幕与 render Adapter 资源域。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** W7；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] CLIENT session 由唯一 Runtime Root 在客户端启动入口创建，早期 key mapping、client tick 和 timer flush 在同一 owner 语义下发生；dedicated server 或裸 JVM 不加载 client-only 类。
- [ ] keybind 注册在正确注册期执行且同 full id 幂等，CLIENT reload 不重复 KeyMapping；pressed/released/tick 事件只派发给新 generation，非法 id/key/category 显式失败并给出可定位错误。
- [ ] 输入与 HUD/overlay callback 继续复用现有 ClientEvents 事件成员与平台 Adapter；事件名、payload、side filter、取消/优先级和触发线程在 catalog/golden 中唯一，不新增重复 bus。
- [ ] reload candidate 阶段新 keybind/HUD listener 对生产路由不可见；commit 后旧 listener/timer/handler 停止接收新 callback，新 generation 恰好执行一次，失败或取消时旧 active 继续可用且候选资源全部清理。
- [ ] 按键状态、consumeClick 与 HUD 呈现结果可从脚本调用者 Interface 观察，断言不依赖 KeyMapping 私有集合或平台回调对象身份。
- [ ] 平台/版本 Adapter 按各节点既定支持等级与声明能力验证真实差异；client-only 过滤、注册时机或输入事件不可用时以 supported/partial/unavailable 明示，不自动补 Fabric parity。
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 和节点 runtime smoke 可互相追溯；普通测试只读 golden，更新需旧新 diff、影响说明和维护者审阅。
- [ ] GUI、render Adapter、PostEffects 与 Assets 保持独立 owner；本票不重复声明或清理它们的资源。
- [ ] 旧输入/HUD入口、重复 handler 或绕过 Runtime Root 的静态装配只能在替代路径 parity、公开迁移表、旧 route 无消费者和维护者确认后删除；不保留长期双路径。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：ClientEvents 输入/HUD成员、side filter、取消/优先级和 catalog/golden 消费由事件面基础提供，不得重复声明 bus。

**切片边界：** keybind 与 HUD/overlay 是同一条 CLIENT 输入/callback 生命周期路径，可单独注册、dispatch、reload 清理和 smoke；GUI/render Adapter 资源另拆，避免原合并客户端票过宽。

**协调事项（不等于额外阻塞）：**

- 与 CLIENT_GUI_RENDER owner 并行协调 ClientEvents 与平台 client Adapter；两票不互相硬串。
- 与 EVENT_SURFACE owner 协调成员唯一性、side filter 与既有 bus；事件基础实现是 blocker，具体成员仍需协调。
- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 client owner thread、generation 路由和失败清理；共享 runtime 文件只作接口协调。

### 27 CLIENT GUI 与 render Adapter 资源呈现清理

**What to build：** CLIENT workspace/error dashboard 等 GUI 调用路径与 render/screen/world render Adapter 资源从注册、呈现到 generation 清理的完整路径；事件成员、side filter、client-only 过滤、声明、按节点能力与迁移可验证，不包含 keybind/HUD 输入域。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** W7；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] workspace、error dashboard、菜单/编辑器等 GUI 调用者 Interface 能打开、执行动作、渲染并报告错误；错误与诊断事实来自 runtime/diagnostics owner 的共享状态，不在 GUI 内建立第二事实源。
- [ ] render、world render 和 screen render callback 继续复用现有 ClientEvents/render 事件与 Adapter；事件名、payload、side filter、取消/优先级和触发线程在 catalog/golden 中唯一，不新增重复 bus。
- [ ] 平台 client Adapter 在正确注册期挂载 render/screen 资源并在 render owner thread 分发；Adapter 持有 MC/loader 类型，shared 作者契约不引入平台类型。
- [ ] reload candidate 阶段新 GUI/render 资源与 listener 对生产路由不可见；commit 后旧 generation 停止接收 callback 并按所有权释放，新 generation 恰好呈现一次，失败或取消时旧 active 继续可用且候选资源全部清理。
- [ ] GUI 操作、render context、取消和错误呈现可从调用者 Interface 观察；断言不依赖私有屏幕字段、Renderer 对象身份或未公开平台集合。
- [ ] 平台/版本 Adapter 按各节点既定支持等级与声明能力验证真实差异；不可用或部分可用时以 supported/partial/unavailable 明示，不自动补 Fabric parity，也不把 experimental 当 primary。
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 和节点 runtime smoke 可互相追溯；普通测试只读 golden，更新需旧新 diff、影响说明和维护者审阅。
- [ ] PostEffects、Assets、recipe/loot/tags/JEI/capability/goal/keybind/HUD 等既有 owner 不被并入本票；本票只验证不重复声明这些事件或 binding。
- [ ] 旧 GUI/render 入口、重复 handler、不受测 wrapper 或绕过 Runtime Root 的资源装配只能在替代路径 parity、公开迁移表、旧 route 无消费者和维护者确认后删除；不保留长期双路径。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：ClientEvents GUI/render 相关成员、payload、side filter、取消/优先级和 catalog/golden 消费由事件面基础提供，不得重复声明 bus。

**切片边界：** GUI 与 render Adapter 共享呈现资源、owner thread 和清理边界，能形成一条新上下文可验的呈现路径；输入/HUD callback 生命周期另有触发与注册语义，继续拆开。

**协调事项（不等于额外阻塞）：**

- 与 CLIENT_INPUT_HUD owner 并行协调 ClientEvents 与平台 client Adapter；两票不互相硬串。
- 与 EVENT_SURFACE owner 协调成员唯一性、side filter 与既有 render bus；事件基础实现是 blocker，具体成员仍需协调。
- 与网络/诊断 owner 协调 error dashboard 数据与 packet 投影；GUI 只做投影，不反向拥有错误事实源。
- 与 POST_EFFECTS/ASSETS owner 并行协调 ClientEvents 使用点，避免同文件改动被误写成串行依赖。

### 28 PostEffects 声明事件与运行 binding 分离

**What to build：** 客户端脚本通过现有 ClientEvents 的资源/reload 子事件声明 PostEffects register/unregister，候选定义在 preflight 与资源生成完成后按 generation 提交；set、clear、toggle、current 继续作为运行时 binding/Adapter。资源 reload、失败保留、旧 generation 清理、声明 parity 与平台能力从调用者 Interface 到节点 Adapter 可验证。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)、[reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md)

**工作单：** W7；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] register/unregister 只通过现有 ClientEvents 客户端资源/reload 子事件贡献；调用者 Interface、事件成员、payload 和 side filter 在 catalog/golden 中唯一，不新增第二个 PostEffects 事件。
- [ ] candidate 阶段只生成定义、JSON/Shader 资源、generation 标记和 Adapter 请求，不提前挂载生产 listener、激活 post chain 或修改当前客户端画面。
- [ ] 合法 commit 后新 generation 的 PostEffects 定义可回读并生效；旧 generation 的注册、listener 和资源不再接收 callback，释放顺序可观察且不产生双重渲染或半更新。
- [ ] 候选 JSON/Shader 无效、资源生成失败、reload 中断、客户端不可用或 commit 取消时旧 active 资源保持可用，候选资源与临时注册全部清理。
- [ ] set、clear、toggle、current 仍是运行时 binding/Adapter，并保持现有调用者可见行为；这些成员不被声明事件替代、删除或误标为 reload 事务操作。
- [ ] 26.x 与 1.21.1 的 PostChain/Shader JSON 形状、资源路径和 mixin/Adapter 时机有 fixture 与节点 smoke 证明；平台差异不进入 common 作者契约。
- [ ] TS/Python declaration、runtime member、contract/golden 与实际事件/binding 成员一致；EntitySelectors、Assets 和已事件化 render 域不被并入本票或重复声明。
- [ ] Fabric 或旧版本不可用时 capability/source-trace/smoke 显式记录 unavailable 或 partial 并给出确定失败；不用静默 no-op、空画面或无错误返回冒充支持。
- [ ] 旧 PostEffectsJS 静态 register/unregister 直连路径只有在事件资源生命周期、运行 binding parity、迁移表、旧 route 无消费者和维护者确认后删除；运行时操作仍保留在最终 binding 面。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：ClientEvents 资源/reload 子事件 wrapper、成员目录、dispatch 语义和 catalog/golden 必须消费既有事件面基础，不能新增第二事件。

**切片边界：** PostEffects 的核心风险是把声明生命周期与运行时动作混在一起；单独收口能明确证明 register/unregister 事务化而 set/clear/toggle/current 仍是 binding。

**协调事项（不等于额外阻塞）：**

- 与 CLIENT_GUI_RENDER owner 并行协调 ClientEvents 与 client Adapter 生命周期；共享事件面不是串行 blocker。
- 与 EVENT_SURFACE owner 确认资源/reload 子事件的 catalog/golden 唯一性。
- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 candidate 资源、commit 与清理顺序。

### 29 Assets 单事件生成与资源回读收口

**What to build：** 脚本作者继续通过已有 Assets typed binding 与唯一 ClientEvents.generateAssets 事件生成 blockstate、model、texture 等资源；生成器路径校验、既有资源写入行为、plugin 与脚本贡献聚合、资源 pack reload 回读、client-only 过滤、声明与按节点 capability 由同一条 Adapter 路径验证，不新增第二 Assets 事件、直写旁路或新资源 policy。

**Blocked by：** [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)、[NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md)

**工作单：** W7；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] ClientEvents.generateAssets 仍是唯一资源生成事件；Assets typed binding 和 plugin contribution 复用同一 DataGenerator/资源根，不新增第二事件、第二根目录、第二生成管线或新资源 policy。
- [ ] blockState、blockModel、itemModel、texture 等调用者成员的参数规范化、默认 namespace/path 补全、JSON 与占位 PNG 输出保留现有已验证行为并有确定 contract/golden 或回读 fixture；本票不为资源限额或 PNG 生成新造 policy。
- [ ] 资源写入保留现有路径包含性、容量、非法 id、冲突 kind 和原子替换行为；无效输入不产生部分文件、不越过资源根，也不把异常路径写进资源 pack。
- [ ] plugin generate-assets Hook 与脚本事件在同一 client generation 聚合，事件按资源 reload 生命周期恰好触发一次；懒读或显式 reload 后资源能被实际 resource manager 回读。
- [ ] client-only 过滤证明 dedicated server 不注册入口、不加载 client 类、不写资源；脚本 side 与节点 capability 一致。
- [ ] 平台/版本 Adapter 按各节点既定支持等级、声明能力与现有限制执行资源 pack 注册、路径和 reload 验证；supported/partial/unavailable 由真实 source trace 与 smoke 决定，不自动补 Fabric parity。
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 与生成文件互相追溯；普通测试不得更新 golden 或资源基线，显式更新需旧新 diff 与审阅。
- [ ] EntitySelectors、DataMap、PostEffects 和已事件化 recipe/loot/tags/JEI/render 域不被并入本票；本票只证明不重复它们的 owner 或事件。
- [ ] 旧直接文件写入、绕过 DataGenerator 的 asset helper 或重复生成入口只能在生成/回读 parity、迁移表、旧 route 无消费者和维护者确认后删除；不保留长期兼容双路径。

**阻塞理由：**

- [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)：ClientEvents.generateAssets wrapper、成员目录、dispatch 时机和 catalog/golden 必须消费既有事件面基础，不能新增第二 bus。

**切片边界：** Assets 已有明确单一事件和 typed binding；本票只需垂直验证复用、路径安全、资源回读和 client-only 边界，把 PostEffects 或 query 工具混入会重复 owner。

**协调事项（不等于额外阻塞）：**

- 与 CLIENT_GUI_RENDER owner 并行协调 ClientEvents.generateAssets 与 client reload 时机；共享事件面不构成串行 blocker。
- 与 EVENT_SURFACE owner 确认 generateAssets 在 catalog/golden 中仍只有一条 bus。
- 与 registry startup owner 只协调默认 block/item model 的输入来源，不把启动注册票作为 Assets blocker。

### 30 错误诊断、telemetry、workspace 与用户报告链路

**What to build：** 脚本语法/转换、模块 resolve/link、Graal 执行、reload/cancel、trust 和 watchdog 错误进入统一错误上下文，保留阶段、owner、generation、ScriptType、模块身份和 source map 后的原始位置；日志历史、telemetry、workspace 打开/编辑、dashboard 和用户可见报告看到同一事实源。普通 runtime 错误保持普通形式，不嵌离线 validator 或迁移修复提示。

**Blocked by：** [12 TS/JSX/TSX 编译、source map 与执行行为路径](#12-tsjsxtsx-编译source-map-与执行行为路径)、[13 Python 转译、模块行为与诊断路径](#13-python-转译模块行为与诊断路径)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)

**状态：** 草案，尚未发布。

**来源 spec：** [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md)、[语言模块管线规格](specs/06-language-module-pipeline.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** 统一 ErrorTracker/ScriptError/ErrorSummaryDTO/ScriptErrorReporter 的错误 ID、阶段、owner、source、generation、ScriptType、module identity、cause 和用户可见字段投影。、为 JS/CJS/ESM、TS/JSX/TSX、Python 的准备、resolve/link、缓存、执行和 reload/trust/watchdog 错误建立 source-map 与阶段矩阵。、让日志历史、dashboard packet/屏幕、workspace 打开与编辑动作、用户报告和 telemetry 投影消费同一 frozen diagnostic record，不形成第二错误事实源。、保留 JavaClassLoadTelemetry 与 runaway watchdog 的既有可观察语义，补充隐私/体积边界和可重复采集口径。、验证 reload boundary：candidate 失败保留 active、generation/owner可读、旧错误历史不误归属新代际；外部副作用边界显式可见。、把可选 offline validator/migration report 保持为显式、默认只读的离线产物，不进入普通错误路径或 release 硬 gate。、收缩 gate：旧错误 tracker/report/dashboard旁路只有在替代 behavior、declaration/字段投影、trace 与无调用者证据通过后移除；公开诊断/workspace功能不删除，清理随本票完成而不是 final release 统一处理。；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] syntax/transform、resolve/link、cache、runtime、reload/cancel、trust 和 watchdog representative 错误均有正确阶段和 owner。
- [ ] JS、CJS、ESM、TS、JSX、TSX、Python 的错误位置能回映射到原始 source，并保留模块身份、行列和 cache/revision 信息。
- [ ] 同一错误在日志、ErrorSummaryDTO、dashboard、workspace 和用户报告中呈现一致的核心字段，没有第二事实源。
- [ ] workspace 能定位并打开对应源文件；generation、owner、ScriptType和 source path 人类可读。
- [ ] 在 RELOAD_COMMIT 后的 candidate/active 状态中验证失败、取消、watchdog 终止与恢复：旧 active 错误历史不丢失，新候选错误不伪装成 active generation；旧 fixture只能作对照。
- [ ] telemetry/Java class-load/watchdog 记录可重复采集、可关闭或降级，并写明是否包含用户路径、脚本内容或环境信息。
- [ ] 普通 runtime 错误文本不包含 offline validator、migration report 或修复提示；辅助工具只能显式独立运行。
- [ ] 历史日志与用户编辑 workspace/declaration 在验证和迁移中不被覆盖或删除。
- [ ] 诊断 golden 只冻结公开字段和用户可见语义，不冻结 UI私有对象、布局或私有异常对象身份。
- [ ] 跨 loader 的 GUI/packet投影与真实诊断记录一致，网络/客户端实现改动通过协调验收而非本票重写；旧投影/报告旁路仅在替代 behavior、declaration/字段投影、trace 通过且无调用者后移除，公开功能不删除，清理不推迟 final release。

**阻塞理由：**

- [12 TS/JSX/TSX 编译、source map 与执行行为路径](#12-tsjsxtsx-编译source-map-与执行行为路径)：完整验收必须包含 TS/JSX/TSX 的原始 source-map、cache revision与阶段映射；仅用 LANGUAGE_PIPELINE 的 JS 基线不能代表全语言诊断。
- [13 Python 转译、模块行为与诊断路径](#13-python-转译模块行为与诊断路径)：完整验收必须包含 Python 的原始 source-map、cache revision与阶段映射；仅用 LANGUAGE_PIPELINE 的 JS 基线不能代表全语言诊断。
- [07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)：诊断验收包括 active watchdog 隔离、取消及关闭状态，不能用旧调度状态代替新结果。

**切片边界：** 诊断是语言、事件、查询和 runtime 的共同用户可见结果；该票以统一上下文和投影一致性为边界，既不接管网络/client实现，也不把工具提示混入普通错误。

**协调事项（不等于额外阻塞）：**

- RUNTIME_ROOT/RELOAD_COMMIT: active/candidate 保留、generation 切换和 watchdog 恢复语义由 runtime 组提供，本票负责诊断投影一致性。
- GLOBAL_STATE: 报告不得通过静态全局旁路读取错误状态。
- MANAGED_SURFACE: 诊断公开字段/错误码进入 managed contract，Probe/declaration只作投影。
- EVENT_SURFACE/RECIPE_DATA_SURFACE/GAMEPLAY_EVENT_SURFACE/QUERY_TOOLS: 各域错误必须回填阶段和 owner，不用私有异常绕过统一链路。
- PERF_BASELINE: telemetry采集成本有基线对照，不自行设定发布阻断阈值。

### 31 Fabric raw loader 源根显式所有权迁移

**What to build：** 把两个 Fabric 节点共享的 raw loader 源、资源、模板和 runtime smoke fixture 迁到由 Fabric convention 显式挂载的唯一 raw 根，并保持迁移前后构建、制品与 smoke 可用。

**Blocked by：** [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[平台构建与 Stonecutter 策略规格](specs/03-platform-build-strategy.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** W8；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 两个 Fabric 节点只通过 Fabric convention 显式挂载共享 raw loader 根；不声称或依赖 Stonecutter 自动预处理该根，未处理的版本差异由既有 compat facade 或节点 override 承担。
- [ ] Stonecutter、五个节点和支持等级保持不变；不新增 Gradle project、API jar或替代版本树。
- [ ] Fabric Java、资源、模板与 runtime smoke fixture 的所有权落位完整，旧路径消费者全部更新或显式归类为过渡引用。
- [ ] runtime smoke fixture 的测试资源接线与 CI 拷贝用途分别记录；CI 直接复制不被误写成 Gradle 测试资源已消费。
- [ ] guard lint 因新增 raw 根扩大的扫描范围被记录并零违规，common 的 Minecraft/loader 隔离和 Graal 许可没有被放宽。
- [ ] 两个 Fabric 节点的编译、检查、制品验证和现有 smoke 通过；失败时能回滚到迁移前源根与 CI 接线。
- [ ] NeoForge 防御性排除、Fabric 禁止资源检查和 Fabric 制品验证仍保留，除非另有独立证明，不因目录收口顺手删除。
- [ ] 迁移前后两个 Fabric 节点的 artifact、resource、mixin、metadata 和 smoke 结果对照无未说明差异；26.2 节点身份、坐标语义和制品命名不变。
- [ ] source bridge、bridge 引用和旧排除规则在本票保持可回滚，不提前删除。

**阻塞理由：**

- [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)：迁移前后 artifact、resource、mixin、metadata、source 与测试对照必须以 P0 基线为输入。

**切片边界：** 它以两个 Fabric 节点的真实构建和 smoke 证明新的显式源根可用，而不是只完成目录搬迁。

**协调事项（不等于额外阻塞）：**

- 与 runtime smoke 和 CI owner 对齐 fixture 消费者；与功能域 owner 解释有意 artifact 差异，但这些沟通不是源根迁移的技术阻塞。

### 32 Fabric 五层源唯一性与 bridge 删除条件

**What to build：** 用 raw、processed、class、去重前打包输入和最终 jar entries 的五层 trace 证明 Fabric 源唯一性和跨 loader 不挂载，并在全部删除条件满足后收掉 bridge；任一缺口则保留 bridge 并回滚。

**Blocked by：** [31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[平台构建与 Stonecutter 策略规格](specs/03-platform-build-strategy.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** W8；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] raw 源、processed 源、编译 class、Jar 去重前输入和最终 ZIP entries 分层记录，生成副本不被误认为第二事实源。
- [ ] 七个同名 FQCN 在五层 trace 中均能解释 origin 与最终重复计数；去重策略、集合存在性、guard 数量或源文件数量不作为唯一性证明。
- [ ] 若某版本差异没有预处理方案，报告明确为未处理并由 compat facade 或节点 override 承担，不写成已由 Stonecutter 处理。
- [ ] 两个 Fabric 节点分别通过编译、检查、制品验证和 runtime smoke，最终 jar 的重复计数与预期一致。
- [ ] 三个 NeoForge 节点检查通过，并证明不挂载 Fabric raw 根或其生成副本。
- [ ] sandbox 聚合检查通过，且结果覆盖 guard lint 与节点检查，而不是只引用任务存在。
- [ ] 迁移前后 source、artifact、resource、mixin 和 metadata trace 等价；每个有意差异都有能力或迁移说明及 owner。
- [ ] 只有五层证据、两个 Fabric 门禁、三个 NeoForge 不挂载检查、聚合检查、smoke 与 fixture 消费者全部满足，且 bridge 依赖、引用与旧排除规则均有替代证明时，才删除 bridge。
- [ ] 任一条件不满足时 bridge 保留，迁移回滚或缺口修复路径明确，不用删除证据来源来换取收口。

**阻塞理由：**

- [31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)：五层 trace、节点门禁和 bridge 删除条件必须基于已显式挂载且可构建的新 raw 根评估。

**切片边界：** 它单独形成可判定的 bridge 删除/保留决策，避免把大规模源迁移和五层唯一性证明压进同一上下文。

**协调事项（不等于额外阻塞）：**

- 与 CI 和 runtime smoke owner 同步 fixture 路径与证据格式；与功能域 owner 复核有意 artifact 差异。

### 33 CI 用途子集与 Fabric processor 延期替代 gate

**What to build：** 收口构建约定与 CI：各类手写节点子集按用途核对，Fabric processor 在 1.2.0 明确延期，并用真实非 processor 的 contract/spec、event/surface 与 declaration 覆盖 gate 补足延期说明。

**Blocked by：** [32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[平台构建与 Stonecutter 策略规格](specs/03-platform-build-strategy.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** W9；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] Fabric common-api-processor 在 1.2.0 保持未接入，延期原因和未覆盖范围公开，不被描述为 NeoForge processor 等价或临时接通。
- [ ] contract/spec、event/surface、declaration 三类非 processor gate 分别有 owner、输入、逐项输出和失败诊断，并能指出缺失的 contract、method、platform、domain、binding、member 或 type。
- [ ] 没有证据的项保持 not verified 并阻塞对应域验收；不得因缺测试直接改判为 unavailable 或 partial，也不得用改表掩盖规范 ALL 与实际能力的差异。
- [ ] NeoForge-only NBT、Fabric-only artifact/smoke、全节点 build、release/publish 子集分别按用途与节点事实源核对，不用单一等值检查冒充所有用途。
- [ ] 每个 CI 子集都有用途说明、节点覆盖、 intentional skip 和一致性检查；manifest 仅作为派生快照，不成为第二节点事实源。
- [ ] 五个节点的 check、artifact 与 source trace 结果进入同一报告；Fabric artifact 验证和已声明能力 smoke 不被静默省略。
- [ ] NeoForge 既有接线保持，过时 Graal lint 更新且不放宽 Minecraft/loader 隔离；不新增 Gradle project、API jar，不删除 Stonecutter，不改变支持矩阵。
- [ ] CI 或 gate 失败输出能定位节点、输入、期望结果和 owner，而不是只留下任务失败摘要。
- [ ] 本票完成表示 CI 与替代 gate 可发现并验证输入，不代表尚未迁移的新功能域已验收；后续域票提交其真实 fixture 并通过同一 gate。

**阻塞理由：**

- [32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)：CI 消费者、节点 source trace 和 Fabric 制品 gate 必须基于最终 raw 根与 bridge 判定结果。
- [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)：非 processor 的 contract/spec、event/surface 与 declaration gate 需要规范契约、coverage ledger、golden 和 declaration fixture 作为真实输入。

**切片边界：** 它交付可运行的 CI/覆盖 gate 和诚实的 processor 延期结论，独立于具体功能实现且可由报告逐项验收。

**协调事项（不等于额外阻塞）：**

- Managed Surface/Probe owner 提供契约与 declaration fixture，build owner 负责接线与报告；CI secret、runner 或共享环境只作协调，不作为 blocked by。

### 34 P4 五节点整体验证与能力矩阵收口

**What to build：** 在主整合完成后执行一次跨五节点的 P4 总体验证，汇总 build/check/artifact、contract/golden、runtime smoke、source trace、coverage ledger、能力矩阵和旧新对照，形成 release 是否可继续的判定。

**Blocked by：** [33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)

**状态：** 草案，尚未发布。

**来源 spec：** [PR 37 维护体验回归约束规格](specs/00-pr37-maintainer-research.md)、[维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[平台构建与 Stonecutter 策略规格](specs/03-platform-build-strategy.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** P4、W10；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 主节点通过编译/检查、artifact与 metadata、contract、data fixture、runtime smoke 和维护者试做所需输入；任一必要缺口阻塞 release。
- [ ] 次级 NeoForge 节点保持可构建、可发布验证和公开契约可追踪，版本差异进入 capability matrix 与发布说明。
- [ ] 三个 experimental 节点至少通过可重复构建、artifact 验证和已声明能力 smoke；不静默承诺完整 parity。
- [ ] 每项能力只以 supported、partial、unavailable 表达实际能力；not verified 与 deferred 作为独立证据/安排维度并阻塞对应未闭合域。
- [ ] managed、legacy、plugin、registry、packet、diagnostic、语言和功能的 contract/golden 均有旧新 diff、原因、影响和审阅记录，普通测试没有改写基线。
- [ ] runtime smoke 使用最终 remap 制品和真实 mods 场景，不把 checkout 开发运行冒充 P4 证据；每个节点输出发现/跳过/执行与失败日志。
- [ ] coverage ledger 每行都有 current path、target owner、gate、证据和删除条件；没有任何功能域因整体通过而被遗漏。
- [ ] 维护者确认的性能发布政策已存在；P4 验证按该政策记录性能 gate 状态，不在本票临时设置或修改数字。
- [ ] Point、Contributor、Hook、显式依赖、freeze 与 Handle 的既有收益没有被重造或回归；新增通道仍只有一个事实源。
- [ ] 所有失败按节点、输入、期望、实际和 owner 记录并阻塞 release，不因单次失败自动降级或 EOL 任何节点。

**阻塞理由：**

- [33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)：最终节点与能力验证必须包含 CI 用途子集和 Fabric processor 延期替代 gate 的结果。
- [04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)：P4 总体验证开始前，性能发布政策必须已由维护者基于 P0 基线确认，后续验证只执行已定政策。
- [08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。

**切片边界：** 它是最终 release 前唯一的全域证据汇总点，但以已完成的主整合和各域 gate 为输入，避免横向重做实现。

**协调事项（不等于额外阻塞）：**

- 由主整合 owner 展开 FEATURES_COMPLETE 后并行收集各域报告；本票不拆解或代替各功能域实现。

### 35 P4 性能复测与政策对照

**What to build：** 在主整合完成后按已确认政策复测 startup、Probe、reload、tick、Adapter 与内存表现，对比 P0 基线和最终结果，并给出是否满足政策、是否需维护者重裁决的可审计结论。

**Blocked by：** [04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)

**状态：** 草案，尚未发布。

**来源 spec：** [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** P4；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 最终采样复用 P0 基线与已确认政策的节点范围、负载、预热、重复次数、统计口径和样本规则；任何必要偏离都记录原因与可比性影响。
- [ ] startup、Probe、reload、tick、Adapter 与 heap/memory 的最终观测逐项与 P0 基线对照，并保留原始样本、汇总方法和离群值处置记录。
- [ ] 复测结果逐项套用已确认政策，明确 pass、fail、not applicable 或需维护者重新裁决；实现者不新增或修改阈值数字。
- [ ] 环境、数据集、初始化状态、样本剔除和执行中断留档；不得选择性删除不利样本。
- [ ] 每个显著变化或政策失败有 owner、定位方向、是否阻塞 release 的结论和必要的维护者重裁决记录。
- [ ] 政策或测量口径在复测中不可临时调整；若环境或实现变化使政策失效，停下取得维护者新确认后再继续。
- [ ] 若后续发生新的性能相关行为改动，明确必须重新复测或重新裁决，不沿用过期对比结论。

**阻塞理由：**

- [04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)：进入 P4 复提前必须已有维护者确认的阈值/不设阈值政策、适用范围与失败规则；复测只执行和对照政策，不临时造政策。
- [33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。

**切片边界：** 它把政策执行、最终复测和基线对照连成一个可审计 release 输入，且不在 P4 临时决定阈值。

**协调事项（不等于额外阻塞）：**

- 执行、Probe、平台和数据 owner 确认负载与样本；若出现政策边界问题，交回维护者裁决。

### 36 P4 维护者四类真实试做

**What to build：** 由维护者在最终集成结构上完成新增事件、新增 Adapter、新增扩展点和新增版本四类真实任务，并记录入口、owner、事实源、依赖方向、受影响节点与测试，证明维护成本确实下降。

**Blocked by：** [04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)

**状态：** 草案，尚未发布。 **需维护者参与。**

**来源 spec：** [PR 37 维护体验回归约束规格](specs/00-pr37-maintainer-research.md)、[维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[平台构建与 Stonecutter 策略规格](specs/03-platform-build-strategy.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** P4；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 维护者真实完成新增事件、新增 Adapter、新增扩展点和新增版本四类任务；四类记录分别保留任务、入口、结果和遇到的问题。
- [ ] Adapter 试做区分注册类型/Builder 与平台能力接线两类差异，不把平台原生时机错误抽象进共享运行时。
- [ ] 每类任务都能从作者入口追踪到 owner、事实源、依赖方向、受影响节点和保护测试，不需要跨多处猜测或静默同步。
- [ ] 扩展点试做复用既有 Point、Contributor、Hook、显式依赖、freeze 与 Handle 语义，不重造生命周期、静态结果表或第二事实源。
- [ ] 新增版本试做覆盖节点身份、坐标、制品命名、CI 用途子集和受影响测试，不改变五节点支持等级。
- [ ] 每个试做改动在相关节点上通过必要检查，并能说明未覆盖节点的 gate 差异。
- [ ] 任何需要修改规则、复制共享逻辑、查多个登记点或猜 owner 才能完成的情况被记录为失败或待修复，不得宣称维护体验改善。
- [ ] 维护者明确确认四类任务可按新结构完成，未解决问题有 owner 和 release 影响。
- [ ] 新增版本试做在临时试做分支或夹具完成，保留验证记录，不把演示节点或试做功能合入最终五节点支持矩阵。

**阻塞理由：**

- [04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)：四类维护者试做属于 P4 活动，开始前必须已有维护者确认的性能发布政策，避免试做引入性能相关改动时缺少裁决口径。
- [33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)：新增事件、Adapter、扩展点和版本需要通过最终 CI/processor 延期替代 gate 验证影响面。
- [08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)：最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。

**切片边界：** 四类真实任务是独立于自动化报告的人类可用性 release gate，能单独审阅且不引入新测试框架。

**协调事项（不等于额外阻塞）：**

- 维护者亲自执行并确认；各域 owner 只提供入口说明和失败修复，不代替试做。

### 37 1.2.0 clean cutover 与发布交接

**What to build：** 在全部 P4 gate 通过后汇总公开迁移材料，核对各域旧路径已按删除条件收缩，完成最终版本切换与本地候选制品准备；仅处理已证明无调用者的少量残余过渡项，不在验收后启动大范围清理。任何影响候选制品的清理或版本包装后，重新验证确切产物；不执行远程上传。

**Blocked by：** [34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)、[35 P4 性能复测与政策对照](#35-p4-性能复测与政策对照)、[36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)

**状态：** 草案，尚未发布。

**来源 spec：** [PR 37 维护体验回归约束规格](specs/00-pr37-maintainer-research.md)、[维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md)、[版本与加载器支持矩阵规格](specs/02-support-matrix.md)、[平台构建与 Stonecutter 策略规格](specs/03-platform-build-strategy.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** P4、W10；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 每个 Script/Plugin public breaking 符号都有旧写法、1.2.0 新写法、替代路径、数据影响和维护者确认；没有迁移项被可选工具替代。
- [ ] 数据保护清单覆盖 config、world、实体/玩家 pdata、脚本与 pack、trust-store、workspace/declaration、日志与 cache 的可再生性、备份、保留和回滚。
- [ ] release rollback 与 data rollback 分开验证；旧制品回退不宣称恢复脚本对外部世界、网络或 Java 对象造成的副作用。
- [ ] 必要数据迁移具备备份或原子替换、版本/schema 标记、旧 fixture 回读、幂等验证、失败恢复和原始数据保留证据；无必要变化的默认路径、key、wire id、格式和启用规则未被暗中改变。
- [ ] 各功能域迁移票已按自身删除条件移除旧 public route、compat shim、重复 runtime path 或第二语义 pipeline，并完成对应域验证；本票只复核无残留，并只删除已被调用者证据证明为无调用者的小量过渡项，不在验收后临时扩大代码清理。
- [ ] 若最终清理、版本切换或发布包装改变确切候选制品，则对该候选制品重新执行必要 build、artifact、metadata、runtime smoke 和性能相关验证；旧制品证据不能冒充最终证据。
- [ ] README、wiki、ADR 与支持/能力矩阵的发布说明一致，公开说明 Fabric processor 延期、WORLD pack 差异和其他 partial/unavailable 能力。
- [ ] 可选离线 validator/migration report 保持显式运行、默认只读且不是 release blocker，也不进入普通 runtime 错误路径或第二套 Script API。
- [ ] 发布产物在本地完成准备、校验和交接清单核对；远程上传、渠道公告或正式发布动作未在本票执行，需维护者另行明确授权。

**阻塞理由：**

- [34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)：五节点、能力、契约、smoke 和 coverage ledger 的最终 release 判定必须已通过。
- [35 P4 性能复测与政策对照](#35-p4-性能复测与政策对照)：最终发布必须具备按已确认政策完成的 P4 复测、基线对照和失败处置结论。
- [36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)：四类维护者真实试做是 release gate。

**切片边界：** 它是所有技术 gate 之后的单一发布交接边界，能完整审计 clean cutover，同时明确不自动获得远程发布授权。

**协调事项（不等于额外阻塞）：**

- 主整合 owner 展开 FEATURES_COMPLETE 并提供各域完成证据；维护者审阅迁移、阈值、试做和最终发布决定。

### 38 离线 validator / migration report（可选）

**What to build：** 显式读取已有契约、迁移表和数据保护清单，输出只读的迁移检查与缺失信息报告；不进入普通 runtime 错误路径，不自动修改脚本、数据或规范源。

**Blocked by：** [03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)

**状态：** 草案，尚未发布。

**来源 spec：** [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md)、[NekoJS 验证与迁移规格](specs/07-validation-and-migration.md)

**工作单：** W10；具体源码落点与删除前提查 [实施交接单](implementation-handoff.md)，不在票内复制易过时的文件位置。

**验收条件（实施后逐项提供证据）：**

- [ ] 默认只读且仅显式启动；故障、取消或报告生成不得改动脚本、world、pdata、pack、trust-store 或用户编辑文档。
- [ ] 报告关联已有旧/新 public symbol 迁移记录与数据保护输入，缺失信息明确标记，不编造替代接口或迁移成功。
- [ ] 相同输入产生可比报告，非法或缺少输入有普通可读错误，不静默跳过风险。
- [ ] 只有用户明确选用时才实施；不作为任何必选票或 release gate 的先决条件。
- [ ] 报告不替代必需的迁移表、旧 fixture 回读、必要迁移/回滚与 contract diff 证据。
- [ ] 验证通过文件内容或校验和不变、公开报告内容与退出状态观察，不为报告新增全仓 catalog/事务/迁移框架。

**阻塞理由：**

- [03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)：报告必须消费受保护数据与旧格式的明确分类，不能自行判定哪些数据可删或可迁移。
- [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)：符号与能力报告使用同一规范源及派生输入，不建立第二份 Script API catalog。

**切片边界：** 单独可选的只读输入到报告路径，不影响必选实现依赖图。

**协调事项（不等于额外阻塞）：**

- 消费各域随实现更新的迁移记录；报告缺少尚未实施域的结果时必须明示。


## 来源与完整覆盖

来源为 [全部规格索引](specs/README.md)、[实施交接单](implementation-handoff.md) 与 [现有功能覆盖账本](proposal.md#25-现有功能覆盖账本迁移前必须闭合)。以下关联确保完整覆盖，不把 spec 编号误当执行顺序。

| 来源 spec | 实现/验收票 |
|---|---|
| [PR 37 维护体验回归约束规格](specs/00-pr37-maintainer-research.md) | [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)、[36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)、[37 1.2.0 clean cutover 与发布交接](#37-120-clean-cutover-与发布交接) |
| [维护者模块设计与运行时所有权规格](specs/01-maintainer-module-design.md) | [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[02 P0 独立性能基线](#02-p0-独立性能基线)、[04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)、[11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)、[32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)、[35 P4 性能复测与政策对照](#35-p4-性能复测与政策对照)、[36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)、[37 1.2.0 clean cutover 与发布交接](#37-120-clean-cutover-与发布交接) |
| [版本与加载器支持矩阵规格](specs/02-support-matrix.md) | [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[02 P0 独立性能基线](#02-p0-独立性能基线)、[04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)、[32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)、[35 P4 性能复测与政策对照](#35-p4-性能复测与政策对照)、[36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)、[37 1.2.0 clean cutover 与发布交接](#37-120-clean-cutover-与发布交接) |
| [平台构建与 Stonecutter 策略规格](specs/03-platform-build-strategy.md) | [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)、[32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)、[36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)、[37 1.2.0 clean cutover 与发布交接](#37-120-clean-cutover-与发布交接) |
| [公开契约与插件模型规格](specs/04-public-contract-and-plugin-model.md) | [08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)、[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路) |
| [运行时生命周期与数据保护规格](specs/05-runtime-lifecycle-and-data.md) | [03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)、[05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)、[06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)、[10 按类型 global、显式 shared 与候选顶层写集联合提交](#10-按类型-global显式-shared-与候选顶层写集联合提交)、[17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[19 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](#19-脚本包分发-trust-决策远端包激活与-fabric-world-现状)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[38 离线 validator / migration report（可选）](#38-离线-validator--migration-report可选) |
| [语言模块管线规格](specs/06-language-module-pipeline.md) | [11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)、[12 TS/JSX/TSX 编译、source map 与执行行为路径](#12-tsjsxtsx-编译source-map-与执行行为路径)、[13 Python 转译、模块行为与诊断路径](#13-python-转译模块行为与诊断路径)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路) |
| [NekoJS 验证与迁移规格](specs/07-validation-and-migration.md) | [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[02 P0 独立性能基线](#02-p0-独立性能基线)、[04 P4 前性能发布政策确认](#04-p4-前性能发布政策确认)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)、[11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)、[12 TS/JSX/TSX 编译、source map 与执行行为路径](#12-tsjsxtsx-编译source-map-与执行行为路径)、[13 Python 转译、模块行为与诊断路径](#13-python-转译模块行为与诊断路径)、[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口)、[30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路)、[31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)、[32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口)、[35 P4 性能复测与政策对照](#35-p4-性能复测与政策对照)、[36 P4 维护者四类真实试做](#36-p4-维护者四类真实试做)、[37 1.2.0 clean cutover 与发布交接](#37-120-clean-cutover-与发布交接)、[38 离线 validator / migration report（可选）](#38-离线-validator--migration-report可选) |
| [NekoJS 搬运功能事件面规格](specs/08-ported-features-event-surface.md) | [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)、[15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口)、[16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径)、[25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离)、[29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口) |
| [reload 候选状态与线程契约规格](specs/09-reload-candidate-state-and-thread-contract.md) | [05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)、[06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)、[08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活)、[10 按类型 global、显式 shared 与候选顶层写集联合提交](#10-按类型-global显式-shared-与候选顶层写集联合提交)、[16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)、[17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁)、[22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询)、[26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理)、[28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离) |
| [global 共享状态与候选写入规格](specs/10-shared-global-candidate-writes.md) | [10 按类型 global、显式 shared 与候选顶层写集联合提交](#10-按类型-global显式-shared-与候选顶层写集联合提交)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁) |

| 功能覆盖账本行 | 负责闭合的票 |
|---|---|
| 启动、runtime 生命周期与 reload | [05 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](#05-单-owner-预整理闭合两个-loader-的运行时生命周期入口)、[06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复) |
| 插件发现、Point、Handle 与 builtin 清单 | [08 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](#08-真实外部-pluginaddon-从-discovery-到贡献消费与-reload-存活) |
| 编译、模块解析与语言路径 | [11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径)、[12 TS/JSX/TSX 编译、source map 与执行行为路径](#12-tsjsxtsx-编译source-map-与执行行为路径)、[13 Python 转译、模块行为与诊断路径](#13-python-转译模块行为与诊断路径) |
| 脚本执行、bindings 与高级 Java access | [06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)、[07 同类型串行、close 优先与 watchdog 隔离恢复](#07-同类型串行close-优先与-watchdog-隔离恢复)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链) |
| managed API、legacy surface、事件公开名与声明 | [09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链)、[14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础) |
| 事件总线、事件绑定与事件包装器 | [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)、[24 Capability/goal/实体行为既有事件面覆盖路径](#24-capabilitygoal实体行为既有事件面覆盖路径) |
| 配方、数据生成（不含 Assets）、loot、tags 与 recipe viewer | [23 Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](#23-recipe数据生成loottagsrecipe-viewer-既有事件域路径) |
| Villager Trades | [22 Villager Trades 声明事件与稳定查询](#22-villager-trades-声明事件与稳定查询) |
| Dynamic Registry（服务器运行期） | [16 Dynamic Registry inert 定义计划与 typed Builder](#16-dynamic-registry-inert-定义计划与-typed-builder)、[21 Dynamic Registry 多人 prepare/ack/commit 门禁](#21-dynamic-registry-多人-prepareackcommit-门禁) |
| 启动期 registry、Builder、声明注册与类型转换 | [15 启动期注册、typed Builder 与连带注册垂直收口](#15-启动期注册typed-builder-与连带注册垂直收口) |
| 客户端脚本、GUI、render、HUD 与 keybind | [26 CLIENT 输入与 HUD callback 生命周期](#26-client-输入与-hud-callback-生命周期)、[27 CLIENT GUI 与 render Adapter 资源呈现清理](#27-client-gui-与-render-adapter-资源呈现清理) |
| PostEffects | [28 PostEffects 声明事件与运行 binding 分离](#28-posteffects-声明事件与运行-binding-分离) |
| 网络、脚本同步、ClientData、PData 与 pack sync | [17 网络注册一次、wire 不变与脚本自定义通道 owner 调度](#17-网络注册一次wire-不变与脚本自定义通道-owner-调度)、[18 PData 与 ClientData 数据同步路径保护和 generation 边界](#18-pdata-与-clientdata-数据同步路径保护和-generation-边界)、[19 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](#19-脚本包分发-trust-决策远端包激活与-fabric-world-现状) |
| 命令、管理入口与权限 | [20 管理命令权限、生命周期入口与阶段诊断结果](#20-管理命令权限生命周期入口与阶段诊断结果) |
| sandbox、config、pack trust、cache 与持久化数据 | [06 候选环境、阶段结果与 owner-thread commit 点](#06-候选环境阶段结果与-owner-thread-commit-点)、[03 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](#03-持久化与用户编辑数据保护基线默认不改可回滚才迁移)、[19 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](#19-脚本包分发-trust-决策远端包激活与-fabric-world-现状)、[11 JS/CJS/ESM 模块身份、缓存与生命周期基础路径](#11-jscjsesm-模块身份缓存与生命周期基础路径) |
| 错误、诊断、telemetry、workspace 与用户可见报告 | [30 错误诊断、telemetry、workspace 与用户报告链路](#30-错误诊断telemetryworkspace-与用户报告链路) |
| 数据映射查询 | [25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径) |
| 自定义事件声明 | [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础) |
| 原生事件桥与 Probe 事件 | [14 事件总线与 Script/Native/Probe 事件声明基础](#14-事件总线与-scriptnativeprobe-事件声明基础)、[09 Managed Surface 单一规范源与声明/Probe 派生链](#09-managed-surface-单一规范源与声明probe-派生链) |
| 实体选择器工具 | [25 DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](#25-datamap-与-entityselectors-查询-bindingadapterdeclaration-路径) |
| 客户端资源生成 | [29 Assets 单事件生成与资源回读收口](#29-assets-单事件生成与资源回读收口) |
| 跨 reload 的 global 共享状态如何参与候选事务？ | [10 按类型 global、显式 shared 与候选顶层写集联合提交](#10-按类型-global显式-shared-与候选顶层写集联合提交) |
| 构建、版本兼容、资源、mixin 与五节点产物 | [01 P0 五节点构建与契约基线](#01-p0-五节点构建与契约基线)、[31 Fabric raw loader 源根显式所有权迁移](#31-fabric-raw-loader-源根显式所有权迁移)、[32 Fabric 五层源唯一性与 bridge 删除条件](#32-fabric-五层源唯一性与-bridge-删除条件)、[33 CI 用途子集与 Fabric processor 延期替代 gate](#33-ci-用途子集与-fabric-processor-延期替代-gate)、[34 P4 五节点整体验证与能力矩阵收口](#34-p4-五节点整体验证与能力矩阵收口) |


## 执行前沿与协调

- 获准实施后的初始前沿只有五节点构建基线与独立性能基线；二者可以分别准备，但真实采样若共享游戏实例/磁盘/CPU，须协调运行窗口，避免污染测量。
- 后续只需等待各票真实 blockers 完成，而不是按编号串行；构建/Fabric、runtime、语言、客户端和查询路径可在输入就绪后并行。
- P4 三类验收消费全部必选域的最终输出与已确认性能政策；展开后的具体阻塞边写在票内，不留下“所有功能完成”这种无法查询的假票。
- 性能政策和维护者四类试做需要人工参与；属于已定实施门禁，不重新打开架构规划问题，也不让代理替人确认。
- 可选离线报告没有必选消费者；不选用它不会卡住任何必选票或 release。
- 同一 root 的生命周期装配、事件注册、Builder 与 fingerprint 公共代码、协议注册、Fabric 源迁移和 CI fixture 消费者可能写入相邻代码。实施时给并行代理划分互不重叠的写集，合并前重跑对应 fixture；仅共享文件不自动产生语义阻塞。
- 当前每张票的单上下文规模是拆分判断，不是已运行实现的工时承诺。若源码实测证明机械变更无法独立保持绿色，应在执行前把该票拆成有界 expand/migrate/contract 批次，并保留共同整合验证点；不能暗中扩成第二架构或长期双路径。

## 确认后的发布方式

本地实现票与决策票分开存放；编号按拓扑顺序从 01 开始，Blocked by 用标题链接指向真实先决票。票内包含 What to build、Blocked by、Status 与未勾选验收项，并带上源 spec 与验证入口。ready-for-agent 表示规格足够说明，不表示本轮已授权执行重构；需要维护者亲自确认的门禁单独标明，不由代理代答。

在维护者确认本拆分之前，不创建 ready-for-agent 的正式票文件；修改粒度或依赖只改本草案，不改变已确认架构语义。

## 复核记录

- 四组草案均由 glm-5.3 / max 子代理生成，主代理整合、校正既定边界并检查依赖。
- 38 张草案：37 必选、1 可选；11/11 份 spec 与 23/23 行功能覆盖账本均有负责票。
- 依赖图无环、无未知 blocker、无自依赖；无必选票依赖可选报告。127 条传递冗余边已移除，保留的依赖均有输入理由；编号满足 blockers 在前。
- 文档只记录计划验收，没有执行实现、构建、运行测试、性能、迁移或发布。
- 原决策票、spec、路线图和交接文件保持原状；本轮只新增此拆分草案。维护者确认后才按本地一票一文件方式发布实现票。
- 本次扫描 36 份规划 Markdown、928 条本地链接（含 489 个标题锚点），目标/锚点缺失为 0，30 张表格列数检查通过；127 条已移除边的最终可达性逐条复验，36 份原有文档的 SHA-256 均未改变。
