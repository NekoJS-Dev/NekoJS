# 2026-09-29 票 36 试做会话·第一场(维护者操作,部分完成)

主会话 agent 启动 `:26.1.2:runClient`(mult@f26b6ddf,含两个热修),维护者进世界操作。
材料:`../2026-09-29-ticket36-trial-prep/`(cookbooks + author-tasks,11 类任务全部部署于
run 目录)。本场为**首场部分会话**:进世界验证 + 输入链复验 + 试做缺陷暴露;截图
(D6 颜色复验)与任务逐项走查未完成,留下一场。

## 已验证(维护者真机)

| 项 | 结果 | 证据 |
|---|---|---|
| **进世界(热修复验)** | ✅ AddressArgument 注册热修当场验证——首轮「无效的玩家数据/进不去」消除,维护者成功进入新世界 | client-session.txt 无 `Unrecognized argument type`/`Couldn't place player` 复发 |
| float 强转热修保持 | ✅ t36-04(item/block modification,含 `friction = 0.9`)在场,SERVER reload 无 DOMAIN_PLAN 失败(修复前整相位失败) | client-session.txt 无 `item-block-modification` 域错误 |
| 输入链(任务6腿) | ✅ 真实按键 40 个 T26 输入标记(PRESSED/CONSUMECLICK 循环) | log-excerpt.txt |
| 旧冒烟状态排查 | 「无效的玩家数据」首轮=旧世界遗留 `t21:demo_ruby`(gate 关)不可解析,清世界后仍复现→定位为 AddressArgument 回归(见下) | 会话过程记录 |

## 试做发现的缺陷(AC10「待修复」记录,均已修或待修)

- **F-T0(关键,已修)**:F1 修复包引入的 `AddressArgument` 未注册 `ArgumentTypeInfos` →
  真实客户端配置阶段命令树序列化抛 `Unrecognized argument type` → **无法进世界**。
  RCON 控制台验证存在结构盲区(不序列化命令树)。热修 `f26b6ddf`(双 loader 注册+行为级
  测试三节点+fabric 套件绿)——**本场真机复验通过**。教训已记入 F1-F4 包 README。
- **F-T3(已修,本场确认保持)**:试做任务 04 触发 `Value.asFloat()` 拒收 `0.9`(D6 同类),
  修复前毒化整相位。热修 `ScriptNumberCoercion`(三 coerce 缝)——本场真机确认。
- **F-T1(已修文档,真机待复验)**:任务 01(票 15 基线示例)`event.custom('mymod:art','art',...)`
  的全局类型名 `'art'` 在 26.1.2 全注册表不存在 → 启动期 EXECUTION 失败
  (`unknown type name 'art'`)。票 15 基线示例过期——owner 15 域文档修正。
- **F-T2(已修引擎,真机待复验)**:任务 06(票 26 基线示例)`hudRender(..., (ctx, gui) => ...)`
  回调**参数**被 binding-preflight 判为自由标识符(`Unknown identifier 'ctx'`)→ CLIENT
  脚本装载失败。票 26 的 JVM 证据(dispatchHud 探针)与真机腿(自写单参脚本)均未覆盖
  基线示例原样运行——preflight 对「直接调用即注册」API 回调参数的处理缺陷,或示例签名
  过期;owner 26 域 triage。

## F-T1 修复记录（2026-10-04，生命周期边界修复）

- 根因确认：26.1.2 的 `minecraft:painting_variant` 出现在 `Registries` 数据注册表集合，但不出现在实际 `BuiltInRegistries` 静态注册表或 NeoForge `RegisterEvent` pass 中；它由 datapack/dynamic registry loader 管理。StartupRegistryRuntime 因此只能收集，无法通过 `RegisterEvent` 抽干，最终报告 `registration pass never fired`。
- 已移除 `paintingVariant` startup builder/type/declaration 与对应 TS/Python golden 输入；保留 `PaintingVariantBuilder` 作为未来 datapack/resource-generation 路径候选。启动文档与票 36 任务示例改用实际有 `RegisterEvent` pass 的 `villagerType`。
- 这不是把动态注册伪装成启动注册：painting variants 必须另走 datapack/resource generation 设计；本轮只关闭错误 public route，未宣称 painting 实际注册成功。

## F-T2 修复记录（2026-10-03，engine fix，真机复跑待维护者完成）

- 根因裁定为 common `ValParser`：括号箭头 `(ctx, gui) => ...` 未被解析为 `ArrowFunc`，导致 `GlobalBindingMemberValidator` 在真实 known-globals 环境中把 `ctx`/`gui` 误报为未知标识符；示例双参数签名与 `ClientRenderRegistry.dispatchHud` 契约一致，不作文档降级。
- 修复 `ValParser.parsePrimary` 的括号参数识别与普通分组回退；新增 `ValParserTest` 的双参数/零参数/分组 AST 回归，以及完整三参数 `ClientEvents.hudRender(id, options, (ctx, gui) => ...)` binding-preflight 回归。
- 已通过 `:common:check` 与 26.1.2 票 26 surface/lifecycle 定向测试；尚未代替维护者运行真实 `runClient` HUD 出图复验，故真机证据仍待维护者补录。

## 未完成(下一场)

- 任务 01–11 逐项走查与「公开材料是否足够」记录仍未完成；维护者四类 cookbook 任务仍未完成。
- `/nekojs error` 定位链任务 08 仍需按任务协议逐字记录；其故意 callback error 仍是预期诊断输入。

## 2026-10-03 票 36 试做会话·第二场（维护者操作）

本场使用重启后的 `:26.1.2:runClient`，加载本工作树最新源码与 `versions/26.1.2/run/nekojs/` 试做脚本。维护者进入同一测试世界并执行真实按键与 `/nekojs reload server`。

| 项 | 结果 | 证据 |
|---|---|---|
| F-T1 修订启动注册 | ⚠️ 未再出现 `unknown type name 'art'`，证明过期类型名错误已消除；但日志同时显示 registration pass 未触发，`mymod:art` 的实际注册效果仍未验证，不能据此关闭任务 1 | `versions/26.1.2/run/logs/latest.log` 02:29:44 附近 |
| F-T2 双参数 HUD preflight | ✅ 无 `Unknown identifier 'ctx'/'gui'`；CLIENT 注册成功并持续显示 | `latest.log` 的 `T26-REGISTERED`，维护者目视 HUD |
| D6 颜色与真实输入 | ✅ 黄/绿/白 HUD 可见；R 单击、长按、`consumeClick`、PRESSED/HELD/RELEASED 均出现；CLIENT reload 后 HUD 保持 | `latest.log` 02:31 前后的 T26 标记 + 维护者操作 |
| Item/Block modification 初始时序 | ✅ 启动输出 `NekoJS modifications applied at startup (5 item(s), 3 block(s))`；服务器 reload 输出 `... applied at commit (5 item(s), 3 block(s))` | `latest.log` 02:31:53、02:31:59、02:41:08 |
| Villager/Item/declaration 生命周期 | ✅ `trial item: minecraft:diamond x2`、`trial empty stack: ok`、`declaration trial server started`；初始 reload 不再因未绑定 registry 失败 | `latest.log` 02:31:53–02:31:54 |
| Server reload 主提交 | ✅ 聊天显示 `NekoJS server reload committed: generation=2`，第二次修订脚本后 `generation=3` | `latest.log` 02:31:59.821、02:41:08.910 |

### 本场修复

- `ModificationDomainOwner.collect` 与 26.x/1.21.1 `VillagerTradeDomainOwner.collect` 在 server 尚未绑定时提交空 inert plan，首次资源 reload 延后真实预检到 `applyInitialPlan`。
- 票 36 任务 9 将 `Item.of`/`Item.empty` 放入 `ServerEvents.started`，避免 registry components 未绑定时访问。
- 票 36/票 24 gameplay 示例更新为当前 26.1.2 payload 形态：`event.source`、`dimension().identifier()` 和不依赖过期 Java getter 的安全日志。
- 票 36 交易 query 示例使用 Java `List.isEmpty()`，不再使用不存在的 `.length`。

### 仍未完成

- 任务 3 的本场 query 输出为 `ACTIVE ... trades=0`，尚不足以证明 add/query 交易结果；需单独隔离任务 3 重跑并记录 `declared 2`、`countOf` 等逐字输出。
- 任务 1–11 的跨节点代表性试做、公开材料充分性表格、维护者四类 cookbook 任务和维护者最终结论仍需人工完成。
- 票 41/43/44 的完整 JSX 真实交互、resize、纹理管线与 golden 审阅仍未完成；票 37 继续被票 36 阻塞。

## 2026-10-04 票 36 试做会话·第三场（维护者操作，生命周期修复后）

本场先修复并验证了生产接线：26.x 与 1.21.1 的 `ServerEventListener.onServerAboutToStart` 现在调用
`VillagerTradeDomainOwner.applyInitialPlan(server)`，并在 server stop 清理 owner 绑定；两节点的源接线回归测试通过。客户端从相同
`Game directory: D:\\mcmodDemo\\NekoJS-mult\\versions\\26.1.2\\run` 重启，完整脚本包恢复后进入同一测试世界。

| 项 | 结果 | 证据 |
|---|---|---|
| F-T1 启动注册边界 | ⚠️ 旧 `unknown type name 'art'` 已消失；随后确认 `painting_variant` 没有 NeoForge startup RegisterEvent pass。该 route 已从 startup public surface 移除，实际 painting 注册需另立 datapack/resource-generation 路径 | `latest.log` 10:14:56；NeoForge 26.1.2 registry-order/source inspection |
| 任务 3 启动声明与查询 | ✅ `declared 2 trade declaration(s)`；启动提交 `2 trade declaration(s)`；`ACTIVE generation=1 adapter=26.x-trade-set trades=2 minecraft:farmer/level_1=2` | `latest.log` 10:26:40–10:26:41 |
| 任务 3 reload | ✅ `declared 2 trade declaration(s)`；`this batch will touch 2 trade declaration(s)`；聊天显示 `NekoJS server reload committed: generation=2 phase=COMMIT` | `latest.log` 10:27:16–10:27:16 |
| 任务 3 独立性 | ⚠️ 数据结果已为 2 条交易，但本场重启前已恢复全部试做脚本；故仍有其它脚本诊断输出，未把本场宣称为“无干扰独立复跑” | 同一 `latest.log`；此前隔离目录仅覆盖未重启的 reload，不能替代独立启动证据 |

### 本场结论

- 交易 owner 的生产启动接线缺口已修复；任务 3 的 add/query 正向数据和 reload 第二代提交均有真实客户端日志证据。
- F-T1 已精确收窄为“旧类型名错误已修正文档层；进一步确认 painting_variant 不属于 startup RegisterEvent”。该 API 已从 startup surface 移除，不能以旧错误消失或任务 3 结果宣称 painting 实际注册成功；未来需另立 datapack/resource-generation 路径。
- 票 36 的 11 项跨节点试做、公开材料充分性记录、四份维护者 cookbook 和最终维护者结论仍未完成。
