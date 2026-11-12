<!-- wiki-page: configuration; locale: cn -->

> **中文** · [English](configuration_us)

<a id="wiki-section-1"></a>
# 配置参考

NekoJS 的可编辑配置集中在游戏目录下的 `nekojs/config/`。引擎权限、声明生成和 AI 创作支持分别配置，开启其中一项不会自动开启其他功能。

<a id="wiki-section-2"></a>
## 应该修改哪个文件

| 文件 | 用途 | 详细说明 |
|---|---|---|
| `nekojs/config/engine.toml` | 脚本权限、模块/JSX 选项、脚本包同步、实验性动态注册和 MixinJS 开关 | 本页 |
| `nekojs/config/probe.toml` | Probe 启停、扫描过滤及声明 backend/输出 | [Probe 类型生成](probe-type-generation_cn) |
| `nekojs/config/ai.toml` | 导出的创作资料、本地 HTTP/MCP 监听器及单独授权的实时控制 | [AI 辅助脚本编写](ai-support_cn) |

生成的 `.neko_probe/` 声明与脚本目录中的 `jsconfig.json` 不是引擎配置。应修改它们对应的规范配置，而不是反复手改生成结果。AI 输出默认位于 `.github/neko_ai`；私有 token 保留在 `nekojs/config/` 下，不得提交到仓库。

<a id="wiki-section-3"></a>
## 引擎默认值

下面列出当前默认配置，不代表建议开启实验功能。TOML 的键名和类型有意义：布尔值不要加引号。根级键必须写在第一个表头之前。

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

| 键 | 默认值 | 含义与边界 |
|---|---|---|
| `allowThreads` | `false` | 允许 guest 创建不受管理的线程；不代表可以并发访问同一个 Graal Context。 |
| `allowReflection` | `false` | 为可信脚本放宽反射相关类限制；其他 host/类限制仍然存在。 |
| `allowAsm` | `false` | 放宽 ASM/字节码相关类限制；不是 MixinJS 的启动开关。 |
| `allowFsWriteOutsideNekojs` | `false` | 将 Node 文件系统写入/删除范围从 `nekojs/` 扩展到游戏目录，不会放开游戏目录外的任意路径；包含性检查仍生效，受保护的引擎配置仍禁止脚本写入。 |
| `enableEsmAuthoring` | `true` | 开启 ESM 创作支持；ESM/CJS 的具体规则见[模块系统](module-system_cn)。 |
| `conciseScriptErrorLogs` | `true` | 默认输出面向源码的简洁错误日志；设为 `false` 可查看详细诊断。 |
| `jsxAutomaticRuntime` | `false` | 选择自动 JSX 转换而非 classic factory；不会因此提供 React 运行时，也不会扩大 UI 支持平台。见 [TypeScript 与 JSX](typescript-and-jsx_cn)。 |
| `scriptMemberValidation` | `true` | 报告受支持的 Binding/回调成员访问错误，不是完整 TypeScript 类型检查。 |
| `packSync.mode` | `"off"` | 可选 `off`、`hashOnly`、`all`；选择同步模式不会绕过包信任、签名或平台限制。见[脚本包](script-packs_cn)。 |
| `packSync.allowUnsigned` | `false` | 默认拒绝未签名的服务端脚本包；不要为了不可信服务器放宽此项。 |
| `dynamicRegistry.enabled` | `false` | 在已实现的平台上启用实验性的启动后注册；不代表所有注册表或 Fabric 动态注册均可用。见[注册新内容](registering-new-content_cn)。 |
| `mixinjs.enabled` | `false` | 仅为启动时显式开关，不解除现有开发环境、profile、信任与线程限制。见 [MixinJS](mixinjs_cn)。 |

<a id="wiki-section-4"></a>
## 生效步骤与旧文件迁移

1. 停止游戏或专用服务器，备份当前文件。
2. 编辑规范配置，确认每个键都处于正确的 TOML 表内。不要把根级键追加在 `[mixinjs]` 下面，也不要重复定义已有表。
3. 重启进程。普通脚本重载不会重建根配置，也不能撤销字节码转换。MixinJS 使用早期启动时的固定配置快照，因此开启与关闭都必须完全重启。
4. 检查启动日志。格式错误的引擎配置可能回退到默认值；早期 MixinJS 设置缺失、不可读、解析失败或不是布尔值时不会开启。应修正文件，而不是假定所有设置都已生效。
5. 编辑器相关设置变化后，运行 `/nekojs probe` 更新 TypeScript 工作区；Python 使用 `/nekojs probe python`。这些命令不能代替引擎重启。

规范引擎配置不存在、但游戏根目录下存在 `config/nekojs-engine.toml` 时，会**只读回退**到旧文件。它不会被自动移动或重写。请备份后手动迁移到 `nekojs/config/engine.toml`；规范文件存在时优先使用它。不要同时维护内容冲突的两份配置。

**已移除的执行控制：** `scriptEvaluationTimeoutSeconds`、`scriptStatementLimit`、`scriptRunawayTimeoutSeconds` 已不再控制普通脚本。可写配置加载时会清理旧键；旧位置的只读文件不会被改写。没有隐藏的替代执行总超时或看门狗。同步死循环可能阻塞所属线程，始终不完成的顶层 `await` 可能让加载持续等待。HTTP/作者工具的请求等待期限、生命周期取消以及独立 Mixin 声明求值器的固定预算属于其他机制，仍然保留。

<a id="wiki-section-5"></a>
## 安全与排查

只运行可信脚本和脚本包。权限开关不能证明任意 Java 对象访问或 host 调用安全，不应为了消除无关报错而开启。MixinJS 不要求开启 `allowAsm` 或 `allowReflection`。AI 监听器和实时控制应保持默认关闭，除非确实需要；不要把 bearer token 随整合包分发。

脚本失败可查看[常见问题](faq_cn)和[错误码参考](error-reference_cn)。脚本、数据和声明的重载范围不同，相关操作见[命令](commands_cn)。

<!-- wiki-nav -->

---

[上一篇: 快速开始](quick-start_cn) · [目录](Home) · [下一篇: 常见问题](faq_cn)
