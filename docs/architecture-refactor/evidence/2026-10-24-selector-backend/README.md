# 显式选择器生成器实现与回归

用户已批准[迁移规则和完整清单](../../../research/2026-10-24-explicit-selector-decision.md)。本项把规则实现到生产生成器；不以手改声明副本作为交付。最新冻结运行时验收仍属于6e43b827，新源码的实际安装、默认声明差分及性能复验须另行绑定，票37保持未完成。

本归档对应首实现7d17de4e。随后五节点首轮安装及保序修复的最新事实见[安装差分跟进](../../../research/2026-10-24-selector-installed-followup.md)，包括原清单漏列的push继承家族与首轮验收脚本错误；本页原16项回归、5174项报告及归档字节保持当时含义。

## 实现和合同

在既有闭包完成后的批量继承投影中，按真实实例字段、真实方法及本次编辑后的继承图决定普通方法名和Graal显式选择器；字段类型和读写保留。方法选择器使用原始Java名称和擦除参数，方法重命名不作为Java签名。共享IR不改名，Python renderer、运行时权限、数据及网络合同没有改动。

安全分支保留普通调用，只在普通方法失于已迁移父合同的首个安全层补回；后代复用已有声明。泛型、方法变量与IR编辑按实际父参数专门化。手写重载留在原模块和泛型作用域，通过父类型索引引用；同名自身重载保留为完整可调用交叉类型，不猜写TypeScript片段。泛型具体实现只有存在真实编译桥接时才补父擦除选择器。隐藏、枚举固定骨架、静态和无源成员不会成为伪造Java选择器的依据，配置改变会刷新选择器快照。

只读审查指出的无关字段触发重写、先祖字段配子类新方法、父IR重命名漏接及安全后代重复补声明，均先以失败回归复现后修复。Graal生产HostAccess的独立回归实际验证Object/String两种擦除选择器，以及继承字段读值1、子方法选择器返回2；这不冒充五节点安装或Mixin注解访问矩阵。

## 已执行验证

- `gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`：最终矩阵PASS。报告共5174项，271 skipped，0 failures/errors；common含16项新增选择器回归，隔离检查通过。
- `npm run test:probe-types`：PASS，包括新增十四组strict=true、skipLibCheck=false的正负消费，以及原有全部消费入口。枚举结构和无关家族回归独立核对渲染结果，不以过滤既有JDK通配符错误来宣称全量严格通过。
- 五节点`source-roots`和`tools/nekojs-ci-gates.py all`：PASS；`npm run check:wiki`：26个双语主题、0问题。双语页用既有stamp流程更新摘要，draft/human pending保持；首轮缺摘要的失败记录保留。
- TypeScript原method与索引属性的赋函数对照均通过；没有据错误的“原method不可赋值”假设新增readonly限制。

主代理执行所有实现、构建和验证；Luna xhigh仅只读审查。[双轴审查](REVIEW.md)记录发现、修复和撤回项。原有无关生成golden的SHA256仍为`CFDFA54CC5F9214D42D4BB5F1EEF3C6984E9DC3414C4952A76236C0A923B2ED2`，未覆盖其改动。

`evidence.zip`保存1047项、964357字节，SHA256为`662040d092641b7e594c91dc0a74c7d2567254929686bacd450c2b9bcfb54804`，包括全部915份JUnit报告、最终构建/消费记录、保留的red日志及十四组实际编译输入。主代理实际执行`python docs/architecture-refactor/evidence/2026-10-24-selector-backend/verify.py --run-types`通过：重算报告、核对源码绑定并从归档重新编译十四组程序。五份旧候选properties仅在本地作私密模式输入，1047个载荷扫描0匹配/0替换；公开工具绑定该收据，不能缺少私密模式而独立重复扫描。归档不含properties、world、运行时JAR或正式性能样本。

## 剩余工作

用本次源码构建的JAR在五个新隔离安装中重新生成默认声明，核对979条替换、25条保留并补选择器和1条继承补回的预期，记录任何实际差异与真实来源；执行完整十组默认严格检查及Python检查。旧原型5537→1621仍是历史副本结果，不能充当本次生成器的实测结果。最终候选冻结后再按原门槛复验运行时、性能和分离回退；没有PR、推送、正式发布或维护者代签。
