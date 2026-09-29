# 07 module imports — file provenance

任务 7（ESM/CJS/TS 导入）的最小示例。本目录八个文件与其正本**逐字节一致**，未做任何
改动（这是刻意的：正本已被引擎侧 smoke 测试逐项执行并断言输出，改动会破坏「可复制
示例 = 已验证行为」的等式）：

| 子目录 | 正本 | 验证 |
|---|---|---|
| `js/`（CommonJS） | `common/src/test/resources/nekojs/module-examples/js/` | `ModuleExamplesSmokeTest`（票 11） |
| `cjs/`（强制 CJS） | `common/src/test/resources/nekojs/module-examples/cjs/` | 同上 |
| `esm/`（强制 ESM） | `common/src/test/resources/nekojs/module-examples/esm/` | 同上 |
| `ts/`（类型擦除） | `common/src/test/resources/nekojs/language-ts-examples/ts/` | 票 12 TS 示例测试 |

语义与能力依据：`docs/architecture-refactor/baseline/2026-09-18-language-pipeline/MIGRATION.md`
§1.1（JS/CJS/ESM 最小示例与缓存行为）、`docs/architecture-refactor/baseline/2026-09-19-language-ts/MIGRATION.md`
（TS 类型擦除与 JSX）。模块能力是 common 面管线，五节点同语义（票 11 MIGRATION §2：
`node_modules` 等跨类型共享条目在按类型清理时保留）。

试做放置与操作（TASKS.md 任务 7 行的展开）：

1. 把 `js/`、`cjs/`、`esm/`、`ts/` 四组文件分别放入 `<gameDir>/nekojs/server_scripts/`
   的四个互不相同的子目录（避免入口同名互相覆盖），每组只放自己的两个文件；
2. `/nekojs reload server` —— 预期四组全部成功加载，无诊断；
3. 验证点（按组记录）：
   - `js`：`require('./greet.js')` 解析同目录依赖，入口导出 `message === 'hello, neko!'`；
   - `cjs`：两次 `require` 返回同一模块身份（`identical === true`，`sum === 42`）；
   - `esm`：默认 + 命名 + 命名空间导入一致（`same === true`）；把 `hello.mjs` 里的
     `TAG` 改名后 reload，预期 **link 期报错且带文件行列**，不是运行时 undefined；
   - `ts`：类型擦除后 `message === 'hello, neko!'`、`answer === 42`；
4. 缓存行为（可选，票 11 已验）：改 `greet.js` 内容后 `/nekojs reload server`，
   预期重跑入口看到新输出，旧模块身份不返回。

跨类型注意：`client_scripts/`、`startup_scripts/` 各自有独立入口语义；本任务只在
server 侧试做即可覆盖模块管线（管线按 ScriptType 复用，票 11 MIGRATION §2）。
