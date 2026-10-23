# 同名事件类型的模块局部导入

主代理继续按`implement`与已批准的公开backend/严格TypeScript seam实现；Luna xhigh仅只读调查。来源是[TS2300分类](2026-10-24-ts2300-followup.md)：两个Fabric节点分别从item和player包导入`PlayerEntityInteractEventJS`，局部名重复。范围仅限生成事件模块；Java类型身份、Script Event目录顺序、运行时和公开方法均保留。

EventDeclarationGenerator复用TypeScriptImportNames，按FQN与导出名分配局部名，预留显式builder payload接口名；import、事件回调类型与dispatch输入统一使用同一TypeAliasRegistry模块视图。别名不写回共享registry。非冲突输出保留原名称，重排事件目录不改变import映射。只导入当前实际事件与可派发key，继续沿用exclude/hide及显式payload替代边界。

## 本轮验证

- 最小回归先红：公开TypeScriptProbeBackend生成跨包同名payload、dispatch class和不同literal输入适配器；Java断言失败，严格TSC实际复现6个重复标识符及错误payload、key/array输入和负例失效。原始日志位于`build/priority-closeout/event-import-red.log`与`event-import-ts-red.log`。
- 修复后该Java用例及严格TSC通过。覆盖两种回调成员、两种输入适配器、数组dispatch、输入重排与复用同一backend后的无冲突输出。
- 增加显式builder payload名与真实类导出名碰撞的消费回归，验证只读属性、隐藏host成员和自定义事件`post`行为；两个Java用例、两份`skipLibCheck:false`的TS正负例均通过。
- 已运行相关LegacyProbeCompatibility、ProbeEventsSurfaceGolden、DynamicRegistryEventsDeclarationGolden、DynamicRegistryDefaultDeclarations、TypeScriptDeclarationClosure回归，全部通过；未重写golden。
- 已接入`npm run test:probe-event-imports`。完整suite在本轮后续共享原因修复完成后统一运行；未把旧5152项矩阵、五节点安装、完整5549条诊断或性能结果标为本次源码结果。

此项解决局部import身份冲突，不解决96个真实字段/方法成员的TS可表达性。未删除成员、改变Graal行为、放宽严格门禁，未推送、发PR或正式发布，未代签人工结论。
