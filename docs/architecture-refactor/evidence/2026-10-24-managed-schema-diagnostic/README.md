# 实际服务器快照的managed schema微测量

源码975496d3，实际安装NeoForge26.1.2.71、同批2206b924…生产JAR，使用原性能脚本fixture与全新世界。诊断helper在真实服务器owner thread读取NekoPluginRuntime的SERVER surface；实际148个symbol（7global/141member），7个global需提取schema，共79个成员名，启动及五次真实无错误COMMIT后记录的System.identityHashCode相同；这不单独证明对象引用相同。

| 检查点 | 单次首测ms | 1000条均值ms | 排序index500 ms | index950 ms | 最大ms |
|---|---:|---:|---:|---:|---:|
| 启动后00 | 0.3849 | 0.0856129 | 0.0727 | 0.1491 | 0.5536 |
| 重载后01 | 0.1854 | 0.0834306 | 0.0598 | 0.0911 | 12.1258 |
| 重载后02 | 0.1013 | 0.0570144 | 0.0548 | 0.0713 | 0.4803 |
| 重载后03 | 0.1010 | 0.0584294 | 0.0553 | 0.0812 | 0.2883 |
| 重载后04 | 0.1111 | 0.0537427 | 0.0490 | 0.0691 | 0.3172 |
| 重载后05 | 0.1478 | 0.0997818 | 0.0844 | 0.2255 | 1.1719 |

每检查点先记录首测，再运行100次预热，并保留随后全部1000条微测量；均值包含12.1258ms等全部不利样本，未剔除。测量包含global索引构建和现有ScriptBindingSchema.fromSurface重新提取，不包含guest proxy/knownGlobals/View/Context安装。这是在启动或重载完成后的重新提取，不是实际候选BINDING阶段分段计时，更不是原runServer正式采样。当前这段纯metadata量级不足以支持新增root缓存；没有据此实现缓存或声称解决重载超标。

首轮v1在第3次重载后的请求写入被读取到半写空内容，独立helper抛异常并触发服务器崩溃。进程虽exit0，仍明确NOT GREEN，不计正常停止。原Java/driver、前三个部分测量、错误日志与结果均保留；Netty日志渲染的后续错误不冒称首因。主代理只改夹具：请求同目录原子替换并重试短暂Windows共享冲突，结果写完后原子发布。全新v2完成六检查点和6000条测量、五次无错误真实COMMIT及一次RCON正常停止0，无强制清理。生产源码及JAR未改。

归档73条目、519,713字节，包含两次原始诊断、精确helper源码/JAR、原始samples、五个实际加载类/PID证明、1641生产类绑定和工具。不含world、properties、完整编译/启动参数、运行库或完整系统crash report，实际测试RCON密码字节按UTF8/UTF16脱敏；实际生产JAR按摘要引用同源码[正式性能归档](../2026-10-24-method-bounds-performance/README.md)。复核：`python docs/architecture-refactor/evidence/2026-10-24-managed-schema-diagnostic/verify.py`。

复核PASS仅确认封存及微测量可复算。正式重载313.8/308.6ms仍超过285.3ms；完整严格TSC5565条仍FAIL，原门槛/采样器/fixture未改。既有当前候选客户端/TCP/回退证据不由本诊断重新判定。
