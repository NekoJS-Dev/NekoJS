# 连续 wildcard 捕获合同

产品 `15c4d9a6` 接续完整视图 `8f71ca31`，保留父方法已经继承的祖先捕获 facet。前驱安装的 1037 条错误继续冻结在 [前驱证据](../2026-10-27-complete-capture-views/README.md)，不覆盖其失败结果。

## 问题与修复

现代 RegistryAccess 的 lookup 返回具体 Optional；直接父接口 lookup 返回较窄 wildcard，祖父接口返回较宽 wildcard。先前收集器遇到直接父方法的 wildcard 后停止，结果只附加一层合同，无法实现父接口已经拥有的两个 facet。完整视图恢复 reduce 等方法后，这个遗漏在四个现代节点的 server/startup 共暴露 8 条 TS2430。

现在添加父方法的上界后，继续遍历当前可见、编辑后的祖先 IR；保留既有隐藏、改名、槽覆写、签名匹配、接收者泛型上下文和去重规则。不重建反射声明，不删除成员或改工厂签名，不更改 Java/Python IR、运行时或权限。

新的公开 backend 夹具包含 Parent、返回更窄 wildcard 的 WildcardMiddle、具体 Optional 实现；验证直接窄读取、向两级父接口赋值、宽 fallback 及错误载荷。旧实现的严格消费实际失败，修复后同一消费者 exit 0。

## 验证范围

定向 `ProbeCaptureReturnContractTest`、`ProbeCompleteCaptureViewTest` 和严格继承返回消费者通过。完整 common/check、处理器、guard 与五节点矩阵通过（7m55s，921 suites / 5200 tests / 271 skipped / 零失败或错误）；完整 npm 严格消费链及四项 CI gate 均通过。无需追加 golden 变化，完整视图前驱的两个基线文件保持。

五节点新安装全部正常 RCON 停止、exit 0、未 kill 且世界锁释放；8 个源码族的 25 个 class 与五份安装 JAR 逐字节匹配。十入口为 **1029，完整严格 TS 仍 FAIL**：对 3d2cc1cb 的完整诊断块移除 0 / 新增 0，所有族与逐入口计数相同，零 UNMAPPED；前驱新增的 8 条已全部消失，F1/F2/F4 均为 0。

| 节点 | server | startup |
|---|---:|---:|
| 1.21.1 | 64 | 65 |
| 26.1.2 | 116 | 117 |
| 26.2.0 | 110 | 111 |
| 26.1.2-fabric | 114 | 115 |
| 26.2.0-fabric | 108 | 109 |

两个阶段均冻结全部默认声明和脚本配置，前驱的十入口完整错误链已独立重现 1037；本阶段七组严格消费者全部 exit 0，十入口完整错误链精确重现 1029，实际结果见 replay.log。基线诊断直接取自已提交的 3d2cc1cb 冻结归档并校验原摘要，比较器保留完整错误链，仅忽略安装目录与源码行号变化。

执行命令包括 `:common:check :common-api-processor:test guardLint` 及五节点 `build`，同一 Gradle 中并行上限 2，所有 Test 任务使用独立临时目录；随后执行 `npm run test:probe-types`。安装使用现有 `bench/tsbaseline/run.py prepare/run/check`，源码参数为完整 `15c4d9a6e1ba202e6ac99b1eb088bf5483d09865`，端口从 34800 起，沿用固定平台/JDK/外部依赖；只测功能，不作为性能样本。

显式 any、完整伪 host 的结构身份及既有 JS 数组桥异常仍不在本修复范围。正式候选性能、Python 默认安装、客户端/TCP/分离回退和真实维护者结论分别验收；整票保持 in-progress。

## 独立重放

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-27-transitive-capture-facets/verify.py --typecheck --installed-typecheck
```

归档绑定源码提交、编译类、安装 JAR 摘要、原始日志及完整默认声明。重放只重定位顶层 tsconfig 的 extends，声明和生成的脚本配置逐字节保留；严格 TS 仍有已知失败时，必须精确重现完整诊断块，不能将重放成功解释为完整严格 TS 通过。
