# 票据 12 迁移材料：TS/JSX/TSX 编译、source map 与执行行为路径（2026-09-19）

> 范围：语言模块管线 W4（TypeScript 擦除、JSX element/fragment、automatic runtime、
> TSX 组合、prepared 身份、准备/执行期 source location、cache 失效）。
> 脚本侧无 breaking；作者可见变化只有两个：`.tsx` 现在有独立 language id `tsx`，
> 以及 TS/JSX/TSX 的错误位置更精确。Java 侧新增一个语言插件与两个内部 API。

## 1. 脚本作者：无须迁移

`.ts`（类型擦除）、`.jsx`（JSX 下降）、`.tsx`（擦除 + 下降）的既有语义全部保留：

- 类型注解、`interface`/`type` 声明、泛型、`as` 断言、非空断言、class 成员修饰符、
  参数属性、`enum`、`namespace` 的运行时结果与 12 号票之前一致（`enum` 数值成员照常
  产出双向映射，`namespace` 照常产出 IIFE）。
- JSX classic runtime（默认）与 automatic runtime（`jsxAutomaticRuntime = true`）的
  element、fragment、`key`、children 归一化行为不变；automatic runtime 仍从
  `nekojs/jsx-runtime` 导入 `jsx`/`jsxs`/`Fragment`。
- `require`/`import` 在 TS/JSX/TSX 与 JS/CJS/ESM 之间混用保持既有解析与身份语义。

作者可见的两点变化：

1. **`.tsx` 的语言身份**：`prepared.languageId()` 由 `jsx` 变为 `tsx`。依赖 language id
   做分支的插件/工具应同时接受 `jsx` 与 `tsx`（两者的转换管线是同一个 `NekoJsxLexer`）。
   既有 `.jsx` 仍报 `jsx`，`.ts` 仍报 `typescript`。
2. **错误位置**：prepare 期与执行期错误的 authored 行/列现在是可观察字段，且不再
   把生成代码行当作作者行。

## 2. 最小可运行示例（交付物正本：`common/src/test/resources/nekojs/language-ts-examples/`）

示例只使用已过 gate 的语言能力；`TypeScriptJsxExamplesSmokeTest` 逐项执行并断言下述输出。

**TS（ESM，`ts/hello.ts` + `ts/greet.ts`）**

`greet.ts`:

```ts
export interface Greeting {
    message: string;
}

export function greet(name: string): Greeting {
    const formatted: string = 'hello, ' + name + '!';
    return { message: formatted } as Greeting;
}

export const TAG: string = 'ts';
```

`hello.ts`（入口）：

```ts
// 最小 TS 示例（ESM）：接口/类型注解/泛型/class 类型不进入运行时，
// 值、控制流与导出形状与等价 JS 完全一致（类型擦除语义）。
import { greet, TAG, type Greeting } from './greet.ts';

interface Box<T> {
    value: T;
}

const box: Box<number> = { value: 20 + 22 };
const greeting: Greeting = greet('neko');

export const message: string = greeting.message;
export const tag: string = TAG;
export const answer: number = box.value;
```

期望：`message === 'hello, neko!'`、`tag === 'ts'`、`answer === 42`。

**JSX（CommonJS，`jsx/hello.jsx` + `jsx/greet.jsx`）**

`greet.jsx`:

```jsx
export function tag() {
    return <span id="tag">jsx</span>;
}

export function empty() {
    return <></>;
}
```

`hello.jsx`（入口）：

```jsx
const { tag, empty } = require('./greet.jsx');

const view = <div id="root">{tag()}</div>;

module.exports = {
    rootTag: view.tag,
    rootId: view.props.id,
    childTag: view.children[0].tag,
    childText: view.children[0].props.children,
    fragmentChildren: empty().children.length
};
```

期望：`rootTag === 'div'`、`rootId === 'root'`、`childTag === 'span'`、
`childText === 'jsx'`、`fragmentChildren === 0`。

**TSX（ESM，`tsx/hello.tsx` + `tsx/greet.tsx`）**

`greet.tsx`:

```tsx
export interface LabelProps {
    text: string;
}

export function label(props: LabelProps) {
    return <li class="label">{props.text}</li>;
}
```

`hello.tsx`（入口）：

```tsx
import { label, type LabelProps } from './greet.tsx';

const props: LabelProps = { text: 'tsx' };
const names: string[] = ['a', 'b'];

const view = <ul id="list">{names.map((item: string) => <li>{item}</li>)}</ul>;
const single = label(props);

export const listId: string = view.props.id;
export const childCount: number = view.children.length;
export const labelText: string = single.props.children;
```

期望：`listId === 'list'`、`childCount === 2`、`labelText === 'tsx'`。

## 3. 语言身份、source map 与 cache key

| 扩展名 | `prepared.languageId()` | 默认 mode | prepared code | source map |
|---|---|---|---|---|
| `.ts` | `typescript` | AUTO（内容决定 CJS/ESM） | 类型擦除后的 JS | compiler 产物（含 `sourcesContent`） |
| `.jsx` | `jsx` | AUTO | JSX 下降后的 JS | compiler 产物 |
| `.tsx` | `tsx` | AUTO | JSX 下降 + 类型擦除后的 JS | compiler 产物 |
| `.mts`/`.cts` | — | ESM/COMMONJS | 未注册语言，不在本票范围 | — |

- `prepared.cacheKey()` 覆盖 source path、language id、mode、code 与实际 source map；
  内容、路径、语言身份、mode 任一变化即产生不同 key；未变化输入可观察命中。
  证据：`NekoTypeScriptJsxPrepareTest#contentLanguageAndModeChangesInvalidateTsJsxTsxEntries`、
  `#unchangedTsJsxTsxInputsHitTheSamePreparedIdentity`。
- 编译语言（TS/JSX/TSX）发布 compiler map；原生 JS/CJS/ESM 与无 map 的 legacy 输入仍由
  `NekoSourceMapBuilder.identity` 生成保守 map（票据 11 口径未变）。
  `NekoSourceMapBuilder` 仍是明确的 source-map utility，不是 parser/lowering/compiler SPI。

## 4. 错误位置与阶段归属

- **prepare 期**（擦除/下降失败）：`NekoModuleError` 带 `Stage.PREPARE`、
  `owner = Script Preparation` 与 authored `sourcePath/sourceLine/sourceColumn`。
  语言前端把索引位置放进 `NekoCompileException`；`NekoModulePipeline` 把它发布成错误字段
  而不是只留在消息文本里。
- **执行期**（guest 抛错）：`Stage.EXECUTE`、`owner = Script Execution Environment`；
  host 先用 prepared/virtual source map 把生成位置解析成 authored 位置，再写进错误字段。
  CommonJS 的 `new Function` 包装会引入合成头部行，loader 自测该偏移并交给 host，
  因此报告的行是 prepared module 的行，而不是包装后的行。
- **非法库无法定位时**：host 不再把生成位置或 Java 栈帧当作 authored 位置；这些字段保持
  `-1`，而不是编造一个假位置。

## 5. Java 调用点迁移

| 变更 | 说明 |
|---|---|
| 新增 `com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin`（`.tsx`） | 由 `NekoCommonBuiltinPlugin` 注册；`NekoJsxLanguagePlugin` 现在只声明 `.jsx`。两者共用 `NekoJsxLexer`，TSX 仍是同一条转换管线，没有第二套实现 |
| 新增 `com.tkisor.nekojs.core.compiler.NekoCompileException` | 语言前端的 authored 行列载体；继承 `IllegalArgumentException` 并保持既有消息文本，旧的 message 断言不受影响 |
| `NekoModuleError.prepare(..., sourceLine, sourceColumn, ...)` 新重载 | 显式位置变体；旧无位置重载保持不变 |
| `NekoScriptModuleLoaderHost.configure(...)` 新五参重载 | 接收 loader 实测的 CommonJS 包装行偏移；旧四参重载保留（偏移 0） |
| `common/src/main/resources/nekojs/node/internal/script-loader.js` | 新增 `bodyLineOffset()` 自测包装偏移并随 `configure` 上报 |

需要显式注册 TS/JSX/TSX 语言插件的测试/工具，应补上 `NekoTsxLanguagePlugin.INSTANCE`
（生产由 `NekoCommonBuiltinPlugin` 自动注册）。

## 6. 未做事项（非本票范围）

- 不引入外部转译依赖、不新增公共 parser SPI、不新增 Gradle project、不建第二套 TS 管线；
  语言语义仍只在既有 compiler/lexer 层，resolver/cache/host 不复制擦除或 JSX 转换。
- 未更新任何 golden；`:common:check` 全绿即交付证据。
- 未运行真实 Minecraft client/server、loader runtime 或 network smoke；本材料只声称
  common JUnit 与平台编译证据。
- 已知边界：literal dynamic import 到一个**失败的 CommonJS 子模块**时，Graal 的
  promise/host 边界丢失了子模块身份，错误归到引用方且不带行列（不编造位置）；
  同步 interop 路径（`#cjs-interop`）保留子模块身份，见 `NekoTypeScriptJsxRuntimeTest`。
- `.mts`/`.cts` 仍不在已注册语言范围内（`identify` 能得到 mode，但 prepare 会报
  “No script compiler registered”）；既有行为不变，本票不扩大语言范围。
