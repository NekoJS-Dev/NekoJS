# 原始泛型的 TypeScript 输出

生产提交 `c0bc2dbe`。普通返回值、字段、嵌套参数、数组和原始泛型上界统一复用已有 raw Java erasure 规则；只在真实未编辑来源匹配时补齐实参，不对整张声明图填 any。Object 投影与 raw 投影共用 TS-only 内部实现，SAM 路径也复用它。原始 Comparable 上界保留 Graal 数字/字符串/布尔兼容，显式或编辑后的界限保持原约束。

共享反射 IR、Python、运行时、权限、依赖闭包和公开成员不变。三个 Java 生成断言先失败，严格消费复现十一条 TS2314；追加真实 Comparable 后又复现两条 TS2344，均已修复。改名断言和跨模块 fixture 的 paths 前提修正也保留失败日志。最终四项回归包含实际 Graal raw 值读写；普通、重命名、编辑后三模式严格正负例通过。

验证：`npm run test:probe-types` 全链通过；`:common:check guardLint` 通过，4m05s，2420 tests / 4 skipped / 零失败错误，含 isolation 与 addon 依赖面检查，guard 381 blocks / 562 files / 零警告。此前独立 Probe 检查为 413 / 2 skipped / 零失败错误。普通测试没有更新 golden。两轴 Luna xhigh 只读审查分别为 Standards 0 硬违规 / 0 smell、Spec 0 findings；主代理完成全部编辑和执行。

归档含精确源码、patch、先红后绿输出、完整 XML、三模式生成声明与消费器、检查日志及工具。离线复核：`python docs/architecture-refactor/evidence/2026-10-23-raw-generic-output/verify.py`。

本源码尚未构建五节点新安装制品或重测全图 IDE/正式性能。前置版本全图 TSC 为 5857 条失败，正式重载仍超标；不由最小严格消费通过推断它们已通过，也不代签维护者结论。Optional existential view、宿主成员读/调用冲突与注册表目录遗漏仍有后续工作。
