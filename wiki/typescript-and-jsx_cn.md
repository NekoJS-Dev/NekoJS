<!-- wiki-page: typescript-and-jsx; locale: cn -->

> **中文** · [English](typescript-and-jsx_us)

<a id="wiki-section-1"></a>
# TypeScript 与 JSX

脚本可以直接用 TypeScript（`.ts`）和 JSX（`.jsx`、`.tsx`）来写。把文件放进脚本目录就能运行，不需要安装 TypeScript，也不需要自己编译。

<a id="wiki-section-2"></a>
## TypeScript

新建 `nekojs/server_scripts/typed_recipes.ts`：

```typescript
interface MyRecipe {
  output: string
  inputs: string[]
}

const recipes: MyRecipe[] = [
  { output: 'minecraft:dirt', inputs: ['minecraft:sand', 'minecraft:gravel'] },
  { output: 'minecraft:stick', inputs: ['minecraft:bamboo'] }
]

ServerEvents.recipes(event => {
  for (const r of recipes) {
    event.shapeless(r.output, r.inputs)
  }
})
```

执行 `/nekojs reload` 生效，和 `.js` 文件一样。

**NekoJS 不做类型检查。** 它只是把类型标注去掉，然后当普通 JavaScript 运行。类型写错了，脚本照样会运行。想检查类型，要靠编辑器：先执行 `/nekojs probe`，再用 VS Code 打开脚本目录，类型错误会在编辑器里标红。

<a id="wiki-section-3"></a>
### 支持的写法

| 写法 | 运行时怎么处理 |
|---|---|
| 类型标注、`type`、`interface`、泛型、联合类型 | 去掉 |
| `as`、`satisfies`、非空断言 `x!` | 去掉 |
| `import type`、`export type`、`import { x, type T }` | 去掉类型部分，保留普通导入 |
| `declare`、`declare module`、`declare global` | 去掉 |
| 可选参数 `name?: T`、函数重载签名、`implements` | 去掉 |
| `public`、`private`、`protected`、`readonly`、`abstract`、`override` | 去掉修饰符，保留 `static` |
| 参数属性 `constructor(public x: number)` | 转成 `this.x = x` |
| `enum`、`const enum` | 转成普通对象 |
| `namespace` | 转成普通对象 |

<a id="wiki-section-4"></a>
### 不支持的写法

**装饰器（`@Decorator`）不支持**，会报错。改用普通函数包一层：

```typescript
// 不支持：
// @register
// class Foo {}

// 改成：
const Foo = register(class Foo {})
```

需要运行时类型信息的功能（例如根据类型做反射）也没有。类型在运行时完全不存在。

<a id="wiki-section-5"></a>
### 共享类型

类型可以放在单独的文件里，用 `import type` 引入：

```typescript
// lib/types.ts
export interface ModConfig {
  version: number
  items: string[]
}
```

```typescript
// main.ts
import type { ModConfig } from './lib/types.ts'

const config: ModConfig = { version: 1, items: ['minecraft:stone'] }
console.info(`配置版本 ${config.version}`)
```

多文件的写法见 [模块系统](module-system_cn)。

<a id="wiki-section-6"></a>
### 补全里的 Java 类型

`/nekojs probe` 生成的类型里，有几个地方和普通 TypeScript 习惯不同：

- Java 的 `Object` 显示成 `unknown`。用之前要先判断类型，例如 `if (typeof value === 'string')`。
- 参数类型是 `List`、`Collection`、`Iterable` 时，可以直接传 JavaScript 数组。
- 参数类型是 `Set` 时，不能传数组，要传一个真正的 Java `Set`。

<a id="wiki-section-7"></a>
## JSX

JSX 是在 JavaScript 里写类似 HTML 标签的语法。`.jsx` 和 `.tsx` 文件里可以用：

```jsx
// client_scripts/tree.jsx
const tree = (
  <panel title="状态">
    <label text="生命值" />
    <label text="饥饿值" />
  </panel>
)
console.info(tree.type)                // panel
console.info(tree.props.title)         // 状态
console.info(tree.children.length)     // 2
```

**JSX 本身只生成一棵数据树，不会显示任何界面。** 要在 Minecraft 里做出能交互的界面，请看 [JSX 客户端 UI](jsx-client-ui_cn)（仅 NeoForge 26.x）。

支持常见的 JSX 写法：元素、自闭合标签、片段 `<>...</>`、属性展开 `{...props}`、表达式 `{x}`、`{cond && <A/>}`、`{list.map(...)}`、组件 `<Foo/>`、`<Foo.Bar/>`。文本里的 `&amp;`、`&lt;` 之类的 HTML 实体会被解码。

<a id="wiki-section-8"></a>
### 默认方式（classic）

默认情况下，每个 JSX 元素会被编译成一次 `globalThis.__nekoJsxFactory(type, props, ...children)` 调用，片段编译成 `globalThis.__nekoJsxFragment(...children)`。

NekoJS 已经提供了这两个函数，返回上面例子里那样的对象（有 `type`、`props`、`children`）。想要别的结构，可以自己替换：

```javascript
globalThis.__nekoJsxFactory = (type, props, ...children) => ({ type, props, children })
globalThis.__nekoJsxFragment = (...children) => children
```

<a id="wiki-section-9"></a>
### automatic 方式（可选）

在 `nekojs/config/engine.toml` 里设置：

```toml
jsxAutomaticRuntime = true
```

重启游戏，再执行一次 `/nekojs probe` 更新编辑器配置。

开启后，JSX 会从内置模块 `nekojs/jsx-runtime` 导入 `jsx`、`jsxs` 和 `Fragment`，写法和 React 17 之后的 JSX 一样。这个模块随 NekoJS 提供，不需要往 `node_modules` 里放任何东西。[JSX 客户端 UI](jsx-client-ui_cn) 用的就是这个模块。

两种方式的区别：

| | classic（默认） | automatic |
|---|---|---|
| 编译成 | `__nekoJsxFactory(...)` | `jsx(...)` / `jsxs(...)` |
| 子元素 | 作为额外参数传入 | 放在 `props.children` 里 |
| `key` 属性 | 留在 props 里 | 单独作为第三个参数 |
| 能否自定义 | 替换两个全局函数即可 | 使用内置模块，不能替换 |

<a id="wiki-section-10"></a>
## 编辑器设置

`/nekojs probe` 会在脚本目录生成 `jsconfig.json`，让 VS Code 认识 TypeScript 和 JSX。JSX 相关设置跟随 `jsxAutomaticRuntime`：

| 设置 | classic（默认） | automatic |
|---|---|---|
| `jsx` | `react` | `react-jsx` |
| `jsxFactory` / `jsxFragmentFactory` | `__nekoJsxFactory` / `__nekoJsxFragment` | 不设置 |
| `jsxImportSource` | 不设置 | `nekojs` |

这几项由 NekoJS 管理。手动改了也会在下次 probe 时被改回来。

想让 `.js` 文件也检查类型，在文件第一行写：

```javascript
// @ts-check
```

补全和 probe 的更多说明见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-11"></a>
## 接下来

- [JSX 客户端 UI](jsx-client-ui_cn)：用 JSX 做游戏内界面（NeoForge 26.x）。
- [模块系统](module-system_cn)：多文件、npm 包、`java:` 导入。
- [全局绑定](global-bindings_cn)：可以直接使用的工具。

<!-- wiki-nav -->

---

[上一篇: 模块系统](module-system_cn) · [目录](Home) · [下一篇: JSX 客户端 UI](jsx-client-ui_cn)
