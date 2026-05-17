package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Value;

import java.util.Objects;

public final class TestJS {
    private int passed;
    private int failed;

    public TestJS pass(String message) {
        passed++;
        log("PASS", message);
        return this;
    }

    public TestJS fail(String message) {
        failed++;
        throw new AssertionError(message == null || message.isBlank() ? "Test assertion failed" : message);
    }

    public TestJS assertTrue(boolean condition, String message) {
        return condition ? pass(message) : fail(message);
    }

    public TestJS assertFalse(boolean condition, String message) {
        return assertTrue(!condition, message);
    }

    public TestJS assertEquals(Object expected, Object actual, String message) {
        if (Objects.equals(expected, actual)) {
            return pass(message);
        }
        return fail(formatMessage(message, "Expected " + expected + " but got " + actual));
    }

    public TestJS assertNotNull(Object value, String message) {
        return assertTrue(value != null, message);
    }

    public TestJS assertThrows(Value callback, String message) {
        if (callback == null || !callback.canExecute()) {
            return fail(formatMessage(message, "assertThrows requires a function"));
        }
        try {
            callback.executeVoid();
        } catch (Throwable ignored) {
            return pass(message);
        }
        return fail(formatMessage(message, "Expected function to throw"));
    }

    public TestJS section(String name) {
        log("SECTION", name);
        return this;
    }

    public TestJS summary() {
        log("SUMMARY", passed + " passed, " + failed + " failed");
        return this;
    }

    public int passed() {
        return passed;
    }

    public int failed() {
        return failed;
    }

    private static String formatMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message + ": " + fallback;
    }

    private static void log(String level, String message) {
        ScriptType.TEST.logger().info("[NekoJS Test][{}] {}", level, message == null ? "" : message);
    }
}
