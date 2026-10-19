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
- 删除 Fabric 自动物品资源的 NEKO-4037 拒绝分支。复用注册成功才发布的 receipt、`RegisteredItemAssets`、节点资源布局与原有所有权恢复规则。首次及后续资源 `openFull` 在枚举前准备模型/物品定义。

## 行为与边界

目录 data 改动由原版 `/reload` 更新，assets 改动由 F3+T 更新。普通脚本重载不强制额外跑完整数据包重载，不引入新的 OS 性能策略。脚本 generation 与原版资源解析分别报告结果，不宣称两者形成跨系统原子事务。签名、信任、wire 格式、公开脚本 API、用户资源与生成文件所有权规则保持。

历史 `FabricWorldPackStatusTest` 的“Fabric 永不激活 WORLD”断言与新能力冲突，改为生命周期与预览来源边界检查；另补真实资源读取/枚举覆盖、卸载后回退、客户端/服务端隔离、远端/MOD/禁用排除及清单环境/依赖约束的行为测试。Fabric 物品测试改为接受配置但等待原生 supplier 成功，不再期待旧拒绝码。NEKO-4037 保留为已停用码，新增 NEKO-6011 报告异步原版资源失败。

## 验证状态

已完成五节点 `compileTestJava` + `guardLint`（exit 0，4m 8s）；补资源栈展开后，两个 Fabric 节点再次 `compileTestJava` + `guardLint`（exit 0，22s），最后收口合法标签/语言 fixture 的相同检查为 exit 0、13s。原始日志有编译器警告，guardLint 零警告。[编译记录与源文件摘要](../architecture-refactor/evidence/2026-10-19-fabric-directory-implementation/README.md)完整保留；没有执行测试或启动游戏。共享及 1.21.1 配方隔离提交为 `3e692eff`。

集中验收仍未运行：两个 Fabric 节点 build/平台门禁、五节点配方 JSON 隔离与注册 receipt 回归、CI 制品检查、初次客户端模型、GLOBAL/WORLD 资源与配方、原版标签/语言资源栈合并、A→B→A、远端授权/拒绝/断线资源撤回、原版 `/reload` 与 F3+T。当前实现尚未按原性能标准复验，最近冻结结果未整体通过，不用本功能编译代替性能复验。
