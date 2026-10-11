# Callable 修复审查

固定基线为 d5f4e2709feea2bdd34ec1d5ca0697fb153548e8。审查范围为三个 Python 生产文件、PythonProbeBackendIntegrationTest 的增量及 typing 包内三个真实 Java fixture。主 agent 独占编辑和构建；两个审查 agent 均只读。此处记录工作区增量审查，因为审查时源码尚未提交，不能用空的 HEAD 三点差异替代实际修改。

## Standards

硬缺陷 0，启发式判断项 0。实际公开类型为 Callable 时才分配私有 typing 名称，复用已有共享名称 owner；模块视图保留该名称，真实 Java 名称不变。基类守卫按 ApiTypeRef 种类区分回调、联合和 void，避免误删真实 Callable。新增注释、测试和 fixture 的语言及范围符合仓库规范；未增加 Any 或放宽安全设置。审查未运行构建。

## Spec

问题 0。本模块实际、未隐藏的公开类型 Callable 触发私有 typing 名称；既有预留机制避开真实 `_NekoTyping_Callable` 类，import 和 callback 引用一致，SYMBOL 和公开名称保留。回调、联合、void 基类按事实排除，真实 Callable 继承恢复；未修改 IR、兜底、collector、manual 或运行时。隐藏、改名、跨包路径未见新增问题。

总计：Standards 0，Spec 0。这份 agent 审查不等于维护者结论；维护者整体验收仍为 NOT RECORDED。完整严格检查中的未使用导入、未标注 lambda 的 Unknown 及尚未完成的总票据验收必须保留。
