<!-- wiki-page: typescript-and-jsx; locale: us -->

> **English** · [中文](typescript-and-jsx_cn)

<a id="wiki-section-1"></a>
# TypeScript and JSX

NekoJS has **built-in** TypeScript and JSX support, with no additional compilation step.

<a id="wiki-section-2"></a>
## TypeScript (`.ts`)

The built-in TypeScript frontend erases/lowers TS syntax, leaving plain JS for execution. It is not a full TS compiler, but the supported syntax covers most practical cases.

<a id="wiki-section-3"></a>
### Supported syntax

| Syntax | Status |
|---|---|
| Type annotations `let x: number` | Supported, erased |
| `type` / `interface` declarations | Supported, erased |
| Generics `<T>` (in type positions) | Supported, erased |
| **Generic arrow functions `<T>(x: T) => T`** | Supported, `<T>` erased |
| Union/intersection types `A \| B`, `A & B` | Supported, erased |
| `as` / `satisfies` assertions (including chains such as `as unknown as T`) | Supported, erased |
| `import type` / `export type` (entire statement) | Supported, erased |
| **Inline `import { x, type T }` (TS 4.5+)** | Supported, erases only `type T` and retains `x` |
| `declare` / `declare module` / `declare global` | Supported, erased |
| Class member visibility modifiers `public`/`private`/`protected`/`readonly`/`abstract`/`override` | Supported, modifiers stripped and `static` retained |
| Parameter properties `constructor(public x: number)` | Supported, converted into `this.x = x` assignments |
| Function overload signatures `function f(): T;` | Supported, signature lines erased |
| `implements IFoo, IBar` | Supported, erased |
| Optional parameters `name?: T` | Supported, `?` and type erased |
| Non-null assertions `a!.x` / `a!` | Supported, `!` erased |
| Definite assignment assertions `x!: T` / `x!;` | Supported, `!` and type erased |
| **`enum` / `const enum`** | Supported, lowered to runtime IIFE objects (two-way numeric mapping / one-way string mapping / auto-incrementing computed members) |
| **`namespace` / `module`** | Supported, lowered to IIFEs; `export` members become `name.member = member` (nested scopes and namespace merging supported) |

<a id="wiki-section-4"></a>
### Unsupported syntax

| Syntax | Reason / alternative |
|---|---|
| **Decorators `@Decorator`** | NekoJS is a scripting engine, not a TS framework; decorators are **not supported**. Encountering `@X` produces an explicit error. Alternative: wrap with an ordinary function (`const Foo = withDecorator(class Foo {...})`). |
| Advanced features requiring type-based emit (such as runtime reflection based on the type system) | Use ordinary JS |

<a id="wiki-section-5"></a>
### Type checking

The TypeScript frontend erases types but does not check them. To enable type checking, add this at the top of a `.js` file:

```javascript
// @ts-check
```

Or use a `.ts` file, where the IDE (VS Code) checks against the type declarations provided by `.neko_probe`.

<a id="wiki-section-6"></a>
### Practical examples

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
// Shared types and modules
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

<a id="wiki-section-7"></a>
## JSX / TSX (`.jsx` / `.tsx`)

NekoJS includes JSX lowering and defaults to the **classic runtime**: JSX elements are rewritten as `globalThis.__nekoJsxFactory(type, props, ...children)`, and fragments use `globalThis.__nekoJsxFragment(...children)`.

<a id="wiki-section-8"></a>
### Supported JSX features

| Feature | Description |
|---|---|
| Elements `<div>...</div>`, self-closing elements `<br/>`, fragments `<>...</>` | Supported |
| String/boolean/expression attributes, spread attributes `{...obj}` | Supported |
| Text children, expression children `{expr}`, nested elements | Supported |
| Uppercase components `<Foo/>` (as references), lowercase `<div/>` (as strings), member expressions `<Foo.Bar/>` | Supported |
| **HTML entity decoding** (`&amp;` becomes `&`, `&lt;` becomes `<`, `&#39;` becomes `'`, `&quot;` becomes `"`, plus numeric/hexadecimal entities) | Decoded in element text; string attribute values are retained unchanged |
| **Namespaced tags** `<svg:rect/>` | Supported, the entire name is passed through to the factory |
| **Generic components** `<Foo<number>/>` (TSX) | Supported, the JSX layer passes through `Foo<number>` and TS erasure removes `<number>` |
| Nested JSX inside expressions (`{cond && <X/>}`, `{arr.map(x => <X/>)}`) | Supported |

<a id="wiki-section-9"></a>
### Classic runtime (default)

```javascript
// Define a jsx factory first (or use it in a client rendering scenario)
globalThis.__nekoJsxFactory = (type, props, ...children) => {
  // Your JSX element handler
  return { type, props, children }
}
globalThis.__nekoJsxFragment = (...children) => children
```

```jsx
// client_scripts/ui.jsx
const element = (
  <div className="container">
    <h1>标题 &amp; 副标题</h1>   {/* &amp; in text is decoded to & */}
    <p>内容</p>
  </div>
)
```

<a id="wiki-section-10"></a>
### Automatic runtime (optional)

Set this in `nekojs/config/engine.toml`:

```toml
jsxAutomaticRuntime = true
```

When enabled, the compiler imports `jsx`, `jsxs`, and `Fragment` from the built-in `nekojs/jsx-runtime` module. This module ships with NekoJS; users do not need to place a file of the same name in `nekojs/node_modules/`. A custom file with that name does not override the built-in module either.

```jsx
// client_scripts/ui.jsx
const element = <Panel title="状态">内容</Panel>
```

The automatic runtime puts JSX children in `props.children`: zero or one child uses `jsx`, multiple children use `jsxs`, and fragments use `Fragment`. If you need fully custom rendering behavior, use the default classic runtime and provide `globalThis.__nekoJsxFactory` / `globalThis.__nekoJsxFragment`.

> JSX itself handles only syntax lowering and VNode generation. To create an interactive Minecraft Screen, use [JSX client UI](jsx-client-ui_us). Ordinary JSX can still describe custom data trees or rendering configuration; it does not create a Screen automatically.

<a id="wiki-section-11"></a>
## Configuration

`jsconfig.json` is generated automatically, and JSX mode **follows engine configuration** (`jsxAutomaticRuntime` in `nekojs/config/engine.toml`): when disabled (the default), it uses the classic runtime with these key fields:

| Field | Value |
|---|---|
| `module` | `ESNext` |
| `jsx` | `react` |
| `jsxFactory` | `__nekoJsxFactory` |
| `jsxFragmentFactory` | `__nekoJsxFragment` |

The JSX mode in `jsconfig.json` follows `engine.toml`: when disabled (the default), it uses the classic runtime with `jsx: "react"`, `jsxFactory`, and `jsxFragmentFactory`; when enabled, it uses `jsx: "react-jsx"` and `jsxImportSource: "nekojs"`, and removes the two classic factory keys. WorkspaceGenerator writes this configuration on first generation, and probe subsequently reconciles the engine-managed JSX keys. Manual changes to these keys are corrected on the next generation.

The automatic runtime uses NekoJS's built-in `nekojs/jsx-runtime`; users do not need to create `node_modules/nekojs/jsx-runtime.js`. Plugins or external workspace generators can call `JSConfigModel#useAutomaticJsxRuntime()` to generate the same configuration.

See [Probe type generation](probe-type-generation_us).

<a id="wiki-section-12"></a>
## Limits and scope

- **Erasable TS is not full TS**. The built-in TS frontend is intentionally lightweight to avoid bundling a large TS dependency. Decorators and other features without NekoJS lowering semantics that require type-based emit are rejected, with error messages guiding you toward alternatives. `enum` and `namespace` are supported NekoJS lowering extensions.
- TS **types do not participate in runtime behavior**: after erasure, the code is ordinary JS.
- More advanced TS/TSX/JSX syntax will continue to be added to the built-in language frontend; switching to an external compiler dependency is not planned.

<a id="wiki-section-13"></a>
## Next steps

- [JSX client UI](jsx-client-ui_us) - Creating NeoForge 26.x client Screens with JSX.
- [Module system](module-system_us) - ESM/CJS/java: imports.
- [Global bindings](global-bindings_us) - Using TS type declarations with bindings.
- [Node.js compatibility](nodejs-compatibility_us).

<!-- wiki-nav -->

---

[Previous: Module system](module-system_us) · [Contents](Home) · [Next: JSX client UI](jsx-client-ui_us)
