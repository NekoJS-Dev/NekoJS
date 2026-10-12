# 当前7d双客户端实机验收

本轮用当前生产源码7d77308bfa400721304b20940ada91092180a1ed的官方NF26.2 JAR；checkout653c2e47仅有后续文档差异。JAR SHA256 `2a64c34ca2d7abc3e8b2cd6402b17f204b3c10b4d15fcd1c2ebc917b3dd9aff2`，新服务端与两个新客户端三份实际安装逐项绑定1592个当前编译class。NF26.2.0.75/JDK25.0.2，fresh owned world/profile、合成离线身份，端口25991/25992。没有HTTP bridge、跨GLFW进程窗口激活或键鼠输入；隐藏console启动不等于另一个Windows桌面，游戏仍可创建窗口。主生产源码/安全/HostAccess/ClassFilter/接口/持久化和wire均未修改。

两个真实客户端各完成配置catch-up generation2并入服。SERVER普通重载先AWAITING_ACK，随后COMMITTED activatedGeneration3；两个实际participant均有ack与activated activation-report，item给两名玩家实际成功。脚本manager重载generation2与动态registry activatedGeneration3不是同一个计数。

CLIENT声明/HUD generation1→资源重载2→恢复3。两端同一非法JS候选真正触发NEKO-1008，active效果仍停留generation2且保持模糊；仅删除精确hash的专属错误fixture后实际资源重载恢复3。原生Screenshot.grab(minecraft,false)在各client tick owner十tick后抓图，避开guest异步consumer。二十张原始PNG全部逐张查看，六组实际清晰/模糊墙面（两端各generation1/2/3）验证固定ROI水平边缘差异：A清晰554/594/594，模糊19/18/20；B清晰473/473/476，模糊16/19/15。教程toast/chat/HUD位于ROI之外，选中pair没有菜单或加载遮罩；不是第一像素认证。

原失败保留：首次建墙fill返回区块未加载，最初A两图并非预定墙面。玩家传送、区块加载后同一建墙命令实际成功；初始图没有覆盖或冒充通过。恢复后的首张A clear-recovered还有Mojang加载遮罩，记该画面FAIL，保留；后续独立命名recovered-settled pair展示实际稳定画面。没有抹掉失败或把首次HUD当完整视觉。复制helper的模板字符串未替代本轮PLAN。一次检查命令拼错Python路径未运行，不影响随后真实调用；没有补造该失败输出快照。

两端Minecraft.stop在tick owner实际请求后0.796/0.891s正常退出0，服务端RCON停服直接0；没有forced kill、hs_err或Graal并发错误，端口最后可绑定。未触碰用户进程/无关profiles。没有重跑构建或改变已有正式性能失败；[4810测试矩阵和五节点安装/IDE](../ticket37-node-assert-repair/README.md)及[精确7d性能](../ticket37-node-assert-performance/README.md)分别保留来源。

[不可变ZIP](current-client-evidence.zip)71entries/4536623bytes，SHA256 `16597a681e47e44aa524e7d610d6bb042fc3490ab11f12aac64506e90f61f7b5`。原日志、二十张原图、实际fixture、candidate结果、RCON响应、进程/direct exit及逐类SHA见[summary](runtime-summary.json)和[manifest](manifest.json)；[独立验证器](verify.py)重算全部hash/原图尺寸/六组像素边缘、真实ACK及两初始catch-up、非法候选/恢复和退出。实际随机RCON密码扫描通过；credentials/properties/world/launcher参数/二进制/native DLL/raw JFR/hs_err/无关用户资料排除。

当前双端catch-up/prepare/ACK/commit、局部视觉和失败保留/恢复PASS。第一像素、当前abort/join barrier/pack下载、其他客户端节点完整领域、磁盘问题、正式重载性能和完整IDE/Pyright仍未完成；HTTP bridge和外部GLFW缺陷也没有修复。真实维护者结论未记录，发布未授权；总体NOT COMPLETE，自动续跑暂停、工具权限字段问题未解决，A22/A28边界不变。
