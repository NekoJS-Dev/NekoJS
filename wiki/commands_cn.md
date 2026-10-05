<!-- wiki-page: commands; locale: cn -->

> **中文** · [English](commands_us)

<a id="wiki-section-1"></a>
# 命令

NekoJS 注册了一个根命令 `/nekojs`，需要权限等级 LEVEL_GAMEMASTERS（op 2 及以上）。

<a id="wiki-section-2"></a>
## 命令一览

| 命令 | 作用 | 平台 |
|---|---|---|
| `/nekojs reload` | 重载**服务端**脚本（默认）。NeoForge 和 Fabric 26.x 会继续处理配方脚本。 | NeoForge / Fabric 26.x |
| `/nekojs reload <type>` | 重载指定类型脚本。`<type>` ∈ `server` / `client` / `test`；`startup` 当前会拒绝执行，启动脚本改动需要重启游戏。 | NeoForge / Fabric 26.x |
| `/nekojs reload <type> <file>` | 重载单个脚本文件（路径相对该脚本类型根目录；文件名支持 TAB 补全） | NeoForge / Fabric 26.x |
| `/nekojs test` | 运行 `nekojs/test_scripts/` 下所有脚本（TEST 脚本上下文） | NeoForge / Fabric 26.x |
| `/nekojs error` | 报告当前是否有脚本错误 | NeoForge / Fabric 26.x |
| `/nekojs view_all_errors` | 列出完整错误；NeoForge 可打开错误 UI，Fabric 输出聊天文本 | NeoForge / Fabric 26.x |
| `/nekojs editor`（历史命令） | 已移除；使用外部编辑器编辑脚本 | 当前平台不提供 |
| `/nekojs packs` | 列出全部脚本包（全局 `nekojs/packs/` 与世界 `<世界>/nekojs_packs/`，含启用态/版本/目录） | NeoForge / Fabric 26.x |
| `/nekojs packs enable\|disable <id>` | 写包状态文件（优先级高于 manifest 的 `enabled`），随后 `/nekojs reload` 生效 | NeoForge / Fabric 26.x |
| `/nekojs trust <address>` | 多人脚本包分发：把服务器加入本客户端信任存储（首连未信任会被断开并提示本命令，信任后重连收包；在单人世界或客户端进程中执行） | NeoForge / Fabric 26.x |
| `/nekojs registry` | 动态注册健康快照；Fabric 当前快照为空 | NeoForge 26.x；Fabric 有命令但能力为空 |
| `/nekojs registry stale` | 只列出上次 reload 后脚本不再注册的动态条目 | NeoForge 26.x；Fabric 有命令但能力为空 |
| `/nekojs probe` | 重新生成 TypeScript 声明（`.d.ts`） | NeoForge / Fabric 26.x |
| `/nekojs probe <语言> [<名字>]` | 指定语言（或精确到 backend 名）生成 | NeoForge / Fabric 26.x |
| `/nekojs probe all` / `list` / `reload` / `enable` / `disable` / `reset_config` | 跑全部 backend / 列出已注册 backend / 重读 probe.toml / 持久化开关 / 恢复编辑器配置出厂值 | NeoForge / Fabric 26.x |

> Cleanroom 1.12.2 不在当前仓库版本图中；该分支的命令差异属于 legacy 资料，本文不对其运行时行为做验证承诺。

<a id="wiki-section-3"></a>
## reload 详解

<a id="wiki-section-4"></a>
### `/nekojs reload`（默认 server）

最常用。热重载服务端脚本（配方、事件监听）。**事务式**：先把脚本加载进候选 Context，全部成功才替换旧的；失败则保留旧 Context，不会进入半坏状态。

<a id="wiki-section-5"></a>
### `/nekojs reload <type>`

| type | 行为 |
|---|---|
| `server` | 同上 |
| `client` | 重载客户端脚本（仅 integrated client 上可用）。命令立即返回，实际 reload **转投客户端主线程**执行——客户端脚本环境（事件/timers）的归属线程就是它，这样 reload 与渲染期事件不会交叉线程 |
| `test` | 重载测试脚本 |
| `startup` | 当前命令会拒绝执行。启动脚本在游戏启动阶段运行，注册内容改动必须重启游戏。 |

<a id="wiki-section-6"></a>
### `/nekojs reload <type> <file>`

只重载单个文件，比全量 reload 快。例：

```
/nekojs reload server recipes.js
/nekojs reload client tooltips.js
```

文件路径相对该脚本类型根目录（如 `recipes.js` 相对 `nekojs/server_scripts/`）。文件名支持 TAB 补全。

<a id="wiki-section-7"></a>
## hand / inventory 详解

<a id="wiki-section-8"></a>
### `/nekojs hand`

开发调试命令：显示执行者主手物品的完整信息。

```
/nekojs hand
minecraft:diamond_sword x1          <- 物品 id（点击可复制）、数量
  damage: 5/1561                     <- 仅可损耗物品显示
  components: {minecraft:enchantments=>{levels={minecraft:sharpness=5}}}   <- 组件补丁（脚本改过的组件在此可见；{} = 无）
```

主手为空显示 `Empty hand`。控制台执行会报错（需要玩家）。

<a id="wiki-section-9"></a>
### `/nekojs inventory`

列出执行者主物品栏（0–35 槽位）所有非空槽位，每行 `slot N: <物品id> x<数量>`；全空显示
`Inventory is empty.`。同样需要玩家身份执行。

两个命令与其它 `nekojs` 子命令一致，需要游戏管理员权限（权限等级 2）。

<a id="wiki-section-10"></a>
## 配方 reload 的平台差异

| 平台 | `/nekojs reload server` 对配方的效果 |
|---|---|
| **Fabric 26.x** | 支持配方热重载；脚本面为当前 Fabric 配方子集。 |
| **NeoForge（26.x / 1.21.1）** | 支持热重载。`RecipeManagerMixin` 缓存数据包阶段的原始配方 JSON，reload 时重建工作集、重跑 `ServerEvents.recipes`，再替换 `RecipeManager`；配方逻辑即时生效，查看器可能需要客户端刷新。 |

> Cleanroom 1.12.2 的配方行为属于独立 legacy 分支，当前仓库不验证。 |

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
3. 若 `probe.modifyType`/`probe.assignType` 有监听器（或选中 Python backend），构建共享 IR 并触发这些 SERVER 事件。
4. 各 backend 渲染产物：先全部渲染进内存，再**逐文件就地同步**输出目录（相同的跳过、变化的覆盖、不再产出的删除）；渲染失败则完全不触盘，旧产物保留。
5. 合并编辑器配置（TS → jsconfig 的 paths/include/typeRoots；Python → 各目录 pyrightconfig 的 extraPaths + 各目录 `.vscode/settings.json` 的 `python.analysis.extraPaths`/`python.languageServer`，通用注入、只改 probe 拥有的键）。

> 加了新 mod、新事件、或脚本注册了新内容后，建议跑一次 `/nekojs probe` 刷新类型声明。详见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-14"></a>
## editor 历史说明

内置工作区编辑器和 `/nekojs editor` 已移除。请使用 VS Code 等外部编辑器，并通过 probe 获取类型提示；只读错误 UI 不提供脚本编辑或上传功能。

<a id="wiki-section-15"></a>
## error / view_all_errors

- `/nekojs error`：快速报告当前是否有错误（yes/no + 简短摘要）。
- `/nekojs view_all_errors`：列出完整错误。NeoForge 会把错误列表交给错误 UI，Fabric 在聊天中输出文本列表。

脚本出错不会崩溃游戏——错误会被捕获、记录到 `logs/nekojs/<type>.log`，出错脚本被标记 disabled 直到 reload 成功清除标记。

<a id="wiki-section-16"></a>
## 权限

所有 `/nekojs` 子命令都需要 **op 2 及以上**（`LEVEL_GAMEMASTERS`）。在单人世界里，开启作弊的玩家自动满足。

<a id="wiki-section-17"></a>
## 注册自定义命令

脚本可以在 `CommandEvents.register` 里注册自己的命令（在每次服务端启动时触发）。API 因平台而异：

- **NeoForge（26.x / 1.21.1）**：`event` 是 Brigadier `RegisterCommandsEvent`，用 `event.getDispatcher()` 走 `.literal(...).executes(...)` 链。
- **Cleanroom 1.12.2**：`event` 是 `FMLServerStartingEvent`，1.12.2 没有 Brigadier；脚本须实现 `ICommand`（通常继承 `CommandBase`）再 `event.registerServerCommand(cmd)`。

完整示例与字段差异见 [事件参考 - CommandEvents](event-reference_cn#wiki-section-18)。

<a id="wiki-section-18"></a>
## 下一步

- [脚本基础](script-basics_cn) —— reload 行为详解。
- [快速开始](quick-start_cn)。
- [常见问题](faq_cn)。

<!-- wiki-nav -->

---

[上一篇: 事件参考](event-reference_cn) · [目录](Home) · [下一篇: 错误与日志参考](error-reference_cn)
