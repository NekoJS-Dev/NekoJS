<!-- wiki-page: event-reference; locale: cn -->

> **中文** · [English](event-reference_us)

<a id="wiki-section-1"></a>
# 事件参考

<a id="wiki-server-events"></a>
<a id="wiki-item-events"></a>
<a id="wiki-capability-events"></a>
<a id="wiki-command-events"></a>
<a id="wiki-keybind-events"></a>

事件是 NekoJS 处理游戏逻辑的核心。当游戏中发生某件事（如玩家登录、物品合成、按下按键），NekoJS 会触发对应事件。

<a id="wiki-section-2"></a>
## ⚡ 如何使用事件

所有事件都以 Events 结尾，通过 .addListener(callback) 注册。

```javascript
// 示例：玩家登录时发送消息
PlayerEvents.loggedIn(event => {
  event.player.sendMessage('欢迎回来！');
});
```

---

<a id="wiki-section-3"></a>
## 📋 常用事件分类

| 分类 | 核心事件 | 用途 |
|---|---|---|
| **玩家事件** | `PlayerEvents.loggedIn`, `PlayerEvents.loggedOut` | 处理登录/退出逻辑 |
| **世界事件** | `WorldEvents.tick`, `WorldEvents.blockChange` | 监听世界逻辑 |
| **交互事件** | `InputEvents.key`, `InputEvents.mouse` | 处理玩家输入 |
| **脚本事件** | `StartupEvents.registry` | 注册新内容 (仅限 startup) |
| **配方/物品** | `ServerEvents.recipes`, `ItemEvents.entityInteract` | 改配方、右键事件 |

---

<a id="wiki-section-4"></a>
## 🛠 事件处理守则

1. **不要在事件里死循环**：例如在 `BlockEvents.break` 里又去破坏同一个方块，这会引发崩溃。
2. **按类型分文件**：将事件监听器按功能逻辑放在不同的 .js 文件里（如 `recipes.js`, `player_events.js`），便于维护。
3. **性能开销**：高频触发的事件（如 `WorldEvents.tick`）里只写最必要的逻辑。

> **获取事件详细参数**：每个事件对象 event 都包含了上下文信息。你可以使用 /nekojs probe 生成 .d.ts 类型定义，编辑器即刻会提示 event 下有哪些属性和方法。

<!-- wiki-nav -->

---

[上一篇: Python 特性清单](python-feature-support_cn) · [目录](Home) · [下一篇: AI 辅助脚本编写](ai-support_cn)
