# 新 Probe 源码其余四节点实机复验

本次均安装 production patch SHA256 `9b70e7439799f09ebf2c7fc2f551cd4e39da4118e62b861b27444e6c9715ea6f` 对应的新官方构建 JAR，独立世界和专属端口，无生成输出手工修改。源码构建时仍为基于87e9b863的WIP；维护者随后明确接受20份golden变更用于本地提交，见[确认记录](MAINTAINER-ACCEPTANCE.md)。

| 节点 | 直接观察结果 | Python stub AST | 安装 JAR SHA256 |
|---|---|---|---|
| NeoForge26.1.2 | 14项注册、嵌套指纹等价/突变、FluidType身份、两后端生成、方块/流体放置、效果施加及重载后重新施加通过 | 377/377 | `66a72bcca76d76484bde698e4dfd564c85c1f10b69613732ef53deb1963e5379` |
| NeoForge26.2 | 同上，通过 | 344/344 | `6f7e01679fb812e7384576906e9b52cd30fb586f9e1b71edd11b00e637406152` |
| Fabric26.1.2 | 7项受支持注册、嵌套指纹、两后端生成、方块放置和重载通过 | 293/293 | `81ba12cfe97e672aa9a6a9e3282fd6726cfeeffcba38736695a3bf19aed1eae5` |
| Fabric26.2 | 同上，通过 | 259/259 | `1d896a8d1b60650f9e2e74f2dd6481fac3d82f70541a1195e0858b6151f6b679` |

运行环境分别为NeoForge26.1.2.71、NeoForge26.2.0.75、Fabric loader0.19.3/API0.155.2+26.1.2、Fabric loader0.19.5/API0.159.0+26.2，全部JDK25。Fabric未被计作支持流体或效果注册。此处是服务端实机检查；客户端视觉、完整IDE和正式性能不是本表的验收项目。

第一轮26.1.2通过；其余三节点在注册检查后发生`Owned RCON response missing`，未完成Probe，不计通过。验收客户端原先连续发送Probe和结束标记请求；修正为先收到Probe的首包，再发送只读标记并收完剩余包。此修改仅涉及验收工具；新建独立世界重跑三节点，JAR字节不变，三节点全部通过。RCON协议解析方面的具体服务端因果尚未单独证明，不把工具修正推断成生产故障修复。

两轮共7个专属服务器均正常RCON关闭，runner直接记录exit0。退出码只证明进程退出；通过判断另由实际命令响应、日志和生成文件给出。未停止任何用户进程，未修改正式性能采样器。

原始失败、成功命令响应、日志、生成树、默认编辑器配置、fixture和进程退出记录保存在[other-node-replay.zip](other-node-replay.zip)，3125条目，SHA256 `adf3c061850a0e7cbbb77fe02c657bb5981d29413df8b41cccd82b9ae67f2cd9`；[manifest](other-node-replay-manifest.json)记录逐条SHA和试验判定。归档读回校验通过，并检查包括解压日志在内的RCON凭据泄露；排除server.properties、world、账号、二进制库和原始JFR。原legacy诊断归档保持原字节。
