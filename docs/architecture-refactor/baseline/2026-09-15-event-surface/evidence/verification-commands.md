# 验证命令与结果（ticket 14，worktree ticket-14-event-surface）

执行日期：2026-09-15。全部在 worktree `D:\mcmodDemo\NekoJS\.worktrees\t14\NekoJS-mult` 内执行。

| 命令 | 结果 | 关键数字 |
|---|---|---|
| `./gradlew :common:check --console=plain` | BUILD SUCCESSFUL | 200 suites / 1474 tests / 0 failures / 4 skipped（本票 +5 suites / +25 tests） |
| `./gradlew :common-api-processor:test guardLint --console=plain` | BUILD SUCCESSFUL | guardLint 通过（common 主代码无 Minecraft/loader 类型引入；唯一主代码改动 PythonEventRenderer 仅 java/probe/surface import） |
| `./gradlew :26.1.2:check --console=plain` | BUILD SUCCESSFUL | 40 suites / 179 tests / 0 failures / 34 skipped（本票 +2 suites / +6 tests；另经 `--rerun-tasks` 强制全量重跑复核） |
| `npm run test:probe-types` | 退出码 0 | tsc -p common/src/test/probe-ts/tsconfig.json --noEmit 无错误 |

## golden 再生成记录（显式流程）

```
./gradlew :common:test --tests "com.tkisor.nekojs.probe.ProbeEventsSurfaceGoldenTest" \
    -Dnekojs.golden.regenerate=true --console=plain
# → 写回 common/src/test/resources/nekojs/probe/probe-events.expected.d.ts（新文件）
# 随后以只读模式重跑同一测试：BUILD SUCCESSFUL（字节比对通过）
```

## golden 完整性核对

```
git status --short   # 提交后仅余本目录（docs）新增
git diff --stat HEAD~5 -- common/src/test/resources/nekojs common/src/test/probe-ts
# → 仅 nekojs/probe/probe-events.expected.d.ts +46 行（新增）；
#    api-manifest-core.json 与 probe-ts/generated/index.d.ts 零变化
```
