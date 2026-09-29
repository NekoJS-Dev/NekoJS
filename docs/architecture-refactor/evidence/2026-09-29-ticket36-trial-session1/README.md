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
- **F-T1(待修,示例缺陷)**:任务 01(票 15 基线示例)`event.custom('mymod:art','art',...)`
  的全局类型名 `'art'` 在 26.1.2 全注册表不存在 → 启动期 EXECUTION 失败
  (`unknown type name 'art'`)。票 15 基线示例过期——owner 15 域文档修正。
- **F-T2(待修,引擎/示例待分诊)**:任务 06(票 26 基线示例)`hudRender(..., (ctx, gui) => ...)`
  回调**参数**被 binding-preflight 判为自由标识符(`Unknown identifier 'ctx'`)→ CLIENT
  脚本装载失败。票 26 的 JVM 证据(dispatchHud 探针)与真机腿(自写单参脚本)均未覆盖
  基线示例原样运行——preflight 对「直接调用即注册」API 回调参数的处理缺陷,或示例签名
  过期;owner 26 域 triage。

## 未完成(下一场)

- D6 颜色复验截图(HUD 黄/绿字)、任务 01–11 逐项走查与「公开材料是否足够」记录、
  /nekojs error 定位链(任务 08)、维护者四类 cookbook 任务(代码类,大部分无需游戏)。
