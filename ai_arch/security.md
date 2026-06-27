# NekoJS 安全分析（第三次审查版）

> 审查日期：2026-06-02（第三次审查）
> 审查方式：源码逐项验证（ClassFilter + NekoSandboxBuilder + NekoJSFileSystem + VFS）+ HostAccess 策略分析
> 安全模型重新校准：ClassFilter 为象征性防护（非硬沙盒），`allowReflection`/`allowAsm`/`allowThreads` 均可配置

## 安全模型定位

NekoJS 的 ClassFilter 并非面向"不受信任代码执行"的硬沙盒。`allowReflection`、`allowAsm`、`allowThreads` 均提供配置开关允许用户主动放宽限制。安全模型的设计意图是**默认提供基本防护 + 避免脚本无意间破坏服务端**，而非抵御恶意脚本作者的主动攻击。

### 六层防御体系

| 层 | 机制 | 作用 |
|---|---|---|
| 1 | `ClassFilter` | 拦截 `Java.type()` 调用，阻止已知危险类 |
| 2 | `@HideFromJS` 注解 | 标记不应暴露给 JS 的特定方法/字段 |
| 3 | `MemberVisibilityQuery` | 运行时检查 `@HideFromJS`、`@Remap`、`@RemapByPrefix` |
| 4 | `NekoJSMemberRemapper` | 二次检查 `@HideFromJS`（双重保障） |
| 5 | `allowCreateProcess(false)` | 硬编码禁止进程创建 |
| 6 | `NekoJSFileSystem` | 限制文件 I/O 到游戏目录 |

---

## 1. 沙盒与 ClassFilter（已验证）

### ClassFilter 配置（已验证）

**位置**: `common/.../core/fs/ClassFilter.java`

```java
public static boolean allowThreads = false;     // 代码默认 false
public static boolean allowReflection = false;  // 代码默认 false
public static boolean allowAsm = false;         // 代码默认 false
```

### GENERAL_BLACKLIST（已验证：`java.lang.Class` 已补充）

| 列入黑名单 | 状态 |
|---|---|
| `java.lang.Runtime` | ✅ |
| `java.lang.Process` | ✅ |
| `java.lang.ProcessBuilder` | ✅ |
| `java.lang.ClassLoader` | ✅ |
| `java.lang.System` | ✅ |
| `java.lang.Class` | ✅ **已补充（上次审查 P2 项已完成）** |
| `java.io.*` | ✅ |
| `java.nio.*` | ✅ |
| `java.net.*` | ✅ |
| `sun.*`, `com.sun.*` | ✅ |
| 更多 GraalVM 内部类 | ✅ |

### `HostAccess.ALL` 是有意选择（已验证）

**位置**: `common/.../core/NekoSharedHostAccess.java:18`

```java
HostAccess.newBuilder(HostAccess.ALL)
```

NekoJS 定位为 Minecraft 脚本引擎，脚本需要广泛访问 Minecraft API（Item、Block、Entity 等数百个类）。`HostAccess.ALL` + 黑名单 + 注解 + 两层成员可见性检查的组合在可用性和安全性之间取得了合理平衡。迁移到 `HostAccess.EXPLICIT` + 白名单需要逐类授权，维护成本远超收益。

### 已知绕过路径

```js
// 绕过 Java.type() 黑名单（通过 .getClass()）
const allowed = Java.type("java.util.ArrayList");
const ClassObj = allowed.class;  // HostAccess.ALL 允许
const UnsafeClass = ClassObj.forName("sun.misc.Unsafe");
```

**评估**: 由于 `allowReflection` 可配置关闭，且 `allowCreateProcess(false)` 硬编码阻止 `Runtime.exec()`，此绕过路径的实际危害取决于被绕过的类的自身能力。

### allowThreads 配置不一致（已验证：已修复）

> **修正**：原文档称代码默认 `true`，实际代码默认 `false`，但配置文件显式设为 `true`。

| 层面 | 值 | 状态 |
|---|---|---|
| `ClassFilter.java:20` 代码默认值 | `false` | ✅ |
| 两个平台 `engine.toml` 配置文件 | ~~`true`~~ → `false` | ✅ **已修复** |

### Nashorn 兼容模式（已验证）

**位置**: `NekoSandboxBuilder.java:65`

```java
.option("js.nashorn-compat", "true")
```

启用 `Java.extend()` API（允许 JS 创建 Java 子类）。从迁移兼容性角度看，关闭此选项可能破坏依赖 `Java.extend()` 的现有脚本。可作为未来评估项，不视为紧急问题。

---

## 2. 文件系统安全 (VFS)（已验证）

### VFS 边界确认

所有文件操作均经过 `NekoJSPaths` 的两层校验：

| 校验层 | 位置 | 作用 |
|---|---|---|
| `verifyInsideGameDir()` | `NekoJSPaths.java:104-114` | 规范化路径 + 检查 startsWith `GAME_DIR` + `toRealPath()` 符号链接解析 |
| `verifyInsideGameDirForCreate()` | `NekoJSPaths.java:118` | 同上 + 校验最近的已存在父目录 real path |
| `createSymbolicLink` 硬拒绝 | `NekoJSFileSystem.java:130` | 抛出 `AccessDeniedException` |

### 访问策略

| 操作 | 默认边界 | 配置项 |
|---|---|---|
| 读 | `.minecraft` 内 | 无放宽 |
| 写/删除 | `.minecraft/nekojs` 内 | `allowFsWriteOutsideNekojs = true` 可放宽至 `.minecraft` |
| 越界 | 拒绝 | 硬编码，不可放宽 |

### TOCTOU 竞态（低风险）

校验和 I/O 之间的 TOCTOU 窗口存在，但在单机/信任服务器环境中实际可利用性极低。改进方向：创建文件时使用 `Files.createFile()` + 事后 `toRealPath()` 验证。

### 包大小限制

| 限制 | 值 | 位置 |
|---|---|---|
| 单文件最大 | 1MB (`MAX_SINGLE_SCRIPT_SIZE`) | `ScriptSyncService.java` |
| 批量最大 | 8MB (`MAX_BATCH_SCRIPT_SIZE`) | `ScriptSyncService.java` |
| 批量总大小 | 32MB (`MAX_BATCH_TOTAL_SIZE`) | `ScriptSyncService.java` |

---

## 3. 网络安全（已验证：已修复信息泄露）

### 错误信息脱敏（上次审查 P2 项已完成）

`NekoJSNetwork.java`（两个平台）中 4 处 `e.getMessage()` 已替换为通用错误消息，详细信息保留在 LOGGER。

### 已正确防护

- ✅ 所有写操作需要 OP level ≥ 2
- ✅ 路径通过 `verifyScriptSyncPath()` 校验
- ✅ 文件扩展名白名单（仅支持的脚本类型）
- ✅ 同步使用 NBT 序列化（非自定义协议）

---

## 4. 信息暴露（设计决定）

### 系统环境变量 / OS 信息

`NekoNodeProcess.env()` 返回完整 `System.getenv()`，`NekoNodeOS` 暴露 hostname、username、网络接口 IP 等。这些是 Node.js 标准行为，为兼容性保留。在服务器场景下，敏感环境变量应通过启动脚本清理。

### 版本号（已验证：已修复）

`NekoNodeProcess.java` 版本号已从硬编码 `"1.0.7"` 改为引用 `NekoJS.VERSION`。✅

---

## 5. 未发现的问题（已验证）

- ✅ 无 `ObjectInputStream` 反序列化
- ✅ 无 `Runtime.exec()` 在 Java 侧（`allowCreateProcess(false)` 硬编码）
- ✅ 无硬编码密钥或 token
- ✅ `ctx.eval()` 参数均为硬编码字符串或已验证路径，无用户注入
- ✅ 无动态 SQL 拼接
- ✅ 无 XXE 风险（使用 GraalVM polyglot，不涉及 XML 解析器）

---

## 6. 安全改进建议（优先级排序）

### P1 — 已完成 ✅

| # | 项目 | 状态 |
|---|---|---|
| 1 | `allowThreads` 配置文件改为 `false` | ✅ 已完成 |
| 2 | `java.lang.Class` 加入黑名单 | ✅ 已完成 |
| 3 | 网络错误信息脱敏 | ✅ 已完成 |

### P2 — 建议执行

| # | 项目 | 行动 | 风险 |
|---|---|---|---|
| 4 | 静态可变字段封装 | `ClassFilter.allowThreads` 等改为 private + setter | 低 |
| 5 | `NekoNodeOS.java:100` 裸吞异常加注释 | 1 行改动 | 极低 |
| 6 | Nashorn 兼容模式可配置化 | 添加开关，评估现有脚本依赖 | 中（可能破坏现有脚本） |

### P3 — 长期评估

| # | 项目 | 说明 |
|---|---|---|
| 7 | VFS TOCTOU 加固 | 创建文件后 `toRealPath()` 二次验证 |
| 8 | `catch(Throwable)` 细化为具体异常类型 | 至少排除 `OutOfMemoryError` 等致命错误 |
| 9 | `process.versions.nekojs` 从 MANIFEST 读取 | 当前已修复为引用常量 |

### 不需要处理

| 项目 | 原因 |
|---|---|
| `HostAccess.ALL` → 白名单 | 与设计目标冲突，维护成本远超收益 |
| `.getClass()` bypass 修复 | 在 `allowReflection` 可配置的前提下，实际攻击面有限 |
| 环境变量/OS 信息过滤 | Node.js 兼容性要求，过滤会破坏标准 API |
| GraalJS CVE 跟踪 | 依赖升级属常规维护 |

---

## 7. 安全评分卡

| 维度 | 评分 | 说明 |
|---|---|---|
| **ClassFilter 完整性** | 9/10 | 黑名单覆盖完善，`java.lang.Class` 已补充 |
| **VFS 边界** | 9/10 | 双层校验 + symlink 硬拒绝，TOCTOU 为低风险 |
| **网络安全** | 8/10 | OP 校验 + 路径校验 + 错误信息已脱敏 |
| **配置安全** | 8/10 | `allowThreads` 已修复，`allowReflection`/`allowAsm` 默认安全 |
| **信息暴露** | 7/10 | 环境变量暴露是设计决定，版本号已修复 |
| **异常处理** | 6/10 | 1 处裸吞异常，多处 `catch(Throwable)` 过宽 |

**总评**：安全模型基本完善。六层防御体系搭配合理，主要安全改进已在 P1 轮次完成。剩余问题为边际改进 — 异常处理细化和静态字段封装。考虑到项目定位（Minecraft 脚本引擎、信任环境），当前安全级别充足。
