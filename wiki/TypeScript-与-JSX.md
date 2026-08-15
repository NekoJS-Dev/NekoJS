# TypeScript 与 JSX

NekoJS **本体内置** TypeScript 和 JSX 支持，无需额外编译步骤。

## TypeScript（`.ts`）

内置 TypeScript 前端。它擦除/降级 TS 语法，留下纯 JS 执行，不是完整的 TS 编译器——但支持的语法覆盖绝大多数实战场景。

### 支持的语法

| 语法 | 状态 |
|---|---|
| 类型注解 `let x: number` | 支持，擦除 |
| `type` / `interface` 声明 | 支持，擦除 |
| 泛型 `<T>`（类型位置） | 支持，擦除 |
| **泛型箭头函数 `<T>(x: T) => T`** | 支持，擦除 `<T>` |
| 联合/交叉类型 `A \| B`、`A & B` | 支持，擦除 |
| `as` / `satisfies` 断言（含 `as unknown as T` 链式） | 支持，擦除 |
| `import type` / `export type`（整条） | 支持，擦除 |
| **内联 `import { x, type T }`（TS 4.5+）** | 支持，只擦除 `type T`，保留 `x` |
| `declare` / `declare module` / `declare global` | 支持，擦除 |
| 类成员可见性修饰符 `public`/`private`/`protected`/`readonly`/`abstract`/`override` | 支持，剥除修饰符并保留 `static` |
| 参数属性 `constructor(public x: number)` | 支持，转为 `this.x = x` 赋值 |
| 函数重载签名 `function f(): T;` | 支持，擦除签名行 |
| `implements IFoo, IBar` | 支持，擦除 |
| 可选参数 `name?: T` | 支持，擦除 `?` 与类型 |
| 非空断言 `a!.x` / `a!` | 支持，擦除 `!` |
| 定值断言 `x!: T` / `x!;` | 支持，擦除 `!` 与类型 |
| **`enum` / `const enum`** | 支持，降级为运行时 IIFE 对象（数字双向映射 / 字符串单向 / 计算成员自增） |
| **`namespace` / `module`** | 支持，降级为 IIFE，`export` 成员转为 `name.member = member`（支持嵌套作用域、namespace 合并） |

### 不支持的语法

| 语法 | 原因 / 替代 |
|---|---|
| **装饰器 `@Decorator`** | NekoJS 是脚本引擎非 TS 框架，**不支持**装饰器。遇到 `@X` 会清晰报错。替代：用普通函数包装（`const Foo = withDecorator(class Foo {...})`）。 |
| 需要类型 emit 的高级特性（如基于类型系统的运行时反射） | 用普通 JS |

### 类型检查

TypeScript 前端只擦除不检查。要类型检查，在 `.js` 文件顶部加：

```javascript
// @ts-check
```

或在 `.ts` 文件里，IDE（VS Code）会用 `.neko_probe` 提供的类型声明做检查。

### 实战示例

```typescript
// server_scripts/typed_recipes.ts

interface MyRecipe {
  output: string
  inputs: string[]
}

function addRecipe(event: any, r: MyRecipe): void {
  event.shapeless(r.output, r.inputs)
}

ServerEvents.recipes((event: any) => {
  const recipes: MyRecipe[] = [
    { output: 'minecraft:dirt', inputs: ['minecraft:sand', 'minecraft:gravel'] },
    { output: 'minecraft:stick', inputs: ['minecraft:bamboo'] }
  ]
  for (const r of recipes) {
    addRecipe(event, r)
  }
})
```

```typescript
// 共享类型与模块
// lib/types.ts
export interface Config {
  version: number
  items: string[]
}

// main.ts
import type { Config } from './lib/types.ts'
import { readFileSync } from 'node:fs'

const config: Config = JSON.parse(readFileSync('./config.json', 'utf8'))
console.info(`加载配置 v${config.version}`)
```

## JSX / TSX（`.jsx` / `.tsx`）

NekoJS 内置 JSX lowering，默认 **classic runtime**：JSX 元素重写为 `globalThis.__nekoJsxFactory(type, props, ...children)`，片段用 `globalThis.__nekoJsxFragment(...children)`。

### 支持的 JSX 特性

| 特性 | 说明 |
|---|---|
| 元素 `<div>...</div>`、自闭合 `<br/>`、片段 `<>...</>` | 支持 |
| 字符串/布尔/表达式属性、spread 属性 `{...obj}` | 支持 |
| 文本子节点、表达式子节点 `{expr}`、嵌套元素 | 支持 |
| 大写组件 `<Foo/>`（作引用）、小写 `<div/>`（作字符串）、成员表达式 `<Foo.Bar/>` | 支持 |
| **HTML 实体解码**（`&amp;` 转成 `&`、`&lt;` 转成 `<`、`&#39;` 转成 `'`、`&quot;` 转成 `"`，以及数字/十六进制实体） | 支持在元素文本内解码；字符串属性值原样保留 |
| **命名空间标签** `<svg:rect/>` | 支持，名称整段透传给 factory |
| **泛型组件** `<Foo<number>/>`（TSX） | 支持，JSX 层透传 `Foo<number>`，TS 擦除阶段去掉 `<number>` |
| 表达式内的嵌套 JSX（`{cond && <X/>}`、`{arr.map(x => <X/>)}`） | 支持 |

### classic runtime（默认）

```javascript
// 你需要先定义 jsx factory（或在客户端渲染场景里用它）
globalThis.__nekoJsxFactory = (type, props, ...children) => {
  // 你的 JSX 元素处理器
  return { type, props, children }
}
globalThis.__nekoJsxFragment = (...children) => children
```

```jsx
// client_scripts/ui.jsx
const element = (
  <div className="container">
    <h1>标题 &amp; 副标题</h1>   {/* 文本里 &amp; 解码成 & */}
    <p>内容</p>
  </div>
)
```

### 自动 runtime（可选）

在 `config/nekojs-engine.toml`（游戏根 config 目录）里设：

```toml
jsxAutomaticRuntime = true
```

切换后使用标准 automatic runtime：编译器会按需从 `nekojs/jsx-runtime` 导入 `jsx`、`jsxs` 和 `Fragment`。零或一个子节点调用 `jsx(type, props[, key])`；多个子节点调用 `jsxs(type, { ...props, children: [...] }[, key])`；片段将 `Fragment` 作为 type 传入。所有子节点都位于 `props.children`，不会再以 rest 参数传给 factory。

在 `nekojs/` 工作区内，你需要在裸模块路径 `node_modules/nekojs/jsx-runtime.js` 放一个 runtime 模块：

```javascript
// node_modules/nekojs/jsx-runtime.js
export function jsx(type, props, key) {
  return { type, key, props, children: props.children }
}

export function jsxs(type, props, key) {
  return { type, key, props, children: props.children }
}

export const Fragment = Symbol.for('nekojs.fragment')
```

> 自动 runtime 适合想从 React 生态借用代码风格的场景；classic runtime 更轻量（只需在 `globalThis` 挂 factory），但两者的调用约定不同。按需选择。

> JSX 主要用于**需要树状数据结构**的场景（比如自定义 GUI 描述、渲染配置）。Minecraft 没有内置的 React-like 渲染层，factory 的实现由你的脚本决定。

## 配置

`jsconfig.json` 自动生成，jsx 模式**跟随引擎配置**（`config/nekojs-engine.toml` 的 `jsxAutomaticRuntime`）：关闭（默认）时使用 classic runtime，关键字段：

| 字段 | 值 |
|---|---|
| `module` | `ESNext` |
| `jsx` | `react` |
| `jsxFactory` | `__nekoJsxFactory` |
| `jsxFragmentFactory` | `__nekoJsxFragment` |

开启 `jsxAutomaticRuntime = true` 时，生成与每次 probe 合并都会把 jsx 键**双向纠正**为 automatic 模式：`"jsx": "react-jsx"` + `"jsxImportSource": "nekojs"`（TS 会自动在其后拼接 `/jsx-runtime`），并移除两个 factory 键；关闭时反向写回 factory 键。jsx 运行时键视为**引擎拥有键**——手动修改它们只会得到与运行时不符的诊断。

自动 runtime 需要你提供 `nekojs/node_modules/nekojs/jsx-runtime.js` 模块（见前文）；`JSConfigModel#useAutomaticJsxRuntime()` 为插件或外部 workspace 生成器提供相同设置。

详见 [Probe 类型生成](Probe-类型生成)。

## 限制与边界

- **可擦除 TS ≠ 完整 TS**。本体的 TS 前端是有意轻量的，避免捆绑重的 TS 依赖。装饰器和其它没有 NekoJS lowering 语义、需要类型 emit 的特性会被拒，错误信息会引导你换写法；`enum` 与 `namespace` 是已支持的 NekoJS lowering 扩展。
- TS 的**类型不参与运行时行为**——擦除后就是普通 JS。
- 后续高级 TS/TSX/JSX 语法会优先在本体语言前端中补齐（见 ROADMAP）。

## 下一步

- [模块系统](模块系统) —— ESM/CJS/java: 导入。
- [全局绑定](全局绑定) —— 配合 TS 类型声明用。
- [Node.js 兼容](Node-js-兼容)。
