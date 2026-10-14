# 脚本包实施契约与进度

日期：2026-10-14。实施起点：`stonecutter` / `fe89c770d2d47efe1d3f72421328565eaf7dd889`。

维护者已要求开始执行交接路线，新增模组 JAR 生成器；实现由主代理完成，仅只读源码探索可使用 Luna xhigh。

## 范围与兼容边界

按 [交接路线](2026-10-14-script-pack-work-handoff.md) 顺序实施 A–E；JAR 生成器纳入 E。现有平铺脚本和无描述版本的目录包继续采用原扫描、Context/global 和文件排序语义。新的入口、依赖与实例机制必须选择加入，不静默改变旧包。现有签名、内容哈希、远端授权和同步 wire 保留。

模组 JAR 是承载脚本包与资源的分发形式，依赖 NekoJS，不是将 JavaScript 编译成独立 Java 模组。目标为仓库当前支持的 NeoForge 1.21.1、26.1.2、26.2.0 和 Fabric 26.1.2、26.2.0。生成器、loader 元数据、已加载模组来源发现与实际运行须共同验证；仅 ZIP 内容检查不等于游戏验收。Forge 未在当前节点矩阵内，不能冒称支持。

## A：首批确定契约（阶段记录）

本节保留首批实现时的范围；当前完整行为与验证见本文后续和 [最终验证记录](2026-10-14-script-pack-final-verification.md)。

- 管理选择器采用 `GLOBAL:<id>`、`WORLD:<id>`、`SERVER_CACHE:<id>`，scope 不区分大小写。旧裸 ID 在唯一匹配时继续工作；跨来源同名返回歧义，禁止默认修改第一个包。
- 查询展示已发现来源快照与配置启用意图，不能把扫描标记写成运行实例已激活。SERVER_CACHE 可查询但不可用本地状态命令改写；执行仍由远端授权控制。
- 本地启停操作由 Registry 负责选择、持久化和更新发现快照，统一返回结果；命令只负责展示结果。保存失败返回失败，不发布请求的新启用值；操作前的正常刷新仍可反映磁盘上已有的外部变化。
- 保留状态文件名称和 JSON 字段。采用同目录暂存、原子替换；不支持原子替换时返回失败并保留旧状态，不能先截断旧文件。无效或不可读取的既有状态不被操作自动覆盖。
- 保存意图与脚本执行分开：普通 SERVER/CLIENT reload 在发现前刷新 GLOBAL 与当前已挂载 WORLD；STARTUP 仍需要重启，现有不可逆重载语义不改变。
- WORLD 根目录只由已有世界生命周期挂载和卸载；刷新不能复活退出世界。Fabric 尚未装配 WORLD 来源，这轮不以列表文字声称已扫描世界。

调用链：三份 `NekoJSCommands` → `ScriptPackRegistry` → `ScriptPackState`；`ScriptManager.discoverWithPacks` → 刷新本地来源 → `ScriptLocator`。新增两类命令查询：`/nekojs packs status <type>` 读取 ScriptManager 当前代的冻结入口结果；`/nekojs packs plan <type>` 使用现有文件预读和 priority/after 排序，不执行或编译脚本。查询经 NekoRuntimeRoot 入口，不向命令泄露 manager。冻结结果记录来源选择器、实际源文件、发现时的包版本、入口结果和故障隔离标记；初次加载/提交/文件重跑更新结果，候选失败保留健康旧结果，世界清理/重置/关闭移除旧结果。

不新增 Script API / Plugin API，不改签名或 wire。`ScriptPackState.save` 是引擎内部方法，返回类型从 void 改成 boolean，仓库消费者已改为统一操作结果。状态文件的名称、enabled 字段和哈希排除规则保留；损坏状态需要作者修复后才能保存，不能用默认值覆盖损坏数据。

首批 status 是入口执行事实，不保证任意宿主资源健康；首批 plan 仅含文件计划。后续 B–D 已加入依赖阻止原因、实际激活查询和 root-owned 包协调入口；查询仍不执行候选编译或脚本。

## 后续阶段与完成门槛

| 阶段 | 工作 | 完成证据 |
|---|---|---|
| A | 配置/发现/实际运行分离，来源选择，统一操作结果与计划 | 同名、保存失败、WORLD 刷新、远端只读；实际 generation 查询 |
| B | 版本化严格描述、入口/库、包版本依赖与 schema | 坏字段、缺失/循环/不兼容依赖、确定排序；旧包兼容 |
| C | Context 最小原型、作用域激活实例、资源 owner、动态能力 | economy 消费者停启/恢复、双世界隔离、旧句柄和迟到资源 |
| D | 受影响闭包与联合候选提交 | 无关包不重跑、失败保留健康旧集合、close 抢占 |
| E | 创建/校验/可重复归档/安装/同步接入/JAR 生成与发现 | 可运行模板、内容完整、失败恢复、签名与各 loader 运行证据 |

源码与契约收敛后按阶段集中验证：common 改动运行 `:common:check`，节点改动运行受影响节点测试/编译及仓库门禁；loader、网络或资源行为补实机证据。PASS、FAIL、NOT_RUN 分别记录，维护者验收不代签。

## 工作区保护与记录

开始时两份已修改的声明文件为 `common/src/test/probe-ts/generated/index.d.ts`、`common/src/test/resources/nekojs/dynamic/dynamic-registry-events.expected.d.ts`。研究文档、native 图片、`.memsearch/` 等既有未跟踪内容保留。不批量暂存，不清理用户目录，不停止用户进程。

当前：A 的来源选择、保存结果、配置/入口执行结果分离、文件执行计划和 WORLD 重载刷新已实现；初次五节点构建通过。新增运行查询后，门禁发现命令直接读取 manager 的问题，已改为 root 的类型可用性查询，门禁断言未放宽。最终验证结果见 [验证记录](2026-10-14-script-pack-phase-a-verification.md)。B–E 已继续实现，以下为当前进展；整体完成判定仍以最终验证和真实生命周期证据为准。


## B–E 最终实现

B 已加入 formatVersion=1 的严格描述、显式 entries、import-only 库、环境/版本依赖和确定排序。SemVer 数字部分、预发布及比较器交集受支持；不引入 npm/Maven 全套求解、自动下载或锁文件。schema 的 canonical 来源为 PackDescriptor.schema()，运行 :common:generatePackSchema 生成 docs/schema/script-pack-v1.schema.json。旧无版本清单保持宽松扫描和共享 Context。

C/D 的最小真实 Graal 验证已通过：新清单包按 ScriptType 与物理来源实例创建独立 Context，root 拥有协调器；global 为包来源实例私有，shared 仍由 root 拥有。Pack.provide/require 使用清单声明的能力、同类型受信来源解析和有界同步 JSON 数据桥；禁止共享任意 guest 对象。句柄绑定提供者和消费者的实际激活，替换或关闭后不能跟随相同 selector 访问新代。物理 import 限定在自己的包内容根中，跨包调用使用声明能力；包内模块依赖由现有 module session 记账。

联合候选先准备整个受影响集合、监听器和受管写集，再单次发布。提供者变化包含版本依赖/能力消费者闭包，未涉及的 chat 保持原激活。候选失败和共享键跨候选冲突保留健康旧集合。现有领域收集器拥有整类型贡献表，暂时按显式理由扩大为完整贡献集合，并联合收集一次；不能将逐包清空表当作局部提交。任意宿主副作用仍不在受管回滚承诺内。STARTUP 的不可逆注册保持重启边界。

E 已有 PackTools 核心入口、root 作者操作、packTool 仓库 CLI 和游戏内创建/校验/归档/JAR 命令。模板携带 Pack API 声明。归档保留原 manifest 与签名字节，内容范围沿用 PackHasher（四种脚本目录、assets/data）；本机意图不分发。固定时间戳和 STORED ZIP 构建可重复。安装复用 ServerPackCache 的暂存/替换/恢复，在明确 replace 时升级并保留旧启用意图，旧内容删除随整目录替换生效。绝对/穿越/反斜线/ADS/设备名/node_modules、重复路径、单文件/总量/数量限制均拒绝。

JAR 生成器依赖 NekoJS，覆盖当前五节点，单 JAR 的 mod id 等于 pack id。NeoForge 1.21.1 使用 lowcodefml；26.x 根据已缓存 FML 11.0.16 源码采用可无代码入口的默认 Java loader，省略旧 lowcodefml 标记。Fabric 采用可无 entrypoint 的元数据。已加载模组提供内容来源，进入 MOD:<id> 只读来源，物化到内容哈希目录；来源 adapter 不创建 Context。新 MOD 内容不加入旧 GLOBAL/WORLD 的同步 wire，JAR 按部署侧安装。资源还复制到 JAR 根 assets/data 供各 loader 原有资源机制使用；不能据 ZIP 检查冒称游戏资源验收。

参考主来源：[NeoForge 元数据](https://docs.neoforged.net/docs/gettingstarted/modfiles/)、[Fabric 元数据](https://docs.fabricmc.net/develop/loader/fabric-mod-json)。旧低代码 loader 与现代默认入口差异已通过本机 FML 4.0.42/11.0.16 sources.jar 复核。

后续已验证真实 Graal 的提供者资源耗尽、等待/恢复与禁用意图、世界 A/B、旧能力句柄、半初始化监听器/timer 清理、同线程关闭、跨线程关闭和世界撤销抢占。新增包测试使用独立 Engine，避免改变旧测试的共享 Engine 宿主配置；timer 断言等待调度器投递后验证实际回调次数。CLIENT 渲染表已去除单候选 Context 假设，在联合边界合并所有贡献；失败保留旧表，删除贡献及关闭按 Context owner 回收。

运行成本边界：每个活动的版本化包/类型实例拥有一个 Context、模块会话和 Node runtime；timer 调度器按该 runtime 归属，候选阶段同时保留旧与新。旧分区仍有独立的类型 Context。四包 economy/shop/quests/chat 原型验证了独立 Context、联合替换及无关 chat 激活保留；没有据此声称 CPU/内存性能优于旧机制。

远端 allowUnsigned 契约已收敛：客户端策略允许与服务器信任缺一不可；批准的无签名文件有单独的 REMOTE_UNSIGNED_AUTHORIZED 证据，不伪造 key、不使用 local-trusted 回退。三参数签名凭证仍要求非空 key。凭证集合先完整验证，再替换；同桶候选失败恢复凭证时不先销毁健康旧包激活。签名 key 复验/持久化失败现在拒绝整个分发操作，不静默报告成功。版本化远端清单与显式入口在物理替换前验证，ID 必须与 packs/worldpacks 的 sync identity 一致；旧清单语义保持。

作者文档已加入 wiki/script-packs_cn.md 与 script-packs_us.md，并通过现有 wiki 生成器更新目录、导航、当前修改主题的 digest；人类翻译审核仍为 pending。schema 经 canonical 生成任务输出。最终矩阵和 CI/Probe 类型结果集中记录于最终验证文档。曾误并行启动同目录测试导致报告文件冲突，原始失败保留在 matrix-3 日志，不能作为源码失败或成功证据。

实际 PowerShell 作者工具的 create/validate/archive/install/jar 均已运行。五个目标节点的内容 JAR 已被真实 loader 加载并执行 MOD 入口，原生 data 资源读取为 true；NeoForge 26.1.2 还完成必需 GameTest。NeoForge 26.2 客户端读取原生 assets、显示中文状态，在配置阶段激活授权的远端 CLIENT 包，断线后撤回远端包。真实 WORLD 来源挂载和回调已验证；世界 A/B/A 私有状态与旧句柄隔离由真实 Graal 回归验证，不冒称已手动切换两个客户端世界。详细日志、截图、源码批次与限制见最终验证记录。

关闭审计已收敛：单个监听器、绑定、Node runtime、Context 或领域清理失败不跳过其余释放；清理异常保留 cause/suppressed，联合操作有独立 cleanupFailures。Graal 已取消/耗尽 Context 在 close 时重复抛出的终止通知不覆盖原始脚本失败。root 的各类型监听器清理分别尝试。回调内关闭先阻止新包分发，等待最外层 guest 返回并恢复归属后释放；延迟清理失败记录 NEKO-1017。timer 注册、入队和取消使用同一短临界区，关闭后不再接收迟到回调，线程退出等待有 2 秒上限，超时/中断报告未完成清理。

世界、连接结束或提供者失效后，整表领域从仍存活的订阅重收集贡献，不重跑入口；CLIENT render 按实际 Context 撤回。重收集失败尝试空计划，失败与未完成撤回分别报告并要求显式恢复。MOD 的 data 已在 loader 原生 JAR 内，不再重复挂载物化目录，避免服务器初始化前资源 reload；目录 GLOBAL/WORLD 仍沿用已有挂载。

版本化 STARTUP 只在 loader 初次边界执行。旧 STARTUP 已完成后热添加版本化包也要求重启；首次部分失败同样消耗初始化边界。旧无版本 STARTUP 重载仍保留原非事务契约。

## 交付与剩余边界

实现、自动验证与本地实机证据已集中交付；提交和推送按维护者后续授权执行。工作区既有修改保留。

- Wiki 全量 strict 保留六个未改主题的历史 digest/translation 失败（12 条）；本轮主题结构/链接已通过。不能替历史内容盖章。
- 新双语主题的人类翻译审核与维护者最终验收仍未获得实际结论；用户点击加载器提示、连接本地服务器的回复只证明完成了这些操作。
- 未新增 VS Code/编辑器插件、Forge 目标、自动依赖下载、市场或 Fabric 目录资源桥。未作性能优劣比较。
- 公共新增脚本契约为 Pack.selector/activation/provide/require 与 opt-in 清单；相关声明随模板提供。Plugin Runtime 不重建，签名格式、同步 wire 与本机状态文件格式保持。
