# Stream 捕获返回：11c9a180 五节点验收

产品提交 `11c9a180668800bdea856dd3cd7ef89152022075`。确切五节点制品安装后，十入口 strict/skipLibCheck=false 诊断 **1029→997，完整诊断块移除32、新增0**。F8清零，F1/F2/F4仍零，其余族逐入口不变，零UNMAPPED。**完整严格TS仍FAIL**；本页不代表票37整体完成。

## 合同与实现

EntityAccess两个乘客流入口返回 `Stream<? extends EntityAccess>`，Entity实现返回 `Stream<Entity>`。复用既有完整StreamView及继承返回facet，保留具体窄返回、父级合同、递归公开操作和操作数域检查；未将原生Stream全局标为协变。反射来源与编辑后IR必须匹配同种容器；hidden、rename、覆写返回及手写替代保持权威。Java/Python IR、运行时与权限未变，无依赖或配置增加，无golden变化。

公开backend夹具先复现旧逻辑失败；正例覆盖两入口的父级赋值、直接窄读取、宽域reducer，负例覆盖窄操作数和错误结果。真实NekoSharedHostAccess调用结果为 `child+child|child`。另有手写Stream、隐藏、编辑IR及Optional/Stream异种父接口对照。

## 验证

- 最终完整矩阵9m9s：`:common:check :common-api-processor:test guardLint`及五节点build，922 suites /5206 tests /271 skipped /零失败错误；common为340 suites /2489 tests /4 skipped。各Test任务独立临时目录，单次Gradle运行。
- `npm run test:probe-types`完整链退出0；四项CI gate通过。
- 五节点安装均RCON正常停服、exit0、未kill、会话锁释放。绑定8份变更来源及25份编译类，逐类与五个安装JAR一致；这支持该次安装来源关联，不代替最终候选实际加载与性能验收。

| 节点 | server | startup |
|---|---:|---:|
| 1.21.1 | 60 | 61 |
| 26.1.2 | 112 | 113 |
| 26.2.0 | 106 | 107 |
| 26.1.2-fabric | 112 | 113 |
| 26.2.0-fabric | 106 | 107 |

`artifacts.zip`冻结完整消费树、五节点实际生成树、十入口配置及原始诊断、来源/类摘要、JUnit XML、驱动摘要和日志；`manifest.json`逐项SHA256。旧15c4d9a6诊断从其已校验归档提取，不依赖可变安装目录。增减比较保留完整多行错误链，仅归一化安装前缀和行列。

从仓库根运行：

```powershell
python -u docs/architecture-refactor/evidence/2026-10-27-stream-capture-11c9a180/verify.py --typecheck --installed-typecheck
```

使用仓库TypeScript 5.8.3。六组独立输入重放包括正常、手写、Optional回归退出0；旧Stream实现退出2；孤儿保留退出0、清理后孤儿import退出2。十入口重放必须逐条多行诊断与冻结输入一致并合计997；通过的是证据复现，严格TS本身保持失败。

## 复查与历史失败

[复查记录](REVIEW.md)包含只读两轴意见和主代理验证。旧消费写入helper未清理孤儿声明：注入后旧生成重跑仍保留文件、import通过；修复后两目录均移除，独立import按预期TS2307。新增路径校验仅允许清理build内的实际解析子目录。异种容器回归也先红后绿，红XML保留。

初轮5205测试矩阵早于同容器guard，仅作历史记录；最终5206矩阵覆盖该guard。一次提前消费读取了仍在生成的旧红输入，日志明确标为premature-old-fixture，不计作通过。此前完整视图阶段首次PackSync安装失败的原因仍未查明；后续完整检查通过不回写该历史失败。

## 未完成

其余997条诊断（含F6 488）继续开放。真实DFU捕获原型被身份/底类型负例否定，未投产。host结构身份及JS数组桥异常仍开放。正式性能仅有d06cd961旧开发启动方式证据，不覆盖本候选；Python默认安装、客户端/TCP、分离回退、包装、内存预算及真实维护者结论仍需各自验收。未push、PR、发布或代签。
