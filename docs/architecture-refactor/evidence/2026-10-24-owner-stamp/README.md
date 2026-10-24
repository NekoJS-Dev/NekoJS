# 同名事件、owner泛型与模块stamp候选

工程源码固定为`6e43b82789a7dbf7b9659dc1c21fefecac662649`：701dd511保留跨包同名事件身份，7e484118补齐真实非静态owner泛型，05afbd33合并重复属性查询，6e43b827补充消费覆盖并从规范生成入口更新节点golden。主代理实现、运行与封存；Luna xhigh仅只读调查/审查。制品仍为1.1.0-preview3，未发PR、推送、上传或发布，未代签维护者结论。

## 验收结果

普通完整矩阵命令：`:common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`。v2用时5m4s，106tasks中34执行；5158项、271跳过、零失败/错误，含common隔离。`npm run test:probe-types`完整链及`python tools/nekojs-ci-gates.py all`通过。

第一轮矩阵在节点声明golden比较失败；按既有原生regenerate入口生成后，确认10个server/startup文件的差异恰好是raw dispatch元数与Fabric私有import引用，5个client文件字节不变。生成模式的43秒完成不算门禁通过；v2不带regenerate开关。保存15份旧字节、完整diff、确切变换检查器和逐文件SHA。安装派生脚本曾引用首轮失败日志，在创建副本之前拒绝；两处护栏修正为通过的普通v2记录后才实际启动游戏，失败日志亦保留。

五节点fresh安装完成启动、注册、放置、重载与Probe验收，5次RCON正常停止、退出0，没有强杀。默认Python生成1609个stub，Pyright1.1.414零错误、零警告；归档验证器还逐个解析所有stub。完整严格TypeScript的10份server/startup默认入口均使用`skipLibCheck:false`，全部完整输出诊断，无编译器崩溃，仍退出2：

| 节点 | server | startup |
|---|---:|---:|
| 1.21.1 | 489 | 490 |
| 26.1.2 NeoForge | 558 | 559 |
| 26.2.0 NeoForge | 547 | 548 |
| 26.1.2 Fabric | 591 | 592 |
| 26.2.0 Fabric | 581 | 582 |

合计**5537，仍FAIL**。对照87baad4b的5549条，按文件/诊断码/首行消息多重集复算：删除12、新增0；8条TS2300是两个Fabric节点两份入口的私有导入名冲突，4条TS2304是两个26.2节点两份入口中Settings.MutableValue的外类T。1.21.1的`$ClientCommonPacketListener`缺失名字是另一问题，保持未解决；不把最初针对Fabric的owner调查说成所有TS2304都来自它。

## 制品绑定与范围

| 节点 | 生产class数 | 已安装JAR SHA-256 |
|---|---:|---|
| 1.21.1 server | 1571 | `427373434160de73889261358a9a1723a530967c40c98b829da32d315abd212c` |
| 26.1.2 NeoForge | 1642 | `7b0dd1544837cf86ba35ccc4817da40cde54ff7c07544fc3b25fd5437027e8ae` |
| 26.2.0 NeoForge | 1642 | `bc4aaeebb1ed737ef451f3a31efcbdf5205b497742c2804c14ac2bd906fc2ff8` |
| 26.1.2 Fabric | 1585 | `bd9289c048b0995dcbcd6db502a619ece0af969f98d1a38316a24e5a6b7ed7c4` |
| 26.2.0 Fabric | 1585 | `a776e63b6fa0d88fd5016657a4a06032d86b56f691b5e6631b5f61c1e230e158` |

真实事件、方法、字段、编辑槽、排除/隐藏规则和runtime对象不删除、不合并；三层owner、同名变量、static边界、raw、adapter、canonical rename、继承方法泛型遮蔽及manual引用均有公开backend严格TS正负例。主扫描的新增真实owner/bound边仍受原有深度和过滤规则约束。Python保留原有泛型近似，未声称逐字不变。受保护历史common TypeScript golden保持原CFDFA54C…字节、未作为本次源码改动提交；完整安装诊断由当前确切JAR独立生成。

独立属性查询微测量的3预热对+10正式对、26条完整记录亦归档：原fixture18文件，每样本18000查询，顺序交替；旧均值4873.64ns，新2418.68ns，checksum相同、fixture SHA不变。仅证明属性API查询的差异，不能替代285.3ms正式重载门禁。正式性能独立记录；此档案不把微测当作整个重载通过。

## 复核与未结项

运行`python docs/architecture-refactor/evidence/2026-10-24-owner-stamp/verify.py`。主代理已实际通过：核对5176个归档条目的大小/SHA与归档SHA，重算全部XML、五次正常退出、1609stub、10份完整诊断及删除12/新增0、实际owner与事件私有别名、微测原始样本与分组。归档9508142字节，原始制品仍在本地、封存时核对整个JAR及每个生产class；验证器不凭哈希声明重新执行了游戏或TSC。

不归档world、properties和启动参数全文。封存工具按五个安装服实际RCON密码的UTF8/UTF16字节扫描替换，本次匹配及替换文件数为0，因此manifest的redactedOriginalHashes为空；不能把空映射写成已有替换和原摘要。主代理又用audit-local-credentials.py私下读取同批本地properties，对全部条目及嵌套ZIP逐个扫描，实际残留匹配0，结果绑定在privacy-audit.json；不输出或保存私有模式及其摘要。公开verify只核对该审计记录和工具/归档绑定，缺少本机私有模式时不能独立证明没有凭据。未改制品或重封ZIP。源码、红绿日志、所有当前声明与完整诊断、上次诊断、矩阵XML及工具在ZIP中。

严格声明的其他结构错误与96个真实字段/同名方法的可表达性仍未解决，不能以删除成员或放宽门禁处理。975496d3的客户端渲染、真实TCP与分离回退是旧候选证据，没有改名为本次源码已重跑；旧本地候选保留其确切六件制品。维护者迁移、golden、回退与发布人工结论尚未记录，最终1.2版本切换及正式发布未做。
