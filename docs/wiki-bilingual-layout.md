# Wiki 双语组织

本仓采用用户确认的 `xxx-yyy_cn.md` / `xxx-yyy_us.md` 命名。`cn` 表示中文正文，`us` 表示英文正文；这是文件约定，不是 Minecraft locale 或运行时配置。

## 文件与导航

```text
wiki/
  Home.md
  _Sidebar.md
  home_cn.md
  home_us.md
  quick-start_cn.md
  quick-start_us.md
  python-scripts_cn.md
  python-scripts_us.md
  jsx-client-ui_cn.md
  jsx-client-ui_us.md
  error-reference_cn.md
  error-reference_us.md
```

主题名使用统一的小写英文 kebab-case，两种语言只改变后缀。正文 H1 使用各自语言，文件名不添加阅读序号。

[wiki-pages.json](wiki-pages.json) 是主题映射和阅读顺序的唯一清单。目前纳入 25 个主题，包括原本仅有英文的错误码参考。每个主题的两版保持相同标题层级、表格与示例顺序；翻译可以自然措辞，不要求逐句直译。

`Home.md` 是 GitHub Wiki 固定共享入口，`_Sidebar.md` 是唯一共享侧栏。两者由清单生成；内容页具有同主题语言切换和同序上一篇/下一篇。目录与导航由工具维护，不再分别手工排序。

旧中文文件和 `en_us/` 下的旧页面保留简短迁移入口。它们不再是正文事实源，也不算额外语言版本；不能假定 GitHub Wiki 为重命名自动创建永久重定向。

## 更新步骤

1. 修改对应的规范中文/英文主题文件。
2. 同步另一版的事实、限制、章节及例子。
3. 执行 `node scripts/wiki-docs.mjs sync` 更新语言头、锚点和导航。
4. 执行 `node scripts/wiki-docs.mjs check --structure-only --strict` 检查配对结构和本地链接。
5. 比较翻译语义后执行 `node scripts/wiki-docs.mjs stamp <topic-id>`，记录已审阅正文摘要。
6. 执行 `npm run test:wiki`、`npm run check:wiki` 和 `git diff --check`。

完整流程见 [translation-workflow.md](translation-workflow.md)，语言与术语约定见 [translation-guide.md](translation-guide.md)。

## 检查范围

- 每个主题存在 `_cn` 与 `_us` 两个正文，主题 ID 唯一且分组有效。
- 顶部语言切换是双向、同主题的，正文链接维持当前语言。
- 中英文标题层级、表格行列和代码围栏序列一致。
- 本地文件链接与显式章节锚点存在。
- 共享首页/侧栏和每页上一篇、下一篇与清单顺序一致。
- 中文源摘要与英文正文摘要匹配审阅时版本；未提交改动也会被检查。

检查默认只报告；严格模式对所有问题返回失败，包括缺页。`scripts/check-translation-drift` 保留旧命令接口，委托同一套 Node 工具，不再单独根据提交历史判断漂移。

`wiki-section-N` 是按标题顺序生成的配对锚点。插入或删除标题时要复核已有跨页章节链接，不能只认为 sync 会理解章节含义。

摘要相同和结构通过不证明语义正确。清单中的英文翻译状态为草稿，人工验收单独记录；工具不会生成维护者签字。GitHub Wiki 发布是另一项动作，本地检查不代表线上路由、锚点和渲染已经验收。

## Python 提示

[中文 Python 指南](../wiki/python-scripts_cn.md) 与 [English Python guide](../wiki/python-scripts_us.md) 均记录：

```python
from nekojs import *
```

也可使用不带别名的具名导入，如 `from nekojs import Item, ServerEvents`。这是给 IDE 解析 Python `.pyi` 类型包的入口，转译器会剥离它；运行时继续访问已有 Binding。

执行 `/nekojs probe python` 生成 `.neko_probe/python/nekojs/`。无参 `/nekojs probe` 只生成 TypeScript，不能代替 Python probe。补全还依赖 Pylance/Pyright 正确识别指向 `.neko_probe/python` 的工作区配置。

不需要 pip 安装 NekoJS，不要用 `import nekojs` 或带别名的导入替代这个特殊入口。快速开始和 FAQ 的两种语言都保留这些说明。

## 迁移安全

本次迁移从当前未提交文档正文复制，不从 HEAD 恢复，不修改并行开发的 Minecraft 源码。替换旧正文为迁移入口前，工具核对旧文件摘要；如果迁移期间原文件又被修改，迁移停止并要求先合并新内容。旧诊断码条目和字面消息保留到新的错误码参考。

维护者登记新错误码时更新规范双语错误码页。历史证据文档中的旧路径保留为历史记录，并可经迁移入口找到新参考。

GitHub 共享侧栏规则见 [官方说明](https://docs.github.com/en/communities/documenting-your-project-with-wikis/creating-a-footer-or-sidebar-for-your-wiki)。若未来迁移到独立文档站，再由 locale 路由提供动态侧栏；本次不新增站点框架。
