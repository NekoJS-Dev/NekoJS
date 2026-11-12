<!-- wiki-page: module-system; locale: cn -->

> **中文** · [English](module-system_us)

<a id="wiki-section-1"></a>
# 模块系统

脚本写多了，就该拆成几个文件。本页讲怎么用 `import` / `export` 或 `require` 在文件之间共享代码，怎么引用 npm 包，以及怎么直接导入 Java 类。

<a id="wiki-section-2"></a>
## 拆分成多个文件

推荐用 `import` / `export`（ES 模块）。例如把常量和辅助函数放进 `lib/` 目录：

```text
nekojs/server_scripts/
├── lib/
│   ├── constants.js
│   └── recipe_helpers.js
└── main.js
```

```javascript
// lib/constants.js
export const ORES = ['minecraft:iron_ore', 'minecraft:gold_ore']
```

```javascript
// lib/recipe_helpers.js
export function smeltOre(event, ore, output) {
  event.smelting(Item.of(output), Ingredient.of(ore)).id(`nekojs:smelt_${ore.split(':')[1]}`)
}
```

```javascript
// main.js
import { ORES } from './lib/constants.js'
import { smeltOre } from './lib/recipe_helpers.js'

ServerEvents.recipes(event => {
  for (const ore of ORES) {
    smeltOre(event, ore, ore.replace('_ore', '_ingot'))
  }
})
```

执行 `/nekojs reload`，熔炉里就能把铁矿石和金矿石直接烧成锭了。

几点要注意：

- 相对路径（`./`、`../`）从**当前文件所在的目录**算起。
- 写完整的文件名，带上扩展名，例如 `./lib/constants.js`。
- 脚本目录里的**每个文件都会被当作脚本加载**，`lib/` 里的也一样。所以辅助文件里只放导出的函数和常量，不要在顶层注册事件，也不要做只该做一次的事。

还支持默认导出、`import * as ns`、循环依赖、`import.meta`、顶层 `await` 和动态 `import()`。JSON 文件也能直接导入：

```javascript
import settings from './settings.json'
console.info(settings.enabled)
```

<a id="wiki-section-3"></a>
## CommonJS

也可以用 `require` 和 `module.exports`：

```javascript
// utils.cjs
function calculateDamage(base, multiplier) {
  return base * multiplier
}
module.exports = { calculateDamage }
```

```javascript
// main.cjs
const { calculateDamage } = require('./utils.cjs')
console.info(calculateDamage(4, 1.5)) // 6
```

两种写法可以混用：`import` 能导入 `.cjs` 文件，`require` 也能加载 ES 模块。

<a id="wiki-section-4"></a>
## 扩展名决定模块类型

| 扩展名 | 按什么方式运行 |
|---|---|
| `.mjs` | ES 模块 |
| `.cjs` | CommonJS |
| `.js` | 自动判断：文件里有 `import` / `export` 就按 ES 模块，否则按 CommonJS |
| `.ts` / `.jsx` / `.tsx` | 先编译成 JavaScript，再和 `.js` 一样自动判断 |

拿不准就用 `.js`。在 `.cjs` 文件里写 `export` 会报语法错误。

`nekojs/config/engine.toml` 里的 `enableEsmAuthoring` 默认是 `true`。改成 `false` 后，所有文件都按 CommonJS 运行，`import` / `export` 会直接报错，不会被自动转换。一般不需要改它。

TypeScript 和 JSX 见 [TypeScript 与 JSX](typescript-and-jsx_cn)。

<a id="wiki-section-5"></a>
## 使用 npm 包

把纯 JavaScript 的包放进 `nekojs/node_modules/`，然后用包名引用：

```javascript
// nekojs/node_modules/greeting/index.js
module.exports = name => `Hello, ${name}!`
```

```javascript
// nekojs/server_scripts/use_greeting.js
const greet = require('greeting')
console.info(greet('NekoJS')) // Hello, NekoJS!
```

NekoJS 按这个顺序查找 `greeting`：

1. 文件 `node_modules/greeting` 本身；
2. 加上扩展名，例如 `node_modules/greeting.js`；
3. 目录里的 `index` 文件，例如 `node_modules/greeting/index.js`。

<a id="wiki-section-6"></a>
### npm 包的限制

- **不读 `package.json` 里的 `main` 和 `exports`。** 包的入口不是 `index.js` 时，写出完整路径，例如 `require('some-lib/dist/some-lib.js')`。
- **不支持带原生代码的包**，例如 `sharp`、`better-sqlite3`、`canvas`。
- NekoJS 只提供一部分 Node.js 核心模块，见 [Node.js 兼容](nodejs-compatibility_cn)。用到其他 Node API 的包跑不起来。
- 包里的代码和你的脚本受同样的限制，只能访问游戏目录里的文件。

一个包能不能用，最快的办法是放进去试一下。

<a id="wiki-api-modules"></a>
<a id="wiki-section-7"></a>
## 内置 API 模块

NekoJS 的大部分功能是全局变量，直接用就行，例如 `Item`、`ServerEvents`。其中一部分也可以从内置模块导入：

| 模块 | 内容 |
|---|---|
| `nekojs/api/stable` | 稳定接口，例如 `ID`、`Native`；客户端脚本里还有 `I18n` |
| `nekojs/api/version` | 跟 Minecraft 版本相关的接口，例如 `Item` 和大部分事件组 |

```typescript
import { ID } from 'nekojs/api/stable'
import { Item } from 'nekojs/api/version'

const id = ID.resource('minecraft:stone')
const stack = Item.of('minecraft:stone')
```

```javascript
const { ID } = require('nekojs/api/stable')
```

这些模块不用安装。某个名字在当前脚本类型里没有时，导入会直接报错。例如 `nekojs/api/stable` 里的 `ServerEvents` 只在服务端脚本里有，而且只有 `tickPre` 和 `tickPost` 两个事件。

插件可以发布自己的模块，路径是 `nekojs/api/stable/<modid>` 或 `nekojs/api/version/<modid>`。只通过模块发布的接口不会变成全局变量。

当前能导入哪些名字，以 `/nekojs probe` 生成的代码补全为准。全局变量的完整列表见 [全局绑定](global-bindings_cn)。

<a id="wiki-section-8"></a>
## 导入 Java 类

用 `java:` 前缀加斜杠路径，可以导入 Java 包或类。

**导入一个包**，再从里面取类：

```javascript
import { ArrayList } from 'java:java/util'

const list = new ArrayList()
list.add('hello')
console.info(list.size()) // 1
```

类名和 JavaScript 自带的名字重名时（比如 `Math`），在前面加 `$`：

```javascript
import { $Math, Integer } from 'java:java/lang'
console.info($Math.max(3, 7))   // 7
console.info(Integer.MAX_VALUE) // 2147483647
```

**直接导入一个类**，默认导出就是这个类：

```javascript
import ArrayList from 'java:java/util/ArrayList'
const HashMap = require('java:java/util/HashMap')
```

路径规则：

| 规则 | 示例 |
|---|---|
| 必须以 `java:` 开头 | `java:java/util` |
| 用斜杠分隔，不能用点 | 写 `java:java/util/ArrayList`，不要写 `java:java.util.ArrayList` |
| 不能有 `.` 或 `..` | `java:../foo` 会报错 |

不用模块也行，直接写 `Java.type('java.util.ArrayList')`。

> 有 NekoJS 绑定时，优先用绑定。例如用 `Item.of(...)`，不要自己导入 Minecraft 的物品类。绑定写起来更短，有补全，换 Minecraft 版本也更稳。`java:` 导入留给绑定还没覆盖到的场景。

<a id="wiki-section-9"></a>
## 顶层 await 与动态 import

ES 模块的顶层可以直接 `await`，也可以等需要时再加载某个文件：

```javascript
const config = { enableExtras: true }

if (config.enableExtras) {
  const extras = await import('./lib/extras.js')
  extras.setup()
}
```

用了顶层 `await` 的 ES 模块不能被 `require` 加载，会报 `Cannot require async ESM module with top-level await: ... Use import() instead.`。按提示改用 `import()` 即可。

<a id="wiki-section-10"></a>
## 重载时模块会怎样

- 保存文件不会自动生效，要执行 `/nekojs reload`（或对应脚本类型的重载命令）。
- 重载时，所有模块都会重新运行，模块里的变量回到初始值。
- 想在重载之间保留数据，用 `global`，见 [脚本基础](script-basics_cn)。不要把旧模块导出的对象存起来，重载后它们就过时了。
- 只重载一个文件时（例如 `/nekojs reload server main.js`），它引用的、改动过的模块也会一起重新加载。

<a id="wiki-section-11"></a>
## 安全限制

脚本不能随意使用 Java 和文件：

- **危险的 Java 类无法导入**，例如 `java.lang.Runtime`、`java.lang.Process`、`java.lang.System`、`java.io`、`java.nio`、`java.net`、反射和类加载器。用 `java:` 还是 `Java.type` 都会失败。
- **只能访问游戏目录里的文件**，默认只能写 `nekojs/` 里的文件。详见 [Node.js 兼容](nodejs-compatibility_cn)。
- 创建线程、反射、操作字节码默认都关闭。对应 `nekojs/config/engine.toml` 里的 `allowThreads`、`allowReflection`、`allowAsm`，见 [配置参考](configuration_cn)。

这些限制能挡住常见的误操作，但**不能让来路不明的脚本变安全**。比如 Java 方法返回的对象就不受类名过滤的限制，NekoJS 也不会强行中断死循环。只运行你信任的脚本和 npm 包。

<a id="wiki-section-12"></a>
## 接下来

- [TypeScript 与 JSX](typescript-and-jsx_cn)：用 `.ts`、`.jsx`、`.tsx` 写脚本。
- [Node.js 兼容](nodejs-compatibility_cn)：能用的 Node 模块，例如 `fs`、`path`、`buffer`。
- [全局绑定](global-bindings_cn)：可以直接使用的工具。

<!-- wiki-nav -->

---

[上一篇: 注册新内容](registering-new-content_cn) · [目录](Home) · [下一篇: TypeScript 与 JSX](typescript-and-jsx_cn)
