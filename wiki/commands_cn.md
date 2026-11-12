<!-- wiki-page: commands; locale: cn -->

> **中文** · [English](commands_us)

<a id="wiki-section-1"></a>
# 命令

所有 NekoJS 命令都以 `/nekojs` 开头。除了 `/nekojs browse`，其他命令都需要管理员权限（单人游戏里打开作弊即可）。

<a id="wiki-section-2"></a>
## 常用命令

| 命令 | 作用 |
|---|---|
| `/nekojs reload` | 重新加载服务端脚本。改完 `server_scripts` 后用它 |
| `/nekojs reload client` | 重新加载客户端脚本 |
| `/nekojs test` | 运行 `test_scripts` 里的测试 |
| `/nekojs error` | 看看现在有没有脚本报错 |
| `/nekojs view_all_errors` | 查看完整的错误列表 |
| `/nekojs probe` | 生成代码补全用的类型声明 |
| `/nekojs hand` | 查看手上物品的 ID、组件和标签，可点击复制 |
| `/nekojs browse` | 在文件管理器里打开 `nekojs` 文件夹 |

<a id="wiki-section-3"></a>
## 重新加载脚本

<a id="wiki-section-4"></a>
### /nekojs reload

| 写法 | 作用 |
|---|---|
| `/nekojs reload` | 重载服务端脚本，和 `/nekojs reload server` 一样 |
| `/nekojs reload server` | 重载服务端脚本，配方也会重新计算 |
| `/nekojs reload client` | 重载客户端脚本。只能在单人游戏或客户端上用 |
| `/nekojs reload test` | 重载并运行测试脚本，和 `/nekojs test` 一样 |
| `/nekojs reload startup` | **不支持**。启动脚本改了要重启游戏 |
| `/nekojs reload server recipes.js` | 只重跑一个文件。路径相对于 `server_scripts`，按 TAB 可补全 |

重载整个类型时，只要有一个脚本报错，这次重载就整体不生效，游戏继续使用上一次正常的脚本。修好错误后再 reload 一次即可。

只重跑一个文件时没有这层保护，而且可能连带重跑引用了它的其他文件。日常开发建议重载整个类型。

`/nekojs reload client` 执行后会先显示“已安排”，真正的重载随后在客户端完成，请留意后续的结果消息。

<a id="wiki-section-5"></a>
### 和原版命令的区别

| 操作 | 重新加载的内容 |
|---|---|
| `/nekojs reload` | NekoJS 服务端脚本，以及脚本修改过的配方 |
| 原版 `/reload` | 数据包；在 NeoForge 上也会顺带重载服务端脚本 |
| F3+T | 客户端资源（材质、模型、语言）；在 NeoForge 上也会顺带重载客户端脚本 |

改了脚本请用 `/nekojs reload`。只改了 `nekojs/data` 下的数据包文件，用原版 `/reload`；只改了 `nekojs/assets` 下的资源，按 F3+T。

<a id="wiki-section-6"></a>
### /nekojs test

运行 `test_scripts` 里的所有测试，并报告断言的通过和失败数量。命令执行完成不代表测试都通过了，请看输出的结果。如果一条断言都没有执行，结果会显示“未观察到”，而不是“通过”。

<a id="wiki-section-7"></a>
## 查看错误

| 命令 | 作用 |
|---|---|
| `/nekojs error` | 在聊天栏显示是否有错误，以及简短摘要 |
| `/nekojs view_all_errors` | 显示完整错误列表 |

`view_all_errors` 在 NeoForge 上会打开错误面板，可以搜索、查看详情、复制错误，还能点击直接在 VS Code 里跳到出错的那一行。Fabric 上以聊天文本显示。

错误面板需要由玩家执行命令才能打开；在服务器控制台里请用 `/nekojs error`。

错误也会写入 `logs/nekojs/<类型>.log`。错误码的含义见 [错误与日志参考](error-reference_cn)。

<a id="wiki-section-8"></a>
## 查看物品和标签

写脚本时经常需要知道物品的准确 ID 和标签。这几条命令输出的内容都可以**点击复制**，粘贴进脚本即可使用。

<a id="wiki-section-9"></a>
### /nekojs hand

查看主手上的物品，显示：

- 物品字符串，比如 `"minecraft:iron_sword[damage=2]"`，可以直接写进 `Item.of(...)`
- 完整的物品 JSON
- 物品的所有标签
- 如果是方块物品，显示对应方块的 ID 和标签
- 如果是装着流体的容器（比如水桶），显示流体的 ID 和标签

<a id="wiki-section-10"></a>
### /nekojs hotbar 和 /nekojs inventory

把快捷栏（`hotbar`）或整个背包（`inventory`）里的物品导出成列表，例如：

```json
["2x minecraft:stone", "minecraft:apple"]
```

空格子会被跳过；不包括盔甲栏和副手。

<a id="wiki-section-11"></a>
### /nekojs list-tag

```text
/nekojs list-tag minecraft:item                  列出所有物品标签
/nekojs list-tag minecraft:item minecraft:logs   列出这个标签里有哪些物品
```

第一个参数是注册表（`minecraft:item`、`minecraft:block` 等），第二个是标签。两个参数都可以按 TAB 补全。这条命令也可以在服务器控制台执行。

<a id="wiki-section-12"></a>
## 代码补全

| 命令 | 作用 |
|---|---|
| `/nekojs probe` | 生成 TypeScript 类型声明，供 VS Code 补全 |
| `/nekojs probe python` | 生成 Python 类型声明 |
| `/nekojs probe all` | 生成所有语言的类型声明 |
| `/nekojs probe reset_config` | 编辑器配置被改乱时，恢复成默认值 |

生成的文件在游戏目录下的 `.neko_probe/`。装了新模组或注册了新内容后，重新执行一次 `/nekojs probe`。

想每次开服都自动生成，在 `nekojs/config/probe.toml` 里设置 `runAtStartup = true`。

其他子命令（`list`、`reload`、`enable`、`disable`、按名称指定生成器等）和完整配置见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-13"></a>
## 脚本包

用来管理 `nekojs/packs/` 下的脚本包。

| 命令 | 作用 |
|---|---|
| `/nekojs packs` | 列出所有脚本包 |
| `/nekojs packs enable <包>` / `disable <包>` | 启用或停用一个包，之后需要 reload 才生效 |
| `/nekojs packs status server` | 查看每个包当前的运行结果 |
| `/nekojs packs plan server` | 预览加载顺序和哪些文件会被跳过，不会真正执行 |
| `/nekojs trust <服务器地址>` | 信任一个服务器，允许它把脚本包发给你的客户端 |

`<包>` 一般写成 `GLOBAL:包名`，世界里的包写 `WORLD:包名`。名字不会重复时也可以只写包名。

脚本包的创建、打包和多人分发见 [脚本包](script-packs_cn)。

<a id="wiki-section-14"></a>
## 其他命令

| 命令 | 作用 |
|---|---|
| `/nekojs registry` | 查看脚本动态注册的内容（实验功能，仅 NeoForge 26.x 有内容） |
| `/nekojs registry stale` | 列出上次 reload 后已不再注册的内容 |

<a id="wiki-section-15"></a>
## 在脚本里添加命令

脚本可以用 `CommandEvents.register` 注册自己的命令，写法和完整示例见 [事件参考 - CommandEvents](event-reference_cn#wiki-command-events)。

<a id="wiki-section-16"></a>
## 接下来

- [脚本基础](script-basics_cn)：什么时候该用哪种 reload。
- [常见问题](faq_cn)：命令执行后没效果怎么办。

<!-- wiki-nav -->

---

[上一篇: AI 辅助脚本编写](ai-support_cn) · [目录](Home) · [下一篇: 错误与日志参考](error-reference_cn)
