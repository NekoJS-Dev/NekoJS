# 6e43b827本地六制品交接

本目录是初次准备六制品时的封存清单；后续状态统一见[当前工程候选验收汇总](../2026-10-24-owner-stamp-acceptance/README.md)，其中TCP、三节点安装渲染与已知版本对的分离回退均已完成各自范围。初始ZIP和本地manifest.json保持原字节，不重写当时NOT RE-RUN记录；新增manifest-acceptance.json记录后续结果。完整TS/性能及最终版本/维护者门禁仍开放。

当前工程源码6e43b827的五节点六个确切JAR已准备到`build/release-candidate-6e43b827`：legacy分开普通client和server分类制品，其余四节点各一个runtime制品。共91,265,828字节，包含SHA256SUMS、制品清单和完整生产class绑定。显示版本仍1.1.0-preview3，没有版本切换、重新包装或远程上传，releaseReady=false。

主代理核对每个制品的完整SHA及全部NekoJS生产类，legacy1571、两个NeoForge现代节点各1642、两个Fabric节点各1585。Fabric还打包既有第三方依赖，不能把它们误算为NekoJS生产类或删除。首次准备在该区别上断言失败，保留本机`build/release-candidate-6e43b827-prepare-v1`；随后逐项比对全部绑定类及NekoJS命名空间的精确集合通过，没有接受缺失或改变的生产类。

当前[完整工程检查](../2026-10-24-owner-stamp/README.md)为5158项/271跳过/零失败、五节点新安装与五正常停止、1609默认Python stub通过；完整严格TypeScript5537条FAIL。[原政策性能](../2026-10-24-owner-stamp-performance/README.md)为重载288.2/299.4ms FAIL、启动26174.8ms PASS。原门槛没有改变。

初次封存时该二进制的安装客户端渲染、跨进程TCP和制品/数据分离回退尚未重新运行；三项后续当前证据见上方汇总。此前[渲染](../2026-10-24-candidate-render/README.md)、[TCP](../2026-10-24-candidate-tcp/README.md)、[回退](../2026-10-24-candidate-rollback/README.md)证明继续绑定975496d3。换旧制品不等于恢复数据；已知旧候选对和完整世界夹具不代表任意旧版本或世界外副作用可恢复。维护者结论未记录，本清单不能授权发布。

归档仅包含清单、生产类绑定和准备工具，不复制六个JAR；完整SHA保留在清单中。`python docs/architecture-refactor/evidence/2026-10-24-owner-stamp-handoff/verify.py`已实际通过，核对了本机全部六个JAR及各生产类。若迁移到没有本机制品的环境，工具明确输出localPayloads=NOT PRESENT，只验证归档清单，不能假称重验二进制。
