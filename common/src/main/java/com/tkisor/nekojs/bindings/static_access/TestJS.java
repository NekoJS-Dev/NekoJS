package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import graal.graalvm.polyglot.Value;

import java.util.Objects;

/**
 * TEST 脚本类型的轻量断言工具：通过即计数并打日志，失败抛 {@link AssertionError}。
 */
@Doc("Tiny assertion harness for test scripts; failures throw AssertionError.")
public final class TestJS {
    private int passed;
    private int failed;

    /** 记录一条通过的断言并打日志。 */
    @Doc("Records a passed assertion and logs it.")
    @Param(name = "message", value = "optional label shown in the log; null/blank renders as empty")
    @Return("this, for chaining")
    public TestJS pass(String message) {
        passed++;
        log("PASS", message);
        return this;
    }

    /** 记录失败并抛出 {@link AssertionError}。 */
    @Doc("Records a failure and throws an AssertionError; never returns normally.")
    @Param(name = "message", value = "failure message; a default text is used when null/blank")
    public TestJS fail(String message) {
        failed++;
        throw new AssertionError(message == null || message.isBlank() ? "Test assertion failed" : message);
    }

    /** 断言为真，失败时抛错。 */
    @Doc("Asserts that the condition is true; throws on failure.")
    @Param(name = "condition", value = "condition expected to be true")
    @Param(name = "message", value = "label used in the pass log and the failure error")
    @Return("this, for chaining")
    public TestJS assertTrue(boolean condition, String message) {
        return condition ? pass(message) : fail(message);
    }

    /** 断言为假，失败时抛错。 */
    @Doc("Asserts that the condition is false; throws on failure.")
    @Param(name = "condition", value = "condition expected to be false")
    @Param(name = "message", value = "label used in the pass log and the failure error")
    @Return("this, for chaining")
    public TestJS assertFalse(boolean condition, String message) {
        return assertTrue(!condition, message);
    }

    /** 断言两值相等（{@link Objects#equals} 语义）。 */
    @Doc("Asserts that expected and actual are equal (Objects.equals semantics).")
    @Param(name = "expected", value = "expected value")
    @Param(name = "actual", value = "actual value")
    @Param(name = "message", value = "optional label prepended to the failure error")
    @Return("this, for chaining")
    public TestJS assertEquals(Object expected, Object actual, String message) {
        if (Objects.equals(expected, actual)) {
            return pass(message);
        }
        return fail(formatMessage(message, "Expected " + expected + " but got " + actual));
    }

    /** 断言非 null。 */
    @Doc("Asserts that the value is not null; throws on failure.")
    @Param(name = "value", value = "value expected to be non-null")
    @Param(name = "message", value = "label used in the pass log and the failure error")
    @Return("this, for chaining")
    public TestJS assertNotNull(Object value, String message) {
        return assertTrue(value != null, message);
    }

    /** 断言给定 JS 函数执行时抛出异常。 */
    @Doc("Runs the given JS function and asserts that it throws.")
    @Param(name = "callback", value = "JS function expected to throw when executed with no arguments")
    @Param(name = "message", value = "optional label prepended to the failure error")
    @Return("this, for chaining")
    public TestJS assertThrows(Object callback, String message) {
        Value fn = callback == null ? null : Value.asValue(callback);
        if (fn == null || !fn.canExecute()) {
            return fail(formatMessage(message, "assertThrows requires a function"));
        }
        try {
            fn.executeVoid();
        } catch (Throwable ignored) {
            return pass(message);
        }
        return fail(formatMessage(message, "Expected function to throw"));
    }

    /** 打一条 SECTION 日志用于分组。 */
    @Doc("Logs a section header to group the assertions that follow.")
    @Param(name = "name", value = "section name shown in the log")
    @Return("this, for chaining")
    public TestJS section(String name) {
        log("SECTION", name);
        return this;
    }

    /** 打一条通过/失败计数的汇总日志。 */
    @Doc("Logs the current passed/failed counts as a summary.")
    @Return("this, for chaining")
    public TestJS summary() {
        log("SUMMARY", passed + " passed, " + failed + " failed");
        return this;
    }

    /** 已通过的断言数。 */
    @Doc("Number of assertions passed so far.")
    @Return("count of passed assertions")
    public int passed() {
        return passed;
    }

    /** 已失败的断言数。 */
    @Doc("Number of assertions failed so far.")
    @Return("count of failed assertions")
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
