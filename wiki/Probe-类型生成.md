# Probe 类型生成

> Probe 是 NekoJS 内置的**类型声明生成器**（ProbeJS 等价物）。它反射运行中的 Java 类，为 **TypeScript** 生成 `.d.ts`、为 **Python** 生成 `.pyi` stub 包，配合编辑器配置让 IDE 获得补全。
>
> 内置两个 backend：`typescript:builtin` 与 `python:builtin`（在 `NekoPluginBootstrap` 注册）。第三方插件可通过 `registerProbeBackends` 注册自己的 backend。

## 命令一览

| 命令 | 作用 |
|---|---|
| `/nekojs probe` | 无参：只跑 TS 内置 backend（`typescript:builtin`） |
| `/nekojs probe <语言>` | 跑该语言默认 backend：`languages.<lang>.backend` 配置优先，未设则取同语言 priority 最高者。如 `/nekojs probe python` |
| `/nekojs probe <语言> <名字>` | 精确指定 backend，如 `/nekojs probe typescript builtin` |
| `/nekojs probe all` | 跑**所有**已注册 backend（跨语言） |
| `/nekojs probe list` | 列出已注册 backend（形如 `typescript:builtin (NekoJS (built-in))`） |
| `/nekojs probe reload` | 丢弃 probe.toml 配置缓存，下次从盘重读（改完配置不必重启） |
| `/nekojs probe enable` / `disable` | 把 `enabled` **持久化**进 probe.toml 并重载缓存 |
| `/nekojs probe reset_config` | 删除各 backend 生成的编辑器配置（jsconfig/pyrightconfig/snippets），重建出厂基础配置并立即重跑默认 TS probe。jsconfig 手动改坏时用它一键恢复 |

`<语言>` 与 `<名字>` 支持 TAB 补全。所有 probe 子命令在服务端线程执行，权限与 `/nekojs` 一致（op 2+）。

**jsconfig 的合并语义**：每次 probe 只**刷新 probe 管理的条目**（`java:*`/`@side-only/*` 路径别名、include/typeRoots 里指向 `.neko_probe/` 的条目、JSX 运行时键），**你手动加的条目和其它键原样保留**——比如自己 include 一个 `./typings/custom.d.ts` 或加自己的 typeRoots，不会被 probe 覆盖。

## 工作流程

用户跑 `/nekojs probe` 时（`ProbeCoordinator` 协调，多 backend 共享同一次收集）：

```text
1. 共享收集（collectClasses）
   ├─ 种子：事件类型、dispatch key 类型、绑定的 Java 类型（含代理绑定的 extraDocTypes）
   └─ BFS 可达闭包，深度 = probe.toml scan.maxDepth（默认 5），按 scan 配置过滤
     （SMART 走 include 白名单 + forceScanMods；FULL 直通仅排除；NONE 直接失败）

2. 共享 IR（按需构建）
   ├─ 触发条件：probe.modifyType / probe.assignType 有监听器，或选中 backend requiresIr（Python 恒 true）
   ├─ TypeReflector 并行反射每个收集到的类 → List<TypeDecl>（反射失败的类跳过）
   ├─ 先应用 probe.assignType（全局类型重定向，标记受影响类 mutated）
   └─ 再触发 probe.modifyType（参数级编辑，显式编辑不被 assign 二次覆盖）
     （TS 无监听器时 ir=null，完全走旧 ClassDeclGenerator 路径，产物零回归）

3. probe.addGlobal / probe.snippets 收集（各有监听器才触发）

4. 每个选中 backend 各自渲染到 <baseDir>/<该语言输出子目录>（默认与语言 id 同名：.neko_probe/typescript、.neko_probe/python）
   ├─ 生成到 <目录>.staging → 全部成功后原子替换 → 旧的备份到 <目录>.old
   └─ 中途失败则丢弃 staging，旧产物完整保留

5. 编辑器配置贡献（各 backend 自报片段，幂等合并）
   ├─ TS：合并各脚本目录 jsconfig.json 的 paths / include / typeRoots（指向 .neko_probe/typescript/）
   ├─ Python：合并各目录 pyrightconfig.json 的 extraPaths（指向 .neko_probe/python），并通过通用注入机制写入各目录 .vscode/settings.json 的 python.analysis.extraPaths / python.languageServer（详见 Python 补全）
   └─ snippets（TS only）：合并进 nekojs/.vscode/nekojs.code-snippets
```

## 输出布局

### TypeScript（`.neko_probe/typescript/`）

```text
.neko_probe/typescript/
├── @package/                       # Java 类声明（包目录树镜像）
│   ├── index.d.ts                  # 顶层包重新导出
│   ├── com/tkisor/nekojs/.../index.d.ts
│   └── net/minecraft/.../index.d.ts
├── @side-only/
│   ├── startup/{events,bindings}/index.d.ts
│   ├── server/{events,events/recipes,bindings}/index.d.ts
│   └── client/{events,bindings}/index.d.ts
├── @special/
│   ├── index.d.ts                  # export * as types from "@special/types"
│   └── types/index.d.ts            # 注册表字面量（如所有 item id 的 union）
├── @manual/
│   ├── index.d.ts                  # 手动 .d.ts 声明
│   └── globals.d.ts                # probe.addGlobal 产出的 declare const（有监听器时）
├── @nekojs/managed/<side>/index.d.ts  # managed API 声明
└── jsconfig.json                   # 内部解析用
```

### Python（`.neko_probe/python/nekojs/`）

```text
.neko_probe/python/nekojs/
├── __init__.pyi    # 全局绑定 + 事件组入口 + probe.addGlobal 全局（`from nekojs import *` 的目标），__all__ 框定
├── py.typed        # PEP 561 marker（空文件）
├── README.md       # 用法说明
├── _java/          # Java 类型 stub（按 Java 包组织，祖先包均有 namespace marker）
│   └── net/minecraft/.../__init__.pyi
└── _events/        # 事件声明（按 script side 组织）
    ├── __init__.pyi
    └── server/__init__.pyi
```

### 编辑器路径映射（TS）

每个 `*_scripts/` 目录下的 `jsconfig.json` 由 probe **自动合并**（probe 拥有的键替换为最新值，用户自定义键保留），把路径映射到 `.neko_probe/typescript/`：

```json
{
  "compilerOptions": {
    "paths": {
      "java:*":        ["../../.neko_probe/typescript/@package/*"],
      "@side-only/server":    ["../../.neko_probe/typescript/@side-only/server"],
      "@side-only/server/*":  ["../../.neko_probe/typescript/@side-only/server/*"],
      "@special":      ["../../.neko_probe/typescript/@special"],
      "@special/*":    ["../../.neko_probe/typescript/@special/*"]
    }
  }
}
```

> 相对路径随脚本目录与输出目录的位置自动计算，不同脚本类型（startup/server/client/test）各自映射自己的 `@side-only/<side>`。

## probe.toml 配置

配置文件在 `<game>/nekojs/config/probe.toml`。首次加载时按需写入默认值与注释（`CommentedFileConfig` + autosave）；文件损坏/异常时回退到默认配置。`/nekojs probe reload` 可热重读。

| 键 | 默认 | 语义 |
|---|---|---|
| `enabled` | `true` | 总开关。`false` 时 probe 直接返回失败结果（"probe disabled in probe.toml"）。`/nekojs probe enable|disable` 就是改这个键 |
| `baseDir` | `".neko_probe"` | 输出基目录（相对游戏目录）。每个语言 backend 拥有其下 `<baseDir>/<语言>` 子目录（如 `.neko_probe/typescript`） |
| `scan.includePackages` | `[]` | 类扫描的包规则白名单。条目为**字面包前缀**（`fqn` 以 `前缀.` 开头）或 **`re:` 正则**（如 `re:com\.example\..*\.api\..*`，对全限定名整体匹配）。**覆盖语义**：非空时**完全取代**默认白名单（`java`、`com.tkisor.nekojs`、平台默认 MC/loader 包）。要**追加**请用 `extraIncludePackages`，不要在此重抄默认值 |
| `scan.extraIncludePackages` | `[]` | 追加到生效白名单（**追加语义**，同样支持字面前缀与 `re:` 正则）。适合加 `com.mojang` 或某 mod 的包 |
| `scan.excludePackages` | `[]` | 排除规则（deny-list，支持字面前缀与 `re:` 正则）。**始终生效**（FULL 模式也保留），命中即跳过 |
| `scan.forceScanMods` | `["minecraft"]` | 在白名单之外**强制纳入**扫描的 mod id 或包规则。内置 modId 映射表：`minecraft`→`net.minecraft`、`neoforge`→`net.neoforged`、`forge`→`net.minecraftforge`、`java`→`java`。条目含 `.` 视为字面包前缀、`re:` 开头为正则；未知 mod id 记 debug 日志并忽略。excludePackages 仍优先于强制包含 |
| `scan.maxDepth` | `5` | 从事件/绑定种子出发的 BFS 最大深度（≤0 时兜底为 5） |
| `scan.mode` | `"SMART"` | 扫描模式：`SMART`=白名单 + forceScanMods 过滤；`FULL`=跳过 include 白名单，闭包只受 exclude 与 maxDepth 约束；`NONE`=完全不扫描，probe 返回失败结果。未知取值兜底为 `SMART` |
| `languages.<lang>.outputDir` | 语言 id 本身 | 该语言产物在 `baseDir` 下的**子目录**（如默认 `typescript` → `.neko_probe/typescript`）。缺省/null 回退语言 id。`ProbeBackend.outputDir` 默认实现优先取此键 |

> **包规则的正则支持**：`includePackages` / `extraIncludePackages` / `excludePackages` / `forceScanMods` 的条目既可以是字面包前缀（旧行为，`fqn.startsWith("前缀.")`），也可以写成 `re:<pattern>` 形式的正则（对类全限定名做**全匹配**，如 `re:com\.example\.(mod1|mod2)\..*` 一次纳入多个 mod 包）。正则编译结果有缓存，扫描热路径无额外开销；非法正则会 warn 一次并视为永不命中。

| `languages.<lang>.backend` | 未设 | `/nekojs probe <lang>` 默认优先选用的 backend 名；未设（或找不到该名字）→ 回退该语言注册表默认（priority 最高者） |

> 默认配置自带 `languages.typescript.outputDir = "typescript"` 与 `languages.python.outputDir = "python"`（首次加载写入，保持输出布局与语言 id 一致）。`languages` 表按任意语言 id 动态解析，第三方 backend 语言也能用。

示例（追加一个 mod 的包、排除调试包、放开深度）：

```toml
enabled = true
baseDir = ".neko_probe"

[scan]
extraIncludePackages = ["com.example.mymod", "com.mojang"]
excludePackages = ["com.example.mymod.debug"]
maxDepth = 6
mode = "SMART"

# per-language（可选）：改默认 backend 或输出子目录
[languages.typescript]
backend = "my-better-ts"     # 未设 → 语言注册表默认（priority 最高者）
outputDir = "typescript"     # 未设 → 语言 id 本身
```

## probe.* 事件

事件组 `ProbeEvents`，4 个事件，**全部为 SERVER 脚本**（probe 命令在服务端线程执行，监听器须在 `server_scripts` 顶层注册）：

| 事件 | 事件对象 | 时机/作用 |
|---|---|---|
| `modifyType` | `ProbeModifyTypeEventJS` | 类型渲染**之前**（IR 构建后）：对反射产出的 `TypeDecl` 做参数级编辑。被触及的类由 `TypeScriptClassRenderer` 重新渲染并覆盖声明缓存；未触及的类仍走旧 `ClassDeclGenerator` 路径（TS 产物零回归） |
| `assignType` | `ProbeAssignTypeEventJS` | 全局类型重定向：把某 Java 全限定名**处处**改写为自定义类型。IR 构建后应用（替换 SYMBOL 类型槽），TS（重渲染被触及类）与 Python 均生效 |
| `addGlobal` | `ProbeAddGlobalEventJS` | 登记额外全局声明（名字+类型）。TS → `@manual/globals.d.ts`（`declare const Name: T;`）；Python → `nekojs/__init__.pyi` |
| `snippets` | `ProbeSnippetEventJS` | 登记 VSCode `.code-snippets` 片段。当前仅 TS backend 消费（合并进 `nekojs/.vscode/nekojs.code-snippets`，probe 拥有的片段名替换，用户片段保留） |

> `modifyType` / `assignType` 有监听器时，coordinator 会构建共享 IR（Python backend 恒需 IR）；两者皆无监听器且只跑 TS 时，IR 不构建，TS 走旧路径零回归。

类型入参（所有编辑/声明方法）接受：
- **字符串**：含 `.` → Java 全限定名（SYMBOL）；否则 → 原始类型名（`"string"`/`"number"`/`"boolean"`/`"int"`…）
- **ApiTypeRef**：经 `event.type(desc)` / `event.array(element)` / `event.union(a, b, ...)` 构造

### modifyType 示例

```javascript
// server_scripts/probe_patch.js
ProbeEvents.modifyType(event => {
  const editor = event.forClass("net.minecraft.world.entity.player.Player")
  if (editor) {
    editor
      .renameMethod("getXxx", "getCustom")                          // 整类改名：所有同名重载一起改
      .changeParamType("addItem", 0, "net.minecraft.world.item.ItemStack")  // 按下标改参数类型
      .markOptional("eat", "level")                                  // 参数变 TS 可选 name?: type
      .setMethodDoc("getCustom", "自定义文档")
      .addParam("eat", "priority", "number")                         // 追加参数到所有同名重载末尾
      .setDoc("玩家扩展")
  }
  // 类型构造辅助：
  const arr = event.array("string")                 // string[]
  const u   = event.union("string", "number")       // string | number
  editor.changeReturnType("getCustom", u)
})
```

### ClassEditor 方法清单

按名操作作用于该类中**所有同名**方法/构造器（含重载）；找不到目标成员时**静默 no-op**（DEBUG 日志），可先用 `hasMethod`/`hasField` 判空。任一编辑都会把所属 `TypeDecl` 标记为 mutated。

| 层级 | 方法 | 说明 |
|---|---|---|
| 类级 | `hide()` | 隐藏整个类（渲染为空声明） |
| 类级 | `setDoc(doc)` | 覆盖类文档（JSDoc） |
| 类级 | `renameClass(newName)` | 重命名类（TS/Python 输出的类名换成 newName；enum 自引用同步改名） |
| 类级 | `changeSuper(type)` | 改父类类型 |
| 方法级 | `hasMethod(name)` | 是否存在该方法（含构造器） |
| 方法级 | `renameMethod(name, newName)` | 重命名（所有同名重载） |
| 方法级 | `hideMethod(name)` | 隐藏（所有同名重载） |
| 方法级 | `setMethodDoc(name, doc)` | 覆盖方法文档 |
| 方法级 | `changeReturnType(name, type)` | 改返回类型 |
| 方法级 | `changeParamType(name, index, type)` | 按下标改参数类型（作用于每个有该下标参数的同名重载） |
| 方法级 | `changeParamType(name, paramName, type)` | 按参数名改类型 |
| 方法级 | `renameParam(name, paramName, newName)` | 按参数名重命名 |
| 方法级 | `removeParam(name, index)` | 按下标移除参数 |
| 方法级 | `markOptional(name, paramName)` | 参数标记为 TS 可选 |
| 方法级 | `addParam(name, paramName, type)` | 追加参数到方法末尾 |
| 方法级 | `addMethod(name, returnType, ...paramSpecs)` | 新增实例方法。`paramSpecs` 每项为 `"type"`（参数名自动 arg0/arg1…）或 `"name:type"` |
| 方法级 | `addStaticMethod(name, returnType, ...paramSpecs)` | 新增静态方法（语义同 `addMethod`） |
| 字段级 | `hasField(name)` | 是否存在该字段 |
| 字段级 | `renameField(name, newName)` | 重命名字段 |
| 字段级 | `hideField(name)` | 隐藏字段 |
| 字段级 | `changeFieldType(name, type)` | 改字段类型 |
| 字段级 | `setFieldDoc(name, doc)` | 覆盖字段文档 |

### assignType 示例

```javascript
ProbeEvents.assignType(event => {
  // 让所有声明里的 net.minecraft.nbt.CompoundTag 变成你自定义的 JS 类型
  event.assign("net.minecraft.nbt.CompoundTag", "MyNbt")
})
```

`assign(javaFqn, typeDesc)` 登记映射；语义上 assign 改的是「反射产出的类型」，若 `modifyType` 之后再显式设类型，后者覆盖优先。

### addGlobal / snippets 示例

```javascript
ProbeEvents.addGlobal(event => {
  event.add("MyFlag", "boolean")                       // → declare const MyFlag: boolean;
  event.add("Helper", "com.example.Helper")            // → SYMBOL 引用（自动收集 import）
})

ProbeEvents.snippets(event => {
  event.add("nekojs-listener", "listen", "ServerEvents.started(e => { $1 })",
            "监听服务端启动")
})
```

## Python 补全

`/nekojs probe python` 生成 `.neko_probe/python/nekojs/` stub 包（PEP 484/561），让 pyright / Pylance / Jedi 为 Python 脚本提供补全：

1. 脚本顶部写魔术 import：`from nekojs import *` —— **转译器会剥离它**（不产出任何 JS、source map 不受影响），它只为类型检查器存在。
2. pyright 通过 `extraPaths` 解析到该 stub 包：NekoJS 把 `.neko_probe/python` 自动（幂等、去重）合并进多个 `pyrightconfig.json` 的 `extraPaths`（游戏根目录、`nekojs/`、各脚本根目录，以及脚本根下每个实际包含 `.py` 文件的嵌套目录）。同一批目录的 `.vscode/settings.json` 还会经通用注入机制写入：
   - `python.analysis.extraPaths`：同上，按字符串去重追加；
   - `python.languageServer`：仅在用户未显式设置时固定为 `"Pylance"`（VSCode 默认的 "Default" 在部分环境会退回 Jedi，而 Jedi 不读 pyrightconfig/extraPaths，导致无补全）。

   **注意 Pylance 只读取「工作区根」的配置**（pyright CLI 才会从源文件就近向上发现）——请在编辑器中直接打开游戏目录（或 `nekojs/`、某个脚本目录、某个含 `.py` 的子目录），而不是它们的上层文件夹；NekoJS 已为上述每个可打开层级各生成一份配置，任一目录作为工作区根都能解析。
3. 补全内容：
   - `nekojs/__init__.pyi`：全局绑定 + 事件组入口 + `probe.addGlobal` 的全局声明（`__all__` 框定，`from nekojs import *` 的目标）；
   - `nekojs/_java/<java.包>/__init__.pyi`：Java 类型 stub（泛型展平为 `Any`；成员名、参数数量、字段类型保留，够日常补全）；
   - `nekojs/_events/<side>/__init__.pyi`：事件声明（按 script side）；
   - `nekojs/py.typed`：PEP 561 标记；`nekojs/README.md`：用法说明。
4. 适配器输入别名：`<Simple>_ = <Simple> | <输入类型们>`（如 `ItemStack_ = ItemStack | str | ...`），与 TS 的 `$Foo_` 对应；仅对 IR 中存在的目标类生成，无法映射的形状（注册表/原始 TS 片段）跳过。

> Python backend `requiresIr()` 恒为 true——只要选中 python backend（含 `/nekojs probe all`），coordinator 就构建共享 IR；`modifyType`/`assignType` 的编辑对 Python 产物同样生效。

## TS 补全（jsconfig 自动合并）

probe 生成后会把以下配置**幂等合并**进每个脚本目录的 `jsconfig.json`（以及 `.neko_probe/jsconfig.json`），全部指向 `.neko_probe/typescript/`：

- `compilerOptions.paths`：`java:*`、`@side-only/<env>`、`@side-only/<env>/*`、`@special`、`@special/*`
- `include`：`@package/**/*.d.ts`、`@manual/**/*.d.ts`、`@side-only/<env>/**/*.d.ts`、`@nekojs/managed/<env>/**/*.d.ts`
- `typeRoots`：`<tsOut>/@package`、`../node_modules/@types`

合并规则：probe 拥有的键替换为最新值，**用户自定义键保留**（不会覆盖你手写的 jsx/其它配置，见 [TypeScript 与 JSX](TypeScript-与-JSX)）。

## 模块布局约定（不变量）

以下是 probe 输出的**稳定约定**，第三方 probe backend 替换实现也要遵守：

| 约定 | 含义 |
|---|---|
| `@package/` | Java 包按目录树镜像 |
| `@side-only/<side>/` | 按脚本侧别分（startup/server/client） |
| `@special/types/` | 注册表字面量 union（如所有物品 id） |
| `@manual/` | 插件/平台手动写的 `.d.ts`（+ `globals.d.ts`） |
| `$ClassName` | 类声明命名约定（避免与 JS 关键字冲突） |
| `$Foo_` | 适配器输入别名（`Foo` 是目标类型名） |
| `java:` / `@side-only` / `@special` | 模块标识符前缀 |

## 关键类

| 类 | 位置 | 职责 |
|---|---|---|
| `ProbeCoordinator` | `common/.../probe/` | 协调器：共享类收集（BFS）、按需共享 IR、事件触发、派发 backend、外部副作用 |
| `ProbeBackendRegistry` | `common/.../probe/` | backend 按 `(语言, 名字)` 二维登记；lock 时冲突崩溃；命令解析与补全 |
| `ProbeBackend` / `ProbeContext` | `common/.../probe/` | 单语言生成器接口（`generate`/`contributeEditorConfig`/`outputDir`/`requiresIr`）；共享上下文 |
| `TypeScriptProbeBackend` | `common/.../probe/` | 内置 TS backend：`.d.ts` 全流程（旧 ClassDeclGenerator 路径 + IR 重渲染） |
| `PythonProbeBackend` | `common/.../probe/backend/python/` | 内置 Python backend：`.pyi` stub 包（`requiresIr=true`） |
| `TypeReflector` / `TypeDecl` | `common/.../probe/ir/` | `Class<?>` → 声明 IR（getter/setter 推断镜像旧实现） |
| `TypeScriptClassRenderer` | `common/.../probe/ir/` | `TypeDecl` → TS 类/接口/枚举块（modifyType 重渲染路径；getter 覆盖表） |
| `ClassDeclGenerator` | `common/.../probe/` | 旧路径：Java 类 → TS 类声明 |
| `IndexFileGenerator` | `common/.../probe/` | 每包的 `index.d.ts`、import 收集、声明缓存覆盖 |
| `BindingDeclarationGenerator` | `common/.../probe/` | `@side-only/{side}/bindings/index.d.ts` |
| `EventDeclarationGenerator` | `common/.../probe/` | `@side-only/{side}/events/index.d.ts` |
| `RecipeEventDeclarationGenerator` | `common/.../probe/` | `event.recipes.<ns>.<type>(...)` 类型 |
| `AdapterAliasGenerator` | `common/.../probe/` | 从 `AdapterInputShape` 渲染 `$Foo_` 别名 |
| `TypeConverter` / `TypeAliasRegistry` | `common/.../probe/types/` | Java `Type` → TS 字符串 |

## 数据源：catalog snapshot

`NekoScriptCatalog.snapshot(runtime)` 生成不可变 `NekoScriptCatalogSnapshot`，backend 只消费 snapshot（纯函数 `(snapshot, collectedClasses, ir)`）。这是关键解耦点。

snapshot 包含：

- `bindings`：所有全局绑定（name + Java 类型）
- `events`：所有事件组/事件（group + name + 事件类型 + 可取消 + dispatch 信息）
- `adapters`：所有适配器（目标类型 + 输入形态）
- `recipeNamespaces`：配方命名空间 + handler 方法签名
- `registryTypes`：注册表字面量（喂给 `@special/types`）
- `hostExtensions`：注入到原版类的扩展方法
- `snippets`：IDE 代码片段
- `typeDocs`：`TypeDocsRegister` 的内容（描述/示例/参数文档）
- `manualDeclarations`：手动 `.d.ts`

## 给 probe 喂数据（插件作者）

### 绑定自带类型

`registry.register("Foo", new FooJS())` —— probe 会反射 `FooJS` 的公共方法。要带 JSDoc，用编程式 `registerTypeDocs`（`TypeDocsRegister.register(...)` / `registerManualDeclaration(...)`，见 [插件开发](插件开发)）；注解式文档（`@Doc`/`@Param`）尚在规划中。

### 注册表字面量

通过 `NekoCatalogPlatformProvider.registryTypes()` 提供注册表 id 列表，probe 生成 `@special/types/index.d.ts`（如所有物品 id 的字符串字面量 union），让脚本里 `'minecraft:stone'` 有补全。

### 手动声明

`NekoCommonManualDeclarations`（common）/ 平台 manual declarations 提供手写的 `.d.ts` 片段，进 `@manual/index.d.ts`。用于无法反射表达的内容。

### Snippet

`NekoCatalogPlatformProvider.snippets()` 提供 IDE 代码片段；脚本侧可用 `ProbeEvents.snippets` 追加。

## 替换 / 新增 backend

第三方实现 `ProbeBackend` 并通过 `registerProbeBackends` 注册（内置 `typescript:builtin` / `python:builtin` 已占位，可用同语言不同名字**新增** backend）：

```java
@Override
public void registerProbeBackends(ProbeBackendRegistry registry) {
    registry.register(new MyBetterProbeBackend(), "my-addon");
}
```

**规则**：
- 同一 `(语言, 名字)` 重复注册 → bootstrap lock 时**崩溃**（确定性冲突，列出所有注册者）。
- 同语言多 backend 时，`/nekojs probe <语言>` 选 priority 最高者，其余用 `/nekojs probe <语言> <名字>` 指定。
- 你的 TS backend 要遵守上面的模块布局约定，否则 `jsconfig.json` 路径失效。

## Probe 的当前局限

> 这些是已知边界，列在这里让你知道。

1. **`@Remap`/`@HideFromJS` 当前在 probe 不生效**：`ClassDeclGenerator` 用裸 `getDeclaredMethods()`，没走 `MemberVisibilityQuery`。计划修复（Phase A）。
2. **JSDoc 覆盖少**：文档注解（`@Doc`/`@Param`）尚在规划中，当前靠编程式 `registerTypeDocs` 或 `ProbeEvents.modifyType` 的 `setDoc` 系列补 JSDoc。
3. **事件 import 可能膨胀**：`EventDeclarationGenerator.collectImports` 递归无深度限制，可能拉进大量无关类（Phase C 修复）。
4. **第三方类参数名退化**：NekoJS 各模块已在根 build.gradle 统一以 `-parameters` 编译，自身类的声明携带真实参数名；但未开 `-parameters` 的第三方 jar 与 JDK 类（标准 JDK 的 `java.*` class 文件不含 MethodParameters 属性）仍取不到参数名，`TypeReflector` 的 `isNamePresent` 兜底会把这类参数退化为 `arg0`/`arg1`…。
5. **版本偏差**：probe 是手动触发的，新加 mod/注册内容后类型声明会过期，需重新 `/nekojs probe`。
6. **Python stub 泛型展平**：类型变量渲染为 `Any`，不保留泛型参数关系（够日常补全，非完整类型）。

## 测试 probe

`common/src/test/.../probe/` 下有：

- `ProbeBackendRegistryTest` —— backend 注册/冲突/lock 测试。
- `ProbeConfigTest` / `ProbeScanModeTest` —— probe.toml 解析与 SMART/FULL/NONE 扫描语义。
- `ProbeOutputCompatibilityTest` / `LegacyProbeTreeTest` / `TypeScriptNoopIrGoldenTest` —— TS 产物兼容性、未编辑 IR 与旧路径逐字一致（golden）、getter 覆盖在 IR 重渲染路径生效。
- `TypeScriptProbeBackendEditorConfigTest` / `FileEditorConfigContributorTest` —— jsconfig/pyrightconfig 幂等合并。
- `ClassDeclGeneratorTest` / `ManagedApiDeclarationGeneratorTest` —— 类声明生成。
- `api/event/EventBusJSHasListenersTest` —— `hasListeners()` 的 `EventBusBase.isEmpty()` 兜底分支（probe 用它在无监听器时跳过 IR 构建）。

详见这些测试了解 probe 的可测边界。

## 下一步

- [注解体系](注解体系) —— `@Remap`/`@RemapByPrefix`/`@HideFromJS`/`@PlatformAvailability` 让 probe 输出准确。
- [类型适配器](类型适配器) —— `AdapterInputShape` 如何变成 `$Foo_` 别名。
- [插件开发](插件开发) —— `registerProbeBackends` 钩子。
- [Python 脚本](Python-脚本) —— Python 侧脚本语言本身。
- [命令](命令) —— `/nekojs probe` 子命令细节。
