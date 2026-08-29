# PR 检查清单

> 提交 PR 前逐项自查；适用于本仓库的所有代码变更。**[必须]** 项未满足时 PR 不应请求 review。

## 通用

- [ ] **[必须]** 未新增任何 javac 警告（`--rerun-tasks` 全量重编译确认）
- [ ] **[必须]** 无混合行尾（工作树 CRLF / 仓库 LF，`.gitattributes` 强制）
- [ ] 提交信息符合 `fix|feat|refactor|build|docs(<scope>): 中文描述` 格式
- [ ] 逻辑独立的改动已拆分提交，无无关格式化混入

## 按改动范围

### 改了 common-api
- [ ] **[必须]** `:common-api:check`
- [ ] **[必须]** `:common:check` + 全量 `:common:test`（common-api jar 指纹强制 common 重编译）

### 改了 common
- [ ] **[必须]** `:common:check`（checkCommonIsolation 门禁：禁止 MC/loader import）
- [ ] **[必须]** 全量 `:common:test`
- [ ] 涉及新依赖：确认 MC/loader 无关
- [ ] 改了插件扩展点（`core/plugin/`）：新扩展点用 `initializer/collector/finisher` 三段式；
      新点只能依赖**先注册**点的产物（点优先执行序）

### 改了 probe（目录 / 渲染器 / IR）
- [ ] **[必须]** 运行 `PythonProbeBackendIntegrationTest`、`NekoScriptCatalogEventsTest` 等 probe 测试
- [ ] **[必须]** 输出变化涉及 golden 时：`./gradlew :common:regenerateGoldens` 后**人工 review diff**
- [ ] **[必须]** 遵守目录不变量（每个 bus 一条条目 + 规范标签）与渲染契约（dispatch 事件双 `@overload`）
- [ ] 生成产物保持确定性排序（FQN / 成员 / import 字典序）

### 改了事件组 / 绑定 / 适配器
- [ ] 对应节点 `compileJava`（SpecCoverageProcessor 范围校验生效）
- [ ] 新增绑定名/事件组名/配方命名空间确认全局唯一
- [ ] 跨加载器中立契约（载荷只用 vanilla 类型）住共享树；加载器专属绑定住守卫内或节点目录

### 改了版本节点 / 平台源码
- [ ] **[必须]** 受影响节点 `build`（含 verifyDevModSourceSets）
- [ ] 改共享树 `src/main/java`：按守卫范围跑**全部**消费节点；改 loader 守卫（`//? if neoforge` 等）
      跑 neoforge + fabric 两侧
- [ ] 新增守卫前自查 guardLint 三坑（文本块内 / 分支首行 `/*` / 未配对）
- [ ] 平台行为变更确认其它平台同步或明确记录不适用

### 改了构建脚本 / 依赖版本
- [ ] `--rerun-tasks` 全量编译确认 0 新增警告
- [ ] 新依赖进版本目录（`gradle/libs.versions.toml`）；刻意分歧需在 toml 头部注释说明
- [ ] stonecutter 节点列表变化：同步 `settings.gradle.kts` 与 `.github/workflows/ci-build.yml` 的 build 步骤

## 特殊任务

- [ ] NBT 相关改动：`nbtSmokeTest`（根分支三个 NeoForge 节点）
- [ ] 打包/装配改动：受影响节点 `:<node>:build` + 检查 jar 内容（fabric 不应含 NeoForge 资源）
- [ ] `checkCommonIsolation` 白名单变化：同步更新 `common` 的 build 脚本

## 收尾

- [ ] `git diff` 通读一遍，确认无调试残留 / 死代码
