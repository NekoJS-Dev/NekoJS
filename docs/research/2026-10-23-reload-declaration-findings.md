# 重载与声明的剩余共享原因

## 已冻结的结果

`7bea6d6d` 的[五节点新候选检查](../architecture-refactor/evidence/2026-10-23-current-candidate-checks/README.md)已提交；完整构建和实际服务器运行通过。正式重载仍为 340.0 / 292.8 ms，原门槛 285.3 ms 不变；完整严格 TSC 仍失败 5867 条。本文的调查和局部修复不替代这两个失败结论。

## 内置模块初始化

Luna xhigh 仅只读调查了 `NekoNodeModuleInstaller`、manifest、模块实现及相应测试。prepared Source 已按 runtime owner 缓存，每个 Context 仍须执行初始化。

未采用延迟执行：插件模块在 manifest 后求值，延迟模块可能观察到插件更改的 `Set`、`Object.freeze` 等全局对象；原本发生在候选安装期间、会关闭候选并保留 active 的失败也可能移到首次 require。timers、Buffer、structuredClone 和 process/performance 还设置即时需要的全局值及时间原点。`module.builtinModules`、Java fallback 与插件覆盖次序同样需要保留。`jsx-runtime` 行数较多不能作为改变这些契约的依据。

更细的[主代理阶段诊断](../architecture-refactor/evidence/2026-10-23-loader-preparation-diagnostic/README.md)完成两轮共十次同节奏重载 COMMIT、两次 RCON 正常停止；临时源码已恢复，重建 class/JAR 与原正式候选逐字节一致。第二轮缓存观测用 ThreadMXBean 并细分准备：每次重载的路径/信任 7.557 ms、读取/戳 2.105 ms、前端 4.778 ms、source map 4.055 ms。没有据此新增小收益缓存或省略实时检查；诊断不替代正式性能失败。

## 事件依赖收集

主代理发现事件生成器在输出 payload/dispatch-key 类型后，仍通过 `ProbeConfigLoader.isRelevantClassDefault` 过滤其引用。该默认过滤既不等于当前配置，也不等于已输出签名的依赖策略。回归边界沿用已批准的生产 TypeScript backend 输出和严格 TS 消费。

局部修复将实际引用交给既有 TypeScript dependency closure；当前配置的 exclude/hide 保持生效。被排除的类不生成声明，依赖报告明确不完整，不用空声明或 any 遮盖。复审另指出：未启用 dispatch 的 key、脚本自定义事件未输出的 key 不应成为依赖，须与实际签名条件一致。

此变更不移动交易事件类、不绕过默认 core 排除，也不宣称完整安装声明转绿。运行时、公开成员和 wire 均不改变。最终验证及提交见后续交接记录。

## Optional 捕获与协变

Luna xhigh 仅只读核对了 26.1.2 server 的真实诊断和相关 Probe 代码：`describeConstable` 共有 14 处 TS2416，其中九处经 `Optional.or` / Supplier，另五处经 `Optional.filter` / Predicate。两组不可混为九处。

JDK 的 `Constable.describeConstable()` 返回 `Optional<? extends ConstantDesc>`，当前生成成精确的 `$Optional<$ConstantDesc>`。`TypeSlot.sourceType` 仍保存来源，但 `TypeReflector.toRef` 和 TS 的 Java Object 投影会把 wildcard 压为上界。Optional 同时含参数与结果位置，SAM alias 又同时表达回调和 Java host 输入，不能简单给整个 Optional 增加 `out T`。

后续实现需要 TS 后端内部的使用位置捕获表示，并沿现有递归 alias cycle guard 和模块命名视图渲染。已编辑 TypeSlot 必须以有效 IR 为准，不能从旧反射签名恢复被用户编辑掉的 wildcard。五个 boxed primitive / ConstantDesc 边界还需单独核对，不能宣称捕获修复会自然解决它们。

最小真实消费应分别导入 `java:java/lang` 的 `$Boolean` 与 `java:java/lang/constant` 的 `$Constable`、`$ConstantDesc`，验证实现可赋给接口，接口结果保留上界，而具体实现视图保留窄结果；同时覆盖非法参数、SAM 方向、编辑后 IR、递归与局部重命名。当前尚未实现该捕获表示，禁止用全图 any/unknown、成员删除或全局协变放宽代替。
