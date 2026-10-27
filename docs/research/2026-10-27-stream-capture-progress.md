# Stream 捕获返回：F8 推进记录

## 来源与目标

当前正式安装15c4d9a6严格TS为1029，F8为32条。1.21.1映射源码和26.1.2 NeoForm源码均确认 EntityAccess 的 getSelfAndPassengers/getPassengersAndSelf 返回 `Stream<? extends EntityAccess>`，Entity返回 `Stream<Entity>`；生成器展平 wildcard 后出现两个方法的TS2416及下游EntityAccess泛型约束错误。只读调查核对了这两版本，未把它冒充26.2源码验证。

目标是复用完整StreamView和既有继承捕获facet，保持具体实现的窄返回、父接口合同、全部公开操作和错误操作数域检查。不把原生Stream全局标为协变，不修改Java/Python IR、运行时或权限，不引入配置或依赖。

## 已完成

- 公开backend夹具先复现两个方法的TS2416、合法父级赋值/宽域调用失败；旧输入和原始诊断已另存。
- 捕获返回收集、可用性和引用标记从Optional扩展到标准Stream。仅接受未覆写、单一非Object上界且无下界的用例；源class与IR容器需一致。
- 父级输出完整StreamView，具体实现保留原始Stream<Child>并附加父级facet；连续wildcard中间覆盖继续携带祖先合同。
- 两组strict/skipLibCheck=false消费通过：正常完整合同及手写Stream替代。隐藏来源报告unavailable，父IR的hide/rename/return覆写保持权威。
- 真实NekoSharedHostAccess执行两个入口的Java host reducer和JavaScript map/toList读取，结果`child+child|child`；未改变运行时。
- 新消费已接入npm test:probe-types。最终完整common/处理器/guard及五节点build通过（9m9s，922 suites /5206 tests /271 skipped /零失败错误），完整npm链与四项CI gate通过，无golden差异。

第一轮完整矩阵通过（9m42s，922 suites /5205 tests /271 skipped /零失败），未产生golden差异。只读规范复审指出新消费目录未清理，主代理注入孤儿声明后，旧生成器重跑仍留下两份文件，孤儿import可编译通过；清理helper增加build范围和实际解析路径校验、逆序删除旧输出后，两份孤儿消失，独立import按预期报TS2307。随后完整common/check+guard通过（4m22s）。

扩展两类容器还补上IR父接口变更的边界：Optional父方法上界不应变成Stream子方法facet。新增公开backend反例先红（冻结XML明确包含错误StreamView<Base>）后加同容器过滤修复；含此反例的六项F8回归、真实HostAccess及两组严格消费均通过。最近声明、hidden/rename/slot覆写权威保持。此后冻结源码再跑最终完整矩阵，不能把前一版矩阵外推为新增guard已验证。

早期一次命名为green的tsc调用在生成器仍编译时读取了旧红输入，日志改记为premature-old-fixture，不作为修复失败或绿态证据。之后均等待生成器正式完成再消费；实际绿态通过退出码确认。

F6已取得真实DFU红证，但不透明标记合并独立捕获、never泄漏底类型，原型全部未投产，证据提交63d30c53。F6仍开放；本项不以其安全边界换取计数消失。

## 剩余门禁

产品11c9a180已本地提交并快进stonecutter。确切五节点JAR新安装全部正常停服、无kill、锁释放；8份来源/25份编译类逐类绑定。十入口1029→997，完整诊断块−32/+0，F8归零，F1/F2/F4仍零，其余族逐入口不变、零UNMAPPED。[冻结输入及独立重放](../architecture-refactor/evidence/2026-10-27-stream-capture-11c9a180/README.md)保留全部历史红证，严格TS整体仍FAIL。

最终性能、Python默认安装、客户端/TCP/分离回退、包装、内存预算与维护者结论仍另行验收。F13下一步先以真实Graal证明Bean/同名方法行为，原真实字段选择器授权不自动扩张至派生Bean属性迁移。
