# F1 成员驱动捕获视图重做与驱动加固（fab58a7d）

日期：2026-10-25。绑定源码 `d075db12`（产品：捕获视图成员驱动重做）+ `fab58a7d`（驱动加固与
根因分类闭合），五节点 jar 摘要见 `preparation-manifest-trimmed.json`（rcon 密码已脱敏）。
本轮针对 [Codex 复核](../../../../research/2026-10-25-glm-f1-capture-review.md) 的五项发现
（Standards P1×1/P2×2、Spec P1×1/P2×2）逐项修复并重新实测；上一轮证据
[2026-10-25-strict-ts-f1-optional-capture-914df3c7](../2026-10-25-strict-ts-f1-optional-capture-914df3c7/README.md)
的历史结论保持原样不覆盖。

## 一、Spec P1：捕获视图不再以缺失合法方法换错误减少

`914df3c7` 的内联视图只有 4 个成员且把合法递归 `stream().findAny().stream()` 写成负例。
本轮改为**来源/成员驱动**的命名视图，`describeConstable()` 一类输出位渲染
`$OptionalView<$Bound>`：

- `$OptionalView<B>`（java/util 模块内声明）：按 `java.util.Optional` 反射实例面生成——
  Bean 属性 `present`/`empty`、`isEmpty/isPresent/equals/hashCode/toString`、`get()`、
  `orElse/orElseGet/orElseThrow×2`、`ifPresent/ifPresentOrElse`、`filter`、`map`、`flatMap`
  （参数位保持具体 `$Optional<U>`）、`stream(): $StreamView<B>`，共 18 个成员。
- `$StreamView<B>`（java/util/stream 模块内声明）：`java.util.stream.Stream` 全部实例成员
  加 `BaseStream` 继承成员（`close/isParallel/iterator/onClose/parallel/sequential/unordered`），
  `findAny/findFirst/max/min/reduce 式` 终端返回 `$OptionalView<B>`，链式返回 `$StreamView`，
  **递归无截断**（`stream().findAny().stream().count()` 合法）。

复核点名的四项正例（`orElseThrow()`、`present`、`filter(() => true)`、
`stream().findAny().stream().count()`）连同扩展正例（`orElse/map/flatMap/iterator/max/深层递归`）
进入夹具 `ProbeWildcardOptionalCaptureTest` 的 exercise.ts，`npm run test:probe-wildcard-optional`
退出 0（`@ts-expect-error` 负例同时要求错误仍发生：错误元素 `get(): number`、窄成员不可达、
错误 Supplier/Predicate 结果种类）。

### 排除集与证明（非任意截断）

视图对每条源成员应用三条机械规则，规则先在 `914df3c7` 真实生成树副本上以十一个迭代变体
（A→J）逐一验证——每次 `tsc` 全树检查定位下一个不兼容成员的完整错误链：

| 规则 | 排除成员 | 证据要点 |
|---|---|---|
| R1 参数位嵌套捕获容器 | `or(Supplier<Optional<? extends T>>)` | 三种参数形状（视图嵌套/具体/联合）双向都落到 `$ConstantDesc` 缺窄成员的同型比较；SAM 宿主分支强制元素级匹配 |
| R2 多重载组含上界 SAM 参数 | `reduce`(3 签名)、`sorted`(2)、`collect`(2) | TS 重载集按序严格配对：整组保留则 B-SAM 参数严格比较失败，部分保留则元数配对失败（`sorted()` 单留实测 "Target signature provides too few arguments"） |
| R3 返回位不变容器 | `toList(): $List<B>`、`spliterator(): $Spliterator<B>` | 同型泛型经方差探测测得不变（`$List` 经 `spliterator().getComparator()` 继承不变）；`$Iterator<B>` 实测协变可留（白名单） |

被排除成员在具体实现（`$Optional<具体>`/`$Stream<具体>`）上完整保留（夹具对 `or/filter`
正负消费同轮验证）。原型变体 J 在 1.21.1-server 真实树上与 `914df3c7` 基线**零净差异**
（102=102，逐条归一化对比 0/0），本轮五节点实测即其产品化结果。

golden 走官方 `:common:regenerateGoldens` 再生，diff 恰为 3 文件：`java/lang/constant`
（1 行：内联视图 → `$OptionalView<$ConstantDesc>`）、`java/util`（+`$OptionalView` 声明与
`$StreamView` 导入）、`java/util/stream`（+`$StreamView` 声明与 `$OptionalView` 导入）。

## 二、Standards：驱动三项加固（均以故障注入验证）

1. **P1 清理脚本误杀**：`kill-servers.ps1` 重写为 `-Target <安装根>` 必选参数，只处理
   `run.py` 启动时写入的 `<node>/server-process.json` 记录，且需三重身份验证（PID 存活、
   命令行仍含启动器特征、创建时刻与记录一致防 PID 复用）；验证不过一律 SKIP。四情形实测：
   记录匹配→STOP；时刻漂移 100s→SKIP；命令行不含启动器特征→SKIP；PID 不存在→SKIP。
2. **P2 异常回收**：`run` 的成功、探针失败、驱动异常共用一条 owned 回收路径（RCON `stop`
   → 有界等待 → kill 自己启动的子进程），失败也先落盘 `run-summary.json` 再抛原始异常；
   启动即写 `server-process.json`。故障注入实测（探针与停服双 RuntimeError）：stopSent=false、
   15 秒有界等待后 kill、子进程消失、摘要含失败节点、原始异常保留。
3. **P2 无效运行误报零诊断**：`check` 将每入口分为 `clean`（exit 0 零诊断）/
   `diagnostics`（exit 1/2 且解析出 TSxxxx）/`invalid`（非零退出且零条诊断）；`invalid`
   显式失败并停止汇总。故障注入实测：exit 1 + `Cannot find module` → `invalid` 且
   SystemExit；正常 exit 2 → `diagnostics` 并计入总数。本轮十入口全部 `diagnostics`。

## 三、Spec P2：根因分类闭合 1609

新增 [`classify_diagnostics.py`](../../../../bench/tsbaseline/classify_diagnostics.py)：
对十份 `*-tsc.txt` 的每条诊断按有序互斥规则归族，任何未映射项都会显式报错退出（非静默桶）。
对 c3ee4097 基线输出 [逐条映射 JSON](../../../../research/2026-10-25-strict-ts-root-cause-mapping-c3ee4097.json)，
14 族 + 尾项合计**精确 1609**。原表 1613 的差源已消除：F6 与 F9 对
`CraftingRecipe/SmithingRecipe` TS2430 的重复计数（统一归 F6 叶因）、F9 其余行按叶因解散
（codec 叶归 F5、Optional 通配叶归 F1、SectionPos↔Vec3i 归 F13）、F1 节点列统一为 server
入口。研究文档总表已替换为分类器计算值。

## 四、十入口重测（fab58a7d 确切产物）

| 入口 | c3ee4097 | fab58a7d | 入口 | c3ee4097 | fab58a7d |
|---|---:|---:|---|---:|---:|
| 1.21.1 server/startup | 111/112 | 102/103 | 26.1.2-fabric server/startup | 186/187 | 175/176 |
| 26.1.2 server/startup | 165/166 | 154/155 | 26.2.0-fabric server/startup | 181/182 | 170/171 |
| 26.2.0 server/startup | 159/160 | 148/149 | **合计** | **1609** | **1503** |

族级增减（分类器对新树重跑）：**F1 156→50（−106），其余 13 族与尾项全部 ±0**；
逐条身份对比**移除 106 / 新增 0**（removed 全部为 `describeConstable` TS2416 与
`HolderLookup$Provider`/`RegistryAccess` TS2430）。F1 剩余 50 为
`$Double/$Float/$Integer/$Long/$String` ×10——number/string 值投影不结构满足
`$ConstantDesc` host 接口的可表达性边界，需单独证明的投影变更，不以联合/any 放宽。

本轮五节点全部 RCON 干净停服（`stopSent=true`、`killed=false`、`exitCode=0`、
`lockReleased=true`，见 `run-summary.json`）；声明文件数 394/448/430/540/519。

## 验证

- `./gradlew :common:check --rerun-tasks`：BUILD SUCCESSFUL（5m51s，含隔离检查）。
- `npm run test:probe-types`（含 `test:probe-wildcard-optional`）：退出 0。
- golden 官方再生 + 人工 diff 复核（上述 3 文件）。
- 驱动故障注入 T1–T7 与 kill 脚本四情形（上文结果，均通过）。
- `python bench/tsbaseline/run.py prepare|run|check --target build/tsbaseline-f1v2`
  与 `classify_diagnostics.py --ide-dir build/tsbaseline-f1v2/ide`（本页数据来源）。

## 边界

- 未进入 F4（fabric 泛型方法投影，80 条）及后续族；本页不宣称多族修复。
- 原政策性能门槛仍绑定 `d06cd961`（285.3ms/41173ms），不因本轮产品变更自动转移，
  §4 冻结时需对最终候选复测。
- 值投影边界（F1 剩余 50）与维护者裁决项维持开放；未 push、未建 PR、未发布、未代签。
