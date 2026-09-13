# Ticket 11 迁移材料：模块身份、缓存与生命周期

> 面向维护者与脚本作者的迁移说明。配套示例见同目录 `minimal-example.js` / `.cjs` / `.mjs` /
> `minimal-example-entry.mjs`，实施报告见 `REPORT.md`。

## 1. 脚本作者：无破坏性变更

**结论：JS / CJS / ESM 的编写方式与语义零变化。** 语言范围、模块模式分派、`require`/`import`
行为、source map 定位和 reload 触发方式全部保持原样。

| 你可能关心的事 | 本票之后 |
|---|---|
| `.js` / `.mjs` / `.cjs` 的模块模式 | 不变（见 `wiki/模块系统.md` 的扩展名分派表） |
| `require` / `import` / 循环依赖 / 顶层 await | 不变 |
| `nekojs/node_modules` 下的共享包 | 不变；按类型 reload 仍不清理它们 |
| 错误定位（文件:行:列） | 不变；跨 import 的执行错误仍回到原始文件 |
| `/nekojs reload` | 不变；脚本编辑后仍会自动失效并重跑 |

新增的内部可观察事实（不改变脚本写法）：prepared module 现在携带 **language id** 与**稳定
cache key**，且 cache 命中判定同时要求内容指纹与语言身份一致。

## 2. 插件作者：可能受影响的面

本票把模块管线的静态入口删除，改为构造注入。以下变更**只影响直接构造这些类的 Java 代码**
（NekoJS 插件若只注册语言/绑定，不受影响）。

### 2.1 删除的 API（原为 static）

| 旧写法 | 新写法 |
|---|---|
| `NekoModulePipeline.bindLegacyInstance(pipeline)` | 不再需要——在装配处构造 `NekoModulePreparation(pipeline, paths)` 并注入 |
| `NekoModulePipeline.legacyInstance()` | 用你持有的 `NekoModulePreparation` / `NekoModulePipeline` 实例 |
| `NekoModulePipeline.legacyPrepare(file, source)` | `new NekoModulePipeline(...).prepare(file, source)` |
| `NekoModulePipelineCache.prepare(path)` | `preparation.prepare(path)` |
| `NekoModulePipelineCache.clear()` / `clear(type)` / `invalidate(path)` | 同名的 `NekoModulePreparation` 实例方法 |

### 2.2 构造器签名变化

| 类 | 新增参数 |
|---|---|
| `NekoRuntimeRoot` | `NekoModulePreparation modulePreparation` |
| `NekoSandboxFactory` | `NekoModulePreparation preparation` |
| `NekoScriptModuleLoaderHost` | `NekoModulePreparation preparation`（单参构造已删除） |
| `NekoEsmLinker` | `NekoModulePreparation preparation` |
| `NekoNativeEsmSourceRewriter` | `NekoModulePreparation preparation` |
| `ModuleReloadCoordinator` | `NekoModulePreparation preparation`, `NekoJSPaths paths` |
| `NekoNodeModuleInstaller.install(...)` | 重载新增 `NekoModulePreparation preparation` |
| `ScriptManager` | `NekoModulePreparation preparation` |
| `NekoModuleReadService` | 由静态工具类改为实例：`new NekoModuleReadService(preparation, filePolicy)` |
| `NekoJSFileSystem` | `NekoModuleReadService readService`（单参构造已删除） |

### 2.3 注册语言

不变。`NekoLanguagePlugin`（lexer/parser/lowering）与 `IScriptCompiler` 两种注册方式都仍然有效：

```java
// 推荐：完整语言插件
registry.register(NekoTypeScriptLanguagePlugin.INSTANCE);
// 兼容：单方法编译器（会被适配成语言插件，language id = "legacy:<ext>"）
registry.registerLanguage("mylang", Set.of(".ml"), new MyCompiler());
```

## 3. 缓存与 reload 语义对照（迁移时的行为核对表）

| 场景 | 行为 |
|---|---|
| 同一文件、内容/语言/mode 未变，重复加载 | 命中 prepared cache，**不重新准备**；CJS 模块体只执行一次 |
| 内容变化（含等长覆盖、mtime 未变） | SHA-256 内容指纹不同 → 旧条目失效 → 重新准备 |
| 路径变化 | cache key 含源路径 → 不同条目 |
| `.cjs` ↔ `.mjs`（mode 变化） | cache key 含请求 mode → 不同条目 |
| 插件 `replaceLanguage` 换掉某扩展名的语言 | language id 变化 → 旧条目失效（本票新增的失效维度） |
| `/nekojs reload`（按 ScriptType） | 只清该类型脚本目录下的 prepared 条目 + 对应 source map；`node_modules` 共享条目保留 |
| `NekoRuntimeRoot.close()` | 释放该 runtime 持有的全部 prepared 条目与 source map |
| 两个独立 root / 两个独立 preparation | **零共享**——没有 process-wide static cache |

## 4. 回滚

本票无数据格式、配置键或用户数据变更，回滚仅为源码回滚（revert 本票 commit 区间）。无迁移遗留状态。
