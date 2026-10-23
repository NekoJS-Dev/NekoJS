# 当前候选验收后的共享原因推进

当前975496d3的[本地候选交接](../architecture-refactor/evidence/2026-10-24-candidate-handoff/README.md)已包含六个确切JAR、完整矩阵/五节点服务端/默认Python，以及本轮安装客户端、真实TCP和分离回退证据。生产源码本片段未改。整票仍有两项工程失败：原门槛重载313.8/308.6ms和完整严格TSC5565条；维护者与发布结论未代签。

## managed schema微测量：未新增缓存

只读调查找到ScriptEnvironmentFactory.bindManagedGlobals逐global调用ScriptBindingSchema.fromSurface、每次扫描完整surface.symbols的重复路径。主代理的[实际服务器快照诊断](../architecture-refactor/evidence/2026-10-24-managed-schema-diagnostic/README.md)已完成：原性能fixture、当前生产JAR、真实SERVER surface有148symbol与7个需处理的global；启动后和五次无错误重载后共6000条重新提取微测量，六组均值0.0537427–0.0997818ms，保留全部样本及12.1258ms最大值。v2 RCON正常停止0；v1夹具半写请求导致的崩溃独立保留，未作为正常停止或成功测量。

该测量是在启动/重载完成后重新执行纯提取，不是整个候选BINDING阶段的分段计时，不能据此给全阶段下结论。当前量级不足以支持增加root缓存；生产未改，正式性能失败不变。若后续不同真实插件集合表现出显著开销，才考虑按不可变ApiSurfaceSnapshot身份保留索引；每代proxy、knownGlobals、View、reporter和候选提交/失败边界继续独立。JavaMemberIndex已有Class缓存，不因调用每代出现就重复做反射缓存。

另一轮Source身份调查确认普通新代URI与改写文本没有generation UUID；ESM capture局部revision只因显式失效变化。性能fixture为扁平JS，require使用内建fs/os/java路径，不含自有ESM依赖；新增ESM Source强引用缓存不能解释本次失败。已有共享Engine、bundled Source和guest状态隔离保持，不延迟初始化或转移失败时机。

## 声明方案继续保留真实合同

主代理的[raw接口重载原型](../architecture-refactor/evidence/2026-10-24-declaration-prototypes/README.md)证明：raw-first虽然保留普通调用结果，却仍给显式泛型具体类调用增加未经证明的返回保证；Java实际反例和TS2578负例均封存。没有把它加入生产，也未改Python或现有编辑选择。

getter-only Bean的readonly属性表示可继续在编辑后IR图上小范围探索。当前Bee.goalSelector只读，而AbstractArrow.noPhysics有setter，不能同改readonly；field-backed getter还可能没有Bean setter但仍有字段writeType。任何实现都须保留真实访问器方法、原读写类型、显式override和隐藏/编辑父类边界；不能反射被排除父类来恢复信息，也不能扩展此前仅五处Entity.attackable删除批准。此项目前仅只读调查，未实现。

Optional/Stream的捕获与SAM host方向、Bean/同名方法、Mixin泛型来源仍需独立设计和真实消费证据。不通过全图any/unknown、全局协变、删除成员、空声明或放宽exclude/hide掩盖失败。后续生产改动完成后再冻结新候选，原政策复验仍使用原采样器、fixture与285.3ms门槛。
