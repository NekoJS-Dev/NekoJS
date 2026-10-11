# Python 同名字段与方法：事实及未通过的模型

这是诊断证据，**没有生产修复，完整 IDE 仍 FAIL**。源码基线 `0a212fbb26d22d7337c25d1e7e1ebe84bd0392c2`；新增两个测试，不删除字段或方法、不添加 Any、不改 HostAccess/ClassFilter、运行时或验收门槛。精确生产源码 `2a421c18` 的[启动及重载性能 PASS](../ticket37-2a421-performance/README.md)单独保留。

## 实际访问事实

通过现有生产 HostAccess/ClassFilter 运行 Java 宿主对象：`locked` 字段读 false、同名方法返回 true；`x` 字段读 3、同名方法返回 4。写入 `locked=true` 后，读值 true、调用返回 false。**保存 `x` 到普通变量再调用会抛 TypeError**，因此不能把读出的数值声明成可调用对象来掩盖问题。本测试执行 GraalJS 宿主访问，不认证 Python 运行时。

实际反射保留两个字段和两个实例方法；完整 Python backend 生成八份 stub，生成前后共享事实数量保持不变。生成文件全部 AST 通过；这不代表 Pyright 通过。

## 编译 RED 与模型控制

未经修改的完整生成输出：basic 检查 2 错误（同名重复声明），strict 检查 6 错误（另含 4 条未使用导入）；合法读值、直接调用及写入的 strict caller 有 7 错误。错误参数和赋值 caller 有 3 错误。四次编译实际 exit1；采集器 exit0 只表示成功保留并确认 RED。

在确认 RED 后，按顺序检查重复声明覆盖、Python 属性/调用与宿主 readMember/invokeMember 差异、反射静态标记错误三个解释。反射事实削弱第三个解释。以下独立 toy 模型没有改生成输出：

| 模型 | 合法 caller 错误 | 保存数值后调用的错误 | 结论 |
| --- | ---: | ---: | --- |
| 只声明方法 | 3 | 0 | 丢失字段读写并错误允许保存数值后调用 |
| 只声明字段 | 4 | 2 | 拒绝保存数值后调用，但丢失真实直接方法调用 |
| 可调用值及属性 setter | 1 | 0 | bool 类型不匹配，且错误允许保存数值后调用 |

六次 toy 检查还各有一条 `reportMissingModuleSource` 警告，均保留。0 错误不认证整个模型通过。以上只排除这三个具体候选，**不证明所有 Python 类型建模方案都不可能**。下一步需保持真实字段读写、直接调用及保存值后的拒绝行为；不能通过删除成员或放宽检查得到 GREEN。

## 验证及审查

- `:common:test --tests '*PythonFieldMethodCollisionTest'`：实际宿主测试 PASS。
- 两个实际宿主/backend 测试：2 测试、0 失败。
- `:common:check --console=plain`：实际 exit0；2212 测试、4 跳过、291 份 XML、0 失败，19 任务、1m40s。
- 新增测试的五节点矩阵 **NOT RUN**；此前生产源码的 4739 测试矩阵是独立证据，不能冒充本次全矩阵。
- [两轴审查](REVIEW.md)：Standards 0 硬问题/0 启发式问题，Spec 0 问题；不代表生产缺陷已修复或维护者验收。

原始生成输出、JSON、测试 XML 压缩快照、完成的控制日志、执行脚本及测试补丁见 [field-method-diagnosis.zip](field-method-diagnosis.zip)，逐项 SHA 及退出结果见 [manifest.json](manifest.json)。73 条目、284476 字节；SHA256 `35f2a535884cb3d106abe94baa4953ceaf39ca19740fa0364add2ff1df7607fe`。[独立完整性校验](archive-verification.json) PASS，完整条目、当前测试补丁、未改生产源码、原始 RED 及六组模型警告均匹配；[完成的归档日志](archive-console.txt)单独保留，未将正在写入的日志打包。

剩余缺口：真实 JAR 的 Python 重复声明及完整 TypeScript 仍 FAIL；其他多人/视觉、领域、发布/回滚和真实维护者结论仍未完成。自动续跑 PAUSED；没有发布或新增公开删除授权。
