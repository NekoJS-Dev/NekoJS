# 擦除返回类型的接口契约恢复

生产源码 `569376c6`，固定增量 `afbea905...569376c6`。主代理独自修改、构建和验证，Luna xhigh 只读复核。运行时方法、名称、参数、数据和 wire 不变；声明返回类型变得更具体。

## 恢复规则

针对实现类的裸泛型实例返回值，复用既有上下文签名解析器，从实现接口层次寻找同 Java 名、同擦除参数、同返回擦除类型的契约。仅当所有同擦除候选的具体类型结构一致、变量属于宿主作用域时恢复，同时写入共享 IR 的 Type 与 ApiTypeRef。保留原先已参数化的返回类型；无契约、raw 接口、冲突、方法级泛型及无法绑定的变量均保留原始返回值。

ServerSpec/LevelSpec 的 Object 父契约不参与 AttachedData 擦除类型的裁决；子接口的参数化契约仍须一致。只替换返回类型槽，不改变成员可见性。没有新增扫描配置、默认泛型参数、Any 回退规则或 Bean 别名迁移。

## 验证结果

```text
.\gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
python tools/nekojs-ci-gates.py all --out build/domain-return-contract-ci-gates.json
python build/domain-closeout-tooling/accept-return-contract.py
python build/domain-closeout-tooling/check-return-contract-outputs.py
python docs/architecture-refactor/evidence/2026-10-16-followup-acceptance/verify.py
```

五个新增回归覆盖间接接口、宿主变量、继承重发、重复/冲突、已有具体返回及保守回退。修正 fixture 后对旧实现的五个测试中三个类型恢复断言失败；旧源码、当时 fixture、XML 和日志保留。早期测试另暴露隐藏方法短路缺失以及 fixture 的继承重发/别名预期错误，修复后的定向测试通过。静态复核发现 Object 父契约阻断恢复后，补齐真实接口层次并重跑完整矩阵；早期矩阵不能充当最终证据。

最终完整构建 PASS，4m06s，106 tasks（36 executed / 70 up-to-date）。七模块测试 XML 合计 4921 tests、271 skipped、0 failures/errors；Probe 类型回归和四项 CI gate PASS。五节点新官方 JAR 逐类绑定确切源码，全部 runtime verdict PASS，正常 RCON stop 后 exitCode=0。

完整默认 Pyright 五节点均为 0 error/warning。严格 TSC 使用原 server/startup 入口和 `skipLibCheck=false`：

| 节点 | server：前 → 后 | startup：前 → 后 | 每入口新增 / 移除 |
|---|---:|---:|---:|
| NeoForge 1.21.1 | 693 → 690 | 694 → 691 | 0 / 3 |
| NeoForge 26.1.2 | 867 → 864 | 868 → 865 | 0 / 3 |
| NeoForge 26.2.0 | 639 → 636 | 640 → 637 | 0 / 3 |
| Fabric 26.1.2 | 1290 → 1271 | 1291 → 1272 | 0 / 19 |
| Fabric 26.2.0 | 923 → 892 | 924 → 893 | 0 / 31 |

十个入口共减少 118 条 TS2314，逐条多重集无新增；完整 TSC 仍 FAIL。Fabric 的其他 Mixin 接口也保留了参数化契约，因此同一规则还恢复了原始集合返回值。

## 实际 Type、TS 与 Python 边界

每个节点对 MinecraftServer、Player、Level 的反射和 IR 作独立观察。NeoForge 三节点分别保留 21/27/27 个标记，Fabric 两节点各 6 个，覆盖初次执行及多次重载；每个标记都确认反射仍是裸 AttachedData，而恢复后的 sourceType 与 ref 同时携带准确 Host，未标成声明编辑覆盖。实际 TS 的三种 `data()` 返回值均为 `AttachedData<Host>`。

Python 后端现有投影不输出 AttachedData 这类普通 host 的泛型实参，三种 `data()` 仍呈现为 AttachedData；这里不声称 Python 已完整保留该实参。三个 NeoForge 节点的 Java 类 stub 逐字节一致。Fabric 26.1.2/26.2.0 分别有 5/11 个集合返回注解从裸 Collection/Set/List 变为既有后端的 `list[Element]` 呈现，涉及网络通道、配方、已加载区块，以及 26.2 的方块实体类型和 loot；Java Set 的这一 Python 呈现沿用现有近似规则。

独立 verifier 对变化的 Java 模块解析完整导入类型身份，确认除这些返回注解外 AST 不变；新增导入解析后纳入类型身份比较。其余入口/事件模块只允许既有顺序与本地别名分配差异，不声称整个目录字节一致。

## 证据与剩余项

[归档](return-contract-evidence.zip)、[清单](return-contract-manifest.json)、[摘要](return-contract-summary.json)保存全部实机日志、完整声明与诊断、源码/class 绑定、测试 XML、修复前失败、重载签名标记及复现工具。首次观察脚本原以仅两次执行预期六个标记，被 NeoForge 交易/动态生命周期的额外重载拒绝；修正为检查全部完整三宿主组，未丢弃样本。初始“Python 应输出 AttachedData[Host]”预期未作为通过证据，其实际后端限制如上记录。

固定源码静态 Standards 0 / Spec 0；Spec 首次发现的 Object 哨兵问题已修复。静态复核不替代实际声明检查。未在本批重跑正式性能、其他客户端首帧或最终候选数据回退；最近的重载性能仍 FAIL。票 28、37 和真实维护者发布验收继续开放。
