# 完整声明中剩余的不可用类型边界

生产源码6e43b827、五节点安装与完整严格5537条诊断见[当前封存](../architecture-refactor/evidence/2026-10-24-owner-stamp/README.md)。本次不把以下问题当成普通局部导入遗漏，也不放开core排除、补空声明或用any替代。主代理核对当前原始日志和报告；Luna xhigh只读调查相关源码，没有实现或运行检查。

## legacy客户端依赖

`build/closeout-owner-stamp-installed/ide/1.21.1-server-tsc.txt`第384/385/793行分别为TS2552、TS2304与TS2724：ClientboundCustomPayloadPacket的implements和handle引用`$ClientCommonPacketListener`，NeoForge PacketAndPayloadAcceptor又从该模块导入它。当前生成声明实际位置为network/protocol/common/index.d.ts第17/26行；不沿用调查初稿中指向旧记录的433/434、15/24位置。

同批`@special/dependencies.json`已明确记录：ClientCommonPacketListener反射时尝试加载Minecraft客户端类，受到DEDICATED_SERVER限制而失败，complete=false。不是缺少Java签名来源，也没有证据支持补一个普通import就能生成其完整合同。TypeScriptDeclarationClosure在保留过滤规则后用Class.forName(name,false,loader)和TypeReflector扩展依赖，捕获RuntimeException/LinkageError并记录实际不可用原因；原报告没有把这条边判成成功。

同报告还保留ClientGamePacketListener、RecipeBookMenu及若干NeoForge客户端扩展的加载失败。后续若设计客户端元数据贡献或其他真实签名来源，必须证明类型、方法、泛型、继承、注解/隐藏和来源编辑一致，保留节点侧加载限制；不能在专用服务器强行启动客户端类型、猜Java方法或用不完整stub声称全图通过。本轮没有新增此类生产机制。

## 四个被排除的engine类型

| 类型 | 真实引用入口 | 必须保留的行为 |
|---|---|---|
| core.modification.ModificationCandidatePlan | Item/BlockModificationEventJS的公开构造签名 | 候选收集、失败标记、联合预检/提交仍由engine拥有；不能把CandidateStatePlan误作脚本payload完整替代 |
| core.villager.VillagerTradeDeclarationEventJS | ServerEvents.tradeDeclaration的实际payload | add与addedCount，参数校验失败使整批失败，不直接修改live registry |
| core.villager.VillagerTradeReloadEventJS | ServerEvents.tradeReload的实际payload | tradeSets、countOf、total、declareObsolete；显式obsolete与普通reload保留遗漏项不同 |
| core.villager.VillagerTradeQuerySurface | VillagerTradesJS.query()返回值 | readonly、generation-bound状态/原因/代号/adapter/总数/计数、unrestored/retired和describe；STALE给确定空值 |

当前legacy报告将四者明确标为excluded。定向源码搜索没有找到能无损替代它们的api.*领域接口。仅有通用EventBus或core.state.CandidateStatePlan并不构成完整公共合同；不能直接对core.*解禁，也不能把payload替换成裸列表或把查询status随意改成字符串。

后续实现应先确定公共合同和精确贡献接缝，再写公开backend严格消费与真实Graal回归。若引入api.*视图，必须完整保留现有实际公开行为、Java调用者的签名/枚举身份、Bean读写和生命周期，明确每处Java/TS合同影响；必要的新Point/Plugin Hook/Contributor遵循现有配对规则。若需要删除或迁移未授权成员，先形成确切可审阅清单，不扩展已批准的五处Entity.attackable范围。

当前没有为这些类型改源码、hide/exclude或运行时权限。它们只是部分严格失败的已确认边界；其他wildcard捕获、SAM host方向和96个真实字段/方法冲突仍各自待解，不能据本调查替整个5537条诊断归因或关闭票37。
