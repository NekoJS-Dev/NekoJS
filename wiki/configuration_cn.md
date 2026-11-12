<!-- wiki-page: configuration; locale: cn -->

> **中文** · [English](configuration_us)

<a id="wiki-section-1"></a>
# 配置参考

NekoJS 的配置文件都在游戏目录下的 `nekojs/config/` 里。大多数人不需要改任何配置，用默认值就能正常写脚本。

<a id="wiki-section-2"></a>
## 有哪些配置文件

| 文件 | 管什么 | 详细说明 |
|---|---|---|
| `nekojs/config/engine.toml` | 脚本权限、模块和 JSX 选项、错误日志、脚本包同步、实验功能 | 本页 |
| `nekojs/config/probe.toml` | 代码补全声明怎么生成 | [Probe 类型生成](probe-type-generation_cn) |
| `nekojs/config/ai.toml` | AI 辅助资料导出、本地 HTTP/MCP 服务 | [AI 辅助脚本编写](ai-support_cn) |

`engine.toml` 和 `probe.toml` 在第一次启动时自动创建。以后缺少的键也会自动补上，并附带英文说明注释。

游戏目录下的 `.neko_probe/` 和脚本目录里的 `jsconfig.json` 是自动生成的文件，不是配置。手动修改会被下一次生成覆盖。

<a id="wiki-section-3"></a>
## engine.toml 的默认内容

```toml
allowThreads = false
allowReflection = false
allowAsm = false
allowFsWriteOutsideNekojs = false
enableEsmAuthoring = true
conciseScriptErrorLogs = true
jsxAutomaticRuntime = false
scriptMemberValidation = true

[packSync]
mode = "off"
allowUnsigned = false

[dynamicRegistry]
enabled = false

[mixinjs]
enabled = false
```

| 键 | 默认值 | 作用 |
|---|---|---|
| `allowThreads` | `false` | 允许脚本自己创建后台线程。可能造成卡顿或资源泄漏。 |
| `allowReflection` | `false` | 允许脚本用 Java 反射绕过访问限制、修改私有数据。 |
| `allowAsm` | `false` | 允许脚本直接操作 Java 字节码。用错可能导致严重崩溃。 |
| `allowFsWriteOutsideNekojs` | `false` | 允许 `fs` 写入和删除游戏目录下的任何位置，而不只是 `nekojs/`。游戏目录以外仍然禁止，`nekojs/config/` 也仍然不能写。 |
| `enableEsmAuthoring` | `true` | 支持 `import` / `export` 写法。只有需要纯 CommonJS `require` 行为时才关掉。见 [模块系统](module-system_cn)。 |
| `conciseScriptErrorLogs` | `true` | 错误日志只显示和脚本源码有关的简短信息。改成 `false` 会输出完整堆栈，适合排查疑难问题。 |
| `jsxAutomaticRuntime` | `false` | JSX 改用 automatic 方式编译，从内置模块 `nekojs/jsx-runtime` 导入。默认是 classic 方式。见 [TypeScript 与 JSX](typescript-and-jsx_cn)。 |
| `scriptMemberValidation` | `true` | 加载脚本前检查绑定和事件回调上的成员名，把拼写错误报到错误面板。这不是完整的类型检查。关掉可以省下这部分解析时间。 |
| `packSync.mode` | `"off"` | 多人游戏中服务器是否把脚本包发给客户端。`off`：不发；`hashOnly`：只发哈希，客户端不运行服务器的包；`all`：发送并在客户端运行。见 [脚本包](script-packs_cn)。 |
| `packSync.allowUnsigned` | `false` | 是否接受没有签名的服务器脚本包。默认拒绝，并断开连接提示玩家。 |
| `dynamicRegistry.enabled` | `false` | 实验功能：允许服务器启动之后再注册新内容。不是所有平台和注册表都支持。见 [注册新内容](registering-new-content_cn)。 |
| `mixinjs.enabled` | `false` | 启用 MixinJS。见 [MixinJS](mixinjs_cn)。 |

<a id="wiki-section-4"></a>
## 修改后怎么生效

1. 关闭游戏或专用服务器。
2. 编辑 `nekojs/config/engine.toml`。
3. 重新启动。

`engine.toml` 只在启动时读取一次，`/nekojs reload` 不会重新读它。

启动后，游戏日志里会出现 `Engine config loaded. Unsafe features enabled: false`（开了权限开关时是 `true`），说明配置已读取。

编写 TOML 时注意：

- 布尔值不加引号：写 `allowThreads = true`，不要写 `"true"`。
- 没有表头的键（比如 `allowThreads`）必须放在第一个 `[...]` 表头之前。写在 `[mixinjs]` 下面就变成了 `mixinjs.allowThreads`，不会生效。
- 同一个表头不要写两次。
- 文件格式错误，或者顶部的键类型写错时，日志会警告 `Failed to load nekojs/config/engine.toml, using default sandbox config`，这时**所有设置都按默认值处理**。

改了 `jsxAutomaticRuntime` 之后，再执行一次 `/nekojs probe`，让编辑器的 `jsconfig.json` 跟着更新。

<a id="wiki-section-5"></a>
## 安全建议

- 只运行你信任的脚本和脚本包。
- `allowThreads`、`allowReflection`、`allowAsm`、`allowFsWriteOutsideNekojs` 都是放宽限制的开关。只在确实需要时打开，不要为了消除一条报错就开。
- 会连接陌生服务器的客户端，保持 `packSync.allowUnsigned = false`。
- 开启 MixinJS 不需要同时打开 `allowAsm` 或 `allowReflection`。
- 脚本不能修改 `nekojs/config/` 里的文件。
- AI 服务的访问令牌保存在 `nekojs/config/ai.token`。不要把它提交到代码仓库，也不要随整合包分发。

<a id="wiki-section-6"></a>
## 旧版配置

**旧文件位置。** 早期版本把引擎配置放在 `config/nekojs-engine.toml`。只有 `nekojs/config/engine.toml` 不存在时，NekoJS 才会读取旧文件，并在日志里提示迁移。旧文件只读，不会被自动移动或修改。请手动把内容搬到 `nekojs/config/engine.toml`，然后删掉旧文件。

**已删除的键。** `scriptEvaluationTimeoutSeconds`、`scriptStatementLimit`、`scriptRunawayTimeoutSeconds`、`prependRequirePatch`、`useNekoScriptLoader`、`useNativeEsmLoader` 已经不再使用，启动时会自动从文件里删掉。现在脚本没有执行超时：脚本里的死循环会让游戏卡住，需要自己避免。

如果脚本出错，先看 [常见问题](faq_cn) 和 [错误码参考](error-reference_cn)。

<!-- wiki-nav -->

---

[上一篇: 快速开始](quick-start_cn) · [目录](Home) · [下一篇: 常见问题](faq_cn)
