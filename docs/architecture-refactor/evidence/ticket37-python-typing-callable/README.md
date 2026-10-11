# Python Callable 类型身份修复：局部通过，整体未通过

本轮修复真实 Java `Callable` 与 Python `typing.Callable` 在同一模块中重名的问题。公开 Java 类型、方法及 SYMBOL 引用保留；仅 typing 的导入和回调引用采用一致的私有名称。已有名称分配机制避开真实类、导入及输入别名，例如实际 `_NekoTyping_Callable` 类会使辅助名称加后缀。基类守卫根据共享 IR 的种类排除回调、联合和 void，恢复此前因名称前缀误删的真实 Callable 继承。

固定源码基线 `d5f4e2709feea2bdd34ec1d5ca0697fb153548e8`，三个生产文件的补丁 SHA256 为 `07ecf23cc86853ee6ef4ef0c1020fb62c4f0028bf45e848ad0cde8a9fab56486`。新 JAR 与这份补丁及逐文件 SHA 绑定；旧制品清单不能认证本轮修改。未更改公共名称、IR、collector、manual、golden、运行时、安全设置或已有 Any 兜底，也未提升原有回调参数精度。

## 回归与严格调用

实际反射 JDK Callable、真实同包接口及实现类，经过完整 backend.generate 留下全部输出。原两项回归稳定 RED；仅改变 typing 导入和引用后，JDK 测试通过而真实继承仍失败。新增有效回调/联合基类边界后，三个测试中两个断言失败；按类型事实修复守卫后通过。已有 HostAccess/ClassFilter 下，真实 Java 方法接受并执行脚本回调。

新增测试曾写错 TypeSlot 构造器、ApiSymbolId 工厂、单成员联合输入和 Nashorn 脚本语法，完整失败日志保留；编译失败时测试为 NOT RUN。这些属于测试编写失败，不能计作产品 RED。修正为已有 API、有效两成员联合和函数表达式后，取得真正的基类断言 RED。

| 检查 | 修复前 | 修复后 | 结论 |
|---|---:|---:|---|
| JDK 全部 5 份 stub，basic |1|0|PASS|
| 同包全部 10 份 stub，basic |2|0|PASS|
| JDK 全部 stub，strict |4|3|FAIL：未使用导入保留|
| 同包全部 stub，strict |4|2|FAIL：未使用导入保留|
| 原未标注 lambda 严格调用 |6|3|FAIL：Unknown 精度限制保留|
| 同一明确参数类型的回调严格调用 |4|0|PASS|
| 错误参数 `apply(17)` |1|1|正确拒绝，exit1|

明确参数类型的调用使用冻结的 RED/GREEN 输出比较；没有手改生成文件、加入 Any 或放宽检查。局部调用通过不等于所有 stub 严格检查通过。

## 普通矩阵与实机

聚焦 backend/renderer：43 项测试、零失败。完整五节点矩阵直接 exit0，4,739 项测试、271 跳过、800 份 XML、零失败/错误；109 个任务，42 执行、67 已是最新，2m22s。XML 包 SHA256 `da594875864bc0ed2c0cd37158aca453b75a87e5123f732cf898f035b7d7abe0`。独立审查 Standards 0、Spec 0，见 [审查记录](REVIEW.md)，并非维护者验收。

复现命令：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
python tools/nekojs-ci-gates.py all --out build/ticket37-python-typing-callable-platform-gates.json
```

新官方 JAR 装入五个全新专属 world 环境，重新生成 Probe；支持的实际注册、放置及重载通过，NeoForge 流体身份及 26.x 效果重载前后通过。Fabric 仅验证支持子集。旧版保留 8 个不可用类型的显式遗漏，结论 PARTIAL。1,600 份 Python stub 全部 AST 通过；1,675 份 TS 声明孤立解析零语法错误。五服均经正常 RCON 关闭，直接观察 exit0，十个专属端口释放；未强停用户进程。

完整 IDE 保留真实默认 editor includes；严格 TSC 仅设置 skipLibCheck=false/noEmit，不缩减依赖范围。Pyright 扩展生成配置并明确枚举全部 stub，核对实际分析数。

| 节点 | Python 分析数 | Python 错误 | TS server/startup 错误 |
|---|---:|---:|---:|
|1.21.1|319|7|768/769|
|26.1.2|378|7|942/943|
|26.2.0|346|7|827/828|
|26.1.2-fabric|295|13|1306/1307|
|26.2.0-fabric|262|13|1046/1047|

以上所有完整 IDE 检查仍为 FAIL；采集器 exit0 只表示材料收集完成。剩余 Python 错误是实际字段与同名方法重复声明，需实测宿主成员访问后继续处理；不能删实际成员使检查变绿。

## 归档与剩余工作

原始源码、失败控制、生成输出、编译诊断、测试 XML、实机日志、制品 SHA 及十组历史诊断身份比较收录在 [证据包](python-typing-callable-candidate.zip)，逐项索引见 [manifest](manifest.json)。共 3,706 项、8,144,540 字节，SHA256 `6c2314ef59d38d9cdc989bd67c77ad86528c69463c33e3387d36989a24f650d7`。逐项 hash/readback、完成日志字节一致性、十组最终比较完整性、精确源码及实际密码的嵌套扫描通过，见 [验证记录](archive-verification.json)。打包器自身运行中的控制台未装入 ZIP，完成后单独保留；未提交密码、world 或原始 JFR。

与上一实机检查 d5f4 比较：五节点各移除一条既有 typing 身份诊断，共 5 条，新增 0；全部 TS 诊断身份不变，没有生成文件路径被删除，旧版遗漏身份不变。源码提交及安装构建一致性见 [交付记录](delivery.json)；远端状态需由真实推送及远端 SHA 单独核验。

总票据 15/16/22/28/34/37 尚未完成，38 可选。完整 TS/Python、精确当前源码性能、持久化移动间歇失败、native 崩溃、fireResistant 和剩余领域、资源下载窗口、跨节点第一帧/视觉、发布/回滚及真实维护者结论仍有缺口。此前已通过的多人 ACK/abort/catch-up 窗口不认证所有窗口。维护者结论 NOT RECORDED，自动续跑 PAUSED；未发布，也未扩大 A22/A28 的删除范围。工具权限字段入口问题未解决。
