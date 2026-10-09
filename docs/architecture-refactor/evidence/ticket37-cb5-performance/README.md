# Exact cb5f7fe6 performance — NOT GREEN

精确源码 `cb5f7fe6a81d1415acff69c5e91ac430457385d5`，专属clean checkout，节点26.1.2。原sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、fixture、样本数及startup41173ms/reload285.3ms门槛均保持。编译后TypeScriptClassRenderer与此前安装试验构建的主工作区class字节一致，SHA256 `73f7ca69d092772f5f07239f7794e2b4192e15a8a734f162063f84de0bdfef05`。

| 预定组 | 全部5个正式值(ms) | 均值 | 采样器exit | 结论 |
|---|---|---:|---|---|
| reload-confirmed-1 |383,344,215,200,235|275.4|NOT OBSERVED|指标低于门槛，验收NOT GREEN|
| reload-confirmed-2 |424,355,251,374,330|346.8|0，直接观测|FAIL|
| startup-confirmed |30130,34153,41102,29636,36138|34231.8|0，直接观测|PASS|

启动warmup40311/31916ms保留；没有丢首样本、改门槛、混revision、合并两组认证或追加替代组。第一组不能凭done标记伪造exit0。总体性能 **NOT GREEN**：第二组重载超限，第一组退出未观测。历史ec7PASS及085FAIL是独立窗口，本次不推断源码变更导致速度变化。

## 捕获故障与安全边界

首组sampler完成并结束、游戏经RCON关闭后，synchronous原PowerShell捕获器仍停留在调用处。实际库存检查确认专属sampler/game均0、端口25871/25872无监听；只结束精确核对的本轮wrapper12304及outer24540。tool直接返回outer termination exit -1；这不是sampler退出。捕获记录、命令身份、PID、时间和原样本完整保留，没有停止用户进程或Gradle daemon。

剩余两个原计划组改用Python subprocess将stdout/stderr直接重定向文件并取得OS wait returncode。首次guard因子进程默认模块环境找不到Get-FileHash退出1，在启动游戏前拒绝；该失败记录保留。随后仅给子进程设置Windows内置PowerShell模块目录及既有JDK25环境，全部原始HEAD/clean/hash/process/port/absolute-path/reparse校验仍执行，未修改sampler、fixture或生产代码。第二重载和启动组sampler均直接exit0，剩余runner直接exit0；没有将捕获修正称为工具权限入口问题已解决。

全部9个游戏PID均有正常RCON stop记录，无forced_kill/killed；两个reload共享各自单服、startup为7个独立启动。最终专属游戏和监听端口已归零。采样期间未并行测试/构建/其他专属游戏。

## 重现与归档

固定 [预声明计划](PLAN.md)；实际脚本副本和每组运行命令位于 [原始证据包](cb5-performance-evidence.zip) runner目录。sampler参数：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload -Node 26.1.2 -Warmup 2 -Samples 5 -Reloads 5 -GradleUserHome D:/mcmodDemo/NekoJS/.gradle-perf02 -ServerPort 25871 -RconPort 25872
```

startup组仅Mode=startup。完整JSONL、启动/重载日志、两个warmup、所有失败/guard/退出材料未删改。归档50项、1196431字节，SHA256 `a6fc831fdf5c3be8b67ee4f27cd69c083ef4b477e7409e7a297007328a70174f`；逐项hash/readback及凭据扫描通过，排除了服务器属性、world、二进制、原始JFR和无关数据。统计与逐项索引见 [summary](summary.json)。

新源码完整普通矩阵与五节点安装/语法结果见 [TS修复](../ticket37-typescript-member-names/README.md)。全IDE仍FAIL、legacy Probe仍PARTIAL，持久化目录移动间歇失败未修复；独立审查、实际configuration/disconnect窗口、fireResistant、第一帧/视觉与发布/回滚维护者结论仍未完成。自动续跑保持PAUSED；HTTPS登录恢复前所有新增提交仅本地。总票据/发布验收未完成。
