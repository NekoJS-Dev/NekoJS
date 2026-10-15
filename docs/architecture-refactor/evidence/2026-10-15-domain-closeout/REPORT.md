# A15 / A16 / A22 领域切换验收

执行：主会话，2026-10-15 至 2026-10-16；分支 `codex/architecture-closeout`，基点 `c92d781a`。本轮源码、测试和文档均由主代理修改；Luna xhigh 仅只读调查。维护者原文 **「批准上述范围，验证通过后删除」** 与精确范围见 [PUBLIC-CUTOVER.md](PUBLIC-CUTOVER.md)。

本报告完成三个领域的替代、消费者迁移、精确删除和对应验证。票 28/37 仍开放；这不是完整发布验收，也没有进行正式版本切换、远程上传或数据删除。

## 改动与契约

- **启动注册**：删除 `NekoRegistryDeclarations` 手写 producer 和旧 14 参数 snapshot ctor；保留 15/16 参数 ctor。事件 sugar、具名类型、计算类型名、custom、Supplier 与 Builder TS/Python 声明由现有元数据/契约生成。补齐实际 FoodBuilder 回调、nullable 属性、别名及插件同名 Builder 隔离。旧全局类型改用事件推导或 `NekoStartupBuilders` 命名空间。
- **动态注册**：删除旧 `DynamicRegistry` Binding、六个立即入口、旧 fluent Builders 和 handle。`DynamicRegistryEvents.dynamicRegistry` 收集候选，整批 activation 成功才发布 claims；空代际退役 claim 并保留物理对象。部分失败会恢复 claims，移除本次新对象及其物品 component initializer；客户端 prepare、commit/catch-up 失败回滚均有回归。保留共享 registry backend、surgery、事件、查询与同步 gate。
- **交易**：删除 26.x add/pendingCount、两套旧 Manager 和 listener/command hooks，保留 query/describe。root owner 保存物化声明，遗漏保留，显式 obsolete 才退役；资源重载后重放已接受集合。26.x 原子恢复两个注册表索引和 holder tags；1.21.1 使用 NeoForge 可变池并在 NORMAL 重建之后以 LOWEST 重放。

没有修改持久化格式、路径、key、payload id 或冻结 wire schema。客户端 full-state ledger 仅描述物理 exposure/conflict view；wire 不携带 stale bit，服务端 claim retirement 仍为权威，不把客户端本地 fresh 标记解释为服务端所有权。

两份 startup golden 的有意变化来自上述契约：Food 回调、其实际公开辅助方法、真实 nullable 属性/参数与嵌套 Item。普通测试先产生差异，审阅后从规范生成结果更新，没有在普通测试中自动改 golden。仅删除专门测试已移除 fluent API 的 `DynamicRegistryBuilderTest`；新事件、计划、回滚和隔离门保留并加强。

## 自动检查

以下最终命令均通过，原始日志与 XML 在 [domain-evidence.zip](domain-evidence.zip)；最终 XML 汇总 **4905 tests、271 skipped、0 failures、0 errors**。这是各模块最终报告总数，不能解释成所有任务均忽略缓存重新执行；完整构建报告明确记录 executed/up-to-date。

```powershell
.\gradlew.bat :common:check :common-api-processor:test guardLint `
  :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest `
  :1.21.1:build :26.1.2:build :26.2.0:build `
  :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
python tools/nekojs-ci-gates.py all --out build/domain-closeout-ci-gates-final.json
# source-roots 分别对上述五个节点执行
python tools/nekojs-ci-gates.py source-roots --node <node>
npm run test:probe-types
git diff --check
python docs/architecture-refactor/evidence/2026-10-15-domain-closeout/verify.py
```

最终构建 `domain-closeout-full-matrix-final3.log`：**BUILD SUCCESSFUL，52s，109 tasks：27 executed / 82 up-to-date**。CI 四项 gate 零失败；五节点 source-roots 通过；定向 Probe 类型测试通过。新增实际 `MappedRegistry` 索引/tag 回滚与 `DataComponentInitializers.build` 红绿回归在五节点适用范围内通过；common check 包含 inert plan 与隔离检查。

Wiki 结构检查 `node scripts/wiki-docs.mjs check --strict --structure-only` 通过。修改的四组双语言页面已核对迁移语义并按现有工作流更新摘要，仍为 draft/humanReview pending。全站 `npm run check:wiki` **失败**，剩余 6 项分别为 plugin-development、type-adapters、event-extensions 的两语言摘要过期；以 `git show c92d781a:<path>` 重算确认这六项在基点已存在。原始和最终日志保留；没有为本次提交改写这三组无关页面或假定人工翻译审阅完成。

## 官方安装包实机

专属目录 `build/domain-closeout-final-installed`，全新测试世界、独立端口 26561–26602；没有使用用户已有世界或游戏 profile。NeoForge 版本为 21.1.227 / 26.1.2.71 / 26.2.0.75；Fabric 复用已安装测试依赖。每个 JAR 内全部 common 和节点编译 class 均逐字节对照本次构建，类摘要、制品 SHA256、基点、生产源码 patch 和新增源文件摘要在归档 `preparation-manifest.json`。类绑定数分别为 1544 / 1616 / 1616 / 1550 / 1550。

| 节点 | 启动回调、实际注册、嵌套指纹、放置、脚本重载 | Python stub AST | 额外实际行为 |
|---|---|---|---|
| 1.21.1 NeoForge | PASS | 320 / 0 语法失败 | FluidType 身份、真实交易池生命周期 PASS |
| 26.1.2 NeoForge | PASS | 378 / 0 语法失败 | FluidType、重载前后实际 MobEffect、交易池、空动态代际保留效果 PASS |
| 26.2.0 NeoForge | PASS | 346 / 0 语法失败 | FluidType、交易池、空动态代际保留效果 PASS |
| 26.1.2 Fabric | PASS（既定子集，无 Fluid 承诺） | 296 / 0 语法失败 | 不升级交易/动态注册能力；既有 unavailable 负例保留 |
| 26.2.0 Fabric | PASS（既定子集，无 Fluid 承诺） | 263 / 0 语法失败 | 同上 |

三个 NeoForge 节点逐项检查真实 farmer pool 的数量：声明后 +1；遗漏后物理数量不变、query total=0/unrestored=1；原版 `reload` 成功后仍保留；显式 obsolete 后恢复基线、retired=1；未知职业整批拒绝且旧 generation/交易不变；重新声明后 +1、retired=0。两个 26.x 节点另验空动态代际后三类 registry 各 1 registered/1 stale，保留 MobEffect 可对实体施加，恢复声明后 stale=0。命令与连续日志均归档。

五个服务器均经 RCON stop 正常退出，exitCode=0。服务器行为、Python 语法通过不等于客户端渲染、多人 ACK 或完整 IDE 验收。

## 保留的失败与修复

归档 `checks/` 和 `history/` 保留本轮失败，不用最终通过覆盖：

1. nullable/Food 声明差异经契约修复并审阅 golden；中途 Runnable checkpoint 违反 inert plan 的回归被改为不可变数据 checkpoint。
2. Food effect 字符串在真实 Graal 包装器中无法转 ResourceLocation，先保留 red，再增加两个节点的 String overload 得到 green。
3. Fabric synthetic registry 需 freeze 后才能使用 holder 值，tag 断言需在 freeze 后设置；修复 fixture，没有放松断言或排除节点。
4. 首次官方 1.21.1 验收暴露不可变交易池使 NeoForge 原版 reload 失败；改为可变池、LOWEST 重放后再次新世界验收。随后发现默认职业回退错误接受未知职业，改 getOptional 后验证拒绝且保留旧代际。
5. 动态物品失败注册会遗留 component initializer，影响下一次原版 build；新增实际 initializer lookup 红绿回归及精确清理。最新 JAR 再跑完整五节点矩阵和实机，未借用修复前制品证明修复。

## 完整 IDE 与剩余窗口

实际安装目录生成的完整默认 workspace，严格 TSC `skipLibCheck=false`，Pyright 1.1.414 分析全部生成 stub，无排除或 suppress：

| 节点 | TSC server / startup 诊断 | Pyright 错误 | 结论 |
|---|---|---|---|
| 1.21.1 | 586 / 587 | 7 | FAIL |
| 26.1.2 | 744 / 745 | 7 | FAIL |
| 26.2.0 | 638 / 639 | 7 | FAIL |
| 26.1.2 Fabric | 1162 / 1163 | 13 | FAIL |
| 26.2.0 Fabric | 917 / 918 | 13 | FAIL |

诊断集中在 host 依赖闭包、继承/泛型和 field/method 重名等；归档 `ide/` 含原始诊断、配置和统计。**定向 npm 测试通过没有关闭票 37 的完整 IDE gate**，也未通过 skipLibCheck 掩盖问题。

仍需：票 28 多节点实际首帧/F3+T/失败候选画面保留；跨进程多人动态同步实测；完整 IDE 修复；当前确切候选的正式性能测量；独立 release rollback 与 data rollback 重启回读；最终发布和回滚政策的维护者结论。旧 e473 性能通过组只证明其绑定源码，不为本轮新 JAR 背书。未执行远程发布、版本提升或数据迁移。

## 复核与证据完整性

主代理复核了当前 diff、所有旧入口生产消费者、成对节点、契约生成路径、失败恢复及真实运行证据。删除后生产树无旧 Binding/Manager/handle 的生产调用；保留 backend 和 query producer。历史文档及迁移注释中的旧符号作为记录保留。新增异常码 NEKO-4040–4046 已登记双语言 error-reference。

正式的整个规格双轴 review 仍随未完成的票 28/37 收尾；本轮领域提交不将其标为已完成。证据摘要见 [summary.json](summary.json)，所有归档条目和制品绑定的摘要见 [manifest.json](manifest.json)，只读校验器见 [verify.py](verify.py)。归档不含世界、用户 profile、RCON 密码或 JAR 二进制。
