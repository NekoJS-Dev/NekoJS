package com.tkisor.nekojs.api.data;

import java.util.Objects;

/**
 * 富文本点击事件（跨平台、MC 版本无关）。
 *
 * <p>各动作的可用性受 MC 版本限制：1.12.2 没有 {@code COPY_TO_CLIPBOARD}，
 * 平台 adapter 会以安全方式降级处理。
 */
public sealed interface TextClickEvent permits
        TextClickEvent.RunCommand,
        TextClickEvent.SuggestCommand,
        TextClickEvent.OpenUrl,
        TextClickEvent.OpenFile,
        TextClickEvent.CopyToClipboard,
        TextClickEvent.ChangePage {

    /** 动作名（脚本侧 {@code event.action}）。 */
    String action();

    /** 动作值（命令/url/文件/剪贴板文本/页码字符串）。 */
    String value();

    /** 运行命令（需玩家有相应权限）。 */
    record RunCommand(String command) implements TextClickEvent {
        public RunCommand { Objects.requireNonNull(command, "command"); }
        @Override public String action() { return "runCommand"; }
        @Override public String value() { return command; }
    }

    /** 建议命令（填入聊天框，不执行）。 */
    record SuggestCommand(String command) implements TextClickEvent {
        public SuggestCommand { Objects.requireNonNull(command, "command"); }
        @Override public String action() { return "suggestCommand"; }
        @Override public String value() { return command; }
    }

    /** 打开 URL。 */
    record OpenUrl(String url) implements TextClickEvent {
        public OpenUrl { Objects.requireNonNull(url, "url"); }
        @Override public String action() { return "openUrl"; }
        @Override public String value() { return url; }
    }

    /** 打开文件（仅客户端）。 */
    record OpenFile(String path) implements TextClickEvent {
        public OpenFile { Objects.requireNonNull(path, "path"); }
        @Override public String action() { return "openFile"; }
        @Override public String value() { return path; }
    }

    /** 复制到剪贴板（1.15+ 可用；1.12.2 无此动作会被忽略）。 */
    record CopyToClipboard(String text) implements TextClickEvent {
        public CopyToClipboard { Objects.requireNonNull(text, "text"); }
        @Override public String action() { return "copyToClipboard"; }
        @Override public String value() { return text; }
    }

    /** 翻书页（书本 UI 内有效）。 */
    record ChangePage(int page) implements TextClickEvent {
        @Override public String action() { return "changePage"; }
        @Override public String value() { return Integer.toString(page); }
    }
}
