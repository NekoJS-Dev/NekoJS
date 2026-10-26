# 完整捕获视图恢复与继续目标

## 范围

从 `2004f149` 的继承返回合同继续，实现 Optional、Stream 及其 List/Collection 导航的完整 TypeScript 捕获视图。视图继承当前可见、编辑后的原始类型，补充捕获导航与输入域合同；不再以删除公开方法或重载获得兼容。不修改 Java/Python IR、运行时转换、权限或工厂签名。

## 意外清理与恢复

原工作区的未提交产品改动被外部清理移走；用户明确回复「意外清理，恢复并继续」。从保留的补丁和两个新增文件恢复到本任务的隔离工作树，以免覆盖原目录的其他工作。原目录 `lite/` 及既有未跟踪文件不属于本次交付，不删除或提交。

恢复后已补充公开 backend 到严格 tsc 的控制：同上界覆盖、较宽 flatMap、原生工厂推断、Collector、重命名 SAM 的 host/对象输入、隐藏/排除、手写 Optional/Stream、公开及私有 helper 名冲突、同一 backend 重用。保留错误元素域和错误回调结果的负例。

## 验证记录与边界

- 恢复后定向生产生成及五组完整捕获消费者通过。重命名规则曾导致两个错误域负例未报错；已取得红证并修复，仅改名仍参与投影，覆盖类型或手写重载继续由原始声明拥有。
- 首次完整 common 检查失败：两项预期 golden 差异，另有 PackSyncClientTest 在首次安装旧包时失败。完整失败 XML 已保留，不能将该轮写成通过。
- 给既有安装断言增加实际失败原因后，该单项及整个 PackSyncClientTest 均通过；尚未查明首次失败根因，没有据此修改资源同步产品代码。
- 已通过官方 `:common:regenerateGoldens --tests '*LegacyProbeTreeTest'` 再生。独立逐字节检查仅 java.util 和 java.util.stream 两文件变化；原始类型与 SAM 别名前缀除导入外不变，其他资源文件字节保持。
- 再生后的普通 `:common:check guardLint` 通过（3m39s，339 suites / 2483 tests / 4 skipped / 零失败或错误）；完整 `npm run test:probe-types` 链 exit 0，含五组新增完整捕获消费者。均未使用 regenerate 开关；之前失败原文继续保留。

## 持续目标

1. 完成本阶段普通门禁、平台矩阵、差异复审和源码绑定证据，本地提交后安全合回 stonecutter。
2. 用确切新制品重新执行五节点隔离安装、十入口严格诊断和逐族增减报告。历史 1029 只绑定 `3d2cc1cb`，不得冒充本阶段安装结果。
3. 继续其余严格 TS 根因；最终候选性能、Python 默认安装、客户端/TCP/分离回退及维护者结论分别验收。

显式 any 及完整伪 host 的结构身份限制仍开放。此前 JS 数组的泛型桥 ClassCastException 仍未解决；真实 Java 数组正例不代表该问题已修复。不 push、不发 PR、不发布、不代签人工接受。
