# 2026-09-28 真机游戏内验证会话(维护者操作)

主会话 agent 启动 dev 客户端(`:26.1.2:runClient`,主树 mult@ea378f16 + 工作区未提交的
D2 修复),维护者本人按提示操作,agent 读取 `run/logs/latest.log` 与 `run/screenshots/`
采集证据。本目录是该会话的原始证据归档;各票引用本目录,不在票内复制日志全文。

## 环境

- 节点 26.1.2(NeoForge,dev 客户端),JEI 在场;单人世界(创造、作弊开)。
- 冒烟脚本:`versions/26.1.2/run/nekojs/server_scripts/t24_d2_broken_cancel.js`
  (BlockEvents.broken return-true 取消 + PlayerEvents.loggedIn + EntityEvents.joinLevel)
  与 `t27_dashboard_sample_error.js`(故意抛错,供错误面板展示)。

## 结果(逐项)

| # | 步骤(维护者操作) | 结果 | 证据 |
|---|---|---|---|
| 1 | 创建并进入世界 | SERVER 脚本加载;故意错误被 frozen diagnostic record 完整记录(phase=EXECUTION/owner/generation=1/source/module/cacheRevision/cause 全字段,即票 30 record 真机存活) | `log-excerpt.txt` `script-diagnostic id=…t27_dashboard_sample_error.js` 行 |
| 2 | `/nekojs error` 打开只读错误面板,点详情/复制/定位 | 面板打开并渲染列表/详情;**定位动作真实打开了 VS Code**(ExternalIDE open 动作经 LocalErrorSource→ErrorOpenService 链路真机成立) | 维护者交互确认 + `2026-09-28_21.48.59.png`(面板) |
| 3 | 破坏方块(多次) | `T24-BROKEN fired (cancelling via return true)` ×14+,**方块未被破坏、无掉落**——工作区未提交的 D2 修复(BlockEvents.broken 可取消 + bridge 回写原生取消)真机生效 | `log-excerpt.txt` T24-BROKEN 行 + `2026-09-28_21.50.34.png` |
| 4 | (顺带)玩家/实体事件 | `PlayerEvents.loggedIn` 触发(payload 属性名以维护者视角记录为读失败,不影响事件面);`EntityEvents.joinLevel` 对 villager/cat/iron_golem/slime/horse/pig/sheep/chicken 真实触发 | `log-excerpt.txt` |

## 供给的票

- 票 24:AC9 的「真实玩家方块破坏 runtime smoke」缺口闭合(D2 修复真机验证);
  玩家/实体事件真机腿补齐。D2 修复本体属并行会话未提交工作,本会话只验证不改动。
- 票 27:AC1 的「有效本地定位动作」获得真机证据(VS Code 实际打开);AC7 的节点
  runtime smoke 腿(GUI 打开/列表/详情/复制/定位)真机成立。
- 票 30(已 closed):frozen diagnostic record 字段链路真机存活确认。

## 限制

- 截图为 PNG 原档(维护者另存有 VS Code 打开瞬间的剪贴板截图,未入库);
  面板过滤/滚动等细粒度 UI 交互未逐项断言,由维护者目视确认"面板渲染正常、按钮有响应"。
- CLIENT 渲染性能、多玩家、fabric 客户端不在本会话范围。
