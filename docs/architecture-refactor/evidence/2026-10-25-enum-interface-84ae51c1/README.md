# 枚举实例接口合同：84ae51c1

运行开始于 2026-10-25，封存复核于 2026-10-26。产品提交 `84ae51c1`，分支 stonecutter；上一轮安装绑定 dad806fa。完整严格 TS **仍 FAIL**，没有维护者整体接受或发布授权。

## 修复与合同

F2 的主要叶因并不是 java.time 的源签名错误，而是枚举生成器只保留 Enum<Self>、丢失真实 implements。IsoEra 因此缺少 Era 的实例成员，连带使 LocalDate、IsoChronology 等合法协变合同无法满足。

生产生成器现在从编辑后的 IR 补回实例接口继承，并保留静态 values/valueOf 与实例继承分离；没有改变运行时方法、数据、权限或 wire 合同。默认方法、泛型及 self 参数、重命名、移除接口均进入严格正负消费。隐藏接口不被重新生成；按照既有边界保留真实引用，由依赖报告标记 hidden/complete=false，不能靠删除来源合同伪造完整图。

F1 的 or、部分 reduce/collect、toList 缺失仍未解决；本轮没有修改或认可其排除规则。

## 新五节点实际安装

使用现有驱动创建全新安装/世界（`build/tsbaseline-enum-84ae51c1`），端口 37800/37801 起，每节点递增10；JDK21/25和外部 mod 沿用驱动固定配置。五节点全部 Probe 完成、RCON stop 成功、exit0、未强杀、世界锁释放，文件数394/448/430/540/519。

`source-artifact-binding.json` 保存五个实际安装 JAR 的完整摘要；各 JAR 内 **TypeScriptClassRenderer.class** 与本次 common 编译类逐字节相同。这个检查范围不外推为全部运行时类路径一致。准备清单移除了 RCON 密码；配置、世界、完整进程命令行未入归档。

| 节点 | dad806fa server/startup | 84ae51c1 server/startup |
|---|---:|---:|
| 1.21.1 NeoForge | 102/103 | 69/70 |
| 26.1.2 NeoForge | 154/155 | 121/122 |
| 26.2.0 NeoForge | 148/149 | 115/116 |
| 26.1.2 Fabric | 152/153 | 119/120 |
| 26.2 Fabric | 146/147 | 113/114 |

十入口共 **1409→1079**；每个入口仍是 tsc exit2/diagnostics。分类闭合1079、零UNMAPPED；F2 **330→0**，其余各族总数完全相同，包括 F1=50、F4=0。

## 原文差分与同源差分必须分开

保持原有 `(入口、文件、错误码、首行原文)` 多重集口径，结果是 **移除334/新增4**，不是原文零新增。四条变化都在1.21.1的 server/startup 客户端 listener 缺失引用：TS2304与带“Did you mean”提示的TS2552之间发生变化。不能因为分类总数不变就直接忽略。

本次额外保存这些诊断的真实所属声明、源码行和缺失名称，并把原始声明文件一同入包。仅对完整匹配“Cannot find name”的TS2304/TS2552按 `(入口、文件、缺失名称、所属声明、源码行)` 比较，保留重复次数；其余错误码/原文原样比较。这样重算为 **移除330/新增0**：变化的4条仍是同一引用位置，未解决，也不是新的未解析引用。两种口径都保存在 `diagnostic-delta.json`，不覆盖原始记录、不改分类器、不放宽任何 tsc 门禁。

## 实际验证

- 最小旧实现红证：新泛型接口默认方法用例在 Java 生成断言失败，严格消费报TS2741/TS2339；修复后正例可用，错误参数/返回继续拒绝。
- 七项枚举回归及六组严格消费通过；`npm.cmd run test:probe-types` 全链exit0。
- 普通 common/check 初次因两项 golden 差异失败；备份后通过原生单类 regenerate 更新四个文件，仅补回接口与相应空接口排序。随后普通门禁不带再生开关。
- 早期矩阵未退出时启动了补跑，发生固定临时游戏目录与结果文件交叉污染；日志占用、失败XML、挂起线程栈和回收身份记录均保留。挂起测试 worker4940经确认后回收。这两轮 **不计通过**，未更改断言或产品运行时。
- 最终用全新临时目录、`--no-daemon --no-parallel --max-workers=1` 运行 common/check、处理器、guardLint和五节点build：2m25s，106 tasks通过；节点测试有缓存复用。冻结917份 test/platformGateTest XML合计 **5187tests、271skipped、零失败/错误**。不计旧 regenerate/NBT 等非本轮任务目录。
- 四项 CI gate通过；Standards/Spec只读复审均无新增确证问题。主代理实现和执行验证，子代理没有修改文件。
- 新安装五节点和十入口原始诊断、互斥分类已重采；没有使用旧制品的生成树代替本轮。

核心复验命令：

```powershell
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
python -X utf8 tools/nekojs-ci-gates.py all --out build/enum-interface-gates.json
python -X utf8 bench/tsbaseline/run.py prepare --target build/tsbaseline-enum-84ae51c1 --revision 84ae51c1 --base-port 37800
python -X utf8 -u bench/tsbaseline/run.py run --target build/tsbaseline-enum-84ae51c1
python -X utf8 -u bench/tsbaseline/run.py check --target build/tsbaseline-enum-84ae51c1
python -X utf8 bench/tsbaseline/classify_diagnostics.py --ide-dir build/tsbaseline-enum-84ae51c1/ide --json build/tsbaseline-enum-84ae51c1/root-cause-mapping.json
python -X utf8 docs/architecture-refactor/evidence/2026-10-25-enum-interface-84ae51c1/verify.py
```

前五条运行命令需其对应工具链和已构建制品；verify只读取冻结归档，不启动游戏。未来重采应另设新target与可用端口，不覆盖现有安装/世界。

## 未完成

严格TS尚余1079条，F1公共捕获合同仍开放。当前候选的默认Python安装/Pyright、正式性能、客户端/PostEffects/TCP/制品与数据分离回退未重测；性能PASS仍仅绑定d06cd961。票37保持in-progress。未push、未发PR、未上传或发布，未代签维护者结论。
