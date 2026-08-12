package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultErrorTracker#recordCallbackError} 回归测试：
 * 同一回调错误必须复用已存储的 {@link ScriptError} 实例并递增频次（去重先于解析，
 * 避免高频回调反复重建 ScriptError 并重读源码文件），不同签名则更新或新增记录。
 */
class DefaultErrorTrackerTest {

    private DefaultErrorTracker tracker;

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void newTracker() {
        tracker = new DefaultErrorTracker(NekoJSPaths.get(), SandboxConfig.defaultConfig());
    }

    private static NekoEsmLinkException esmError(int line, int column, String message) {
        return new NekoEsmLinkException(new NekoEsmDiagnostic(null, null, line, column, message));
    }

    @Test
    void repeatedCallbackErrorReusesStoredInstanceAndIncrementsCount() {
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));

        ScriptError first = singleError();

        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));

        ScriptError stored = singleError();
        assertSame(first, stored, "重复错误必须复用同一实例（不得重新解析异常/读取源码文件）");
        assertEquals(3, stored.getOccurrenceCount(), "每次出现都必须计入频次");
        assertEquals("boom", stored.getErrorMessage());
        assertEquals(10, stored.getLineNumber());
        assertEquals(5, stored.getColumnNumber());
        assertTrue(stored.getLogDetailText(true).contains("连续发生了 3 次"),
                "连续重复计数必须体现在日志明细中");
    }

    @Test
    void differentPositionWithSameMessageUpdatesStoredError() {
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(11, 5, "boom"));

        ScriptError stored = singleError();
        assertEquals(11, stored.getLineNumber(), "位置变化后应更新同一条记录");
        assertEquals(1, stored.getOccurrenceCount(), "新位置错误是更新而非重复，频次应重置为 1");
    }

    @Test
    void differentMessageCreatesSeparateEntry() {
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "bang"));

        assertEquals(2, tracker.getErrorCount(), "不同错误信息应各自记录");
    }

    @Test
    void freshPolyglotExceptionsWithSameSourcePositionDeduplicate() {
        tracker.recordCallbackError(ScriptType.SERVER, "event", throwingPolyglot());
        tracker.recordCallbackError(ScriptType.SERVER, "event", throwingPolyglot());

        ScriptError stored = singleError();
        assertEquals(2, stored.getOccurrenceCount(), "相同源码位置的重复 Polyglot 异常必须去重并递增频次");
    }

    /** 每次执行都抛出一个全新的 {@link PolyglotException}，模拟 20Hz tick 回调的重复错误。 */
    private PolyglotException throwingPolyglot() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.eval(Source.newBuilder("js", "function tick() {\n  throw new Error('tick failure');\n}\ntick();\n", "tick.js").build());
            throw new AssertionError("expected PolyglotException");
        } catch (PolyglotException e) {
            return e;
        } catch (Exception e) {
            throw new AssertionError("failed to build throwing source", e);
        }
    }

    private ScriptError singleError() {
        assertEquals(1, tracker.getErrorCount(), "应只保留一条错误记录");
        return tracker.getAllErrors().iterator().next();
    }
}
