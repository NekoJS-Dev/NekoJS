# 重载与声明的剩余共享原因

## 已冻结的结果

`7bea6d6d` 的[五节点新候选检查](../architecture-refactor/evidence/2026-10-23-current-candidate-checks/README.md)已提交；完整构建和实际服务器运行通过。正式重载仍为 340.0 / 292.8 ms，原门槛 285.3 ms 不变；完整严格 TSC 仍失败 5867 条。本文的调查和局部修复不替代这两个失败结论。

后续 `1f877b0f` 的[新五节点安装 IDE 测量](../architecture-refactor/evidence/2026-10-23-event-installed/README.md)为十入口 5857 条、全部 exit2、无编译器崩溃；1609 个 Python stub 的默认 Pyright 仍通过。下文局部修复不得借用这个前置制品的数值声称当前声明已通过。

## 内置模块初始化

Luna xhigh 仅只读调查了 `NekoNodeModuleInstaller`、manifest、模块实现及相应测试。prepared Source 已按 runtime owner 缓存，每个 Context 仍须执行初始化。

未采用延迟执行：插件模块在 manifest 后求值，延迟模块可能观察到插件更改的 `Set`、`Object.freeze` 等全局对象；原本发生在候选安装期间、会关闭候选并保留 active 的失败也可能移到首次 require。timers、Buffer、structuredClone 和 process/performance 还设置即时需要的全局值及时间原点。`module.builtinModules`、Java fallback 与插件覆盖次序同样需要保留。`jsx-runtime` 行数较多不能作为改变这些契约的依据。

更细的[主代理阶段诊断](../architecture-refactor/evidence/2026-10-23-loader-preparation-diagnostic/README.md)完成两轮共十次同节奏重载 COMMIT、两次 RCON 正常停止；临时源码已恢复，重建 class/JAR 与原正式候选逐字节一致。第二轮缓存观测用 ThreadMXBean 并细分准备：每次重载的路径/信任 7.557 ms、读取/戳 2.105 ms、前端 4.778 ms、source map 4.055 ms。没有据此新增小收益缓存或省略实时检查；诊断不替代正式性能失败。

## 事件依赖收集

主代理发现事件生成器在输出 payload/dispatch-key 类型后，仍通过 `ProbeConfigLoader.isRelevantClassDefault` 过滤其引用。该默认过滤既不等于当前配置，也不等于已输出签名的依赖策略。回归边界沿用已批准的生产 TypeScript backend 输出和严格 TS 消费。

局部修复将实际引用交给既有 TypeScript dependency closure；当前配置的 exclude/hide 保持生效。被排除的类不生成声明，依赖报告明确不完整，不用空声明或 any 遮盖。复审另指出：未启用 dispatch 的 key、脚本自定义事件未输出的 key 不应成为依赖，须与实际签名条件一致。

此变更不移动交易事件类、不绕过默认 core 排除，也不宣称完整安装声明转绿。运行时、公开成员和 wire 均不改变。修复已提交为 `1f877b0f`，[验收记录](../architecture-refactor/evidence/2026-10-23-event-dependencies/README.md)包含两项先红后绿回归、2416 项 common 测试、严格定向消费及复审结论；完整安装 IDE 与正式性能未据此改判。

## Optional 捕获与协变

Luna xhigh 仅只读核对了 26.1.2 server 的真实诊断和相关 Probe 代码：`describeConstable` 共有 14 处 TS2416，其中九处经 `Optional.or` / Supplier，另五处经 `Optional.filter` / Predicate。两组不可混为九处。

JDK 的 `Constable.describeConstable()` 返回 `Optional<? extends ConstantDesc>`，当前生成成精确的 `$Optional<$ConstantDesc>`。`TypeSlot.sourceType` 仍保存来源，但 `TypeReflector.toRef` 和 TS 的 Java Object 投影会把 wildcard 压为上界。Optional 同时含参数与结果位置，SAM alias 又同时表达回调和 Java host 输入，不能简单给整个 Optional 增加 `out T`。

后续实现需要 TS 后端内部的使用位置捕获表示，并沿现有递归 alias cycle guard 和模块命名视图渲染。已编辑 TypeSlot 必须以有效 IR 为准，不能从旧反射签名恢复被用户编辑掉的 wildcard。五个 boxed primitive / ConstantDesc 边界还需单独核对，不能宣称捕获修复会自然解决它们。

最小真实消费应分别导入 `java:java/lang` 的 `$Boolean` 与 `java:java/lang/constant` 的 `$Constable`、`$ConstantDesc`，验证实现可赋给接口，接口结果保留上界，而具体实现视图保留窄结果；同时覆盖非法参数、SAM 方向、编辑后 IR、递归与局部重命名。当前尚未实现该捕获表示，禁止用全图 any/unknown、成员删除或全局协变放宽代替。

进一步只读调查确认：完整 Optional 还返回 `Stream<CAP>`，其不变泛型成员同样需要递归捕获；现有 SAM 输入 alias 的 host fallback 不能直接充当输出 existential view。九个 or 目标为 Boolean、Byte、Character、Class、Enum、Short、MethodHandle、MethodType、VarHandle；五个 filter 目标为 Double、Float、Integer、Long、String。后者还涉及四个 number/一个 string 与 ConstantDesc host interface 的投影差异。没有据此推出可直接套用的最小正确表示，仍保留未实现状态。

## raw 泛型输出与界限

当前 26.1.2 server 的两条 TS2314 分别来自 Guava Range 和 DiscreteDomain 的原始 `C extends Comparable`；不是手写声明。来源 Type 仍保留，但 TS 输出槽与界限没有复用原本只用于输入 SAM 的 raw-erasure 规则。

本批主代理将 Object 与原始泛型的 TS-only 来源投影合在同一内部实现 JavaTypeScriptProjection，SAM 输入复用它。只有未编辑、反射来源与符号匹配的 raw Class 使用按 arity 的既有 erasure 参数；参数化类、数组与 extends wildcard 递归处理。普通声明的泛型参数、共享 IR、Python、运行时与显式编辑不变。原始 Comparable 界限复用已有 primitive 兼容规则，保留数字/字符串等实际 Graal 值；显式参数化或编辑后的 Comparable 不因此放宽。

最小 TypeReflector → 正式 renderer/index 生成用例先出现三个 Java 断言失败与十一条 TS2314；追加真实 Comparable 数字/字符串消费又出现两条 TS2344，分别修复。改名测试曾错误假设内部引用也直接改名，实际应保留 canonical export alias；跨模块夹具也补上正式 paths 映射，失败日志保留。生产修复 `c0bc2dbe` 的[独立归档](../architecture-refactor/evidence/2026-10-23-raw-generic-output/README.md)记录最终四项 Java 用例（含实际 Graal raw 值读写）、三模式严格 TS 正负例、413 项 Probe（2 skipped）与完整定向 `test:probe-types` 通过。common/check 和 guard 通过（2420 tests / 4 skipped / 零失败错误，含 isolation）；五节点新制品和全图 IDE 尚未据此重测。两轴只读源码审查最终均零剩余项。

## 注册表适配器与目录一致性：下一项实现范围

当前 26.1.2 server 的十二条 TS2694 引用自动适配器生成的 RegistryTypes 成员，而两个平台 provider 都只列十个注册表。另一个 TS2724 来自 BlockStateAdapter 声明 `registry("BlockState")`，实际查询的是 BLOCK；须改为 Block，并保留支持 `[property=value]` 的 string 形状。

现有 RegistryInfos/RegistryTypes Point 服务于 builder，不是实时 Probe 目录；不能借它们猜测已加载注册表。接下来的实现应以现有 AdaptersPoint 已发布的实际 SimpleRegistryBasedAdapter 为来源，复用其持有的 Registry，快照时读取当前条目/标签；保留平台已有十项及手写适配器优先级。通过现有平台目录 provider 的兼容扩展传入实际适配器，不新增第二次反射扫描、全局缓存或独立贡献 Point。共享 Minecraft 层合并真实来源，并显式拒绝同名异来源，而非补十二个硬编码名称或静默合并。标签跨版本读取沿版本 facade/既有节点分割实现。

回归需覆盖未生成/空注册表、快照前后新增条目、手写覆盖、相同来源去重与同名不同来源拒绝、两种快照入口和现有 provider 兼容；以公开 backend 输出严格消费 registry 值/标签与 BlockState 语法。该方案只保证实际发布适配器与目录的一致性，不声称发现 bootstrap 之后才出现、从未发布适配器的全部注册表。五节点构建/实际安装声明随后绑定新源码验证。
