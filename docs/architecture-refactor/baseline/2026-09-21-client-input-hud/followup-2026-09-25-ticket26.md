# 票 26 Follow-up 证据（2026-09-25，分支 `mult`）

执行者：ticket-26 follow-up subagent。Blocker 票 14 Status 为 closed，可开工。
本轮只做既有接缝处补强，不做重构；历史 AC 勾选不变（AC5/AC6/AC7 部分满足、AC9 未勾选）。

## 1. 改动（唯一源码改动）

`src/test/java/com/tkisor/nekojs/bindings/event/client/KeyBindEventsTest.java`（+54 行，测试 only）：
- `keyHandleHeldStateIsObservableThroughPublicMethods`：`register` 返回句柄经
  `KeyBindEventJS.getKeyMapping()` 透传（`assertSame`）；vanilla 公开 `setDown(true/false)`
  驱动的 `isDown()` 经事件对象可观察；`finally` 复位，不污染同 JVM 其他用例。
  只用公开方法；不读私有 `BINDINGS` 表、不断言回调对象身份。
- `consumeClickWithNoClickSourceConsumesNothing`：无头 JVM 下新鲜句柄 `consumeClick()` 为
  false。红→绿留痕：首版曾用公开静态 `KeyMapping.click(Key)` 供给一次点击，红于
  `KeyMapping.forAllKeyMappings` 内 `Minecraft.getInstance()` NPE（`command-output/`
  `followup-2026-09-25-11`），证明 click-count 源需真机；改写后只断言无头可观察部分。
- 未动生产代码，未新增 bus/收集器/守卫；`ClientEvents.java` diff 仍 0 行。

## 2. AC 逐条结论

| AC | 结论 | 证据指针 |
|---|---|---|
| AC1 | 满足（无变化） | 09-21 REPORT §3；本轮未动相关路径 |
| AC2 | 满足（无变化） | 既有用例绿（本轮 KeyBindEventsTest 10/0/0 含幂等/非法输入用例） |
| AC3 | 满足（无变化） | SurfaceTest 4/0/0（本轮复跑绿，见 13/14） |
| AC4 | 满足（无变化） | LifecycleTest 4/0/0（本轮复跑绿，见 13） |
| AC5 | 部分满足，未勾选：held-state 可观察性补强落地（上表两新用例）；`consumeClick()` 端到端仍缺真实按键源 | `12`（绿）+ `11`（`KeyMapping.click` 无头 NPE 红态原文）；owner 票 34 真机 smoke |
| AC6 | 部分满足，未勾选：共享树事实与节点声明能力只读复核通过；focused + gate 绿；未跑 1.21.1/fabric 节点 test，未新造矩阵条目 | `13`（Ticket26 8/0/0）、`14`（gate 2/0/0 无 drift）、`16`；owner 票 31/32 |
| AC7 | 部分满足（declaration 面不勾选）：5 个 declaration golden 0 命中复核成立；runtime member 反射 fixture（SurfaceTest）绿；未碰声明派生链 | `16`；owner 09/33/34 |
| AC8 | 满足（无变化） | SurfaceTest 跨域断言绿；`ClientEvents.java` 0 行 diff |
| AC9 | 未勾选（preparation only）：删除替代 parity/迁移表/无消费者证据仍有效；无新删除，不代答 sign-off | `17` + MIGRATION.md §1/§3 |

## 3. 真实命令与结果

| 命令 | 结果 |
|---|---|
| `:26.1.2:test --tests "*KeyBindEventsTest*" --rerun`（首版探针） | FAILED，10 tests 1 failed（`11`） |
| `:26.1.2:test --tests "*KeyBindEventsTest*" --rerun`（改写后） | BUILD SUCCESSFUL；tests=10 failures=0 errors=0（`12`） |
| `:26.1.2:test --tests "*Ticket26*" --rerun` | BUILD SUCCESSFUL；Lifecycle 4/0/0、Surface 4/0/0（`13`） |
| `:26.1.2:platformGateTest --rerun` | BUILD SUCCESSFUL；gate 2/0/0，无 member-drift（`14`） |
| `guardLint` | BUILD SUCCESSFUL；守卫块 316、扫描 467 文件、超限豁免 0、警告 0（`15`） |

未跑：`:1.21.1:test`、fabric 节点 test、`:common:check` 全量（09-21 已跑 1756/0/0；本轮按验证表只跑本票相关）、节点 runtime smoke（票 34）、`npm run test:probe-types`（声明面未动）。

## 4. 剩余缺口与 owner

- 脚本侧 `consumeClick()` 真实按键端到端 → 票 34（P4 真机）。
- TS/Python declaration 面 0 命中 → Managed Surface/Probe owner（09/33/34）。
- 1.21.1/fabric 节点 test 与 capability 矩阵条目 → 票 31/32（本票不造条目、不声称 Fabric parity）。
- `ClientRenderPlugin` 删除的维护者确认 → AC9 sign-off 门禁（备选保留方案见 MIGRATION §3）。

## 5. golden / 公开契约

本轮未改动任何 golden/probe 产物（普通测试只读 golden；`git status` 无 golden 改动，
`platformGateTest` 无 drift）。公开脚本写法零变更（测试 only 新增两个 characterization 用例）。
用户可见文本零变更。
