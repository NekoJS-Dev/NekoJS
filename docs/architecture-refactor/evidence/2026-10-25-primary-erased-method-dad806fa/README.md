# 主代理接手：擦除的方法泛型合同恢复与新安装复验

日期：2026-10-25。产品提交 `dad806fa`；驱动修复为 `a8c0f274`、`555ee037`。本阶段全部实现、测试和提交由主代理执行，Luna xhigh 仅只读审核/调查。无 PR、push、发布或人工验收代签。

## 审核 GLM f1v3

`122c8c3e` 恢复了 sorted 双签名、三参 reduce、supplier collect 和 spliterator；`f71b45fb` 的精确进程身份、异常链和显式分类尾项均有改进。

F1 仍未完整通过公开合同审查：or、两个 BinaryOperator reduce、Collector collect、toList 仍主动省略，不能以“实证不可表示”替代保留公开方法的要求。本轮没有扩大这些排除，也没有将 F1 标为完成。完整表示仍需继续验证；F4 是独立且有明确来源证明的共享修复。

另发现服务端 Popen 后的身份查询超时会跳过回收；主代理补齐此路径及复审发现的启动输出窗口，并覆盖中断和清理失败。

## 实现与合同

- 驱动从启动开始保护 owned 子进程；身份查询/记录、状态输出、中断均进入回收。日志文件受上下文管理器保护；正常异常保留 cause，中断原样传播，回收失败另记 cleanupFailure，不能遮盖原操作错误。
- `TypeReflector` 不按 Fabric 类名或方法名打补丁。对反射签名已擦除的实现，仅当接口候选参数/返回擦除完全一致、方法变量和界限都在作用域内、所有候选完整合同一致时，恢复方法泛型、参数、界限与返回值。
- 等价方法变量按位置比较并校验界限；冲突/无来源/已有类型信息保持原路径。保留实现的 sourceMethod、隐藏与重映射状态，不修改 Java 运行逻辑、持久化或网络协议。
- 严格消费保留 typed key 读写和接口赋值；错误返回类型与错误载荷仍须被拒绝。恢复发生于共享 IR，定向/common 检查覆盖既有后端；本轮未重新采集五节点 Python stub/Pyright。

## 验证

| 检查 | 结果 |
|---|---|
| 驱动故障回归 | 4 项 PASS：身份超时、中断、启动输出断管、清理失败；全部使用假进程，没有终止用户进程 |
| TypeReflectorReturnContractTest | 8 项 PASS；新增完整方法泛型、等价变量/冲突界限及公开生成器消费 |
| common/check、处理器、guardLint、五节点 build | PASS，4m17s；所运行 test/platformGateTest 冻结 XML 合计 5183 tests、271 skipped、0 failures/errors |
| common/test | 2466 tests、4 skipped、零失败/错误（包含在上述总数内） |
| npm run test:probe-types | 完整定向链 PASS，含新增 erased-method-contract 组 |
| nekojs-ci-gates.py all | 4 checks、0 failures |
| 五节点新安装 | Probe 文件数 394/448/430/540/519；全部 stopSent=true、exitCode=0、killed=false、锁释放 |
| 完整默认严格 TS | **FAIL，1409 条**；十入口均 exit 2，不冒称全图通过 |

先红证据：恢复泛型测试在旧实现失败；身份超时在 `0b875b83` 回放失败，中断/启动输出/清理失败回归分别先失败再转绿。实现途中补齐过缺失 imports，并给等价接口的 raw fixture 添加显式覆写以满足 javac；这些编译失败不计作产品行为红证。未更新或手改 golden。

## 十入口与逐条增减

对照为 `f71b45fb` 的 f1v3 确切安装；本轮使用新目录 `build/tsbaseline-primary-f4-dad806fa` 和 37400 起的隔离端口。

| 节点 | 原 server/startup | 新 server/startup |
|---|---:|---:|
| 1.21.1 NeoForge | 102/103 | 102/103 |
| 26.1.2 NeoForge | 154/155 | 154/155 |
| 26.2.0 NeoForge | 148/149 | 148/149 |
| 26.1.2 Fabric | 175/176 | 152/153 |
| 26.2 Fabric | 170/171 | 146/147 |
| 总数 | 1503 | **1409** |

按入口、包路径、错误码与诊断首行建立多重集身份，忽略本轮路径与行号漂移：**移除 94，新增 0**。F4 80→0；尾项 26→12，额外消除 14 条，同样来自 Fabric 注入实现缺失方法泛型：fabric_enumCycle、DataComponentMap.Builder 的 get/getOrCreate、MinecraftServer.getOrThrow。其余族计数不变。分类沿用已有互斥口径，不将这些尾项重复加算到 F4。

## 证据与复算

`source-artifact-binding.json` 保存产品修订、源摘要及五个安装 JAR 摘要；每个 JAR 的 TypeReflector.class 均与当前编译输出逐字节一致。安装是新进程与新 mods 目录，不是开发 runServer 产物外推。没有将该关联扩大成任意类/平台的完整加载来源证明。

`validation.zip` 保存前后十入口原文、当前完整分类映射、五节点 stdout/stderr、检查日志和 917 份矩阵 XML。配置/世界/完整进程命令行与 RCON 密码没有入包；平台清单已去除密码。`manifest.json` 固定 ZIP 和每个成员及松散数据文件的摘要。

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-25-primary-erased-method-dad806fa/verify.py
```

复算 PASS 仅表示证据一致，不表示 1409 条完整严格 TS 通过。

实际运行命令：

```powershell
python -X utf8 -m unittest discover -s bench/tsbaseline -p test_run.py
.\gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
python -X utf8 tools/nekojs-ci-gates.py all --out build/primary-f4-ci-gates.json
python -X utf8 bench/tsbaseline/run.py prepare --target build/tsbaseline-primary-f4-dad806fa --revision dad806fa --base-port 37400
python -X utf8 -u bench/tsbaseline/run.py run --target build/tsbaseline-primary-f4-dad806fa
python -X utf8 -u bench/tsbaseline/run.py check --target build/tsbaseline-primary-f4-dad806fa
python -X utf8 bench/tsbaseline/classify_diagnostics.py --ide-dir build/tsbaseline-primary-f4-dad806fa/ide --json build/tsbaseline-primary-f4-dad806fa/root-cause-mapping.json
```

## 未完成

F1 完整捕获合同与其余严格 TS 族仍开放；当前视图排除不获本次审核背书。原性能 PASS 仍绑定 d06cd961，未移用到本轮。最终候选的客户端/TCP/分离回退、Python 默认检查、版本包装、维护者接受及发布决定未代做。票 37 保持 in-progress。
