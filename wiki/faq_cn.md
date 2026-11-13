<!-- wiki-page: faq; locale: cn -->

> **中文** · [English](faq_us)

<a id="wiki-section-1"></a>
# 常见问题

<a id="wiki-section-2"></a>
## 安装

<a id="wiki-section-3"></a>
### 启动时崩溃，提示找不到 GraalJS 或 polyglot

没有装 **Graal** 前置模组，或者装错了版本。使用[对应加载器的下载](quick-start_cn#wiki-section-2)安装匹配的 GraalMC 25.5.0 或更高版本。两个固定版本均包含其使用的重定位 ICU。

<a id="wiki-section-4"></a>
### 脚本一用正则就报 `No language for id regex found`

Graal 版本太低或缺少正则语言 provider。安装满足[对应加载器要求](quick-start_cn#wiki-section-2)的版本。

<a id="wiki-section-5"></a>
### 游戏目录里没有 nekojs 文件夹

1. 检查 NekoJS 和 Graal 是否都在 `mods/` 里，并且启动日志里没有报错。
2. 检查游戏目录是否有写入权限。
3. 进入世界后执行一次 `/nekojs probe`，它也会创建这个文件夹。

<a id="wiki-section-6"></a>
### 支持哪些版本？

NeoForge 1.21.1、26.1.2、26.2.0，以及 Fabric 26.1.2、26.2。Fabric 版本缺少部分功能，见 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-section-7"></a>
## 脚本没有效果

<a id="wiki-section-8"></a>
### 写了脚本但什么都没发生

按顺序检查：

1. **目录对吗？** 改配方要放在 `server_scripts`，注册物品要放在 `startup_scripts`。见 [脚本基础](script-basics_cn)。
2. **reload 了吗？** 保存文件不会自动生效。服务端脚本执行 `/nekojs reload`，客户端脚本执行 `/nekojs reload client`。
3. **是启动脚本吗？** 启动脚本必须重启游戏。
4. **有报错吗？** 执行 `/nekojs error`。
5. **被禁用了吗？** 文件开头有 `// disable:` 的脚本不会运行。
6. **缺模组吗？** 文件开头写了 `// modloaded: xxx`，而 xxx 没装时，脚本不会运行。

<a id="wiki-section-9"></a>
### 报错 `xxx is not defined` 或 `xxx is not a function`

通常是以下原因之一：

- **脚本放错了目录**。例如 `ServerEvents.recipes` 只能在 `server_scripts` 里用，`ClientEvents` 只能在 `client_scripts` 里用。
- **名字拼错了**。去 [事件参考](event-reference_cn) 或 [全局绑定](global-bindings_cn) 核对。开启 [代码补全](#wiki-section-21) 能避免大部分拼写错误。
- **当前平台没有这个功能**。部分事件和绑定只在 NeoForge 上有，见 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-section-10"></a>
### reload 之后行为没有变化

- 先执行 `/nekojs error`。如果新脚本有错误，这次 reload 会整体不生效，游戏继续使用旧脚本。修好错误再 reload。
- 改的是启动脚本？启动脚本必须重启游戏。
- 改的是客户端脚本？要用 `/nekojs reload client`，单独的 `/nekojs reload` 只重载服务端脚本。

<a id="wiki-section-11"></a>
### 游戏在加载脚本时卡住了

脚本里可能有死循环，或者 `await` 了一个永远不会完成的 Promise。NekoJS 不会强行中断这类脚本。检查最近修改的代码，特别是循环和顶层的 `await`。

<a id="wiki-section-12"></a>
### 顶层 await 或 import 报错

`import`、`export` 和顶层 `await` 只能在 ES 模块里用。把文件后缀改成 `.mjs` 最保险。后缀为 `.cjs` 的文件不支持这些语法。详见 [模块系统](module-system_cn)。

<a id="wiki-section-13"></a>
## 配方

<a id="wiki-section-14"></a>
### 加了配方，但 JEI 里看不到

`/nekojs reload` 之后，配方在服务端已经生效，可以直接去工作台验证。JEI、REI 等配方查看器有时不会自动刷新，重新进入世界即可。

<a id="wiki-section-15"></a>
### 怎么给配方指定 ID？

在配方后面加 `.id('命名空间:名字')`：

```javascript
event.shaped('4x minecraft:stick', ['C'], { C: 'minecraft:cobblestone' })
  .id('mypack:cobble_to_sticks')
```

不指定时会自动生成一个 `nekojs:` 开头的 ID。按 ID 删除配方用 `event.removeById(id)`。

<a id="wiki-section-16"></a>
### 某个模组的配方类型调用不了，或者补全里显示 `never`

NekoJS 会尽量自动识别其他模组的配方类型，但不是所有类型都能识别：

- **补全里找不到这个配方方法**：可以用 `event.custom({...})` 直接写这个配方的完整 JSON，格式参考该模组自己的数据包。
- **补全里显示为 `never`**：NekoJS 识别出了这个配方类型，但确认无法安全支持它。查看日志里的 `NEKO-52xx` 错误码了解原因，并改用 `event.custom(...)`。

不要手动修改 `.neko_probe` 里生成的文件来“修好”它，下次 probe 会被覆盖，运行时也依然会报错。详见 [配方系统](recipe-system_cn)。

<a id="wiki-section-17"></a>
## 新物品和方块

<a id="wiki-section-18"></a>
### 物品显示成紫黑格子

缺少材质或模型。把材质 PNG 放在：

```text
nekojs/assets/<命名空间>/textures/item/<名字>.png
```

例如 `mymod:cool_gem` 的材质放在 `nekojs/assets/mymod/textures/item/cool_gem.png`。改完资源按 F3+T 刷新。详见 [注册新内容 - 资源与本地化](registering-new-content_cn#wiki-assets)。

<a id="wiki-section-19"></a>
### 物品名字显示成 `item.mymod.xxx`

缺少语言文件。在 `nekojs/assets/mymod/lang/zh_cn.json` 里写：

```json
{ "item.mymod.xxx": "物品的中文名" }
```

<a id="wiki-section-20"></a>
### 改了启动脚本，新物品没出现

启动脚本只在游戏启动时运行一次，必须重启游戏。`/nekojs reload startup` 不可用。

<a id="wiki-section-21"></a>
## 代码补全

<a id="wiki-section-22"></a>
### VS Code 里没有补全

1. 在游戏里执行 `/nekojs probe`。
2. 用 VS Code 打开 **`nekojs/server_scripts` 这个文件夹**（或者其他脚本目录），不要打开整个游戏目录。
3. 确认游戏目录下有 `.neko_probe/` 文件夹（和 `nekojs/` 在同一层）。

<a id="wiki-section-23"></a>
### 补全的内容和实际不一致

补全内容是执行 `/nekojs probe` 那一刻生成的。加了模组、注册了新内容之后，重新执行一次 `/nekojs probe`。

<a id="wiki-section-24"></a>
### Python 脚本能运行，但没有补全

1. 执行 `/nekojs probe python`（只执行 `/nekojs probe` 不会生成 Python 的声明）。
2. 在每个 `.py` 文件顶部写：

```python
from nekojs import *
```

也可以只导入用到的名字，比如 `from nekojs import Item, ServerEvents`。这一行只给编辑器看，运行时会被自动去掉。不需要 pip 安装任何东西，也不要写成 `import nekojs`。

3. 确认 VS Code 装了 Pylance 扩展。

详见 [Python 脚本](python-scripts_cn)。

<a id="wiki-section-25"></a>
### .ts 文件写错了类型也能运行

正常。NekoJS 运行 TypeScript 时只是去掉类型标注，不做类型检查。类型错误由 VS Code 提示。在 `.js` 文件顶部加 `// @ts-check` 也能让 VS Code 检查类型。

<a id="wiki-section-26"></a>
## npm 和模块

<a id="wiki-section-27"></a>
### `require('xxx')` 报 module not found

1. 包要放在 `nekojs/node_modules/<包名>/`。
2. 只支持**纯 JavaScript** 的包。包含 C/C++ 编译代码的包（原生模块）无法使用。
3. `require('./xxx')` 是相对于当前文件的路径；`require('xxx')` 才会去 `node_modules` 里找。

NekoJS 不是完整的 Node.js，能用的内置模块见 [Node.js 兼容](nodejs-compatibility_cn)。

<a id="wiki-section-28"></a>
## 性能

<a id="wiki-section-29"></a>
### tick 事件里的代码让游戏变卡

`ServerEvents.tickPost` 之类的事件每秒触发 20 次，里面的代码要尽量轻。把计算结果缓存起来，或者用计数器每隔几个 tick 才执行一次：

```javascript
let ticks = 0
ServerEvents.tickPost(event => {
  if (++ticks % 20 !== 0) return // 每秒只执行一次
  // ...
})
```

<a id="wiki-section-30"></a>
### 第一次加载脚本比较慢

JavaScript 引擎第一次运行需要预热，属于正常现象。如果一直很慢，检查是否引入了很大的 npm 包，或者在脚本顶层读写了大量文件。

<a id="wiki-section-31"></a>
## 安全

<a id="wiki-section-32"></a>
### 脚本读写文件被拒绝

为了安全，脚本默认只能写入 `nekojs/` 文件夹里的文件。详见 [Node.js 兼容 - 访问范围限制](nodejs-compatibility_cn#wiki-fs-access)。

<a id="wiki-section-33"></a>
### 在多人服务器上用 NekoJS 安全吗？

脚本拥有很大的权限，能访问大部分游戏内部功能。**只运行你信任的人写的脚本。** NekoJS 的沙盒能挡住一部分危险操作，但不能保证完全阻止恶意脚本。

如果开启了 [AI 辅助功能](ai-support_cn) 的远程控制，不要把访问令牌提交到代码仓库或分享给别人。

<a id="wiki-section-34"></a>
## 还是没解决？

- 查看 [错误与日志参考](error-reference_cn)，按错误码查原因。
- 到 [GitHub Issues](https://github.com/NekoJS-Dev/NekoJS/issues) 提问。请附上 `logs/nekojs/` 里相关的日志、出错的脚本，以及你的 Minecraft、加载器和 NekoJS 版本。

<!-- wiki-nav -->

---

[上一篇: 配置参考](configuration_cn) · [目录](Home) · [下一篇: 脚本基础](script-basics_cn)
