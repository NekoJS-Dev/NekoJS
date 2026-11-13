package com.tkisor.nekojs.api.data;

import java.util.Objects;

/**
 * A click event for rich text, independent of platform and MC version.
 *
 * <p>Not every action exists in every MC version: 1.12.2 has no {@code COPY_TO_CLIPBOARD},
 * and the platform adapter degrades safely.
 */
public sealed interface TextClickEvent permits
        TextClickEvent.RunCommand,
        TextClickEvent.SuggestCommand,
        TextClickEvent.OpenUrl,
        TextClickEvent.OpenFile,
        TextClickEvent.CopyToClipboard,
        TextClickEvent.ChangePage {

    /** The action name, exposed to scripts as {@code event.action}. */
    String action();

    /** The action value: a command, url, file, clipboard text or page number string. */
    String value();

    /** Runs a command. The player needs the matching permission. */
    record RunCommand(String command) implements TextClickEvent {
        public RunCommand { Objects.requireNonNull(command, "command"); }
        @Override public String action() { return "runCommand"; }
        @Override public String value() { return command; }
    }

    /** Suggests a command: fills it into the chat box without running it. */
    record SuggestCommand(String command) implements TextClickEvent {
        public SuggestCommand { Objects.requireNonNull(command, "command"); }
        @Override public String action() { return "suggestCommand"; }
        @Override public String value() { return command; }
    }

    /** Opens a URL. */
    record OpenUrl(String url) implements TextClickEvent {
        public OpenUrl { Objects.requireNonNull(url, "url"); }
        @Override public String action() { return "openUrl"; }
        @Override public String value() { return url; }
    }

    /** Opens a file. Client side only. */
    record OpenFile(String path) implements TextClickEvent {
        public OpenFile { Objects.requireNonNull(path, "path"); }
        @Override public String action() { return "openFile"; }
        @Override public String value() { return path; }
    }

    /** Copies to the clipboard. Available on 1.15 and later; 1.12.2 has no such action and ignores it. */
    record CopyToClipboard(String text) implements TextClickEvent {
        public CopyToClipboard { Objects.requireNonNull(text, "text"); }
        @Override public String action() { return "copyToClipboard"; }
        @Override public String value() { return text; }
    }

    /** Turns a book page. Only takes effect inside the book UI. */
    record ChangePage(int page) implements TextClickEvent {
        @Override public String action() { return "changePage"; }
        @Override public String value() { return Integer.toString(page); }
    }
}
