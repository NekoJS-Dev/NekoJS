# 工单 12 迁移材料：TS / JSX / TSX

> 面向脚本作者与插件作者。本票没有删除任何公开语言，也没有改变任何既定写法；
> 下面**只有一条**需要作者配合的行为变化，其余是澄清与身份说明。

## 1. 唯一的行为变化：未终止的类型构造现在会报错（fail closed）

**旧行为（缺陷）**：未闭合的 `interface` / `type` / 泛型类型标注会被静默擦到文件末尾，
作者写在后面的代码凭空消失且没有任何提示。例如：

```ts
interface Foo {
  bar: string;

SIDE_EFFECT();     // ← 旧行为：连同 const x 一起被擦成空白，无任何报错
const x = 1;
```

**新行为**：在**准备阶段**抛出带文件与行列的 `IllegalArgumentException`：

```
Unterminated TypeScript type construct in <file> at 1:1.
The type body never closes, so the eraser cannot tell which code belongs to the declaration
and which is executable script. ...
```

**作者要做什么**：把没有写完的类型补完（或删掉）。错误消息已经给出确切的行列。

现在会 fail closed 的形态：

| 形态 | 示例 |
|---|---|
| 未闭合的 `interface` 体 | `interface Foo {\n  bar: string;\n` |
| 未闭合的 `type` 对象体 | `type T = {\n  a: string;\n` |
| 未闭合的 `export interface` / `declare class` 体 | `export interface I {\n` / `declare class C {\n` |
| 未闭合的泛型参数列表 | `const x: Array<string = SIDE_EFFECT();` |
| 未闭合的 `implements` 子句 | `class C implements Foo<Bar {\n` |

**仍然合法、行为不变**（本票特意钉住这些边界，不许过度收紧）：

```ts
interface Foo { bar: string }        // 合法 interface
type T = string                       // 合法类型别名
type T = { a: string }                // 合法对象类型
function id<T>(x: T): T { return x }  // 泛型函数
const id = <T,>(x: T) => x            // 泛型箭头
const m: Map<string, number> = ...    // 泛型实参
let x: number                         // 文件末尾的类型标注
```

## 2. 澄清：`.tsx` 的 language id 是 `jsx`

`.jsx` 与 `.tsx` 由**同一个** `NekoJsxLanguagePlugin` 拥有，语言 id 都是 `jsx`。
TSX 是「JSX lowering + TS 擦除」的组合形态，不是第二种语言。

旧实现里 `.tsx` 会同时报出 `jsx`（prepared module）与 `tsx`（IR）两个 id——同一文件两种身份，
已修复。**对作者的影响**：只有在读取 `language id`（例如按语言过滤诊断或写插件）时才可见；
脚本的写法、module mode、可执行行为都没有变化。

## 3. 澄清：转译语言的 source map 现在也覆盖 ESM import 链

`import` 一个 `.ts`/`.jsx`/`.tsx` 时抛出的执行期异常，现在会经 source map 回到
**原始文件与原始行**（此前经 ESM virtual module 执行时会落到 virtual id 上而丢失原始位置）。
原生 JS 不受影响（其 source map 语义是恒等映射）。

## 4. 不需要改的东西

- `.ts` / `.jsx` / `.tsx` 全部保留，扩展名、module mode、JSX runtime 选择均未变。
- classic runtime（默认）仍是 `globalThis.__nekoJsxFactory` / `__nekoJsxFragment`；
  `jsxAutomaticRuntime = true` 时仍是 `import { jsx, jsxs, Fragment } from 'nekojs/jsx-runtime'`。
- 装饰器（`@Decorator`）仍然明确报错——NekoJS 是脚本引擎，不是 TS 框架。
- 未新增任何转译依赖、公共 parser SPI 或 Gradle project。

## 5. 示例

- `minimal-example.ts`：可擦除 TypeScript（interface / type / 泛型 / 类型标注）
- `minimal-example.jsx`：classic runtime 的 element 与 fragment
- `minimal-example.tsx`：TSX = JSX lowering + TS 擦除
- `minimal-example-entry.mjs`：同一个 ESM 入口混合 import 上述三者

示例被 `NekoTranspiledMinimalExampleTest` **真实执行**：示例与实现漂移即测试失败。
