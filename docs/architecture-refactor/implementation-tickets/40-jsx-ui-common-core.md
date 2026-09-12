# 40: JSX UI common core、公开契约与 Fake Host Proof

**What to build:** 在 common 中交付不依赖 Minecraft/loader 的 JSX UI 核心路径：automatic runtime 产生不可变 VNode，函数组件与 Fragment 展开为候选树，signal/store 依赖触发了受影响 root reconcile，keyed reconciler 通过公开 host Adapter contract 创建、更新、排序和释放 fake host node。同一票冻结最小 UI facade、primitive props、事件对象、signal/store、root handle、布局约束和诊断字段的 managed contract，并让 TypeScript JSX declaration / Probe 输出与 fake runtime 语义一致。

**Blocked by:**
- [05: 单 owner 预整理：闭合两个 loader 的运行生命周期入口](05-runtime-root.md)
- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)
- [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [ ] `jsx` / `jsxs` / `Fragment` 到 VNode、函数组件、条件、数组、spread、key 和 children 的语义有公开 contract fixture；VNode 不持有 Minecraft、loader、`GuiGraphics`、Font、Screen、GL 或长期可变 host 对象。
- [ ] 初始 primitive 集合 `screen`、`panel`、`row`、`column`、`stack`、`scroll`、`label`、`button`、`input`、`image`、`spacer` 有唯一登记表；未知 primitive、非法 props、非法布局值和重复 key 显式失败。
- [ ] signal/store 的读取、写入、批量失效、依赖重建、disposed root 写入和 owner-thread 拒绝/排队语义可从公开调用者 Interface 观察，不检查私有依赖集合。
- [ ] reconciler 的候选 render、结构/属性/能力校验、原子提交、keyed diff、事件替换、子节点排序、布局失效和幂等 cleanup 均可由 fake host 观察；失败更新保留最后一次有效树。
- [ ] render、component、layout、event、host update 阶段错误可区分并进入统一诊断 seam；单个 event 错误不影响其它控件，render/layout 失败不提交半成品树。
- [ ] UI facade、primitive、props、事件对象、signal/store 和 capability 进入 09 的 NormativeApiContract 派生链；TypeScript JSX declaration 与 Probe 输出同源生成，不存在声明有而 runtime 无、或 runtime 有而声明缺失的能力。
- [ ] common Module 不导入 Minecraft/loader，不新增 Gradle 项目、API jar、第二 runtime owner、第二事件 bus 或独立声明事实源。
- [ ] close/dispose、重复 close、候选失败清理和最后有效树保留都有 fake Adapter 测试；cleanup 不推迟到后续真实客户端票。

## Dependency rationale

- [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
