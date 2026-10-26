# 完整捕获视图前驱：源码通过，安装发现祖先合同遗漏

产品提交 `8f71ca31`，独立测试诊断提交 `5dd4de8e`；归档源码绑定后者，产品代码相同。此前未提交改动遭意外清理，用户批准恢复后在隔离工作树继续实现。没有提交或删除原目录的 `lite/` 等外部材料。

## 改动与合同

`TypeScriptCaptureViews` 从当前可见、编辑后的 IR 生成 Optional/Stream/List/Collection/SequencedCollection/ListIterator 视图，继承完整原始实例面，投影全部相关重载，不再通过删掉公开方法获得兼容。恢复 `or`、全部 `reduce`、Collector `collect`、`toList` 与递归导航。隐藏、排除、手写替代、改名、覆盖槽及原始工厂合同保持各自权威；Java/Python IR、权限和运行时转换不变。

SAM 输入描述复用现有参数及返回转换规则；严格 host 输入先于回调/对象代理重载，为捕获操作数保留域检查及普通工厂的上下文推断。容器载体保留完整 host 结构和可见载荷观察方法；Collector 同时保留完整原始接口和累加器域。公开视图名、模块私有输入名及跨模块导入均分配避让名称。

五组新公开 backend 消费覆盖正常完整 API、同上界覆盖、较宽 flatMap、工厂推断、嵌套导航、IR 编辑与 SAM 重命名、手写 Optional/Stream、helper 名冲突和 backend 重用；隐藏/排除另有依赖报告回归。七组冻结输入包含上述五组及既有 wildcard、继承返回消费者，全部用 TypeScript 5.8.3、strict、skipLibCheck=false 重放。

## 实际验证

- 普通 `:common:check guardLint` 通过，3m39s：339 suites / 2483 tests / 4 skipped / 零失败或错误，含公共模块隔离。
- `npm run test:probe-types` 完整链 exit 0，含全部新增消费者及原有正负合同。
- `python .../verify.py --typecheck` 对冻结输入独立重放七组，全部 exit 0。
- 官方再生只有 java.util、java.util.stream 两文件变化；逐字节检查原始类型和 SAM 别名前缀除导入外不变，其他资源文件字节保持。旧字节、差异摘要、生成检查脚本与新源码都在归档。
- 五节点矩阵通过（5m20s）：921 suites / 5200 tests / 271 skipped / 零失败或错误。初次宽泛目录汇总把 golden 生成任务留下的一项跳过计入，正式统计已排除该生成报告。
- 四项 CI gate 通过；首次汇总因新工作树缺少五份 source-roots 记录失败，随后用现有探针从真实构建配置补齐，失败及最终通过均保留。
- 五节点新安装均正常 RCON 停止、exit 0、未 kill 且世界锁释放；8 个源码族的 25 个 class 与五份安装 JAR 逐字节匹配。
- **十入口严格 TS 为 1037，FAIL**：对 `3d2cc1cb` 的 1029，完整诊断块移除 0 / 新增 8。新增均为现代节点 RegistryAccess 的连续 wildcard 继承合同，F1 0→8，其余族不变，零 UNMAPPED。因此本前驱不能视为 F1 收口。

| 节点 | server | startup |
|---|---:|---:|
| 1.21.1 | 64 | 65 |
| 26.1.2 | 117 | 118 |
| 26.2.0 | 111 | 112 |
| 26.1.2-fabric | 115 | 116 |
| 26.2.0-fabric | 109 | 110 |

遗漏来自父方法自己是 wildcard 覆盖时，仅收集该层上界，没有继续收集其祖先 facet。后续修复及新候选验证另行记录，本归档保留失败，不覆盖为未来通过状态。完整默认声明、实际脚本配置及十入口诊断均已冻结，可仅重定位顶层 extends 后重放，无需读取当前安装目录。

## 保留失败与限制

旧生成器缺少完整成员的严格消费红证、重命名投影漏掉输入域保护的两个 TS2578 红证及 helper 名冲突失败日志均保留。首次完整 common 的两项录制树比较失败与一项 PackSync 初次安装失败也保留：随后该单项、整个类和普通完整 common 通过，首次 PackSync 失败根因未查明。仅增加断言的失败原因，不修改同步产品代码或放宽断言。

显式 any 和完整伪 host 的结构身份限制仍开放；JS 数组触发泛型桥 ClassCastException 的旧失败未修复。正式性能仍只绑定旧提交 `d06cd961`；没有以本页代替最终候选性能、Python 默认安装、客户端/TCP/分离回退或维护者结论。票 37 保持 in-progress，不 push、不发 PR、不发布、不代签。

## 重放

在仓库根目录安装锁定的 npm 依赖后运行：

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-27-complete-capture-views/verify.py --typecheck
python -X utf8 docs/architecture-refactor/evidence/2026-10-27-complete-capture-views/verify.py --installed-typecheck
```

`manifest.json` 逐条绑定 `artifacts.zip` 中的源码、原始日志、报告和完整消费输入；核对总摘要与各项摘要后再重放。它不调用未来生成树，保留原始失败与最后普通通过报告。
