# 当前候选验收后的共享原因推进

最新源码6e43b827的[完整验收](../architecture-refactor/evidence/2026-10-24-owner-stamp/README.md)通过5158项/五节点新安装/1609默认stub，完整严格TSC5549→5537，移除12/新增0，仍FAIL。[正式原门槛](../architecture-refactor/evidence/2026-10-24-owner-stamp-performance/README.md)重载288.2/299.4ms失败，启动26174.8ms通过。[六制品清单](../architecture-refactor/evidence/2026-10-24-owner-stamp-handoff/README.md)已绑定6e，旧975渲染/TCP/回退不冒称本轮。owner捕获与事件局部别名已实现、生成golden已用原流程更新；字段/方法同名、wildcard捕获与完整TS仍待解决。下一性能诊断见[source map分段](2026-10-24-source-map-followup.md)，不据猜测新增缓存。

最新工程源码87baad4b的[继承属性修复](../architecture-refactor/evidence/2026-10-24-inherited-bean/README.md)已完成5152项完整矩阵、五节点新安装与1609默认stub，完整严格TSC降到5549条，仍FAIL。[最新原政策性能](../architecture-refactor/evidence/2026-10-24-inherited-bean-performance/README.md)重载287.4/305.6ms仍FAIL、启动28014.8ms通过。975496d3的[本地候选交接](../architecture-refactor/evidence/2026-10-24-candidate-handoff/README.md)仍保留其六个确切JAR及客户端/TCP/回退证明，不冒称那些流程已对87重跑。维护者与发布结论未代签。

## managed schema微测量：未新增缓存

只读调查找到ScriptEnvironmentFactory.bindManagedGlobals逐global调用ScriptBindingSchema.fromSurface、每次扫描完整surface.symbols的重复路径。主代理的[实际服务器快照诊断](../architecture-refactor/evidence/2026-10-24-managed-schema-diagnostic/README.md)已完成：原性能fixture、当前生产JAR、真实SERVER surface有148symbol与7个需处理的global；启动后和五次无错误重载后共6000条重新提取微测量，六组均值0.0537427–0.0997818ms，保留全部样本及12.1258ms最大值。v2 RCON正常停止0；v1夹具半写请求导致的崩溃独立保留，未作为正常停止或成功测量。

该测量是在启动/重载完成后重新执行纯提取，不是整个候选BINDING阶段的分段计时，不能据此给全阶段下结论。当前量级不足以支持增加root缓存；生产未改，正式性能失败不变。若后续不同真实插件集合表现出显著开销，才考虑按不可变ApiSurfaceSnapshot身份保留索引；每代proxy、knownGlobals、View、reporter和候选提交/失败边界继续独立。JavaMemberIndex已有Class缓存，不因调用每代出现就重复做反射缓存。

另一轮Source身份调查确认普通新代URI与改写文本没有generation UUID；ESM capture局部revision只因显式失效变化。性能fixture为扁平JS，require使用内建fs/os/java路径，不含自有ESM依赖；新增ESM Source强引用缓存不能解释本次失败。已有共享Engine、bundled Source和guest状态隔离保持，不延迟初始化或转移失败时机。

## 声明方案继续保留真实合同

当前975候选的[30轮分段诊断](../architecture-refactor/evidence/2026-10-24-reload-stage-profile/README.md)已完成，临时源码和1641个class/JAR恢复为确切原字节。脚本execution与配方墙钟均值约45.9/42.2ms，裸Context build约0.5ms；细分样本支持继续核实文件路径工作，不支持新增schema或Context缓存。诊断的节奏、样本数和JFR开销不同，未取代正式失败。

主代理的[raw接口重载原型](../architecture-refactor/evidence/2026-10-24-declaration-prototypes/README.md)证明：raw-first虽然保留普通调用结果，却仍给显式泛型具体类调用增加未经证明的返回保证；Java实际反例和TS2578负例均封存。没有把它加入生产，也未改Python或现有编辑选择。

getter-only Bean的readonly属性表示已按[窄投影方案](2026-10-24-inherited-bean-properties.md)由主代理实现并通过最小红绿、扩展回归、完整矩阵和五节点新实际安装，全图删除16条TS2611、没有新增。Bee.goalSelector保持只读，AbstractArrow.noPhysics保持可写；field-backed getter用既有字段writeType。真实方法、读写类型、override与隐藏/编辑父类边界保留，不反射被排除父类，也不扩展五处Entity.attackable删除批准。

[TS2300分类](2026-10-24-ts2300-followup.md)已由Luna只读调查和主代理独立解析核实：五份server基线1006个位置，跨节点96个真实字段/方法成员、1个重复import名；其中Bean accessor alias冲突为0。跨包同名事件import已由701dd511复用既有局部别名机制修复，6e完整安装差分确认移除8条；96个真实字段/方法成员保持。真实字段/方法的可表达性仍受已封存Graal反例约束，不能自动删除未授权成员。

## 后续限定调查

当前6e源码的[source map实测](2026-10-24-source-map-followup.md)已证实重复键，但每轮parse合计均值2.80407ms，不能据此宣称能消除正式超标，未新增memo。

两处TS2416代表也不支持“缺少父类同名overload”：ServerPlayer/Player.teleport两侧均有坐标与TeleportTransition签名，IsoChronology/AbstractChronology.dateNow两侧均有Clock、ZoneId和无参签名；TypeReflector已经重发可见父类同名Method。level()的诊断明确追到ServerLevel.noSave字段与Level.noSave()方法冲突；teleport日志只展开到arity，尚未证明它的叶子根因，不能用前者替后者下结论。未新增重复overload、raw/any或删除成员。

后续[十轮配方实测](../architecture-refactor/evidence/2026-10-24-owner-recipe-diagnostic/README.md)已完成：原fixture每次reload COMMIT恰好一遍apply、每遍1515条，另十次list查询不算reload；codec均值37.52728ms，filter1.66348/copy1.93726/mapBuild1.04497ms，十次COMMIT和一次正常停止。单遍RegistryOps已经memoize注册表查找，Recipe/Ingredient codec静态复用，没有据此增加缓存。源码、1642class与整个JAR恢复原字节。此诊断不取代原正式288.2/299.4ms失败。

条件性的目录数据包路径仍须独立验证：SERVER命令先applyRecipeScripts，若目录数据包激活再触发资源重载，其尾部也应用配方。两遍可能使用不同资源基线、分别提交RecipeMap并触发回调，不能直接跳过其中一遍。本次原flat fixture的单遍结果不推广到该路径。每次JSON工作集的深拷贝继续保护基线，不缓存可变recipe或改变事件/失败时机。

[不可用声明边界](2026-10-24-unavailable-declaration-boundaries.md)另核实了legacy客户端类型加载限制与四个真实core引用，原依赖报告已明确不完整。没有现成api.*领域接口可无损代替，不把这些错误当成可随意补import、空stub或放开core的依据。

Optional/Stream的捕获与SAM host方向、Bean/同名方法、Mixin泛型来源仍需独立设计和真实消费证据。不通过全图any/unknown、全局协变、删除成员、空声明或放宽exclude/hide掩盖失败。后续生产改动完成后再冻结新候选，原政策复验仍使用原采样器、fixture与285.3ms门槛。
