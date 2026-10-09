# Java interface static surface — partial IDE acceptance

基线 `77aa54b00b3d22aa7fade97eef5ee635a2dabbf9`。共享 TypeScriptClassRenderer 补丁SHA256 `c46e4cb47997d6850dc032956141696e3fa5bc6e8dedb9fa13faf0caaeb2d03c`；新测试作为独立源码字节归档，SHA见 [manifest](manifest.json)。

## 修改与契约

实际全量声明中，Java 接口静态常量位于 TS interface 内，产生 TS1070；静态工厂错误地成为实例方法。共享 renderer 保留实例方法，把实际静态成员发射到同名导出的 const 对象类型中，常量只读。静态方法的名称、泛型和输入/输出签名保持，quoted/edited/hidden 成员仍通过既有路径；无静态成员时不生成多余值声明。overload 按当前格式化位置决定 static 修饰符，普通 class 的静态方法/overload行为保持。

脚本可继续通过实际 Java.type 返回值调用静态工厂和读取常量；实例类型不再错误宣称这些成员。这个声明契约修正没有删除 Java 公开成员、重命名运行期 API、增加虚假运行期别名、降为Any或放宽HostAccess/ClassFilter。Python、完整manual、网络、持久化和重载所有权没有改动。

## PASS

- 真实 generic Java interface 反射产物驱动同一个 strict caller：旧TSC退出2，新退出0，skipLibCheck=false。验证工厂/常量、泛型推断、真实literal overload、默认实例方法，以及错误参数、实例静态调用/常量访问、常量赋值的拒绝。
- 三个聚焦回归通过：真实反射、edited/quoted/hidden/overload、原生产HostAccess/ClassFilter下Graal调用。最初测试fixture错误地在默认Nashorn ES5上下文使用const，独立语法失败已保留；改为var只修正fixture，生产引擎设置未动。真实声明契约RED和旧TSC失败单独保留，不把这个fixture语法错误归因于生产声明修复。
- `:common:regenerateGoldens`通过，现有工作流重新生成7份legacy package文件。只迁移静态成员、将常量修饰为readonly。[AST审查](golden-audit.json)确认136个迁移成员的名称/签名保持，接口泛型/heritage和其他声明逐字保持；没有手改生成结果。
- 完整五节点矩阵 PASS：106任务/1m36s，799XML、4684测试、271跳过、零失败/错误；含common隔离门禁、五节点build和guardLint。`npm.cmd run test:probe-types` PASS。
- 五份新官方JAR实机生成TS/Python、支持的注册/放置/重载，NeoForge26.x FluidType身份和MobEffect重载前后实际施加完成；五服正常RCON关闭，直接观测exit0。1590Python stub AST全通过，1667TS声明只读解析语法0错误。legacy不可用客户端类型仍明确报告，类型闭包PARTIAL；Fabric仍为支持子集。
- [独立审查](REVIEW.md)：Standards0/Spec0发现；人工维护者对这7份golden的验收结论尚未记录，不能由代理审查代替。

## FAIL / NOT RUN

相同fixture、默认editor includes，仅skipLibCheck=false/noEmit=true的完整严格TSC仍FAIL，均exit2。所有TS1070都已归零，但仍有真实语义错误：

| 节点 | 原server/startup | 新server/startup | TS1070原→新 |
|---|---|---|---|
| 1.21.1 server | 2514 / 2515 | 2072 / 2073 | 399 → 0 |
| NeoForge26.1.2 | 3330 / 3331 | 2782 / 2783 | 497 → 0 |
| NeoForge26.2 | 2784 / 2785 | 2303 / 2304 | 448 → 0 |
| Fabric26.1.2 | 3230 / 3231 | 2710 / 2711 | 442 → 0 |
| Fabric26.2 | 2667 / 2668 | 2215 / 2216 | 392 → 0 |

[完整诊断身份对照](ide-comparison.json)忽略行号但保留文件/代码/消息。三个NeoForge节点各有2条新Codec TS2430，不能只报净减少：MapCodec类前后字节一致，MapDecoder/MapEncoder存续实例签名保持，旧类已经缺失继承接口方法，见 [单独诊断](codec-diagnosis.json)。这支持既有继承声明缺口，但完整因果分析尚未完成。只移除旧声明非法static修饰符的内存控制没有得到预期两条错误；失败控制也归档，不能宣称非法修饰符单独导致类型污染。实际生成输出在控制中未修改。

完整Pyright1.1.414仍FAIL、均exit1，79/88/87/88/88错误；五节点诊断身份相对cb5移除0/新增0。IDE捕获runner exit0只代表证据收集成功，不代表IDE通过，见 [摘要](ide-summary.json)。

当前源码正式性能NOT RUN；cb5历史重载346.8ms FAIL保留。文件系统间歇AccessDenied、实际in-flight configuration/pre-ACK disconnect、fireResistant、客户端第一帧/视觉、发布分类选择和真实维护者发布/回滚验收保持未完成。整体票据未完成，自动续跑PAUSED；本轮没有发布授权或发布动作。

## 复现与证据

```powershell
./gradlew.bat :common:test --tests '*TypeScriptInterfaceStaticSurfaceTest' --tests '*TypeScriptClassRendererTest' --tests '*TypeScriptMemberNameTest' --offline --console=plain
./gradlew.bat :common:regenerateGoldens --offline --console=plain
./gradlew.bat :common:check :common-api-processor:test :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build guardLint --offline --console=plain
npm.cmd run test:probe-types
```

[固定证据包](interface-static-evidence.zip)3543项、7419547字节，SHA256 `cddbd9800ca1bee8b8f11feb865177561faab49a14449ad1cc812f2a4ca775f6`。包含实际输出/fixture/config/日志/直接退出、RED/GREEN/TSC caller、完整XML、前后源码/golden、严格IDE诊断与失败控制。逐项hash/readback、gzip/嵌套ZIP凭据扫描通过；排除RCON属性/密码、world、账户、binaries和原始JFR。新JAR精确SHA不能复用77aa54b0、892或cb5清单；同fixture旧Probe输出参照 [固定上一证据](../ticket37-892-installed-validation/README.md)。
