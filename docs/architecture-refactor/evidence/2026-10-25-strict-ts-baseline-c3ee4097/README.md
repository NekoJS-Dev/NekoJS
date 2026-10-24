# c3ee4097 五节点隔离安装严格 TS 基线（可复用驱动首次执行）

本页绑定提交 `c3ee4097`（产品源码与 `d38c14dd`/`d06cd961` 一致，其后仅测试/文档），按
[bench/tsbaseline](../../../../bench/tsbaseline/README.md) 新保存的可复用驱动完成：五节点新目录新端口
隔离安装（平台取自 v3 钉版加载器/外部制品，jar 为本轮 `versions/<node>/build/libs` 现构建产物，
SHA256 见 [preparation-manifest-trimmed.json](preparation-manifest-trimmed.json)，rcon 密码已脱敏），
实际启动已安装服务端、RCON 触发 `nekojs probe typescript`、RCON `stop` 干净停服，再用生成 jsconfig
（strict=true）覆盖 `skipLibCheck=false`/`noEmit=true` 执行十入口 tsc（TypeScript 5.8.3）。

## 结果

[run-summary.json](run-summary.json)：五节点 probe 394/448/430/540/519 文件，exit 全 0、无强杀、
session.lock 全部释放。[ide-check-summary.json](ide-check-summary.json)：**十入口共 1609 条诊断、
全部 exit 2、无编译器崩溃**，逐入口 111/112、165/166、159/160、186/187、181/182 与错误码分布
（TS2416×804、TS2344×656、TS2430×50、TS2307×39、TS2724×24、TS2552×20、TS2426×10、TS2420×4、
TS2304×2）与 v3 修正计数**逐项一致**。

更强的同一性验证：本轮五节点 `.neko_probe/typescript/@package` 声明树与 v3（3ccea55a）安装目录
**逐字节一致**（diff -rq 全部无差异）。即默认安装（无验收脚本）实测 = v3 的 1629−20 修正值，
1609 就是默认声明的真实严格诊断数；3ccea55a→c3ee4097 间声明零漂移（混合引号修复不影响本轮声明）。

## 边界

- 这是**默认安装基线**，不是修复进展：1609 仍 FAIL，全部历史 FAIL 记录保留。
- Python stub（v3 1609 份、Pyright 0 错误）是另一指标，本轮未重采。
- 平台（libraries/fabric-server-launch/graal/fabric-api）为钉版外部制品，从 v3 安装目录复制；
  与仓库源码绑定的只有五个 nekojs jar（manifest 记录来源路径与 SHA256）。
- 完整诊断原文保留在本地 `build/tsbaseline-c3ee4097/ide/*-tsc.txt`，作为根因分类输入；根因表与
  Constable→Optional 族的修复验证见后续独立证据页。
