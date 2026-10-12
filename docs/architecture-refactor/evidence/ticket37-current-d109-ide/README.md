# 当前 d109：五节点真实 Probe 与完整 IDE 结果

五个当前官方 JAR 在新专属世界完成 STARTUP 注册、放置、SERVER重载及实际 Probe生成；1600份 Python stub AST全部通过，五服 RCON正常关闭/直接0，十个端口释放。**完整 IDE仍失败**：保留真实默认 editor includes，严格 TypeScript诊断数与旧93相同；Pyright仍为7/7/7/13/13。生成输出没有手工修补，没有修改配置来隐藏错误，没有删除manual生产器或改变 HostAccess/ClassFilter。

源码 `d10912ebbf24d9d5df75a908dc7a5529da09979a`，checkout `9d4280b74f3896137c408110e48c4002f2d1f703`，差异仅文档。每份实际安装 JAR的全部可匹配 NekoJS class与该节点/common当前编译字节逐项相等，class清单和实际制品SHA在[结果](runtime-summary.json)。运行使用原先明确所有权的 loader缓存（NeoForge21.1.227/26.1.2.71/26.2.0.75、Fabric0.19.3/0.19.5）、JDK21.0.10/25.0.2，未重新下载或认证账号。

| 节点 | Python AST | 完整 TS SERVER/STARTUP | Pyright错误 | 实机注册/放置/重载 |
|---|---:|---:|---:|---|
| 1.21.1 NeoForge | 319通过 | 755/756失败 | 7失败 | 通过 |
| 26.1.2 NeoForge | 378通过 | 929/930失败 | 7失败 | 通过；effect实际施加及重载后重新施加 |
| 26.2.0 NeoForge | 346通过 | 814/815失败 | 7失败 | 通过 |
| 26.1.2 Fabric | 295通过 | 1290/1291失败 | 13失败 | 通过；无Fabric fluid能力推断 |
| 26.2.0 Fabric | 262通过 | 1030/1031失败 | 13失败 | 通过；无Fabric fluid能力推断 |

每个真实项目的验收 tsconfig仅extends原 editor jsconfig并设置 `skipLibCheck=false`、`noEmit=true`，没有覆盖 include/typeRoots/paths或增加排除。Python按原 pyrightconfig分析每一份真实生成stub；版本1.1.414。检查wrapper直接0代表完成了报告收集，**不是**tsc或Pyright通过：实际tsc直接2、Pyright直接1都完整记录。

与[旧93真实输出](../ticket37-ts-same-name-import-repair/README.md)相比，去掉安装根路径及诊断行/列之后的 `file + TS code + first message` 多重集合在十个项目中新增0、删除0。该比较不包括后续多行解释，不能声称所有原始文本字节或所有诊断坐标相同。错误主要仍来自完整manual闭包缺失名称、包出口/引用、泛型约束、继承成员冲突；Python仍为字段/同名方法重声明。当前社区修复及item组件变更没有消除这些整体失败。

当前源码已含实际 STARTUP事件关联、Python重载及受限HostAccess回归，不能把旧[STARTUP源码调查](../ticket37-b7-candidate-validation/STARTUP-TRACE.md)中的“尚未实现”当作现在的源码状态。本轮实际使用它们，未重复实现或删除manual。完整 `@manual`仍被默认项目纳入，局部事件caller通过也不能给完整IDE盖章。

## 证据与验证

[原始日志/命令/生成输出/IDE报告](current-d109-ide-evidence.zip)、[逐项清单](manifest.json)、[独立验证器](verify.py)。压缩包3439条、6903333字节，SHA256 `0fb7bc600c344697ecfab77351313302cc6b6a297ba8341fd11c081bc1683e30`；独立验证器读回全部hash、1600AST、真实exit/诊断数及未收窄tsconfig。实际RCON密码逐个扫描，server.properties、world、制品/缓存、rawJFR/hs_err及无关文件不入库。

本地原始环境 `build/ticket37-current-d109-installed`，helper `build/ticket37-config-sync-repair`，精确执行源已冻在包内：

```powershell
python build/ticket37-config-sync-repair/prepare-current-d109-installed.py
python build/ticket37-config-sync-repair/run-other-nodes.py build/ticket37-current-d109-installed --skip-install
python build/ticket37-config-sync-repair/prove-other-nodes.py build/ticket37-current-d109-installed 1.21.1 26.1.2 26.2.0 26.1.2-fabric 26.2.0-fabric
python build/ticket37-config-sync-repair/check-current-d109-ide.py
python docs/architecture-refactor/evidence/ticket37-current-d109-ide/verify.py
```

准备/运行采用fresh-root/exclusive-create，旧实例不能原地覆盖重跑。各服原始输出、既有legacy能力省略及 warning留在包中，局部runtime PASS不是所有平台注册能力都通过。

本轮没有修改生产代码，未重复已通过的源码矩阵。正式 d109性能此记录时尚未开始，下一步按原采样器、门槛、首样本、cache/path/reparse守卫执行；它不能由旧93成绩替代。其余客户端节点、多人abort/download、第一像素、磁盘/领域、维护者结论及发布回滚仍需单独收口，整体未完成。
