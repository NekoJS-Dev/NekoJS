# Issue #36 实施与验证记录

实施分支：`stonecutter`。起点：`c6455e0477b2f1336a72fbc717d0d5371ae4709f`。
需求：[Issue #36](https://github.com/NekoJS-Dev/NekoJS/issues/36)。范围依据本目录已批准的 [PLAN.md](PLAN.md)。实现由主代理完成；源码调查笔记来自此前的 Luna xhigh 调查。

## 实施内容与契约

- ItemBuilder 增加 `component(id, value)`、`components`、`jukeboxPlayable`、`parentModel`、`texture(id)`、`texture(slot, id)` 和 `textures`；26.x 另有 `trimMaterial`。沿用 `RegistryEvents.register` 和 Bean 属性。批量配置替换显式配置，单条方法覆盖同一 ID；普通属性先应用，显式组件后应用。顶层 null 不表示移除默认组件。
- JSON 在 common 层规范化、深复制并冻结，进入现有定义指纹。拒绝函数、宿主对象、循环、非有限数值及非法路径。集合读取不可修改；注册回调结束后拒绝修改物品定义。Food 配置也复制快照，保留的 food 回调参数不能改变已收集定义。
- Minecraft 类型查找与 codec 解码留在版本树。26.x 使用原生延迟组件初始化的 RegistryOps；歌曲和现代 trim 引用也通过此路径解码，保留物品、组件、节点及 codec 原因。1.21.1 使用 JsonOps，可解析的内建引用仍可用，需要动态注册表 provider 的 ID 引用明确失败。26.x 原生最终化会设置 `item_name` / `item_model`，本次明确拒绝覆盖这两个身份组件。
- 嵌套 BlockItem 使用同一组件/资源配置。修复 `block.item` Bean 属性遮蔽 `block.item(callback)` 的共享原因：非空子 builder 同时支持属性访问和回调调用。`noItem()` 仍抑制 BlockItem。冻结后不能再改变是否生成 BlockItem。
- 成功的原生注册才发布不可变资源声明。supplier 未执行、supplier/平台注册失败、重复 ID、回调失败和 `noItem()` 不发布相应物品资源。声明归属于现有启动注册 Runtime，普通资源重载不重建 Plugin Runtime、不重新注册 Item。
- NeoForge 自动生成配置过的物品模型；1.21.1 输出 `models/item/*.json`，26.x 同时输出 `items/*.json` 引用。资源准备进入现有 PackResources `openFull`，早于首次资源枚举和模型解析。Fabric 资源配置在 supplier 执行前以 `NEKO-4037` 拒绝；其组件、歌曲及现代 trim 配置仍可用。本次没有扩展 Fabric Assets 挂载能力。
- 无 parent/纹理配置时不生成资源。默认 parent 是 `minecraft:item/generated`，默认 layer0 是物品自身的 `namespace:item/path`；texture 只引用已有图片。已有 `Assets.itemModel` 的含义保持不变。
- 自动文件所有权持久化到 `nekojs/.nekojs-item-assets/ownership.json`，不放进 assets 命名空间。已有手写/显式资源及被修改文件优先；只有仍匹配记录哈希的自动文件可更新、删除。纹理文件不受此写入器管理。整组输出先暂存；发布失败尝试恢复旧文件和索引，恢复失败保留为 suppressed cause。此保证限于进程内异常恢复，不承诺崩溃事务或任意外部并发写入。
- 更新英文/中文 wiki、错误码、手写声明入口与反射声明生成器。没有新增依赖、Point、插件 Hook、运行时 owner、网络格式或数据包 DSL；没有复制 KubeJS 源码。

## 自动检查

最终生产代码的完整命令：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
node_modules/.bin/tsc.cmd --strict --noEmit --skipLibCheck docs/architecture-refactor/evidence/2026-10-12-issue36-plan/fixtures/items.ts
node_modules/.bin/tsc.cmd --strict --noEmit --skipLibCheck docs/architecture-refactor/evidence/2026-10-12-issue36-plan/fixtures/items-legacy.ts
python tools/nekojs-ci-gates.py all --json
./gradlew.bat :26.1.2:runGameTestServer --console=plain
node scripts/wiki-docs.mjs check --strict --structure-only
git diff --check
```

以上检查通过。GameTest 的成功依据是日志中的 `All 1 required tests passed :)`，不是进程启动。它是既有 GameTest 冒烟；新功能的原生默认组件证据来自下面的真实 loader fixture。

| test 任务 | 总数 | 失败/错误 | skipped |
| --- | ---: | ---: | ---: |
| common | 2244 | 0 | 4 |
| common-api-processor | 13 | 0 | 0 |
| NeoForge 1.21.1 | 431 | 0 | 31 |
| NeoForge 26.1.2 | 666 | 0 | 81 |
| NeoForge 26.2.0 | 666 | 0 | 81 |
| Fabric 26.1.2 | 389 | 0 | 37 |
| Fabric 26.2.0 | 389 | 0 | 37 |

总计 4798 个 test 任务用例，271 个 skipped 没有计作行为验收。新增 common 配置/资源回归 8 个、每节点 Graal Builder 回归 5 个均执行通过。资源回归覆盖哈希所有权跨实例读取、显式覆盖、删除声明、成对文件中途发布失败恢复、损坏索引拒绝与暂存清理；Graal 回归覆盖 Bean/方法指纹、深复制、函数拒绝、批量原子性、嵌套回调/属性和 Fabric 拒绝路径。

两个 Builder golden 的变更是刻意的：新增 ItemBuilder API、纹理 map 值类型、26.x trim 成员及可调用的 BlockBuilder.item 属性；旧节点不生成 trim 成员。修改规范源/生成器后，由 `RegistryBuilderSurfaceGoldenTest` 输出并审阅生成结果，再更新 golden，最终普通测试通过。严格 TS fixture 同时验证正确调用与预期类型错误，不能用未触发的 `@ts-expect-error` 混过检查。

`npm.cmd run check:wiki` **未通过**：16 项内容戳记/翻译审核问题。将修改前 HEAD 的 wiki、manifest 与检查脚本放进独立 build 目录复查，也是相同 16 项；结构、语言配对和链接/锚点检查为 0 项问题。没有伪造翻译审核戳记或维护者结论。完整 CI gate 输出保存在 [ci-gates-final.json](ci-gates-final.json)。

## 原生 loader 与客户端

所有游戏运行在本仓库 `build/issue36-*` 测试目录；没有使用用户的正式存档。使用 Gradle 实际节点依赖：NeoForge 21.1.227 / MC 1.21.1，NeoForge 26.1.2.71 / MC 26.1.2，NeoForge 26.2.0.57 / MC 26.2。

[fixtures](fixtures/) 保存实际脚本及 Gradle init 文件。专用服使用 startup.js + server.js；客户端使用 startup.js + client-server.js，26.2 使用 client-262.js，其他节点使用 client.js。先运行专用服生成测试世界，再把 world 复制为客户端 `saves/issue36`。测试目录中的 options 设置 `onboardAccessibility:false`、`pauseOnLostFocus:false`。运行示例：

```powershell
./gradlew.bat :26.1.2:runServer -I docs/architecture-refactor/evidence/2026-10-12-issue36-plan/fixtures/runtime.init.gradle '-Pissue36Node=26.1.2' '-Pissue36Game=build/issue36-server-261' --console=plain
./gradlew.bat :26.1.2:runClient -I docs/architecture-refactor/evidence/2026-10-12-issue36-plan/fixtures/runtime.init.gradle '-Pissue36Node=26.1.2' '-Pissue36Game=build/issue36-client-261-final' '-Pissue36Run=client' --console=plain
```

专用服 fixture 通过后主动停止；客户端 fixture 拍摄首次背包、手持/掉落和重载后背包，并请求一次原生资源重载，再停止。26.2 使用原生 Screenshot.grab(Minecraft, boolean)，并自动继续 GraalMC 弃用 logoFile 属性的非致命提示页。组件断言检查真实 ItemStack：max_stack_size 覆盖普通 maxStackSize 为 16、EPIC 稀有度、custom_data、唱片组件、现代 trim 组件、嵌套 BlockItem RARE、noItem 缺失及 Food 快照 nutrition=4。

| 原生检查 | 结果 |
| --- | --- |
| 三个 NeoForge 节点真实 ItemStack 断言 | 通过；各有 `ISSUE36_NATIVE_PASS` |
| 三个节点首次模型加载 | 通过；启动前无本 fixture 的生成资源/所有权索引，见 [client-clean-start.json](client-clean-start.json) |
| 三个节点背包、手持、掉落物与资源重载后模型 | 主代理逐张检查实际 PNG；无缺失物品纹理 |
| 26.1.2 非法 rarity codec | 预期失败；加载中止，`NEKO-4036` + item/component + `Unknown element name:not_a_rarity` |
| 1.21.1 非法 rarity codec | 预期拒绝；该物品未注册，`ISSUE36_INVALID_ITEM_REJECTED`，codec 原因在日志中 |
| 26.1.2 缺失歌曲引用 | 预期失败；`NEKO-4036` + jukebox_playable + `Failed to get element issue36:missing_song` |
| 26.2 缺失 trim 引用 | 预期失败；`NEKO-4036` + provides_trim_material + 缺失 ResourceKey |
| 1.21.1 原生 codec 能力边界 | 内建 potion_contents ID 解析通过；动态 trim 的字符串 holder 引用、未知组件类型拒绝，且物品未注册 |

[screenshots](screenshots/) 保留每节点三张原生证据。26.1.2 的掉落物截图另做一次相机朝向校准；该节点首次/重载图片仍来自无既有资源的启动。日志的关键标记与结果收录在 [verification.txt](verification.txt)，完整原始日志留在本地 `build/issue36-evidence/raw/`。

## 剩余限制与验收状态

- Fabric 两节点构建、契约、Graal 与平台门禁通过；没有执行 Fabric 的真实客户端/专用服运行。Fabric 自动物品资源仍是明确不支持的范围。
- 1.21.1 没有 trimMaterial helper；raw codec 对依赖动态 RegistryOps 的 ID 引用会失败，不等于所有带内建引用的组件都失败。原生堆叠/耐久组合约束仍由 Minecraft 最终校验。
- 1.21.1 和 26.1.2 使用带 JS consumer 的异步 Screenshot fixture 时出现过 Graal 多线程访问诊断；三阶段截图与原生断言仍完成，进程成功退出。这是本轮运行观察到的限制，未将其描述为无告警运行，也未扩大本票据去修改回调线程调度。
- 尚未运行磁盘崩溃恢复、外部同时写入、真实唱片声音播放或完整锻造 UI 交互验收；这里验证的是原生组件与资源引用初始化。
- 维护者人工验收结论尚未提供。以上自动/视觉检查是代理的实际观察，不代表维护者已接受。未修改 GitHub Issue/PR 状态，未推送远端。
