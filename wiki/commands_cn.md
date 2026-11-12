<!-- wiki-page: commands; locale: cn -->

> **中文** · [English](commands_us)

<a id="wiki-section-1"></a>
# 命令

NekoJS 的服务端根命令 `/nekojs` 需要游戏管理员权限（op 2 及以上）。客户端 `/nekojs browse` 只打开本地目录，无需服务端权限。

<a id="wiki-section-2"></a>
## 命令一览

| 命令 | 作用 | 平台 |
|---|---|---|
| `/nekojs reload` | 重载**服务端**脚本（默认）。NeoForge 和 Fabric 26.x 会继续处理配方脚本。 | NeoForge / Fabric 26.x |
| `/nekojs reload <type>` | 重载指定类型脚本。`<type>` ∈ `server` / `client` / `test`；`startup` 当前会拒绝执行，启动脚本改动需要重启游戏。 | NeoForge / Fabric 26.x |
| `/nekojs reload <type> <file>` | 在活动 Context 中重跑旧式文件；依赖关系可能使受影响入口一同重跑。路径相对脚本类型根目录，文件名支持 TAB 补全。 | NeoForge / Fabric 26.x |
| `/nekojs test` | 运行 `nekojs/test_scripts/` 下所有脚本（TEST 脚本上下文） | NeoForge / Fabric 26.x |
| `/nekojs error` | 报告当前是否有脚本错误 | NeoForge / Fabric 26.x |
| `/nekojs view_all_errors` | 列出完整错误；NeoForge 可打开错误 UI，Fabric 输出聊天文本 | NeoForge / Fabric 26.x |
| `/nekojs editor`（历史命令） | 已移除；使用外部编辑器编辑脚本 | 当前平台不提供 |
| `/nekojs packs` | 列出已挂载来源中的脚本包、配置启用意图、版本和目录；远端缓存包由授权控制，配置状态不代表已经运行 | NeoForge / Fabric 26.x |
| `/nekojs packs enable\|disable <selector>` | 保存本地启用意图；可用 `GLOBAL:my_pack` / `WORLD:my_pack`，裸 ID 必须唯一。保存失败返回失败。重载对应类型应用，STARTUP 需要重启；远端缓存只读 | NeoForge / Fabric 26.x |
| `/nekojs packs status <type>` | 查看当前类型 generation 中已完成的入口执行结果、原版本与故障隔离状态；不重新扫描、不执行脚本 | NeoForge / Fabric 26.x |
| `/nekojs packs plan <type>` | 查看当前文件的执行顺序、跳过原因与排序诊断；不执行或编译脚本，不表示候选激活已经通过 | NeoForge / Fabric 26.x |
| `/nekojs trust <address>` | 多人脚本包分发：把服务器加入本客户端信任存储（首连未信任会被断开并提示本命令，信任后重连收包；在单人世界或客户端进程中执行） | NeoForge / Fabric 26.x |
| `/nekojs registry` | 动态注册健康快照；Fabric 当前快照为空 | NeoForge 26.x；Fabric 有命令但能力为空 |
| `/nekojs registry stale` | 只列出上次 reload 后脚本不再注册的动态条目 | NeoForge 26.x；Fabric 有命令但能力为空 |
| `/nekojs hand` | 主手物品、组件数据及物品/方块/流体标签，可点击复制 | NeoForge 1.21.1/26.x / Fabric 26.x |
| `/nekojs hotbar` / `inventory` | 导出快捷栏/主背包的带组件的物品字符串与完整 JSON 列表 | NeoForge 1.21.1/26.x / Fabric 26.x |
| `/nekojs list-tag <registry> [<tag>]` | 列出注册表标签或指定标签的成员，支持补全 | NeoForge 1.21.1/26.x / Fabric 26.x |
| `/nekojs browse` | 打开本机 `nekojs` 文件夹；仅客户端、无需管理员权限 | NeoForge 1.21.1/26.x / Fabric 26.x |
| `/nekojs probe` | 重新生成 TypeScript 声明（`.d.ts`） | NeoForge / Fabric 26.x |
| `/nekojs probe <语言> [<名字>]` | 指定语言（或精确到 backend 名）生成 | NeoForge / Fabric 26.x |
| `/nekojs probe all` / `list` / `reload` / `enable` / `disable` / `reset_config` | 跑全部 backend / 列出已注册 backend / 重读 probe.toml / 持久化开关 / 恢复编辑器配置出厂值 | NeoForge / Fabric 26.x |

> Cleanroom 1.12.2 不在当前仓库版本图中；该分支的命令差异属于 legacy 资料，本文不对其运行时行为做验证承诺。

<a id="wiki-section-3"></a>
## reload 详解

<a id="wiki-section-4"></a>
### `/nekojs reload`（默认 server）

此命令显式请求完整的 SERVER generation 重载。普通 SERVER/CLIENT/TEST 重载先在候选 Context 中完成准备与执行，再激活；候选失败会保留健康的活动代。受管理的监听器、定时器和暂存状态在提交点切换，不会重建 runtime root 或 Plugin Runtime。

这不代表能回滚脚本任意 Java 调用、世界修改、文件写入或共享对象的嵌套修改。SERVER 脚本重载成功后，命令另行应用配方并在支持的平台刷新包数据资源；后续资源刷新失败不会撤销脚本提交。版本化脚本包还遵循依赖与领域贡献的影响集规则，见[脚本包](script-packs_cn)。

CLIENT 命令已排队不等于重载已经完成，应等待后续结果。SERVER／CLIENT／TEST generation 已提交，也不等于所有脚本行为或断言都正确，仍须检查输出与错误。单独授权的[实时 Authoring 控制](ai-support_cn#wiki-live-controls)只在对应运行时可用时提供完整的 `server`／`client`／`test` 重载，不提供 STARTUP 或文件重载。其等待超时并非普通斜杠命令的新超时设置：执行开始后超时或被中断，可能报告 unknown effects，而操作仍继续并在稍后提交。**不要自动重试效果未知的操作**，应先检查运行时状态与诊断。

<a id="wiki-section-5"></a>
### `/nekojs reload <type>`

| type | 行为 |
|---|---|
| `server` | 同上 |
| `client` | 重载客户端脚本（仅 integrated client 上可用）。命令立即返回，实际 reload **转投客户端主线程**执行——客户端脚本环境（事件/timers）的归属线程就是它，这样 reload 与渲染期事件不会交叉线程 |
| `test` | 与 `/nekojs test` 一样重载并运行 TEST 脚本，然后冲刷定时器。流程完成不等于所有 `Test` 断言通过，仍须查看测试输出与脚本错误。 |
| `startup` | 当前命令会拒绝执行。启动脚本在游戏启动阶段运行，注册内容改动必须重启游戏。 |

TEST 命令分别报告执行结果与本次断言计数：捕获了失败断言也仍按断言失败报告；零断言明确显示为未观察到，而不是通过。实时 `context.testRunReport` 可查询最近结束的一次尝试（包括 legacy 与脚本包），不是当前进度或所有未来回调的保证；见[可查询运行结果](ai-support_cn#wiki-live-results)。

<a id="wiki-section-6"></a>
### `/nekojs reload <type> <file>`

在**现有活动 Context** 中重新执行旧式脚本文件。依赖跟踪还可能使模块失效并重新执行受影响的入口，因此不保证只执行一个文件，失败也没有完整 generation 的回滚保障。例：

```
/nekojs reload server recipes.js
/nekojs reload client tooltips.js
```

文件路径相对该脚本类型根目录（如 `recipes.js` 相对 `nekojs/server_scripts/`）。文件名支持 TAB 补全。版本化包内文件必须使用包或类型重载。依赖无法匹配，或活动代已因宿主取消或资源耗尽而失效时，应执行完整重载；单文件重跑无法恢复被隔离的代。

**这些触发方式彼此独立：**保存文件不会自动重载；当前没有已文档化的文件监视器或自动重载开关。模块依赖失效（内部有时称为 HMR）属于显式重载或加载路径，不是保存监听。原版 `/reload` 刷新服务端数据包，F3+T 刷新客户端资源，两者都不能代替脚本 generation 重载。包的选择与启用配置、包数据或资源刷新，也不同于脚本代的激活。

<a id="wiki-section-7"></a>
## 物品、标签和本地目录

以下四个服务端信息命令在 NeoForge 1.21.1、NeoForge 26.x、Fabric 26.x 共用实现，沿用 `/nekojs` 管理员权限。

<a id="wiki-section-8"></a>
### `/nekojs hand`

查看执行者主手物品。每行都能点击复制，悬停查看说明：

- 物品字符串，如 `"minecraft:iron_sword[damage=2]"` 或 `"3x minecraft:stone"`，可直接传给 `Item.of(...)`。组件值使用原版 SNBT，`!component` 保留组件删除；字符串通过 JSON 引号转义，可直接复制进 JS/TS。
- 完整物品 JSON，包含数量及组件补丁（包括删除组件），不截断为调试摘要。
- 物品标签及成员数、模组命名空间及物品数、已构建的创造模式物品栏及其成员数。
- 方块物品对应的方块 ID/标签；流体容器中的首种流体 ID/标签。流体读取使用各加载器原生容器接口，不消耗物品或流体。

创造模式栏复制的是原始栏 ID；不生成 NekoJS 尚未支持的 KubeJS `%tab` 过滤表达式。

空手显示本地化提示。需要玩家，控制台执行会报错。

完整 JSON 是 Minecraft `ItemStack.CODEC` 数据格式。重建物品优先复制组件字符串行：所有五节点的 `Item.of(string)` 和 ItemStack 字符串适配器均支持该写法，也支持对象形式 `{id: "minecraft:iron_sword[damage=2]", count: 1}`。完整 JSON 对象本身仍不是跨版本 `Item.of` 输入合同；1.21.1 的对象适配器不读取 `components` 字段。

组件按当前服务端或客户端连接的注册表解析；启动阶段尚未建立世界时只能使用内建注册表。未知组件、无效值、重复组件和末尾多余内容会报错，不会退回只有 ID 的物品。`Item.id(...)` 仍仅查询物品类型，不接收组件补丁。

<a id="wiki-section-9"></a>
### `/nekojs hotbar` / `/nekojs inventory`

`hotbar` 读取 0–8 槽，`inventory` 读取主背包 0–35 槽，均不含装备和副手。保留非空物品的原顺序及重复项，过滤空槽，空背包输出 `[]`。

每次输出两个可复制列表：带组件的物品字符串数组，以及保留完整组件的物品 JSON 数组。例如数组为 `["2x minecraft:stone","minecraft:apple"]`，可逐项传给 `Item.of`。数组不保留空槽位置。两条命令都需要玩家。

**`/nekojs list-tag <registry> [<tag>]`**

从当前服务器的实时注册表查询；控制台也可执行。注册表和标签参数均支持 TAB 补全，可使用 `minecraft:item`、`minecraft:block` 或其他已加载注册表。

```text
/nekojs list-tag minecraft:item
/nekojs list-tag minecraft:item minecraft:logs
```

只给注册表时列出全部标签和总数，点击标签运行正确的 `/nekojs list-tag ...` 命令查看内容。给出标签时列出可复制的成员 ID 和数量。未知注册表或不存在的标签明确报错；已存在的空标签正常显示 0 个成员。

**`/nekojs browse`**

客户端命令：通过系统文件管理器打开**客户端游戏目录下的 `nekojs` 文件夹**，目录不存在时先创建。多人游戏中仍打开本机目录，不读取或打开远程服务器目录。NeoForge 1.21.1/26.x 与 Fabric 26.x 客户端均提供，无参数、无需管理员权限；专用服务器不注册此命令。

<a id="wiki-section-10"></a>
## 配方 reload 的平台差异

| 平台 | `/nekojs reload server` 对配方的效果 |
|---|---|
| **Fabric 26.x** | 支持配方热重载；脚本面为当前 Fabric 配方子集。 |
| **NeoForge（26.x / 1.21.1）** | 支持热重载。`RecipeManagerMixin` 缓存数据包阶段的原始配方 JSON，reload 时重建工作集、重跑 `ServerEvents.recipes`，再替换 `RecipeManager`；配方逻辑即时生效，查看器可能需要客户端刷新。 |

> Cleanroom 1.12.2 的配方行为属于独立 legacy 分支，当前仓库不验证。

> NeoForge 的热重载基于 `ReloadableServerResourcesMixin`（资源 reload 收尾时触发）+ `RecipeManagerMixin.nekojs$applyScripts()`（可重入），由 `/nekojs reload server` 在脚本 reload 后主动调用。配方脚本里新增/修改/删除的配方都会重新解析进 `RecipeMap`。

<a id="wiki-section-11"></a>
## probe 详解

<a id="wiki-section-12"></a>
### 子命令

| 命令 | 行为 |
|---|---|
| `/nekojs probe` | 无参：只跑 TS 内置 backend（`typescript:builtin`），生成 `.neko_probe/typescript/` 下的 `.d.ts` |
| `/nekojs probe <语言>` | 跑该语言默认 backend：`probe.toml` 的 `languages.<lang>.backend` 配置优先，未设则取同语言 priority 最高者。如 `/nekojs probe python` 生成 `.neko_probe/python/nekojs/` 的 `.pyi` stub 包 |
| `/nekojs probe <语言> <名字>` | 精确指定 backend，如 `/nekojs probe typescript builtin` |
| `/nekojs probe all` | 跑所有已注册 backend（跨语言） |
| `/nekojs probe list` | 列出已注册 backend（`语言:名字 (来源)`） |
| `/nekojs probe reload` | 丢弃 probe.toml 配置缓存，下次从盘重读（改完配置不必重启游戏） |
| `/nekojs probe enable` / `disable` | 把 `enabled` 持久化进 probe.toml 并重载缓存 |
| `/nekojs probe reset_config` | 删除各 backend 管理的编辑器配置（jsconfig/pyrightconfig/snippets），保留用户自定义条目，随后重新生成默认 TS 声明；NeoForge 和 Fabric 26.x 都支持 |

`<语言>` 与 `<名字>` 支持 TAB 补全。

> 成功后命令会显示输出目录，例如 `Output: .neko_probe/typescript`。probe 正在运行时再次调用会返回 `probe already running`，不排队。`probe.toml` 的 `runAtStartup = true` 可在开服时运行默认 probe，默认关闭；详见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-13"></a>
### 一次 probe 做了什么

1. 收集当前所有绑定、事件、适配器、配方 schema、注册表内容。
2. 从种子类 BFS 收集相关 Java 类（深度与包过滤由 `probe.toml` 控制）。
3. backend 需要 IR 时（内置 TypeScript 与 Python 均需要），或 `ProbeEvents.modifyType`/`ProbeEvents.assignType` 有监听器时，构建共享 IR。已注册监听器作为 SERVER 事件运行，应在 SERVER 脚本中订阅。
4. 默认 backend 生成流程先在内存中渲染声明文件，再**逐文件就地同步该 backend 的输出目录**（跳过未变化文件、覆盖变化文件、清理旧文件）。渲染失败会保留该声明目录原有输出；写入失败可能留下部分更新，旧文件删除失败会产生警告。运行前面已生成的工作区或编辑器文件不会因此回滚。
5. 合并编辑器配置（TS → jsconfig 的 paths/include/typeRoots；Python → 各目录 pyrightconfig 的 extraPaths + 各目录 `.vscode/settings.json` 的 `python.analysis.extraPaths`/`python.languageServer`，通用注入、只改 probe 拥有的键）。

> 加了新 mod、新事件、或脚本注册了新内容后，建议跑一次 `/nekojs probe` 刷新类型声明。详见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-14"></a>
## editor 历史说明

内置工作区编辑器和 `/nekojs editor` 已移除。请使用 VS Code 等外部编辑器，并通过 probe 获取类型提示；只读错误 UI 不提供脚本编辑或上传功能。

<a id="wiki-section-15"></a>
## error / view_all_errors

- `/nekojs error`：快速报告当前是否有错误（yes/no + 简短摘要）。
- `/nekojs view_all_errors`：列出完整错误。NeoForge 会把错误列表交给错误 UI，Fabric 在聊天中输出文本列表。

NeoForge 存在错误时需要玩家才能打开错误 UI；非玩家来源不能接收该数据包，可使用 `/nekojs error` 查看控制台概览。Fabric 的文本错误列表也可从非玩家来源查询。

NeoForge 26.1.2／26.2.0 使用内置 JSX 仪表盘，接入真实错误快照、搜索、详情独立滚动、经确认的复制与经检查的异步 VS Code 定位。玩家执行 `/nekojs view_all_errors` 总会打开面板，零错误时显示空状态；`/nekojs error` 仍只在聊天中报告概览，被动快照更新不会打开已关闭的面板。它独立于用户 CLIENT 脚本 generation；内置 UI 失败时回退现有原生面板。NeoForge 1.21.1 保留原生 UI。命令权限、网络包字段和聊天链接打开流程均不变。

入口求值失败会被捕获、记录到 `logs/nekojs/<type>.log`，并将该入口标记为 disabled；入口成功执行后清除此标记。回调/事件错误另行记录，因此并非每条错误都代表入口被禁用。Context 被终止时会隔离所属 generation，需要显式完整 reload 恢复；普通失败也不表示会自动恢复。

<a id="wiki-section-16"></a>
## 权限

服务端 `/nekojs` 子命令需要 **op 2 及以上**（26.x 使用 `LEVEL_GAMEMASTERS`，NeoForge 1.21.1 使用 `hasPermission(2)`）。单人世界开启作弊的玩家满足此要求。生命周期命令需要服务端命令来源，不要求一定是玩家：满足权限的服务端控制台可请求 SERVER 或 TEST 重载，CLIENT 重载则需要 integrated client runtime。`hand`、`hotbar`、`inventory` 等玩家专用命令不能从非玩家来源执行。Authoring 命令执行使用固定的非玩家“NekoJS Authoring”来源，权限为 gamemaster／2 级、无实体、位置与朝向为零、维度为主世界；它不模拟玩家，也不提供不受限的控制台权限。客户端本地 `/nekojs browse` 不需要管理员权限。

<a id="wiki-section-17"></a>
## 注册自定义命令

脚本可在服务端命令树构建时通过 `CommandEvents.register` 注册命令。各平台 payload 不同；dispatcher 用来注册 `Commands.literal(...)` 创建的 builder，并不提供 `dispatcher.literal(...)` 方法：

- **NeoForge（26.x / 1.21.1）**：`event` 是原生 `RegisterCommandsEvent`；通过 `event.dispatcher` Bean 属性取得 dispatcher 并注册 Brigadier builder。
- **Fabric 26.x**：`event` 是 `CommandRegistryEventJS`，提供 `event.dispatcher` 与 `event.registryAccess` Bean 属性。通过 dispatcher 注册 builder；`registryAccess` 为参数类型提供命令构建上下文。

当前仓库不验证 Cleanroom 的自定义命令 API。

完整示例与字段差异见 [事件参考 - CommandEvents](event-reference_cn#wiki-section-18)。

<a id="wiki-section-18"></a>
## 下一步

- [脚本基础](script-basics_cn) —— reload 行为详解。
- [快速开始](quick-start_cn)。
- [常见问题](faq_cn)。

版本化包另提供创建、严格校验、归档、模组 JAR 导出和包局部重载；完整命令、依赖、能力和兼容边界见 [版本化脚本包](script-packs_cn.md)。

<!-- wiki-nav -->

---

[上一篇: AI 辅助脚本编写](ai-support_cn) · [目录](Home) · [下一篇: 错误与日志参考](error-reference_cn)
