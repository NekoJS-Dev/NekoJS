<!-- wiki-page: module-system; locale: cn -->

> **中文** · [English](module-system_us)

<a id="wiki-section-1"></a>
# 模块系统

NekoJS 同时支持**原生 ESM** 和 **CommonJS**，并且能把 Java 包/类当作模块导入。本页讲怎么拆分多文件、引用 npm 依赖、导入 Java 类。

<a id="wiki-section-2"></a>
## ESM（推荐）

NekoJS 提供原生 ESM 加载器；下列语法与行为不等于完整的 Node.js 或浏览器兼容性：

```javascript
// math_utils.mjs
export function add(a, b) { return a + b }
export const PI = 3.14
export default function square(x) { return x * x }
```

```javascript
// main.mjs
import square, { add, PI } from './math_utils.mjs'
import { existsSync } from 'node:fs'        // 也能导入 node: 模块

console.info(square(add(1, 2)))              // 9
```

**支持的特性**：
- `import` / `export`（具名、默认、命名空间）
- live binding（导出值变化会被导入方感知）
- 循环依赖
- `import.meta`
- **顶层 await**（top-level await）
- 动态 `import('...')`
- ESM/CJS 互操作

> `jsconfig.json` 默认 `module: "ESNext"`，IDE 完全支持。

<a id="wiki-section-3"></a>
## CommonJS

```javascript
// utils.cjs
function calculateDamage(base, multiplier) { return base * multiplier }
const MOD_NAME = 'NekoJS'
module.exports = { calculateDamage, MOD_NAME }
```

```javascript
// main.cjs
const { calculateDamage, MOD_NAME } = require('./utils.cjs')

ServerEvents.tickPre(event => {
  // 用 calculateDamage
})
```

脚本入口及其依赖由 NekoJS 模块加载器、解析器与 generation 所属缓存处理。Context 也启用了工作目录为 `nekojs/` 的 GraalJS CommonJS 支持，但该选项本身不能说明 NekoJS 的依赖身份或重载行为。相对 import/require 从导入模块的位置解析。

<a id="wiki-section-4"></a>
## 扩展名与模块模式

这张表不是命名建议，是引擎的分派规则（`NekoModuleMode.fromExtension`）：

| 扩展名 | 模块模式 |
|---|---|
| `.mjs` | `enableEsmAuthoring` 开启时请求 ESM |
| `.cjs` | 强制 CJS |
| `.js` | `AUTO`：编译结果具有模块语法时选 ESM，非模块程序选 CommonJS |
| `.ts` | 可擦除 TypeScript（擦除后按上面规则，`AUTO`） |
| `.jsx`/`.tsx` | JSX lowering（默认 classic，可选 automatic runtime），模块模式为 `AUTO` |

`enableEsmAuthoring=false` 会让 `.mjs` 也走 CommonJS 准备路径，并不提供将 import/export 自动改写为 CommonJS 的兼容转换。开启 ESM 时，`.cjs` 里写 `export` 不会因为你希望它是 ESM 就变成 ESM。而目录名（`server_scripts` / `client_scripts` / `startup_scripts` / `test_scripts`）与 `nekojs/` 根同样是硬编码的（`NekoJSPaths`），没有配置项可以改。

详见 [TypeScript 与 JSX](typescript-and-jsx_cn)。

模块缓存与依赖记录属于脚本 generation。显式旧式单文件重载会使改动模块及已知受影响的依赖或入口失效，然后在活动 Context 中重新执行入口；重跑失败不会撤销副作用。完整代重载使用新的候选 Context 与缓存，在提交后释放旧代。不要把旧代 namespace 或 exports 引用当作持久状态保留。仅保存模块不会自动重载。旧式脚本根目录下的辅助模块也会作为入口被发现，因此辅助模块顶层应避免注册事件或执行本应只运行一次的副作用。版本化包内文件要求包或类型重载，见[命令](commands_cn)与[脚本包](script-packs_cn)。

<a id="wiki-section-5"></a>
## 引用 npm 依赖

把**纯 JS** 的 npm 包放进 `nekojs/node_modules/`，然后像在 Node 里一样 `require`/`import`：

```javascript
// nekojs/node_modules/greeting/index.cjs
module.exports = name => `Hello, ${name}!`
```

```javascript
// server_scripts/use_greeting.cjs
const greet = require('greeting/index.cjs')
console.info(greet('NekoJS')) // Hello, NekoJS!
```

<a id="wiki-section-6"></a>
### 限制

- 注意：**只支持纯 JS 包**。包含原生 bindings（C/C++ 编译产物）的包**不能用**（如 `node-sass`、`sharp`、`better-sqlite3`）。
- 这是 shim，**不等于完整 Node.js 运行时**。能用的核心模块见 [Node.js 兼容](nodejs-compatibility_cn)。纯 JS 不保证兼容：未实现 package.json `main` 或 `exports` 解析，因此示例提供了完整模块及其明确入口。同步 `require()` 会拒绝尚未完成求值且含顶层 await 或异步静态依赖的 ESM，应改用 `import()`。
- 文件访问限制在游戏目录内，有符号链接逃逸检查。

<a id="wiki-api-modules"></a>
<a id="wiki-section-7"></a>
## 内置 NekoJS API 模块

这些路由由 NekoJS 内置提供，不是需要通过 npm 安装的包：

| 路由 | 契约 |
|---|---|
| `nekojs/api/stable` | 显式发布的稳定视图，包括 `ID` 与 `Native` 原生互操作桥 |
| `nekojs/api/version` | 当前节点的帮助对象与已发布事件组，包括 `Item` |

`stable` 不表示每个导出都能在所有加载器、Minecraft 版本或 Script Type 中使用。例如稳定 `ServerEvents` 仅提供 SERVER 的 `tickPre`/`tickPost`，`I18n` 仅供 CLIENT 使用。请查当前实例的 Probe 声明，不要认为原生事件载荷与稳定视图可以互换。

```ts
import { ID } from 'nekojs/api/stable'
import { Item } from 'nekojs/api/version'

const resource = ID.resource('minecraft:stone')
const stack = Item.of('minecraft:stone')
```

CommonJS 使用同一个 Context 专属命名空间：

```js
const { ID } = require('nekojs/api/stable')
const resource = ID.resource('minecraft:stone')
```

插件发布使用 `nekojs/api/stable/<modid>` 或 `nekojs/api/version/<modid>`。导入当前环境不可用的成员会失败，而不是返回占位对象。仅模块发布不会创建全局变量；已有旧式全局绑定仍受支持。这些路由独立于 `java:` 导入和 `nekojs/commands` 等现有领域模块。可用接口请结合[全局绑定](global-bindings_cn)与实例内的 [AI 创作手册](ai-support_cn)查询。

<a id="wiki-section-8"></a>
## 导入 Java 类（`java:` 模块）

Java 包/类可以作为特殊模块导入。**只接受 `java:` 前缀和斜杠分隔路径**。

<a id="wiki-section-9"></a>
### 包级模块（懒加载 namespace proxy）

```ts
import { Integer, $Integer, Math as JavaMath } from 'java:java/lang'
const { Integer, $Integer, Math: JavaMath } = require('java:java/lang')
```

- 普通名字（如 `Integer`、`Math`）按属性查找。
- `$Integer` 这种 `$` 前缀名字会直接映射到 `Java.type('java.lang.Integer')`。
- `Integer`、`$Integer`、`Math` / `JavaMath` 这几种写法都能用。

<a id="wiki-section-10"></a>
### 类级模块（直接返回 Java 类 proxy）

```ts
import IntegerClass, { $Integer } from 'java:java/lang/Integer'
const IntegerClass = require('java:java/lang/Integer')
```

- 直接返回 Java 类 proxy。
- 额外暴露 `default` 和 `$Class`。
- 如果只想拿一个明确的 Java 类，这种写法最直接。

<a id="wiki-section-11"></a>
### 规则

| 规则 | 说明 |
|---|---|
| 只接受 `java:` 前缀 | 不支持 `org.example:` 这类 |
| 只接受斜杠分隔路径 | `java:java/lang`、`java:java/lang/Integer` |
| 不支持 `.` / `..` | — |
| 动态 `import('java:...')` | 返回带 `default` / `namespace` 的合成 ESM 模块 |

<a id="wiki-section-12"></a>
### 实战：调用任意 Java API

```javascript
import { $ArrayList } from 'java:java/util'
const ArrayList = $ArrayList          // 等价 Java.type('java.util.ArrayList')
const list = new ArrayList()
list.add('hello')
console.info(list.size())             // 1
```

```javascript
// 调用原版 Java 静态方法
const JavaMath = Java.type('java.lang.Math')
console.info(JavaMath.max(3, 7))        // 7
console.info(JavaMath.PI)               // 3.141592653589793
```

> 配方/事件脚本里**首选 NekoJS 绑定**（`Item.of(...)` 而不是 `Java.type(...)`），更简洁、有类型提示。`java:` 导入主要用于绑定未覆盖的场景。

<a id="wiki-section-13"></a>
## 安全沙盒

NekoJS 限制脚本访问范围：

- **Java 类**：高危类被过滤（`java.lang.Runtime`、`Process`、`ClassLoader`、`System`、`java.io.*`、`java.nio.*`、反射、net、lwjgl、polyglot 本身，以及 `java.awt`/`javax.swing`/`javax.imageio`、`javax.naming`、`java.rmi`、`java.sql`/`javax.sql`、`java.lang.Module`、`org.graalvm`/`com.oracle.truffle`、NekoJS 内部实现 `com.tkisor.nekojs.core` 等）。具体黑名单见 `common/.../core/fs/ClassFilter.java`。
- **文件系统**：访问范围限制在游戏目录内，符号链接逃逸检查。
- **配置**：`nekojs/config/engine.toml`（与 probe.toml 同目录；旧 `config/nekojs-engine.toml` 仅作只读回退）可调 `allowThreads`、`allowReflection`、`allowAsm`、`enableEsmAuthoring` 等。 当前可写配置保存时会清理这三个已移除的执行预算键；旧路径兜底仍为只读，不改写旧文件。离线 Mixin 声明专用沙盒保留固定的 100,000 条语句限制，与普通执行无关。 所有现有引擎键以及独立的 Probe/AI 设置见[配置](configuration_cn)。
- **执行时长**：普通脚本执行已不设模块求值截止时间、语句预算或失控 watchdog。`scriptEvaluationTimeoutSeconds`、`scriptStatementLimit`、`scriptRunawayTimeoutSeconds` 已移除，没有替代性的默认超时。有限代码不会被这些预算中断；未完成的顶层 await 可以持续等待，访客/宿主无限循环可能阻塞所属线程或进程。生命周期取消、关闭/抢占与外部请求截止时间是独立机制，不保证能中断任意宿主工作。
- **HostAccess 边界**：按名黑名单只拦截 `Java.type` 一类的**类查找**；Java 方法返回值的对象图由 Graal `HostAccess` 控制（当前 `HostAccess.ALL`），黑名单类的实例仍可能经方法返回值进入脚本。

> 应将脚本及其依赖视为**受信任代码**。类查找过滤和文件系统检查不能使任意 Java 宿主调用变得安全，也没有自动 watchdog 来兜住无限循环。不要运行不受信任的第三方脚本，多人脚本分发时尤其如此。

<a id="wiki-section-14"></a>
## 顶层 await 与动态 import

```javascript
// JSON data-store 是同步 API，路径固定在 nekojs/data/ 下
JsonIO.write('profiles/default.json', { enabled: true })
const data = JsonIO.read('profiles/default.json')
console.info(data?.toPrettyString())

// 动态 import：按需加载
if (someCondition) {
  const mod = await import('./feature.js')
  mod.run()
}
```

<a id="wiki-section-15"></a>
## 实战：拆分多文件项目

```
nekojs/server_scripts/
├── lib/
│   ├── constants.js       // 共享常量
│   └── recipe_helpers.js  // 配方辅助函数
├── main.js                // 主入口
└── jsconfig.json
```

```javascript
// lib/constants.js
export const ORES = ['minecraft:iron_ore', 'minecraft:gold_ore']
```

```javascript
// lib/recipe_helpers.js
export function dustify(event, ore, output) {
  event.smelting(Item.of(output), Ingredient.of(ore)).id(`nekojs:smelt_${ore.split(':')[1]}`)
}
```

```javascript
// main.js
import { ORES } from './lib/constants.js'
import { dustify } from './lib/recipe_helpers.js'

ServerEvents.recipes(event => {
  for (const ore of ORES) {
    dustify(event, ore, ore.replace('_ore', '_ingot'))
  }
})
```

<a id="wiki-section-16"></a>
## 下一步

- [TypeScript 与 JSX](typescript-and-jsx_cn) —— 用 TS 写脚本。
- [Node.js 兼容](nodejs-compatibility_cn) —— `fs`/`path`/`buffer` 等可用模块。
- [全局绑定](global-bindings_cn) —— 顶层 API。

<!-- wiki-nav -->

---

[上一篇: 注册新内容](registering-new-content_cn) · [目录](Home) · [下一篇: TypeScript 与 JSX](typescript-and-jsx_cn)
