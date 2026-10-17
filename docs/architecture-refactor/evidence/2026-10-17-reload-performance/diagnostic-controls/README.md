# 独立性能对照：排除的假设与快速重载诊断

日期：2026-10-17。生产源码仍为 `6e5bb8f0`；本目录没有引入生产修改，也没有新增正式性能组。最新正式结论仍是[重载 FAIL、启动 PASS](../trust-6e5bb8f0/README.md)。下列组件均不能代替原门禁。

## CommonJS Source 替换：否决

共享 Engine、每轮新 Context、六个真实存在路径上的最小模块，配置生产相关的 strict、Nashorn compatibility 和 latest ECMAScript。五轮预热/arm，随后十对交替，每 arm 十个 Context。旧方案调用 `new Function` 并附绝对 `sourceURL`；新方案在各 Context 单独 eval 同一个不可变 Source，再执行本 Context 的函数。参数对象在计时前创建。

六模块均值：动态 Function 0.506983 ms、Source 0.266995 ms，差异约 0.24 ms。执行段 `FileSystem.toRealPath` 回调两方案均为 0。它只证明这个最小场景没有该回调，不能证明真实 fixture、其他文件系统方法或全部 Graal 内部行为没有 I/O。

每个 Context 的计数从 1 独立增长至 6，但 `this === globalThis` 在旧方案为 true、新方案为 false。原型因此否决，不进入生产，也不把函数包装换成“貌似等价”的版本。它尚未完成源码位置、动态 Function 覆盖、循环 require 和 watchdog 全部回归，不能声称可兼容替换。

保留失败：首版 Java import/构造参数编译错误；v2 未设置 latest ECMAScript 导致箭头语法错误；v3/v5 对 this 的相反预设分别失败；v4 生成脚本自身 NameError，在运行前失败。v6 根据实际观察分别验证旧/新语义差异后完成对照。没有删除失败来制造兼容性结论。

## 正则预编译：收益不足，未修改生产

精确当前 `GlobalBindingMemberValidator` 与只把八个固定 Pattern 提为静态常量的候选，独立 classloader、同一 JVM，以原六份源码测试 `collectSourceLevelNames`。每 arm 一百轮预热，十对交替，每 arm 一百轮；每次比较收集出的完整名称集合，均相同。

六源码均值 0.7455271 → 0.7288971 ms，差异约 0.0166 ms。这是私有方法微基准，不能表示完整 validator 或真实重载收益，不足以支持当前优先修改。没有改变声明收集规则、用 token 扫描取代正则或跳过诊断。

## 快速 root 重载闭环

组合现有 `PackActivationTest` / `ScriptReloadGenerationTest` 装配方式，走生产 `NekoRuntimeRoot`、SandboxFactory、Node installer、module preparation 和文件系统。原六个 server fixture 逐字复制到专属诊断目录，提供四个 stub Event Group，无普通 Minecraft Binding；不创建 RUN_BENCH，不分发事件，因此 fixture 自有开关仍关闭重负载和定时采样。

连续三十次 reload 全部 COMMIT、错误记录为 0、每次新 Context、每个旧 Context 在下一轮关闭后拒绝 eval、每轮恰有六个 prepared entries。验证的是关闭和代际替换，没有证明 GC 回收、包 Context 内存成本或完整脚本包最终验收。

- 首版在反射读取不存在的 context 字段时失败，保留日志；v2 改用已有 Context 访问入口后成功，但 classpath 缺少 SLF4J provider，脚本日志走 NOP。
- v3 补齐匹配的 Log4j api/core/slf4j2-impl 2.25.2，脚本日志实际写入 `server.log`，保留内容。三十条原始样本全部保留，首值约 1419.69 ms、末二十均值 55.71 ms。
- 上述均值只描述带 JFR 的独立诊断，没有使用原正式预热规则。它缺少 Minecraft、完整平台绑定、配方重应用、RCON、生产控制台日志配置，路径在 D 盘，因此不能作为 285.3 ms 门槛的合格成绩，也不能把 v2/v3 差异全归因于日志。
- JFR 按末二十 root 调用窗口过滤，只输出安全的聚合摘要：主线程 CPU/native 样本 113 个。采样次数不是耗时；原始 JFR 没有提交。

此工具可用于更快否证后续假设，完整游戏环境仍需继续补分段计时。只读调查确认内建 17 个资源多数建立每 Context 的 globals、时钟或 runner 状态；没有整体懒加载。JSX runtime 的独立延迟加载尚无逐资源计时收益证据，也会影响初始化失败时机，暂未实现。

## RCON 客户端启动对照

同一 Python 3.14.4、二十对交替子进程、仅导入 RCON 需要的模块而不连游戏：正常 / `-S` 均值 41.25153 / 36.699485 ms。没有改变正式采样器或运行环境；这一对照不能证明实际网络及调度耗时。

## 归档与复核

[evidence.zip](evidence.zip) 含 75 个条目、64253 字节，SHA-256 为 `c49b9874accf70ba5772ec8a464c616e07a944f46fdfad93b605f52084f86e72`。含原型源码、原始日志和样本、正则候选、失败记录、root profiler 聚合和实际脚本日志；无 JAR、class、world、凭据或未筛选 JFR。[manifest.json](manifest.json) 绑定各条目。

```powershell
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/diagnostic-controls/verify.py
```

退出 0 仅表示归档一致、已保存数值可复算和诊断约束成立。它不重跑原型、不验证完整游戏行为、更不表示正式性能通过。
