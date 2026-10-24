# 6e43b827双节点真实TCP复验

当前生产源码6e43b827，两个现代NeoForge节点各15阶段全部通过，共30阶段、14个实际进程正常退出0，无进程超时或强制清理。使用当前六制品中的真实JAR、全新隔离目录和世界；26.1.2 SHA为7b0dd154…，26.2.0为bc4aaeeb…。没有重标975旧结果或在生产中注入故障。

| 每节点套件 | 覆盖 | 正常停止 |
|---|---|---:|
| 八阶段协议 | 空状态、关闭gate的真实NACK、单端提交、配置补同步、双ACK、ACK超时与迟到ACK、PREPARE断线、重连只恢复已激活状态 | 4 |
| 七阶段物理激活失败 | 正常双端提交、A实际注册失败而server/B已提交、在线补同步失败保留A旧条目、持续故障重连拒绝、同A进程移除故障后恢复、再次双端提交 | 3 |

故障仍只匹配测试clientA与精确fixture物品键，在MappedRegistry真实注册入口抛出。server保持COMMITTED、B已拥有新物品；A在线补同步拒绝后保留旧条目，直到夹具显式kick才断开，不称为全局DEGRADED或一律拒绝后续批次。最终真实物品stack=16、rarity=RARE；八阶段套件不触发该故障键。每套件完整保留原断言，没有修改ACK期限或伪造包。

后置binder独立核对1101个生产/构建源码与提交、每节点1642个common/node生产class和整个JAR；每进程另核PID、ModList、CodeSource及五个实际加载类。源码相对6e没有生产变化，后续文档commit不改变该二进制绑定。两个独立driver和原来的协议验证器均exit0。26.1.2两端loader26.1.2.71；26.2.0客户端26.2.0.57、服务端26.2.0.75；helper启动器加载生产JAR，不能称为外部正式安装器或维护者人工验收。

归档261项、18,346,340字节，包含四次运行的全部阶段/命令/日志、helper源码/JAR、绑定和精确工具。26.2.0真实JAR同包；26.1.2按原摘要引用[同源码正式性能包](../2026-10-24-owner-stamp-performance/README.md)。`python docs/architecture-refactor/evidence/2026-10-24-owner-stamp-tcp/verify.py`实际PASS，核对完整30阶段、14正常退出、全部源码/class绑定及加载证明；需要本仓6e Git对象，逐个核对1101个已提交blob与其ID、LF/CRLF工作字节摘要，不表示重新编译或运行游戏。不含world、properties、完整启动descriptor/参数或运行库。原封存工具实际替换文件数为0、redactedOriginalHashes为空；后续私有模式扫描见[验收汇总](../2026-10-24-owner-stamp-acceptance/README.md)，不把空映射说成已有替换前原摘要或公开验证器可独立重做私有扫描。

本项把当前候选的TCP缺口补齐；后续[渲染](../2026-10-24-owner-stamp-render/README.md)和[分离回退](../2026-10-24-owner-stamp-rollback/README.md)也已完成各自范围。整体状态统一见[验收汇总](../2026-10-24-owner-stamp-acceptance/README.md)；完整严格TSC5537条、重载288.2/299.4ms仍FAIL，票37仍in-progress，维护者及发布结论未代签。
