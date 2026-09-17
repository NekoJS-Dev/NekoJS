# 孪生文件重提取审计（ticket 16，2026-09-17 双轴审查 M1）

对象：`versions/1.21.1/src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java`
（1.21.1 编译单元）与共享 `src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java`。

## 前置事实（复核结论）

- 共享文件的头两行自述：「26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是
  `versions/1.21.1/src` 下的同名文件，改本文件行为时须同步它。」
- 1.21.1 不编译共享文件：`versions/1.21.1/build/generated/stonecutter/main/java/com/tkisor/nekojs/listener/`
  下没有该文件的生成副本，编译单元是孪生文件。
- `BlockModificationEventJS` 整文件 `>=26` 守卫；1.21.1 编译产物目录
  （`versions/1.21.1/build/classes/java/main/com/tkisor/nekojs/wrapper/event/server/`）**没有**
  `BlockModificationEventJS.class` → 该类型在 1.21.1 不存在。

## 提取命令与结果

```bash
./gradlew :1.21.1:stonecutterGenerate --console=plain
python tools/extract_evaluated.py \
  versions/1.21.1/build/stonecutter-cache/sources/main/java/com/tkisor/nekojs/listener/ServerEventListener.java \
  build/t16-logs/extracted-ServerEventListener.java
# -> 221 -> 216 行
diff -u versions/1.21.1/src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java \
        build/t16-logs/extracted-ServerEventListener.java
```

## diff 摘要（孪生 → 提取产物）

| # | 差异 | 判定 |
|---|---|---|
| 1 | 头部注释：孪生「1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对…」 vs 提取产物保留共享文件的「26.x 实现…」头 | 预期（孪生的 1.21.1 头是本地标注；提取产物只反映共享文件内容） |
| 2 | `+import ...BlockModificationEventJS;` 与 `+BlockModificationEventJS.fire();` | **刻意的节点适配**：该类型在 1.21.1 不存在（上节实证），孪生必须缺席；提取产物含该行 → 直接落盘会导致 1.21.1 编译失败 |
| 3 | `-import ...AddReloadListenerEvent;` / `+import ...AddServerReloadListenersEvent;` 与对应方法签名 | **节点 API 差异**：资源 reload 事件名两节点不同，孪生用 1.21.1 的 `AddReloadListenerEvent` |
| 4 | `+` ticket 16 的 facade 调用行 | **不存在**——`//? if >=26 {` 守卫已把它丢进失活分支（这正是守卫的作用）；提取产物与孪生在此一致 |

结论：提取产物**不可整文件落盘**（差异 2、3 是必要的人工适配）。本次只做注释同步：孪生
对应位置写明「方块属性修改调用与 facade 调用在 26.x 侧存在、本节点缺席，重新提取时须保持
缺席」，零行为变更；`tools/extract_evaluated.py` 的用途在本文件（以及 `BlockModificationEventJS`
这类整文件守卫面）上只能作为审计/起点，孪生仍需人工维护（REPORT §11 G10）。
