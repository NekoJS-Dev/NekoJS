<!-- wiki-page: probe-type-generation; locale: cn -->

> **中文** · [English](probe-type-generation_us)

<a id="wiki-section-1"></a>
# Probe 类型生成

Probe 是 NekoJS 内置的类型声明生成器，作用相当于 ProbeJS。它读取当前游戏里实际加载的 Java 类、事件、绑定和注册表，生成 TypeScript 的 `.d.ts` 和 Python 的 `.pyi`，并配置好编辑器。这样 VS Code 就能补全事件名、方法和物品 ID。

本页前半部分写给脚本作者：命令、配置、编辑器设置和常见问题。后半部分写给插件作者和贡献者：生成流程、数据来源和自定义 backend。

<a id="wiki-section-2"></a>
## 快速开始

1. 进入世界，执行 `/nekojs probe`。
2. 用 VS Code 打开一个脚本目录，例如 `nekojs/server_scripts`。
3. 打开一个 `.js` 或 `.ts` 文件，输入 `ServerEvents.`，应该能看到补全列表。

生成的文件在游戏目录下的 `.neko_probe/`，和 `nekojs/` 在同一层。不要手动修改它们，下次生成会覆盖。

装了新模组、注册了新物品之后，再执行一次 `/nekojs probe`。声明只反映执行命令那一刻的游戏状态。

用 Python 写脚本时，改用 `/nekojs probe python`，见下文“Python”一节。

<a id="wiki-section-3"></a>
## 命令

所有 probe 命令都需要管理员权限（单人游戏里打开作弊即可）。语言名和 backend 名都可以按 TAB 补全。

| 命令 | 作用 |
|---|---|
| `/nekojs probe` | 生成 TypeScript 声明。日常只需要这一条 |
| `/nekojs probe python` | 生成 Python 声明 |
| `/nekojs probe all` | 运行所有已注册的 backend，包括插件提供的 |
| `/nekojs probe <语言>` | 运行该语言的默认 backend |
| `/nekojs probe <语言> <名字>` | 运行指定 backend，例如 `/nekojs probe typescript builtin` |
| `/nekojs probe list` | 列出已注册的 backend |
| `/nekojs probe reload` | 重新读取 `probe.toml`，改完配置不用重启 |
| `/nekojs probe enable` / `disable` | 打开或关闭 probe，会写回 `probe.toml` |
| `/nekojs probe reset_config` | 编辑器配置被改乱时，恢复默认配置并重新生成 |

想每次开服都自动生成，在 `probe.toml` 里设置 `runAtStartup = true`。生成失败只写日志，不影响开服。

<a id="wiki-section-4"></a>
## 编辑器设置

<a id="wiki-section-5"></a>
### TypeScript 和 JavaScript

Probe 会自动维护每个脚本目录下的 `jsconfig.json`，你不需要手写。它会：

- 把 `java:*`、`@side-only/...`、`@special` 等模块名映射到 `.neko_probe/typescript/`；
- 让每种脚本只看到自己能用的事件和绑定，例如 `server_scripts` 里看不到客户端事件；
- 保留你自己加的配置。你手动加的 `include`、`typeRoots` 或其他选项不会被覆盖。

用 VS Code 打开**某个脚本目录**，例如 `nekojs/server_scripts`，补全最准确。打开整个游戏目录也能用，但不同脚本类型的声明会混在一起。

在 `.js` 文件顶部加 `// @ts-check`，VS Code 会提示类型错误。NekoJS 运行时不做类型检查，这只是编辑器提示。

<a id="wiki-section-6"></a>
### Python

1. 执行 `/nekojs probe python`。只执行 `/nekojs probe` 不会生成 Python 声明。
2. 在每个 `.py` 文件顶部写：

```python
from nekojs import *
```

这一行只给编辑器看。NekoJS 运行脚本时会把它删掉。

3. 在 VS Code 里装 Pylance 扩展。
4. 用 VS Code 直接打开游戏目录、`nekojs/` 或某个脚本目录，不要打开它们的上层文件夹。Pylance 只读取工作区根目录的配置。

Probe 会在这些目录写入 `pyrightconfig.json` 和 `.vscode/settings.json`，把 `.neko_probe/python` 加进搜索路径。如果你没有自己设置 `python.languageServer`，它会被设为 `"Pylance"`。

Python 声明比 TypeScript 粗略：泛型都变成 `Any`，复杂的对象形状变成 `dict[str, Any]`。日常补全够用。详见 [Python 脚本](python-scripts_cn)。

<a id="wiki-section-7"></a>
## 在脚本里使用生成的类型

大多数时候你不用显式导入任何东西：全局绑定和事件参数的类型都已经有了。需要给自己的函数写类型时，可以导入：

```typescript
import type { $ItemStack, $ItemStack_ } from 'java:net/minecraft/world/item'
import type { RegistryTypes } from '@special/types'

function describe(stack: $ItemStack): string {
  return stack.getHoverName().getString()
}

const id: RegistryTypes.Item = 'minecraft:diamond'
```

几个命名规则：

- `$ItemStack`：Java 类的声明。加 `$` 前缀是为了不和 JavaScript 里的名字冲突。
- `$ItemStack_`：这个类型**能接受的输入**，例如 `'minecraft:stone'` 这样的字符串也能当物品栈传。只有 NekoJS 真的能转换的类型才有这个别名。
- `RegistryTypes.Item`：所有物品 ID 的字面量。`RegistryTypes.ItemTag` 是物品标签，`RegistryTypes.Namespace` 是所有命名空间。
- `java:` 导入的写法见 [模块系统](module-system_cn)。

`import type` 只在编辑器里起作用，运行时会被删掉。

<a id="wiki-section-8"></a>
## probe.toml

配置文件在 `nekojs/config/probe.toml`，第一次运行时自动生成，带注释。改完执行 `/nekojs probe reload` 再重新生成即可。

| 键 | 默认值 | 作用 |
|---|---|---|
| `enabled` | `true` | 总开关。关闭后执行 probe 会直接失败 |
| `runAtStartup` | `false` | 开服后自动生成一次 TypeScript 声明 |
| `baseDir` | `".neko_probe"` | 输出目录，相对于游戏目录 |
| `scan.mode` | `"SMART"` | `SMART`：只扫描白名单里的包；`FULL`：不限白名单，只看排除列表和深度；`NONE`：不扫描，probe 会失败 |
| `scan.extraIncludePackages` | `[]` | 在默认白名单之外**追加**要扫描的包 |
| `scan.includePackages` | `[]` | 非空时**替换**整个默认白名单。一般用 `extraIncludePackages` |
| `scan.excludePackages` | 内置列表 | 不扫描的包，任何模式下都生效 |
| `scan.forceScanMods` | `["minecraft"]` | 强制扫描的模组 ID 或包名 |
| `scan.maxDepth` | `5` | 从事件和绑定出发，最多沿类型引用走几层 |
| `languages.<语言>.outputDir` | 语言名 | 该语言在 `baseDir` 下的子目录 |
| `languages.<语言>.backend` | 不设置 | `/nekojs probe <语言>` 默认使用的 backend |

例如，想补全某个模组的类：

```toml
[scan]
extraIncludePackages = ["com.example.mymod", "com.mojang"]
excludePackages = ["com.example.mymod.debug"]
maxDepth = 6
```

关于包规则：

- 默认白名单是 `java`、`com.tkisor.nekojs` 和当前平台的 Minecraft / 加载器包。
- 每一项可以是包前缀（`com.example.mymod`），也可以是 `re:` 开头的正则，正则要匹配完整类名，例如 `re:com\.example\.(mod1|mod2)\..*`。写错的正则会警告一次，然后被忽略。
- `forceScanMods` 认识 `minecraft`、`neoforge`、`forge` 和 `java` 这几个模组 ID；其他条目写包名或 `re:` 正则。
- 默认排除列表包括 `com.tkisor.nekojs.core`、`java.awt`、`javax.swing`、`java.sql`、`org.graalvm` 等脚本本来就用不了的包。写 `excludePackages = []` 会关闭这些排除，但脚本照样不能使用这些类。

扫描范围越大，生成越慢，文件越多。

<a id="wiki-section-9"></a>
## 常见问题

<a id="wiki-section-10"></a>
### 没有补全

1. 确认执行过 `/nekojs probe`，游戏目录下有 `.neko_probe/typescript/`。
2. 用 VS Code 打开脚本目录，而不是游戏目录的上层文件夹。
3. 如果 `jsconfig.json` 被你改坏了，执行 `/nekojs probe reset_config`。
4. Python 见上文“Python”一节的四个步骤。

<a id="wiki-section-11"></a>
### 补全是旧的，或者缺少某个模组的类

声明不会自动更新。装了模组、改了注册脚本之后，重新执行 `/nekojs probe`。

还是没有的话，这个模组的包可能不在扫描范围里。把它加进 `scan.extraIncludePackages`，或者加大 `scan.maxDepth`。

<a id="wiki-section-12"></a>
### 日志里出现 NEKO-4048

`NEKO-4048` 表示有些声明引用的类型没法生成，例如被排除、被隐藏或无法读取。缺了哪些写在 `.neko_probe/typescript/@special/dependencies.json`。声明文件已经生成，只是编辑器里可能有个别类型显示为找不到。可以检查是不是把需要的包排除了。

<a id="wiki-section-13"></a>
### 方法要写成 `vector["x()"]()`

一个 Java 类同时有字段 `x` 和方法 `x()` 时，`vector.x` 读的是字段。方法要用 Graal 的签名写法调用：`vector["x()"]()`。有重载时写参数类型，例如 `matrix["m00(double)"](0.5)`。

<a id="wiki-section-14"></a>
### 声明里少了某个属性或方法

有几处同名成员会在 TypeScript 里冲突，声明做了以下取舍。运行时两种写法都能用，只是编辑器提示不同：

- `entity.attackable` 属性没有声明，用 `entity.isAttackable()`。`livingEntity.attackable()` 方法照常可用。
- `SectionPos` 的 `section.x()`、`y()`、`z()` 没有声明，用 `section.x` 或 `section.getX()`。

<a id="wiki-section-15"></a>
### 配方方法显示为 never

配方里某个成员的类型是 `never`，表示这个配方类型被 NekoJS 明确拒绝了，不是声明写不出来。看 `/nekojs error` 里的配方诊断，详见 [配方系统](recipe-system_cn)。

<a id="wiki-section-16"></a>
## 用脚本调整声明：ProbeEvents

生成的声明不对或者不够好时，可以在 `server_scripts` 里用 `ProbeEvents` 修改它们，而不是手改生成的文件。这些事件只在执行 `/nekojs probe` 时触发，监听器要写在脚本顶层。

| 事件 | 作用 |
|---|---|
| `ProbeEvents.modifyType` | 修改某个类的声明：改名、改类型、隐藏、加文档、加方法 |
| `ProbeEvents.assignType` | 把某个 Java 类型在所有声明里替换成另一个类型 |
| `ProbeEvents.addGlobal` | 声明额外的全局变量 |
| `ProbeEvents.snippets` | 添加 VS Code 代码片段 |

修改对 TypeScript 和 Python 声明都生效（代码片段只有 TypeScript）。

需要写类型的地方可以传字符串：带 `.` 的当作 Java 类名，例如 `"net.minecraft.world.item.ItemStack"`；不带的当作基本类型，例如 `"string"`、`"number"`、`"boolean"`。

<a id="wiki-section-17"></a>
### modifyType

```javascript
// server_scripts/probe_patch.js
ProbeEvents.modifyType(event => {
  const editor = event.forClass('net.minecraft.world.entity.player.Player')
  if (!editor) return

  editor
    .setDoc('玩家')
    .setMethodDoc('getName', '返回玩家名字')
    .renameParam('addItem', 'arg0', 'stack')
    .changeReturnType('getInventory', 'net.minecraft.world.entity.player.Inventory')
    .changeParamType('addItem', 0, event.union('string', 'net.minecraft.world.item.ItemStack'))
})
```

- `event.forClass(类名)` 返回这个类的编辑器。类不在本次扫描范围内时返回 `null`，所以要判空。`event.hasClass(类名)` 可以先检查。
- `event.type(...)`、`event.array(类型)`、`event.union(a, b, ...)` 用来构造复杂类型。
- 按名字操作的方法会作用于**所有同名重载**。找不到成员时什么也不做，不报错。

编辑器的方法：

| 方法 | 作用 |
|---|---|
| `hide()` | 隐藏整个类 |
| `setDoc(doc)` | 设置类的文档 |
| `renameClass(newName)` | 改类名 |
| `changeSuper(type)` | 改父类 |
| `hasMethod(name)` | 是否有这个方法（包括构造器） |
| `renameMethod(name, newName)` | 改方法名 |
| `hideMethod(name)` | 隐藏方法 |
| `setMethodDoc(name, doc)` | 设置方法文档 |
| `changeReturnType(name, type)` | 改返回类型 |
| `changeParamType(name, index, type)` | 按位置改参数类型 |
| `changeParamType(name, paramName, type)` | 按参数名改参数类型 |
| `renameParam(name, paramName, newName)` | 改参数名 |
| `removeParam(name, index)` | 删除参数 |
| `markOptional(name, paramName)` | 把参数标为可选 |
| `addParam(name, paramName, type)` | 在末尾加参数 |
| `addMethod(name, returnType, ...params)` | 加实例方法。每个参数写 `"类型"` 或 `"名字:类型"` |
| `addStaticMethod(name, returnType, ...params)` | 加静态方法 |
| `hasField(name)` | 是否有这个字段 |
| `renameField(name, newName)` | 改字段名 |
| `hideField(name)` | 隐藏字段 |
| `changeFieldType(name, type)` | 改字段类型 |
| `setFieldDoc(name, doc)` | 设置字段文档 |

<a id="wiki-section-18"></a>
### assignType

```javascript
ProbeEvents.assignType(event => {
  // 所有声明里的 CompoundTag 都显示为 MyNbt
  event.assign('net.minecraft.nbt.CompoundTag', 'MyNbt')
})
```

`assignType` 先于 `modifyType` 执行。同一个位置在 `modifyType` 里又改了类型时，以 `modifyType` 为准。

<a id="wiki-section-19"></a>
### addGlobal 和 snippets

```javascript
ProbeEvents.addGlobal(event => {
  event.add('MyFlag', 'boolean')            // declare const MyFlag: boolean
  event.add('Helper', 'com.example.Helper') // 引用一个 Java 类
})

ProbeEvents.snippets(event => {
  event.add('nekojs-listener', 'listen', 'ServerEvents.started(e => { $1 })', '监听服务端启动')
})
```

`addGlobal` 只影响声明。变量本身要由你自己创建，例如用插件注册一个绑定。代码片段写进 `nekojs/.vscode/nekojs.code-snippets`，你自己写的片段会保留。

<a id="wiki-section-20"></a>
## 生成流程

从这里开始是给插件作者和贡献者的内容。

<a id="wiki-section-21"></a>
### 一次 probe 做了什么

`/nekojs probe` 在服务端线程上运行，由 `ProbeCoordinator` 统一调度。同一时间只能跑一次，重复执行会返回 `probe already running`。

```text
1. 收集类（ProbeClassCollector）
   种子：事件类型、绑定的 Java 类型、配方输入类型、声明依赖
   按 scan 配置过滤，沿类型引用做广度优先搜索，深度为 scan.maxDepth

2. 构建 IR（ProbeIrBuilder，按需）
   有 backend 声明 requiresIr()，或有 modifyType / assignType 监听器时构建
   TypeReflector 并行反射每个类，得到 List<TypeDecl>
   先应用 ProbeEvents.assignType，再触发 ProbeEvents.modifyType

3. 触发 ProbeEvents.addGlobal / ProbeEvents.snippets（有监听器时）

4. 写入工作区基础配置（WorkspaceGenerator）

5. 依次运行每个选中的 backend
   render() 在内存中产出 “相对路径 → 文本”
   ProbeOutputCommitter 逐文件同步到输出目录：
   内容相同跳过，有变化覆盖，本次没产出的删除

6. 各 backend 贡献编辑器配置（jsconfig / pyrightconfig / settings.json / snippets）
```

需要注意：

- 内置的 TypeScript 和 Python backend 都要求 IR，所以 IR 实际上每次都会构建。
- `render` 抛异常时，这个 backend 的输出目录不变。但第 4 步的工作区文件已经写过，不会回滚。
- 写盘是逐文件进行的，不是整个目录原子替换。写到一半出错时，输出可能只更新了一部分。文件被其他程序锁住时会重试，仍失败则报出文件名。
- TypeScript 声明生成成功后，会向 AI 文档目录（默认 `.github/neko_ai`，由 `nekojs/config/ai.toml` 决定）发布一份 API 快照。修改 `baseDir` 不会移动 AI 文档。见 [AI 辅助脚本编写](ai-support_cn)。

<a id="wiki-section-22"></a>
### 输出布局

内置 TypeScript backend 的输出（默认 `.neko_probe/typescript/`）：

```text
.neko_probe/typescript/
├── @package/                      # Java 类声明，按包名组织目录
│   ├── index.d.ts
│   └── net/minecraft/.../index.d.ts
├── @side-only/
│   ├── startup/{events,bindings}/index.d.ts
│   ├── server/{events,events/recipes,bindings}/index.d.ts
│   └── client/{events,bindings}/index.d.ts
├── @special/
│   ├── index.d.ts
│   ├── types/index.d.ts           # 注册表字面量（RegistryTypes）
│   └── dependencies.json          # 声明依赖的补齐情况
├── @manual/
│   ├── index.d.ts                 # 手写声明
│   ├── globals.d.ts               # ProbeEvents.addGlobal
│   └── references.d.ts            # 有手动引用时
├── @nekojs/managed/<side>/index.d.ts
├── @registry-builders/index.d.ts
└── @event-builders/index.d.ts
```

内置 Python backend 的输出（默认 `.neko_probe/python/nekojs/`）：

```text
.neko_probe/python/nekojs/
├── __init__.pyi    # 全局绑定、事件组、addGlobal；from nekojs import * 的目标
├── py.typed        # PEP 561 标记
├── README.md
├── _java/          # Java 类型 stub，按包组织
└── _events/        # 事件声明，按脚本类型组织
```

这些目录名是内置 backend 自己的约定，probe 框架并不检查。框架只要求 `render` 返回相对路径到文本的映射。

`nekojs/api/stable` 等 API 模块的声明写在 `@side-only/<side>/bindings/api-modules.d.ts`，见 [模块系统](module-system_cn#wiki-api-modules)。

<a id="wiki-section-23"></a>
### 编辑器配置的合并

每次 probe 只更新自己管理的条目，其他内容原样保留：

- `compilerOptions.paths`：`java:*`、`@side-only/<side>`、`@side-only/<side>/*`、`@special`、`@special/*`；
- `include`：指向 `.neko_probe/typescript/` 下 `@package`、`@manual`、`@side-only/<side>`、`@nekojs/managed/<side>` 的条目，以及标准脚本文件的 glob；
- `typeRoots`：`<输出目录>/@package` 和 `../node_modules/@types`；
- `typeAcquisition.enable = false`；
- JSX 相关的键，跟随 `engine.toml` 的 `jsxAutomaticRuntime`，见 [TypeScript 与 JSX](typescript-and-jsx_cn)。

相对路径按脚本目录和输出目录的位置自动计算。`.neko_probe/jsconfig.json` 也会生成一份。

Python 的 `extraPaths` 写进游戏目录、`nekojs/`、各脚本目录，以及脚本目录下包含 `.py` 文件的子目录（最多 8 层，跳过依赖、构建和缓存目录）。

`reset_config` 会删除各 backend 整个拥有的配置文件（jsconfig、pyrightconfig、snippets），保留与用户共享的 `.vscode/settings.json`，然后重建默认配置并重新运行 TypeScript probe。

<a id="wiki-section-24"></a>
### 关键类

| 类 | 包 | 职责 |
|---|---|---|
| `ProbeCoordinator` | `probe` | 调度整个流程：收集、IR、事件、backend、编辑器配置 |
| `ProbeClassCollector` | `probe` | 从种子出发收集类 |
| `ProbeIrBuilder` | `probe` | 构建共享 IR 并应用 ProbeEvents 的修改 |
| `ProbeBackend` / `ProbeContext` | `probe` | backend 接口和传给它的上下文 |
| `ProbeBackendRegistry` | `probe` | 按（语言, 名字）登记 backend，检测冲突 |
| `ProbeBackendSelector` | `probe` | 解析 `/nekojs probe` 的参数，各平台命令共用 |
| `ProbeOutputCommitter` | `probe` | 校验输出路径并逐文件写盘 |
| `ProbeConfig` / `ProbeConfigLoader` | `probe` | 读写 `probe.toml` |
| `TypeReflector` / `TypeDecl` | `probe.ir` | 把 `Class<?>` 反射成语言无关的声明 IR，处理 `@Remap`、`@HideFromJS`、`@Doc` |
| `TypeScriptClassRenderer` | `probe.ir` | 把 `TypeDecl` 渲染成 TypeScript |
| `TypeAliasRegistry` | `probe.types` | 生成 `$Foo_` 输入别名和集合输入类型 |
| `TypeScriptProbeBackend` | `probe.backend.typescript` | 内置 TypeScript backend，调用 `BindingDeclarationGenerator`、`EventDeclarationGenerator`、`RecipeEventDeclarationGenerator` 等生成器 |
| `PythonProbeBackend` | `probe.backend.python` | 内置 Python backend |
| `NekoProbeBuiltinPlugin` | `probe` | 注册上面两个内置 backend |

包名都在 `com.tkisor.nekojs` 下，源码在 `common/src/main/java/com/tkisor/nekojs/probe/`。

<a id="wiki-section-25"></a>
### 数据来源：catalog 快照

backend 不直接读运行时状态。命令先调用 `NekoScriptCatalog.snapshot(runtime)` 生成一份不可变的 `NekoScriptCatalogSnapshot`，backend 只读这份快照、收集到的类和 IR。快照包括：

- `bindings`：全局绑定的名字和 Java 类型；
- `events`：事件组、事件名、事件类型、是否可取消；
- `adapters`：类型适配器的目标类型和输入形状；
- `moduleBindings` / `moduleEvents`：按脚本类型发布的 API 模块；
- `recipeNamespaces`：配方命名空间和方法签名；
- `registryTypes`：注册表 ID、标签和命名空间；
- `hostExtensions`：注入到原版类的扩展方法；
- `snippets`、`typeDocs`、`manualDeclarations`。

<a id="wiki-section-26"></a>
### TypeScript 投影规则

一些 Java 写法在 TypeScript 里没法精确表达，内置 backend 的处理方式：

- `char` 和 `Character` 显示为 `string`。长度是否为 1 由运行时检查。
- 构造器不能有自己的泛型参数，构造器上的类型变量按上界显示。
- 集合参数区分 Java 值和脚本输入：只有元素类型的 Java 值和输入相同时，才用 `$List_<T>` 这样的短别名，否则写成 `$List<$Payload> | $Payload_[]`。
- 有字段的配方 schema 同时提供位置参数重载和完整的命名选项对象重载。
- 可调用声明按 120 字符软换行，单个很长的类型仍可能超出。

<a id="wiki-section-27"></a>
## 给 probe 提供数据（插件作者）

插件注册的绑定、事件和适配器会自动进入快照，不需要额外操作。下面几种方式可以让声明更准确。

<a id="wiki-section-28"></a>
### 文档注解

`registry.register("Foo", new FooJS())` 注册绑定后，probe 会反射 `FooJS` 的公开方法。在类和方法上加 `@Doc`，在方法上用 `@Param` 描述参数、用 `@Return` 描述返回值，就会生成对应的 JSDoc：

```java
@Doc("Utility helpers for my mod.")
public final class FooJS {
    @Doc("Returns the display name of an item.")
    @Param(name = "id", value = "item id, e.g. minecraft:stone")
    public String displayName(String id) {
        // ...
    }
}
```

不方便加注解时，用 `registerTypeDocs` 钩子编程式提供。`@Remap`、`@HideFromJS` 等注解同样影响声明，见 [注解](annotations_cn) 和 [插件开发](plugin-development_cn)。

<a id="wiki-section-29"></a>
### 指定输入和输出类型

- 参数上加 `@ScriptInput(Provider.class)`：用 provider 的 `AdapterInputShape` 替换宽泛的输入类型。provider 实现 `ScriptInputShapeProvider`，并有公开的无参构造器。
- 方法上加 `@ScriptOutput("TypeScript 类型表达式")`：替换 TypeScript 返回类型。外部类型写成 `import("module").Type`。如果返回值仍有 Java 成员，用交叉类型把 Java 类型保留下来。

这两个注解只影响 TypeScript 声明，不会注册转换器，也不做运行时校验。Python 声明仍使用反射得到的类型。`ProbeEvents.modifyType` 的修改优先于它们。

<a id="wiki-section-30"></a>
### 注册表字面量

`NekoCatalogPlatformProvider.registryTypes(...)` 提供注册表 ID 列表，probe 据此生成 `@special/types`：

- `RegistryTypes.<名字>`：注册表里所有 ID，例如 `RegistryTypes.Item`。
- `RegistryTypes.<名字>Tag`：该注册表当前的标签。适配器用 `template("#", registryTag("Item"))` 让 `'#minecraft:planks'` 也能补全。没有标签时退化为 `string`。
- `RegistryTypes.Namespace`：所有模组 ID 加上所有注册表条目的命名空间。适配器用 `template("@", namespace())` 让 `'@create'` 能补全。

已发布的 `SimpleRegistryBasedAdapter` 用到的注册表会自动加入。同一个名字对应不同注册表，或和生成的 `Tag` / `Namespace` 类型重名时，probe 报 `NEKO-4052` 并拒绝生成。Python 在条目少于 512 个时生成 `Literal[...]`，否则退化为 `str`。

适配器输入形状如何变成 `$Foo_` 见 [类型适配器](type-adapters_cn)。

<a id="wiki-section-31"></a>
### 手写声明和代码片段

- 反射表达不了的内容，在 `registerTypeDocs` 钩子里调用 `registry.registerManualDeclaration(...)` 提供手写的 `.d.ts` 片段，生成到 `@manual/index.d.ts`。NekoJS 自己的手写声明在 `NekoCommonManualDeclarations`。
- `NekoCatalogPlatformProvider.snippets()` 提供代码片段。脚本也可以用 `ProbeEvents.snippets` 追加。

<a id="wiki-section-32"></a>
## 编写自定义 backend

内置的是 `typescript:builtin` 和 `python:builtin`。插件可以用同一语言、不同名字提供替代实现，也可以用新的语言 ID 生成其他产物，例如 Lua 声明、JSON 清单或文档站。

<a id="wiki-section-33"></a>
### 最小示例

一个 backend 只需实现 `languageId()`、`name()` 和 `render(ctx)`。下面这个 backend 把收集到的类列成 JSON：

```java
public final class JsonListBackend implements ProbeBackend {

    @Override
    public String languageId() {          // 语言 ID：命令和默认输出目录都用它
        return "json";
    }

    @Override
    public String name() {                // 在同一语言里唯一
        return "list";
    }

    @Override
    public Map<String, String> render(ProbeContext ctx) {
        // 返回 “相对路径 → 文本”，不要自己写盘
        StringBuilder sb = new StringBuilder("{\"classes\":[\n");
        boolean first = true;
        for (Class<?> c : ctx.collectedClasses()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {\"fqn\": \"").append(c.getName()).append("\"}");
        }
        sb.append("\n]}\n");
        return Map.of("classes.json", sb.toString());
    }
}
```

在插件的 `registerProbeBackends` 钩子里注册：

```java
@Override
public void registerProbeBackends(ProbeBackendRegistry registry) {
    registry.register(new JsonListBackend(), "my-addon");
}
```

之后执行 `/nekojs probe json`，会生成 `.neko_probe/json/classes.json`。`/nekojs probe all` 也会运行它。

<a id="wiki-section-34"></a>
### render 的约定

- 返回值的键是相对于输出目录的路径，用 `/` 分隔；值是 UTF-8 文本。
- 不要碰磁盘。默认的 `generate()` 负责写盘、删除旧文件、文件被锁时重试和路径校验。
- 抛异常时，这个 backend 记为失败，异常消息显示给执行命令的人，输出目录保持不变。缺少前置条件时抛 `IllegalArgumentException` 并写清原因即可。
- `ctx` 提供 `snapshot()`、`collectedClasses()`、`ir()`、`config()`、`paths()`、`languageDir()` 和 `overrides()`。

<a id="wiki-section-35"></a>
### 可选方法

| 方法 | 默认 | 什么时候覆盖 |
|---|---|---|
| `requiresIr()` | `false` | 需要 `ctx.ir()`（包含 ProbeEvents 修改的 `TypeDecl` 列表）时返回 `true` |
| `priority()` | `0` | 同一语言有多个 backend 时，最高的作为默认 |
| `outputDir(paths, config)` | `<baseDir>/<语言>`，可被 `outputDir` 配置覆盖 | 需要特殊目录时 |
| `contributeEditorConfig(contributor, ctx)` | 不做事 | 让编辑器认识你的产物，通过 `contributor` 的 merge 方法合并 |
| `resetEditorConfig(paths)` | 不做事 | 执行 `reset_config` 时删除你写的配置文件 |
| `generate(ctx)` | render 后写盘 | 一般不要覆盖 |

<a id="wiki-section-36"></a>
### 规则

框架会拦下这些情况：

- 同一（语言, 名字）注册两次：启动时注册表锁定，游戏崩溃并列出冲突的注册者。
- `render` 返回非法路径（空、绝对路径、包含 `..`）：这个 backend 失败，不写任何文件。
- 一次运行中两个 backend 的输出目录相同：只运行第一个，其余跳过并警告。否则后一个会把前一个的文件当旧文件删掉。

同一语言的多个 backend 可以共存。`/nekojs probe <语言>` 先用 `probe.toml` 里的 `languages.<语言>.backend`，没有设置就用 priority 最高的那个；其他的用 `/nekojs probe <语言> <名字>` 运行。

<a id="wiki-section-37"></a>
### 替换 TypeScript backend

- 沿用内置的目录名（`@package`、`@side-only`、`@special`、`$Foo`、`$Foo_`）最省事，现有的编辑器配置直接能用。
- 想换一套布局，要同时改两处：backend 的 `contributeEditorConfig`，以及插件钩子 `NekoJSPlugin.modifyWorkspaceConfig`。后者负责第一次生成的 `jsconfig.json`，不改的话在第一次运行 probe 之前，路径别名是错的。
- 已有脚本里写死了 `@side-only/...` 这类模块名，换布局对脚本作者来说是不兼容的改动。

<a id="wiki-section-38"></a>
## 已知限制

- 文档的完整程度取决于注解。没有 `@Doc` 的类只有签名，可以用 `registerTypeDocs` 或 `ProbeEvents.modifyType` 补。
- 没有用 `-parameters` 编译的第三方 jar 和 JDK 类，参数名显示为 `arg0`、`arg1`。NekoJS 自己的类有真实参数名。
- 声明是快照，游戏内容变了要重新生成。
- Python 声明不保留泛型关系。
- 声明依赖按当前的排除和隐藏规则补齐，并不是所有 Java 类都会导出。

<a id="wiki-section-39"></a>
## 测试

测试在 `common/src/test/java/com/tkisor/nekojs/probe/`。常用的几个：

- `ProbeBackendRegistryTest`：backend 注册和冲突。
- `ProbeConfigTest`、`ProbeScanModeTest`：`probe.toml` 解析和三种扫描模式。
- `ProbeOutputCommitterTest`：写盘和路径校验。
- `TypeScriptNoopIrGoldenTest`、`LegacyProbeTreeTest`、`ProbeOutputCompatibilityTest`：TypeScript 输出的 golden 对比。
- `TypeScriptProbeBackendEditorConfigTest`、`FileEditorConfigContributorTest`：编辑器配置合并。
- `ProbeEventsSurfaceGoldenTest`：ProbeEvents 的公开接口。

修改 probe 后运行 `:common:check`。

<a id="wiki-section-40"></a>
## 相关页面

- [命令](commands_cn)：所有 `/nekojs` 命令。
- [TypeScript 与 JSX](typescript-and-jsx_cn)：TypeScript 脚本和 `jsconfig.json` 的 JSX 设置。
- [Python 脚本](python-scripts_cn)：Python 脚本本身。
- [模块系统](module-system_cn)：`java:` 导入和 API 模块。
- [注解](annotations_cn)：`@Remap`、`@HideFromJS` 等影响声明的注解。
- [类型适配器](type-adapters_cn)：`$Foo_` 输入别名从哪来。
- [插件开发](plugin-development_cn)：`registerProbeBackends` 等插件钩子。

<!-- wiki-nav -->

---

[上一篇: 项目架构](project-architecture_cn) · [目录](Home) · [下一篇: 构建系统](build-system_cn)
