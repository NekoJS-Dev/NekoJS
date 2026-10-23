# 类泛型完整约束

票37的完整严格 TypeScript 声明继续按原门禁推进，基线 `f860df0c`。本片段修复反射只保留类泛型首个上界的问题，例如 `Enum<E> & StringRepresentable`；主代理实现和验证，Luna xhigh 仅只读调查及两轴审查。

## 实现范围

- `TypeReflector.typeParametersOf` 保留所有非 Object 上界，`TypeDecl.TypeParam.bounds()` 提供当前有效约束。
- 旧的公开 `bound` 字段及构造函数继续可用。替换、清空或编辑首槽表示整约束的显式修改，旧尾界不能复活；对尾槽的独立赋值保留其他界。
- 类声明、重命名 canonical 导出和适配器输入别名共用渲染。多重约束逐项分组，防止 `A & (B | C)` 变为 `A & B | C`。
- 依赖收集及 `assign_type` 消费所有有效类上界，沿用现有声明闭包，不绕过 scan/exclude/hide。Python 继续原有泛型擦除输出。
- 不改变运行时权限、Method 来源、wire、持久数据、加载器支持或注册行为。不新增泛型猜测或 any 回退。

## 验证与交接

真实功能红测已分别保留：遗漏尾界导致 Java 依赖断言和严格 TS 负例失败；别名尚未迁移时严格 TS 三条诊断；尾界 assign 未迁移时 Java/TS 失败；union 未分组时 Java 精确断言失败及两个 TS2578。最初错误使用不存在的 catalog.empty() 是夹具编译错误，单独记录，不充当功能红测。

当前定向21项Java测试通过，包括实际Graal调用。`npm run test:probe-types` 完整消费链通过，包含 plain、aliases、edited、unflagged、replaced、cleared、assigned、union 八种新模式。两轴最终审查：Standards 0硬违反/0剩余气味，Spec 0；对未置 overridden 的 ref 编辑疑问经实际输出及 JavaTypeScriptProjection 调用核对后撤销。

完整 common/check、处理器、guard 与五节点构建及 platform/artifact 检查通过：5146 tests / 271 skipped / 零失败错误，5m44s，106 tasks / 41 executed；guard 383 blocks / 567 files / 0 warnings。命令：`gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home="C:/Program Files/Java/jdk-25.0.2" :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --offline --no-parallel --console=plain`。后续[同一源码新JAR验收](../architecture-refactor/evidence/2026-10-23-class-bounds/README.md)已完成：五节点运行及正常退出0，1609默认stub通过；完整严格TSC为5565，移除10/新增0，仍FAIL。不能用定向通过称为全图声明通过。

下一片段仍需处理已存在的方法级约束编辑/交叉类型分组路径；本片段只解决类级约束。完整捕获类型、成员冲突、Mixin擦除泛型、正式重载性能及最终统一候选客户端/TCP/分离回退仍按各自范围开放。没有PR、push、正式发布或人工验收代签。
