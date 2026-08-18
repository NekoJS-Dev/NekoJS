# PR 检查清单（开发规范配套）

> 配合 `docs/DEVELOPMENT_SPEC.md` 使用。提交 PR 前逐项自查；适用于本仓库的所有代码变更。
> 对应规范的强制性条款标记为 **[必须]**。

## 通用

- [ ] **[必须]** 本次改动对应的 ROADMAP 条目已勾选/更新（`docs/ROADMAP.md`）
- [ ] **[必须]** 未新增任何 javac 警告（`--rerun-tasks` 全量重编译确认；警告处理惯例见规范 §2.4）
- [ ] **[必须]** 无混合行尾（工作树 CRLF / 仓库 LF，`.gitattributes` 强制）
- [ ] 提交信息符合 `fix|feat|refactor|build|docs(<scope>): 中文描述` 格式
- [ ] 逻辑独立的改动已拆分提交，无无关格式化混入

## 按改动范围

### 改了 common-api
- [ ] **[必须]** `:common-api:check`（checkApiBoundaries 门禁）
- [ ] **[必须]** `:common:check` + 全量 `:common:test`（common-api jar 指纹强制 common 重编译）

### 改了 common
- [ ] **[必须]** `:common:check`（checkCommonIsolation 门禁：禁止 MC/Forge/NeoForge import）
- [ ] **[必须]** 全量 `:common:test`
- [ ] 涉及新依赖：确认 MC/loader 无关；common 测试运行时依赖注入清单见规范 §4.1

### 改了 probe（目录 / 渲染器 / IR）
- [ ] **[必须]** 运行 `PythonProbeBackendIntegrationTest`、`NekoScriptCatalogEventsTest` 等 probe 测试
- [ ] **[必须]** 输出变化涉及 golden 时：`./gradlew :common:regenerateGoldens` 后**人工 review diff**
- [ ] **[必须]** 遵守目录不变量（每个 bus 一条条目 + 规范标签）与渲染契约（dispatch 事件双 `@overload`）
- [ ] 生成产物保持确定性排序（FQN / 成员 / import 字典序）

### 改了事件组 / 绑定 / 适配器
- [ ] 对应平台 `compileJava`（SpecCoverageProcessor 范围校验生效）
- [ ] 新增绑定名/事件组名/配方命名空间确认全局唯一

### 改了平台模块
- [ ] **[必须]** 该平台 `build`（含 verifyRuntimeArtifact / verifyDevModSourceSets）
- [ ] 改 `neoforge-shared` / `neoforge-26-shared` 共享树：跑**全部**消费平台（1.21.1 + 26.1 + 26.2）
- [ ] 平台行为变更确认其它平台同步或明确记录不适用（`platform_support.md`）

### 改了构建脚本 / 依赖版本
- [ ] `--rerun-tasks` 全量编译确认 0 新增警告
- [ ] 新依赖进版本目录（`gradle/libs.versions.toml`）；刻意分歧需在 toml 头部注释说明
- [ ] cleanroom 分发字节码保持 Java 21（勿动）

## 特殊任务

- [ ] NBT 相关改动：`nbtSmokeTest`
- [ ] 平台打包/装配改动：`:platforms:<p>:build`（remap 产物校验）
- [ ] `checkApiBoundaries` / `checkCommonIsolation` 白名单变化：同步更新对应 build.gradle 与规范 §1.3

## 收尾

- [ ] 更新受影响的 `ai_docs/`（行为文档 / 平台支持说明）与 `docs/DEVELOPMENT_SPEC.md`（如涉及新不变量）
- [ ] `git diff` 通读一遍，确认无调试残留 / 死代码
