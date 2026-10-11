# Node util 类型谓词修复验收

源码提交 `60139fffffa6e54b2248797c2a32d4fd75025a54`，基线 `10a586796a2175d12fb4516264cdbf0b2c0dd808`。生产资源仅修改 `isProxy` 的返回谓词引用：参数实际名为 `_value`，此前错误地引用不存在的 `value`。生成器、参数名、公开成员、既有返回 `false` 的行为和安全配置保持。没有手改生成输出，也没有弱化声明检查。

## 修复与行为边界

真实 canonical 资源经 `NodeModuleTypeDocs` 提取后，严格合法 caller 原有四条错误变为零；普通 `util` 和 `node:util` 均能提供既有的对象类型收窄。非法字符串赋值仍被 TS2322 拒绝。首次回归测试真实 RED，修复后两个定向测试 PASS。

实际模块安装 fixture 验证 null、数字、对象及 Proxy，在两个导入别名下均返回 false。fixture 原有 `allowAllAccess` 未改；这一结果是内置模块行为证明，不是生产沙箱验收。完整擦除 JS 原始字节从 10,736 增为 10,737，不能称为字节相同。只读完整 JS AST 对比通过，改变返回值为 true 的负面对照被识别；该比较忽略位置及 trivia。

## 验证记录

- `:common:check :common-api-processor:test guardLint`、三个 NeoForge `nbtSmokeTest` 及五个节点 `build`：首次直接退出 1，4,744 测试中两项失败；完整日志及 801 份 XML 保留。`PackSyncTrustStoreTest.corruptFileDegradesToEmptyStore` 写入后读回为 false；`ServerPackCacheTest.physicalRestorePropagatesDeleteFailureAndRetainsStaging` 在实际目录移动处 AccessDenied，尚未到注入的删除失败断言。
- 源码及测试未改的 11 项文件系统定向复测直接退出 0；随后完整矩阵确认直接退出 0，4,744 测试、271 跳过、零失败，801 XML，109 任务，1m45s。确认 XML ZIP SHA256 `f772b44848f30d878795cc46d6921339e13ef9a1fcc1837a0e894290b061d12a`。首次失败没有被覆盖；文件系统根因仍 UNRESOLVED，不能根据复测通过宣布修复。
- `npm run test:probe-types` 和构建完成后的 `tools/nekojs-ci-gates.py all` 直接退出 0。Standards 0 hard/0 heuristic，Spec 0 findings，均为只读审查。
- 五个精确新 JAR 安装到独立新 world；检查资源字节与当前源文件一致并记录各 JAR SHA256。显式调用全部五节点证明生成、注册、放置及重载；NeoForge fluid identity 和 26.x MobEffect 重载前后验证通过。1,600 Python stub AST、1,675 TS 声明语法检查通过。五个测试服正常 RCON 关闭、直接退出 0，十个独占端口释放。1.21.1 八项历史反射缺失仍为 PARTIAL。
- 默认 editor includes 保持，仅验收设 `skipLibCheck=false`。各十个项目恰好移除两条 TS1225，新增诊断身份零；其余 TS 与所有 Python 诊断身份不变，生成文件路径集合完整。只读 scoped AST 检查保留同名 overload 和 class body 顺序；Python import 按真实限定目标核对。原始排序及部分本地 import alias 可变化，未宣称本地名称确定性或全部输出字节稳定。

| 节点 | 严格 TS server/startup | 全部 Python stub Pyright |
| --- | --- | --- |
| 1.21.1 | 762 / 763 FAIL | 7 FAIL |
| 26.1.2 | 936 / 937 FAIL | 7 FAIL |
| 26.2.0 | 821 / 822 FAIL | 7 FAIL |
| 26.1.2-fabric | 1300 / 1301 FAIL | 13 FAIL |
| 26.2.0-fabric | 1040 / 1041 FAIL | 13 FAIL |

## 制品与剩余项

[manifest.json](manifest.json) 绑定生产 patch SHA256 `f2770070ddf33e67c5fef9460add18ac122cb1a29da2e471560bf70859803e38`、源码、五份 JAR、完整测试和实机输出。[node-util-predicate.zip](node-util-predicate.zip) 含 3,534 项、8,271,876 bytes，SHA256 `2d74e2bca40e8b8db5bc39adc8daffe7b9938e94b29bcf9f7037b8a17b6d9a9c`。完整 entry hash、解压读回、源码提交/patch、五制品资源字节、已完成比较及首轮失败保留均通过独立 [验证](archive-verification.json)。打包时递归检查实际 RCON 密码，排除 properties、world、原始 JFR、二进制 JAR 和无关文件；打包自己的活动输出不进 ZIP，完成控制台另存。

此声明包生成时新源码正式性能尚未运行；随后独立执行的结果见 [精确 60139fff 性能](../ticket37-util-performance/README.md)，不得套用旧 f84 的分数。完整 IDE、Python 同名 field/method、名称确定性、文件系统根因、native crash、fireResistant、其他领域、多人的独立下载窗口、首帧像素/跨节点视觉、发布/回滚及维护者结论仍有缺口。维护者 NOT RECORDED；票据总体 NOT COMPLETE，自动续跑 PAUSED，工具权限字段问题 UNRESOLVED。A22/A28 原有精确范围没有扩大，无发布授权。
