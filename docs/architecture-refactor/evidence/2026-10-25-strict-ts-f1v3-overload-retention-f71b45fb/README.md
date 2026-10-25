# F1 重载保留修正与驱动身份闭合（f71b45fb）

日期：2026-10-25。绑定源码 `122c8c3e`（产品：捕获视图重载保留）+ `f71b45fb`（驱动：身份窗、
异常链、分类器兜底），五节点 jar 摘要见 `preparation-manifest-trimmed.json`（rcon 密码已脱敏）。
本轮针对[第二轮复核](../../../../research/2026-10-25-glm-f1v2-review.md)的四项发现逐项修复；
上一轮证据 [f1v2](../2026-10-25-strict-ts-f1v2-member-view-fab58a7d/README.md) 保持原样。

## 一、P1：不再整组删除合法重载

复核确认 fab58a7d 的 R2 规则把 `sorted(Comparator)` 的不可表示性传染给了合法的无参
`sorted()`。在 f1v2 真实树副本上重跑实验矩阵（X4–X8、Y1–Y16，共 19 轮 tsc 全树检查）得到
修正后的规律，全部有实验编号锚定：

| 发现 | 实验 |
|---|---|
| 单独保留任何一个 `sorted`/`reduce`/`collect` 重载都通过（TS2416 零新增） | X4 sorted(cmp)、X5 sorted()、X6 reduce\<U\>、X7 collect#1 |
| `sorted` 两个重载都保留时**无参签名在前**即可通过 | Y1/Y12（源序 cmp 在前失败：X1–X3） |
| SAM 别名**全部类型实参均为裸 B**（`$BinaryOperator_<B,B,B,B>`）的重载在多重载组内不可表示（host 返回位协变必败） | Y2/Y3 失败、Y4 组合同败；同形状单签名不受影响（`orElseGet` 一直在） |
| `collect` 两重载无论顺序均失败，保留源序第一个 | X8/Y8 失败、Y11/Y12 过 |
| `spliterator(): $Spliterator<B>` 单独可恢复（此前被批量误删） | Y15 过；与 `toList` 同留则失败（Y13/Y14，`$List` 不变复证） |
| TS 重载集比较存在组间相互作用（非单调）——`toList` 在场会使原本稳定的 `sorted` 崩溃 | Y13 blame 落在 sorted |

产品规则据此重写为（`TypeScriptClassRenderer.selectCaptureViewMembers`）：

- **R1a** 参数嵌套捕获容器（`or`）——维持；
- **R1b** 多重载组内"全 B 别名"重载（两个 `reduce(BinaryOperator)` 形态）排除；同形状单签名保留；
- **R2** 组内混有 B-SAM 重载与零参重载时保留"零参（前置）+ 首个剩余"（`sorted`）；无零参时仅
  保留首个（`collect`）；无 B-SAM 的组全保留源序（`toArray`、`orElseThrow` 不受影响）；
- **R3** 不变容器返回排除（`toList`），白名单 `{$Iterator<B>, $Spliterator<B>}`。

视图净增 5 个成员（`sorted()`、`sorted(cmp)`、`reduce<U>`、`collect#1`、`spliterator`）；
`collect#2`（Collector 版）与两个 BinaryOperator reduce、`toList`、`or` 为实证不可表示边界，
具体实现上完整保留。夹具把复核确认的全部恢复项写成正向合同（含 `sorted()`、
`sorted(comparator)`、三参 `reduce`、supplier 版 `collect`、`spliterator()`），不可表示项保持
`@ts-expect-error` 负例；golden 官方再生恰 +5 行。

## 二、P2：kill 脚本身份窗关闭

`server-process.json` 启动时经 WMI 采集**微秒精度创建时刻**（`yyyy-MM-ddTHH:mm:ss.fffffff`）
与**完整命令行**；kill 脚本要求 PID 存活 + 两个字段**字符串全等**，未采集到身份的记录一律
SKIP。复核场景实测（K-3）：记录进程退出后，一个**命令行完全相同**的新进程占同一 PID——仅
创建时刻不同——脚本正确 SKIP（`creation time differs (PID reuse)`）。另有命令行不同
（K-2 `command line differs`）、无身份（`record has no captured identity`）、PID 不存在
（`not running`）三个 SKIP 分支实测。

## 三、P2：失败原因入摘要、原异常链保留

`run-summary.json` 每节点新增 `failure` 字段；`raise SystemExit(...) from 原异常`——故障注入
实测（T3'）：探针与停服双 RuntimeError 时 `__cause__` 为原 RuntimeError、摘要含
`probe failed: rcon unreachable`、回收路径不变（stopSent=false→15s 有界等待→kill→子进程消失）。

## 四、P2：分类器去兜底

删除无条件尾项规则，尾项按已知签名显式枚举（菜单 4 类 + `getOrCreate/getOrThrow/leashHolder/
fabric_enumCycle/get`）；未匹配任何规则的诊断落入 UNMAPPED 并使分类器非零退出。实测：c3ee4097
仍精确闭合 1609（各族数字不变、尾项 26、UNMAPPED 0）；构造的未知 TS9999 诊断不被任何规则
吸收（可达性 PASS）。

## 五、f1v3 十入口重测（f71b45fb 确切产物）

| 入口 | 基线 1609 | f1v2 1503 | f1v3 1503 | 入口 | 基线 | f1v2 | f1v3 |
|---|---:|---:|---:|---|---:|---:|---:|
| 1.21.1 s/u | 111/112 | 102/103 | 102/103 | 26.1.2-fabric s/u | 186/187 | 175/176 | 175/176 |
| 26.1.2 s/u | 165/166 | 154/155 | 154/155 | 26.2.0-fabric s/u | 181/182 | 170/171 | 170/171 |
| 26.2.0 s/u | 159/160 | 148/149 | 148/149 | **合计** | **1609** | **1503** | **1503** |

分类器对 f1v3 零 UNMAPPED；族级 F1 156→50，其余 13 族与尾项全部 ±0；逐条身份对比基线
**移除 106 / 新增 0**。**重载保留（+5 视图成员）零诊断代价**。五节点全部 RCON 干净停服
（probeFiles 394/448/430/540/519，exit 0，stopSent=true，killed=false）。

## 验证

- `:common:test`（夹具）绿；`npm run test:probe-types`（含 wildcard-optional）退出 0；
- `:common:check --rerun-tasks` BUILD SUCCESSFUL（6m52s，含隔离）；golden 再生 +5 行复核；
- 驱动注入验证：T3'（异常链+failure 字段）、K-2/K-3（PID 复用两形态）、分类器闭合与
  UNMAPPED 可达性；
- `run.py prepare|run|check --target build/tsbaseline-f1v3` + `classify_diagnostics.py`
  （本页数据来源）。

## 边界

- `collect#2`（Collector 版）、BinaryOperator 版 `reduce`、`toList`、`or` 为实证不可表示边界
  （各有失败实验编号）；具体实现上完整保留；值投影边界（F1 剩余 50）不变。
- 未进入 F4；性能门槛仍绑 `d06cd961`；不 push、不建 PR、不发布、不代签。
