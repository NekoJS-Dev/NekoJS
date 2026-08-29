# 脚本注册 API：一次性切换，无兼容层

原建图约束"脚本 API 必须稳定"在 R3 grilling 中被用户明确解除：**写法简洁好用优先，破坏性可接受**。据此，现状 12 个类型化事件入口（`RegistryEvents.item/block/fluid/...`）+ 链式 builder + wiki 方法表整体切换为新 API，**不做兼容层、不留 deprecated 包装**。

1. **单一入口** `RegistryEvents.register(event => ...)`（PR #37 §4.1 形态）；旧 12 个类型化事件入口删除。
2. **event 方法面**（三个，全部平台无关，ADR-0004 分层）：
   - `event.<registry>(id, cb)`：每注册表**糖方法**，自动从 `registry_types` 注册的 default 类型派生（有 default 才有糖方法；引擎生成，不手写）——如 `event.item('mymod:x', b => { b.maxStackSize = 16 })`；
   - `event.custom(id, typeName, cb)`：命名类型；
   - `event.register(id, supplier)`：裸 Supplier。
3. **Builder 写法 = ADR-0005 public field**（`b.maxStackSize = 16`）；复合动作保留 `void` 方法（`b.food(cb)` / `b.tag(...)` / `b.item(cb)`）。
4. **迁移 UX 替代兼容层**：wiki 附迁移表。老脚本启动即报错（普通成员检查报错，**不带修复指引**——P2 落地时用户裁定去掉 validator 里的旧写法迁移提示，报错保持简洁）。
5. **probe/catalog**：机制已有（遍历 catalog 自动生成声明），无新工作；验收 = 声明含新 API 全量（归 G1 检查单）。

## Consequences

- **约束修订**：脚本 API 从"必须稳定"改为"简洁好用优先，一次性 breaking + validator 迁移提示"（地图 Notes 与 `CONTEXT.md` 术语已同步修订）。
- wiki《注册新内容》《快速开始》重写归 G1 编排。
- 糖方法随 `registry_types` 注册自动出现/消失——第三方注册表同样免费获得糖方法（判据③加分）。
