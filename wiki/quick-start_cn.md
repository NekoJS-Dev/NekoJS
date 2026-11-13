<!-- wiki-page: quick-start; locale: cn -->

> **中文** · [English](quick-start_us)

<a id="wiki-section-1"></a>
# 快速开始

本页带你装好 NekoJS，写出第一个脚本，改一个配方，再加一个新物品。全程大约 15 分钟。

<a id="wiki-section-2"></a>
## 1. 安装

你需要两个模组：

- **NekoJS**：从 [CurseForge](https://www.curseforge.com/minecraft/search?search=NekoJS) 或 [GitHub Releases](https://github.com/NekoJS-Dev/NekoJS/releases) 下载。
- **[Graal](https://www.curseforge.com/minecraft/mc-mods/graal)**：NekoJS 的前置，负责真正执行 JavaScript。两个加载器均要求 **GraalMC 25.5.0 或更高**。当前固定下载为 [Modrinth 上的 NeoForge 版本](https://modrinth.com/mod/bjcrgPcA/version/O8qtoV6d) 和 [CurseForge 上的 Fabric 版本（文件 9555615）](https://www.curseforge.com/minecraft/mc-mods/graal/files/9555615)。

两者都要选和你的 Minecraft 版本、加载器（NeoForge 或 Fabric）对应的文件。把它们放进 `mods/` 文件夹，启动游戏。

> 每个 Minecraft 版本与加载器组合只提供一个 NekoJS 模组 jar，客户端和专用服务器使用同一个文件，不再提供单独的 `-server` jar。

> NekoJS 不内嵌 ICU4J。两个加载器当前固定的 GraalMC 25.5.0 均包含供 GraalJS 使用的重定位 ICU，因此该运行时不需要单独安装 ICU 库。

> Graal 版本太低时，脚本里一用正则表达式就会报 `No language for id regex found`。看到这条报错就去升级 Graal。

<a id="wiki-section-3"></a>
## 2. 认识 nekojs 文件夹

第一次启动后，游戏目录下会多出一个 `nekojs/` 文件夹。你最常用的是这四个目录：

```text
nekojs/
├── startup_scripts/   # 启动脚本：注册新物品、方块。改了要重启游戏
├── server_scripts/    # 服务端脚本：改配方、监听游戏事件。用 /nekojs reload 生效
├── client_scripts/    # 客户端脚本：HUD、按键、界面。用 /nekojs reload client 生效
├── test_scripts/      # 测试脚本：只在执行 /nekojs test 时运行
├── assets/            # 资源：材质、模型、语言文件
├── data/              # 数据包内容
├── packs/             # 可选：把脚本打包分发，见“脚本包”
└── config/            # 配置文件，见“配置参考”
```

脚本文件可以是 `.js`、`.ts`、`.jsx`、`.tsx`、`.mjs`、`.cjs` 或 `.py`。新手用 `.js` 就好。

> 把脚本放错目录是最常见的“脚本没反应”原因。改配方必须放在 `server_scripts`，注册物品必须放在 `startup_scripts`。区别见 [脚本基础](script-basics_cn)。

<a id="wiki-section-4"></a>
## 3. 第一个脚本

新建 `nekojs/server_scripts/hello.js`：

```javascript
console.info('Hello, NekoJS!')
```

进入一个世界。如果世界已经打开，执行：

```text
/nekojs reload
```

然后打开游戏目录下的 `logs/nekojs/server.log`，可以看到 `Hello, NekoJS!`。每种脚本都有自己的日志文件：`startup.log`、`server.log`、`client.log`、`test.log`。

> 保存文件不会自动生效，每次改完都要执行一次 reload。

<a id="wiki-section-5"></a>
## 4. 改一个配方

新建 `nekojs/server_scripts/recipes.js`：

```javascript
ServerEvents.recipes(event => {
  // 删除所有产出木棍的配方
  event.remove({ output: 'minecraft:stick' })

  // 新增：1 个圆石合成 4 根木棍
  event.shaped('4x minecraft:stick', [
    'C'
  ], {
    C: 'minecraft:cobblestone'
  }).id('nekojs:cobble_to_sticks')
})
```

执行 `/nekojs reload`，在工作台里放一个圆石，就能合成出 4 根木棍，原来的木棍配方都没了。

如果 JEI 之类的配方查看器还显示旧配方，重新进入世界即可，合成本身已经生效。想恢复原样，删掉这个文件再 reload 一次。

更多写法见 [配方系统](recipe-system_cn)。

<a id="wiki-section-6"></a>
## 5. 添加一个新物品

注册新物品必须写在**启动脚本**里。新建 `nekojs/startup_scripts/items.js`：

```javascript
RegistryEvents.register(event => {
  event.item('mymod:cool_gem', item => {
    item.maxStackSize = 16
    item.rarity = 'rare'
  })
})
```

**重启游戏**（启动脚本不能 reload），然后执行 `/give @s mymod:cool_gem`。

这时物品还是紫黑格子、名字显示成 `item.mymod.cool_gem`，因为还没有材质和名称：

- 材质：放一张 PNG 到 `nekojs/assets/mymod/textures/item/cool_gem.png`
- 名称：在 `nekojs/assets/mymod/lang/zh_cn.json` 写入 `{ "item.mymod.cool_gem": "酷炫宝石" }`

> 物品一旦在存档里用过，就不要随便删掉它的注册脚本，否则存档里的这些物品会丢失。建议先在测试世界里试。

更多写法见 [注册新内容](registering-new-content_cn)。

<a id="wiki-section-7"></a>
## 6. 开启代码补全

NekoJS 能根据你当前的游戏（包括装了哪些模组）生成类型声明，让 VS Code 提示有哪些事件、方法和物品 ID。

1. 在游戏里执行 `/nekojs probe`。
2. 用 VS Code **打开 `nekojs/server_scripts` 这个文件夹**（不是整个游戏目录）。
3. 输入 `ServerEvents.` 就能看到提示了。

加了新模组或注册了新内容之后，再执行一次 `/nekojs probe` 刷新。

用 Python 写脚本的话，执行 `/nekojs probe python`，并在每个 `.py` 文件顶部写 `from nekojs import *`。详见 [Python 脚本](python-scripts_cn)。

<a id="wiki-section-8"></a>
## 7. 接下来

- [脚本基础](script-basics_cn)：四种脚本的区别、加载顺序、出错了怎么查。
- [命令](commands_cn)：所有 `/nekojs` 命令。
- [事件参考](event-reference_cn)：可以监听哪些游戏事件。
- [全局绑定](global-bindings_cn)：`Item`、`Text`、`JsonIO` 等可以直接用的工具。
- [常见问题](faq_cn)：遇到问题先来这里。

<!-- wiki-nav -->

---

[上一篇: 平台与兼容性](platform-compatibility_cn) · [目录](Home) · [下一篇: 配置参考](configuration_cn)
