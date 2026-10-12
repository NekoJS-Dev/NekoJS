# Manual BlockBuilder.item 声明修复

保留完整 manual 生产器，将同名 `item` 属性和方法合为一个可调用、可访问 ItemBuilder 属性且允许 null 的属性。类型直接取自既有 RegistryBuilderContract；运行时、HostAccess/ClassFilter、公开方法和其他节点支持范围不变。`block.item` 非 null 时可配置属性或调用 callback，置空后不能调用。

源码基点 `ab59f914f6688c9fe5ff4e38d6e45c12b2e99b9a`，生产补丁 SHA256 `2aca509bbeee74c2b0cd75df994456038a666a7a0d44fbb57208ab748918e354`。包内保存精确源码、测试和 diff；本页提交后以此内容核验制品，不能使用旧 d109 JAR 清单。

实际生产器捕获的 manual 文件完成 RED→GREEN：RED Java 回归退出1，严格 caller 退出2，出现两个重复 `item`、不可调用及回调隐式 any；GREEN 同一合法 caller 退出0，三个非法 callback/属性/赋值仍各被拒绝。caller 只为 EntityTypeBuilder 放置明确的空依赖叶，不能称完整默认 IDE 通过。RED Java XML 未在 GREEN 前冻结；原始失败日志、捕获声明和 TSC 结果保留，未补造 XML。

新增受限实际脚本面回归：生产 NekoSharedHostAccess、禁止 host class lookup，验证 nested callback、属性写入、显式 setter、null 写入和 null 后调用失败。三个 NeoForge 节点检查实际 manual 输出，五节点检查运行时行为；Fabric 的历史 manual 子集问题仍开放。

完整矩阵 PASS：4806 tests、271 skipped、0 failures/errors、810 XML；109 tasks，54s。命令如下，日志和全部 XML 位于包内：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
python tools/nekojs-ci-gates.py all --out build/ticket37-manual-item-repair/platform-gates.json
```

五个新官方 JAR 在新专属 world 实机生成 Probe，注册、放置、重载、NeoForge fluid identity、26.1.2 effect 重载前后实际施加 PASS；1600 份 Python stub 全部 AST PASS。五服正常 RCON stop、直接退出0，十个端口可绑定。完整实际制品 SHA 和与当前编译类逐项相等清单见 [结果](runtime-summary.json)。没有手改生成声明、提交密码/world/raw JFR 或停止用户进程。

| 节点 | 严格 TS server/startup | Pyright errors | 结果 |
|---|---:|---:|---|
| NeoForge 1.21.1 | 753 / 754 | 7 | FAIL |
| NeoForge 26.1.2 | 927 / 928 | 7 | FAIL |
| NeoForge 26.2.0 | 812 / 813 | 7 | FAIL |
| Fabric 26.1.2 | 1290 / 1291 | 13 | FAIL |
| Fabric 26.2.0 | 1030 / 1031 | 13 | FAIL |

保留默认 editor includes，仅严格验收改 skipLibCheck=false/noEmit=true。与 [d109 实机](../ticket37-current-d109-ide/README.md)比较 file/code/首行消息的多重集，忽略位置/安装根，三个 NeoForge 项目每侧只移除两次同一 `item` TS2300，零新增；Fabric 零增减。未比较多行说明，未宣称其他错误由此次缺陷造成。TSC 直接退出2、Pyright1，外层收集器0只表示证据收集完成。

自审结论：改动仅限 canonical manual 类型及两个行为回归；复用共同契约、保留 null/调用和安全边界，不新增依赖或运行时成本，不修改 golden，不放宽 gate。没有声称独立审查或真实维护者结论。源码 SHA、源码/测试 patch、测试及实际 IDE 输出一并可复核。

[证据 ZIP](manual-item-evidence.zip)：4286 entries、7808262 bytes，SHA256 `996eacfc663e8e29e9b0fc06d30bc8e2d6cd50a4d11a6ac9f07bc3fb8972f84c`。[逐项清单](manifest.json)、[独立验证器](verify.py)检查完整 hash、矩阵 XML、RED/GREEN caller、受限运行时回归、实际退出和剩余失败。

新补丁正式性能 NOT RUN；[d109 正式性能 PASS](../ticket37-current-d109-performance/README.md)属于旧精确 revision，不能继承为本补丁验收。完整 IDE、Python 同名 field/method、其他多人/下载/首像素/客户端节点/领域/磁盘及真实维护者和发布回滚验收仍未完成。自动续跑暂停，工具权限字段问题未解决；A22/A28 和发布授权边界不变，整体 NOT COMPLETE。

后续精确提交63d538dc的[正式性能](../ticket37-manual-item-performance/README.md)已执行：重载372.2/366.8ms FAIL285.3，启动11941.2ms PASS41173。上面的 NOT RUN 和冻结结果描述本修复归档时状态；新失败单独保留，不改写历史 ZIP，不继承旧PASS。
