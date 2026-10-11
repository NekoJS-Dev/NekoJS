# constructor 普通成员修复验收

源码提交 `7b724a50f51367ad43f5903b1e7240e8e69bad10`，基线 `077e769a1d092ecc3e94e7b5d43aa59fa80bbcb9`。延续 [真实诊断及失败模型](../ticket37-ts-constructor-member-diagnosis/README.md)，共享 `TypeScriptClassRenderer` 将普通 `constructor` 成员输出为 `["constructor"]`，Bean getter/setter 及覆盖路径复用同一命名规则；真构造函数继续走独立发射路径。保留实际名称、签名、字段、接口常量、枚举、手写 overload、hidden/rename 和 Bean 权限；其他名字输出保持。没有公开删除、any 扩张、manual 生产器删除、安全设置或黄金基线变更。

## 反馈与验证

真实 TypeReflector→共享 renderer 先 RED：三个新测试中两个失败，生产 HostAccess/ClassFilter 下真实成员访问先通过。首次 fixture 的接口同时有同名 field/method；保留该源码/输出后拆成两个接口，以单独验证上下文命名而不夹带另一项已知建模问题。第二次仍两个 RED、运行时通过。修复后 29 项定向测试 PASS，含旧 renderer/static-interface 回归。

实际反射输出的严格合法 caller RED 存在 constructor field/enum 的 TS1005；修复后零错误，六种非法调用/赋值均拒绝（类型、参数数量、readonly、枚举类型）。初版 collector 将含 overload 细节的多行输出数误当错误数，assert 失败；完整输出保留，修正后按六个 `error TS` 诊断头计数，compiler 的严格配置和退出值未改。真实构造函数、static/instance factory、Bean 读写、field、interface method/constant、enum 访问均在生产 HostAccess/ClassFilter、禁止 createProcess 的实际 Context 下通过；Context 正常关闭。

完整原命令 `:common:check :common-api-processor:test guardLint`、三个 NeoForge `nbtSmokeTest`、全部五节点 `build` 直接退出 0：4,747 测试、271 跳过、零失败，802 XML，109 任务，2m12s。XML ZIP SHA256 `3a68e6d551e36f127ec4b95cd57a68a67de42dff638a1075b78833d9f257dd06`。`npm run test:probe-types` 与构建完成后的 `tools/nekojs-ci-gates.py all` 退出 0。Standards 0 hard/0 heuristic，Spec 0 findings；独立只读审查没有将后续验收当作已通过。

五个精确新 JAR 的实际 renderer class 字节 SHA256 `d4f2ae48dadd54cc6570d50741a8b969285928c9eecb1d914bcc252d96c628eb` 与当前编译一致。使用独立新 world、显式全部五节点实际生成、注册、放置和重载，NeoForge fluid identity 与 26.x effect 重载前后 PASS；1,600 Python AST、1,675 TS 语法 PASS。五个测试服正常 RCON 关闭、直接退出 0，十个独占端口释放。1.21.1 八项历史反射缺失继续 PARTIAL。

保持默认 editor includes，仅验收设置 `skipLibCheck=false`。三个 NeoForge 节点的每个 server/startup 项目恰好移除四条现存 TS1341/TS1089/TS1093，两个 Fabric 节点各移除其实际现存的一条；十个项目均新增零诊断身份。Python 的所有诊断身份不变，生成文件路径完整，manual 字节不变。package 文件只发生普通 constructor 名称替换，真构造函数及其他字节保持；side-only 排序经只读 scoped AST 核对，保留同名 overload/class body 顺序，Python 导入按限定目标核对。本地 import alias 确定性仍不认证，不声称所有原始输出字节相同。

| 节点 | 全默认严格 TS server/startup | 全 Python stub Pyright |
| --- | --- | --- |
| 1.21.1 | 758 / 759 FAIL | 7 FAIL |
| 26.1.2 | 932 / 933 FAIL | 7 FAIL |
| 26.2.0 | 817 / 818 FAIL | 7 FAIL |
| 26.1.2-fabric | 1299 / 1300 FAIL | 13 FAIL |
| 26.2.0-fabric | 1039 / 1040 FAIL | 13 FAIL |

## 制品与剩余边界

[manifest.json](manifest.json) 绑定生产 patch SHA256 `0c02c1dab12ccefcb59573477851c5999d36e35f46db2b73b58ed98e68512ae2`、源码、JAR、测试及完整实机输出。[constructor-member.zip](constructor-member.zip) 含 3,556 项、7,644,018 bytes，SHA256 `bebb316822b6ae414c1f6f25a37e6f618b2d2e44c1570d88567e909eac116715`；[独立验证](archive-verification.json) 核对全 hash/读回、提交 patch、新测试在基线不存在、实际五制品 class、RED、六项非法拒绝和完成比较/退出。第一版打包脚本误读旧 comparison-f84 文件名，退出 1 且没有生成 ZIP；该失败保留，修正为已完成 comparison-60139fff 后才归档。打包自己的活动控制台不入 ZIP，完成控制台另存。递归扫描实际 RCON 密码，排除 properties/world/JAR/raw JFR 和无关文件。

此包归档时新源码性能 NOT RUN；随后独立结果见 [精确 7b724a50 性能](../ticket37-constructor-performance/README.md)，不套用 60139fff 或 f84 的结果。完整 IDE、同名 field/method、名称确定性、历史文件系统根因、native crash、fireResistant、其他领域、多人的独立下载窗口、首帧像素/跨节点视觉、维护者/发布/回滚仍有缺口。维护者 NOT RECORDED、总体 NOT COMPLETE、自动续跑 PAUSED、权限字段问题 UNRESOLVED；A22/A28 精确边界与无发布授权保持。
