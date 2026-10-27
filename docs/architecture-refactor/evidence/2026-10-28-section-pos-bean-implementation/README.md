# SectionPos精确迁移：3f9f1b0a正式安装验收

用户已批准五节点15处TypeScript坐标方法声明迁移。产品提交 `3f9f1b0a1fb4455fd1dfd507af58b1ad2c301891`；新五节点隔离安装及十入口严格检查确认 **997→973，完整诊断块移除24、新增0**。F13从36降至12，其余族逐入口不变，零UNMAPPED。**完整严格TS仍FAIL，票37继续开放。**

## 改动及合同

TypeDocs贡献新增精确BeanMethodProjectionCatalogEntry，经原有common/node收集、runtime及各侧catalog快照传递。规则匹配真实零参实例方法及已编辑可见祖先链中的getter；旧snapshot构造签名保留。common无MC/loader依赖，版本树只登记SectionPos的x/getX、y/getY、z/getZ。

| 原TS调用 | 新TS读法 | 保留 |
|---|---|---|
| `section.x()` | `section.x`或`section.getX()` | 数字Bean、getter、静态x(long) |
| `section.y()` | `section.y`或`section.getY()` | 数字Bean、getter、静态y(long) |
| `section.z()` | `section.z`或`section.getZ()` | 数字Bean、getter、静态z(long) |

JavaScript普通直接调用继续可用；真实五节点Graal证明`section["x()"]()`等会读取数字后调用失败，因此未套用真实字段选择器规则。隐藏、改名、返回编辑、合成getter/字段及手写替代保持权威；共享IR、Python、Java方法、权限、运行时和其他名称均保持。Shelf.level不属于批准范围。

## 验证结果

- 完整common/check、处理器测试、guard及五节点build：5m48s，929 suites /5226 tests /271 skipped /零失败错误。common为342 suites /2504 tests /4 skipped；无golden变化。
- `npm run test:probe-types`完整链退出0，含新严格正负消费；四项CI gate及五节点source-roots检查通过。
- 新五节点均RCON正常停服、exit0、无kill、会话锁释放。固定JDK21.0.10/25.0.2、隔离端口35200起；驱动记录来源和实际制品摘要。
- 17份来源、38份common编译类逐类与五个安装JAR匹配；另逐节点绑定MinecraftTypeDocsPlugin编译类。关联不外推为最终候选性能或全部运行时身份门禁通过。

| 节点 | server | startup |
|---|---:|---:|
| 1.21.1 | 52 | 53 |
| 26.1.2 | 111 | 112 |
| 26.2.0 | 105 | 106 |
| 26.1.2-fabric | 111 | 112 |
| 26.2.0-fabric | 105 | 106 |

五份MC core模块与旧模块仅省略三条SectionPos零参方法后的文本逐字一致，其他内容完整保留。另34份声明经TypeScript AST核对仅移动独立命名声明，保留同名重载组顺序；无新增/删除文件。1.21.1的dependencies.json还记录同一不可加载IClientBlockExtensions的底层客户端类异常文本由ClientLevel变为ParticleEngine；仍为同一不可用类型，F12未消除。未把该诊断文字变化或声明顺序当作本轮产品改动。

## 冻结与复现

artifacts.zip包含完整五节点实际声明/配置、十入口原始多行诊断、来源与编译类、最终JUnit XML、驱动摘要/日志、原始红输入和合成编辑反例。manifest逐项SHA256；基线诊断和差异声明从已校验的11c9a180冻结包提取。

从仓库根运行：

```powershell
python -u docs/architecture-refactor/evidence/2026-10-28-section-pos-bean-implementation/verify.py --typecheck --installed-typecheck
```

使用仓库TypeScript5.8.3。正常消费者退出0，旧逻辑输入必须按冻结错误码退出2；另重建完整五节点输入，AST顺序审计及十入口多行诊断均须精确匹配973。复现通过表示证据一致，严格TS整体仍失败。

实际生成命令：`bench/tsbaseline/run.py prepare --target <隔离目录> --platform-source <v3平台目录> --revision 3f9f1b0a1fb4455fd1dfd507af58b1ad2c301891 --base-port 35200`，随后同一驱动的`run`与`check --target <隔离目录>`；无需改默认扫描配置或增加脚本夹具。来源绑定、完整诊断比较及声明差异审计驱动均已归档。

## 历史失败及剩余范围

[主代理复核](REVIEW.md)记录先红后绿和边界。旧实现产生TS2416/TS2426及合法赋值/泛型约束失败；合成getter/字段两个反例先红后修复。首次完整矩阵因旧节点贡献测试未接受新规则失败，更新接收方后继续严格断言原Entity规则及三条坐标规则；失败XML/日志保留。首次绿色候选的静态参数名断言误写arg0，实际源码为value，已纠正；未改源合同或弱化类型门禁。

F13余12条仍涉及26.1.2的Shelf；F6等其他973条诊断继续开放。正式性能仍仅绑定d06cd961旧开发运行；新候选性能、Python默认安装、客户端/TCP、分离回退、包装、内存预算和真实维护者结论另行验收。未push、PR、发布或代签。
