# 6e43b827当前工程候选验收汇总

本目录是当前6e43b827五节点六制品的后续工程验收汇总。生产代码与初次六制品一致；后续文档commit没有改变制品。当前TCP、三个受支持NeoForge节点的安装渲染及已知版本对的26.1.2分离回退已补齐，完整严格TypeScript与原门槛重载仍失败，releaseReady=false。

| 项目 | 绑定该源码的实际结果 | 原始证据 |
|---|---|---|
| 完整矩阵 | 5158项 / 271跳过 / 零失败错误，含common隔离与五节点构建 | [完整检查](../2026-10-24-owner-stamp/README.md) |
| 新安装服务端 | 五节点通过，五正常停止 | [安装与IDE](../2026-10-24-owner-stamp/README.md) |
| 默认Python | 1609 stub，默认Pyright零错误警告 | [完整声明](../2026-10-24-owner-stamp/README.md) |
| 完整严格TypeScript | **5537条，FAIL**；删除12 / 新增0，不放宽默认includes或skipLibCheck | [完整诊断](../2026-10-24-owner-stamp/README.md) |
| 正式重载 | **288.2 / 299.4ms，FAIL**，原门槛285.3ms | [原政策性能](../2026-10-24-owner-stamp-performance/README.md) |
| 正式启动 | 26174.8ms，PASS，原门槛41173ms | [17有效记录 / 9正常停止](../2026-10-24-owner-stamp-performance/README.md) |
| 真实TCP | 两节点30阶段 / 14正常退出，含物理激活失败及同进程恢复 | [TCP](../2026-10-24-owner-stamp-tcp/README.md) |
| 安装客户端渲染 | 30原图逐张查看、13合成原生F3+T / 六正常退出 | [渲染](../2026-10-24-owner-stamp-render/README.md) |
| 制品/数据分离回退 | 已知d91ca974↔6e、26.1.2完整世界夹具，四阶段 / 四正常退出 | [回退](../2026-10-24-owner-stamp-rollback/README.md) |
| 最终版本与维护者结论 | 仍为1.1.0-preview3；最终1.2.0包装未做、维护者未签收 | [票37](../../implementation-tickets/37-release-1-2-0-handoff.md) |

最新本地交接清单是`build/release-candidate-6e43b827/manifest-acceptance.json`，仍引用同一六个JAR和完整SHA/class绑定。初始manifest.json、SHA256SUMS与[初始交接ZIP](../2026-10-24-owner-stamp-handoff/README.md)保留原字节；其中NOT RE-RUN是初次快照，不覆盖本目录后续证据。新归档8项/410,583字节只保存汇总、初始清单、class绑定、收集工具和隐私审计记录，不重复打包六JAR。

`python docs/architecture-refactor/evidence/2026-10-24-owner-stamp-acceptance/verify.py`已实际通过，核对六份依赖归档、各门禁原记录及本机全部六JAR/生产class；缺少本机制品时明确报告localPayloads=NOT PRESENT。各原始证据的verify.py另核完整诊断、Git源blob、实际阶段、原图ROI或保留的世界目录，汇总验证不假称重新运行游戏或重新执行完整测试。

主代理另用audit-local-credentials.py对TCP/render/rollback三个封存包，分别私下读取其测试properties，以UTF8/UTF16字节模式扫描全部条目及嵌套JAR/ZIP，共24,729个payload，残留匹配0。三个原封存工具实际替换文件数均为0，redactedOriginalHashes为空，没有需要记录的替换前原摘要；不能把空映射说成已有脱敏记录。privacy-audit.json只保存计数及工具/归档绑定，不保存私有值或其摘要。公开验证只绑定这份本地审计记录，缺少私有模式时不能独立重复证明无凭据；原归档字节未改。

范围保持：Fabric PostEffects unavailable、legacy resources-only不扩大；ModDev helper、合成按键和帧缓冲图不等于官方安装器、物理键盘/显示器或维护者验收；回退不承诺任意旧版、custom registry存档或世界外副作用。票37保持in-progress，没有PR、push、远程上传、正式发布或人工代签。

初次[显式方法选择器原型](../2026-10-24-method-selectors/README.md)完成26.1.2 NeoForge实际观察及最小严格消费；后续[默认继承范围与完整副本](../2026-10-24-selector-closure/README.md)完成五个安装签名采集，并在保留安全分支普通写法的方案中得到5537→1621、删除3916/新增0。生产未迁移，规则仍待用户批准；本页实际默认5537条FAIL和原性能FAIL保持，副本原型不改变候选或发布结论。

后续[模块准备分段诊断](../2026-10-24-owner-source-diagnostic/README.md)完成十次COMMIT和确切源码/class/JAR恢复。测量范围有限，没有生产优化或门槛变更；原性能FAIL保持，下一调查边界见[测量后的方向](../../../research/2026-10-24-module-preparation-profile.md)。

后续[路径边界诊断](../2026-10-24-owner-path-diagnostic/README.md)完成十次COMMIT、440个调用事件及确切源码/class/JAR恢复。所测区间约5.32ms/轮、非.js存在性探测约0.005ms/轮，未形成生产优化；不能把这些值直接扣减正式结果。
