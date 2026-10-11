# 精确 2a421c18 性能复测计划

预声明源码 `2a421c18bd36dc59d0006db8e68d515f0122ce8e`；这是本轮 Callable 修复的已提交生产源码。文档提交 01565e85 没有改变生产文件。复用原专属、已清洁且没有运行中游戏的 `build/ticket37-perf-owned-ac810fa7` 隔离 checkout，先执行原绝对路径、reparse、进程、端口与 clean guard，再正常切换精确 commit。不要 reset/clean、删除无关内容或停止用户进程。

沿用固定 sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`，原 fixtures 与采样口径不变。节点 26.1.2，原隔离 GRADLE_USER_HOME、JDK25、25871/25872 端口。顺序预定三组：reload-confirmed-1、reload-confirmed-2、startup-confirmed。每重载组保留全部 5 个正式样本；启动保留 2 个 warmup 和全部 5 个正式样本。门槛 startup41173ms、reload285.3ms，不删首样本、不混 revision、不追加替代组。无当前分数；历史 cb5 FAIL 保留，不能宣称本轮 Python 修改造成速度变化。

使用已验证的 Python subprocess 捕获真实 OS wait returncode，每组前执行全部原 guard。子 PowerShell 使用 Windows 内置模块路径以避免历史 Get-FileHash 环境缺失。原 sampler 不改。采样期间不并行构建、测试或其他专属游戏；五节点实机、IDE 编译和诊断比较均已直接退出。Git 网络传输或文字归档不认证性能，也不改变样本。

所有样本、完整日志、失败与退出记录保留。停止必须记录正常 RCON；强制关闭、timeout、缺少 direct exit 任一出现均不能认证该组。每组后核验原游戏和端口结清。记录源码/采样器/fixture SHA，并将性能 checkout 编译的三个修改后类与已验收主工作区构建的类字节比较。归档排除属性密码、world、二进制和原始 JFR。

本轮仅更新性能证据；完整 IDE、native/domain/视觉/下载窗口、发布/回滚与维护者结论仍独立未完成。自动续跑保持 PAUSED；工具权限字段入口问题仍未解决。
