# global/shared 状态迁移材料（ticket 10 / 1.2.0 clean cutover）

> 面向脚本作者的「按类型 global、显式 shared 与候选顶层写集」使用与迁移要点。
> `shared` 是工作名，最终公开符号由 MANAGED_SURFACE（W5）冻结；届时只需同步 binding
> 定义与本表命名，运行时语义不变。

## 1. 变化要点

| 面 | 1.2.0 之前 | 1.2.0 起 |
|---|---|---|
| `global` 的共享范围 | 进程级共享 Map：STARTUP/SERVER/CLIENT/TEST 全部互相可见 | 同一 root 内**按 ScriptType 私有**：同类型多文件与该类型普通 reload 共享；跨类型互不可见 |
| 跨类型共享 | 隐式（写同一个 `global` 即可） | **显式** `shared` 入口（工作名）；沿用同一顶层 Map 语义 |
| reload 期间的顶层写 | 立即写进共享 Map（候选失败也留下） | 顶层 `set/delete/clear` 进候选写集：read-your-writes；成功 commit 才发布，失败全部丢弃 |
| 并发写 | 后写覆盖前写（静默丢写） | 其他 writer 在候选期间改同一受管顶层 key → 冲突被检测、candidate 失败、**其他 writer 的已提交值保留** |
| `global` 与 `globalThis` | 绑定遮蔽同名 globalThis 属性（容器接管 `global` 名） | 同左（分工显式化并钉入 fixture）：`global` = NekoJS 状态容器，`globalThis` = 当前 Context 的语言全局对象，二者不恒等 |
| 保留期限 | 进程级（永不清理） | 同一 root 内跨普通 reload / server stop / 切世界保留；游戏（root）关闭释放；generation close 只失效该 generation 写入的 guest 值 |

## 2. 迁移表（旧跨类型 `global` → 显式 `shared`，逐项）

| # | 旧写法（已删除） | 1.2.0 新写法 | 说明 |
|---|---|---|---|
| 1 | server: `global.handoff = x`；client: `global.handoff` | server: `shared.handoff = x`；client: `shared.handoff` | 跨类型读写的唯一替代路径；client 读旧写法得到 `undefined` |
| 2 | server: `global.count = (global.count \|\| 0) + 1`；startup 里也读写 `global.count` | 需要跨类型共用的计数改 `shared.count`；只在本类型用的保持 `global.count` | 同类型用法**不变**（server↔server 继续 `global`） |
| 3 | startup: `global.config = {...}`；server/client 读 | startup: `shared.config = {...}` | 显式选择共享；类型私有配置继续 `global` |
| 4 | test: `global.fixture = x`（借跨类型准备夹具） | test: `shared.fixture = x` | TEST 与 SERVER/CLIENT 之间同样只经 `shared` |
| 5 | `delete global.foo`（跨类型删别人的 key） | `delete shared.foo` | 删除同样按 store 隔离；跨类型删除只能删 shared 的 |
| 6 | `global.clear()`（清掉所有类型的全局） | `global.clear()` 只清本类型；`shared.clear()` 清共享域 | clear 也进候选写集（失败候选不发布） |
| 7 | 把 `global` 当模块全局对象用（`global.require`、`global.process`） | 改用 `globalThis.require` / `globalThis.process` 或裸标识符 `require` | `global` 是状态容器，不是 Node 语言全局；Node shim 语境里 `global === globalThis` 只在无绑定的 shim 语境成立 |
| 8 | 在 global 里存 guest 函数长期复用（依赖 reload 后仍可调用） | 需要跨 reload 的可调用引用请重新注册（listener/timer），或存可序列化数据 | 存入 Map 不获得永久保活：写入方 generation 销毁时其 guest 函数/对象失效（宿主值保留） |

配套示例（可执行，见 [examples/](examples/)）：
[same-type-global.js](examples/same-type-global.js)、
[explicit-shared-server.js](examples/explicit-shared-server.js) +
[explicit-shared-client.js](examples/explicit-shared-client.js)、
[legacy-cross-type-migration-server.js](examples/legacy-cross-type-migration-server.js) +
[legacy-cross-type-migration-client.js](examples/legacy-cross-type-migration-client.js)、
[failure-retention.js](examples/failure-retention.js)。

## 3. 事务语义速查（顶层 key 级别）

- 进写集的操作：顶层 `set`（`global.k = v` / `shared.k = v`）、`delete`、`clear()`。
- 不进写集（不承诺回滚）：嵌套对象/列表的内部修改、已共享 Java 对象的内部修改、
  高级 Java 访问的任意副作用。需要事务保护时，构造新值后整体替换顶层 key。
- 失败语义：reload 失败（含语句上限/watchdog 终止、EVENT_PLAN 失败、STATE_PLAN 冲突、
  close 抢占）→ 候选写集全部丢弃，旧 active 看到的值不变。
- 冲突语义：候选期间其他 writer（其它类型的 active 脚本、Java 插件侧）提交过同一受管
  顶层 key → 候选失败（`phase=STATE_PLAN domain=global-write-conflict`），对方已提交值保留。
- 联合语义：一次候选同时写 `global` 与 `shared` → 两边联合成功或全部不发布，无半提交。

## 4. 明确不做 / 不是承诺

- `shared` 不是网络同步：只覆盖同一 JVM 内同一 root 的进程内存共享；客户端/服务端
  数据推送继续走 Network / ClientData 域。
- 不提供跨进程、跨重启持久化；世界数据继续由既有数据域负责。
- 不对嵌套结构做深回滚/深拷贝；Map 的并发安全不等于任意 guest 对象可跨线程安全调用。
- 未新增权限系统、第二 runtime owner、网络协议或通用事务框架；高级 Java 访问未收紧。
