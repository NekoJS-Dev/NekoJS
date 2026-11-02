<!-- wiki-page: script-api-style; locale: cn -->

> **中文** · [English](script-api-style_us)

<a id="wiki-section-1"></a>
# 脚本 API 约定与对象参数

NekoJS 的脚本入口以**事件组、命名 Binding、事件对象上的操作、配置回调和 Bean 属性**为主。参考其他项目的实现机制不意味着照搬它的脚本调用形态。这里区分「当前可调用接口」与「后续应统一的设计」，不会把尚未实现的写法当成教程。

<a id="wiki-section-2"></a>
## 事件组负责订阅

事件名是组上的成员；过滤条件或选择器（如有）放在回调之前。不是用一个对象创建整组事件，也不是给组赋值来定义新事件。


```js
// server_scripts/welcome.js
PlayerEvents.loggedIn(event => {
    event.player.sendMessage("Welcome to NekoJS!");
});
```

回调内的数据和事件操作从 event 取得，一般用途的帮助方法从 Binding 取得。事件触发时机、可取消性、Script Type 与平台支持必须分别查看[事件参考](event-reference_cn)，不能从名称猜测。

<a id="wiki-section-3"></a>
## Binding 与配置回调

例如 Item.of 是命名 Binding 上的方法。可配置定义使用必要参数在前、配置回调在后的形式；以下是实际注册入口，放入 startup_scripts。静态注册通常需要进程重启，动态注册属于独立受限能力，见[注册内容](registering-new-content_cn)。

```js
// startup_scripts/content.js
RegistryEvents.register(event => {
    event.item("nekojs_example:ruby", build => {
        build.maxStackSize = 16;
    });
});
```

这里的 event 负责创建，build 负责配置；不要把它们混成同一个对象。不需要额外调用 build()。只有确实返回 builder 的方法才能链式调用。

<a id="wiki-section-4"></a>
## Bean 属性不等于任意字段

JavaBean 的公开读取/写入能力在脚本面通常表现为属性，但必须以实际包装器与生成声明为准：

- 只读属性不能赋值，只写属性不能读取。
- 带参数、执行操作或验证的普通方法不能仅因名字含 get/set 就改写成属性。
- event.player、注册 builder 的 maxStackSize 等使用实际公开的属性；不要据 Java 类名凭空猜成员。
- 声明与运行时不一致时应修复两者的共享来源，而不是教用户绕过验证。

<a id="wiki-section-5"></a>
## 哪些 {} 是合理的数据

| 形态 | 含义 | 是否应一律换成 builder |
|---|---|---|
| event.remove({ id: "minecraft:stick" }) | 配方筛选数据 | 否；是当前过滤器契约 |
| 配方 custom JSON、组件与 shader JSON | 数据格式/资源内容 | 否；保留其结构与校验 |
| JSX props、UI.createRoot 的属性/宿主选项 | UI 的属性数据与运行时接口 | 否；不能凭括号形状判定 |
| MCP/HTTP 请求体 | 外部协议消息 | 否；不是游戏脚本事件接口 |
| 创建一个行为定义所需的大量可选配置 | 可配置定义接口 | 应审查是否适合配置回调与具名操作 |

<a id="wiki-section-6"></a>
## 显式数据选项与实际 builder

以下是当前已实现的两种不同职责：

- ClientEvents.hudRender(id, options, callback) / worldRender：options 是显式调度数据 { layer, priority }，也保留层名字符串和整数优先级简写，以及 (id, callback)。字段与层名由运行时 schema 和 Probe 共用；未知字段、错误层或非 32 位整数优先级会失败。它不是定义 builder。
- ClientEvents.postEffects(event => event.register(id, build => { ... }))：register 提供真正的节点专属后处理 builder，配置回调在最后。26.x 示例属性为 build.blurRadius = 4、build.blurRounds = 1；1.21.1 使用自己的 chainJson/program 字段。回调后 builder 封闭，不需要调用 build()。兼容旧对象/Map 重载，但同样严格校验字段与值。

它们的生命周期、完整示例与平台限制见[客户端绘制与后处理](client-rendering_cn)。后处理「定义已安装」不等于效果已成为可激活资源。

运行时、Probe 与失败保留必须一起验证；对象外形本身不是是否符合约定的判断依据。shader JSON、配方筛选和渲染调度数据仍保留各自适合的数据结构。

<a id="wiki-section-7"></a>
## 判断参考实现是否符合 NekoJS

检查顺序是：事件是否挂在正确组与 Script Type → 回调是否最后 → 定义与配置职责是否分开 → 属性读写与方法是否准确 → 候选失败是否保留旧代次 → 声明是否与实际调用一致。来源注释能证明某些底层机制参考 Katton，但不能据此断言每个对象式参数都来自 Katton，或每个移植接口都不合格。

遇到未列出的接口，先查当前实例的 Probe 声明与[AI 精确查询](ai-support_cn)，再报告缺失或不一致；不要从其他项目示例直接推导 NekoJS 成员。

<!-- wiki-nav -->

---

[上一篇: 脚本基础](script-basics_cn) · [目录](Home) · [下一篇: 脚本属性](script-properties_cn)
