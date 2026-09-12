# NekoJS 报错 UI：VS Code 文件定位调研

> 调研日期：2026-09-10（Asia/Shanghai）。范围仅限 VS Code 官方文档与仓库现有 DTO/UI 契约。
> 本轮未启动 VS Code、未运行游戏或构建、未创建子任务；所有网页均于 2026-09-10 访问，页面未公开标注发布日期。
> 事实等级：以下把“官方明示”“官方未规定”“建议”分开；本地 UI 六字段来自 `docs/architecture-refactor/evidence/error-dashboard-ui-research.md:12,72-104`。

## 0. 给 Astra 的一句话

**安全实现建议：只在客户端确认单人集成本地服务端、把 DTO `path` 解析为脚本根目录内真实存在的可读普通文件后，才允许打开；URL 仅在 line/column 都有可信结构化值时使用，当前只有 line 时若必须精确定位则只用官方 `--goto file:line` 的无 shell argv 形式。远端、virtual、`Unknown location`、无法验证或打开失败一律禁用/报错并说明原因，绝不伪造本地路径、猜列号、下载源码或让原型启动外部程序。**

## 1. 官方核验结论

### 1.1 URL 定位（官方明示）

- VS Code 官方 URL 格式：项目 `vscode://file/{full path to project}/`；文件 `vscode://file/{full path to file}`；文件行列 `vscode://file/{full path to file}:line:column`。
- 官方 Windows 示例：`vscode://file/c:/myProject/package.json:5:10`；官方未给出 Linux/macOS 的完整示例，只给出通用占位符。
- 官方说明该 URL 交给“platform's URL handling mechanism”，可供浏览器或文件管理器解析并重定向；Insiders 使用 `vscode-insiders://`。
- 因此行列 URI 的官方契约是 `:line:column`（两个数值）；文档没有声明只写 `:line` 的 URL 一定受支持，不能把该省略形式当作可靠契约。
- 官方没有说明缺失 URL handler、未安装 VS Code 时的错误形态，也没有提供跨平台“已安装/可用”检测 API。

### 1.2 `code --goto`（官方明示）

- 官方语法：`code --goto file:line{:character}`；`line` 必填，`character`（列/字符位置）可省略。
- 官方解释该参数与部分操作系统允许文件名含 `:` 有关，并支持“只有行号、没有列号”；这不等于任意含冒号的文件名都能无歧义地解析，实施复核见第 7 节。
- `code` 依赖 PATH：Windows/Linux 安装通常加入 PATH，但可手动/未加入；macOS 必须运行 **Shell Command: Install 'code' command in PATH**。官方 FAQ 对找不到 `code` 的说明是操作系统找不到该 binary。
- 官方还说明：CLI 打开不存在的文件时，VS Code 会创建该文件及中间目录并标记为已编辑。这是必须拒绝未验证路径的关键理由。

## 2. 路径与 URI 编码

| 平台 | 输入绝对实路径（示意） | 安全的候选输出（建议） | 官方覆盖度 |
|---|---|---|---|
| Windows | `C:\work\中文\My Script.js` | `vscode://file/c:/work/%E4%B8%AD%E6%96%87/My%20Script.js:12:4` | 官方有 Windows 示例；斜杠、驱动器冒号形式已示例 |
| Linux | `/home/me/中文/My Script.js` | `vscode://file/home/me/%E4%B8%AD%E6%96%87/My%20Script.js:12:4` | 只有通用 `{full path}`，无完整示例 |
| macOS | `/Users/me/中文/My Script.js` | `vscode://file/Users/me/%E4%B8%AD%E6%96%87/My%20Script.js:12:4` | 只有通用 `{full path}`，无完整示例 |

- 官方 URL 页没有规定空格、中文、`# ? %`、反斜杠或 POSIX 冒号的编码/转义细节；上表 Linux/macOS 单斜杠形式是基于 URI 路径语义的建议，不是官方示例。
- 建议实现：先把绝对路径规范化为 URI path，再按 UTF-8 百分号编码；空格用 `%20`（不要 `+`），中文按 UTF-8 字节编码，`#`→`%23`、`?`→`%3F`、`%`→`%25`，保留 `/` 分隔符；Windows 保留官方示例中的驱动器 `c:`。
- 不要用字符串拼接后直接打开。应使用能正确编码 path component 的 URI 构造器，再只在末尾追加经过校验的数字 `:line:column`；路径本身的 `%` 不得被二次解码或误当转义。
- POSIX 文件名可含 `:`，URI 文档未定义如何消除它和行列后缀的歧义，CLI 对含数字冒号段的文件名也存在歧义（见第 7 节）；本轮保守降级为打开已验证文件、不请求行列。Windows 驱动器冒号是官方示例的一部分，不能一律编码掉。

## 3. `--goto` 的调用边界

- CLI 路径是原生参数，不做 URL 百分号编码；必须调用 argv/参数数组，不能拼 shell 命令，也不能把路径插入 `cmd /c`、`sh -c` 一类字符串。空格、中文此时由参数边界承载。
- 不支持/不可用情况包括：`code` 不在 PATH、macOS 未安装 shell command、便携/未注册安装、受管环境拦截等。官方没有提供可靠的、统一的“VS Code 已安装”查询；只能尝试分派并处理失败。
- `--goto` 是进程执行能力，不等同于 URL 分派。真实实现若采用它，应捕获启动/退出异常并显示失败；不得因为写了成功 toast 就假定已打开。
- 即使用 URL 分派，也应等平台接受调用后再反馈；不要在点击前显示“已打开”。未安装或 handler 不可检测时，最诚实的 UI 是“VS Code 打开不可用/无法确认”而不是伪造成功。

## 4. 远端、虚拟路径与 DTO 六字段限制

- 当前客户端 DTO 精确为 `id, path, line, count, message, fullDetails`：`common/src/main/java/com/tkisor/nekojs/network/ErrorSummaryDTO.java:3-9`。没有 `column`，也没有本机/远端、workspace root、URI authority 或 VS Code remote host 字段。
- `path` 可能是 root-relative、virtual module display path、绝对路径或 `Unknown location`；`line` 缺失为 `-1`；`column` 只能从自由的 `fullDetails` 弱解析，不能作为结构化事实。不要把这种文本解析升级成“已验证列号”。
- 官方 Remote 文档说明：Remote-SSH 在远端运行扩展，源码可以完全不在本机；专用服务器上的 `path` 不是客户端本地路径。`vscode-remote://ssh-remote+<host>/<path>` 需要已配置的远端 authority，当前 DTO 没有该信息。
- 远端 `vscode-remote://` 只能在其 host/authority 已由用户配置且 scheme 明确可用时构造；NekoJS 不能把远端 `/server/...` 直接映射成 `vscode://file/...`，也不能悄悄下载到本地后打开。
- 本地判定需同时满足：客户端 `hasSingleplayerServer()`、路径位于 NekoJS 脚本根目录且不越界、`toRealPath()` 后仍是该根内的可读普通文件。现有实现已用 `hasSingleplayerServer()` 和 `verifyInsideNekoRoot`/`isInsideScriptRoot` 做相近校验：`src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java:187-195`。
- 对 remote、virtual、`Unknown location`、空/越界路径、非普通文件或不可读文件：按钮应禁用或降级为“文件位置不可用”；`line == -1` 时不追加行列，但文件本身已验证时仍可只打开文件。

## 5. HTML 原型与真实实现

- 本轮 HTML 样例只演示“打开计划”：按钮可打开页内只读说明/禁用态，展示将校验的 `path`、已知 `line` 与“真实实现可能打开”；不要执行 `window.open`、设置 `location`、放入 `vscode://` 超链接的点击行为。
- 原型不读/写磁盘，不访问 File System Access，不 `fetch`，不下载，不启动 VS Code，不执行 shell。fixture 路径只是演示文本，不能暗示它在本机存在或已被验证。
- 真实按钮流程：选中 DTO → 客户端本地路径/根边界/真实文件校验 → 仅在已安装且可调用时分派。URL 用于文件级打开或可信的 `line:column`；当前 DTO 无列、只有行且确需跳转时，使用官方文档化的 `--goto file:line` argv（无 shell），或仅打开文件而不伪造列。任何失败都显示不可用/失败，不回退到下载或创建文件。
- 列号缺失时不填 `1`：若只能可靠拿到 `line`，可打开文件不只标列，或使用 `--goto file:line`；不要把源码位置变成猜测。

## 6. 官方来源（均于 2026-09-10 访问）

1. [VS Code Command Line Interface：Opening VS Code with URLs / --goto / Opening Files and Folders / 'code' not recognized](https://code.visualstudio.com/docs/editor/command-line#_opening-vs-code-with-urls)
2. [VS Code macOS Setup：Launch VS Code from the command line](https://code.visualstudio.com/docs/setup/mac#_launch-vs-code-from-the-command-line)
3. [VS Code Windows Setup：PATH 与安装方式](https://code.visualstudio.com/docs/setup/windows)
4. [VS Code Remote - SSH：源码可不在本机、远端运行 VS Code Server](https://code.visualstudio.com/docs/remote/ssh)
5. [VS Code Remote Troubleshooting：`--remote`、`--file-uri`、`--folder-uri`、`vscode-remote://`](https://code.visualstudio.com/docs/remote/troubleshooting#_connect-to-a-remote-host-from-the-terminal)

## 7. 原生实施复核（2026-09-10）

原生实现中的可执行文件发现不直接执行 Windows `.cmd/.bat` shim：使用已存在的绝对 `Code.exe`，可从已安装的 PATH `bin` 推导父目录，或发现常见用户/系统安装路径；否则尝试已注册协议，所有候选失败就反馈失败。相对及空 PATH 项忽略，避免选择当前工作目录中的同名程序。所有进程参数按 argv 传递，不使用 shell 命令字符串。

对 POSIX 冒号文件的争议核对了 [VS Code 官方 `parseLineAndColumnAware` 实现](https://github.com/microsoft/vscode/blob/main/src/vs/base/common/extpath.ts)：该实现按冒号拆分输入，并将遇到的数字段识别成行、列。因此 `/scripts/name:123` 即使是合法文件，附加 `:42` 后也存在把文件名的 `123` 当作行、`42` 当作列的歧义。本轮选择：允许通过真实脚本根校验的冒号文件，只请求文件级打开、不推断列号；不将每个相对路径里出现的冒号都误判为 virtual scheme。此结论基于访问当天源码，并非跨未来版本永久保证。

复核通过修正的是路径分类和无 shell 分派边界；未在此研究中实际打开 VS Code。真实集成验证状态以 [原生面板验证记录](native-error-dashboard-verification.md) 为准。
