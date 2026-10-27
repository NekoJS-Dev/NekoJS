# SectionPos Bean迁移原型（未投产）

正式产品11c9a180仍为997条严格TS错误。本原型仅供[精确迁移决策](../../../research/2026-10-27-section-pos-bean-decision.md)，不表示已获批准或已实现。

五节点实际Vec3i/SectionPos、生产HostAccess均证明：`section.x`是数字，`section.x()`可调用，`section["x()"]()`失败，已有`getX()`可调用；y/z相同。完整观察保存在runtime/*.json，JUnit报告与来源一并冻结。临时测试仅写在忽略的build目录，通过临时Gradle init接入；最终五节点运行2m4s通过。此前初始化脚本误应用buildSrc、未指定ECMAScript导致ES5语法失败均保留，不计作产品回归或通过。

只改隔离声明副本五处SectionPos类/接口的15条零参坐标方法，保留数字Bean及已有getter。十入口997→973，完整诊断块−24/+0；F13 36→12，其余族逐入口不变，零UNMAPPED。余下12条及其他族仍FAIL，未改运行时、Java/Python、公共生成器或其他名称。

artifacts.zip包含五节点真实观察、测试报告、临时测试源码、驱动、五份改动声明、十入口完整诊断、分类及多行诊断差分。原始完整输入复用已冻结Stream证据，并由manifest摘要绑定。

仓库根运行 `python docs/architecture-refactor/evidence/2026-10-27-section-pos-bean-prototype/verify.py` 可核对摘要、运行观察、精确改动及诊断差分；加`--typecheck`可重建五份隔离输入并重跑十入口，必须逐条多行诊断一致、总计973。证据复现通过不代表生产严格TS通过。
