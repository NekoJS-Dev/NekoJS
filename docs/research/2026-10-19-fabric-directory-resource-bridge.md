# Fabric 目录包资源桥与自动物品模型

2026-10-19。依据用户“先完成任务，不要着急测试”，本阶段先收口实现，再集中验证。主代理修改代码；Luna xhigh 只读核对两个节点的锁定 API 与来源/生命周期，没有承担实现或验证。

## 实现范围

- 使用原版 `RepositorySource`，在 Fabric 仓库构造结束时与原有来源并存，不依赖 Fabric 内部实现类。两个 Fabric API 分别为 0.155.2+26.1.2、0.159.0+26.2；本机两版原版签名及 Fabric resource loader 源码用于核对接缝。
- 一个必选 `nekojs:directory` 包合成多个目录，避开 Fabric 原生模组包 `nekojs` 的 id，直接查找与枚举采用相同覆盖规则：根目录 → GLOBAL → WORLD → SERVER_CACHE。同来源按 registry 的 id 顺序。原版 MOD 资源不再次挂载，禁用及环境/依赖阻止的目录不挂载。
- 目录组在原版 `MultiPackResourceManager` 构造入口展开为独立、限定资源侧的目录层，初次加载及后续重载采用同一路径。标签和语言等读取 resource stack 的消费者仍能看到全部层并按原版规则合并；不把同名文件提前扁平化成单个赢家。非目录包的顺序与对象保持。
- 服务端来源绑定该仓库原版 datapacks 目录的父存档，首次资源加载就可读取 GLOBAL/WORLD data。发现使用独立的目录 registry；不会把存档预览变成运行时 WORLD 激活。已有 MOD 仅作为依赖证据。
- 客户端只挂载 assets；服务端只挂载 data，SERVER_CACHE 永不参与服务端数据。客户端 WORLD 仅在本地内存连接期间参与。
- Fabric SERVER_STARTING 在脚本加载前激活 WORLD；停止事件派发后撤销包实例和旧监听器。客户端 JOIN 补载 WORLD CLIENT 脚本；延迟清理不会撤销下一世界的客户端 generation。
- 初次原版 WorldLoader 配方绑定发生在 SERVER_STARTING 之前，WORLD 配方监听器在 SERVER_STARTED、玩家加入前补应用。为保证重复应用不会污染原始 datapack JSON，共享及 1.21.1 的 `RecipeEventJS` 在构造时复制可变 JSON 树；不缓存跨 registry 的已解码 Recipe，不跳过 codec。新增独立的嵌套别名与下一轮恢复回归；性能影响留待原标准复验，不据此承诺提速。
- 远端脚本 reload 成功/断线及本地连接变化只请求刷新，由客户端后续 tick 发起原版资源重载；不在同步 latch 内等待模型加载，重叠请求合并，失败不堵死后续请求。
- 同步被拒绝时，两个接收入口在断线前都显式请求资源刷新。致命回滚可能已经清空 SERVER_CACHE 注册表，此时不能再凭包数量推断原生资源管理器中是否还留着旧资源；正常无包断线仍不额外触发完整资源重载。
- 删除 Fabric 自动物品资源的 NEKO-4037 拒绝分支。复用注册成功才发布的 receipt、`RegisteredItemAssets`、节点资源布局与原有所有权恢复规则。首次及后续资源 `openFull` 在枚举前准备模型/物品定义。
- Fabric 排空注册内容后保证 ITEM 阶段已完成，包括本次没有物品定义的情况。否则生成器会一直被注册完成门控挡住，移除全部自定义物品后的旧自有模型无法清理。空 ITEM 阶段仍排在其他生产者之后；已经完成的 ITEM 不重复执行，失败不标成完成。

## 行为与边界

目录 data 改动由原版 `/reload` 更新，assets 改动由 F3+T 更新。普通脚本重载不强制额外跑完整数据包重载，不引入新的 OS 性能策略。脚本 generation 与原版资源解析分别报告结果，不宣称两者形成跨系统原子事务。签名、信任、wire 格式、公开脚本 API、用户资源与生成文件所有权规则保持。

历史 `FabricWorldPackStatusTest` 的“Fabric 永不激活 WORLD”断言与新能力冲突，改为生命周期与预览来源边界检查；另补真实资源读取/枚举覆盖、卸载后回退、客户端/服务端隔离、远端/MOD/禁用排除及清单环境/依赖约束的行为测试。Fabric 物品测试改为接受配置但等待原生 supplier 成功，不再期待旧拒绝码。NEKO-4037 保留为已停用码，新增 NEKO-6011 报告异步原版资源失败。

## 实现复查

主代理收口前请两名 Luna xhigh 在限定文件范围内只读检查生命周期与原版仓库接缝。仓库构造后、首次发现前加入来源，资源管理器构造入口展开目录层，原版负责读取栈与关闭各层；registry 源码确认 GLOBAL、MOD、WORLD、SERVER_CACHE 顺序。生命周期复查发现上述致命回滚的资源撤回缺口，已在 Fabric 的 hash-list/bundle 拒绝分支补齐；沿用现有 source-trace 回归框架约束两个入口的请求传递与断线前顺序，尚未执行。

原版异步资源解析失败与脚本事务仍是独立边界，失败只报告 NEKO-6011 并交给原版资源恢复；不把已接受的脚本 generation 标成同步回滚。目录层复用已有 `NekoJSPathPackResources` 的直接路径读取，不声明原版 `FolderRepositorySource` 的 `DirectoryValidator` 校验语义；本阶段没有改变共享资源的符号链接策略。运行时 Mixin 应用、资源恢复和实际合并行为仍待集中验收。

快速入门中两处历史“Fabric 无 WORLD/data 挂载”说明已与当前实现同步；数据生成 Script Event 的平台覆盖仍独立记录。此次复查与修正没有执行测试、编译或启动游戏，不能扩展下节既有编译记录的覆盖范围。

后续实现复查又确认了空 ITEM 阶段的清理缺口。两个 Fabric 节点的 `FabricRegistryDrainBehaviorTest` 已改为检查空批次、仅非物品内容、再次调用不重复及空阶段失败不发布完成；未知注册表失败用例仍检查错误不丢失，阶段总数随最后的空 ITEM 增加一次。已有 `RegisteredItemAssetsTest` 检查空定义清理匹配所有权的文件、保留用户修改文件；此次未改生成器或所有权协议，也未运行上述测试、编译或游戏。

## 验证状态

已完成五节点 `compileTestJava` + `guardLint`（exit 0，4m 8s）；补资源栈展开后，两个 Fabric 节点再次 `compileTestJava` + `guardLint`（exit 0，22s），最后收口合法标签/语言 fixture 的相同检查为 exit 0、13s。原始日志有编译器警告，guardLint 零警告。[编译记录与源文件摘要](../architecture-refactor/evidence/2026-10-19-fabric-directory-implementation/README.md)完整保留；没有执行测试或启动游戏。共享及 1.21.1 配方隔离提交为 `3e692eff`。

随后在 `4dcfe604` 完成[集中检查](../architecture-refactor/evidence/2026-10-19-implementation-checks/README.md)：定向 131 项全部执行并通过；完整 common isolation/check、处理器测试、guardLint、五节点 build/平台门禁及四项 CI 一致性 gate 均通过，全量 5,020 次测试执行、0 failures/errors、271 skipped。五节点配方 JSON 隔离、Fabric 注册完成门控及 receipt 回归已执行；没有修改 golden 或放宽门禁。

实机集中验收仍未运行：Mixin 实际应用、初次客户端模型、GLOBAL/WORLD 资源与配方、原版标签/语言实际合并、A→B→A、远端授权/拒绝/断线资源撤回、原版 `/reload` 与 F3+T。当前 `4dcfe604` 已按[原性能标准复验](../architecture-refactor/evidence/2026-10-19-preflight-performance/README.md)，重载 285.8 / 286.6 ms 均 FAIL、启动 24250.4 ms PASS；不以本功能测试通过代替性能通过。

2026-10-20 补验：两个 Fabric 真实服务端均完成[目录资源原生验收](../architecture-refactor/evidence/2026-10-20-fabric-directory-server/README.md)，初次 WORLD data/脚本配方均存在于原生 RecipeManager；GLOBAL/WORLD 覆盖、block tag 合并、原版 reload、禁用回退与恢复均通过。每节点三次脚本 COMMIT / no errors、三次资源重载，正常停服。两个准备/观察器失败完整保留，实际生产类与封存 JAR 一致，没有生产源码修改。客户端模型、语言合并、F3+T、Fabric 双世界及远端资源撤回仍未验收，不能标为本功能全部完成。同期当前性能源码 `26844b7e` 的原门槛正式三组已 [PASS](../architecture-refactor/evidence/2026-10-20-binding-preflight-performance/README.md)。

后续完整游戏资源观测发现另一处平台接线遗漏：[Fabric SERVER 定时器未被原生 tick 冲刷](../architecture-refactor/evidence/2026-10-20-fabric-server-timers/README.md)。已在现有 end tick 接回 root 的 SERVER 冲刷入口，使旧目录脚本和独立包共用相同调度；不改 TEST 或 CLIENT 调度。修复后检查尚未运行。上述 data/配方证据仍是对应旧源码的通过记录，未覆盖定时器行为。

客户端补验入口已实现于 [tools/acceptance/fabric-directory-client](../../tools/acceptance/fabric-directory-client/README.md)：使用锁定 Fabric Client GameTest 的公开创建/重开存档、输入与截图接口，独立准备和执行；覆盖初次自动模型、资源栈/语言、真实 F3+T 输入和同客户端 A→B→A，并为截图审核及远端网络保留独立状态。源码与生产类有摘要绑定，失败不覆盖旧目录。按本轮实现优先要求，尚未编译或运行，不当作客户端通过证据；不依赖 MCP 接管，也没有改变其控制权限。

随后补齐该入口的可选 `--remote` 场景：独立服务端 JVM、真实 TCP、owned 地址的先拒绝后信任、签名 assets 接受，以及在已挂载资源时通过 Fabric 公开 reconfigure 重新握手，分别检查篡改/换钥拒绝、恢复和正常断线撤回。拒绝需匹配原生 narration 中的具体原因；私钥仅位于隔离服务端，未放宽 `allowUnsigned`，没有直接调用接收 handler。它仍是未执行的验收实现，不能作为远端客户端通过证据。
