# TypeScript member-name syntax repair — partial acceptance

基线 `8a8aa2d7` 加共享 TypeScriptClassRenderer 补丁，SHA256 `460dc1b5470d37f6818422b140d7f35e442122c1163efb009d22b119e5ed296c`。五节点官方新JAR由这个WIP源码构建；精确制品SHA和源文件字节见 [manifest](manifest.json)。

## 改动与契约

真实 Fabric Mixin 方法 `handler$zek000$fabric-events-interaction-v0$fakePlayerGameMode` 含连字符，旧生成器把它直接写成TS方法名，导致声明无法解析。有效标识符仍逐字输出；无效成员名通过既有Gson依赖转义为字符串字面量，保留真实名字和 bracket 调用形态。普通/静态方法、手写overload、原getter降级入口、字段、枚举常量与接口方法共用同一私有规则。

只改声明语法，没有重命名或删除运行期成员、伪造别名、降为Any、改变非法Bean属性的既有降级策略或扩展公开接口。共享IR、反射/导入闭包、Python生产器、完整manual、HostAccess/ClassFilter、持久化、网络和资源/重载所有权未改。无golden再生成或基线变更。

## PASS

- 真实反射/Remap输入和现有编辑IR路径先运行RED：两个新测试失败，实际生成声明和TSC解析错误保留。修复后普通/静态方法、overload、getter、特殊转义及field/enum/interface边界GREEN；既有TypeScriptClassRenderer和NoopIrGolden聚焦检查通过。
- 同一严格caller/config对旧/新生成声明分别退出2/0，参数及返回类型仍检查；未手改生成正文。首个GREEN的synthetic fixture未指定类型名，错误地期待Edited而实际按既有逻辑得到Unknown；明确fixture renameTo后通过，该失败未丢弃，生产类型命名逻辑未改。
- 普通完整矩阵 `:common:check :common-api-processor:test`、五节点build、guardLint PASS，106任务/3m44s；792XML、4597测试、271跳过、0失败/错误，直接退出0。包含common隔离检查；无测试排除或golden更新。`npm run test:probe-types` PASS。
- 五节点精确新JAR实际生成TS/Python、支持的注册检查、放置与重载完成；NeoForge26.1.2/26.2另验证FluidType身份和重载前后MobEffect，Fabric验证7项支持子集。1590Python stub AST全部通过（317/377/344/293/259）。五服正常RCON关闭，退出码直接观测为0。
- 全部实际声明只读解析：legacy334、NF26.1.2 391、NF26.2 360、Fabric26.1.2 307、Fabric26.2 275份声明。旧Fabric673/630语法诊断→0，新五节点语法诊断均0；其余三节点原本就是0。
- 归档3544项、7232849字节，SHA256 `7ca1d262f28719a70473327d9e8454e95e42e836f274b74e2a213a4a90546ed3`；逐项hash/readback、gzip/嵌套ZIP凭据扫描通过。

## FAIL / PARTIAL / NOT RUN

全量strictTSC仍全部FAIL，使用实际默认editor includes，仅设置skipLibCheck=false/noEmit=true；全部退出2。Fabric语法修复后显现既有语义问题，不能把总诊断增加当作新增问题，也不能把语法通过称为IDE通过。

| 节点 | 严格TSC server/startup | 全量Pyright错误 / exit |
|---|---|---|
| legacy1.21.1 server |2785 / 2786|79 / 1|
| NeoForge26.1.2 |3552 / 3553|88 / 1|
| NeoForge26.2 |2927 / 2928|87 / 1|
| Fabric26.1.2 |3506 / 3507|88 / 1|
| Fabric26.2 |2862 / 2863|88 / 1|

Pyright1.1.414枚举全部实际stub，仍FAIL。legacy与上一Python修复的诊断身份完全相同，移除0/新增0；不把上一修复的129→79归功于本次TS变更。legacy Probe仍明确报告8类不可用类型，闭包PARTIAL，不能声称完整host类型覆盖。

旧源码目录移动100次复现2失败，纯Files.move嵌套输入亦复现；[原始诊断](../ticket37-python-member-collisions/FILESYSTEM-DIAGNOSIS.md)保留。当前普通矩阵PASS没有修复或否定此间歇失败，生产文件系统代码未改。责任代理[手工审查](REVIEW.md)与独立审查分开；服务用量限制之后没有新的独立审查结论，独立审查NOT COMPLETED。当前补丁正式性能NOT RUN，精确085历史reload FAIL保持。第一帧/视觉、实际in-flight configuration/pre-ACK disconnect、fireResistant、发布分类选择与真实维护者发布/回滚结论仍未完成；总票据未完成，自动续跑PAUSED，用户恢复HTTPS登录前不推送。

## 复现和原始文件

```powershell
./gradlew.bat :common:test --tests '*TypeScriptMemberNameTest' --tests '*TypeScriptClassRendererTest' --tests '*TypeScriptNoopIrGoldenTest' --offline --console=plain
./gradlew.bat :common:check :common-api-processor:test :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build guardLint --offline --console=plain
npm run test:probe-types
```

[固定原始证据包](ts-member-evidence.zip)含RED/首个GREEN失败/确认GREEN、严格caller、完整XML/矩阵日志、全部实际输出/fixture/configs、五服日志/退出、IDE命令/原始诊断与脚本副本。排除服务器属性/RCON凭据、world、账户、二进制与原始JFR。摘要分别见 [矩阵](ts-member-final-test-summary.json)、[IDE](ts-member-ide-summary.json)、[旧解析](ts-member-syntax-before.json)、[新解析](ts-member-syntax-after.json)。

后续精确提交cb5f7fe6的[正式性能窗口](../ticket37-cb5-performance/README.md)已运行：startup34231.8ms PASS，第二重载346.8ms FAIL，首组275.4ms但exit未观测；性能仍NOT GREEN。上面的NOT RUN是构建/安装归档时状态，不能替代后续结果。
