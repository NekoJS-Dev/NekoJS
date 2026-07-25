# Probe 类型生成

> Probe 是 NekoJS 内置的 **`.d.ts` 类型声明生成器**（ProbeJS 等价物）。它反射运行中的 Java 类，生成 TypeScript 声明文件，配合 `jsconfig.json` 让 IDE 获得补全。

## 工作流程

用户跑 `/nekojs probe` 时：

```text
1. 收集 snapshot
   ├─ NekoJSPluginRuntime（bindings/events/adapters/recipes/...）
   └─ NekoCatalogPlatformProvider（注册表字面量、namespace handler、host extension）

2. seed + BFS 类发现（最大深度 5）
   ├─ 种子：事件类型、dispatch key 类型、绑定的 Java 类型
   └─ 按前缀过滤：java. / net.minecraft. / net.neoforged. / com.tkisor.nekojs.

3. 适配器别名准备（AdapterAliasGenerator）
   └─ 每个 JSTypeAdapter.inputShapes() → $Foo_ union 别名

4. 并行预生成（≤8 线程，Class.forName(initialize=false) 避免 <clinit>）

5. 并行生成文件
   ├─ @package/<包名>/index.d.ts      （Java 类声明）
   ├─ @side-only/{startup|server|client}/events/index.d.ts
   ├─ @side-only/{side}/events/recipes/index.d.ts
   ├─ @side-only/{side}/bindings/index.d.ts
   ├─ @special/types/index.d.ts        （注册表字面量 union）
   ├─ @manual/index.d.ts               （手动声明）
   └─ side 根 index

6. 原子交换：生成到 .staging → 替换 .neko_probe → 旧的备份到 .old
   （失败则保留旧 .neko_probe）

7. 更新 jsconfig.json 路径
```

## 输出布局

```text
.neko_probe/
├── jsconfig.json                 # 内部解析用
├── @package/
│   ├── com/tkisor/nekojs/.../index.d.ts
│   ├── net/minecraft/.../index.d.ts
│   └── ...
├── @side-only/
│   ├── startup/{events,bindings}/index.d.ts
│   ├── server/{events,events/recipes,bindings}/index.d.ts
│   └── client/{events,bindings}/index.d.ts
├── @special/types/index.d.ts     # 注册表字面量（如所有 item id 的 union）
└── @manual/index.d.ts            # 手动 .d.ts 声明
```

每个 `*_scripts/` 目录下的 `tsconfig.json` 会把这些路径映射进来：

```json
{
  "compilerOptions": {
    "paths": {
      "java:*":          ["../../.neko_probe/@package/*"],
      "@side-only/*":    ["../../.neko_probe/@side-only/*"],
      "@special/*":      ["../../.neko_probe/@special/*"]
    }
  }
}
```

## 模块布局约定（不变量）

以下是 probe 输出的**稳定约定**，第三方 probe 替换实现也要遵守：

| 约定 | 含义 |
|---|---|
| `@package/` | Java 包按目录树镜像 |
| `@side-only/<side>/` | 按脚本侧别分（startup/server/client） |
| `@special/types/` | 注册表字面量 union（如所有物品 id） |
| `@manual/` | 插件/平台手动写的 `.d.ts` |
| `$ClassName` | 类声明命名约定（避免与 JS 关键字冲突） |
| `$Foo_` | 适配器输入别名（`Foo` 是目标类型名） |
| `java:` / `@side-only` / `@special` | 模块标识符前缀 |

## 关键类

| 类 | 位置 | 职责 |
|---|---|---|
| `ProbeOrchestrator` | `common/.../probe/` | 内置生成器主流程；原子 staging 交换；BFS 类发现 |
| `ProbeRegistry` | `common/.../probe/` | 单生成器策略：内置是 fallback，第三方可 `setGenerator` 替换，多第三方冲突报错 |
| `ProbeGenerator` | `common/.../probe/` | 生成器接口：`generate(snapshot, outputDir)` |
| `ClassDeclGenerator` | `common/.../probe/` | Java 类 → TS 类声明 |
| `IndexFileGenerator` | `common/.../probe/` | 每包的 `index.d.ts`、import 收集 |
| `BindingDeclarationGenerator` | `common/.../probe/` | `@side-only/{side}/bindings/index.d.ts` |
| `EventDeclarationGenerator` | `common/.../probe/` | `@side-only/{side}/events/index.d.ts` |
| `RecipeEventDeclarationGenerator` | `common/.../probe/` | `event.recipes.<ns>.<type>(...)` 类型 |
| `AdapterAliasGenerator` | `common/.../probe/` | 从 `AdapterInputShape` 渲染 `$Foo_` 别名 |
| `AgentTemplateGenerator` | `common/.../probe/` | `.github/agents/*.agent.md`（AI 辅助文件） |
| `TypeConverter` / `TypeAliasRegistry` | `common/.../probe/types/` | Java `Type` → TS 字符串 |

## 数据源：catalog snapshot

`NekoScriptCatalog.snapshot(runtime)` 生成不可变 `NekoScriptCatalogSnapshot`，生成器只消费 snapshot（纯函数 `(snapshot, outputDir)`）。这是关键解耦点，让单生成器替换策略可行。

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

`registry.register("Foo", new FooJS())` —— probe 会反射 `FooJS` 的公共方法。加 `@Doc`/`@Param` 让 `.d.ts` 带文档（见 [注解体系](注解体系)）。

### 注册表字面量

通过 `NekoCatalogPlatformProvider.registryTypes()` 提供注册表 id 列表，probe 生成 `@special/types/index.d.ts`（如所有物品 id 的字符串字面量 union），让脚本里 `'minecraft:stone'` 有补全。

### 手动声明

`NekoCommonManualDeclarations`（common）/ 平台 manual declarations 提供手写的 `.d.ts` 片段，进 `@manual/index.d.ts`。用于无法反射表达的内容。

### Snippet

`NekoCatalogPlatformProvider.snippets()` 提供 IDE 代码片段。

## Probe 的当前局限

> 这些是已知边界，列在这里让你知道。

1. **`@Remap`/`@HideFromJS` 当前在 probe 不生效**：`ClassDeclGenerator` 用裸 `getDeclaredMethods()`，没走 `MemberVisibilityQuery`。计划修复（Phase A）。
2. **JSDoc 覆盖少**：`@Doc`/`@Param` 体系正在接入 `ClassDeclGenerator`（Phase B）。
3. **事件 import 可能膨胀**：`EventDeclarationGenerator.collectImports` 递归无深度限制，可能拉进大量无关类（Phase C 修复）。
4. **配方参数名退化**：编译没开 `-parameters` 时，参数名退化成类型名（Phase C 修复）。
5. **版本偏差**：probe 是手动触发的，新加 mod/注册内容后类型声明会过期，需重新 `/nekojs probe`。

## 替换内置 probe

第三方可以实现 `ProbeGenerator` 并通过 `ProbeRegistry.setGenerator(yourGenerator, "your-plugin")` 替换内置：

```java
@Override
public void registerProbeGenerator(ProbeRegistry registry) {
    registry.setGenerator(new MyBetterProbeGenerator(), "my-addon");
}
```

**规则**：
- 第一个第三方注册的赢，内置降为 fallback。
- 多个第三方注册 → 启动时崩溃（确定性冲突，列出所有注册者）。
- bootstrap 完成后注册 → fail-fast。
- 你的生成器仍要遵守上面的模块布局约定，否则 `jsconfig.json` 路径失效。

## 测试 probe

`common/src/test/.../probe/` 下有：
- `ProbeRegistryTest` —— 注册策略/冲突/fail-fast 测试（4 个）。
- `ClassDeclGeneratorTest` —— 类声明生成测试。

详见这些测试了解 probe 的可测边界。

## 下一步

- [注解体系](注解体系) —— `@Doc`/`@Param`/`@Remap`/`@HideFromJS` 让 probe 输出准确。
- [类型适配器](类型适配器) —— `AdapterInputShape` 如何变成 `$Foo_` 别名。
- [插件开发](插件开发) —— `registerProbeGenerator` 钩子。
